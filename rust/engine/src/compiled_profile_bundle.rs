use std::collections::{HashMap, HashSet};

use crate::compiled_ecu_profile::CompiledResolvedEcuProfile;
use crate::ecu_definition_resolver::{
    DefinitionResolveError, DefinitionResolveRequest, ResolvedDefinition, resolve_definition,
};
use crate::ecu_profile::ResolvedEcuProfile;
use crate::ecu_protocol::{CompiledProtocol, ProtocolCompileError, compile_protocol};
use crate::profile_import::{
    OwnedCompiledProfileImport, OwnedSemanticTableBindingSpec, compile_owned_profile_import,
};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct OwnedDefinitionBundleImport {
    pub profile_name: String,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<String>,
    pub root_definition: String,
    pub enabled_symbols: HashSet<String>,
    pub includes: HashMap<String, String>,
    pub semantic_tables: Vec<OwnedSemanticTableBindingSpec>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CompiledEcuProfileBundle {
    pub resolved_definition: ResolvedDefinition,
    pub profile: CompiledResolvedEcuProfile,
    pub protocol: CompiledProtocol,
}

#[derive(Clone, Debug, PartialEq)]
pub enum CompiledProfileBundleError {
    Resolve(DefinitionResolveError),
    Profile(crate::compiled_ecu_profile::CompiledProfileBuildError),
    Protocol(ProtocolCompileError),
    PageCountMismatch {
        profile_pages: usize,
        protocol_pages: usize,
    },
}

pub fn compile_definition_bundle(
    import: &OwnedDefinitionBundleImport,
) -> Result<CompiledEcuProfileBundle, CompiledProfileBundleError> {
    let resolved_definition = resolve_definition(DefinitionResolveRequest {
        text: &import.root_definition,
        enabled_symbols: &import.enabled_symbols,
        includes: &import.includes,
    })
    .map_err(CompiledProfileBundleError::Resolve)?;

    let profile_import = OwnedCompiledProfileImport {
        profile_name: import.profile_name.clone(),
        profile_generation: import.profile_generation,
        definition_fingerprint: import.definition_fingerprint.clone(),
        resolved_definition: resolved_definition.text.clone(),
        semantic_tables: import.semantic_tables.clone(),
    };
    let profile = compile_owned_profile_import(&profile_import)
        .map_err(CompiledProfileBundleError::Profile)?;
    let protocol = compile_protocol(&resolved_definition.text)
        .map_err(CompiledProfileBundleError::Protocol)?;

    let profile_pages = profile.page_sizes().len();
    let protocol_pages = protocol.pages.len();
    if profile_pages != protocol_pages {
        return Err(CompiledProfileBundleError::PageCountMismatch {
            profile_pages,
            protocol_pages,
        });
    }

    Ok(CompiledEcuProfileBundle {
        resolved_definition,
        profile,
        protocol,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const ROOT: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI bundle.generic.900"

#include "constants.inc"
"#;

    const CONSTANTS: &str = r#"
[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 32
pageIdentifier = "\x00\x00"
#if INDEXED_READ
pageReadCommand = "R%2i%2o%2c"
#else
pageReadCommand = "R%2o%2c"
#endif
page = 1
rpmBins = array, U16, 0, [2], "RPM", 1, 0, 0, 10000, 0
loadBins = array, U08, 4, [2], "Load", 1, 0, 0, 255, 0
values = array, U08, 6, [2x2], "%", 1, 0, 0, 255, 0

[TableEditor]
table = bundleTable, bundleMap, "Bundle Table", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#;

    fn import(indexed_read: bool) -> OwnedDefinitionBundleImport {
        let mut enabled_symbols = HashSet::new();
        if indexed_read {
            enabled_symbols.insert("INDEXED_READ".to_string());
        }
        OwnedDefinitionBundleImport {
            profile_name: "bundle-generic".to_string(),
            profile_generation: 900,
            definition_fingerprint: Some("bundle-fingerprint".to_string()),
            root_definition: ROOT.to_string(),
            enabled_symbols,
            includes: HashMap::from([("constants.inc".to_string(), CONSTANTS.to_string())]),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42_900,
                editor_table_id: "bundleTable".to_string(),
            }],
        }
    }

    #[test]
    fn one_resolution_compiles_profile_and_protocol_from_identical_text() {
        let bundle = compile_definition_bundle(&import(true)).unwrap();

        assert_eq!(
            bundle.profile.identity().signature,
            "rusEFI bundle.generic.900"
        );
        assert_eq!(bundle.profile.page_sizes(), &[32]);
        assert_eq!(bundle.protocol.pages.len(), 1);
        assert_eq!(bundle.protocol.pages[0].identifier, vec![0, 0]);
        assert_eq!(bundle.protocol.pages[0].read_command, "R%2i%2o%2c");
        assert!(bundle.resolved_definition.text.contains("R%2i%2o%2c"));
        assert!(
            !bundle
                .resolved_definition
                .text
                .contains("R%2o%2c\"\n#endif")
        );
    }

    #[test]
    fn explicit_symbol_selection_changes_protocol_without_board_routing() {
        let indexed = compile_definition_bundle(&import(true)).unwrap();
        let plain = compile_definition_bundle(&import(false)).unwrap();

        assert_eq!(
            indexed.profile.identity().signature,
            plain.profile.identity().signature
        );
        assert_eq!(indexed.protocol.pages[0].read_command, "R%2i%2o%2c");
        assert_eq!(plain.protocol.pages[0].read_command, "R%2o%2c");
    }

    #[test]
    fn missing_include_fails_before_profile_or_protocol_exists() {
        let mut broken = import(true);
        broken.includes.clear();
        assert_eq!(
            compile_definition_bundle(&broken),
            Err(CompiledProfileBundleError::Resolve(
                DefinitionResolveError::IncludeNotFound("constants.inc".to_string())
            ))
        );
    }

    #[test]
    fn unsupported_condition_fails_closed_at_bundle_resolution_boundary() {
        let mut broken = import(true);
        broken.root_definition = ROOT.replace(
            "#include \"constants.inc\"",
            "#if A && B\n#include \"constants.inc\"\n#endif",
        );
        assert!(matches!(
            compile_definition_bundle(&broken),
            Err(CompiledProfileBundleError::Resolve(
                DefinitionResolveError::UnsupportedCondition(_)
            ))
        ));
    }
}
