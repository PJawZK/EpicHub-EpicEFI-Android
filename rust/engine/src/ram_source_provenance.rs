use crate::engine::with_tune_source_authority;
use crate::tuning::TuneSnapshotOrigin;
use crate::write_reconciliation_transaction::GuardedRamWriteTransaction;

pub const RAM_SOURCE_STATE_SNAPSHOT_SIZE: usize = 64;
const RAM_SOURCE_STATE_SNAPSHOT_MAGIC: [u8; 4] = *b"RAM1";
const RAM_SOURCE_STATE_SNAPSHOT_VERSION: u8 = 1;
const SNAPSHOT_FLAG_CONFIRMED_REVISION: u8 = 0x01;
const SNAPSHOT_FLAG_LAST_RECONCILIATION: u8 = 0x02;
const SNAPSHOT_FLAG_LAST_RECONCILIATION_COMPLETE: u8 = 0x04;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RamSourceAcquisition {
    LiveControllerRead,
}

impl RamSourceAcquisition {
    fn snapshot_code(self) -> u8 {
        match self {
            Self::LiveControllerRead => 1,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RamSynchronizationStatus {
    FreshLiveControllerConfirmation,
    CurrentDraftConfirmedInRam,
    LocalDraftAheadOfConfirmedRam,
    PartiallyVerifiedInterruptedWrite,
    RamUnconfirmedAfterInterruptedWrite,
}

impl RamSynchronizationStatus {
    fn snapshot_code(self) -> u8 {
        match self {
            Self::FreshLiveControllerConfirmation => 1,
            Self::CurrentDraftConfirmedInRam => 2,
            Self::LocalDraftAheadOfConfirmedRam => 3,
            Self::PartiallyVerifiedInterruptedWrite => 4,
            Self::RamUnconfirmedAfterInterruptedWrite => 5,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RamVerifiedWriteRange {
    pub page: usize,
    pub offset: usize,
    pub length: usize,
    pub draft_revision: u64,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RamWriteReconciliationSummary {
    pub draft_revision: u64,
    pub verified_transaction_count: usize,
    pub transaction_count: usize,
    pub complete: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RamSourceProvenance {
    pub profile_generation: u64,
    pub acquisition: RamSourceAcquisition,
    pub confirmed_draft_revision: Option<u64>,
    pub verified_write_ranges: Vec<RamVerifiedWriteRange>,
    pub last_reconciliation: Option<RamWriteReconciliationSummary>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineRamSourceState {
    pub current_draft_revision: u64,
    pub provenance: RamSourceProvenance,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum RamSourceProvenanceError {
    VerifiedPrefixTooLarge { verified: usize, available: usize },
}

impl RamSourceProvenance {
    pub fn from_live_controller_read(profile_generation: u64, draft_revision: u64) -> Self {
        Self {
            profile_generation,
            acquisition: RamSourceAcquisition::LiveControllerRead,
            confirmed_draft_revision: Some(draft_revision),
            verified_write_ranges: Vec::new(),
            last_reconciliation: None,
        }
    }

    pub fn reconciled_with_verified_prefix(
        &self,
        draft_revision: u64,
        transactions: &[GuardedRamWriteTransaction],
        verified_transaction_count: usize,
    ) -> Result<Self, RamSourceProvenanceError> {
        if verified_transaction_count > transactions.len() {
            return Err(RamSourceProvenanceError::VerifiedPrefixTooLarge {
                verified: verified_transaction_count,
                available: transactions.len(),
            });
        }

        let complete = verified_transaction_count == transactions.len();
        let mut next = self.clone();
        next.confirmed_draft_revision = complete.then_some(draft_revision);
        next.last_reconciliation = Some(RamWriteReconciliationSummary {
            draft_revision,
            verified_transaction_count,
            transaction_count: transactions.len(),
            complete,
        });
        next.verified_write_ranges.extend(
            transactions
                .iter()
                .take(verified_transaction_count)
                .map(|transaction| RamVerifiedWriteRange {
                    page: transaction.page,
                    offset: transaction.offset,
                    length: transaction.after.len(),
                    draft_revision,
                }),
        );
        Ok(next)
    }
}

impl EngineRamSourceState {
    pub fn synchronization_status(&self) -> RamSynchronizationStatus {
        if let Some(last_reconciliation) = &self.provenance.last_reconciliation {
            if !last_reconciliation.complete {
                return if last_reconciliation.verified_transaction_count == 0 {
                    RamSynchronizationStatus::RamUnconfirmedAfterInterruptedWrite
                } else {
                    RamSynchronizationStatus::PartiallyVerifiedInterruptedWrite
                };
            }
        }

        match self.provenance.confirmed_draft_revision {
            Some(confirmed) if confirmed == self.current_draft_revision => {
                if self.provenance.last_reconciliation.is_none() {
                    RamSynchronizationStatus::FreshLiveControllerConfirmation
                } else {
                    RamSynchronizationStatus::CurrentDraftConfirmedInRam
                }
            }
            Some(_) => RamSynchronizationStatus::LocalDraftAheadOfConfirmedRam,
            None => RamSynchronizationStatus::RamUnconfirmedAfterInterruptedWrite,
        }
    }

    pub fn snapshot_bytes(&self) -> [u8; RAM_SOURCE_STATE_SNAPSHOT_SIZE] {
        let mut bytes = [0u8; RAM_SOURCE_STATE_SNAPSHOT_SIZE];
        bytes[0..4].copy_from_slice(&RAM_SOURCE_STATE_SNAPSHOT_MAGIC);
        bytes[4] = self.synchronization_status().snapshot_code();
        bytes[5] = self.provenance.acquisition.snapshot_code();
        bytes[7] = RAM_SOURCE_STATE_SNAPSHOT_VERSION;

        let mut flags = 0u8;
        if self.provenance.confirmed_draft_revision.is_some() {
            flags |= SNAPSHOT_FLAG_CONFIRMED_REVISION;
        }
        if let Some(last_reconciliation) = &self.provenance.last_reconciliation {
            flags |= SNAPSHOT_FLAG_LAST_RECONCILIATION;
            if last_reconciliation.complete {
                flags |= SNAPSHOT_FLAG_LAST_RECONCILIATION_COMPLETE;
            }
        }
        bytes[6] = flags;

        write_u64(&mut bytes, 8, self.provenance.profile_generation);
        write_u64(&mut bytes, 16, self.current_draft_revision);
        write_u64(
            &mut bytes,
            24,
            self.provenance.confirmed_draft_revision.unwrap_or(u64::MAX),
        );
        write_u64(
            &mut bytes,
            32,
            u64::try_from(self.provenance.verified_write_ranges.len()).unwrap_or(u64::MAX),
        );

        if let Some(last_reconciliation) = &self.provenance.last_reconciliation {
            write_u64(&mut bytes, 40, last_reconciliation.draft_revision);
            write_u64(
                &mut bytes,
                48,
                u64::try_from(last_reconciliation.verified_transaction_count).unwrap_or(u64::MAX),
            );
            write_u64(
                &mut bytes,
                56,
                u64::try_from(last_reconciliation.transaction_count).unwrap_or(u64::MAX),
            );
        } else {
            write_u64(&mut bytes, 40, u64::MAX);
        }

        bytes
    }
}

fn write_u64(bytes: &mut [u8; RAM_SOURCE_STATE_SNAPSHOT_SIZE], offset: usize, value: u64) {
    bytes[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
}

pub fn engine_ram_source_state(engine_handle: i64) -> Option<Option<EngineRamSourceState>> {
    with_tune_source_authority(engine_handle, |session, source| {
        let source = source?;
        let provenance = if let Some(provenance) = source.provenance() {
            provenance.clone()
        } else if session.baseline().origin == TuneSnapshotOrigin::LiveController {
            RamSourceProvenance::from_live_controller_read(session.profile_generation(), 0)
        } else {
            return None;
        };
        Some(EngineRamSourceState {
            current_draft_revision: session.revision(),
            provenance,
        })
    })
}

pub fn engine_ram_source_state_snapshot(
    engine_handle: i64,
) -> Option<Option<[u8; RAM_SOURCE_STATE_SNAPSHOT_SIZE]>> {
    engine_ram_source_state(engine_handle).map(|state| state.map(|state| state.snapshot_bytes()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::write_envelope_plan::FramedPageWritePlan;

    fn transaction(
        page: usize,
        offset: usize,
        before: &[u8],
        after: &[u8],
    ) -> GuardedRamWriteTransaction {
        GuardedRamWriteTransaction {
            page,
            offset,
            before: before.to_vec(),
            after: after.to_vec(),
            precondition_read_request_frame: vec![0x11],
            precondition_expected_response_size: before.len(),
            write: FramedPageWritePlan {
                page,
                offset,
                before: before.to_vec(),
                after: after.to_vec(),
                request_frame: vec![0x22],
                expected_response_size: 1,
            },
            readback_request_frame: vec![0x33],
            readback_expected_response_size: after.len(),
        }
    }

    fn state(current_draft_revision: u64, provenance: RamSourceProvenance) -> EngineRamSourceState {
        EngineRamSourceState {
            current_draft_revision,
            provenance,
        }
    }

    #[test]
    fn live_acquisition_confirms_the_installed_draft_revision() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);

        assert_eq!(provenance.profile_generation, 77);
        assert_eq!(provenance.confirmed_draft_revision, Some(0));
        assert!(provenance.verified_write_ranges.is_empty());
        assert_eq!(provenance.last_reconciliation, None);
        assert_eq!(
            state(0, provenance).synchronization_status(),
            RamSynchronizationStatus::FreshLiveControllerConfirmation,
        );
    }

    #[test]
    fn local_edit_is_explicitly_ahead_of_confirmed_ram() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);

        assert_eq!(
            state(1, provenance).synchronization_status(),
            RamSynchronizationStatus::LocalDraftAheadOfConfirmedRam,
        );
    }

    #[test]
    fn full_verified_prefix_confirms_the_written_draft_and_records_exact_ranges() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);
        let transactions = vec![
            transaction(1, 6, &[100], &[101]),
            transaction(1, 8, &[200], &[201]),
        ];

        let reconciled = provenance
            .reconciled_with_verified_prefix(4, &transactions, 2)
            .unwrap();

        assert_eq!(reconciled.confirmed_draft_revision, Some(4));
        assert_eq!(
            reconciled.last_reconciliation,
            Some(RamWriteReconciliationSummary {
                draft_revision: 4,
                verified_transaction_count: 2,
                transaction_count: 2,
                complete: true,
            }),
        );
        assert_eq!(
            reconciled.verified_write_ranges,
            vec![
                RamVerifiedWriteRange {
                    page: 1,
                    offset: 6,
                    length: 1,
                    draft_revision: 4,
                },
                RamVerifiedWriteRange {
                    page: 1,
                    offset: 8,
                    length: 1,
                    draft_revision: 4,
                },
            ],
        );
        assert_eq!(
            state(4, reconciled).synchronization_status(),
            RamSynchronizationStatus::CurrentDraftConfirmedInRam,
        );
    }

    #[test]
    fn incomplete_prefix_never_claims_a_whole_draft_is_confirmed() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);
        let transactions = vec![
            transaction(1, 6, &[100], &[101]),
            transaction(1, 8, &[200], &[201]),
        ];

        let reconciled = provenance
            .reconciled_with_verified_prefix(4, &transactions, 1)
            .unwrap();

        assert_eq!(reconciled.confirmed_draft_revision, None);
        assert_eq!(reconciled.verified_write_ranges.len(), 1);
        assert!(!reconciled.last_reconciliation.as_ref().unwrap().complete);
        assert_eq!(
            state(4, reconciled).synchronization_status(),
            RamSynchronizationStatus::PartiallyVerifiedInterruptedWrite,
        );
    }

    #[test]
    fn zero_verified_transactions_in_an_incomplete_batch_invalidates_full_confirmation() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);
        let transactions = vec![transaction(1, 6, &[100], &[101])];

        let reconciled = provenance
            .reconciled_with_verified_prefix(4, &transactions, 0)
            .unwrap();

        assert_eq!(reconciled.confirmed_draft_revision, None);
        assert!(reconciled.verified_write_ranges.is_empty());
        assert_eq!(
            reconciled.last_reconciliation,
            Some(RamWriteReconciliationSummary {
                draft_revision: 4,
                verified_transaction_count: 0,
                transaction_count: 1,
                complete: false,
            }),
        );
        assert_eq!(
            state(4, reconciled).synchronization_status(),
            RamSynchronizationStatus::RamUnconfirmedAfterInterruptedWrite,
        );
    }

    #[test]
    fn empty_complete_batch_can_confirm_the_current_draft_without_ranges() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);

        let reconciled = provenance
            .reconciled_with_verified_prefix(3, &[], 0)
            .unwrap();

        assert_eq!(reconciled.confirmed_draft_revision, Some(3));
        assert!(reconciled.verified_write_ranges.is_empty());
        assert!(reconciled.last_reconciliation.as_ref().unwrap().complete);
        assert_eq!(
            state(3, reconciled).synchronization_status(),
            RamSynchronizationStatus::CurrentDraftConfirmedInRam,
        );
    }

    #[test]
    fn oversized_prefix_does_not_mutate_existing_provenance() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);
        let transactions = vec![transaction(1, 6, &[100], &[101])];

