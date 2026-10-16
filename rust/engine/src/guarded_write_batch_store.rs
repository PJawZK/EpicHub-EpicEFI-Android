use std::collections::HashMap;
use std::sync::{Arc, LazyLock, Mutex};

use crate::engine_guarded_write_batch::{
    EngineGuardedRamWriteBatch, EngineGuardedRamWriteBatchError,
    EngineGuardedSourceReconciliationError, plan_selected_engine_guarded_ram_write_batch,
    reconcile_selected_engine_guarded_batch_verified_prefix,
    require_selected_engine_guarded_batch_current,
};
use crate::write_reconciliation_transaction::GuardedRamWriteTransaction;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct GuardedWriteBatchSummary {
    pub profile_generation: u64,
    pub draft_revision: u64,
    pub transaction_count: usize,
}

#[derive(Clone, Debug, PartialEq)]
pub enum GuardedWriteBatchStoreError {
    InvalidHandle,
    InvalidTransactionIndex { index: usize, count: usize },
    Authority(EngineGuardedRamWriteBatchError),
    Reconciliation(EngineGuardedSourceReconciliationError),
}

pub struct GuardedWriteBatchStore {
    next_handle: i64,
    batches: HashMap<i64, Arc<EngineGuardedRamWriteBatch>>,
}

impl GuardedWriteBatchStore {
    pub fn new() -> Self {
        Self {
            next_handle: 1,
            batches: HashMap::new(),
        }
    }

    pub fn insert(&mut self, batch: EngineGuardedRamWriteBatch) -> i64 {
        let handle = self.next_handle;
        if handle <= 0 || handle == i64::MAX {
            return 0;
        }
        self.next_handle += 1;
        self.batches.insert(handle, Arc::new(batch));
        handle
    }

    pub fn close(&mut self, handle: i64) {
        if handle != 0 {
            self.batches.remove(&handle);
        }
    }

    pub fn is_alive(&self, handle: i64) -> bool {
        handle != 0 && self.batches.contains_key(&handle)
    }

    pub fn active_count(&self) -> i32 {
        i32::try_from(self.batches.len()).unwrap_or(i32::MAX)
    }

    pub fn get(
        &self,
        handle: i64,
    ) -> Result<Arc<EngineGuardedRamWriteBatch>, GuardedWriteBatchStoreError> {
        self.batches
            .get(&handle)
            .cloned()
            .ok_or(GuardedWriteBatchStoreError::InvalidHandle)
    }
}

impl Default for GuardedWriteBatchStore {
    fn default() -> Self {
        Self::new()
    }
}

static GUARDED_WRITE_BATCH_STORE: LazyLock<Mutex<GuardedWriteBatchStore>> =
    LazyLock::new(|| Mutex::new(GuardedWriteBatchStore::new()));

fn with_store<T>(operation: impl FnOnce(&mut GuardedWriteBatchStore) -> T) -> T {
    let mut guard = GUARDED_WRITE_BATCH_STORE
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut guard)
}

pub fn create_selected_engine_guarded_write_batch(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    expected_draft_revision: u64,
) -> Result<i64, GuardedWriteBatchStoreError> {
    let batch = plan_selected_engine_guarded_ram_write_batch(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        expected_draft_revision,
    )
    .map_err(GuardedWriteBatchStoreError::Authority)?;
    let handle = with_store(|store| store.insert(batch));
    if handle == 0 {
        return Err(GuardedWriteBatchStoreError::InvalidHandle);
    }
    Ok(handle)
}

pub fn close_guarded_write_batch(handle: i64) {
    with_store(|store| store.close(handle));
}

pub fn guarded_write_batch_is_alive(handle: i64) -> bool {
    with_store(|store| store.is_alive(handle))
}

pub fn active_guarded_write_batch_count() -> i32 {
    with_store(|store| store.active_count())
}

pub fn guarded_write_batch_summary(
    handle: i64,
) -> Result<GuardedWriteBatchSummary, GuardedWriteBatchStoreError> {
    let batch = with_store(|store| store.get(handle))?;
    Ok(GuardedWriteBatchSummary {
        profile_generation: batch.profile_generation,
        draft_revision: batch.draft_revision,
        transaction_count: batch.transactions.len(),
    })
}

pub fn guarded_write_transaction(
    handle: i64,
    index: usize,
) -> Result<GuardedRamWriteTransaction, GuardedWriteBatchStoreError> {
    let batch = with_store(|store| store.get(handle))?;
    batch.transactions.get(index).cloned().ok_or(
        GuardedWriteBatchStoreError::InvalidTransactionIndex {
            index,
            count: batch.transactions.len(),
        },
    )
}

pub fn require_guarded_write_batch_current(
    handle: i64,
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
) -> Result<(), GuardedWriteBatchStoreError> {
    // Clone the Arc while holding the batch-store mutex, then release the mutex before
    // entering profile/engine authority. This prevents cross-store lock ordering from
    // becoming part of the public safety contract.
    let batch = with_store(|store| store.get(handle))?;
    require_selected_engine_guarded_batch_current(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        &batch,
    )
    .map_err(GuardedWriteBatchStoreError::Authority)
}

