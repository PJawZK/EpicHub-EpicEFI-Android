use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::crc32_check_envelope_plan::{
    Crc32CheckEnvelopeError, FramedPageCrc32CheckPlan, frame_page_crc32_check,
};
use crate::ecu_profile::ResolvedEcuProfile;
use crate::engine::with_tune_source_authority_with_epoch;
use crate::ms_envelope_codec::crc32_ieee;
use crate::page_burn_planner::{PageBurnPlan, PageBurnPlanError, plan_page_burn};
use crate::page_crc32_check_planner::{
    PageCrc32CheckPlan, PageCrc32CheckPlanError, plan_full_page_crc32_check,
};
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::ram_source_provenance::{RamSourceAcquisition, RamSourceProvenance};
use crate::tune_source_image::TuneSourcePageImage;
use crate::tuning::TuneSnapshotOrigin;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineGuardedPageBurnPlan {
    pub profile_generation: u64,
    pub session_epoch: u64,
    pub draft_revision: u64,
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub expected_page_bytes: Vec<u8>,
    pub expected_page_crc32: u32,
    pub burn: PageBurnPlan,
    pub crc_check: PageCrc32CheckPlan,
    pub framed_crc_check: FramedPageCrc32CheckPlan,
    source_image: TuneSourcePageImage,
}

#[derive(Clone, Debug, PartialEq)]
pub enum EngineGuardedPageBurnError {
    ProfileAuthority(RegistryStoreError),
    InvalidEngineHandle,
    MissingSourceImage,
    NonLiveControllerSource(TuneSnapshotOrigin),
    StaleProfileGeneration {
        expected: u64,
        actual: u64,
    },
    RamProvenanceGenerationMismatch {
        expected: u64,
        actual: u64,
    },
    RamNotConfirmedForCurrentDraft {
        draft_revision: u64,
        confirmed_revision: Option<u64>,
    },
    MissingSourcePage {
        page: usize,
    },
    SourcePageSizeMismatch {
        page: usize,
        expected: usize,
        actual: usize,
    },
    SourceImageChanged,
    StaleSessionEpoch { expected: u64, actual: u64 },
    BurnPlan(PageBurnPlanError),
    CrcPlan(PageCrc32CheckPlanError),
    CrcEnvelope(Crc32CheckEnvelopeError),
}

pub fn plan_selected_engine_guarded_page_burn(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    page: usize,
) -> Result<EngineGuardedPageBurnPlan, EngineGuardedPageBurnError> {
    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            with_tune_source_authority_with_epoch(engine_handle, |session_epoch, session, source| {
                validate_live_ram_authority(bundle, session, source)?;
                let source = source.ok_or(EngineGuardedPageBurnError::MissingSourceImage)?;
                let expected_page_bytes = exact_source_page(bundle, source, page)?.to_vec();
                let burn =
                    plan_page_burn(bundle, page).map_err(EngineGuardedPageBurnError::BurnPlan)?;
                let crc_check = plan_full_page_crc32_check(bundle, page)
                    .map_err(EngineGuardedPageBurnError::CrcPlan)?;
                let framed_crc_check = frame_page_crc32_check(bundle, &crc_check)
                    .map_err(EngineGuardedPageBurnError::CrcEnvelope)?;

                Ok(EngineGuardedPageBurnPlan {
                    profile_generation: session.profile_generation(),
                    session_epoch,
                    draft_revision: session.revision(),
                    page,
                    expected_page_crc32: crc32_ieee(&expected_page_bytes),
                    expected_page_bytes,
                    burn,
                    crc_check,
                    framed_crc_check,
                    source_image: source.clone(),
                })
            })
            .ok_or(EngineGuardedPageBurnError::InvalidEngineHandle)?
        },
    )
    .map_err(EngineGuardedPageBurnError::ProfileAuthority)?
}

