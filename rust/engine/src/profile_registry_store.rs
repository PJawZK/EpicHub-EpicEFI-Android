use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use crate::compiled_ecu_profile::CompiledProfileBuildError;
use crate::compiled_profile_bundle::{
    CompiledEcuProfileBundle, CompiledProfileBundleError, OwnedDefinitionBundleImport,
    compile_definition_bundle,
};
use crate::ecu_profile::ResolvedEcuProfile;
use crate::ecu_profile_registry::{OwnedProfileRegistry, ProfileRegistryError};
use crate::live_channel_store::LiveChannelBatch;
use crate::profile_import::{OwnedCompiledProfileImport, compile_owned_profile_import};
use crate::runtime_acquisition_session::RuntimeRequestToken;
use crate::runtime_bound_live_session::{
    BoundRuntimeError, BoundRuntimeIdentity, RuntimeBoundLiveSession,
};
use crate::runtime_definition_catalog::NamedRuntimeChannel;
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
    runtime: RuntimeBoundLiveSession,
}

impl RuntimeProfileRegistryState {
    fn new() -> Self {
        Self {
            profiles: OwnedProfileRegistry::new(),
            compiled_bundles: HashMap::new(),
            runtime: RuntimeBoundLiveSession::new(),
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
        state.runtime.disconnect();
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
        state.runtime.disconnect();
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
        state.runtime.disconnect();
        state
            .profiles
            .register_profile(profile)
            .map_err(RegistryStoreError::Registry)?;
        state.compiled_bundles.insert(signature, bundle);
        Ok(())
    }

    /// Native-only candidate binding, not evidence of Android USB ownership.
    /// Exactly one registry handle selects both the profile and resolved definition.
    /// A caller's expected generation must agree with that exact selection.
    pub fn bind_runtime_candidate(
        &mut self,
        handle: i64,
        endpoint_id: &str,
        signature_response: &[u8],
        signature_limit: usize,
        expected_generation: u64,
    ) -> Result<BoundRuntimeIdentity, RuntimeRegistryBindingError> {
        let state =
            self.registries
                .get_mut(&handle)
                .ok_or(RuntimeRegistryBindingError::Registry(
                    RegistryStoreError::InvalidHandle,
                ))?;
        // Even a rejected selection revokes the previously selected candidate.
        state.runtime.disconnect();
        let profile = select_profile_from_runtime_response(
            &state.profiles,
            signature_response,
            signature_limit,
        )
        .map_err(|error| {
            RuntimeRegistryBindingError::Registry(RegistryStoreError::Authority(error))
        })?;
        if expected_generation == 0 || profile.identity().profile_generation != expected_generation
        {
            return Err(RuntimeRegistryBindingError::GenerationMismatch);
        }
        let signature = profile.identity().signature;
        let bundle = state.compiled_bundles.get(signature).ok_or_else(|| {
            RuntimeRegistryBindingError::Registry(RegistryStoreError::MissingCompiledBundle(
                signature.to_string(),
            ))
        })?;
        if bundle.profile.identity() != profile.identity() {
            return Err(RuntimeRegistryBindingError::ProfileMismatch);
        }
        state
            .runtime
            .bind(
                endpoint_id,
                signature,
                &bundle.profile,
                &bundle.resolved_definition.text,
            )
            .map_err(RuntimeRegistryBindingError::Bound)
    }

    /// These host-only methods prove lease scoping and do not perform USB IO.
    /// Future JNI must keep every invocation under Android's retained USB owner.
    pub fn begin_runtime_read(
        &mut self,
        handle: i64,
        lease: &BoundRuntimeIdentity,
        now_ms: u64,
        timeout_ms: u64,
    ) -> Result<(RuntimeRequestToken, Vec<u8>), RuntimeRegistryBindingError> {
        self.runtime_mut(handle)?
            .begin(lease, now_ms, timeout_ms)
            .map_err(RuntimeRegistryBindingError::Bound)
    }

    pub fn complete_runtime_read(
        &mut self,
        handle: i64,
        lease: &BoundRuntimeIdentity,
        token: RuntimeRequestToken,
        now_ms: u64,
        response: &[u8],
    ) -> Result<LiveChannelBatch, RuntimeRegistryBindingError> {
        self.runtime_mut(handle)?
            .complete(lease, token, now_ms, response)
            .cloned()
            .map_err(RuntimeRegistryBindingError::Bound)
    }

    pub fn latest_runtime_batch(
        &mut self,
        handle: i64,
        lease: &BoundRuntimeIdentity,
        now_ms: u64,
        max_age_ms: u64,
    ) -> Result<Option<LiveChannelBatch>, RuntimeRegistryBindingError> {
        self.runtime_mut(handle)?
            .latest(lease, now_ms, max_age_ms)
            .map(|batch| batch.cloned())
            .map_err(RuntimeRegistryBindingError::Bound)
    }

    /// Snapshot metadata from the *same* native binding as runtime decoding.
    /// No caller-chosen labels, IDs or type definitions are accepted.
    pub fn runtime_catalog_for_epoch(
        &self,
        handle: i64,
        epoch: u64,
    ) -> Result<(BoundRuntimeIdentity, Vec<NamedRuntimeChannel>), RuntimeRegistryBindingError> {
        let state = self
            .registries
            .get(&handle)
            .ok_or(RuntimeRegistryBindingError::Registry(
                RegistryStoreError::InvalidHandle,
            ))?;
        let binding = state
            .runtime
            .binding_for_epoch(epoch)
            .map_err(RuntimeRegistryBindingError::Bound)?;
        let catalog = state
            .runtime
            .channel_info(&binding)
            .map_err(RuntimeRegistryBindingError::Bound)?;
        Ok((binding, catalog.to_vec()))
    }

    /// The epoch is resolved against a private native lease under the registry
    /// mutex; callers never supply the profile, definition, or lease fields.
    pub fn prepare_runtime_read_for_epoch(
        &mut self,
        handle: i64,
        epoch: u64,
        now_ms: u64,
        timeout_ms: u64,
    ) -> Result<(RuntimeRequestToken, Vec<u8>, usize), RuntimeRegistryBindingError> {
        let runtime = self.runtime_mut(handle)?;
        let lease = runtime
            .binding_for_epoch(epoch)
            .map_err(RuntimeRegistryBindingError::Bound)?;
        let response_bytes = runtime
            .expected_response_bytes(&lease)
            .map_err(RuntimeRegistryBindingError::Bound)?;
        let (token, request) = runtime
            .begin(&lease, now_ms, timeout_ms)
            .map_err(RuntimeRegistryBindingError::Bound)?;
        Ok((token, request, response_bytes))
    }

    pub fn finish_runtime_read_for_epoch(
        &mut self,
        handle: i64,
        epoch: u64,
        sequence: u64,
        now_ms: u64,
        response: &[u8],
    ) -> Result<(BoundRuntimeIdentity, LiveChannelBatch), RuntimeRegistryBindingError> {
        let runtime = self.runtime_mut(handle)?;
        let lease = runtime
            .binding_for_epoch(epoch)
            .map_err(RuntimeRegistryBindingError::Bound)?;
        let batch = runtime
            .complete(
                &lease,
                RuntimeRequestToken {
                    session: epoch,
                    sequence,
                },
                now_ms,
                response,
            )
            .cloned()
            .map_err(RuntimeRegistryBindingError::Bound)?;
        Ok((lease, batch))
    }

    pub fn disconnect_runtime(&mut self, handle: i64) -> Result<(), RegistryStoreError> {
        let state = self
            .registries
            .get_mut(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        state.runtime.disconnect();
        Ok(())
    }

    fn runtime_mut(
        &mut self,
        handle: i64,
    ) -> Result<&mut RuntimeBoundLiveSession, RuntimeRegistryBindingError> {
        self.registries
            .get_mut(&handle)
            .map(|state| &mut state.runtime)
            .ok_or(RuntimeRegistryBindingError::Registry(
                RegistryStoreError::InvalidHandle,
            ))
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

#[derive(Clone, Debug, PartialEq)]
pub enum RuntimeRegistryBindingError {
    Registry(RegistryStoreError),
    GenerationMismatch,
    ProfileMismatch,
    Bound(BoundRuntimeError),
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

/// Registry-owned candidate binding. The caller must additionally hold the
/// serialized physical connection owner across observed identity and binding.
pub fn bind_runtime_candidate_from_registry(
    handle: i64,
    endpoint_id: &str,
    response: &[u8],
    max_response_bytes: usize,
    expected_generation: u64,
) -> Result<BoundRuntimeIdentity, RuntimeRegistryBindingError> {
    with_store(|store| {
        store.bind_runtime_candidate(
            handle,
            endpoint_id,
            response,
            max_response_bytes,
            expected_generation,
        )
    })
}

/// Revokes the registry's candidate lease; never closes a physical USB port.
pub fn disconnect_runtime_candidate_from_registry(handle: i64) -> Result<(), RegistryStoreError> {
    with_store(|store| store.disconnect_runtime(handle))
}

pub fn runtime_catalog_from_registry(
    handle: i64,
    epoch: u64,
) -> Result<(BoundRuntimeIdentity, Vec<NamedRuntimeChannel>), RuntimeRegistryBindingError> {
    with_store(|store| store.runtime_catalog_for_epoch(handle, epoch))
}

pub fn prepare_runtime_read_from_registry(
    handle: i64,
    epoch: u64,
    now_ms: u64,
    timeout_ms: u64,
) -> Result<(RuntimeRequestToken, Vec<u8>, usize), RuntimeRegistryBindingError> {
    with_store(|store| store.prepare_runtime_read_for_epoch(handle, epoch, now_ms, timeout_ms))
}

pub fn finish_runtime_read_from_registry(
    handle: i64,
    epoch: u64,
    sequence: u64,
    now_ms: u64,
    response: &[u8],
) -> Result<(BoundRuntimeIdentity, LiveChannelBatch), RuntimeRegistryBindingError> {
    with_store(|store| {
        store.finish_runtime_read_for_epoch(handle, epoch, sequence, now_ms, response)
    })
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
        assert_eq!(
            bundle.protocol.pages[0].burn_command.as_deref(),
            Some("B%2i")
        );
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

    fn runtime_bundle_import() -> OwnedDefinitionBundleImport {
        let mut import = bundle_import();
        import.includes.insert(
            "bundle.inc".to_string(),
            format!(
                "{BUNDLE_INCLUDE}\n[OutputChannels]\nochGetCommand = \"O%2o%2c\"\nochBlockSize = 4\nrpm = scalar, F32, 0, \"RPM\", 1, 0\n"
            ),
        );
        import
    }

    fn runtime_response(value: f32) -> Vec<u8> {
        let mut body = vec![0];
        body.extend_from_slice(&value.to_le_bytes());
        let mut frame = Vec::new();
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crate::ms_envelope_codec::crc32_ieee(&body).to_be_bytes());
        frame
    }

    const RUNTIME_SIGNATURE: &[u8] = b"rusEFI registry.bundle.800";

    #[test]
    fn exact_registry_owned_bundle_binds_decoder_command_and_live_batch() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_definition_bundle(handle, &runtime_bundle_import())
            .unwrap();
        let lease = store
            .bind_runtime_candidate(handle, "usb:one", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        assert_eq!(lease.signature(), "rusEFI registry.bundle.800");
        assert_eq!(lease.profile_generation(), 800);
        assert_eq!(lease.profile_fingerprint(), "registry-bundle-fingerprint");
        let (token, request) = store.begin_runtime_read(handle, &lease, 10, 40).unwrap();
        assert_eq!(&request[2..7], &[b'O', 0, 0, 4, 0]);
        let batch = store
            .complete_runtime_read(handle, &lease, token, 11, &runtime_response(920.0))
            .unwrap();
        assert_eq!(batch.samples.len(), 1);
        assert_eq!(batch.samples[0].value, 920.0);
        assert_eq!(
            batch.validity,
            crate::runtime_acquisition_session::RuntimeSampleState::Fresh
        );
        assert_eq!(
            store
                .latest_runtime_batch(handle, &lease, 20, 10)
                .unwrap()
                .unwrap()
                .validity,
            crate::runtime_acquisition_session::RuntimeSampleState::Fresh
        );
        assert_eq!(
            store
                .latest_runtime_batch(handle, &lease, 21, 10)
                .unwrap()
                .unwrap()
                .validity,
            crate::runtime_acquisition_session::RuntimeSampleState::Stale
        );
    }

    #[test]
    fn compiled_catalog_metadata_is_fenced_to_exact_registry_epoch() {
        use crate::runtime_channel_decode::RuntimeValueType;
        let mut store = ProfileRegistryStore::new();
        let first_handle = store.create();
        let second_handle = store.create();
        store
            .register_definition_bundle(first_handle, &runtime_bundle_import())
            .unwrap();
        store
            .register_definition_bundle(second_handle, &runtime_bundle_import())
            .unwrap();
        let first = store
            .bind_runtime_candidate(first_handle, "usb:first", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        let (binding, catalog) = store
            .runtime_catalog_for_epoch(first_handle, first.session_epoch())
            .unwrap();
        assert_eq!(binding, first);
        assert_eq!(catalog.len(), 1);
        assert_eq!(catalog[0].name, "rpm");
        assert_eq!(catalog[0].units, "RPM");
        assert_eq!(catalog[0].value_type, RuntimeValueType::F32);
        assert_ne!(catalog[0].id, 0);

        assert_eq!(
            store.runtime_catalog_for_epoch(second_handle, first.session_epoch()),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::SessionClosed
            ))
        );
        let newer = store
            .bind_runtime_candidate(first_handle, "usb:second", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        assert_ne!(first.session_epoch(), newer.session_epoch());
        assert_eq!(
            store.runtime_catalog_for_epoch(first_handle, first.session_epoch()),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::StaleAuthority
            ))
        );
        store.disconnect_runtime(first_handle).unwrap();
        assert_eq!(
            store.runtime_catalog_for_epoch(first_handle, newer.session_epoch()),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::SessionClosed
            ))
        );
        store.close(first_handle);
        assert_eq!(
            store.runtime_catalog_for_epoch(first_handle, newer.session_epoch()),
            Err(RuntimeRegistryBindingError::Registry(
                RegistryStoreError::InvalidHandle
            ))
        );
    }

