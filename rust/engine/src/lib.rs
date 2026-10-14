mod channel_store;
mod diagnostics;
mod engine;
mod jni_engine;
mod jni_tune_session;
mod offline_tune_source;
pub mod resolved_profile_source;
mod runtime_snapshot;
mod table_projection_abi;
mod tune_catalog_abi;
mod tune_session;
mod tune_source_install;
pub mod tuning;
mod tuning_brush_pull;
mod tuning_interpolation;
mod tuning_smoothing;

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
