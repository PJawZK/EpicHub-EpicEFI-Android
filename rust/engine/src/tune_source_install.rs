use crate::engine::with_tune_session;
use crate::resolved_profile_source::{
    Mega144h7ResolvedModes, ProfileResolveError, resolve_mega144h7_tune_pages,
};
use crate::tune_catalog_abi;
use crate::tune_session::TuneSession;
use crate::tuning::TuneSnapshotOrigin;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TuneSourceInstallError {
    InvalidHandle,
    Resolve(ProfileResolveError),
}

impl From<ProfileResolveError> for TuneSourceInstallError {
    fn from(value: ProfileResolveError) -> Self {
        Self::Resolve(value)
    }
}

/// Decodes and validates the complete profile source before taking mutation authority over the
/// active engine session. A failed decode leaves the existing TuneSession untouched.
pub fn install_mega144h7_imported_pages(
    handle: i64,
    page_1: &[u8],
    page_2: &[u8],
    modes: Mega144h7ResolvedModes,
) -> Result<u64, TuneSourceInstallError> {
    let snapshot =
        resolve_mega144h7_tune_pages(page_1, page_2, modes, TuneSnapshotOrigin::ImportedFile)?;
    let profile_generation = snapshot.profile_generation;
    with_tune_session(handle, move |session| {
        *session = TuneSession::from_snapshot(snapshot);
    })
    .ok_or(TuneSourceInstallError::InvalidHandle)?;
    Ok(profile_generation)
}

pub fn tune_catalog_bytes(handle: i64) -> Option<Vec<u8>> {
    with_tune_session(handle, |session| {
        tune_catalog_abi::encode(session.baseline(), session.revision())
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::engine::{close_engine, create_engine};
    use crate::offline_tune_source::OFFLINE_SOURCE_PRIMARY_TABLE_ID;
    use crate::resolved_profile_source::{
        MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, TABLE_ID_VE,
    };

    fn valid_pages() -> (Vec<u8>, Vec<u8>) {
        let mut page_1 = vec![0; MEGA144H7_PAGE_1_SIZE];
        page_1[18_848..19_872].fill(147);
        (page_1, vec![0; MEGA144H7_PAGE_2_SIZE])
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

        let (page_1, page_2) = valid_pages();
        install_mega144h7_imported_pages(
            handle,
            &page_1,
            &page_2,
            Mega144h7ResolvedModes::default(),
        )
        .unwrap();

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
    fn failed_decode_preserves_existing_session_and_history() {
        let handle = create_engine();
        with_tune_session(handle, |session| {
            session
                .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
                .unwrap();
        })
        .unwrap();

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
        close_engine(first);
        close_engine(second);
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
    fn invalid_handle_does_not_create_a_session() {
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
    }
}
