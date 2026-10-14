use crate::tuning::{TableProjection, TuneSnapshotOrigin};

pub const TABLE_PROJECTION_ABI_VERSION: u32 = 1;
pub const TABLE_PROJECTION_HEADER_SIZE: usize = 112;
pub const TABLE_PROJECTION_FLAG_OFFLINE: u32 = 1 << 0;
pub const TABLE_PROJECTION_FLAG_FIXTURE: u32 = 1 << 1;

pub fn encoded_size(projection: &TableProjection) -> usize {
    let name_len = projection.descriptor.name.len();
    let x_len = projection.x_axis.len() * size_of::<f64>();
    let y_len = projection.y_axis.len() * size_of::<f64>();
    let values_len = projection.values.len() * size_of::<f64>();
    let name_end = TABLE_PROJECTION_HEADER_SIZE.saturating_add(name_len);
    let x_offset = align_up(name_end, 8);
    x_offset
        .saturating_add(x_len)
        .saturating_add(y_len)
        .saturating_add(values_len)
        .saturating_add(projection.editable.len())
}

pub fn encode(
    profile_generation: u64,
    origin: TuneSnapshotOrigin,
    projection: &TableProjection,
) -> Vec<u8> {
    let total_size = encoded_size(projection);
    let name = projection.descriptor.name.as_bytes();
    let name_offset = TABLE_PROJECTION_HEADER_SIZE;
    let x_offset = align_up(name_offset + name.len(), 8);
    let y_offset = x_offset + projection.x_axis.len() * size_of::<f64>();
    let values_offset = y_offset + projection.y_axis.len() * size_of::<f64>();
    let editable_offset = values_offset + projection.values.len() * size_of::<f64>();

    let mut bytes = vec![0_u8; total_size];
    let flags = match origin {
        TuneSnapshotOrigin::OfflineFixture => {
            TABLE_PROJECTION_FLAG_OFFLINE | TABLE_PROJECTION_FLAG_FIXTURE
        }
        TuneSnapshotOrigin::ImportedFile => TABLE_PROJECTION_FLAG_OFFLINE,
        TuneSnapshotOrigin::LiveController => 0,
    };

    write_u32(&mut bytes, 0, TABLE_PROJECTION_ABI_VERSION);
    write_u32(&mut bytes, 4, u32::try_from(total_size).unwrap_or(u32::MAX));
    write_u64(&mut bytes, 8, profile_generation);
    write_u64(&mut bytes, 16, projection.draft_revision);
    write_u32(&mut bytes, 24, origin_code(origin));
    write_u32(&mut bytes, 28, projection.descriptor.id);
    write_u32(&mut bytes, 32, projection.descriptor.rows as u32);
    write_u32(&mut bytes, 36, projection.descriptor.cols as u32);
    write_u32(
        &mut bytes,
        40,
        projection.descriptor.display_decimals as u32,
    );
    write_u32(&mut bytes, 44, flags);
    write_f64(&mut bytes, 48, projection.descriptor.minimum);
    write_f64(&mut bytes, 56, projection.descriptor.maximum);
    write_f64(&mut bytes, 64, projection.descriptor.quantum);
    write_f64(&mut bytes, 72, projection.descriptor.quantum_origin);
    write_f64(&mut bytes, 80, projection.descriptor.edit_increment);
    write_u32(&mut bytes, 88, name.len() as u32);
    write_u32(&mut bytes, 92, name_offset as u32);
    write_u32(&mut bytes, 96, x_offset as u32);
    write_u32(&mut bytes, 100, y_offset as u32);
    write_u32(&mut bytes, 104, values_offset as u32);
    write_u32(&mut bytes, 108, editable_offset as u32);

    bytes[name_offset..name_offset + name.len()].copy_from_slice(name);
    write_f64_slice(&mut bytes, x_offset, &projection.x_axis);
    write_f64_slice(&mut bytes, y_offset, &projection.y_axis);
    write_f64_slice(&mut bytes, values_offset, &projection.values);
    for (index, editable) in projection.editable.iter().enumerate() {
        bytes[editable_offset + index] = u8::from(*editable);
    }

    bytes
}

fn origin_code(origin: TuneSnapshotOrigin) -> u32 {
    match origin {
        TuneSnapshotOrigin::OfflineFixture => 1,
        TuneSnapshotOrigin::ImportedFile => 2,
        TuneSnapshotOrigin::LiveController => 3,
    }
}

fn align_up(value: usize, alignment: usize) -> usize {
    let mask = alignment - 1;
    value.saturating_add(mask) & !mask
}

fn write_u32(target: &mut [u8], offset: usize, value: u32) {
    target[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn write_u64(target: &mut [u8], offset: usize, value: u64) {
    target[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
}

fn write_f64(target: &mut [u8], offset: usize, value: f64) {
    target[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
}

fn write_f64_slice(target: &mut [u8], mut offset: usize, values: &[f64]) {
    for value in values {
        write_f64(target, offset, *value);
        offset += size_of::<f64>();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tuning_fixture::{OFFLINE_FIXTURE_PRIMARY_TABLE_ID, offline_fixture_draft};

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    fn read_u64(bytes: &[u8], offset: usize) -> u64 {
        u64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
    }

    fn read_f64(bytes: &[u8], offset: usize) -> f64 {
        f64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
    }

    #[test]
    fn projection_abi_is_versioned_variable_sized_and_self_describing() {
        let draft = offline_fixture_draft();
        let projection = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();
        let bytes = encode(draft.profile_generation(), draft.origin(), &projection);

        assert_eq!(read_u32(&bytes, 0), TABLE_PROJECTION_ABI_VERSION);
        assert_eq!(read_u32(&bytes, 4) as usize, bytes.len());
        assert_eq!(read_u64(&bytes, 8), draft.profile_generation());
        assert_eq!(read_u64(&bytes, 16), 0);
        assert_eq!(read_u32(&bytes, 28), OFFLINE_FIXTURE_PRIMARY_TABLE_ID);
        assert_eq!(read_u32(&bytes, 32), 4);
        assert_eq!(read_u32(&bytes, 36), 5);
        assert_eq!(
            read_u32(&bytes, 44),
            TABLE_PROJECTION_FLAG_OFFLINE | TABLE_PROJECTION_FLAG_FIXTURE
        );

        let x_offset = read_u32(&bytes, 96) as usize;
        let values_offset = read_u32(&bytes, 104) as usize;
        let editable_offset = read_u32(&bytes, 108) as usize;
        assert_eq!(x_offset % 8, 0);
        assert_eq!(read_f64(&bytes, x_offset), 1000.0);
        assert_eq!(read_f64(&bytes, values_offset), 42.0);
        assert_eq!(bytes[editable_offset + 19], 0);
    }

    #[test]
    fn live_controller_projection_is_not_marked_offline() {
        let draft = offline_fixture_draft();
        let projection = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();
        let bytes = encode(
            draft.profile_generation(),
            TuneSnapshotOrigin::LiveController,
            &projection,
        );
        assert_eq!(read_u32(&bytes, 24), 3);
        assert_eq!(read_u32(&bytes, 44), 0);
    }
}
