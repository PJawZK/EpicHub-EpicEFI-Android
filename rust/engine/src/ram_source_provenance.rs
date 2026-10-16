use crate::write_reconciliation_transaction::GuardedRamWriteTransaction;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RamSourceAcquisition {
    LiveControllerRead,
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::write_envelope_plan::FramedPageWritePlan;

    fn transaction(page: usize, offset: usize, before: &[u8], after: &[u8]) -> GuardedRamWriteTransaction {
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
                request_frame: vec![0x22],
                expected_response_size: 1,
            },
            readback_request_frame: vec![0x33],
            readback_expected_response_size: after.len(),
        }
    }

    #[test]
    fn live_acquisition_confirms_the_installed_draft_revision() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);

        assert_eq!(provenance.profile_generation, 77);
        assert_eq!(provenance.confirmed_draft_revision, Some(0));
        assert!(provenance.verified_write_ranges.is_empty());
        assert_eq!(provenance.last_reconciliation, None);
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
        assert_eq!(reconciled.last_reconciliation.as_ref().unwrap().complete, false);
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
    }

    #[test]
    fn empty_complete_batch_can_confirm_the_current_draft_without_ranges() {
        let provenance = RamSourceProvenance::from_live_controller_read(77, 0);

        let reconciled = provenance
            .reconciled_with_verified_prefix(3, &[], 0)
            .unwrap();

        assert_eq!(reconciled.confirmed_draft_revision, Some(3));
        assert!(reconciled.verified_write_ranges.is_empty());
        assert_eq!(reconciled.last_reconciliation.as_ref().unwrap().complete, true);
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
}
