use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ms_envelope_codec::{EnvelopeCodecError, decode_response, encode_request};
use crate::page_read_planner::{PageReadPlanError, plan_page_read};
use crate::page_write_planner::{PageWritePlan, PageWritePlanError, plan_page_patch_writes};
use crate::write_envelope_plan::{
    FramedPageWritePlan, WriteEnvelopePlanError, frame_page_write, verify_page_write_ack,
};
use crate::write_reconciliation_plan::PageBytePatch;

pub const RECONCILIATION_RESPONSE_OVERHEAD_BYTES: usize = 7;
pub const RECONCILIATION_OK_STATUS: u8 = 0x00;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GuardedRamWriteTransaction {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub offset: usize,
    pub before: Vec<u8>,
    pub after: Vec<u8>,
    pub precondition_read_request_frame: Vec<u8>,
    pub precondition_expected_response_size: usize,
    pub write: FramedPageWritePlan,
    pub readback_request_frame: Vec<u8>,
    pub readback_expected_response_size: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum ReconciliationTransactionError {
    WritePlanner(PageWritePlanError),
    WriteEnvelope(WriteEnvelopePlanError),
    ReadPlanner(PageReadPlanError),
    Envelope(EnvelopeCodecError),
    PreconditionStatus(u8),
    PreconditionLengthMismatch { expected: usize, actual: usize },
    PreconditionMismatch { expected: Vec<u8>, actual: Vec<u8> },
    ReadbackStatus(u8),
    ReadbackLengthMismatch { expected: usize, actual: usize },
    ReadbackMismatch { expected: Vec<u8>, actual: Vec<u8> },
    RangeOverflow,
}

impl From<PageWritePlanError> for ReconciliationTransactionError {
    fn from(value: PageWritePlanError) -> Self {
        Self::WritePlanner(value)
    }
}

impl From<PageReadPlanError> for ReconciliationTransactionError {
    fn from(value: PageReadPlanError) -> Self {
        Self::ReadPlanner(value)
    }
}

impl From<EnvelopeCodecError> for ReconciliationTransactionError {
    fn from(value: EnvelopeCodecError) -> Self {
        Self::Envelope(value)
    }
}

pub fn plan_guarded_ram_write_transactions(
    bundle: &CompiledEcuProfileBundle,
    patch: &PageBytePatch,
) -> Result<Vec<GuardedRamWriteTransaction>, ReconciliationTransactionError> {
    let write_plans = plan_page_patch_writes(bundle, patch)?;
    let mut transactions = Vec::new();
    for write_plan in write_plans {
        append_framable_transactions(bundle, write_plan, &mut transactions)?;
    }
    Ok(transactions)
}

pub fn verify_precondition(
    bundle: &CompiledEcuProfileBundle,
    transaction: &GuardedRamWriteTransaction,
    response_frame: &[u8],
) -> Result<(), ReconciliationTransactionError> {
    verify_expected_payload(
        bundle,
        response_frame,
        &transaction.before,
        VerificationPhase::Precondition,
    )
}

pub fn verify_write_ack(
    bundle: &CompiledEcuProfileBundle,
    transaction: &GuardedRamWriteTransaction,
    response_frame: &[u8],
) -> Result<(), ReconciliationTransactionError> {
    verify_page_write_ack(bundle, response_frame)
        .map_err(ReconciliationTransactionError::WriteEnvelope)?;
    if response_frame.len() != transaction.write.expected_response_size {
        return Err(ReconciliationTransactionError::WriteEnvelope(
            WriteEnvelopePlanError::Envelope(EnvelopeCodecError::ResponseLengthMismatch {
                declared: 1,
                actual: response_frame.len().saturating_sub(6),
            }),
        ));
    }
    Ok(())
}

pub fn verify_readback(
    bundle: &CompiledEcuProfileBundle,
    transaction: &GuardedRamWriteTransaction,
    response_frame: &[u8],
) -> Result<(), ReconciliationTransactionError> {
    verify_expected_payload(
        bundle,
        response_frame,
        &transaction.after,
        VerificationPhase::Readback,
    )
}

fn append_framable_transactions(
    bundle: &CompiledEcuProfileBundle,
    plan: PageWritePlan,
    transactions: &mut Vec<GuardedRamWriteTransaction>,
) -> Result<(), ReconciliationTransactionError> {
    match frame_page_write(bundle, &plan) {
        Ok(framed) => transactions.push(build_transaction(bundle, framed)?),
        Err(WriteEnvelopePlanError::Envelope(EnvelopeCodecError::RequestBodyTooLarge(_)))
            if plan.after.len() > 1 =>
        {
            let split = plan.after.len() / 2;
            let left = PageBytePatch {
                page: plan.page,
                offset: plan.offset,
                before: plan.before[..split].to_vec(),
                after: plan.after[..split].to_vec(),
            };
            let right_offset = plan
                .offset
                .checked_add(split)
                .ok_or(ReconciliationTransactionError::RangeOverflow)?;
            let right = PageBytePatch {
                page: plan.page,
                offset: right_offset,
                before: plan.before[split..].to_vec(),
                after: plan.after[split..].to_vec(),
            };
            for child in plan_page_patch_writes(bundle, &left)? {
                append_framable_transactions(bundle, child, transactions)?;
            }
            for child in plan_page_patch_writes(bundle, &right)? {
                append_framable_transactions(bundle, child, transactions)?;
            }
        }
        Err(error) => return Err(ReconciliationTransactionError::WriteEnvelope(error)),
    }
    Ok(())
}

fn build_transaction(
    bundle: &CompiledEcuProfileBundle,
    write: FramedPageWritePlan,
) -> Result<GuardedRamWriteTransaction, ReconciliationTransactionError> {
    let read = plan_page_read(bundle, write.page - 1, write.offset, write.after.len())?;
    let read_request_frame = encode_request(bundle, &read.request_body)?;
    let expected_response_size = write
        .after
        .len()
        .checked_add(RECONCILIATION_RESPONSE_OVERHEAD_BYTES)
        .ok_or(ReconciliationTransactionError::RangeOverflow)?;

    Ok(GuardedRamWriteTransaction {
        page: write.page,
        offset: write.offset,
        before: write.before.clone(),
        after: write.after.clone(),
        precondition_read_request_frame: read_request_frame.clone(),
        precondition_expected_response_size: expected_response_size,
        write,
        readback_request_frame: read_request_frame,
        readback_expected_response_size: expected_response_size,
    })
}

enum VerificationPhase {
    Precondition,
    Readback,
}

fn verify_expected_payload(
    bundle: &CompiledEcuProfileBundle,
    response_frame: &[u8],
    expected: &[u8],
    phase: VerificationPhase,
) -> Result<(), ReconciliationTransactionError> {
    let response = decode_response(bundle, response_frame)?;
    if response.status != RECONCILIATION_OK_STATUS {
        return Err(match phase {
            VerificationPhase::Precondition => {
                ReconciliationTransactionError::PreconditionStatus(response.status)
            }
            VerificationPhase::Readback => {
                ReconciliationTransactionError::ReadbackStatus(response.status)
            }
        });
    }
    if response.payload.len() != expected.len() {
        return Err(match phase {
            VerificationPhase::Precondition => {
                ReconciliationTransactionError::PreconditionLengthMismatch {
                    expected: expected.len(),
                    actual: response.payload.len(),
                }
            }
            VerificationPhase::Readback => {
                ReconciliationTransactionError::ReadbackLengthMismatch {
                    expected: expected.len(),
                    actual: response.payload.len(),
                }
            }
        });
    }
    if response.payload != expected {
        return Err(match phase {
            VerificationPhase::Precondition => ReconciliationTransactionError::PreconditionMismatch {
                expected: expected.to_vec(),
                actual: response.payload,
            },
            VerificationPhase::Readback => ReconciliationTransactionError::ReadbackMismatch {
                expected: expected.to_vec(),
                actual: response.payload,
            },
        });
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::ms_envelope_codec::crc32_ieee;

    fn bundle(page_size: usize) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI reconciliation-transaction.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = {page_size}
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2i%2o%2c"
pageChunkWrite = "C%2i%2o%2c%v"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "reconciliation-transaction".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
    }

    fn response(status: u8, payload: &[u8]) -> Vec<u8> {
        let mut body = Vec::with_capacity(payload.len() + 1);
        body.push(status);
        body.extend_from_slice(payload);
        let mut frame = Vec::with_capacity(body.len() + 6);
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        frame
    }

    #[test]
    fn transaction_contains_guard_write_ack_and_readback_steps() {
        let bundle = bundle(64);
        let patch = PageBytePatch {
            page: 1,
            offset: 10,
            before: vec![1, 2, 3],
            after: vec![4, 5, 6],
        };

        let transaction = plan_guarded_ram_write_transactions(&bundle, &patch)
            .unwrap()
            .remove(0);
        assert_eq!(transaction.page, 1);
        assert_eq!(transaction.offset, 10);
        assert_eq!(transaction.before, vec![1, 2, 3]);
        assert_eq!(transaction.after, vec![4, 5, 6]);
        assert_eq!(transaction.precondition_expected_response_size, 10);
        assert_eq!(transaction.write.expected_response_size, 7);
        assert_eq!(transaction.readback_expected_response_size, 10);
        assert_eq!(
            transaction.precondition_read_request_frame,
            transaction.readback_request_frame
        );
    }

    #[test]
    fn stale_ram_fails_before_write_can_be_authorized() {
        let bundle = bundle(64);
        let transaction = plan_guarded_ram_write_transactions(
            &bundle,
            &PageBytePatch {
                page: 1,
                offset: 0,
                before: vec![1, 2],
                after: vec![3, 4],
            },
        )
        .unwrap()
        .remove(0);

        assert_eq!(
            verify_precondition(&bundle, &transaction, &response(0, &[1, 9])),
            Err(ReconciliationTransactionError::PreconditionMismatch {
                expected: vec![1, 2],
                actual: vec![1, 9],
            })
        );
    }

    #[test]
    fn acknowledgement_does_not_replace_readback_verification() {
        let bundle = bundle(64);
        let transaction = plan_guarded_ram_write_transactions(
            &bundle,
            &PageBytePatch {
                page: 1,
                offset: 0,
                before: vec![1, 2],
                after: vec![3, 4],
            },
        )
        .unwrap()
        .remove(0);

        assert_eq!(verify_write_ack(&bundle, &transaction, &response(0, &[])), Ok(()));
        assert_eq!(
            verify_readback(&bundle, &transaction, &response(0, &[3, 9])),
            Err(ReconciliationTransactionError::ReadbackMismatch {
                expected: vec![3, 4],
                actual: vec![3, 9],
            })
        );
    }

    #[test]
    fn exact_before_ack_and_after_sequence_all_verify() {
        let bundle = bundle(64);
        let transaction = plan_guarded_ram_write_transactions(
            &bundle,
            &PageBytePatch {
                page: 1,
                offset: 5,
                before: vec![0x10, 0x20],
                after: vec![0xaa, 0xbb],
            },
        )
        .unwrap()
        .remove(0);

        assert_eq!(
            verify_precondition(&bundle, &transaction, &response(0, &[0x10, 0x20])),
            Ok(())
        );
        assert_eq!(verify_write_ack(&bundle, &transaction, &response(0, &[])), Ok(()));
        assert_eq!(
            verify_readback(&bundle, &transaction, &response(0, &[0xaa, 0xbb])),
            Ok(())
        );
    }

    #[test]
    fn oversized_write_body_is_split_until_every_envelope_is_representable() {
        let bundle = bundle(65_536);
        let patch = PageBytePatch {
            page: 1,
            offset: 0,
            before: vec![0; 65_536],
            after: vec![1; 65_536],
        };

        let transactions = plan_guarded_ram_write_transactions(&bundle, &patch).unwrap();
        assert!(transactions.len() >= 2);
        assert_eq!(transactions.first().unwrap().offset, 0);
        assert_eq!(
            transactions.last().unwrap().offset + transactions.last().unwrap().after.len(),
            65_536
        );
        assert!(transactions.iter().all(|transaction| {
            transaction.write.request_frame.len() <= u16::MAX as usize + 6
        }));
    }

    #[test]
    fn non_success_or_wrong_length_reads_fail_closed() {
        let bundle = bundle(8);
        let transaction = plan_guarded_ram_write_transactions(
            &bundle,
            &PageBytePatch {
                page: 1,
                offset: 0,
                before: vec![1, 2],
                after: vec![3, 4],
            },
        )
        .unwrap()
        .remove(0);

        assert_eq!(
            verify_precondition(&bundle, &transaction, &response(0x84, &[1, 2])),
            Err(ReconciliationTransactionError::PreconditionStatus(0x84))
        );
        assert_eq!(
            verify_readback(&bundle, &transaction, &response(0, &[3])),
            Err(ReconciliationTransactionError::ReadbackLengthMismatch {
                expected: 2,
                actual: 1,
            })
        );
    }
}
