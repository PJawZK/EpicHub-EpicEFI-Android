use std::collections::HashMap;

use crate::ecu_calibration::{CalibrationPrimitive, CalibrationShape, StorageType};
use crate::ecu_definition::Endianness;
use crate::ecu_editor_definition::{CompiledEditorDescriptors, TableDescriptor as EditorTableDescriptor};
use crate::ecu_page_codec::{PageCodecError, PagedCalibrationPrimitive, decode_primitive};
use crate::tuning::{
    TableDescriptor, TableSnapshot, TuneError, TuneSnapshot, TuneSnapshotOrigin,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SemanticTableBinding<'a> {
    pub semantic_id: u32,
    pub editor_table_id: &'a str,
}

#[derive(Clone, Debug, PartialEq)]
pub enum CompiledSnapshotError {
    UnknownEditorTable(String),
    UnknownPrimitive(String),
    PrimitivePageMismatch {
        table: String,
        primitive: String,
        expected: usize,
        actual: usize,
    },
    InvalidValueShape(String),
    MissingMinimum(String),
    MissingMaximum(String),
    MissingDecimals(String),
    NonDiscreteValueStorage(String),
    InvalidRepresentableRange(String),
    PageCodec(PageCodecError),
    InvalidTune(TuneError),
}

impl From<PageCodecError> for CompiledSnapshotError {
    fn from(value: PageCodecError) -> Self {
        Self::PageCodec(value)
    }
}

impl From<TuneError> for CompiledSnapshotError {
    fn from(value: TuneError) -> Self {
        Self::InvalidTune(value)
    }
}

#[allow(clippy::too_many_arguments)]
pub fn assemble_compiled_snapshot(
    profile_generation: u64,
    origin: TuneSnapshotOrigin,
    pages: &[&[u8]],
    endianness: Endianness,
    primitives: &[PagedCalibrationPrimitive],
    editors: &CompiledEditorDescriptors,
    bindings: &[SemanticTableBinding<'_>],
) -> Result<TuneSnapshot, CompiledSnapshotError> {
    let primitive_map: HashMap<&str, &PagedCalibrationPrimitive> = primitives
        .iter()
        .map(|entry| (entry.primitive.name.as_str(), entry))
        .collect();
    let editor_map: HashMap<&str, &EditorTableDescriptor> = editors
        .tables
        .iter()
        .map(|table| (table.table_id.as_str(), table))
        .collect();

    let mut tables = Vec::with_capacity(bindings.len());
    for binding in bindings {
        let editor = editor_map
            .get(binding.editor_table_id)
            .copied()
            .ok_or_else(|| {
                CompiledSnapshotError::UnknownEditorTable(binding.editor_table_id.to_string())
            })?;
        tables.push(assemble_table(
            binding.semantic_id,
            editor,
            pages,
            endianness,
            &primitive_map,
        )?);
    }

    TuneSnapshot::new(profile_generation, origin, tables).map_err(CompiledSnapshotError::InvalidTune)
}

fn assemble_table(
    semantic_id: u32,
    editor: &EditorTableDescriptor,
    pages: &[&[u8]],
    endianness: Endianness,
    primitive_map: &HashMap<&str, &PagedCalibrationPrimitive>,
) -> Result<TableSnapshot, CompiledSnapshotError> {
    let x = lookup_primitive(&editor.x_bins.primitive_name, primitive_map)?;
    let y = lookup_primitive(&editor.y_bins.primitive_name, primitive_map)?;
    let z = lookup_primitive(&editor.z_bins, primitive_map)?;

    if let Some(expected_page) = editor.page {
        validate_page(editor, x, expected_page)?;
        validate_page(editor, y, expected_page)?;
        validate_page(editor, z, expected_page)?;
    }

    let cols = one_dimensional_len(&x.primitive)?;
    let rows = one_dimensional_len(&y.primitive)?;
    let expected_shape = vec![cols, rows];
    if z.primitive.shape != CalibrationShape::Array(expected_shape) {
        return Err(CompiledSnapshotError::InvalidValueShape(
            z.primitive.name.clone(),
        ));
    }

    let minimum = z
        .primitive
        .minimum
        .ok_or_else(|| CompiledSnapshotError::MissingMinimum(z.primitive.name.clone()))?;
    let maximum = z
        .primitive
        .maximum
        .ok_or_else(|| CompiledSnapshotError::MissingMaximum(z.primitive.name.clone()))?;
    let display_decimals = z
        .primitive
        .decimals
        .ok_or_else(|| CompiledSnapshotError::MissingDecimals(z.primitive.name.clone()))?;
    let (minimum, maximum, quantum, quantum_origin) =
        representable_descriptor_bounds(&z.primitive, minimum, maximum)?;

    Ok(TableSnapshot {
        descriptor: TableDescriptor {
            id: semantic_id,
            name: editor.title.clone(),
            rows,
            cols,
            minimum,
            maximum,
            quantum,
            quantum_origin,
            edit_increment: quantum,
            display_decimals,
        },
        x_axis: decode_primitive(pages, x, endianness)?,
        y_axis: decode_primitive(pages, y, endianness)?,
        values: decode_primitive(pages, z, endianness)?,
        editable: vec![true; rows * cols],
    })
}

fn lookup_primitive<'a>(
    name: &str,
    primitive_map: &HashMap<&str, &'a PagedCalibrationPrimitive>,
) -> Result<&'a PagedCalibrationPrimitive, CompiledSnapshotError> {
    primitive_map
        .get(name)
        .copied()
        .ok_or_else(|| CompiledSnapshotError::UnknownPrimitive(name.to_string()))
}

