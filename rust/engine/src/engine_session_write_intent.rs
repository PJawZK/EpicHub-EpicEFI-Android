use crate::engine::with_tune_source_authority;
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::session_write_intent::{
    SessionRamWriteIntent, SessionWriteIntentError, plan_session_ram_write_intent,
};

#[derive(Clone, Debug, PartialEq)]
pub enum EngineSessionWriteIntentError {
    ProfileAuthority(RegistryStoreError),
    InvalidEngineHandle,
    MissingSourceImage,
    StaleDraftRevision { expected: u64, actual: u64 },
    Planning(SessionWriteIntentError),
}

pub fn plan_selected_engine_ram_write_intent(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    expected_draft_revision: u64,
) -> Result<SessionRamWriteIntent, EngineSessionWriteIntentError> {
    let planned = with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            with_tune_source_authority(engine_handle, |session, source| {
                let actual_revision = session.revision();
                if actual_revision != expected_draft_revision {
                    return Err(EngineSessionWriteIntentError::StaleDraftRevision {
                        expected: expected_draft_revision,
                        actual: actual_revision,
                    });
                }
                let source = source.ok_or(EngineSessionWriteIntentError::MissingSourceImage)?;
                plan_session_ram_write_intent(bundle, session, source)
                    .map_err(EngineSessionWriteIntentError::Planning)
            })
            .ok_or(EngineSessionWriteIntentError::InvalidEngineHandle)?
        },
    )
    .map_err(EngineSessionWriteIntentError::ProfileAuthority)?;

    planned
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{close_engine, create_engine, with_tune_session};
    use crate::live_tune_install::install_selected_live_pages;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };
    use crate::runtime_profile_authority::RuntimeProfileAuthorityError;

    const SIGNATURE: &str = "rusEFI engine-session-write-intent.test.1";
    const DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI engine-session-write-intent.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 24
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
pageChunkWrite = "C%2o%2c%v"
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

    fn bundle_import() -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "engine-session-write-intent".to_string(),
            profile_generation: 77,
            definition_fingerprint: None,
            root_definition: DEFINITION.to_string(),
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42,
                editor_table_id: "valuesTable".to_string(),
            }],
        }
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

    fn live_engine() -> (i64, i64, Vec<u8>) {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        let page = source_page();
        install_selected_live_pages(engine, registry, SIGNATURE.as_bytes(), 256, &[&page]).unwrap();
        (engine, registry, page)
    }

    #[test]
    fn exact_selected_bundle_and_atomic_engine_source_produce_intent() {
        let (engine, registry, _) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        let intent = plan_selected_engine_ram_write_intent(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        assert_eq!(intent.profile_generation, 77);
        assert_eq!(intent.draft_revision, 1);
        assert_eq!(intent.patches.len(), 1);
        assert_eq!(intent.patches[0].page, 1);
        assert_eq!(intent.patches[0].offset, 6);
        assert_eq!(intent.patches[0].before, vec![100]);
        assert_eq!(intent.patches[0].after, vec![101]);

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn stale_expected_revision_fails_before_patch_derivation() {
        let (engine, registry, _) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        assert_eq!(
            plan_selected_engine_ram_write_intent(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                0,
            ),
            Err(EngineSessionWriteIntentError::StaleDraftRevision {
                expected: 0,
                actual: 1,
            })
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn missing_source_and_invalid_engine_are_distinct() {
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        let engine = create_engine();

        assert_eq!(
            plan_selected_engine_ram_write_intent(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                0,
            ),
            Err(EngineSessionWriteIntentError::MissingSourceImage)
        );
        close_engine(engine);

        assert_eq!(
            plan_selected_engine_ram_write_intent(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                0,
            ),
            Err(EngineSessionWriteIntentError::InvalidEngineHandle)
        );

        close_profile_registry(registry);
    }

    #[test]
    fn unknown_signature_never_reaches_engine_source_authority() {
        let (engine, registry, _) = live_engine();
        assert_eq!(
            plan_selected_engine_ram_write_intent(
                engine,
                registry,
                b"unknown.signature",
                256,
                0,
            ),
            Err(EngineSessionWriteIntentError::ProfileAuthority(
                RegistryStoreError::Authority(RuntimeProfileAuthorityError::UnknownController)
            ))
        );

        close_profile_registry(registry);
        close_engine(engine);
    }
}
