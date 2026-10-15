mod channel_store;
pub mod compiled_ecu_profile;
pub mod compiled_profile_bundle;
#[cfg(test)]
mod compiled_profile_install_tests;
mod diagnostics;
pub mod ecu_calibration;
pub mod ecu_compiled_snapshot;
pub mod ecu_definition;
pub mod ecu_definition_resolver;
pub mod ecu_editor_definition;
pub mod ecu_page_codec;
pub mod ecu_profile;
pub mod ecu_profile_registry;
pub mod ecu_protocol;
mod engine;
pub mod engine_guarded_write_batch;
pub mod engine_session_write_intent;
pub mod guarded_write_batch_store;
mod jni_definition_probe_plan;
mod jni_engine;
mod jni_guarded_ram_write;
mod jni_guarded_write_batch;
mod jni_live_tune_install;
mod jni_profile_registry;
mod jni_readonly_page_acquisition;
mod jni_readonly_tune_metadata;
mod jni_runtime_identity;
mod jni_tune_session;
pub mod live_tune_install;
mod mega144h7_profile;
pub mod ms_envelope_codec;
mod offline_tune_source;
pub mod page_read_planner;
pub mod page_write_planner;
pub mod profile_import;
pub mod profile_registry_store;
pub mod readonly_page_acquisition;
pub mod resolved_profile_source;
pub mod runtime_identity_probe;
#[cfg(test)]
mod runtime_probe_selection_tests;
pub mod runtime_profile_authority;
#[cfg(test)]
mod runtime_profile_selection_tests;
mod runtime_snapshot;
pub mod session_write_intent;
mod table_projection_abi;
mod tune_catalog_abi;
mod tune_session;
pub mod tune_source_image;
mod tune_source_install;
pub mod tuning;
mod tuning_brush_pull;
mod tuning_interpolation;
mod tuning_smoothing;
pub mod verified_source_reconciliation;
pub mod write_envelope_plan;
pub mod write_reconciliation_plan;
pub mod write_reconciliation_transaction;

#[cfg(test)]
mod tuning_fixture {
    pub use crate::offline_tune_source::{
        OFFLINE_SOURCE_PRIMARY_TABLE_ID as OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
        offline_source_snapshot as offline_fixture_snapshot,
    };
    use crate::tuning::TuneDraft;

    pub fn offline_fixture_draft() -> TuneDraft {
        let snapshot = offline_fixture_snapshot();
        TuneDraft::from_snapshot(&snapshot)
    }
}
