use std::collections::HashSet;

use crate::ecu_profile::ResolvedEcuProfile;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RuntimeControllerIdentity {
    pub signature: String,
    pub firmware_info: Option<String>,
    pub board_info: Option<String>,
}

impl RuntimeControllerIdentity {
    pub fn from_signature(signature: impl Into<String>) -> Self {
        Self {
            signature: signature.into(),
            firmware_info: None,
            board_info: None,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum ProfileRegistryError {
    DuplicateProfileSignature(String),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProfileSelectionError {
    UnknownController,
    RecognizedControllerWithoutProfile,
}

pub trait ExactProfileSelector {
    fn select_exact_profile(
        &self,
        runtime_identity: &RuntimeControllerIdentity,
    ) -> Result<&dyn ResolvedEcuProfile, ProfileSelectionError>;
}

fn selection_error(
    recognized_signatures: &HashSet<String>,
    runtime_identity: &RuntimeControllerIdentity,
) -> ProfileSelectionError {
    if recognized_signatures.contains(runtime_identity.signature.as_str()) {
        ProfileSelectionError::RecognizedControllerWithoutProfile
    } else {
        ProfileSelectionError::UnknownController
    }
}

pub struct ProfileRegistry<'a> {
    profiles: Vec<&'a dyn ResolvedEcuProfile>,
    recognized_signatures: HashSet<String>,
}

impl<'a> ProfileRegistry<'a> {
    pub fn new() -> Self {
        Self {
            profiles: Vec::new(),
            recognized_signatures: HashSet::new(),
        }
    }

    pub fn register_recognized_signature(&mut self, signature: impl Into<String>) {
        self.recognized_signatures.insert(signature.into());
    }

    pub fn register_profile(
        &mut self,
        profile: &'a dyn ResolvedEcuProfile,
    ) -> Result<(), ProfileRegistryError> {
        let signature = profile.identity().signature;
        if self
            .profiles
            .iter()
            .any(|registered| registered.identity().signature == signature)
        {
            return Err(ProfileRegistryError::DuplicateProfileSignature(
                signature.to_string(),
            ));
        }
        self.recognized_signatures.insert(signature.to_string());
        self.profiles.push(profile);
        Ok(())
    }

    pub fn profile_count(&self) -> usize {
        self.profiles.len()
    }
}

impl ExactProfileSelector for ProfileRegistry<'_> {
    fn select_exact_profile(
        &self,
        runtime_identity: &RuntimeControllerIdentity,
    ) -> Result<&dyn ResolvedEcuProfile, ProfileSelectionError> {
        if let Some(profile) = self.profiles.iter().copied().find(|profile| {
            profile
                .identity()
                .exact_signature_matches(&runtime_identity.signature)
        }) {
            return Ok(profile);
        }

        Err(selection_error(
            &self.recognized_signatures,
            runtime_identity,
        ))
    }
}

impl<'a> Default for ProfileRegistry<'a> {
    fn default() -> Self {
        Self::new()
    }
}

pub struct OwnedProfileRegistry {
    profiles: Vec<Box<dyn ResolvedEcuProfile>>,
    recognized_signatures: HashSet<String>,
}

impl OwnedProfileRegistry {
    pub fn new() -> Self {
        Self {
            profiles: Vec::new(),
            recognized_signatures: HashSet::new(),
        }
    }

    pub fn register_recognized_signature(&mut self, signature: impl Into<String>) {
        self.recognized_signatures.insert(signature.into());
    }

    pub fn register_profile<P>(&mut self, profile: P) -> Result<(), ProfileRegistryError>
    where
        P: ResolvedEcuProfile + 'static,
    {
        self.register_boxed_profile(Box::new(profile))
    }

    pub fn register_boxed_profile(
        &mut self,
        profile: Box<dyn ResolvedEcuProfile>,
    ) -> Result<(), ProfileRegistryError> {
        let signature = profile.identity().signature.to_string();
        if self
            .profiles
            .iter()
            .any(|registered| registered.identity().signature == signature)
        {
            return Err(ProfileRegistryError::DuplicateProfileSignature(signature));
        }
        self.recognized_signatures.insert(signature);
        self.profiles.push(profile);
        Ok(())
    }

    pub fn profile_count(&self) -> usize {
        self.profiles.len()
    }
}

impl ExactProfileSelector for OwnedProfileRegistry {
    fn select_exact_profile(
        &self,
        runtime_identity: &RuntimeControllerIdentity,
    ) -> Result<&dyn ResolvedEcuProfile, ProfileSelectionError> {
        if let Some(profile) = self.profiles.iter().find(|profile| {
            profile
                .identity()
                .exact_signature_matches(&runtime_identity.signature)
        }) {
            return Ok(profile.as_ref());
        }

        Err(selection_error(
            &self.recognized_signatures,
            runtime_identity,
        ))
    }
}

impl Default for OwnedProfileRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile::{EcuProfileResolveError, ProfileIdentity};
    use crate::resolved_profile_source::ProfileResolveError;
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
            pages: &[&[u8]],
            origin: TuneSnapshotOrigin,
        ) -> Result<TuneSnapshot, EcuProfileResolveError> {
            if !pages.is_empty() {
                return Err(EcuProfileResolveError::InvalidPageCount {
                    expected: 0,
                    actual: pages.len(),
                });
            }
            TuneSnapshot::new(self.generation, origin, [])
                .map_err(ProfileResolveError::InvalidTune)
                .map_err(EcuProfileResolveError::Profile)
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
    fn selects_only_the_exact_full_live_signature() {
        let first = profile("first", "rusEFI target.first.100", 100);
        let second = profile("second", "rusEFI target.second.200", 200);
        let mut registry = ProfileRegistry::new();
        registry.register_profile(&first).unwrap();
        registry.register_profile(&second).unwrap();

        let matched = registry
            .select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI target.second.200",
            ))
            .unwrap();
        assert_eq!(matched.identity().profile_name, "second");

        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI target.second"
            )),
            Err(ProfileSelectionError::UnknownController)
        ));
        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "RUSEFI TARGET.SECOND.200"
            )),
            Err(ProfileSelectionError::UnknownController)
        ));
    }

    #[test]
    fn distinguishes_recognized_controller_without_profile_from_unknown_firmware() {
        let registry_signature = "rusEFI known.no-profile.300";
        let mut registry = ProfileRegistry::new();
        registry.register_recognized_signature(registry_signature);

        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                registry_signature
            )),
            Err(ProfileSelectionError::RecognizedControllerWithoutProfile)
        ));
        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI unknown.400"
            )),
            Err(ProfileSelectionError::UnknownController)
        ));
    }

    #[test]
    fn board_and_firmware_descriptions_do_not_participate_in_authority() {
        let first = profile("first", "rusEFI target.first.100", 100);
        let mut registry = ProfileRegistry::new();
        registry.register_profile(&first).unwrap();
        let identity = RuntimeControllerIdentity {
            signature: first.signature.clone(),
            firmware_info: Some("descriptive firmware text".to_string()),
            board_info: Some("completely different board label".to_string()),
        };

        assert_eq!(
            registry
                .select_exact_profile(&identity)
                .unwrap()
                .identity()
                .profile_name,
            "first"
        );
    }

    #[test]
    fn duplicate_profile_signature_is_rejected_as_ambiguous_authority() {
        let first = profile("first", "rusEFI target.first.100", 100);
        let alias = profile("alias", "rusEFI target.first.100", 999);
        let mut registry = ProfileRegistry::new();
        registry.register_profile(&first).unwrap();
        assert_eq!(
            registry.register_profile(&alias),
            Err(ProfileRegistryError::DuplicateProfileSignature(
                "rusEFI target.first.100".to_string()
            ))
        );
        assert_eq!(registry.profile_count(), 1);
    }

    fn populate_owned_registry() -> OwnedProfileRegistry {
        let mut registry = OwnedProfileRegistry::new();
        registry
            .register_profile(profile("first", "rusEFI owned.first.500", 500))
            .unwrap();
        registry
            .register_profile(profile("second", "rusEFI owned.second.600", 600))
            .unwrap();
        registry.register_recognized_signature("rusEFI owned.no-profile.700");
        registry
    }

    #[test]
    fn owned_registry_keeps_dynamic_profiles_alive_after_registration_scope_ends() {
        let registry = populate_owned_registry();

        let selected = registry
            .select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI owned.second.600",
            ))
            .unwrap();
        assert_eq!(selected.identity().profile_name, "second");
        assert_eq!(selected.identity().profile_generation, 600);
        assert_eq!(registry.profile_count(), 2);
    }

    #[test]
    fn owned_registry_preserves_unknown_no_profile_and_duplicate_semantics() {
        let mut registry = populate_owned_registry();

        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI owned.no-profile.700"
            )),
            Err(ProfileSelectionError::RecognizedControllerWithoutProfile)
        ));
        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI owned.unknown.800"
            )),
            Err(ProfileSelectionError::UnknownController)
        ));
        assert_eq!(
            registry.register_profile(profile("alias", "rusEFI owned.first.500", 999)),
            Err(ProfileRegistryError::DuplicateProfileSignature(
                "rusEFI owned.first.500".to_string()
            ))
        );
        assert_eq!(registry.profile_count(), 2);
    }

    fn assert_send_sync<T: Send + Sync>() {}

    #[test]
    fn owned_registry_is_send_sync_for_future_native_handle_storage() {
        assert_send_sync::<OwnedProfileRegistry>();
    }
}
