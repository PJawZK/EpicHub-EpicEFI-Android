use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ecu_page_codec::{PageCodecError, encode_primitive};
use crate::tune_source_image::TuneSourcePageImage;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageWriteCapabilities {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub value_write: bool,
    pub chunk_write: bool,
    pub crc32_check: bool,
    pub burn: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageBytePatch {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub offset: usize,
    pub before: Vec<u8>,
    pub after: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SemanticTablePatchPlan {
    pub semantic_table_id: u32,
    pub patches: Vec<PageBytePatch>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum WriteReconciliationPlanError {
    SourcePageCountMismatch {
        expected: usize,
        actual: usize,
    },
    SourcePageSizeMismatch {
        page: usize,
        expected: usize,
        actual: usize,
    },
    UnknownSemanticTable(u32),
    MissingEditorDescriptor(String),
    MissingValuePrimitive(String),
    MissingChunkWriteCapability {
        page: usize,
    },
    Codec(PageCodecError),
}

impl From<PageCodecError> for WriteReconciliationPlanError {
    fn from(value: PageCodecError) -> Self {
        Self::Codec(value)
    }
}

pub fn audit_write_capabilities(bundle: &CompiledEcuProfileBundle) -> Vec<PageWriteCapabilities> {
    bundle
        .protocol
        .pages
        .iter()
        .enumerate()
        .map(|(index, page)| PageWriteCapabilities {
            page: index + 1,
            value_write: page.value_write_command.is_some(),
            chunk_write: page.chunk_write_command.is_some(),
            crc32_check: page.crc32_check_command.is_some(),
            burn: page.burn_command.is_some(),
        })
        .collect()
}

pub fn plan_semantic_table_value_patch(
    bundle: &CompiledEcuProfileBundle,
    source: &TuneSourcePageImage,
    semantic_table_id: u32,
    values: &[f64],
) -> Result<SemanticTablePatchPlan, WriteReconciliationPlanError> {
    validate_source_geometry(bundle, source)?;

    let binding = bundle
        .profile
        .semantic_tables()
        .iter()
        .find(|binding| binding.semantic_id == semantic_table_id)
        .ok_or(WriteReconciliationPlanError::UnknownSemanticTable(
            semantic_table_id,
        ))?;
    let editor = bundle
        .profile
        .editors()
        .tables
        .iter()
        .find(|table| table.table_id == binding.editor_table_id)
        .ok_or_else(|| {
            WriteReconciliationPlanError::MissingEditorDescriptor(binding.editor_table_id.clone())
        })?;
    let paged = bundle
        .profile
        .paged_calibration()
        .iter()
        .find(|paged| paged.primitive.name == editor.z_bins)
        .ok_or_else(|| {
            WriteReconciliationPlanError::MissingValuePrimitive(editor.z_bins.clone())
        })?;

    let page_index = paged.page - 1;
    let protocol = &bundle.protocol.pages[page_index];
    if protocol.chunk_write_command.is_none() {
        return Err(WriteReconciliationPlanError::MissingChunkWriteCapability { page: paged.page });
    }

    let before_pages = source.pages().to_vec();
    let mut after_pages = before_pages.clone();
    let mut mutable_pages: Vec<&mut [u8]> = after_pages.iter_mut().map(Vec::as_mut_slice).collect();
    encode_primitive(
        &mut mutable_pages,
        paged,
        bundle.profile.definition().endianness,
        values,
    )?;

    let patches = diff_pages(&before_pages, &after_pages);
    Ok(SemanticTablePatchPlan {
        semantic_table_id,
        patches,
    })
}

fn validate_source_geometry(
    bundle: &CompiledEcuProfileBundle,
    source: &TuneSourcePageImage,
) -> Result<(), WriteReconciliationPlanError> {
    let expected = &bundle.profile.definition().page_sizes;
    if source.page_count() != expected.len() {
        return Err(WriteReconciliationPlanError::SourcePageCountMismatch {
            expected: expected.len(),
            actual: source.page_count(),
        });
    }
    for (index, expected_size) in expected.iter().copied().enumerate() {
        let actual = source.page(index).map_or(0, <[u8]>::len);
        if actual != expected_size {
            return Err(WriteReconciliationPlanError::SourcePageSizeMismatch {
                page: index + 1,
                expected: expected_size,
                actual,
            });
        }
    }
    Ok(())
}

fn diff_pages(before_pages: &[Vec<u8>], after_pages: &[Vec<u8>]) -> Vec<PageBytePatch> {
    let mut result = Vec::new();
    for (page_index, (before, after)) in before_pages.iter().zip(after_pages).enumerate() {
        let mut offset = 0usize;
        while offset < before.len() {
            if before[offset] == after[offset] {
                offset += 1;
                continue;
            }
            let start = offset;
            while offset < before.len() && before[offset] != after[offset] {
                offset += 1;
            }
            result.push(PageBytePatch {
                page: page_index + 1,
                offset: start,
                before: before[start..offset].to_vec(),
                after: after[start..offset].to_vec(),
            });
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::profile_import::OwnedSemanticTableBindingSpec;

    const DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI write-plan.generic.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 24
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
pageValueWrite = "W%2o%v"
pageChunkWrite = "C%2o%2c%v"
crc32CheckCommand = "k%2o%2c"
burnCommand = "B"
page = 1
rpmBins = array, U16, 0, [2], "RPM", 1, 0, 0, 10000, 0
loadBins = array, U08, 4, [2], "Load", 1, 0, 0, 255, 0
values = array, U16, 6, [2x2], "%", 1, 0, 0, 1000, 0

[TableEditor]
table = valuesTable, valuesMap, "Values", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#;

    fn bundle(definition: &str) -> CompiledEcuProfileBundle {
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "write-plan".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition.to_string(),
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42,
                editor_table_id: "valuesTable".to_string(),
            }],
        })
        .unwrap()
    }

    fn source_page() -> Vec<u8> {
        let mut page = vec![0xA5; 24];
        page[0..2].copy_from_slice(&1000u16.to_le_bytes());
        page[2..4].copy_from_slice(&2000u16.to_le_bytes());
        page[4] = 10;
        page[5] = 20;
        for (index, value) in [100u16, 200, 300, 400].into_iter().enumerate() {
            let offset = 6 + index * 2;
            page[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
        }
        page
    }

    #[test]
    fn capability_audit_reports_definition_owned_write_features() {
        let bundle = bundle(DEFINITION);
        assert_eq!(
            audit_write_capabilities(&bundle),
            vec![PageWriteCapabilities {
                page: 1,
                value_write: true,
                chunk_write: true,
                crc32_check: true,
                burn: true,
            }]
        );
    }

    #[test]
    fn patch_plan_changes_only_codec_owned_value_bytes() {
        let bundle = bundle(DEFINITION);
        let source_bytes = source_page();
        let source = TuneSourcePageImage::from_pages(&[&source_bytes]);

        let plan =
            plan_semantic_table_value_patch(&bundle, &source, 42, &[101.0, 200.0, 300.0, 401.0])
                .unwrap();

        assert_eq!(source.page(0), Some(source_bytes.as_slice()));
        assert_eq!(plan.semantic_table_id, 42);
        assert_eq!(
            plan.patches,
            vec![
                PageBytePatch {
                    page: 1,
                    offset: 6,
                    before: vec![100],
                    after: vec![101],
                },
                PageBytePatch {
                    page: 1,
                    offset: 12,
                    before: vec![144],
                    after: vec![145],
                },
            ]
        );
        assert_eq!(source.page(0).unwrap()[14..], source_bytes[14..]);
    }

    #[test]
    fn unchanged_values_produce_no_ram_patch() {
        let bundle = bundle(DEFINITION);
        let source_bytes = source_page();
        let source = TuneSourcePageImage::from_pages(&[&source_bytes]);

        let plan =
            plan_semantic_table_value_patch(&bundle, &source, 42, &[100.0, 200.0, 300.0, 400.0])
                .unwrap();

        assert!(plan.patches.is_empty());
    }

    #[test]
    fn missing_chunk_write_capability_fails_closed() {
        let bundle = bundle(&DEFINITION.replace("pageChunkWrite = \"C%2o%2c%v\"\n", ""));
        let source_bytes = source_page();
        let source = TuneSourcePageImage::from_pages(&[&source_bytes]);

        assert_eq!(
            plan_semantic_table_value_patch(&bundle, &source, 42, &[100.0, 200.0, 300.0, 401.0]),
            Err(WriteReconciliationPlanError::MissingChunkWriteCapability { page: 1 })
        );
    }

    #[test]
    fn invalid_source_geometry_fails_before_encoding() {
        let bundle = bundle(DEFINITION);
        let short_page = vec![0u8; 23];
        let source = TuneSourcePageImage::from_pages(&[&short_page]);

        assert_eq!(
            plan_semantic_table_value_patch(&bundle, &source, 42, &[100.0, 200.0, 300.0, 401.0]),
            Err(WriteReconciliationPlanError::SourcePageSizeMismatch {
                page: 1,
                expected: 24,
                actual: 23,
            })
        );
    }

    #[test]
    fn codec_range_validation_is_reused_for_write_planning() {
        let bundle = bundle(DEFINITION);
        let source_bytes = source_page();
        let source = TuneSourcePageImage::from_pages(&[&source_bytes]);

        assert!(matches!(
            plan_semantic_table_value_patch(&bundle, &source, 42, &[100.0, 200.0, 300.0, 5000.0],),
            Err(WriteReconciliationPlanError::Codec(
                PageCodecError::OutsideDeclaredRange { .. }
            ))
        ));
    }

    #[test]
    fn unknown_semantic_table_cannot_gain_write_authority() {
        let bundle = bundle(DEFINITION);
        let source_bytes = source_page();
        let source = TuneSourcePageImage::from_pages(&[&source_bytes]);

        assert_eq!(
            plan_semantic_table_value_patch(&bundle, &source, 99, &[100.0, 200.0, 300.0, 401.0]),
            Err(WriteReconciliationPlanError::UnknownSemanticTable(99))
        );
    }
}
