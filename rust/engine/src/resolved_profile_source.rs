use crate::tuning::{TableDescriptor, TableSnapshot, TuneError, TuneSnapshot, TuneSnapshotOrigin};

pub const MEGA144H7_PROFILE_NAME: &str = "353";
pub const MEGA144H7_PROFILE_SIGNATURE: &str = "rusEFI master.2026.08.26.MEGA144H7.2273317132";
pub const MEGA144H7_PROFILE_FINGERPRINT: &str =
    "1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa";
pub const MEGA144H7_PROFILE_GENERATION: u64 = 2_273_317_132;
pub const MEGA144H7_PAGE_1_SIZE: usize = 55_764;
pub const MEGA144H7_PAGE_2_SIZE: usize = 5_000;

pub const TABLE_ID_VE: u32 = 1_001;
pub const TABLE_ID_IGNITION: u32 = 1_002;
pub const TABLE_ID_TARGET_MIXTURE: u32 = 1_003;
pub const TABLE_ID_MAP_ESTIMATE: u32 = 1_004;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Mega144h7ResolvedModes {
    pub hundredth_ve: bool,
    pub hundredth_ignition: bool,
    pub lambda_targets: bool,
}

impl Default for Mega144h7ResolvedModes {
    fn default() -> Self {
        Self {
            hundredth_ve: true,
            hundredth_ignition: true,
            lambda_targets: true,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProfileResolveError {
    InvalidPageSize {
        page: u8,
        expected: usize,
        actual: usize,
    },
    OutOfBounds(&'static str),
    InvalidTune(TuneError),
}

impl From<TuneError> for ProfileResolveError {
    fn from(value: TuneError) -> Self {
        Self::InvalidTune(value)
    }
}

/// Resolves the two raw tune pages for the qualified Mega144H7 profile into EpicHub's semantic
/// TuneSnapshot. The caller owns source provenance. This decoder never guesses where the bytes
/// came from, so imported files and future live-controller captures can share one profile compiler.
pub fn resolve_mega144h7_tune_pages(
    page_1: &[u8],
    page_2: &[u8],
    modes: Mega144h7ResolvedModes,
    origin: TuneSnapshotOrigin,
) -> Result<TuneSnapshot, ProfileResolveError> {
    validate_page_size(1, page_1, MEGA144H7_PAGE_1_SIZE)?;
    validate_page_size(2, page_2, MEGA144H7_PAGE_2_SIZE)?;

    TuneSnapshot::new(
        MEGA144H7_PROFILE_GENERATION,
        origin,
        [
            decode_ve_table(page_1, modes)?,
            decode_ignition_table(page_1, modes)?,
            decode_target_mixture_table(page_1, modes)?,
            decode_map_estimate_table(page_1)?,
        ],
    )
    .map_err(ProfileResolveError::InvalidTune)
}

fn decode_ve_table(
    page: &[u8],
    modes: Mega144h7ResolvedModes,
) -> Result<TableSnapshot, ProfileResolveError> {
    table_u16(
        page,
        TableSpec {
            id: TABLE_ID_VE,
            name: "VE Table",
            rows: 32,
            cols: 32,
            value_offset: 16_448,
            x_offset: 18_560,
            y_offset: 18_496,
            value_scale: 0.01,
            x_scale: 1.0,
            y_scale: 1.0,
            minimum: 0.0,
            maximum: 650.0,
            display_decimals: if modes.hundredth_ve { 2 } else { 1 },
            key: "veTable",
        },
    )
}

fn decode_ignition_table(
    page: &[u8],
    modes: Mega144h7ResolvedModes,
) -> Result<TableSnapshot, ProfileResolveError> {
    table_i16(
        page,
        TableSpec {
            id: TABLE_ID_IGNITION,
            name: "Ignition Table",
            rows: 32,
            cols: 32,
            value_offset: 14_272,
            x_offset: 16_384,
            y_offset: 16_320,
            value_scale: 0.01,
            x_scale: 1.0,
            y_scale: 1.0,
            minimum: -90.0,
            maximum: 90.0,
            display_decimals: if modes.hundredth_ignition { 2 } else { 1 },
            key: "ignitionTable",
        },
    )
}

fn decode_target_mixture_table(
    page: &[u8],
    modes: Mega144h7ResolvedModes,
) -> Result<TableSnapshot, ProfileResolveError> {
    let (name, scale, minimum, maximum, decimals) = if modes.lambda_targets {
        (
            "Target Lambda Table",
            0.006_802_721_088_435_374,
            0.6,
            1.5,
            2,
        )
    } else {
        ("Target AFR Table", 0.1, 0.0, 25.0, 1)
    };
    let (minimum, maximum) = representable_bounds(minimum, maximum, scale);
    let rows = 32;
    let cols = 32;

    Ok(TableSnapshot {
        descriptor: descriptor(
            TABLE_ID_TARGET_MIXTURE,
            name,
            rows,
            cols,
            minimum,
            maximum,
            scale,
            decimals,
        ),
        x_axis: read_u8_scaled(page, 19_936, cols, 100.0, "lambdaRpmBins")?,
        y_axis: read_u16_scaled(page, 19_872, rows, 1.0, "lambdaLoadBins")?,
        values: read_u8_scaled(page, 18_848, rows * cols, scale, "lambdaTable")?,
        editable: vec![true; rows * cols],
    })
}

fn decode_map_estimate_table(page: &[u8]) -> Result<TableSnapshot, ProfileResolveError> {
    let rows = 16;
    let cols = 16;

    Ok(TableSnapshot {
        descriptor: descriptor(
            TABLE_ID_MAP_ESTIMATE,
            "MAP Estimate",
            rows,
            cols,
            0.0,
            650.0,
            0.01,
            2,
        ),
        x_axis: read_u8_scaled(page, 13_680, cols, 100.0, "mapEstimateRpmBins")?,
        y_axis: read_u8_scaled(page, 13_664, rows, 0.5, "mapEstimateTpsBins")?,
        values: read_u16_scaled(page, 13_152, rows * cols, 0.01, "mapEstimateTable")?,
        editable: vec![true; rows * cols],
    })
}

#[derive(Clone, Copy)]
struct TableSpec {
    id: u32,
    name: &'static str,
    rows: usize,
    cols: usize,
    value_offset: usize,
    x_offset: usize,
    y_offset: usize,
    value_scale: f64,
    x_scale: f64,
    y_scale: f64,
    minimum: f64,
    maximum: f64,
    display_decimals: u8,
    key: &'static str,
}

fn table_u16(page: &[u8], spec: TableSpec) -> Result<TableSnapshot, ProfileResolveError> {
    let values = read_u16_scaled(
        page,
        spec.value_offset,
        spec.rows * spec.cols,
        spec.value_scale,
        spec.key,
    )?;
    let x_axis = read_u16_scaled(page, spec.x_offset, spec.cols, spec.x_scale, "x axis")?;
    let y_axis = read_u16_scaled(page, spec.y_offset, spec.rows, spec.y_scale, "y axis")?;
    Ok(table_from_parts(spec, values, x_axis, y_axis))
}

fn table_i16(page: &[u8], spec: TableSpec) -> Result<TableSnapshot, ProfileResolveError> {
    let values = read_i16_scaled(
        page,
        spec.value_offset,
        spec.rows * spec.cols,
        spec.value_scale,
        spec.key,
    )?;
    let x_axis = read_u16_scaled(page, spec.x_offset, spec.cols, spec.x_scale, "x axis")?;
    let y_axis = read_u16_scaled(page, spec.y_offset, spec.rows, spec.y_scale, "y axis")?;
    Ok(table_from_parts(spec, values, x_axis, y_axis))
}

fn table_from_parts(
    spec: TableSpec,
    values: Vec<f64>,
    x_axis: Vec<f64>,
    y_axis: Vec<f64>,
) -> TableSnapshot {
    TableSnapshot {
        descriptor: descriptor(
            spec.id,
            spec.name,
            spec.rows,
            spec.cols,
            spec.minimum,
            spec.maximum,
            spec.value_scale,
            spec.display_decimals,
        ),
        x_axis,
        y_axis,
        values,
        editable: vec![true; spec.rows * spec.cols],
    }
}

#[allow(clippy::too_many_arguments)]
fn descriptor(
    id: u32,
    name: &str,
    rows: usize,
    cols: usize,
    minimum: f64,
    maximum: f64,
    quantum: f64,
    display_decimals: u8,
) -> TableDescriptor {
    TableDescriptor {
        id,
        name: name.to_string(),
        rows,
        cols,
        minimum,
        maximum,
        quantum,
        quantum_origin: 0.0,
        edit_increment: quantum,
        display_decimals,
    }
}

fn validate_page_size(
    page_number: u8,
    page: &[u8],
    expected: usize,
) -> Result<(), ProfileResolveError> {
    if page.len() != expected {
        return Err(ProfileResolveError::InvalidPageSize {
            page: page_number,
            expected,
            actual: page.len(),
        });
    }
    Ok(())
}

fn representable_bounds(minimum: f64, maximum: f64, quantum: f64) -> (f64, f64) {
    let first = (minimum / quantum).ceil() * quantum;
    let last = (maximum / quantum).floor() * quantum;
    (first, last)
}

fn read_u8_scaled(
    page: &[u8],
    offset: usize,
    count: usize,
    scale: f64,
    key: &'static str,
) -> Result<Vec<f64>, ProfileResolveError> {
    let end = offset
        .checked_add(count)
        .ok_or(ProfileResolveError::OutOfBounds(key))?;
    let bytes = page
        .get(offset..end)
        .ok_or(ProfileResolveError::OutOfBounds(key))?;
    Ok(bytes
        .iter()
        .map(|value| f64::from(*value) * scale)
        .collect())
}

fn read_u16_scaled(
    page: &[u8],
    offset: usize,
    count: usize,
    scale: f64,
    key: &'static str,
) -> Result<Vec<f64>, ProfileResolveError> {
    read_i16_width(page, offset, count, key, |raw| {
        f64::from(u16::from_le_bytes(raw)) * scale
    })
}

fn read_i16_scaled(
    page: &[u8],
    offset: usize,
    count: usize,
    scale: f64,
    key: &'static str,
) -> Result<Vec<f64>, ProfileResolveError> {
    read_i16_width(page, offset, count, key, |raw| {
        f64::from(i16::from_le_bytes(raw)) * scale
    })
}

fn read_i16_width(
    page: &[u8],
    offset: usize,
    count: usize,
    key: &'static str,
    decode: impl Fn([u8; 2]) -> f64,
) -> Result<Vec<f64>, ProfileResolveError> {
    let byte_count = count
        .checked_mul(2)
        .ok_or(ProfileResolveError::OutOfBounds(key))?;
    let end = offset
        .checked_add(byte_count)
        .ok_or(ProfileResolveError::OutOfBounds(key))?;
    let bytes = page
        .get(offset..end)
        .ok_or(ProfileResolveError::OutOfBounds(key))?;
    Ok(bytes
        .chunks_exact(2)
        .map(|pair| decode([pair[0], pair[1]]))
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tuning::TuneDraft;

    fn pages() -> (Vec<u8>, Vec<u8>) {
        let mut page_1 = vec![0; MEGA144H7_PAGE_1_SIZE];
        page_1[18_848..19_872].fill(147);
        (page_1, vec![0; MEGA144H7_PAGE_2_SIZE])
    }

    fn imported_snapshot(
        page_1: &[u8],
        page_2: &[u8],
        modes: Mega144h7ResolvedModes,
    ) -> Result<TuneSnapshot, ProfileResolveError> {
        resolve_mega144h7_tune_pages(page_1, page_2, modes, TuneSnapshotOrigin::ImportedFile)
    }

    fn put_u8(page: &mut [u8], offset: usize, value: u8) {
        page[offset] = value;
    }

    fn put_u16(page: &mut [u8], offset: usize, value: u16) {
        page[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
    }

    fn put_i16(page: &mut [u8], offset: usize, value: i16) {
        page[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
    }

    #[test]
    fn profile_page_sizes_are_strict() {
        let (mut page_1, page_2) = pages();
        page_1.pop();
        assert_eq!(
            imported_snapshot(&page_1, &page_2, Mega144h7ResolvedModes::default()),
            Err(ProfileResolveError::InvalidPageSize {
                page: 1,
                expected: MEGA144H7_PAGE_1_SIZE,
                actual: MEGA144H7_PAGE_1_SIZE - 1,
            })
        );
    }

    #[test]
    fn resolves_real_profile_tables_into_one_semantic_snapshot() {
        let (mut page_1, page_2) = pages();
        put_u16(&mut page_1, 16_448, 7_825);
        put_u16(&mut page_1, 18_560, 800);
        put_u16(&mut page_1, 18_496, 50);
        put_i16(&mut page_1, 14_272, -1_250);
        put_u16(&mut page_1, 16_384, 1_000);
        put_u16(&mut page_1, 16_320, 100);
        put_u8(&mut page_1, 18_848, 147);
        put_u8(&mut page_1, 19_936, 10);
        put_u16(&mut page_1, 19_872, 75);
        put_u16(&mut page_1, 13_152, 12_345);
        put_u8(&mut page_1, 13_680, 25);
        put_u8(&mut page_1, 13_664, 80);

        let snapshot =
            imported_snapshot(&page_1, &page_2, Mega144h7ResolvedModes::default()).unwrap();

        assert_eq!(snapshot.origin, TuneSnapshotOrigin::ImportedFile);
        assert_eq!(snapshot.profile_generation, MEGA144H7_PROFILE_GENERATION);
        assert_eq!(snapshot.tables.len(), 4);

        let ve = &snapshot.tables[&TABLE_ID_VE];
        assert_eq!((ve.descriptor.rows, ve.descriptor.cols), (32, 32));
        assert_eq!(ve.values[0], 78.25);
        assert_eq!(ve.x_axis[0], 800.0);
        assert_eq!(ve.y_axis[0], 50.0);
        assert!(ve.editable.iter().all(|editable| *editable));

        let ignition = &snapshot.tables[&TABLE_ID_IGNITION];
        assert_eq!(ignition.values[0], -12.5);
        assert_eq!(ignition.x_axis[0], 1_000.0);
        assert_eq!(ignition.y_axis[0], 100.0);

        let target = &snapshot.tables[&TABLE_ID_TARGET_MIXTURE];
        assert_eq!(target.x_axis[0], 1_000.0);
        assert_eq!(target.y_axis[0], 75.0);
        assert!((target.values[0] - 1.0).abs() < 1e-12);

        let map = &snapshot.tables[&TABLE_ID_MAP_ESTIMATE];
        assert_eq!((map.descriptor.rows, map.descriptor.cols), (16, 16));
        assert_eq!(map.values[0], 123.45);
        assert_eq!(map.x_axis[0], 2_500.0);
        assert_eq!(map.y_axis[0], 40.0);
    }

    #[test]
    fn profile_modes_change_presentation_not_integer_storage_quantum() {
        let (page_1, page_2) = pages();
        let snapshot = imported_snapshot(
            &page_1,
            &page_2,
            Mega144h7ResolvedModes {
                hundredth_ve: false,
                hundredth_ignition: false,
                lambda_targets: false,
            },
        )
        .unwrap();

        let ve = &snapshot.tables[&TABLE_ID_VE].descriptor;
        assert_eq!(ve.quantum, 0.01);
        assert_eq!(ve.display_decimals, 1);
        let ignition = &snapshot.tables[&TABLE_ID_IGNITION].descriptor;
        assert_eq!(ignition.quantum, 0.01);
        assert_eq!(ignition.display_decimals, 1);
        let target = &snapshot.tables[&TABLE_ID_TARGET_MIXTURE].descriptor;
        assert_eq!(target.name, "Target AFR Table");
        assert_eq!(target.quantum, 0.1);
        assert_eq!(target.minimum, 0.0);
        assert_eq!(target.maximum, 25.0);
    }

    #[test]
    fn lambda_bounds_are_clamped_to_the_encoded_grid() {
        let (page_1, page_2) = pages();
        let snapshot =
            imported_snapshot(&page_1, &page_2, Mega144h7ResolvedModes::default()).unwrap();
        let target = &snapshot.tables[&TABLE_ID_TARGET_MIXTURE].descriptor;
        assert!(target.minimum >= 0.6);
        assert!(target.maximum <= 1.5);
        let min_steps = target.minimum / target.quantum;
        let max_steps = target.maximum / target.quantum;
        assert!((min_steps - min_steps.round()).abs() < 1e-9);
        assert!((max_steps - max_steps.round()).abs() < 1e-9);
    }

    #[test]
    fn resolved_snapshot_is_accepted_by_existing_tune_draft() {
        let (page_1, page_2) = pages();
        let snapshot =
            imported_snapshot(&page_1, &page_2, Mega144h7ResolvedModes::default()).unwrap();
        let draft = TuneDraft::from_snapshot(&snapshot);
        let projection = draft.table_projection(TABLE_ID_VE).unwrap();
        assert_eq!(projection.descriptor.name, "VE Table");
        assert_eq!(projection.values.len(), 1_024);
        assert_eq!(draft.origin(), TuneSnapshotOrigin::ImportedFile);
    }
}
