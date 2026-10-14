use crate::ecu_profile::ResolvedEcuProfile;
use crate::ecu_profile_registry::{ProfileRegistry, ProfileSelectionError};
use crate::runtime_identity_probe::{RuntimeProbeError, runtime_identity_from_response};

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum RuntimeProfileAuthorityError {
    Identity(RuntimeProbeError),
    UnknownController,
    RecognizedControllerWithoutProfile,
}

pub fn select_profile_from_runtime_response<'registry>(
    registry: &'registry ProfileRegistry<'registry>,
    response: &[u8],
    max_response_bytes: usize,
) -> Result<&'registry dyn ResolvedEcuProfile, RuntimeProfileAuthorityError> {
    let identity = runtime_identity_from_response(response, max_response_bytes)
        .map_err(RuntimeProfileAuthorityError::Identity)?;

    registry
        .select_exact_profile(&identity)
        .map_err(|error| match error {
            ProfileSelectionError::UnknownController => RuntimeProfileAuthorityError::UnknownController,
            ProfileSelectionError::RecognizedControllerWithoutProfile => {
                RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile
            }
        })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::compiled_ecu_profile::test_support::mega_profile;
    use crate::ecu_profile::{EcuProfileResolveError, ProfileIdentity};
    use crate::resolved_profile_source::{MEGA144H7_PROFILE_GENERATION, MEGA144H7_PROFILE_SIGNATURE};
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
            _pages: &[&[u8]],
            _origin: TuneSnapshotOrigin,
        ) -> Result<TuneSnapshot, EcuProfileResolveError> {
            unreachable!("selection tests do not resolve calibration pages")
        }
    }

    #[test]
    fn exact_validated_signature_selects_the_registered_compiled_profile() {
        let profile = mega_profile();
        let mut registry = ProfileRegistry::new();
        registry.register_profile(&profile).unwrap();

        let selected = select_profile_from_runtime_response(
            &registry,
            MEGA144H7_PROFILE_SIGNATURE.as_bytes(),
            256,
        )
        .unwrap();

        assert_eq!(selected.identity().signature, MEGA144H7_PROFILE_SIGNATURE);
        assert_eq!(
            selected.identity().profile_generation,
            MEGA144H7_PROFILE_GENERATION
        );
    }

    #[test]
    fn valid_but_non_exact_signature_is_unknown_even_when_it_looks_related() {
        let profile = mega_profile();
        let mut registry = ProfileRegistry::new();
        registry.register_profile(&profile).unwrap();

        assert!(matches!(
            select_profile_from_runtime_response(
                &registry,
                b"rusEFI master.2026.08.26.MEGA144H7",
                256,
            ),
            Err(RuntimeProfileAuthorityError::UnknownController)
        ));
    }

    #[test]
    fn recognized_signature_without_profile_remains_distinct_from_unknown_controller() {
        let mut registry = ProfileRegistry::new();
        registry.register_recognized_signature("rusEFI known.no-profile.300");

        assert_eq!(
            select_profile_from_runtime_response(
                &registry,
                b"rusEFI known.no-profile.300",
                256,
            )
            .map(|_| ()),
            Err(RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile)
        );
        assert_eq!(
            select_profile_from_runtime_response(&registry, b"rusEFI unknown.400", 256)
                .map(|_| ()),
            Err(RuntimeProfileAuthorityError::UnknownController)
        );
    }

    #[test]
    fn invalid_runtime_response_never_reaches_profile_selection() {
        static IMPOSSIBLE: TestProfile = TestProfile {
            name: "impossible",
            signature: "",
            generation: 1,
        };
        let mut registry = ProfileRegistry::new();
        registry.register_profile(&IMPOSSIBLE).unwrap();

        assert_eq!(
            select_profile_from_runtime_response(&registry, &[0xff], 256).map(|_| ()),
            Err(RuntimeProfileAuthorityError::Identity(
                RuntimeProbeError::InvalidUtf8
            ))
        );
        assert_eq!(
            select_profile_from_runtime_response(&registry, b"", 256).map(|_| ()),
            Err(RuntimeProfileAuthorityError::Identity(
                RuntimeProbeError::EmptySignature
            ))
        );
    }
}
