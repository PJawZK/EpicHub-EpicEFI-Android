use std::collections::HashSet;

use crate::ecu_calibration::{
    CalibrationCompileError, CalibrationPrimitive, compile_calibration_primitives,
};
use crate::ecu_compiled_snapshot::{SemanticTableBinding, assemble_compiled_snapshot};
use crate::ecu_definition::{
    CompiledEcuDefinition, DefinitionCompileError, DefinitionCompileRequest, compile_definition,
};
use crate::ecu_editor_definition::{
    CompiledEditorDescriptors, EditorCompileError, compile_editor_descriptors,
};
use crate::ecu_page_codec::{
    PageCodecError, PagedCalibrationPrimitive, compile_paged_calibration_primitives,
};
use crate::ecu_profile::{EcuProfileResolveError, ProfileIdentity, ResolvedEcuProfile};
use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct OwnedSemanticTableBinding {
    pub semantic_id: u32,
    pub editor_table_id: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SemanticTableBindingSpec<'a> {
    pub semantic_id: u32,
    pub editor_table_id: &'a str,
}

pub struct CompiledProfileCompileRequest<'a> {
    pub profile_name: &'a str,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<&'a str>,
    pub resolved_definition: &'a str,
    pub semantic_tables: &'a [SemanticTableBindingSpec<'a>],
}

#[derive(Clone, Debug, PartialEq)]
pub enum CompiledProfileBuildError {
    Definition(DefinitionCompileError),
    Calibration(CalibrationCompileError),
    PageCodec(PageCodecError),
    Editor(EditorCompileError),
    DuplicateSemanticId(u32),
    DuplicateEditorBinding(String),
    UnknownEditorBinding(String),
}

impl From<DefinitionCompileError> for CompiledProfileBuildError {
    fn from(value: DefinitionCompileError) -> Self {
        Self::Definition(value)
    }
}

impl From<CalibrationCompileError> for CompiledProfileBuildError {
    fn from(value: CalibrationCompileError) -> Self {
        Self::Calibration(value)
    }
}

impl From<PageCodecError> for CompiledProfileBuildError {
    fn from(value: PageCodecError) -> Self {
        Self::PageCodec(value)
    }
}