pub fn reconcile_guarded_write_batch_verified_prefix(
    handle: i64,
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    verified_transaction_count: usize,
) -> Result<(), GuardedWriteBatchStoreError> {
    // Preserve the same lock-order contract as the currentness check: retain the batch
    // through an Arc, release the batch-store mutex, then enter profile/engine authority.
    let batch = with_store(|store| store.get(handle))?;
    reconcile_selected_engine_guarded_batch_verified_prefix(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        &batch,
        verified_transaction_count,
    )
    .map_err(GuardedWriteBatchStoreError::Reconciliation)
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{
        close_engine, create_engine, with_tune_session, with_tune_source_image,
    };
    use crate::live_tune_install::install_selected_live_pages;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };
    use crate::verified_source_reconciliation::VerifiedSourceReconciliationError;

    const SIGNATURE: &str = "rusEFI guarded-write-batch-store.test.1";
    const DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI guarded-write-batch-store.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 24
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
pageChunkWrite = "C%2o%2c%v"
page = 1
rpmBins = array, U16, 0, [2], "RPM", 1, 0, 0, 10000, 0
loadBins = array, U08, 4, [2], "Load", 1, 0, 0, 255, 0
values = array, U16, 6, [2x2], "%", 1, 0, 0, 1000, 0

[TableEditor]
table = valuesTable, valuesMap, "Values", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#;

    fn bundle_import() -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "guarded-write-batch-store".to_string(),
            profile_generation: 91,
            definition_fingerprint: None,
            root_definition: DEFINITION.to_string(),
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42,
                editor_table_id: "valuesTable".to_string(),
            }],
        }
    }

    fn source_page() -> Vec<u8> {
        let mut page = vec![0xA5; 24];
        page[0..2].copy_from_slice(&1000u16.to_le_bytes());
        page[2..4].copy_from_slice(&2000u16.to_le_bytes());
        page[4] = 10;
        page[5] = 20;
        for (index, value) in [100u16, 200, 300, 400].into_iter().enumerate() {
            let offset = 6 + index * 2;
            page[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
        }
        page
    }

    fn live_engine() -> (i64, i64) {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        let page = source_page();
        install_selected_live_pages(engine, registry, SIGNATURE.as_bytes(), 256, &[&page]).unwrap();
        (engine, registry)
    }

    #[test]
    fn store_owns_batch_until_explicit_close() {
        let (engine, registry) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        let handle = create_selected_engine_guarded_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        assert!(guarded_write_batch_is_alive(handle));
        assert_eq!(
            guarded_write_batch_summary(handle).unwrap(),
            GuardedWriteBatchSummary {
                profile_generation: 91,
                draft_revision: 1,
                transaction_count: 1,
            }
        );
        let transaction = guarded_write_transaction(handle, 0).unwrap();
        assert_eq!((transaction.page, transaction.offset), (1, 6));

        close_guarded_write_batch(handle);
        close_guarded_write_batch(handle);
        assert!(!guarded_write_batch_is_alive(handle));

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn current_check_happens_after_store_lock_is_released() {
        let (engine, registry) = live_engine();
        let handle = create_selected_engine_guarded_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            0,
        )
        .unwrap();
        assert_eq!(
            require_guarded_write_batch_current(
                handle,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
            ),
            Ok(())
        );

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        assert_eq!(
            require_guarded_write_batch_current(
                handle,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
            ),
            Err(GuardedWriteBatchStoreError::Authority(
                EngineGuardedRamWriteBatchError::StaleDraftRevision {
                    expected: 0,
                    actual: 1,
                }
            ))
        );

        close_guarded_write_batch(handle);
        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn reconciliation_uses_opaque_batch_after_store_lock_is_released() {
        let (engine, registry) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let handle = create_selected_engine_guarded_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();

        assert_eq!(
            reconcile_guarded_write_batch_verified_prefix(
                handle,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                1,
            ),
            Ok(())
        );
        assert_eq!(
            with_tune_source_image(engine, |source| source.unwrap().page(0).unwrap()[6]),
            Some(101),
        );
        assert!(guarded_write_batch_is_alive(handle));
        assert_eq!(
            with_tune_session(engine, |session| session.revision()),
            Some(1)
        );

        close_guarded_write_batch(handle);
        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn reconciliation_invalid_handle_and_invalid_prefix_fail_closed() {
        let (engine, registry) = live_engine();
        assert_eq!(
            reconcile_guarded_write_batch_verified_prefix(
                i64::MAX,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                0,
            ),
            Err(GuardedWriteBatchStoreError::InvalidHandle)
        );

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let handle = create_selected_engine_guarded_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        let before = with_tune_source_image(engine, |source| {
            source.unwrap().page(0).unwrap().to_vec()
        })
        .unwrap();

        assert_eq!(
            reconcile_guarded_write_batch_verified_prefix(
                handle,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                2,
            ),
            Err(GuardedWriteBatchStoreError::Reconciliation(
                EngineGuardedSourceReconciliationError::VerifiedSource(
                    VerifiedSourceReconciliationError::VerifiedPrefixTooLarge {
                        verified: 2,
                        available: 1,
                    }
                )
            ))
        );
        assert_eq!(
            with_tune_source_image(engine, |source| {
                source.unwrap().page(0).unwrap().to_vec()
            }),
            Some(before),
        );

        close_guarded_write_batch(handle);
        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn invalid_handles_and_transaction_indices_fail_closed() {
        assert_eq!(
            guarded_write_batch_summary(i64::MAX),
            Err(GuardedWriteBatchStoreError::InvalidHandle)
        );
        assert_eq!(
            guarded_write_transaction(i64::MAX, 0),
            Err(GuardedWriteBatchStoreError::InvalidHandle)
        );

        let (engine, registry) = live_engine();
        let handle = create_selected_engine_guarded_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            0,
        )
        .unwrap();
        assert_eq!(
            guarded_write_transaction(handle, 0),
            Err(GuardedWriteBatchStoreError::InvalidTransactionIndex { index: 0, count: 0 })
        );

        close_guarded_write_batch(handle);
        close_profile_registry(registry);
        close_engine(engine);
    }
}
