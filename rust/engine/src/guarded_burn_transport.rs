use crate::burn_envelope_plan::{
    BurnEnvelopeError, FramedPageBurnPlan, VerifiedPageBurnAck, frame_page_burn,
    verify_page_burn_response,
};
use crate::crc32_check_envelope_plan::{
    Crc32CheckEnvelopeError, VerifiedPageCrc32Check, verify_page_crc32_check_response,
};
use crate::engine_guarded_burn::{
    EngineGuardedPageBurnError, EngineGuardedPageBurnPlan,
    require_selected_engine_guarded_page_burn_current,
};
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GuardedPageBurnTransportPlan {
    pub page: usize,
    pub burn: FramedPageBurnPlan,
    pub crc_check: crate::crc32_check_envelope_plan::FramedPageCrc32CheckPlan,
    pub expected_page_crc32: u32,
}

#[derive(Clone, Debug, PartialEq)]
pub enum GuardedPageBurnTransportError {
    Authority(EngineGuardedPageBurnError),
    ProfileAuthority(RegistryStoreError),
    BurnEnvelope(BurnEnvelopeError),
    CrcEnvelope(Crc32CheckEnvelopeError),
}

pub fn stage_selected_engine_guarded_page_burn(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    plan: &EngineGuardedPageBurnPlan,
) -> Result<GuardedPageBurnTransportPlan, GuardedPageBurnTransportError> {
    require_selected_engine_guarded_page_burn_current(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        plan,
    )
    .map_err(GuardedPageBurnTransportError::Authority)?;

    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            let burn = frame_page_burn(bundle, &plan.burn)
                .map_err(GuardedPageBurnTransportError::BurnEnvelope)?;
            Ok(GuardedPageBurnTransportPlan {
                page: plan.page,
                burn,
                crc_check: plan.framed_crc_check.clone(),
                expected_page_crc32: plan.expected_page_crc32,
            })
        },
    )
    .map_err(GuardedPageBurnTransportError::ProfileAuthority)?
}

pub fn verify_selected_engine_guarded_page_burn_ack(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    plan: &EngineGuardedPageBurnPlan,
    response_frame: &[u8],
) -> Result<VerifiedPageBurnAck, GuardedPageBurnTransportError> {
    require_selected_engine_guarded_page_burn_current(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        plan,
    )
    .map_err(GuardedPageBurnTransportError::Authority)?;

    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            let framed = frame_page_burn(bundle, &plan.burn)
                .map_err(GuardedPageBurnTransportError::BurnEnvelope)?;
            verify_page_burn_response(bundle, &framed, response_frame)
                .map_err(GuardedPageBurnTransportError::BurnEnvelope)
        },
    )
    .map_err(GuardedPageBurnTransportError::ProfileAuthority)?
}

pub fn verify_selected_engine_guarded_page_burn_crc(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    plan: &EngineGuardedPageBurnPlan,
    response_frame: &[u8],
) -> Result<VerifiedPageCrc32Check, GuardedPageBurnTransportError> {
    require_selected_engine_guarded_page_burn_current(
        engine_handle,
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        plan,
    )
    .map_err(GuardedPageBurnTransportError::Authority)?;

    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            verify_page_crc32_check_response(
                bundle,
                &plan.framed_crc_check,
                &plan.expected_page_bytes,
                response_frame,
            )
            .map_err(GuardedPageBurnTransportError::CrcEnvelope)
        },
    )
    .map_err(GuardedPageBurnTransportError::ProfileAuthority)?
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::burn_envelope_plan::BURN_OK_STATUS;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{close_engine, create_engine, with_tune_session};
    use crate::engine_guarded_burn::plan_selected_engine_guarded_page_burn;
    use crate::engine_guarded_write_batch::{
        plan_selected_engine_guarded_ram_write_batch,
        reconcile_selected_engine_guarded_batch_verified_prefix,
    };
    use crate::live_tune_install::install_selected_live_pages;
    use crate::ms_envelope_codec::crc32_ieee;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };

    const SIGNATURE: &str = "rusEFI guarded-burn-transport.test.1";

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
            profile_name: "guarded-burn-transport".to_string(),
            profile_generation: 91,
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

    fn source_page(first_value: u16) -> Vec<u8> {
        let mut page = vec![0xA5; 24];
        page[0..2].copy_from_slice(&1000u16.to_le_bytes());
        page[2..4].copy_from_slice(&2000u16.to_le_bytes());
        page[4] = 10;
        page[5] = 20;
        for (index, value) in [first_value, 200, 300, 400].into_iter().enumerate() {
            let offset = 6 + index * 2;
            page[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
        }
        page
    }

    fn live_engine() -> (i64, i64) {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import()).unwrap();
        let page = source_page(100);
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
    fn stage_exposes_only_definition_owned_framed_burn_and_crc_requests() {
        let (engine, registry) = live_engine();
        let guarded =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();
        let staged = stage_selected_engine_guarded_page_burn(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            &guarded,
        )
        .unwrap();

        assert_eq!(staged.page, 1);
        assert_eq!(staged.burn.page, 1);
        assert_eq!(staged.burn.expected_response_size, 7);
        assert_eq!(staged.crc_check.page, 1);
        assert_eq!(staged.crc_check.offset, 0);
        assert_eq!(staged.crc_check.count, 24);
        assert_eq!(staged.expected_page_crc32, guarded.expected_page_crc32);

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn burn_ack_requires_burn_specific_status_and_does_not_replace_crc_proof() {
        let (engine, registry) = live_engine();
        let guarded =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();

        assert_eq!(
            verify_selected_engine_guarded_page_burn_ack(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &guarded,
                &response_frame(BURN_OK_STATUS, &[]),
            )
            .unwrap()
            .page,
            1
        );
        assert!(matches!(
            verify_selected_engine_guarded_page_burn_ack(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &guarded,
                &response_frame(0, &[]),
            ),
            Err(GuardedPageBurnTransportError::BurnEnvelope(
                BurnEnvelopeError::UnexpectedStatus(0)
            ))
        ));

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn post_burn_crc_must_match_exact_guarded_source_page() {
        let (engine, registry) = live_engine();
        let guarded =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();
        let crc_payload = guarded.expected_page_crc32.to_be_bytes();
        let response = response_frame(0, &crc_payload);

        let verified = verify_selected_engine_guarded_page_burn_crc(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            &guarded,
            &response,
        )
        .unwrap();
        assert_eq!(verified.page, 1);
        assert_eq!(verified.actual_crc32, guarded.expected_page_crc32);

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn authority_change_stales_transport_stage_before_physical_burn() {
        let (engine, registry) = live_engine();
        let guarded =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        assert!(matches!(
            stage_selected_engine_guarded_page_burn(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &guarded,
            ),
            Err(GuardedPageBurnTransportError::Authority(
                EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft { .. }
            ))
        ));

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn ram_confirmation_restores_burn_authority_for_new_guarded_plan_only() {
        let (engine, registry) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let write_batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        reconcile_selected_engine_guarded_batch_verified_prefix(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            &write_batch,
            write_batch.transactions.len(),
        )
        .unwrap();

        let guarded =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();
        assert!(stage_selected_engine_guarded_page_burn(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            &guarded,
        )
        .is_ok());

        close_profile_registry(registry);
        close_engine(engine);
    }
}
