use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};
use std::time::Instant;

use crate::channel_store::ChannelStore;
use crate::diagnostics::{
    DIAGNOSTIC_BATCH_SIZE, DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED, DIAGNOSTIC_SEVERITY_TRACE,
    EngineDiagnostics,
};
use crate::runtime_snapshot::{RUNTIME_SNAPSHOT_SIZE, RuntimeSnapshot};
use crate::tune_session::TuneSession;
use crate::tuning_fixture::{OFFLINE_FIXTURE_PRIMARY_TABLE_ID, offline_fixture_snapshot};

struct EngineState {
    started_at: Instant,
    sequence: u64,
    channels: ChannelStore,
    diagnostics: EngineDiagnostics,
    tune_session: TuneSession,
}

impl EngineState {
    fn new(handle: i64) -> Self {
        Self {
            started_at: Instant::now(),
            sequence: 0,
            channels: ChannelStore::new_synthetic(),
            diagnostics: EngineDiagnostics::new(u64::try_from(handle).unwrap_or(0)),
            tune_session: TuneSession::from_snapshot(offline_fixture_snapshot()),
        }
    }

    fn next_snapshot(&mut self, session_id: u64) -> [u8; RUNTIME_SNAPSHOT_SIZE] {
        let elapsed = self.started_at.elapsed();
        self.channels.update_synthetic(elapsed.as_secs_f32());
        self.sequence = self.sequence.saturating_add(1);

        let monotonic_nanos = u64::try_from(elapsed.as_nanos()).unwrap_or(u64::MAX);
        if self.sequence == 1 || self.sequence.is_multiple_of(20) {
            self.diagnostics.record(
                monotonic_nanos,
                DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED,
                DIAGNOSTIC_SEVERITY_TRACE,
                self.sequence,
                self.channels.samples().len() as u64,
            );
        }

        RuntimeSnapshot::from_channel_store(
            self.sequence,
            monotonic_nanos,
            session_id,
            &self.channels,
        )
        .encode()
    }

    fn drain_diagnostics(&mut self) -> [u8; DIAGNOSTIC_BATCH_SIZE] {
        self.diagnostics.drain_batch()
    }
}

struct EngineRegistry {
    next_handle: i64,
    engines: HashMap<i64, EngineState>,
}

impl EngineRegistry {
    fn new() -> Self {
        Self {
            next_handle: 1,
            engines: HashMap::new(),
        }
    }

    fn create(&mut self) -> i64 {
        let handle = self.next_handle;
        if handle <= 0 || handle == i64::MAX {
            return 0;
        }

        self.next_handle += 1;
        self.engines.insert(handle, EngineState::new(handle));
        handle
    }

    fn close(&mut self, handle: i64) {
        if handle != 0 {
            self.engines.remove(&handle);
        }
    }

    fn is_alive(&self, handle: i64) -> bool {
        handle != 0 && self.engines.contains_key(&handle)
    }

    fn active_count(&self) -> i32 {
        i32::try_from(self.engines.len()).unwrap_or(i32::MAX)
    }

    fn snapshot(&mut self, handle: i64) -> Option<[u8; RUNTIME_SNAPSHOT_SIZE]> {
        let session_id = u64::try_from(handle).ok()?;
        self.engines
            .get_mut(&handle)
            .map(|engine| engine.next_snapshot(session_id))
    }

    fn diagnostic_batch(&mut self, handle: i64) -> Option<[u8; DIAGNOSTIC_BATCH_SIZE]> {
        self.engines
            .get_mut(&handle)
            .map(EngineState::drain_diagnostics)
    }

    fn tune_session<T>(
        &mut self,
        handle: i64,
        operation: impl FnOnce(&mut TuneSession) -> T,
    ) -> Option<T> {
        self.engines
            .get_mut(&handle)
            .map(|engine| operation(&mut engine.tune_session))
    }
}

static ENGINE_REGISTRY: LazyLock<Mutex<EngineRegistry>> =
    LazyLock::new(|| Mutex::new(EngineRegistry::new()));

fn with_registry<T>(operation: impl FnOnce(&mut EngineRegistry) -> T) -> T {
    let mut guard = ENGINE_REGISTRY
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut guard)
}

pub fn create_engine() -> i64 {
    with_registry(EngineRegistry::create)
}

pub fn close_engine(handle: i64) {
    with_registry(|registry| registry.close(handle));
}

pub fn engine_is_alive(handle: i64) -> bool {
    with_registry(|registry| registry.is_alive(handle))
}

pub fn active_engine_count() -> i32 {
    with_registry(|registry| registry.active_count())
}

pub fn runtime_snapshot(handle: i64) -> Option<[u8; RUNTIME_SNAPSHOT_SIZE]> {
    with_registry(|registry| registry.snapshot(handle))
}

