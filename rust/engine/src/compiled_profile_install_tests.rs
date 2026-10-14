use crate::compiled_ecu_profile::test_support::mega_profile;
use crate::ecu_profile::ResolvedEcuProfile;
use crate::engine::{close_engine, create_engine, with_tune_session};
use crate::offline_tune_source::OFFLINE_SOURCE_PRIMARY_TABLE_ID;
use crate::resolved_profile_source::{MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, TABLE_ID_VE};
use crate::tune_source_install::install_imported_pages;
use crate::tuning::TuneSnapshotOrigin;

#[test]
fn compiled_profile_installs_exact_snapshot_through_generic_atomic_source_path() {
    let profile = mega_profile();
    let mut page_1 = vec![0_u8; MEGA144H7_PAGE_1_SIZE];
    page_1[18_848..19_872].fill(147);
    let page_2 = vec![0_u8; MEGA144H7_PAGE_2_SIZE];
    let expected = profile
        .resolve_pages(&[&page_1, &page_2], TuneSnapshotOrigin::ImportedFile)
        .unwrap();

    let handle = create_engine();
    let generation = install_imported_pages(handle, &profile, &[&page_1, &page_2]).unwrap();
    assert_eq!(generation, profile.identity().profile_generation);

    with_tune_session(handle, |session| {
        assert_eq!(session.baseline(), &expected);
        assert_eq!(session.revision(), 0);
        assert!(session.projection_bytes(TABLE_ID_VE).is_ok());
        assert!(
            session
                .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                .is_err()
        );
        assert!(session.undo().is_err());
    })
    .unwrap();
    close_engine(handle);
}
