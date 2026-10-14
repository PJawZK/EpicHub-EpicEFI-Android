use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::tune_source_install::{TuneSourceInstallError, install_live_pages};

#[derive(Clone, Debug, PartialEq)]
pub enum LiveTuneInstallError {
    ProfileAuthority(RegistryStoreError),
    Install(TuneSourceInstallError),
}

pub fn install_selected_live_pages(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    pages: &[&[u8]],
) -> Result<u64, LiveTuneInstallError> {
    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| install_live_pages(engine_handle, &bundle.profile, pages),
    )
    .map_err(LiveTuneInstallError::ProfileAuthority)?
    .map_err(LiveTuneInstallError::Install)
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{close_engine, create_engine, with_tune_session};
    use crate::offline_tune_source::OFFLINE_SOURCE_PRIMARY_TABLE_ID;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };
    use crate::tuning::TuneSnapshotOrigin;

    const SIGNATURE: &str = "rusEFI live.install.test.1";
    const ROOT: &str = r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI live.install.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 8
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2i%2o%2c"
"#;

    fn bundle_import() -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "live-install-test".to_string(),
            profile_generation: 4_242,
            definition_fingerprint: Some("live-install-test-fingerprint".to_string()),
            root_definition: ROOT.to_string(),
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        }
    }

    #[test]
    fn exact_selected_bundle_installs_live_origin_and_resets_history() {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
                .unwrap();
            assert_eq!(session.revision(), 1);
        })
        .unwrap();

        let page = [0_u8; 8];
        let generation =
            install_selected_live_pages(engine, registry, SIGNATURE.as_bytes(), 256, &[&page])
                .unwrap();
        assert_eq!(generation, 4_242);
        with_tune_session(engine, |session| {
            assert_eq!(session.profile_generation(), 4_242);
            assert_eq!(
                session.baseline().origin,
                TuneSnapshotOrigin::LiveController
            );
            assert_eq!(session.revision(), 0);
            assert!(session.undo().is_err());
        })
        .unwrap();

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn decode_failure_preserves_existing_session_and_history() {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
                .unwrap();
        })
        .unwrap();

        let short_page = [0_u8; 7];
        assert!(
            install_selected_live_pages(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &[&short_page],
            )
            .is_err()
        );
        with_tune_session(engine, |session| {
            assert_eq!(session.revision(), 1);
            assert!(
                session
                    .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                    .is_ok()
            );
            assert_eq!(session.undo().unwrap(), 2);
        })
        .unwrap();

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn unknown_runtime_signature_never_mutates_engine_session() {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        let page = [0_u8; 8];

        assert!(
            install_selected_live_pages(engine, registry, b"unknown.signature", 256, &[&page])
                .is_err()
        );
        with_tune_session(engine, |session| {
            assert_eq!(
                session.baseline().origin,
                TuneSnapshotOrigin::OfflineFixture
            );
            assert_eq!(session.revision(), 0);
        })
        .unwrap();

        close_profile_registry(registry);
        close_engine(engine);
    }
}
