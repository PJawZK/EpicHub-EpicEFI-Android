use crate::compiled_ecu_profile::{
    CompiledProfileBuildError, CompiledProfileCompileRequest, CompiledResolvedEcuProfile,
    OwnedSemanticTableBinding, SemanticTableBindingSpec,
};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CompiledProfileImportRequest {
    pub profile_name: String,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<String>,
    pub resolved_definition: String,
    pub semantic_tables: Vec<OwnedSemanticTableBinding>,
}

pub fn compile_imported_profile(
    request: &CompiledProfileImportRequest,
) -> Result<CompiledResolvedEcuProfile, CompiledProfileBuildError> {
    let semantic_tables: Vec<SemanticTableBindingSpec<'_>> = request
        .semantic_tables
        .iter()
        .map(|binding| SemanticTableBindingSpec {
            semantic_id: binding.semantic_id,
            editor_table_id: &binding.editor_table_id,
        })
        .collect();

    CompiledResolvedEcuProfile::compile(CompiledProfileCompileRequest {
        profile_name: &request.profile_name,
        profile_generation: request.profile_generation,
        definition_fingerprint: request.definition_fingerprint.as_deref(),
        resolved_definition: &request.resolved_definition,
        semantic_tables: &semantic_tables,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::compiled_ecu_profile::test_support::{MEGA_BINDINGS, MEGA_COMPILED_DEFINITION};
    use crate::ecu_profile::ResolvedEcuProfile;
    use crate::resolved_profile_source::{
        MEGA144H7_PROFILE_FINGERPRINT, MEGA144H7_PROFILE_GENERATION, MEGA144H7_PROFILE_NAME,
        MEGA144H7_PROFILE_SIGNATURE,
    };

    #[test]
    fn owned_import_compiles_into_real_resolved_profile() {
        let request = CompiledProfileImportRequest {
            profile_name: MEGA144H7_PROFILE_NAME.to_string(),
            profile_generation: MEGA144H7_PROFILE_GENERATION,
            definition_fingerprint: Some(MEGA144H7_PROFILE_FINGERPRINT.to_string()),
            resolved_definition: MEGA_COMPILED_DEFINITION.to_string(),
            semantic_tables: MEGA_BINDINGS
                .iter()
                .map(|binding| OwnedSemanticTableBinding {
                    semantic_id: binding.semantic_id,
                    editor_table_id: binding.editor_table_id.to_string(),
                })
                .collect(),
        };

        let profile = compile_imported_profile(&request).unwrap();
        assert_eq!(profile.identity().profile_name, MEGA144H7_PROFILE_NAME);
        assert_eq!(profile.identity().signature, MEGA144H7_PROFILE_SIGNATURE);
        assert_eq!(
            profile.identity().definition_fingerprint,
            Some(MEGA144H7_PROFILE_FINGERPRINT)
        );
    }

    #[test]
    fn invalid_definition_fails_before_profile_exists() {
        let request = CompiledProfileImportRequest {
            profile_name: "broken".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            resolved_definition: "[TunerStudio]\nsignature = \"broken\"\n".to_string(),
            semantic_tables: Vec::new(),
        };

        assert!(compile_imported_profile(&request).is_err());
    }
}