pub fn require_selected_engine_guarded_page_burn_current(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    plan: &EngineGuardedPageBurnPlan,
) -> Result<(), EngineGuardedPageBurnError> {
    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            let selected_generation = bundle.profile.identity().profile_generation;
            if selected_generation != plan.profile_generation {
                return Err(EngineGuardedPageBurnError::StaleProfileGeneration {
                    expected: plan.profile_generation,
                    actual: selected_generation,
                });
            }

            with_tune_source_authority_with_epoch(engine_handle, |session_epoch, session, source| {
                if session_epoch != plan.session_epoch {
                    return Err(EngineGuardedPageBurnError::StaleSessionEpoch {
                        expected: plan.session_epoch,
                        actual: session_epoch,
                    });
                }
                validate_live_ram_authority(bundle, session, source)?;
                if session.revision() != plan.draft_revision {
                    return Err(EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft {
                        draft_revision: session.revision(),
                        confirmed_revision: source.and_then(|source| {
                            effective_provenance(session, source).confirmed_draft_revision
                        }),
                    });
                }
                let source = source.ok_or(EngineGuardedPageBurnError::MissingSourceImage)?;
                if source != &plan.source_image {
                    return Err(EngineGuardedPageBurnError::SourceImageChanged);
                }
                let current_page = exact_source_page(bundle, source, plan.page)?;
                if current_page != plan.expected_page_bytes.as_slice() {
                    return Err(EngineGuardedPageBurnError::SourceImageChanged);
                }
                Ok(())
            })
            .ok_or(EngineGuardedPageBurnError::InvalidEngineHandle)?
        },
    )
    .map_err(EngineGuardedPageBurnError::ProfileAuthority)?
}

fn validate_live_ram_authority(
    bundle: &CompiledEcuProfileBundle,
    session: &crate::tune_session::TuneSession,
    source: Option<&TuneSourcePageImage>,
) -> Result<(), EngineGuardedPageBurnError> {
    if session.baseline().origin != TuneSnapshotOrigin::LiveController {
        return Err(EngineGuardedPageBurnError::NonLiveControllerSource(
            session.baseline().origin,
        ));
    }

    let selected_generation = bundle.profile.identity().profile_generation;
    let session_generation = session.profile_generation();
    if session_generation != selected_generation {
        return Err(EngineGuardedPageBurnError::StaleProfileGeneration {
            expected: selected_generation,
            actual: session_generation,
        });
    }

    let source = source.ok_or(EngineGuardedPageBurnError::MissingSourceImage)?;
    let provenance = effective_provenance(session, source);
    if provenance.profile_generation != selected_generation {
        return Err(
            EngineGuardedPageBurnError::RamProvenanceGenerationMismatch {
                expected: selected_generation,
                actual: provenance.profile_generation,
            },
        );
    }
    if provenance.acquisition != RamSourceAcquisition::LiveControllerRead {
        return Err(EngineGuardedPageBurnError::NonLiveControllerSource(
            session.baseline().origin,
        ));
    }
    let draft_revision = session.revision();
    if provenance.confirmed_draft_revision != Some(draft_revision) {
        return Err(EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft {
            draft_revision,
            confirmed_revision: provenance.confirmed_draft_revision,
        });
    }
    Ok(())
}

fn effective_provenance(
    session: &crate::tune_session::TuneSession,
    source: &TuneSourcePageImage,
) -> RamSourceProvenance {
    source.provenance().cloned().unwrap_or_else(|| {
        RamSourceProvenance::from_live_controller_read(session.profile_generation(), 0)
    })
}

