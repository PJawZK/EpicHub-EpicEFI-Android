use crate::table_projection_abi;
use crate::tuning::{TableDescriptor, TableSnapshot, TuneDraft, TuneSnapshot, TuneSnapshotOrigin};

pub const OFFLINE_FIXTURE_PROFILE_GENERATION: u64 = 42;
pub const OFFLINE_FIXTURE_PRIMARY_TABLE_ID: u32 = 7;

pub fn offline_fixture_draft() -> TuneDraft {
    let snapshot = TuneSnapshot::new(
        OFFLINE_FIXTURE_PROFILE_GENERATION,
        TuneSnapshotOrigin::OfflineFixture,
        [TableSnapshot {
            descriptor: TableDescriptor {
                id: OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
                name: "VE Table 1".to_string(),
                rows: 4,
                cols: 5,
                minimum: 0.0,
                maximum: 200.0,
                quantum: 0.25,
                quantum_origin: 0.0,
                edit_increment: 0.5,
                display_decimals: 2,
            },
            x_axis: vec![1000.0, 2000.0, 3000.0, 4000.0, 5000.0],
            y_axis: vec![30.0, 60.0, 100.0, 150.0],
            values: vec![
                42.0, 46.0, 50.0, 54.0, 57.0, 50.0, 56.0, 62.0, 68.0, 72.0, 62.0, 70.0, 78.0, 86.0,
                92.0, 72.0, 82.0, 92.0, 102.0, 110.0,
            ],
            editable: vec![
                true, true, true, true, true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, false,
            ],
        }],
    )
    .expect("built-in offline fixture must remain valid");

    TuneDraft::from_snapshot(&snapshot)
}

pub fn offline_fixture_projection_bytes() -> Vec<u8> {
    let draft = offline_fixture_draft();
    let projection = draft
        .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
        .expect("built-in offline fixture table must exist");
    table_projection_abi::encode(draft.profile_generation(), draft.origin(), &projection)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::table_projection_abi::TABLE_PROJECTION_ABI_VERSION;

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    #[test]
    fn offline_fixture_is_explicit_and_profile_bound() {
        let draft = offline_fixture_draft();
        let projection = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();

        assert_eq!(
            draft.profile_generation(),
            OFFLINE_FIXTURE_PROFILE_GENERATION
        );
        assert_eq!(draft.origin(), TuneSnapshotOrigin::OfflineFixture);
        assert_eq!(projection.descriptor.rows, 4);
        assert_eq!(projection.descriptor.cols, 5);
        assert_eq!(projection.values.len(), 20);
        assert!(!projection.editable[19]);
    }

    #[test]
    fn fixture_projection_adapter_uses_the_same_versioned_abi() {
        let bytes = offline_fixture_projection_bytes();
        assert_eq!(read_u32(&bytes, 0), TABLE_PROJECTION_ABI_VERSION);
        assert_eq!(read_u32(&bytes, 28), OFFLINE_FIXTURE_PRIMARY_TABLE_ID);
    }
}