pub fn diagnostic_batch(handle: i64) -> Option<[u8; DIAGNOSTIC_BATCH_SIZE]> {
    with_registry(|registry| registry.diagnostic_batch(handle))
}

pub(crate) fn with_tune_session<T>(
    handle: i64,
    operation: impl FnOnce(&mut TuneSession) -> T,
) -> Option<T> {
    with_registry(|registry| registry.tune_session(handle, operation))
}

pub fn tune_table_projection(handle: i64, table_id: u32) -> Option<Vec<u8>> {
    with_tune_session(handle, |session| session.projection_bytes(table_id)).and_then(Result::ok)
}

pub fn offline_table_projection(handle: i64) -> Option<Vec<u8>> {
    tune_table_projection(handle, OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::diagnostics::{
        DIAGNOSTIC_BATCH_ABI_VERSION, DIAGNOSTIC_CODE_ENGINE_CREATED,
        DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED,
    };
    use crate::runtime_snapshot::{RUNTIME_SNAPSHOT_ABI_VERSION, RUNTIME_SNAPSHOT_SIZE};
    use crate::table_projection_abi::TABLE_PROJECTION_ABI_VERSION;

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    fn read_u64(bytes: &[u8], offset: usize) -> u64 {
        u64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
    }

    #[test]
    fn local_registry_create_close_is_idempotent_and_bounded() {
        let mut registry = EngineRegistry::new();
        let handle = registry.create();

        assert_ne!(handle, 0);
        assert!(registry.is_alive(handle));
        assert_eq!(registry.active_count(), 1);

        registry.close(handle);
        registry.close(handle);
        registry.close(0);

        assert!(!registry.is_alive(handle));
        assert_eq!(registry.active_count(), 0);
    }

    #[test]
    fn snapshot_sequence_advances_within_one_session() {
        let mut registry = EngineRegistry::new();
        let handle = registry.create();

        let first = registry.snapshot(handle).unwrap();
        let second = registry.snapshot(handle).unwrap();

        assert_eq!(first.len(), RUNTIME_SNAPSHOT_SIZE);
        assert_eq!(read_u32(&first, 0), RUNTIME_SNAPSHOT_ABI_VERSION);
        assert_eq!(read_u64(&first, 8), 1);
        assert_eq!(read_u64(&second, 8), 2);
        assert_eq!(read_u64(&first, 24), handle as u64);
    }

    #[test]
    fn native_diagnostics_are_bounded_and_drainable() {
        let mut registry = EngineRegistry::new();
        let handle = registry.create();
        let _ = registry.snapshot(handle).unwrap();

        let first = registry.diagnostic_batch(handle).unwrap();
        let second = registry.diagnostic_batch(handle).unwrap();

        assert_eq!(read_u32(&first, 0), DIAGNOSTIC_BATCH_ABI_VERSION);
        assert_eq!(read_u32(&first, 8), 2);
        assert_eq!(read_u32(&first, 32), DIAGNOSTIC_CODE_ENGINE_CREATED);
        assert_eq!(
            read_u32(&first, 72),
            DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED
        );
        assert_eq!(read_u32(&second, 8), 0);
    }

    #[test]
    fn tune_session_is_owned_by_engine_and_isolated_per_engine() {
        let mut registry = EngineRegistry::new();
        let first_handle = registry.create();
        let second_handle = registry.create();

        let first_revision = registry
            .tune_session(first_handle, |session| {
                session
                    .apply_rectangle_step(
                        OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
                        0,
                        0,
                        0,
                        0,
                        1,
                        0,
                    )
                    .unwrap()
            })
            .unwrap();
        let second_revision = registry
            .tune_session(second_handle, |session| session.revision())
            .unwrap();

        assert_eq!(first_revision, 1);
        assert_eq!(second_revision, 0);
    }

    #[test]
    fn tune_projection_is_owned_by_engine_generation() {
        let mut registry = EngineRegistry::new();
        let handle = registry.create();
        let projection = registry
            .tune_session(handle, |session| {
                session
                    .projection_bytes(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                    .unwrap()
            })
            .unwrap();

        assert_eq!(read_u32(&projection, 0), TABLE_PROJECTION_ABI_VERSION);
        assert_eq!(read_u32(&projection, 28), OFFLINE_FIXTURE_PRIMARY_TABLE_ID);
        assert_eq!(read_u64(&projection, 16), 0);
    }

    #[test]
    fn closed_or_unknown_handle_cannot_publish_state() {
        let mut registry = EngineRegistry::new();
        let handle = registry.create();
        registry.close(handle);

        assert!(registry.snapshot(handle).is_none());
        assert!(registry.diagnostic_batch(handle).is_none());
        assert!(registry.tune_session(handle, TuneSession::revision).is_none());
        assert!(registry.snapshot(i64::MAX).is_none());
        assert!(registry.diagnostic_batch(i64::MAX).is_none());
        assert!(registry.tune_session(i64::MAX, TuneSession::revision).is_none());
    }
}