        assert_eq!(
            provenance.reconciled_with_verified_prefix(4, &transactions, 2),
            Err(RamSourceProvenanceError::VerifiedPrefixTooLarge {
                verified: 2,
                available: 1,
            }),
        );
        assert_eq!(provenance.confirmed_draft_revision, Some(0));
        assert!(provenance.verified_write_ranges.is_empty());
    }

    #[test]
    fn snapshot_keeps_semantic_status_and_evidence_atomic() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0)
            .reconciled_with_verified_prefix(
                4,
                &[
                    transaction(1, 6, &[100], &[101]),
                    transaction(1, 8, &[200], &[201]),
                ],
                1,
            )
            .unwrap();
        let snapshot = state(4, provenance).snapshot_bytes();

        assert_eq!(&snapshot[0..4], b"RAM1");
        assert_eq!(snapshot[4], 4);
        assert_eq!(snapshot[5], 1);
        assert_eq!(snapshot[6], SNAPSHOT_FLAG_LAST_RECONCILIATION);
        assert_eq!(snapshot[7], RAM_SOURCE_STATE_SNAPSHOT_VERSION);
        assert_eq!(u64::from_le_bytes(snapshot[8..16].try_into().unwrap()), 77);
        assert_eq!(u64::from_le_bytes(snapshot[16..24].try_into().unwrap()), 4);
        assert_eq!(u64::from_le_bytes(snapshot[24..32].try_into().unwrap()), u64::MAX);
        assert_eq!(u64::from_le_bytes(snapshot[32..40].try_into().unwrap()), 1);
        assert_eq!(u64::from_le_bytes(snapshot[40..48].try_into().unwrap()), 4);
        assert_eq!(u64::from_le_bytes(snapshot[48..56].try_into().unwrap()), 1);
        assert_eq!(u64::from_le_bytes(snapshot[56..64].try_into().unwrap()), 2);
    }
}
