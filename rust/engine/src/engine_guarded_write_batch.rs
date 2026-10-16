use crate::ecu_profile::ResolvedEcuProfile;
use crate::engine::{with_tune_source_authority, with_tune_source_authority_mut};
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::ram_source_provenance::{RamSourceProvenance, RamSourceProvenanceError};
use crate::session_write_intent::{SessionWriteIntentError, plan_session_ram_write_intent};
use crate::tune_source_image::TuneSourcePageImage;
use crate::tuning::TuneSnapshotOrigin;
use crate::verified_source_reconciliation::{
    VerifiedSourceReconciliationError, reconcile_verified_transaction_prefix,
};
use crate::write_reconciliation_transaction::{
    GuardedRamWriteTransaction, ReconciliationTransactionError, plan_guarded_ram_write_transactions,
};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineGuardedRamWriteBatch {
    pub profile_generation: u64,
    pub draft_revision: u64,
    pub transactions: Vec<GuardedRamWriteTransaction>,
    source_image: TuneSourcePageImage,
}

#[derive(Clone, Debug, PartialEq)]
pub enum EngineGuardedRamWriteBatchError {
    ProfileAuthority(RegistryStoreError),
    InvalidEngineHandle,
    MissingSourceImage,
    NonLiveControllerSource(TuneSnapshotOrigin),
    StaleDraftRevision { expected: u64, actual: u64 },
    StaleProfileGeneration { expected: u64, actual: u64 },
    SourceImageChanged,
    Intent(SessionWriteIntentError),
    Transaction(ReconciliationTransactionError),
}

#[derive(Clone, Debug, PartialEq)]
pub enum EngineGuardedSourceReconciliationError {
    GuardedBatch(EngineGuardedRamWriteBatchError),
    VerifiedSource(VerifiedSourceReconciliationError),
}

pub fn plan_selected_engine_guarded_ram_write_batch(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    expected_draft_revision: u64,
) -> Result<EngineGuardedRamWriteBatch, EngineGuardedRamWriteBatchError> {
    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            with_tune_source_authority(engine_handle, |session, source| {
                let actual_revision = session.revision();
                if actual_revision != expected_draft_revision {
                    return Err(EngineGuardedRamWriteBatchError::StaleDraftRevision {
                        expected: expected_draft_revision,
                        actual: actual_revision,
                    });
                }
                if session.baseline().origin != TuneSnapshotOrigin::LiveController {
                    return Err(EngineGuardedRamWriteBatchError::NonLiveControllerSource(
                        session.baseline().origin,
                    ));
                }
                let source = source.ok_or(EngineGuardedRamWriteBatchError::MissingSourceImage)?;
                let intent = plan_session_ram_write_intent(bundle, session, source)
                    .map_err(EngineGuardedRamWriteBatchError::Intent)?;

                let mut transactions = Vec::new();
                for patch in &intent.patches {
                    transactions.extend(
                        plan_guarded_ram_write_transactions(bundle, patch)
                            .map_err(EngineGuardedRamWriteBatchError::Transaction)?,
                    );
                }

                Ok(EngineGuardedRamWriteBatch {
                    profile_generation: intent.profile_generation,
                    draft_revision: intent.draft_revision,
                    transactions,
                    source_image: source.clone(),
                })
            })
            .ok_or(EngineGuardedRamWriteBatchError::InvalidEngineHandle)?
        },
    )
    .map_err(EngineGuardedRamWriteBatchError::ProfileAuthority)?
}