    #[test]
    fn epoch_only_read_cycle_fences_reconnect_bad_crc_and_late_frames() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_definition_bundle(handle, &runtime_bundle_import())
            .unwrap();
        let first = store
            .bind_runtime_candidate(handle, "usb:first", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        let (token, request, count) = store
            .prepare_runtime_read_for_epoch(handle, first.session_epoch(), 10, 50)
            .unwrap();
        assert_eq!(count, 11);
        assert_eq!(&request[2..7], &[b'O', 0, 0, 4, 0]);
        assert!(matches!(
            store.prepare_runtime_read_for_epoch(handle, first.session_epoch(), 20, 50),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::Acquisition(
                    crate::runtime_acquisition_session::RuntimeAcquisitionError::RequestAlreadyPending
                )
            ))
        ));
        let (_, accepted) = store
            .finish_runtime_read_for_epoch(
                handle,
                first.session_epoch(),
                token.sequence,
                21,
                &runtime_response(810.0),
            )
            .unwrap();
        assert_eq!(accepted.samples[0].value, 810.0);

        let (bad, _, _) = store
            .prepare_runtime_read_for_epoch(handle, first.session_epoch(), 30, 50)
            .unwrap();
        let mut corrupt = runtime_response(1000.0);
        *corrupt.last_mut().unwrap() ^= 1;
        assert!(matches!(
            store.finish_runtime_read_for_epoch(
                handle,
                first.session_epoch(),
                bad.sequence,
                31,
                &corrupt
            ),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::Acquisition(_)
            ))
        ));
        let prior = store
            .latest_runtime_batch(handle, &first, 32, 500)
            .unwrap()
            .unwrap();
        assert_ne!(
            prior.validity,
            crate::runtime_acquisition_session::RuntimeSampleState::Fresh
        );

        let (late, _, _) = store
            .prepare_runtime_read_for_epoch(handle, first.session_epoch(), 40, 50)
            .unwrap();
        assert!(matches!(
            store.finish_runtime_read_for_epoch(
                handle,
                first.session_epoch(),
                late.sequence,
                90,
                &runtime_response(1400.0)
            ),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::Acquisition(
                    crate::runtime_acquisition_session::RuntimeAcquisitionError::RequestTimedOut
                )
            ))
        ));
        let newer = store
            .bind_runtime_candidate(handle, "usb:second", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        assert_ne!(first.session_epoch(), newer.session_epoch());
        assert_eq!(
            store.prepare_runtime_read_for_epoch(handle, first.session_epoch(), 100, 50),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::StaleAuthority
            ))
        );
        assert_eq!(
            store.finish_runtime_read_for_epoch(
                handle,
                first.session_epoch(),
                late.sequence,
                100,
                &runtime_response(999.0)
            ),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::StaleAuthority
            ))
        );
        let (current, _, _) = store
            .prepare_runtime_read_for_epoch(handle, newer.session_epoch(), 101, 50)
            .unwrap();
        store.disconnect_runtime(handle).unwrap();
        assert_eq!(
            store.finish_runtime_read_for_epoch(
                handle,
                newer.session_epoch(),
                current.sequence,
                102,
                &runtime_response(1.0)
            ),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::SessionClosed
            ))
        );
    }

    #[test]
    fn wrong_signature_or_generation_revokes_prior_registry_runtime_lease() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_definition_bundle(handle, &runtime_bundle_import())
            .unwrap();
        let prior = store
            .bind_runtime_candidate(handle, "usb:one", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        let (old_token, _) = store.begin_runtime_read(handle, &prior, 0, 100).unwrap();
        assert_eq!(
            store.bind_runtime_candidate(handle, "usb:one", RUNTIME_SIGNATURE, 256, 801),
            Err(RuntimeRegistryBindingError::GenerationMismatch)
        );
        assert_eq!(
            store.complete_runtime_read(handle, &prior, old_token, 1, &runtime_response(930.0)),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::SessionClosed
            ))
        );
        let next = store
            .bind_runtime_candidate(handle, "usb:one", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        assert_ne!(prior.session_epoch(), next.session_epoch());
        assert_eq!(
            store.bind_runtime_candidate(
                handle,
                "usb:two",
                b"rusEFI registry.bundle.801",
                256,
                800
            ),
            Err(RuntimeRegistryBindingError::Registry(
                RegistryStoreError::Authority(RuntimeProfileAuthorityError::UnknownController)
            ))
        );
        assert_eq!(
            store.begin_runtime_read(handle, &next, 2, 100),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::SessionClosed
            ))
        );
    }

    #[test]
    fn missing_or_unsupported_registry_runtime_bundle_fails_closed() {
        let mut store = ProfileRegistryStore::new();
        let handle = store.create();
        store
            .register_compiled_import(handle, &compiled_import())
            .unwrap();
        assert_eq!(
            store.bind_runtime_candidate(
                handle,
                "usb:one",
                b"rusEFI registry.import.700",
                256,
                700
            ),
            Err(RuntimeRegistryBindingError::Registry(
                RegistryStoreError::MissingCompiledBundle("rusEFI registry.import.700".to_string())
            ))
        );
        let handle = store.create();
        let mut unsupported = runtime_bundle_import();
        unsupported.includes.insert(
            "bundle.inc".to_string(),
            format!(
                "{BUNDLE_INCLUDE}\n[OutputChannels]\nochGetCommand = \"O%2o%2c\"\nochBlockSize = 4\nrpm = {{ 100 + 1 }}\n"
            ),
        );
        store
            .register_definition_bundle(handle, &unsupported)
            .unwrap();
        assert_eq!(
            store.bind_runtime_candidate(handle, "usb:one", RUNTIME_SIGNATURE, 256, 800),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::Definition(
                    crate::runtime_definition_catalog::RuntimeDefinitionError::UnsupportedChannel(
                        "rpm".to_string()
                    )
                )
            ))
        );
    }

    #[test]
    fn registry_close_mutation_and_cross_handle_lease_are_fail_closed() {
        let mut store = ProfileRegistryStore::new();
        let one = store.create();
        let two = store.create();
        store
            .register_definition_bundle(one, &runtime_bundle_import())
            .unwrap();
        store
            .register_definition_bundle(two, &runtime_bundle_import())
            .unwrap();
        let first = store
            .bind_runtime_candidate(one, "usb:one", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        let second = store
            .bind_runtime_candidate(two, "usb:two", RUNTIME_SIGNATURE, 256, 800)
            .unwrap();
        assert_eq!(
            store.begin_runtime_read(two, &first, 1, 100),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::StaleAuthority
            ))
        );
        store.register_recognized_signature(one, "rusEFI other.900");
        assert_eq!(
            store.latest_runtime_batch(one, &first, 2, 100),
            Err(RuntimeRegistryBindingError::Bound(
                BoundRuntimeError::SessionClosed
            ))
        );
        store.close(two);
        assert_eq!(
            store.begin_runtime_read(two, &second, 1, 100),
            Err(RuntimeRegistryBindingError::Registry(
                RegistryStoreError::InvalidHandle
            ))
        );
        let replacement = store.create();
        assert_ne!(two, replacement);
    }

    #[test]
    fn authority_error_type_keeps_profile_selection_categories_explicit() {
        let _ = ProfileSelectionError::UnknownController;
    }
}