fn validate_page(
    editor: &EditorTableDescriptor,
    primitive: &PagedCalibrationPrimitive,
    expected: usize,
) -> Result<(), CompiledSnapshotError> {
    if primitive.page != expected {
        return Err(CompiledSnapshotError::PrimitivePageMismatch {
            table: editor.table_id.clone(),
            primitive: primitive.primitive.name.clone(),
            expected,
            actual: primitive.page,
        });
    }
    Ok(())
}

fn one_dimensional_len(primitive: &CalibrationPrimitive) -> Result<usize, CompiledSnapshotError> {
    match &primitive.shape {
        CalibrationShape::Array(dimensions) if dimensions.len() == 1 => Ok(dimensions[0]),
        _ => Err(CompiledSnapshotError::InvalidValueShape(
            primitive.name.clone(),
        )),
    }
}

fn representable_descriptor_bounds(
    primitive: &CalibrationPrimitive,
    declared_minimum: f64,
    declared_maximum: f64,
) -> Result<(f64, f64, f64, f64), CompiledSnapshotError> {
    let (raw_minimum, raw_maximum) = match primitive.storage_type {
        StorageType::U08 => (0.0, f64::from(u8::MAX)),
        StorageType::S08 => (f64::from(i8::MIN), f64::from(i8::MAX)),
        StorageType::U16 => (0.0, f64::from(u16::MAX)),
        StorageType::S16 => (f64::from(i16::MIN), f64::from(i16::MAX)),
        StorageType::U32 => (0.0, f64::from(u32::MAX)),
        StorageType::S32 => (f64::from(i32::MIN), f64::from(i32::MAX)),
        StorageType::F32 => {
            return Err(CompiledSnapshotError::NonDiscreteValueStorage(
                primitive.name.clone(),
            ));
        }
    };
    let quantum = primitive.scale.abs();
    if !quantum.is_finite() || quantum == 0.0 || !primitive.translate.is_finite() {
        return Err(CompiledSnapshotError::InvalidRepresentableRange(
            primitive.name.clone(),
        ));
    }

    let endpoint_a = raw_minimum * primitive.scale + primitive.translate;
    let endpoint_b = raw_maximum * primitive.scale + primitive.translate;
    let storage_minimum = endpoint_a.min(endpoint_b);
    let storage_maximum = endpoint_a.max(endpoint_b);
    let requested_minimum = declared_minimum.max(storage_minimum);
    let requested_maximum = declared_maximum.min(storage_maximum);
    let origin = primitive.translate;
    let minimum = ((requested_minimum - origin) / quantum).ceil() * quantum + origin;
    let maximum = ((requested_maximum - origin) / quantum).floor() * quantum + origin;
    if !minimum.is_finite() || !maximum.is_finite() || minimum > maximum {
        return Err(CompiledSnapshotError::InvalidRepresentableRange(
            primitive.name.clone(),
        ));
    }
    Ok((minimum, maximum, quantum, origin))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_calibration::compile_calibration_primitives;
    use crate::ecu_editor_definition::compile_editor_descriptors;
    use crate::ecu_page_codec::compile_paged_calibration_primitives;
    use crate::resolved_profile_source::{
        MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, MEGA144H7_PROFILE_GENERATION,
        Mega144h7ResolvedModes, TABLE_ID_IGNITION, TABLE_ID_MAP_ESTIMATE,
        TABLE_ID_TARGET_MIXTURE, TABLE_ID_VE, resolve_mega144h7_tune_pages,
    };

    const MEGA_TABLE_DEFINITION: &str = r#"
[Constants]
page = 2
luaScript = string, ASCII, 0, 5000
page = 1
mapEstimateTable = array, U16, 13152, [16x16], "kPa", 0.01, 0, 0, 650, 2
mapEstimateTpsBins = array, U08, 13664, [16], "% TPS", 0.5, 0, 0, 100, 1
mapEstimateRpmBins = array, U08, 13680, [16], "RPM", 100.0, 0, 0, 25000, 0
ignitionTable = array, S16, 14272, [32x32], "deg", 0.01, 0, -90, 90, 2
ignitionLoadBins = array, U16, 16320, [32], "Load", 1, 0, 0, 650, 0
ignitionRpmBins = array, U16, 16384, [32], "RPM", 1, 0, 0, 32000, 0
veTable = array, U16, 16448, [32x32], "%", 0.01, 0, 0, 650, 2
veLoadBins = array, U16, 18496, [32], "Load", 1, 0, 0, 650, 0
veRpmBins = array, U16, 18560, [32], "RPM", 1, 0, 0, 32000, 0
lambdaTable = array, U08, 18848, [32x32], "lambda", 0.006802721088435374, 0, 0.6, 1.5, 2
lambdaLoadBins = array, U16, 19872, [32], "", 1, 0, 0, 1000, 0
lambdaRpmBins = array, U08, 19936, [32], "RPM", 100.0, 0, 0, 25000, 0

[TableEditor]
table = ignitionTableTbl, ignitionTableMap, "Ignition Table", 1
    xBins = ignitionRpmBins, rpmForIgnitionTableDot
    yBins = ignitionLoadBins, loadForIgnitionTableDot
    zBins = ignitionTable

table = veTableTbl, veTableMap, "VE Table", 1
    xBins = veRpmBins, RPMValue
    yBins = veLoadBins, veTableYAxis
    zBins = veTable

table = mapEstimateTableTbl, mapEstimateTableMap, "MAP Estimate", 1
    xBins = mapEstimateRpmBins, RPMValue
    yBins = mapEstimateTpsBins, TPSValue
    zBins = mapEstimateTable

table = lambdaTableTbl, lambdaTableMap, "Target Lambda Table", 1
    xBins = lambdaRpmBins, RPMValue
    yBins = lambdaLoadBins, afrTableYAxis
    zBins = lambdaTable
"#;

    const MEGA_BINDINGS: &[SemanticTableBinding<'static>] = &[
        SemanticTableBinding {
            semantic_id: TABLE_ID_VE,
            editor_table_id: "veTableTbl",
        },
        SemanticTableBinding {
            semantic_id: TABLE_ID_IGNITION,
            editor_table_id: "ignitionTableTbl",
        },
        SemanticTableBinding {
            semantic_id: TABLE_ID_TARGET_MIXTURE,
            editor_table_id: "lambdaTableTbl",
        },
        SemanticTableBinding {
            semantic_id: TABLE_ID_MAP_ESTIMATE,
            editor_table_id: "mapEstimateTableTbl",
        },
    ];

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
    fn compiled_mega_tables_match_qualified_hand_coded_snapshot() {
        let mut page_1 = vec![0_u8; MEGA144H7_PAGE_1_SIZE];
        let page_2 = vec![0_u8; MEGA144H7_PAGE_2_SIZE];
        page_1[18_848..19_872].fill(147);
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

        let calibration = compile_calibration_primitives(MEGA_TABLE_DEFINITION).unwrap();
        let paged = compile_paged_calibration_primitives(MEGA_TABLE_DEFINITION).unwrap();
        let editors = compile_editor_descriptors(MEGA_TABLE_DEFINITION, &calibration).unwrap();
        let compiled = assemble_compiled_snapshot(
            MEGA144H7_PROFILE_GENERATION,
            TuneSnapshotOrigin::ImportedFile,
            &[&page_1, &page_2],
            Endianness::Little,
            &paged,
            &editors,
            MEGA_BINDINGS,
        )
        .unwrap();
        let reference = resolve_mega144h7_tune_pages(
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
            TuneSnapshotOrigin::ImportedFile,
        )
        .unwrap();

        assert_eq!(compiled, reference);
    }

    #[test]
    fn descriptor_page_mismatch_fails_closed() {
        let definition = r#"
[Constants]
page = 2
x = array, U08, 0, [2], "", 1, 0, 0, 255, 0
y = array, U08, 2, [2], "", 1, 0, 0, 255, 0
z = array, U08, 4, [2x2], "", 1, 0, 0, 255, 0
[TableEditor]
table = testTbl, testMap, "Test", 1
    xBins = x
    yBins = y
    zBins = z
"#;
        let calibration = compile_calibration_primitives(definition).unwrap();
        let paged = compile_paged_calibration_primitives(definition).unwrap();
        let editors = compile_editor_descriptors(definition, &calibration).unwrap();
        let binding = [SemanticTableBinding {
            semantic_id: 77,
            editor_table_id: "testTbl",
        }];
        assert_eq!(
            assemble_compiled_snapshot(
                1,
                TuneSnapshotOrigin::ImportedFile,
                &[&[0_u8; 8], &[0_u8; 8]],
                Endianness::Little,
                &paged,
                &editors,
                &binding,
            ),
            Err(CompiledSnapshotError::PrimitivePageMismatch {
                table: "testTbl".to_string(),
                primitive: "x".to_string(),
                expected: 1,
                actual: 2,
            })
        );
    }
}
