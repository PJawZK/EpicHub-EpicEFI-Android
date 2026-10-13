use crate::tuning::{TableDescriptor, TableSnapshot, TuneSnapshot, TuneSnapshotOrigin};

pub const OFFLINE_SOURCE_PROFILE_GENERATION: u64 = 42;
pub const OFFLINE_SOURCE_PRIMARY_TABLE_ID: u32 = 7;

pub fn offline_source_snapshot() -> TuneSnapshot {
    TuneSnapshot::new(
        OFFLINE_SOURCE_PROFILE_GENERATION,
        TuneSnapshotOrigin::OfflineFixture,
        [TableSnapshot {
            descriptor: TableDescriptor {
                id: OFFLINE_SOURCE_PRIMARY_TABLE_ID,
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
    .expect("built-in offline tune source must remain valid")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_source_is_immutable_snapshot_data() {
        let source = offline_source_snapshot();
        let table = &source.tables[&OFFLINE_SOURCE_PRIMARY_TABLE_ID];
        assert_eq!(source.profile_generation, OFFLINE_SOURCE_PROFILE_GENERATION);
        assert_eq!(source.origin, TuneSnapshotOrigin::OfflineFixture);
        assert_eq!(table.descriptor.rows, 4);
        assert_eq!(table.descriptor.cols, 5);
        assert_eq!(table.values[0], 42.0);
        assert!(!table.editable[19]);
    }
}