fn exact_source_page<'a>(
    bundle: &CompiledEcuProfileBundle,
    source: &'a TuneSourcePageImage,
    page: usize,
) -> Result<&'a [u8], EngineGuardedPageBurnError> {
    if page == 0 {
        return Err(EngineGuardedPageBurnError::BurnPlan(
            PageBurnPlanError::InvalidPageNumber(page),
        ));
    }
    let page_index = page - 1;
    let page_sizes = bundle.profile.page_sizes();
    let expected = *page_sizes
        .get(page_index)
        .ok_or(EngineGuardedPageBurnError::BurnPlan(
            PageBurnPlanError::PageOutOfRange {
                page,
                page_count: page_sizes.len(),
            },
        ))?;
    let bytes = source
        .page(page_index)
        .ok_or(EngineGuardedPageBurnError::MissingSourcePage { page })?;
    if bytes.len() != expected {
        return Err(EngineGuardedPageBurnError::SourcePageSizeMismatch {
            page,
            expected,
            actual: bytes.len(),
        });
    }
    Ok(bytes)
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{close_engine, create_engine, with_tune_session};
    use crate::engine_guarded_write_batch::{
        plan_selected_engine_guarded_ram_write_batch,
        reconcile_selected_engine_guarded_batch_verified_prefix,
    };
    use crate::live_tune_install::install_selected_live_pages;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };

    const SIGNATURE: &str = "rusEFI guarded-burn.test.1";

    fn definition(burn: &str, crc: &str) -> String {
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
{burn}
{crc}
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

    fn bundle_import(generation: u64, burn: bool, crc: bool) -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "guarded-burn".to_string(),
            profile_generation: generation,
            definition_fingerprint: None,
            root_definition: definition(
                if burn { "burnCommand = \"B\"" } else { "" },
                if crc {
                    "crc32CheckCommand = \"k%2o%2c\""
                } else {
                    ""
                },
            ),
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

    fn live_engine(burn: bool, crc: bool) -> (i64, i64, Vec<u8>) {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import(77, burn, crc)).unwrap();
        let page = source_page(100);
        install_selected_live_pages(engine, registry, SIGNATURE.as_bytes(), 256, &[&page]).unwrap();
        (engine, registry, page)
    }

    #[test]
    fn fresh_live_controller_can_authorize_current_page_burn() {
        let (engine, registry, page) = live_engine(true, true);
        let plan =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();

        assert_eq!(plan.profile_generation, 77);
        assert_eq!(plan.draft_revision, 0);
        assert_eq!(plan.page, 1);
        assert_eq!(plan.expected_page_bytes, page);
        assert_eq!(
            plan.expected_page_crc32,
            crc32_ieee(&plan.expected_page_bytes)
        );
        assert_eq!(plan.burn.request_body, vec![b'B']);
        assert_eq!(plan.crc_check.offset, 0);
        assert_eq!(plan.crc_check.count, 24);
        assert_eq!(
            require_selected_engine_guarded_page_burn_current(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &plan,
            ),
            Ok(())
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn edited_draft_cannot_burn_until_ram_write_is_fully_confirmed() {
        let (engine, registry, _) = live_engine(true, true);
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        assert_eq!(
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1,),
            Err(EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft {
                draft_revision: 1,
                confirmed_revision: Some(0),
            })
        );

        let write_batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        let verified = write_batch.transactions.len();
        reconcile_selected_engine_guarded_batch_verified_prefix(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            &write_batch,
            verified,
        )
        .unwrap();

        let burn =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();
        assert_eq!(burn.draft_revision, 1);
        assert_eq!(burn.expected_page_bytes[6..8], 101u16.to_le_bytes());

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn incomplete_ram_reconciliation_never_authorizes_burn() {
        let (engine, registry, _) = live_engine(true, true);
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
            0,
        )
        .unwrap();

        assert_eq!(
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1,),
            Err(EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft {
                draft_revision: 1,
                confirmed_revision: None,
            })
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn missing_definition_capabilities_fail_closed() {
        let (engine, registry, _) = live_engine(false, true);
        assert!(matches!(
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1,),
            Err(EngineGuardedPageBurnError::BurnPlan(
                PageBurnPlanError::MissingBurnCapability { page: 1 }
            ))
        ));
        close_profile_registry(registry);
        close_engine(engine);

        let (engine, registry, _) = live_engine(true, false);
        assert!(matches!(
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1,),
            Err(EngineGuardedPageBurnError::CrcPlan(
                PageCrc32CheckPlanError::MissingCrc32CheckCapability { page: 1 }
            ))
        ));
        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn later_draft_edit_stales_previously_authorized_burn() {
        let (engine, registry, _) = live_engine(true, true);
        let plan =
            plan_selected_engine_guarded_page_burn(engine, registry, SIGNATURE.as_bytes(), 256, 1)
                .unwrap();

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        assert_eq!(
            require_selected_engine_guarded_page_burn_current(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &plan,
            ),
            Err(EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft {
                draft_revision: 1,
                confirmed_revision: Some(0),
            })
        );

        close_profile_registry(registry);
        close_engine(engine);
    }
}
