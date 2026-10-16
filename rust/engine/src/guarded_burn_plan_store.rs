use std::collections::HashMap;
use std::sync::{Arc, LazyLock, Mutex};

use crate::engine_guarded_burn::{
    EngineGuardedPageBurnError, EngineGuardedPageBurnPlan, plan_selected_engine_guarded_page_burn,
};
use crate::guarded_burn_transport::{
    GuardedPageBurnTransportError, GuardedPageBurnTransportPlan,
    stage_selected_engine_guarded_page_burn, verify_selected_engine_guarded_page_burn_ack,
    verify_selected_engine_guarded_page_burn_crc,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct GuardedBurnPlanSummary {
    pub profile_generation: u64,
    pub draft_revision: u64,
    pub page: usize,
}

#[derive(Clone, Debug, PartialEq)]
pub enum GuardedBurnPlanStoreError {
    InvalidHandle,
    Authority(EngineGuardedPageBurnError),
    Transport(GuardedPageBurnTransportError),
}

pub struct GuardedBurnPlanStore {
    next_handle: i64,
    plans: HashMap<i64, Arc<EngineGuardedPageBurnPlan>>,
}

impl GuardedBurnPlanStore {
    pub fn new() -> Self {
        Self {
            next_handle: 1,
            plans: HashMap::new(),
        }
    }

    pub fn insert(&mut self, plan: EngineGuardedPageBurnPlan) -> i64 {
        let handle = self.next_handle;
        if handle <= 0 || handle == i64::MAX {
            return 0;
        }
        self.next_handle += 1;
        self.plans.insert(handle, Arc::new(plan));
        handle
    }

    pub fn close(&mut self, handle: i64) {
        if handle != 0 {
            self.plans.remove(&handle);
        }
    }

    pub fn is_alive(&self, handle: i64) -> bool {
        handle != 0 && self.plans.contains_key(&handle)
    }

    pub fn active_count(&self) -> i32 {
        i32::try_from(self.plans.len()).unwrap_or(i32::MAX)
    }

    pub fn get(
        &self,
        handle: i64,
    ) -> Result<Arc<EngineGuardedPageBurnPlan>, GuardedBurnPlanStoreError> {
        self.plans
            .get(&handle)
            .cloned()
            .ok_or(GuardedBurnPlanStoreError::InvalidHandle)
    }
}

impl Default for GuardedBurnPlanStore {
    fn default() -> Self {
        Self::new()
    }
}

static GUARDED_BURN_PLAN_STORE: LazyLock<Mutex<GuardedBurnPlanStore>> =
    LazyLock::new(|| Mutex::new(GuardedBurnPlanStore::new()));

fn with_store<T>(operation: impl FnOnce(&mut GuardedBurnPlanStore) -> T) -> T {
    let mut guard = GUARDED_BURN_PLAN_STORE
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut guard)
}

pub fn create_selected_engine_guarded_burn_plan(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    page: usize,
) -> Result<i64, GuardedBurnPlanStoreError> {
    let plan = plan_selected_engine_guarded_page_burn(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        page,
    )
    .map_err(GuardedBurnPlanStoreError::Authority)?;
    let handle = with_store(|store| store.insert(plan));
    if handle == 0 {
        return Err(GuardedBurnPlanStoreError::InvalidHandle);
    }
    Ok(handle)
}

pub fn close_guarded_burn_plan(handle: i64) {
    with_store(|store| store.close(handle));
}

pub fn guarded_burn_plan_is_alive(handle: i64) -> bool {
    with_store(|store| store.is_alive(handle))
}

pub fn active_guarded_burn_plan_count() -> i32 {
    with_store(|store| store.active_count())
}

pub fn guarded_burn_plan_summary(
    handle: i64,
) -> Result<GuardedBurnPlanSummary, GuardedBurnPlanStoreError> {
    let plan = with_store(|store| store.get(handle))?;
    Ok(GuardedBurnPlanSummary {
        profile_generation: plan.profile_generation,
        draft_revision: plan.draft_revision,
        page: plan.page,
    })
}

pub fn stage_guarded_burn_plan(
    handle: i64,
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
) -> Result<GuardedPageBurnTransportPlan, GuardedBurnPlanStoreError> {
    // Retain the plan through an Arc, then release the store mutex before re-entering
    // profile/engine authority. This mirrors the guarded RAM-write store lock order.
    let plan = with_store(|store| store.get(handle))?;
    stage_selected_engine_guarded_page_burn(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        &plan,
    )
    .map_err(GuardedBurnPlanStoreError::Transport)
}

