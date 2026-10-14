use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

pub const TUNE_CATALOG_ABI_VERSION: u32 = 1;
pub const TUNE_CATALOG_HEADER_SIZE: usize = 40;
pub const TUNE_CATALOG_ENTRY_SIZE: usize = 24;

pub fn encode(snapshot: &TuneSnapshot, revision: u64) -> Vec<u8> {
    let mut tables: Vec<_> = snapshot.tables.values().collect();
    tables.sort_by_key(|table| table.descriptor.id);

    let names_size = tables
        .iter()
        .map(|table| table.descriptor.name.len())
        .sum::<usize>();
    let entries_size = tables
        .len()
        .checked_mul(TUNE_CATALOG_ENTRY_SIZE)
        .expect("catalog entry size overflow");
    let total_size = TUNE_CATALOG_HEADER_SIZE
        .checked_add(entries_size)
        .and_then(|size| size.checked_add(names_size))
        .expect("catalog size overflow");

    let mut bytes = Vec::with_capacity(total_size);
    write_u32(&mut bytes, TUNE_CATALOG_ABI_VERSION);
    write_u32(&mut bytes, TUNE_CATALOG_HEADER_SIZE as u32);
    write_u32(&mut bytes, TUNE_CATALOG_ENTRY_SIZE as u32);
    write_u32(&mut bytes, u32::try_from(tables.len()).expect("catalog table count overflow"));
    write_u64(&mut bytes, snapshot.profile_generation);
    write_u64(&mut bytes, revision);
    write_u32(&mut bytes, origin_code(snapshot.origin));
    write_u32(&mut bytes, u32::try_from(total_size).expect("catalog total size overflow"));

    let names_offset = TUNE_CATALOG_HEADER_SIZE + entries_size;
    let mut next_name_offset = names_offset;
    for table in &tables {
        write_u32(&mut bytes, table.descriptor.id);
        write_u32(
            &mut bytes,
            u32::try_from(table.descriptor.rows).expect("catalog row count overflow"),
        );
        write_u32(
            &mut bytes,
            u32::try_from(table.descriptor.cols).expect("catalog column count overflow"),
        );
        write_u32(&mut bytes, u32::from(table.descriptor.display_decimals));
        write_u32(
            &mut bytes,
            u32::try_from(next_name_offset).expect("catalog name offset overflow"),
        );
        write_u32(
            &mut bytes,
            u32::try_from(table.descriptor.name.len()).expect("catalog name length overflow"),
        );
        next_name_offset += table.descriptor.name.len();
    }

    for table in &tables {
        bytes.extend_from_slice(table.descriptor.name.as_bytes());
    }
    debug_assert_eq!(bytes.len(), total_size);
    bytes
}

fn origin_code(origin: TuneSnapshotOrigin) -> u32 {
    match origin {
        TuneSnapshotOrigin::OfflineFixture => 1,
        TuneSnapshotOrigin::ImportedFile => 2,
    }
}

fn write_u32(bytes: &mut Vec<u8>, value: u32) {
    bytes.extend_from_slice(&value.to_le_bytes());
}

fn write_u64(bytes: &mut Vec<u8>, value: u64) {
    bytes.extend_from_slice(&value.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::resolved_profile_source::{
        MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, Mega144h7ResolvedModes,
        TABLE_ID_IGNITION, TABLE_ID_MAP_ESTIMATE, TABLE_ID_TARGET_MIXTURE, TABLE_ID_VE,
        resolve_mega144h7_tune_pages,
    };

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    fn read_u64(bytes: &[u8], offset: usize) -> u64 {
        u64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
    }

    #[test]
    fn catalog_is_versioned_sorted_and_self_describing() {
        let mut page_1 = vec![0; MEGA144H7_PAGE_1_SIZE];
        page_1[18_848..19_872].fill(147);
        let page_2 = vec![0; MEGA144H7_PAGE_2_SIZE];
        let snapshot = resolve_mega144h7_tune_pages(
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
            TuneSnapshotOrigin::ImportedFile,
        )
        .unwrap();
        let bytes = encode(&snapshot, 7);

        assert_eq!(read_u32(&bytes, 0), TUNE_CATALOG_ABI_VERSION);
        assert_eq!(read_u32(&bytes, 4) as usize, TUNE_CATALOG_HEADER_SIZE);
        assert_eq!(read_u32(&bytes, 8) as usize, TUNE_CATALOG_ENTRY_SIZE);
        assert_eq!(read_u32(&bytes, 12), 4);
        assert_eq!(read_u64(&bytes, 16), snapshot.profile_generation);
        assert_eq!(read_u64(&bytes, 24), 7);
        assert_eq!(read_u32(&bytes, 32), 2);
        assert_eq!(read_u32(&bytes, 36) as usize, bytes.len());

        assert_eq!(read_u32(&bytes, 40), TABLE_ID_VE);
        assert_eq!(read_u32(&bytes, 64), TABLE_ID_IGNITION);
        assert_eq!(read_u32(&bytes, 88), TABLE_ID_TARGET_MIXTURE);
        assert_eq!(read_u32(&bytes, 112), TABLE_ID_MAP_ESTIMATE);

        let first_name_offset = read_u32(&bytes, 56) as usize;
        let first_name_len = read_u32(&bytes, 60) as usize;
        assert_eq!(
            std::str::from_utf8(&bytes[first_name_offset..first_name_offset + first_name_len])
                .unwrap(),
            "VE Table"
        );
    }
}
