use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use crate::compiled_ecu_profile::CompiledProfileBuildError;
use crate::compiled_profile_bundle::{
    CompiledEcuProfileBundle, CompiledProfileBundleError, OwnedDefinitionBundleImport,
    compile_definition_bundle,
};
use crate::ecu_profile::ResolvedEcuProfile;
use crate::ecu_profile_registry::{OwnedProfileRegistry, ProfileRegistryError};
use crate::profile_import::{OwnedCompiledProfileImport, compile_owned_profile_import};
use crate::runtime_profile_authority::{
    RuntimeProfileAuthorityError, select_profile_from_runtime_response,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SelectedProfileSummary {
    pub profile_generation: u64,
}

struct RuntimeProfileRegistryState {
    profiles: OwnedProfileRegistry,
    compiled_bundles: HashMap<String, CompiledEcuProfileBundle>,
}

impl RuntimeProfileRegistryState {
    fn new() -> Self {
        Self {
            profiles: OwnedProfileRegistry::new(),
            compiled_bundles: HashMap::new(),
        }
    }
}

pub struct ProfileRegistryStore {
    next_handle: i64,
    registries: HashMap<i64, RuntimeProfileRegistryState>,
}

impl ProfileRegistryStore {
    pub fn new() -> Self {
        Self {
            next_handle: 1,
            registries: HashMap::new(),
        }
    }

    pub fn create(&mut self) -> i64 {
        let handle = self.next_handle;
        if handle <= 0 || handle == i64::MAX {
            return 0;
        }
        self.next_handle += 1;
        self.registries
            .insert(handle, RuntimeProfileRegistryState::new());
        handle
    }

    pub fn close(&mut self, handle: i64) {
        if handle != 0 {
            self.registries.remove(&handle);
        }
    }

    pub fn is_alive(&self, handle: i64) -> bool {
        handle != 0 && self.registries.contains_key(&handle)
    }

    pub fn active_count(&self) -> i32 {
        i32::try_from(self.registries.len()).unwrap_or(i32::MAX)
    }

    pub fn register_recognized_signature(
        &mut self,
        handle: i64,
        signature: impl Into<String>,
    ) -> bool {
        let Some(state) = self.registries.get_mut(&handle) else {
            return false;
        };
        state.profiles.register_recognized_signature(signature);
        true
    }

    pub fn register_profile<P>(&mut self, handle: i64, profile: P) -> Result<(), RegistryStoreError>
    where
        P: ResolvedEcuProfile + 'static,
    {
        let state = self
            .registries
            .get_mut(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        state
            .profiles
            .register_profile(profile)
            .map_err(RegistryStoreError::Registry)
    }

    pub fn register_compiled_import(
        &mut self,
        handle: i64,
        import: &OwnedCompiledProfileImport,
    ) -> Result<(), RegistryStoreError> {
        if !self.registries.contains_key(&handle) {
            return Err(RegistryStoreError::InvalidHandle);
        }
        let profile = compile_owned_profile_import(import).map_err(RegistryStoreError::Compile)?;
        self.register_profile(handle, profile)
    }

    pub fn register_definition_bundle(
        &mut self,
        handle: i64,
        import: &OwnedDefinitionBundleImport,
    ) -> Result<(), RegistryStoreError> {
        if !self.registries.contains_key(&handle) {
            return Err(RegistryStoreError::InvalidHandle);
        }

        let bundle =
            compile_definition_bundle(import).map_err(RegistryStoreError::BundleCompile)?;
        let signature = bundle.profile.identity().signature.to_string();
        let profile = bundle.profile.clone();
        let state = self
            .registries
            .get_mut(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        state
            .profiles
            .register_profile(profile)
            .map_err(RegistryStoreError::Registry)?;
        state.compiled_bundles.insert(signature, bundle);
        Ok(())
    }

    pub fn compiled_bundle_count(&self, handle: i64) -> Result<usize, RegistryStoreError> {
        let state = self
            .registries
            .get(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        Ok(state.compiled_bundles.len())
    }

    pub fn select_from_runtime_response(
        &self,
        handle: i64,
        response: &[u8],
        max_response_bytes: usize,
    ) -> Result<SelectedProfileSummary, RegistryStoreError> {
        let state = self
            .registries
            .get(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        let profile =
            select_profile_from_runtime_response(&state.profiles, response, max_response_bytes)
                .map_err(RegistryStoreError::Authority)?;
        Ok(SelectedProfileSummary {
            profile_generation: profile.identity().profile_generation,
        })
    }

    pub fn select_compiled_bundle_from_runtime_response(
        &self,
        handle: i64,
        response: &[u8],
        max_response_bytes: usize,
    ) -> Result<&CompiledEcuProfileBundle, RegistryStoreError> {
        let state = self
            .registries
            .get(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        let profile =
            select_profile_from_runtime_response(&state.profiles, response, max_response_bytes)
                .map_err(RegistryStoreError::Authority)?;
        let signature = profile.identity().signature;
        state
            .compiled_bundles
            .get(signature)
            .ok_or_else(|| RegistryStoreError::MissingCompiledBundle(signature.to_string()))
    }
}

impl Default for ProfileRegistryStore {
    fn default() -> Self {
        Self::new()
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum RegistryStoreError {
    InvalidHandle,
    Compile(CompiledProfileBuildError),
    BundleCompile(CompiledProfileBundleError),
    Registry(ProfileRegistryError),
    Authority(RuntimeProfileAuthorityError),
    MissingCompiledBundle(String),
}

static PROFILE_REGISTRY_STORE: LazyLock<Mutex<ProfileRegistryStore>> =
    LazyLock::new(|| Mutex::new(ProfileRegistryStore::new()));

fn with_store<T>(operation: impl FnOnce(&mut ProfileRegistryStore) -> T) -> T {
    let mut guard = PROFILE_REGISTRY_STORE
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut guard)
}

pub fn create_profile_registry() -> i64 {
    with_store(ProfileRegistryStore::create)
}

pub fn close_profile_registry(handle: i64) {
    with_store(|store| store.close(handle));
}

pub fn profile_registry_is_alive(handle: i64) -> bool {
    with_store(|store| store.is_alive(handle))
}

pub fn active_profile_registry_count() -> i32 {
    with_store(|store| store.active_count())
}

pub fn register_recognized_signature(handle: i64, signature: impl Into<String>) -> bool {
    with_store(|store| store.register_recognized_signature(handle, signature))
}

pub fn register_compiled_profile_import(
    handle: i64,
    import: &OwnedCompiledProfileImport,
) -> Result<(), RegistryStoreError> {
    with_store(|store| store.register_compiled_import(handle, import))
}

pub fn register_definition_bundle_import(
    handle: i64,
    import: &OwnedDefinitionBundleImport,
) -> Result<(), RegistryStoreError> {
    with_store(|store| store.register_definition_bundle(handle, import))
}

pub fn with_selected_compiled_bundle<T>(
    handle: i64,
    response: &[u8],
    max_response_bytes: usize,
    operation: impl FnOnce(&CompiledEcuProfileBundle) -> T,
) -> Result<T, RegistryStoreError> {
    with_store(|store| {
        let bundle = store.select_compiled_bundle_from_runtime_response(
            handle,
            response,
            max_response_bytes,
        )?;
        Ok(operation(bundle))
    })
}

pub fn select_profile_summary_from_runtime_response(
    handle: i64,
    response: &[u8],
    max_response_bytes: usize,
) -> Result<SelectedProfileSummary, RegistryStoreError> {
    with_store(|store| store.select_from_runtime_response(handle, response, max_response_bytes))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile::{EcuProfileResolveError, ProfileIdentity};
    use crate::ecu_profile_registry::ProfileSelectionError;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::runtime_identity_probe::RuntimeProbeError;
    use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};
    use std::collections::{HashMap, HashSet};

    const IMPORT_DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI registry.import.700"

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
table = registryTable, registryMap, "Registry Table", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#;

    const BUNDLE_ROOT: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI registry.bundle.800"

#include "bundle.inc"
"#;

    const BUNDLE_INCLUDE: &str = r#"
[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 32
pageIdentifier = "\x00\x01"
pageReadCommand = "R%2i%2o%2c"
burnCommand = "B%2i"
page = 1
rpmBins = array, U16, 0, [2], "RPM", 1, 0, 0, 10000, 0
loadBins = array, U08, 4, [2], "Load", 1, 0, 0, 255, 0
values = array, U08, 6, [2x2], "%", 1, 0, 0, 255, 0

[TableEditor]
table = registryBundleTable, registryBundleMap, "Registry Bundle Table", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#;

    struct TestProfile {
        name: String,
        signature: String,
        generation: u64,
    }

    impl ResolvedEcuProfile for TestProfile {
        fn identity(&self) -> ProfileIdentity<'_> {
            ProfileIdentity {
                profile_name: &self.name,
                signature: &self.signature,
                profile_generation: self.generation,
                definition_fingerprint: None,
            }
        }

        fn page_sizes(&self) -> &[usize] {
            &[]
        }

        fn resolve_pages(
            &self,
            _pages: &[&[u8]],
            _origin: TuneSnapshotOrigin,
        ) -> Result<TuneSnapshot, EcuProfileResolveError> {
            unreachable!("registry handle tests do not resolve pages")
        }
    }

    fn profile(name: &str, signature: &str, generation: u64) -> TestProfile {
        TestProfile {
            name: name.to_string(),
            signature: signature.to_string(),
            generation,
        }
    }

    fn compiled_import() -> OwnedCompiledProfileImport {
        OwnedCompiledProfileImport {
            profile_name: "registry-import".to_string(),
            profile_generation: 700,
            definition_fingerprint: Some("registry-fingerprint".to_string()),
            resolved_definition: IMPORT_DEFINITION.to_string(),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42_700,
                editor_table_id: "registryTable".to_string(),
            }],
        }
    }

    fn bundle_import() -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "registry-bundle".to_string(),
            profile_generation: 800,
            definition_fingerprint: Some("registry-bundle-fingerprint".to_string()),
            root_definition: BUNDLE_ROOT.to_string(),
            enabled_symbols: HashSet::new(),
            includes: HashMap::from([("bundle.inc".to_string(), BUNDLE_INCLUDE.to_string())]),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42_800,
                editor_table_id: "registryBundleTable".to_string(),
            }],
        }
    }

    #[test]
    fn local_store_owns_registries_and_profiles_independently() {
        let mut store = ProfileRegistryStore::new();
        let first = store.create();
        let second = store.create();
        store
            .register_profile(first, profile("first", "rusEFI owned.first.100", 100))
            .unwrap();
        store
            .register_profile(second, profile("second", "rusEFI owned.second.200", 200))
            .unwrap();

        assert_eq!(
            store
                .select_from_runtime_response(first, b"rusEFI owned.first.100", 256)
                .unwrap(),
            SelectedProfileSummary {
                profile_generation: 100,
            }
        );
        assert_eq!(
            store.select_from_runtime_response(first, b"rusEFI owned.second.200", 256),
            Err(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController,
            ))
        );
        assert_eq!(store.active_count(), 2);
    }

    #[test]
    fn compiled_import_registers_real_profile_and_is_exactly_selectable() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_compiled_import(handle, &compiled_import())
            .unwrap();

        assert_eq!(
            store
                .select_from_runtime_response(handle, b"rusEFI registry.import.700", 256)
                .unwrap(),
            SelectedProfileSummary {
                profile_generation: 700,
            }
        );
    }

    #[test]
    fn definition_bundle_registration_keeps_protocol_with_exact_profile_authority() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_definition_bundle(handle, &bundle_import())
            .unwrap();

        let bundle = store
            .select_compiled_bundle_from_runtime_response(
                handle,
                b"rusEFI registry.bundle.800",
                256,
            )
            .unwrap();
        assert_eq!(bundle.profile.identity().profile_generation, 800);
        assert_eq!(bundle.profile.page_sizes(), &[32]);
        assert_eq!(bundle.protocol.pages.len(), 1);
        assert_eq!(bundle.protocol.pages[0].identifier, vec![0, 1]);
        assert_eq!(bundle.protocol.pages[0].read_command, "R%2i%2o%2c");
        assert_eq!(bundle.protocol.pages[0].burn_command.as_deref(), Some("B%2i"));
        assert_eq!(store.compiled_bundle_count(handle).unwrap(), 1);
    }

    #[test]
    fn profile_only_match_cannot_authorize_protocol_dependent_bundle_access() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_compiled_import(handle, &compiled_import())
            .unwrap();

        assert_eq!(
            store.select_compiled_bundle_from_runtime_response(
                handle,
                b"rusEFI registry.import.700",
                256,
            ),
            Err(RegistryStoreError::MissingCompiledBundle(
                "rusEFI registry.import.700".to_string(),
            ))
        );
    }

    #[test]
    fn failed_bundle_compile_does_not_partially_register_profile_or_protocol() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        let mut import = bundle_import();
        import.includes.clear();

        assert!(matches!(
            store.register_definition_bundle(handle, &import),
            Err(RegistryStoreError::BundleCompile(_))
        ));
        assert_eq!(store.compiled_bundle_count(handle).unwrap(), 0);
        assert_eq!(
            store.select_from_runtime_response(handle, b"rusEFI registry.bundle.800", 256),
            Err(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController,
            ))
        );
    }

    #[test]
    fn duplicate_bundle_signature_preserves_original_bundle() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        let import = bundle_import();
        store.register_definition_bundle(handle, &import).unwrap();

        let mut duplicate = import.clone();
        duplicate.profile_generation = 999;
        duplicate.includes.insert(
            "bundle.inc".to_string(),
            BUNDLE_INCLUDE.replace("R%2i%2o%2c", "R%2o%2c"),
        );
        assert!(matches!(
            store.register_definition_bundle(handle, &duplicate),
            Err(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature(_)
            ))
        ));

        let bundle = store
            .select_compiled_bundle_from_runtime_response(
                handle,
                b"rusEFI registry.bundle.800",
                256,
            )
            .unwrap();
        assert_eq!(bundle.profile.identity().profile_generation, 800);
        assert_eq!(bundle.protocol.pages[0].read_command, "R%2i%2o%2c");
        assert_eq!(store.compiled_bundle_count(handle).unwrap(), 1);
    }

    #[test]
    fn failed_compiled_import_does_not_partially_register_signature() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        let mut import = compiled_import();
        import.semantic_tables[0].editor_table_id = "missingTable".to_string();

        assert!(matches!(
            store.register_compiled_import(handle, &import),
            Err(RegistryStoreError::Compile(_))
        ));
        assert_eq!(
            store.select_from_runtime_response(handle, b"rusEFI registry.import.700", 256),
            Err(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController,
            ))
        );
    }

    #[test]
    fn duplicate_compiled_import_is_rejected_without_replacing_original() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        let import = compiled_import();
        store.register_compiled_import(handle, &import).unwrap();

        assert!(matches!(
            store.register_compiled_import(handle, &import),
            Err(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature(_)
            ))
        ));
        assert_eq!(
            store
                .select_from_runtime_response(handle, b"rusEFI registry.import.700", 256)
                .unwrap()
                .profile_generation,
            700
        );
    }

    #[test]
    fn close_is_idempotent_and_invalid_handles_fail_closed() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        assert!(store.is_alive(handle));

        store.close(handle);
        store.close(handle);
        store.close(0);

        assert!(!store.is_alive(handle));
        assert_eq!(store.active_count(), 0);
        assert_eq!(
            store.select_from_runtime_response(handle, b"rusEFI any.1", 256),
            Err(RegistryStoreError::InvalidHandle)
        );
    }

    #[test]
    fn recognized_without_profile_and_invalid_identity_keep_distinct_errors() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        assert!(store.register_recognized_signature(handle, "rusEFI known.no-profile.300"));

        assert_eq!(
            store.select_from_runtime_response(handle, b"rusEFI known.no-profile.300", 256),
            Err(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile,
            ))
        );
        assert_eq!(
            store.select_from_runtime_response(handle, &[0xff], 256),
            Err(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::Identity(RuntimeProbeError::InvalidUtf8),
            ))
        );
        assert_eq!(
            store.select_from_runtime_response(handle, b"rusEFI unknown.400", 256),
            Err(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController,
            ))
        );
    }

    #[test]
    fn duplicate_profile_signature_error_survives_handle_store_boundary() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_profile(handle, profile("first", "rusEFI duplicate.500", 500))
            .unwrap();

        assert_eq!(
            store.register_profile(handle, profile("alias", "rusEFI duplicate.500", 999)),
            Err(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature("rusEFI duplicate.500".to_string(),),
            ))
        );
    }

    #[test]
    fn recognized_registration_rejects_invalid_handle() {
        let mut store = ProfileRegistryStore::new();
        assert!(!store.register_recognized_signature(999, "rusEFI known.600"));
    }

    #[test]
    fn authority_error_type_keeps_profile_selection_categories_explicit() {
        let _ = ProfileSelectionError::UnknownController;
    }
}
