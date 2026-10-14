use crate::compiled_ecu_profile::test_support::mega_profile;
use crate::ecu_profile_registry::{
    ProfileRegistry, ProfileSelectionError, RuntimeControllerIdentity,
};
use crate::engine::{close_engine, create_engine, with_tune_session};
use crate::offline_tune_source::OFFLINE_SOURCE_PRIMARY_TABLE_ID;
use crate::resolved_profile_source::{
    MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, MEGA144H7_PROFILE_SIGNATURE, TABLE_ID_VE,
};
use crate::runtime_identity_probe::{
    ReadOnlyProbeChannel, RuntimeProbeError, SignatureProbePlan, TransportCandidate,
    probe_runtime_identity,
};
use crate::tune_source_install::install_imported_pages;

struct FakeProbeChannel {
    response: Result<Vec<u8>, String>,
    calls: usize,
}

impl ReadOnlyProbeChannel for FakeProbeChannel {
    fn transact_read_only(
        &mut self,
        _candidate: &TransportCandidate,
        _request: &[u8],
        _max_response_bytes: usize,
    ) -> Result<Vec<u8>, String> {
        self.calls += 1;
        self.response.clone()
    }
}

fn candidate(description: &str) -> TransportCandidate {
    TransportCandidate {
        transport_kind: "usb-serial".to_string(),
        endpoint_id: "0483:5740:test-endpoint".to_string(),
        description: Some(description.to_string()),
    }
}

fn signature_plan() -> SignatureProbePlan {
    SignatureProbePlan {
        query: b"S".to_vec(),
        max_response_bytes: 256,
    }
}

fn dirty_offline_session(handle: i64) {
    with_tune_session(handle, |session| {
        session
            .apply_rectangle_step(OFFLINE_SOURCE_PRIMARY_TABLE_ID, 0, 0, 0, 0, 1, 0)
            .unwrap();
        assert_eq!(session.revision(), 1);
    })
    .unwrap();
}

fn assert_dirty_offline_session_preserved(handle: i64) {
    with_tune_session(handle, |session| {
        assert_eq!(session.revision(), 1);
        assert!(
            session
                .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                .is_ok()
        );
        assert!(session.projection_bytes(TABLE_ID_VE).is_err());
        assert_eq!(session.undo().unwrap(), 2);
    })
    .unwrap();
}

#[test]
fn transport_metadata_cannot_authorize_a_profile_when_live_signature_is_unknown() {
    let profile = mega_profile();
    let mut registry = ProfileRegistry::new();
    registry.register_profile(&profile).unwrap();

    let misleading_candidate = candidate("Mega144H7 ECU on known EpicEFI USB VID/PID");
    let mut channel = FakeProbeChannel {
        response: Ok(b"rusEFI completely.unknown.999".to_vec()),
        calls: 0,
    };
    let identity = probe_runtime_identity(
        &mut channel,
        &misleading_candidate,
        &signature_plan(),
    )
    .unwrap();

    assert_eq!(channel.calls, 1);
    assert!(matches!(
        registry.select_exact_profile(&identity),
        Err(ProfileSelectionError::UnknownController)
    ));
}

#[test]
fn probe_failure_does_not_mutate_existing_tune_session_or_history() {
    let handle = create_engine();
    dirty_offline_session(handle);

    let mut channel = FakeProbeChannel {
        response: Err("read-only signature request failed".to_string()),
        calls: 0,
    };
    assert_eq!(
        probe_runtime_identity(&mut channel, &candidate("candidate"), &signature_plan()),
        Err(RuntimeProbeError::Transport(
            "read-only signature request failed".to_string()
        ))
    );
    assert_dirty_offline_session_preserved(handle);
    close_engine(handle);
}

#[test]
fn unknown_and_recognized_without_profile_do_not_mutate_existing_session() {
    let handle = create_engine();
    dirty_offline_session(handle);

    let registry = {
        let mut registry = ProfileRegistry::new();
        registry.register_recognized_signature("rusEFI known.no-profile.300");
        registry
    };

    let unknown = RuntimeControllerIdentity::from_signature("rusEFI unknown.400");
    assert!(matches!(
        registry.select_exact_profile(&unknown),
        Err(ProfileSelectionError::UnknownController)
    ));
    let recognized = RuntimeControllerIdentity::from_signature("rusEFI known.no-profile.300");
    assert!(matches!(
        registry.select_exact_profile(&recognized),
        Err(ProfileSelectionError::RecognizedControllerWithoutProfile)
    ));

    assert_dirty_offline_session_preserved(handle);
    close_engine(handle);
}

#[test]
fn exact_read_only_probe_can_select_compiled_profile_then_use_generic_source_install() {
    let profile = mega_profile();
    let mut registry = ProfileRegistry::new();
    registry.register_profile(&profile).unwrap();

    let mut channel = FakeProbeChannel {
        response: Ok(MEGA144H7_PROFILE_SIGNATURE.as_bytes().to_vec()),
        calls: 0,
    };
    let identity = probe_runtime_identity(
        &mut channel,
        &candidate("descriptive transport metadata only"),
        &signature_plan(),
    )
    .unwrap();
    let selected = registry.select_exact_profile(&identity).unwrap();

    let mut page_1 = vec![0_u8; MEGA144H7_PAGE_1_SIZE];
    page_1[18_848..19_872].fill(147);
    let page_2 = vec![0_u8; MEGA144H7_PAGE_2_SIZE];

    let handle = create_engine();
    install_imported_pages(handle, selected, &[&page_1, &page_2]).unwrap();
    with_tune_session(handle, |session| {
        assert_eq!(session.revision(), 0);
        assert!(session.projection_bytes(TABLE_ID_VE).is_ok());
        assert!(
            session
                .projection_bytes(OFFLINE_SOURCE_PRIMARY_TABLE_ID)
                .is_err()
        );
    })
    .unwrap();
    close_engine(handle);
}
