use crate::ecu_profile::{EcuProfileResolveError, ResolvedEcuProfile};
use crate::engine::{replace_tune_session, replace_tune_source, with_tune_session};
use crate::mega144h7_profile::Mega144h7Profile;
use crate::resolved_profile_source::Mega144h7ResolvedModes;
use crate::tune_catalog_abi;
use crate::tune_session::TuneSession;
use crate::tune_source_image::TuneSourcePageImage;
use crate::tuning::TuneSnapshotOrigin;

#[derive(Clone, Debug, PartialEq)]
pub enum TuneSourceInstallError {
    InvalidHandle,
    Resolve(EcuProfileResolveError),
}

impl From<EcuProfileResolveError> for TuneSourceInstallError {
    fn from(value: EcuProfileResolveError) -> Self {
        Self::Resolve(value)
    }
}

fn install_pages_with_origin(
    handle: i64,
    profile: &dyn ResolvedEcuProfile,
    pages: &[&[u8]],
    origin: TuneSnapshotOrigin,
) -> Result<u64, TuneSourceInstallError> {
    let snapshot = profile.resolve_pages(pages, origin)?;
    let profile_generation = snapshot.profile_generation;
    if !replace_tune_session(handle, TuneSession::from_snapshot(snapshot)) {
        return Err(TuneSourceInstallError::InvalidHandle);
    }
    Ok(profile_generation)
}

/// Resolves and validates a complete imported source before atomically replacing the active
/// TuneSession authority. Imported sources have no retained live-controller page image, so any
/// previous live page image is cleared together with the semantic session replacement.
pub fn install_imported_pages(
    handle: i64,
    profile: &dyn ResolvedEcuProfile,
    pages: &[&[u8]],
) -> Result<u64, TuneSourceInstallError> {
    install_pages_with_origin(handle, profile, pages, TuneSnapshotOrigin::ImportedFile)
}

/// Installs a complete page set acquired directly from the selected live controller. Decode and
/// validation finish before the active semantic TuneSession and exact source-page image are
/// replaced together, so any failure preserves both authorities exactly.
pub fn install_live_pages(
    handle: i64,
    profile: &dyn ResolvedEcuProfile,
    pages: &[&[u8]],
) -> Result<u64, TuneSourceInstallError> {
    let snapshot = profile.resolve_pages(pages, TuneSnapshotOrigin::LiveController)?;
    let profile_generation = snapshot.profile_generation;
    let source_image = TuneSourcePageImage::from_pages(pages);
    if !replace_tune_source(handle, TuneSession::from_snapshot(snapshot), source_image) {
        return Err(TuneSourceInstallError::InvalidHandle);
    }
    Ok(profile_generation)
}

/// Compatibility adapter for the already-qualified Mega144H7 source boundary. New source-provider
/// code should select a ResolvedEcuProfile and call install_imported_pages instead of adding another
/// board-specific install function.
pub fn install_mega144h7_imported_pages(
    handle: i64,
    page_1: &[u8],
    page_2: &[u8],
    modes: Mega144h7ResolvedModes,
) -> Result<u64, TuneSourceInstallError> {
    let profile = Mega144h7Profile { modes };
    install_imported_pages(handle, &profile, &[page_1, page_2])
}

