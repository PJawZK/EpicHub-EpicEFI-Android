use crate::resolved_profile_source::ProfileResolveError;
use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ProfileIdentity {
    pub profile_name: &'static str,
    pub signature: &'static str,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<&'static str>,
}

impl ProfileIdentity {
    pub fn exact_signature_matches(&self, live_signature: &str) -> bool {
        self.signature == live_signature
    }
}

/// Definition-resolved ECU profile contract.
///
/// Transport discovery and board-name recognition are intentionally absent here. A profile owns
/// exact firmware/profile identity, raw source geometry and the conversion from validated source
/// pages into EpicHub's semantic TuneSnapshot model.
pub trait ResolvedEcuProfile {
    fn identity(&self) -> ProfileIdentity;
    fn page_sizes(&self) -> &'static [usize];

    fn resolve_pages(
        &self,
        pages: &[&[u8]],
        origin: TuneSnapshotOrigin,
    ) -> Result<TuneSnapshot, ProfileResolveError>;
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
        identity: ProfileIdentity,
    }

    impl ResolvedEcuProfile for TestProfile {
        fn identity(&self) -> ProfileIdentity {
            self.identity
        }

        fn page_sizes(&self) -> &'static [usize] {
            &[]
        }

        fn resolve_pages(
            &self,
            pages: &[&[u8]],
            origin: TuneSnapshotOrigin,
        ) -> Result<TuneSnapshot, ProfileResolveError> {
            if !pages.is_empty() {
                return Err(ProfileResolveError::InvalidPageCount {
                    expected: 0,
                    actual: pages.len(),
                });
            }
            TuneSnapshot::new(self.identity.profile_generation, origin, [])
                .map_err(ProfileResolveError::InvalidTune)
        }
    }

    const FIRST: TestProfile = TestProfile {
        identity: ProfileIdentity {
            profile_name: "profile-a",
            signature: "firmware-a.100",
            profile_generation: 100,
            definition_fingerprint: Some("fingerprint-a"),
        },
    };

    const SECOND: TestProfile = TestProfile {
        identity: ProfileIdentity {
            profile_name: "profile-b",
            signature: "firmware-b.200",
            profile_generation: 200,
            definition_fingerprint: Some("fingerprint-b"),
        },
    };

    #[test]
    fn exact_signature_match_selects_between_profiles_without_board_switching() {
        let profiles: [&dyn ResolvedEcuProfile; 2] = [&FIRST, &SECOND];
        let matched = find_exact_profile("firmware-b.200", profiles).unwrap();
        assert_eq!(matched.identity().profile_name, "profile-b");
    }

    #[test]
    fn signature_prefix_or_near_match_does_not_authorize_a_profile() {
        let profiles: [&dyn ResolvedEcuProfile; 2] = [&FIRST, &SECOND];
        assert!(find_exact_profile("firmware-b", profiles).is_none());
        assert!(find_exact_profile("firmware-b.201", profiles).is_none());
        assert!(find_exact_profile("FIRMWARE-B.200", profiles).is_none());
    }
}
