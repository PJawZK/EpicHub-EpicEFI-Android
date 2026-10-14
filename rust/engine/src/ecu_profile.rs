use crate::ecu_compiled_snapshot::CompiledSnapshotError;
use crate::resolved_profile_source::ProfileResolveError;
use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ProfileIdentity<'a> {
    pub profile_name: &'a str,
    pub signature: &'a str,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<&'a str>,
}

impl ProfileIdentity<'_> {
    pub fn exact_signature_matches(&self, live_signature: &str) -> bool {
        self.signature == live_signature
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum EcuProfileResolveError {
    InvalidPageCount {
        expected: usize,
        actual: usize,
    },
    InvalidPageSize {
        page: usize,
        expected: usize,
        actual: usize,
    },
    Profile(ProfileResolveError),
    Compiled(CompiledSnapshotError),
}

impl From<ProfileResolveError> for EcuProfileResolveError {
    fn from(value: ProfileResolveError) -> Self {
        Self::Profile(value)
    }
}

impl From<CompiledSnapshotError> for EcuProfileResolveError {
    fn from(value: CompiledSnapshotError) -> Self {
        Self::Compiled(value)
    }
}

pub trait ResolvedEcuProfile: Send + Sync {
    fn identity(&self) -> ProfileIdentity<'_>;
    fn page_sizes(&self) -> &[usize];

    fn resolve_pages(
        &self,
        pages: &[&[u8]],
        origin: TuneSnapshotOrigin,
    ) -> Result<TuneSnapshot, EcuProfileResolveError>;
}

pub fn find_exact_profile<'a>(
    live_signature: &str,
    profiles: impl IntoIterator<Item = &'a dyn ResolvedEcuProfile>,
) -> Option<&'a dyn ResolvedEcuProfile> {
    profiles
        .into_iter()
        .find(|profile| profile.identity().exact_signature_matches(live_signature))
}

#[cfg(test)]
mod tests {
    use super::*;

    struct TestProfile {
        profile_name: String,
        signature: String,
        profile_generation: u64,
        fingerprint: Option<String>,
        page_sizes: Vec<usize>,
    }

    impl ResolvedEcuProfile for TestProfile {
        fn identity(&self) -> ProfileIdentity<'_> {
            ProfileIdentity {
                profile_name: &self.profile_name,
                signature: &self.signature,
                profile_generation: self.profile_generation,
                definition_fingerprint: self.fingerprint.as_deref(),
            }
        }

        fn page_sizes(&self) -> &[usize] {
            &self.page_sizes
        }

        fn resolve_pages(
            &self,
            pages: &[&[u8]],
            origin: TuneSnapshotOrigin,
        ) -> Result<TuneSnapshot, EcuProfileResolveError> {
            if pages.len() != self.page_sizes.len() {
                return Err(EcuProfileResolveError::InvalidPageCount {
                    expected: self.page_sizes.len(),
                    actual: pages.len(),
                });
            }
            TuneSnapshot::new(self.profile_generation, origin, [])
                .map_err(ProfileResolveError::InvalidTune)
                .map_err(EcuProfileResolveError::Profile)
        }
    }

    fn profile(name: &str, signature: &str, generation: u64) -> TestProfile {
        TestProfile {
            profile_name: name.to_string(),
            signature: signature.to_string(),
            profile_generation: generation,
            fingerprint: None,
            page_sizes: Vec::new(),
        }
    }

    #[test]
    fn exact_signature_match_supports_runtime_owned_profiles() {
        let first = profile("profile-a", "firmware-a.100", 100);
        let second = profile("profile-b", "firmware-b.200", 200);
        let profiles: [&dyn ResolvedEcuProfile; 2] = [&first, &second];
        let matched = find_exact_profile("firmware-b.200", profiles).unwrap();
        assert_eq!(matched.identity().profile_name, "profile-b");
    }

    #[test]
    fn signature_prefix_or_near_match_does_not_authorize_a_profile() {
        let first = profile("profile-a", "firmware-a.100", 100);
        let second = profile("profile-b", "firmware-b.200", 200);
        let profiles: [&dyn ResolvedEcuProfile; 2] = [&first, &second];
        assert!(find_exact_profile("firmware-b", profiles).is_none());
        assert!(find_exact_profile("firmware-b.201", profiles).is_none());
        assert!(find_exact_profile("FIRMWARE-B.200", profiles).is_none());
    }
}