pub fn verify_guarded_burn_ack(
    handle: i64,
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    response_frame: &[u8],
) -> Result<(), GuardedBurnPlanStoreError> {
    let plan = with_store(|store| store.get(handle))?;
    verify_selected_engine_guarded_page_burn_ack(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        &plan,
        response_frame,
    )
    .map(|_| ())
    .map_err(GuardedBurnPlanStoreError::Transport)
}

pub fn verify_guarded_burn_crc(
    handle: i64,
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    response_frame: &[u8],
) -> Result<(), GuardedBurnPlanStoreError> {
    let plan = with_store(|store| store.get(handle))?;
    verify_selected_engine_guarded_page_burn_crc(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        &plan,
        response_frame,
    )
    .map(|_| ())
    .map_err(GuardedBurnPlanStoreError::Transport)
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::burn_envelope_plan::BURN_OK_STATUS;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{close_engine, create_engine, with_tune_session};
    use crate::live_tune_install::install_selected_live_pages;
    use crate::ms_envelope_codec::crc32_ieee;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };

    const SIGNATURE: &str = "rusEFI guarded-burn-store.test.1";

    fn definition() -> String {
        format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "{SIGNATURE}"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = 24
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
pageChunkWrite = "C%2o%2c%v"
burnCommand = "B"
crc32CheckCommand = "k%2o%2c"
page = 1
rpmBins = array, U16, 0, [2], "RPM", 1, 0, 0, 10000, 0
loadBins = array, U08, 4, [2], "Load", 1, 0, 0, 255, 0
values = array, U16, 6, [2x2], "%", 1, 0, 0, 1000, 0

[TableEditor]
table = valuesTable, valuesMap, "Values", 1
    xBins = rpmBins, RPMValue
    yBins = loadBins, LoadValue
    zBins = values
"#,
        )
    }

    fn bundle_import() -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "guarded-burn-store".to_string(),
            profile_generation: 113,
            definition_fingerprint: None,
            root_definition: definition(),
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

    fn response_frame(status: u8, payload: &[u8]) -> Vec<u8> {
        let mut body = Vec::with_capacity(1 + payload.len());
        body.push(status);
        body.extend_from_slice(payload);
        let mut frame = Vec::with_capacity(2 + body.len() + 4);
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        frame
    }

    #[test]
    fn store_owns_burn_plan_until_explicit_close() {
        let (engine, registry) = live_engine();
        let handle = create_selected_engine_guarded_burn_plan(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();

        assert!(guarded_burn_plan_is_alive(handle));
        assert_eq!(
            guarded_burn_plan_summary(handle).unwrap(),
            GuardedBurnPlanSummary {
                profile_generation: 113,
                draft_revision: 0,
                page: 1,
            }
        );
        let staged =
            stage_guarded_burn_plan(handle, engine, registry, SIGNATURE.as_bytes(), 256).unwrap();
        assert_eq!(staged.burn.expected_response_size, 7);

        close_guarded_burn_plan(handle);
        close_guarded_burn_plan(handle);
        assert!(!guarded_burn_plan_is_alive(handle));

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn opaque_handle_revalidates_authority_after_store_lock_is_released() {
        let (engine, registry) = live_engine();
        let handle = create_selected_engine_guarded_burn_plan(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        assert!(matches!(
            stage_guarded_burn_plan(handle, engine, registry, SIGNATURE.as_bytes(), 256),
            Err(GuardedBurnPlanStoreError::Transport(
                GuardedPageBurnTransportError::Authority(
                    EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft { .. }
                )
            ))
        ));

        close_guarded_burn_plan(handle);
        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn opaque_plan_keeps_burn_ack_and_crc_proof_native() {
        let (engine, registry) = live_engine();
        let handle = create_selected_engine_guarded_burn_plan(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        let staged =
            stage_guarded_burn_plan(handle, engine, registry, SIGNATURE.as_bytes(), 256).unwrap();

        assert_eq!(
            verify_guarded_burn_ack(
                handle,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &response_frame(BURN_OK_STATUS, &[]),
            ),
            Ok(())
        );
        assert_eq!(
            verify_guarded_burn_crc(
                handle,
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &response_frame(0, &staged.expected_page_crc32.to_be_bytes()),
            ),
            Ok(())
        );

        close_guarded_burn_plan(handle);
        close_profile_registry(registry);
        close_engine(engine);
    }
}
