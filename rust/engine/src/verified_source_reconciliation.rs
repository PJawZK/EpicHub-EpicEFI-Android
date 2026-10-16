use crate::tune_source_image::TuneSourcePageImage;
use crate::write_reconciliation_transaction::GuardedRamWriteTransaction;

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum VerifiedSourceReconciliationError {
    VerifiedPrefixTooLarge {
        verified: usize,
        available: usize,
    },
    InvalidPage(usize),
    EmptyPatch,
    PatchLengthMismatch {
        before: usize,
        after: usize,
    },
    RangeOverflow,
    RangeOutOfBounds {
        page: usize,
        offset: usize,
        length: usize,
        page_size: usize,
    },
    BeforeMismatch {
        page: usize,
        offset: usize,
        expected: Vec<u8>,
        actual: Vec<u8>,
    },
}

pub fn reconcile_verified_transaction_prefix(
    source: &TuneSourcePageImage,
    transactions: &[GuardedRamWriteTransaction],
    verified_transaction_count: usize,
) -> Result<TuneSourcePageImage, VerifiedSourceReconciliationError> {
    if verified_transaction_count > transactions.len() {
        return Err(VerifiedSourceReconciliationError::VerifiedPrefixTooLarge {
            verified: verified_transaction_count,
            available: transactions.len(),
        });
    }

    let mut pages = source.pages().to_vec();
    for transaction in transactions.iter().take(verified_transaction_count) {
        reconcile_transaction(&mut pages, transaction)?;
    }
    let refs = pages.iter().map(Vec::as_slice).collect::<Vec<_>>();
    Ok(TuneSourcePageImage::from_pages(&refs))
}

fn reconcile_transaction(
    pages: &mut [Vec<u8>],
    transaction: &GuardedRamWriteTransaction,
) -> Result<(), VerifiedSourceReconciliationError> {
    if transaction.page == 0 {
        return Err(VerifiedSourceReconciliationError::InvalidPage(0));
    }
    if transaction.before.is_empty() || transaction.after.is_empty() {
        return Err(VerifiedSourceReconciliationError::EmptyPatch);
    }
    if transaction.before.len() != transaction.after.len() {
        return Err(VerifiedSourceReconciliationError::PatchLengthMismatch {
            before: transaction.before.len(),
            after: transaction.after.len(),
        });
    }

    let page_index = transaction.page - 1;
    let page = pages
        .get_mut(page_index)
        .ok_or(VerifiedSourceReconciliationError::InvalidPage(
            transaction.page,
        ))?;
    let end = transaction
        .offset
        .checked_add(transaction.before.len())
        .ok_or(VerifiedSourceReconciliationError::RangeOverflow)?;
    if end > page.len() {
        return Err(VerifiedSourceReconciliationError::RangeOutOfBounds {
            page: transaction.page,
            offset: transaction.offset,
            length: transaction.before.len(),
            page_size: page.len(),
        });
    }

    let actual = &page[transaction.offset..end];
    if actual != transaction.before {
        return Err(VerifiedSourceReconciliationError::BeforeMismatch {
            page: transaction.page,
            offset: transaction.offset,
            expected: transaction.before.clone(),
            actual: actual.to_vec(),
        });
    }
    page[transaction.offset..end].copy_from_slice(&transaction.after);
    Ok(())
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
            precondition_expected_response_size: 1,
            write: FramedPageWritePlan {
                page,
                offset,
                before: before.to_vec(),
                after: after.to_vec(),
                request_frame: vec![0x22],
                expected_response_size: 1,
            },
            readback_request_frame: vec![0x33],
            readback_expected_response_size: 1,
        }
    }

    fn source() -> TuneSourcePageImage {
        TuneSourcePageImage::from_pages(&[&[1_u8, 2, 3, 4], &[5_u8, 6, 7, 8]])
    }

    #[test]
    fn zero_verified_transactions_leave_source_exactly_unchanged() {
        let source = source();
        let reconciled =
            reconcile_verified_transaction_prefix(&source, &[transaction(1, 1, &[2], &[9])], 0)
                .unwrap();

        assert_eq!(reconciled, source);
    }

    #[test]
    fn verified_prefix_advances_only_proven_ranges() {
        let source = source();
        let transactions = vec![
            transaction(1, 1, &[2], &[9]),
            transaction(2, 2, &[7], &[0xAA]),
        ];

        let first = reconcile_verified_transaction_prefix(&source, &transactions, 1).unwrap();
        assert_eq!(first.page(0), Some(&[1, 9, 3, 4][..]));
        assert_eq!(first.page(1), Some(&[5, 6, 7, 8][..]));

        let all = reconcile_verified_transaction_prefix(&source, &transactions, 2).unwrap();
        assert_eq!(all.page(0), Some(&[1, 9, 3, 4][..]));
        assert_eq!(all.page(1), Some(&[5, 6, 0xAA, 8][..]));
    }

    #[test]
    fn stale_retained_before_bytes_fail_closed_without_partial_result() {
        let source = source();
        let transactions = vec![
            transaction(1, 1, &[2], &[9]),
            transaction(2, 0, &[0xFF], &[1]),
        ];

        assert_eq!(
            reconcile_verified_transaction_prefix(&source, &transactions, 2),
            Err(VerifiedSourceReconciliationError::BeforeMismatch {
                page: 2,
                offset: 0,
                expected: vec![0xFF],
                actual: vec![5],
            })
        );
        assert_eq!(source.page(0), Some(&[1, 2, 3, 4][..]));
        assert_eq!(source.page(1), Some(&[5, 6, 7, 8][..]));
    }

    #[test]
    fn verified_prefix_cannot_exceed_batch_length() {
        let source = source();
        let transactions = vec![transaction(1, 0, &[1], &[2])];

        assert_eq!(
            reconcile_verified_transaction_prefix(&source, &transactions, 2),
            Err(VerifiedSourceReconciliationError::VerifiedPrefixTooLarge {
                verified: 2,
                available: 1,
            })
        );
    }

    #[test]
    fn malformed_transaction_geometry_is_rejected() {
        let source = source();
        let mut malformed = transaction(1, 3, &[4], &[9]);
        malformed.after.push(10);
        assert_eq!(
            reconcile_verified_transaction_prefix(&source, &[malformed], 1),
            Err(VerifiedSourceReconciliationError::PatchLengthMismatch {
                before: 1,
                after: 2,
            })
        );

        let out_of_range = transaction(2, 4, &[1], &[2]);
        assert_eq!(
            reconcile_verified_transaction_prefix(&source, &[out_of_range], 1),
            Err(VerifiedSourceReconciliationError::RangeOutOfBounds {
                page: 2,
                offset: 4,
                length: 1,
                page_size: 4,
            })
        );
    }
}
