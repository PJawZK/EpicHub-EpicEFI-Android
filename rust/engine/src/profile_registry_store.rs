use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use crate::ecu_profile::ResolvedEcuProfile;
use crate::ecu_profile_registry::{OwnedProfileRegistry, ProfileRegistryError};
use crate::runtime_profile_authority::{
    RuntimeProfileAuthorityError, select_profile_from_runtime_response,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SelectedProfileSummary {
    pub profile_generation: u64,
}

pub struct ProfileRegistryStore {
    next_handle: i64,
    registries: HashMap<i64, OwnedProfileRegistry>,
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
        self.registries.insert(handle, OwnedProfileRegistry::new());
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
        let Some(registry) = self.registries.get_mut(&handle) else {
            return false;
        };
        registry.register_recognized_signature(signature);
        true
    }

    pub fn register_profile<P>(&mut self, handle: i64, profile: P) -> Result<(), RegistryStoreError>
    where
        P: ResolvedEcuProfile + 'static,
    {
        let registry = self
            .registries
            .get_mut(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        registry
            .register_profile(profile)
            .map_err(RegistryStoreError::Registry)
    }

    pub fn select_from_runtime_response(
        &self,
        handle: i64,
        response: &[u8],
        max_response_bytes: usize,
    ) -> Result<SelectedProfileSummary, RegistryStoreError> {
        let registry = self
            .registries
            .get(&handle)
            .ok_or(RegistryStoreError::InvalidHandle)?;
        let profile = select_profile_from_runtime_response(registry, response, max_response_bytes)
            .map_err(RegistryStoreError::Authority)?;
        Ok(SelectedProfileSummary {
            profile_generation: profile.identity().profile_generation,
        })
    }
}

impl Default for ProfileRegistryStore {
    fn default() -> Self {
        Self::new()
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum RegistryStoreError {
    InvalidHandle,
    Registry(ProfileRegistryError),
    Authority(RuntimeProfileAuthorityError),
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
    use crate::runtime_identity_probe::RuntimeProbeError;
    use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

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