pub fn require_selected_engine_guarded_batch_current(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    batch: &EngineGuardedRamWriteBatch,
) -> Result<(), EngineGuardedRamWriteBatchError> {
    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            let selected_generation = bundle.profile.identity().profile_generation;
            if selected_generation != batch.profile_generation {
                return Err(EngineGuardedRamWriteBatchError::StaleProfileGeneration {
                    expected: batch.profile_generation,
                    actual: selected_generation,
                });
            }

            with_tune_source_authority(engine_handle, |session, source| {
                let actual_generation = session.profile_generation();
                if actual_generation != batch.profile_generation {
                    return Err(EngineGuardedRamWriteBatchError::StaleProfileGeneration {
                        expected: batch.profile_generation,
                        actual: actual_generation,
                    });
                }
                let actual_revision = session.revision();
                if actual_revision != batch.draft_revision {
                    return Err(EngineGuardedRamWriteBatchError::StaleDraftRevision {
                        expected: batch.draft_revision,
                        actual: actual_revision,
                    });
                }
                if session.baseline().origin != TuneSnapshotOrigin::LiveController {
                    return Err(EngineGuardedRamWriteBatchError::NonLiveControllerSource(
                        session.baseline().origin,
                    ));
                }
                let source = source.ok_or(EngineGuardedRamWriteBatchError::MissingSourceImage)?;
                if source != &batch.source_image {
                    return Err(EngineGuardedRamWriteBatchError::SourceImageChanged);
                }
                Ok(())
            })
            .ok_or(EngineGuardedRamWriteBatchError::InvalidEngineHandle)?
        },
    )
    .map_err(EngineGuardedRamWriteBatchError::ProfileAuthority)?
}