pub fn tune_catalog_bytes(handle: i64) -> Option<Vec<u8>> {
    with_tune_session(handle, |session| {
        tune_catalog_abi::encode(session.baseline(), session.revision())
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile::{ProfileIdentity, ResolvedEcuProfile};
    use crate::engine::{close_engine, create_engine, tune_session_epoch, with_tune_source_image};
    use crate::offline_tune_source::OFFLINE_SOURCE_PRIMARY_TABLE_ID;
    use crate::resolved_profile_source::{
        MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, ProfileResolveError, TABLE_ID_VE,
    };
    use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

    fn valid_pages() -> (Vec<u8>, Vec<u8>) {
        let mut page_1 = vec![0; MEGA144H7_PAGE_1_SIZE];
        page_1[18_848..19_872].fill(147);
        (page_1, vec![0; MEGA144H7_PAGE_2_SIZE])
    }

    struct AlternateTestProfile;

    impl ResolvedEcuProfile for AlternateTestProfile {
        fn identity(&self) -> ProfileIdentity<'_> {
            ProfileIdentity {
                profile_name: "alternate-test-profile",
                signature: "alternate-firmware.9001",
                profile_generation: 9_001,
                definition_fingerprint: Some("alternate-test-fingerprint"),
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
            TuneSnapshot::new(self.identity().profile_generation, origin, [])
                .map_err(ProfileResolveError::InvalidTune)
                .map_err(EcuProfileResolveError::Profile)
        }
    }

    #[test]
    fn successful_install_replaces_baseline_draft_and_history_atomically() {
        let handle = create_engine();
        with_tune_session(handle, |session| {
            session
                .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
                .unwrap();
            assert_eq!(session.revision(), 1);
        })
        .unwrap();
        assert_eq!(tune_session_epoch(handle), Some(1));

        let (page_1, page_2) = valid_pages();
        install_mega144h7_imported_pages(
            handle,
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
        )
        .unwrap();

        assert_eq!(tune_session_epoch(handle), Some(2));
        with_tune_session(handle, |session| {
            assert_eq!(session.revision(), 0);
            assert!(
                session
                    .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                    .is_err()
            );
            assert!(session.projection_bytes(TABLE_ID_VE).is_ok());
            assert!(session.undo().is_err());
        })
        .unwrap();
        close_engine(handle);
    }

    #[test]
    fn failed_decode_preserves_existing_session_history_and_epoch() {
        let handle = create_engine();
        with_tune_session(handle, |session| {
            session
                .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
                .unwrap();
        })
        .unwrap();
        let epoch = tune_session_epoch(handle).unwrap();

        let (mut page_1, page_2) = valid_pages();
        page_1.pop();
        assert!(
            install_mega144h7_imported_pages(
                handle,
                &page_1,
                &page_2,
                Mega144h7ResolvedModes::default(),
            )
            .is_err()
        );

        assert_eq!(tune_session_epoch(handle), Some(epoch));
        with_tune_session(handle, |session| {
            assert_eq!(session.revision(), 1);
            assert!(
                session
                    .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                    .is_ok()
            );
            assert_eq!(session.undo().unwrap(), 2);
        })
        .unwrap();
        close_engine(handle);
    }

    #[test]
    fn imported_install_clears_a_previous_live_source_image() {
        let handle = create_engine();
        install_live_pages(handle, &AlternateTestProfile, &[]).unwrap();
        assert!(with_tune_source_image(handle, |image| image.is_some()).unwrap());

        install_imported_pages(handle, &AlternateTestProfile, &[]).unwrap();
        assert!(!with_tune_source_image(handle, |image| image.is_some()).unwrap());
        close_engine(handle);
    }

    #[test]
    fn source_install_is_engine_local() {
        let first = create_engine();
        let second = create_engine();
        let (page_1, page_2) = valid_pages();
        install_mega144h7_imported_pages(
            first,
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
        )
        .unwrap();

        with_tune_session(first, |session| {
            assert!(session.projection_bytes(TABLE_ID_VE).is_ok());
        })
        .unwrap();
        with_tune_session(second, |session| {
            assert!(session.projection_bytes(TABLE_ID_VE).is_err());
            assert!(
                session
                    .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                    .is_ok()
            );
        })
        .unwrap();
        assert_eq!(tune_session_epoch(first), Some(2));
        assert_eq!(tune_session_epoch(second), Some(1));
        close_engine(first);
        close_engine(second);
    }

    #[test]
    fn generic_install_accepts_a_second_profile_without_board_specific_routing() {
        let handle = create_engine();
        let generation = install_imported_pages(handle, &AlternateTestProfile, &[]).unwrap();
        assert_eq!(generation, 9_001);
        assert_eq!(tune_session_epoch(handle), Some(2));
        with_tune_session(handle, |session| {
            assert_eq!(session.baseline().profile_generation, 9_001);
            assert_eq!(session.baseline().origin, TuneSnapshotOrigin::ImportedFile);
            assert_eq!(session.revision(), 0);
        })
        .unwrap();
        close_engine(handle);
    }

    #[test]
    fn live_install_marks_controller_origin_and_resets_history_atomically() {
        let handle = create_engine();
        with_tune_session(handle, |session| {
            session
                .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
                .unwrap();
        })
        .unwrap();

        let generation = install_live_pages(handle, &AlternateTestProfile, &[]).unwrap();
        assert_eq!(generation, 9_001);
        assert_eq!(tune_session_epoch(handle), Some(2));
        with_tune_session(handle, |session| {
            assert_eq!(
                session.baseline().origin,
                TuneSnapshotOrigin::LiveController
            );
            assert_eq!(session.revision(), 0);
            assert!(session.undo().is_err());
        })
        .unwrap();
        close_engine(handle);
    }

    #[test]
    fn catalog_tracks_active_session_generation_revision_and_tables() {
        let handle = create_engine();
        let initial = tune_catalog_bytes(handle).unwrap();
        assert_eq!(u32::from_le_bytes(initial[12..16].try_into().unwrap()), 1);

        let (page_1, page_2) = valid_pages();
        let generation = install_mega144h7_imported_pages(
            handle,
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
        )
        .unwrap();
        let catalog = tune_catalog_bytes(handle).unwrap();
        assert_eq!(u32::from_le_bytes(catalog[12..16].try_into().unwrap()), 4);
        assert_eq!(
            u64::from_le_bytes(catalog[16..24].try_into().unwrap()),
            generation
        );
        assert_eq!(u64::from_le_bytes(catalog[24..32].try_into().unwrap()), 0);
        close_engine(handle);
    }

    #[test]
    fn invalid_handle_does_not_create_a_session_or_epoch() {
        let (page_1, page_2) = valid_pages();
        assert_eq!(
            install_mega144h7_imported_pages(
                i64::MAX,
                &page_1,
                &page_2,
                Mega144h7ResolvedModes::default(),
            ),
            Err(TuneSourceInstallError::InvalidHandle)
        );
        assert!(tune_catalog_bytes(i64::MAX).is_none());
        assert!(tune_session_epoch(i64::MAX).is_none());
    }
}
