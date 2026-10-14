use crate::ecu_profile::{EcuProfileResolveError, ProfileIdentity, ResolvedEcuProfile};
use crate::resolved_profile_source::{
    MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, MEGA144H7_PROFILE_FINGERPRINT,
    MEGA144H7_PROFILE_GENERATION, MEGA144H7_PROFILE_NAME, MEGA144H7_PROFILE_SIGNATURE,
    Mega144h7ResolvedModes, resolve_mega144h7_tune_pages,
};
use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

const MEGA144H7_PAGE_SIZES: [usize; 2] = [MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE];

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct Mega144h7Profile {
    pub modes: Mega144h7ResolvedModes,
}

impl ResolvedEcuProfile for Mega144h7Profile {
    fn identity(&self) -> ProfileIdentity<'_> {
        ProfileIdentity {
            profile_name: MEGA144H7_PROFILE_NAME,
            signature: MEGA144H7_PROFILE_SIGNATURE,
            profile_generation: MEGA144H7_PROFILE_GENERATION,
            definition_fingerprint: Some(MEGA144H7_PROFILE_FINGERPRINT),
        }
    }

    fn page_sizes(&self) -> &[usize] {
        &MEGA144H7_PAGE_SIZES
    }

    fn resolve_pages(
        &self,
        pages: &[&[u8]],
        origin: TuneSnapshotOrigin,
    ) -> Result<TuneSnapshot, EcuProfileResolveError> {
        if pages.len() != MEGA144H7_PAGE_SIZES.len() {
            return Err(EcuProfileResolveError::InvalidPageCount {
                expected: MEGA144H7_PAGE_SIZES.len(),
                actual: pages.len(),
            });
        }
        resolve_mega144h7_tune_pages(pages[0], pages[1], self.modes, origin).map_err(Into::into)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exposes_definition_identity_and_page_geometry() {
        let profile = Mega144h7Profile::default();
        assert_eq!(profile.identity().signature, MEGA144H7_PROFILE_SIGNATURE);
        assert_eq!(
            profile.page_sizes(),
            &[MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE]
        );
    }

    #[test]
    fn rejects_wrong_page_count_before_profile_decode() {
        let profile = Mega144h7Profile::default();
        assert_eq!(
            profile.resolve_pages(&[&[]], TuneSnapshotOrigin::ImportedFile),
            Err(EcuProfileResolveError::InvalidPageCount {
                expected: 2,
                actual: 1,
            })
        );
    }
}