pub fn reconcile_selected_engine_guarded_batch_verified_prefix(
    engine_handle: i64,
    registry_handle: i64,
    runtime_signature_response: &[u8],
    max_signature_response_bytes: usize,
    batch: &EngineGuardedRamWriteBatch,
    verified_transaction_count: usize,
) -> Result<(), EngineGuardedSourceReconciliationError> {
    with_selected_compiled_bundle(
        registry_handle,
        runtime_signature_response,
        max_signature_response_bytes,
        |bundle| {
            let selected_generation = bundle.profile.identity().profile_generation;
            if selected_generation != batch.profile_generation {
                return Err(EngineGuardedSourceReconciliationError::GuardedBatch(
                    EngineGuardedRamWriteBatchError::StaleProfileGeneration {
                        expected: batch.profile_generation,
                        actual: selected_generation,
                    },
                ));
            }

            with_tune_source_authority_mut(engine_handle, |session, source| {
                let actual_generation = session.profile_generation();
                if actual_generation != batch.profile_generation {
                    return Err(EngineGuardedSourceReconciliationError::GuardedBatch(
                        EngineGuardedRamWriteBatchError::StaleProfileGeneration {
                            expected: batch.profile_generation,
                            actual: actual_generation,
                        },
                    ));
                }
                let actual_revision = session.revision();
                if actual_revision != batch.draft_revision {
                    return Err(EngineGuardedSourceReconciliationError::GuardedBatch(
                        EngineGuardedRamWriteBatchError::StaleDraftRevision {
                            expected: batch.draft_revision,
                            actual: actual_revision,
                        },
                    ));
                }
                if session.baseline().origin != TuneSnapshotOrigin::LiveController {
                    return Err(EngineGuardedSourceReconciliationError::GuardedBatch(
                        EngineGuardedRamWriteBatchError::NonLiveControllerSource(
                            session.baseline().origin,
                        ),
                    ));
                }
                let source = source.ok_or(EngineGuardedSourceReconciliationError::GuardedBatch(
                    EngineGuardedRamWriteBatchError::MissingSourceImage,
                ))?;
                if *source != batch.source_image {
                    return Err(EngineGuardedSourceReconciliationError::GuardedBatch(
                        EngineGuardedRamWriteBatchError::SourceImageChanged,
                    ));
                }

                let reconciled = reconcile_verified_transaction_prefix(
                    &*source,
                    &batch.transactions,
                    verified_transaction_count,
                )
                .map_err(EngineGuardedSourceReconciliationError::VerifiedSource)?;
                let base_provenance = source.provenance().cloned().unwrap_or_else(|| {
                    RamSourceProvenance::from_live_controller_read(batch.profile_generation, 0)
                });
                let provenance = base_provenance
                    .reconciled_with_verified_prefix(
                        batch.draft_revision,
                        &batch.transactions,
                        verified_transaction_count,
                    )
                    .map_err(|error| match error {
                        RamSourceProvenanceError::VerifiedPrefixTooLarge {
                            verified,
                            available,
                        } => EngineGuardedSourceReconciliationError::VerifiedSource(
                            VerifiedSourceReconciliationError::VerifiedPrefixTooLarge {
                                verified,
                                available,
                            },
                        ),
                    })?;
                *source = reconciled.with_provenance(provenance);
                Ok(())
            })
            .ok_or(EngineGuardedSourceReconciliationError::GuardedBatch(
                EngineGuardedRamWriteBatchError::InvalidEngineHandle,
            ))?
        },
    )
    .map_err(|error| {
        EngineGuardedSourceReconciliationError::GuardedBatch(
            EngineGuardedRamWriteBatchError::ProfileAuthority(error),
        )
    })?
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
    use crate::engine::{
        close_engine, create_engine, replace_tune_source, with_tune_session, with_tune_source_image,
    };
    use crate::live_tune_install::install_selected_live_pages;
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::profile_registry_store::{
        close_profile_registry, create_profile_registry, register_definition_bundle_import,
    };
    use crate::ram_source_provenance::engine_ram_source_state;
    use crate::tune_session::TuneSession;

    const SIGNATURE: &str = "rusEFI engine-guarded-write-batch.test.1";
    const DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI engine-guarded-write-batch.test.1"

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

    fn bundle_import(generation: u64) -> OwnedDefinitionBundleImport {
        OwnedDefinitionBundleImport {
            profile_name: "engine-guarded-write-batch".to_string(),
            profile_generation: generation,
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

    fn live_engine() -> (i64, i64, Vec<u8>) {
        let engine = create_engine();
        let registry = create_profile_registry();
        register_definition_bundle_import(registry, &bundle_import(77)).unwrap();
        let page = source_page(100);
        install_selected_live_pages(engine, registry, SIGNATURE.as_bytes(), 256, &[&page]).unwrap();
        (engine, registry, page)
    }

    #[test]
    fn edited_live_session_becomes_guarded_transaction_batch() {
        let (engine, registry, _) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();

        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        assert_eq!(batch.profile_generation, 77);
        assert_eq!(batch.draft_revision, 1);
        assert_eq!(batch.transactions.len(), 1);
        assert_eq!(batch.transactions[0].page, 1);
        assert_eq!(batch.transactions[0].offset, 6);
        assert_eq!(batch.transactions[0].before, vec![100]);
        assert_eq!(batch.transactions[0].after, vec![101]);
        assert_eq!(
            require_selected_engine_guarded_batch_current(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
            ),
            Ok(())
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn unchanged_live_session_is_a_valid_empty_batch() {
        let (engine, registry, _) = live_engine();
        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            0,
        )
        .unwrap();
        assert_eq!(batch.profile_generation, 77);
        assert_eq!(batch.draft_revision, 0);
        assert!(batch.transactions.is_empty());

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn later_draft_edit_stales_the_batch() {
        let (engine, registry, _) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "102", 1)
                .unwrap();
        })
        .unwrap();

        assert_eq!(
            require_selected_engine_guarded_batch_current(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
            ),
            Err(EngineGuardedRamWriteBatchError::StaleDraftRevision {
                expected: 1,
                actual: 2,
            })
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn live_source_reload_stales_batch_even_when_revision_and_generation_match() {
        let (engine, registry, old_page) = live_engine();
        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            0,
        )
        .unwrap();

        let new_page = source_page(101);
        let selected = crate::profile_registry_store::with_selected_compiled_bundle(
            registry,
            SIGNATURE.as_bytes(),
            256,
            |bundle| {
                bundle
                    .profile
                    .resolve_pages(&[&new_page], TuneSnapshotOrigin::LiveController)
            },
        )
        .unwrap()
        .unwrap();
        assert!(replace_tune_source(
            engine,
            TuneSession::from_snapshot(selected),
            TuneSourcePageImage::from_pages(&[&new_page]),
        ));
        assert_ne!(old_page, new_page);

        assert_eq!(
            require_selected_engine_guarded_batch_current(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
            ),
            Err(EngineGuardedRamWriteBatchError::SourceImageChanged)
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn verified_prefix_reconciles_only_retained_source_and_preserves_session_history() {
        let (engine, registry, original_page) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();

        assert_eq!(
            reconcile_selected_engine_guarded_batch_verified_prefix(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
                1,
            ),
            Ok(())
        );

        let retained =
            with_tune_source_image(engine, |source| source.unwrap().page(0).unwrap().to_vec())
                .unwrap();
        assert_eq!(retained[6], 101);
        assert_eq!(retained[7..], original_page[7..]);
        assert_eq!(
            with_tune_session(engine, |session| session.revision()),
            Some(1)
        );
        let ram_state = engine_ram_source_state(engine).unwrap().unwrap();
        assert_eq!(ram_state.current_draft_revision, 1);
        assert_eq!(ram_state.provenance.confirmed_draft_revision, Some(1));
        assert_eq!(ram_state.provenance.verified_write_ranges.len(), 1);
        assert!(
            ram_state
                .provenance
                .last_reconciliation
                .as_ref()
                .unwrap()
                .complete
        );
        assert!(
            with_tune_session(engine, |session| session.undo())
                .unwrap()
                .is_ok()
        );
        assert!(
            with_tune_session(engine, |session| session.redo())
                .unwrap()
                .is_ok()
        );

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn incomplete_reconciliation_clears_whole_draft_confirmation_without_changing_unverified_bytes() {
        let (engine, registry, original_page) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();
        assert_eq!(batch.transactions.len(), 1);

        assert_eq!(
            reconcile_selected_engine_guarded_batch_verified_prefix(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
                0,
            ),
            Ok(())
        );

        assert_eq!(
            with_tune_source_image(engine, |source| source.unwrap().page(0).unwrap().to_vec()),
            Some(original_page),
        );
        let ram_state = engine_ram_source_state(engine).unwrap().unwrap();
        assert_eq!(ram_state.current_draft_revision, 1);
        assert_eq!(ram_state.provenance.confirmed_draft_revision, None);
        assert!(ram_state.provenance.verified_write_ranges.is_empty());
        let last = ram_state.provenance.last_reconciliation.unwrap();
        assert_eq!(last.draft_revision, 1);
        assert_eq!(last.verified_transaction_count, 0);
        assert_eq!(last.transaction_count, 1);
        assert!(!last.complete);

        close_profile_registry(registry);
        close_engine(engine);
    }

    #[test]
    fn stale_or_invalid_reconciliation_never_replaces_retained_source() {
        let (engine, registry, original_page) = live_engine();
        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
                .unwrap();
        })
        .unwrap();
        let batch = plan_selected_engine_guarded_ram_write_batch(
            engine,
            registry,
            SIGNATURE.as_bytes(),
            256,
            1,
        )
        .unwrap();

        assert_eq!(
            reconcile_selected_engine_guarded_batch_verified_prefix(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
                2,
            ),
            Err(EngineGuardedSourceReconciliationError::VerifiedSource(
                VerifiedSourceReconciliationError::VerifiedPrefixTooLarge {
                    verified: 2,
                    available: 1,
                }
            ))
        );
        assert_eq!(
            with_tune_source_image(engine, |source| source.unwrap().page(0).unwrap().to_vec()),
            Some(original_page.clone()),
        );

        with_tune_session(engine, |session| {
            session
                .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "102", 1)
                .unwrap();
        })
        .unwrap();
        assert_eq!(
            reconcile_selected_engine_guarded_batch_verified_prefix(
                engine,
                registry,
                SIGNATURE.as_bytes(),
                256,
                &batch,
                1,
            ),
            Err(EngineGuardedSourceReconciliationError::GuardedBatch(
                EngineGuardedRamWriteBatchError::StaleDraftRevision {
                    expected: 1,
                    actual: 2,
                }
            ))
        );
        assert_eq!(
            with_tune_source_image(engine, |source| source.unwrap().page(0).unwrap().to_vec()),
            Some(original_page),
        );

        close_profile_registry(registry);
        close_engine(engine);
    }
}