impl From<EditorCompileError> for CompiledProfileBuildError {
    fn from(value: EditorCompileError) -> Self {
        Self::Editor(value)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct CompiledResolvedEcuProfile {
    definition: CompiledEcuDefinition,
    calibration: Vec<CalibrationPrimitive>,
    paged_calibration: Vec<PagedCalibrationPrimitive>,
    editors: CompiledEditorDescriptors,
    semantic_tables: Vec<OwnedSemanticTableBinding>,
}

impl CompiledResolvedEcuProfile {
    pub fn compile(
        request: CompiledProfileCompileRequest<'_>,
    ) -> Result<Self, CompiledProfileBuildError> {
        let definition = compile_definition(DefinitionCompileRequest {
            profile_name: request.profile_name,
            profile_generation: request.profile_generation,
            definition_fingerprint: request.definition_fingerprint,
            text: request.resolved_definition,
        })?;
        let calibration = compile_calibration_primitives(request.resolved_definition)?;
        let paged_calibration = compile_paged_calibration_primitives(request.resolved_definition)?;
        let editors = compile_editor_descriptors(request.resolved_definition, &calibration)?;
        let semantic_tables = validate_semantic_bindings(request.semantic_tables, &editors)?;

        Ok(Self {
            definition,
            calibration,
            paged_calibration,
            editors,
            semantic_tables,
        })
    }

    pub fn definition(&self) -> &CompiledEcuDefinition {
        &self.definition
    }

    pub fn calibration(&self) -> &[CalibrationPrimitive] {
        &self.calibration
    }

    pub fn paged_calibration(&self) -> &[PagedCalibrationPrimitive] {
        &self.paged_calibration
    }

    pub fn editors(&self) -> &CompiledEditorDescriptors {
        &self.editors
    }

    pub fn semantic_tables(&self) -> &[OwnedSemanticTableBinding] {
        &self.semantic_tables
    }
}

impl ResolvedEcuProfile for CompiledResolvedEcuProfile {
    fn identity(&self) -> ProfileIdentity<'_> {
        self.definition.identity()
    }

    fn page_sizes(&self) -> &[usize] {
        &self.definition.page_sizes
    }

    fn resolve_pages(
        &self,
        pages: &[&[u8]],
        origin: TuneSnapshotOrigin,
    ) -> Result<TuneSnapshot, EcuProfileResolveError> {
        if pages.len() != self.definition.page_sizes.len() {
            return Err(EcuProfileResolveError::InvalidPageCount {
                expected: self.definition.page_sizes.len(),
                actual: pages.len(),
            });
        }
        for (index, (page, expected)) in pages
            .iter()
            .zip(self.definition.page_sizes.iter())
            .enumerate()
        {
            if page.len() != *expected {
                return Err(EcuProfileResolveError::InvalidPageSize {
                    page: index + 1,
                    expected: *expected,
                    actual: page.len(),
                });
            }
        }

        let bindings: Vec<SemanticTableBinding<'_>> = self
            .semantic_tables
            .iter()
            .map(|binding| SemanticTableBinding {
                semantic_id: binding.semantic_id,
                editor_table_id: &binding.editor_table_id,
            })
            .collect();
        assemble_compiled_snapshot(
            self.definition.profile_generation,
            origin,
            pages,
            self.definition.endianness,
            &self.paged_calibration,
            &self.editors,
            &bindings,
        )
        .map_err(EcuProfileResolveError::Compiled)
    }
}

fn validate_semantic_bindings(
    bindings: &[SemanticTableBindingSpec<'_>],
    editors: &CompiledEditorDescriptors,
) -> Result<Vec<OwnedSemanticTableBinding>, CompiledProfileBuildError> {
    let editor_ids: HashSet<&str> = editors
        .tables
        .iter()
        .map(|table| table.table_id.as_str())
        .collect();
    let mut semantic_ids = HashSet::new();
    let mut bound_editors = HashSet::new();
    let mut result = Vec::with_capacity(bindings.len());

    for binding in bindings {
        if !semantic_ids.insert(binding.semantic_id) {
            return Err(CompiledProfileBuildError::DuplicateSemanticId(
                binding.semantic_id,
            ));
        }
        if !bound_editors.insert(binding.editor_table_id) {
            return Err(CompiledProfileBuildError::DuplicateEditorBinding(
                binding.editor_table_id.to_string(),
            ));
        }
        if !editor_ids.contains(binding.editor_table_id) {
            return Err(CompiledProfileBuildError::UnknownEditorBinding(
                binding.editor_table_id.to_string(),
            ));
        }
        result.push(OwnedSemanticTableBinding {
            semantic_id: binding.semantic_id,
            editor_table_id: binding.editor_table_id.to_string(),
        });
    }
    Ok(result)
}

#[cfg(test)]
pub(crate) mod test_support {
    use super::*;
    use crate::resolved_profile_source::{
        MEGA144H7_PROFILE_FINGERPRINT, MEGA144H7_PROFILE_GENERATION, MEGA144H7_PROFILE_NAME,
        TABLE_ID_IGNITION, TABLE_ID_MAP_ESTIMATE, TABLE_ID_TARGET_MIXTURE, TABLE_ID_VE,
    };

    pub(crate) const MEGA_COMPILED_DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 2
pageSize = 55764, 5000
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

    pub(crate) const MEGA_BINDINGS: &[SemanticTableBindingSpec<'static>] = &[
        SemanticTableBindingSpec {
            semantic_id: TABLE_ID_VE,
            editor_table_id: "veTableTbl",
        },
        SemanticTableBindingSpec {
            semantic_id: TABLE_ID_IGNITION,
            editor_table_id: "ignitionTableTbl",
        },
        SemanticTableBindingSpec {
            semantic_id: TABLE_ID_TARGET_MIXTURE,
            editor_table_id: "lambdaTableTbl",
        },
        SemanticTableBindingSpec {
            semantic_id: TABLE_ID_MAP_ESTIMATE,
            editor_table_id: "mapEstimateTableTbl",
        },
    ];

    pub(crate) fn mega_profile() -> CompiledResolvedEcuProfile {
        CompiledResolvedEcuProfile::compile(CompiledProfileCompileRequest {
            profile_name: MEGA144H7_PROFILE_NAME,
            profile_generation: MEGA144H7_PROFILE_GENERATION,
            definition_fingerprint: Some(MEGA144H7_PROFILE_FINGERPRINT),
            resolved_definition: MEGA_COMPILED_DEFINITION,
            semantic_tables: MEGA_BINDINGS,
        })
        .unwrap()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::compiled_ecu_profile::test_support::{MEGA_COMPILED_DEFINITION, mega_profile};
    use crate::ecu_profile::{ResolvedEcuProfile, find_exact_profile};
    use crate::resolved_profile_source::{
        MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, MEGA144H7_PROFILE_SIGNATURE,
        Mega144h7ResolvedModes, resolve_mega144h7_tune_pages,
    };

    fn valid_pages() -> (Vec<u8>, Vec<u8>) {
        let mut page_1 = vec![0_u8; MEGA144H7_PAGE_1_SIZE];
        page_1[18_848..19_872].fill(147);
        page_1[16_448..16_450].copy_from_slice(&7_825_u16.to_le_bytes());
        page_1[18_560..18_562].copy_from_slice(&800_u16.to_le_bytes());
        page_1[18_496..18_498].copy_from_slice(&50_u16.to_le_bytes());
        page_1[14_272..14_274].copy_from_slice(&(-1_250_i16).to_le_bytes());
        page_1[16_384..16_386].copy_from_slice(&1_000_u16.to_le_bytes());
        page_1[16_320..16_322].copy_from_slice(&100_u16.to_le_bytes());
        page_1[18_848] = 147;
        page_1[19_936] = 10;
        page_1[19_872..19_874].copy_from_slice(&75_u16.to_le_bytes());
        page_1[13_152..13_154].copy_from_slice(&12_345_u16.to_le_bytes());
        page_1[13_680] = 25;
        page_1[13_664] = 80;
        (page_1, vec![0_u8; MEGA144H7_PAGE_2_SIZE])
    }

    #[test]
    fn compiled_profile_is_exact_signature_authority() {
        let profile = mega_profile();
        let profiles: [&dyn ResolvedEcuProfile; 1] = [&profile];
        assert!(find_exact_profile(MEGA144H7_PROFILE_SIGNATURE, profiles).is_some());
        let profiles: [&dyn ResolvedEcuProfile; 1] = [&profile];
        assert!(find_exact_profile("rusEFI master.2026.08.26.MEGA144H7", profiles).is_none());
    }

    #[test]
    fn compiled_profile_resolves_same_mega_snapshot_as_compatibility_oracle() {
        let profile = mega_profile();
        let (page_1, page_2) = valid_pages();
        let compiled = profile
            .resolve_pages(&[&page_1, &page_2], TuneSnapshotOrigin::ImportedFile)
            .unwrap();
        let oracle = resolve_mega144h7_tune_pages(
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
            TuneSnapshotOrigin::ImportedFile,
        )
        .unwrap();
        assert_eq!(compiled, oracle);
    }

    #[test]
    fn compiled_profile_rejects_wrong_page_size_before_snapshot_decode() {
        let profile = mega_profile();
        let page_1 = vec![0_u8; MEGA144H7_PAGE_1_SIZE - 1];
        let page_2 = vec![0_u8; MEGA144H7_PAGE_2_SIZE];
        assert_eq!(
            profile.resolve_pages(&[&page_1, &page_2], TuneSnapshotOrigin::ImportedFile),
            Err(EcuProfileResolveError::InvalidPageSize {
                page: 1,
                expected: MEGA144H7_PAGE_1_SIZE,
                actual: MEGA144H7_PAGE_1_SIZE - 1,
            })
        );
    }

    #[test]
    fn compile_rejects_unknown_semantic_editor_binding() {
        let bad_binding = [SemanticTableBindingSpec {
            semantic_id: 42,
            editor_table_id: "missingTable",
        }];
        assert_eq!(
            CompiledResolvedEcuProfile::compile(CompiledProfileCompileRequest {
                profile_name: "bad",
                profile_generation: 1,
                definition_fingerprint: None,
                resolved_definition: MEGA_COMPILED_DEFINITION,
                semantic_tables: &bad_binding,
            }),
            Err(CompiledProfileBuildError::UnknownEditorBinding(
                "missingTable".to_string()
            ))
        );
    }
}
