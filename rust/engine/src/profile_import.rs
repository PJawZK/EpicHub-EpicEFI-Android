use crate::compiled_ecu_profile::{
    CompiledProfileBuildError, CompiledProfileCompileRequest, CompiledResolvedEcuProfile,
    SemanticTableBindingSpec,
};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct OwnedSemanticTableBindingSpec {
    pub semantic_id: u32,
    pub editor_table_id: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct OwnedCompiledProfileImport {
    pub profile_name: String,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<String>,
    pub resolved_definition: String,
    pub semantic_tables: Vec<OwnedSemanticTableBindingSpec>,
}

pub fn compile_owned_profile_import(
    import: &OwnedCompiledProfileImport,
) -> Result<CompiledResolvedEcuProfile, CompiledProfileBuildError> {
    let semantic_tables: Vec<SemanticTableBindingSpec<'_>> = import
        .semantic_tables
        .iter()
        .map(|binding| SemanticTableBindingSpec {
            semantic_id: binding.semantic_id,
            editor_table_id: &binding.editor_table_id,
        })
        .collect();

    CompiledResolvedEcuProfile::compile(CompiledProfileCompileRequest {
        profile_name: &import.profile_name,
        profile_generation: import.profile_generation,
        definition_fingerprint: import.definition_fingerprint.as_deref(),
        resolved_definition: &import.resolved_definition,
        semantic_tables: &semantic_tables,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile::ResolvedEcuProfile;

    const SIMPLE_DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI generic.import.100"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 32
page = 1
rpmBins = array, U16, 0, [2], "RPM", 1, 0, 0, 10000, 0
loadBins = array, U08, 4, [2], "Load", 1, 0, 0, 255, 0
values = array, U08, 6, [2x2], "%", 1, 0, 0, 255, 0

[TableEditor]
table = genericTable, genericMap, "Generic Table", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#;

    fn import() -> OwnedCompiledProfileImport {
        OwnedCompiledProfileImport {
            profile_name: "generic-import".to_string(),
            profile_generation: 100,
            definition_fingerprint: Some("fingerprint-100".to_string()),
            resolved_definition: SIMPLE_DEFINITION.to_string(),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42_001,
                editor_table_id: "genericTable".to_string(),
            }],
        }
    }

    #[test]
    fn owned_import_compiles_without_borrowing_the_callers_strings() {
        let profile = {
            let request = import();
            compile_owned_profile_import(&request).unwrap()
        };

        assert_eq!(profile.identity().profile_name, "generic-import");
        assert_eq!(profile.identity().signature, "rusEFI generic.import.100");
        assert_eq!(profile.identity().profile_generation, 100);
        assert_eq!(profile.identity().definition_fingerprint, Some("fingerprint-100"));
        assert_eq!(profile.page_sizes(), &[32]);
        assert_eq!(profile.semantic_tables()[0].semantic_id, 42_001);
        assert_eq!(profile.semantic_tables()[0].editor_table_id, "genericTable");
    }

    #[test]
    fn invalid_semantic_binding_fails_before_a_profile_can_be_registered() {
        let mut request = import();
        request.semantic_tables[0].editor_table_id = "missingTable".to_string();
        assert!(matches!(
            compile_owned_profile_import(&request),
            Err(CompiledProfileBuildError::UnknownEditorBinding(binding)) if binding == "missingTable"
        ));
    }
}
