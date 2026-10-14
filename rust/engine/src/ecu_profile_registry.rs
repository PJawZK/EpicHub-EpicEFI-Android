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

pub struct ProfileRegistry {
    profiles: Vec<Box<dyn ResolvedEcuProfile>>,
    recognized_signatures: HashSet<String>,
}

impl ProfileRegistry {
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

    pub fn select_exact_profile(
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

        if self
            .recognized_signatures
            .contains(runtime_identity.signature.as_str())
        {
            Err(ProfileSelectionError::RecognizedControllerWithoutProfile)
        } else {
            Err(ProfileSelectionError::UnknownController)
        }
    }

    pub fn profile_count(&self) -> usize {
        self.profiles.len()
    }
}

impl Default for ProfileRegistry {
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
        name: &'static str,
        signature: &'static str,
        generation: u64,
    }

    impl ResolvedEcuProfile for TestProfile {
        fn identity(&self) -> ProfileIdentity<'_> {
            ProfileIdentity {
                profile_name: self.name,
                signature: self.signature,
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

    const FIRST: TestProfile = TestProfile {
        name: "first",
        signature: "rusEFI target.first.100",
        generation: 100,
    };
    const SECOND: TestProfile = TestProfile {
        name: "second",
        signature: "rusEFI target.second.200",
        generation: 200,
    };

    #[test]
    fn selects_only_the_exact_full_live_signature() {
        let mut registry = ProfileRegistry::new();
        registry.register_profile(FIRST).unwrap();
        registry.register_profile(SECOND).unwrap();

        let matched = registry
            .select_exact_profile(&RuntimeControllerIdentity::from_signature(SECOND.signature))
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
        let mut registry = ProfileRegistry::new();
        registry.register_recognized_signature("rusEFI known.no-profile.300");

        assert!(matches!(
            registry.select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI known.no-profile.300"
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
        let mut registry = ProfileRegistry::new();
        registry.register_profile(FIRST).unwrap();
        let identity = RuntimeControllerIdentity {
            signature: FIRST.signature.to_string(),
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
        let alias = TestProfile {
            name: "alias",
            signature: FIRST.signature,
            generation: 999,
        };
        let mut registry = ProfileRegistry::new();
        registry.register_profile(FIRST).unwrap();
        assert_eq!(
            registry.register_profile(alias),
            Err(ProfileRegistryError::DuplicateProfileSignature(
                FIRST.signature.to_string()
            ))
        );
        assert_eq!(registry.profile_count(), 1);
    }

    #[test]
    fn accepts_runtime_owned_boxed_profiles_without_borrowed_lifetimes() {
        let profile = Box::new(TestProfile {
            name: "runtime-loaded",
            signature: "rusEFI runtime.loaded.500",
            generation: 500,
        });
        let mut registry = ProfileRegistry::new();
        registry.register_boxed_profile(profile).unwrap();

        let selected = registry
            .select_exact_profile(&RuntimeControllerIdentity::from_signature(
                "rusEFI runtime.loaded.500",
            ))
            .unwrap();
        assert_eq!(selected.identity().profile_name, "runtime-loaded");
    }
}
