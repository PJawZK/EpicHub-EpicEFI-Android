use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ecu_profile::ResolvedEcuProfile;
use crate::table_projection_abi::{TABLE_PROJECTION_ABI_VERSION, TABLE_PROJECTION_HEADER_SIZE};
use crate::tune_session::TuneSession;
use crate::tune_source_image::TuneSourcePageImage;
use crate::tuning::{TuneError, TuneSnapshotOrigin};
use crate::write_reconciliation_plan::{
    PageBytePatch, WriteReconciliationPlanError, plan_semantic_table_value_patch,
};

const LIVE_CONTROLLER_ORIGIN_CODE: u32 = 3;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SessionRamWriteIntent {
    pub profile_generation: u64,
    pub draft_revision: u64,
    pub patches: Vec<PageBytePatch>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum SessionWriteIntentError {
    NonLiveControllerSource(TuneSnapshotOrigin),
    ProfileGenerationMismatch {
        session: u64,
        bundle: u64,
    },
    Projection(TuneError),
    ProjectionTooShort(usize),
    ProjectionAbiVersion(u32),
    ProjectionSizeMismatch {
        declared: usize,
        actual: usize,
    },
    ProjectionProfileGenerationMismatch {
        expected: u64,
        actual: u64,
    },
    ProjectionRevisionMismatch {
        expected: u64,
        actual: u64,
    },
    ProjectionOriginMismatch(u32),
    ProjectionTableMismatch {
        expected: u32,
        actual: u32,
    },
    ProjectionShapeOverflow,
    ProjectionValuesOutOfBounds,
    Planning(WriteReconciliationPlanError),
    PatchRangeOverflow,
    OverlappingSemanticOwnership {
        first_table: u32,
        second_table: u32,
        page: usize,
        offset: usize,
    },
}

impl From<WriteReconciliationPlanError> for SessionWriteIntentError {
    fn from(value: WriteReconciliationPlanError) -> Self {
        Self::Planning(value)
    }
}

pub fn plan_session_ram_write_intent(
    bundle: &CompiledEcuProfileBundle,
    session: &TuneSession,
    source: &TuneSourcePageImage,
) -> Result<SessionRamWriteIntent, SessionWriteIntentError> {
    if session.baseline().origin != TuneSnapshotOrigin::LiveController {
        return Err(SessionWriteIntentError::NonLiveControllerSource(
            session.baseline().origin,
        ));
    }

    let session_generation = session.profile_generation();
    let bundle_generation = bundle.profile.identity().profile_generation;
    if session_generation != bundle_generation {
        return Err(SessionWriteIntentError::ProfileGenerationMismatch {
            session: session_generation,
            bundle: bundle_generation,
        });
    }

    let revision = session.revision();
    let mut owned_patches = Vec::<(u32, PageBytePatch)>::new();

    for table_id in session.baseline().tables.keys().copied() {
        let projection = session
            .projection_bytes(table_id)
            .map_err(SessionWriteIntentError::Projection)?;
        let values = decode_projection_values(&projection, session_generation, revision, table_id)?;
        let plan = plan_semantic_table_value_patch(bundle, source, table_id, &values)?;

        for patch in plan.patches {
            require_unambiguous_ownership(&owned_patches, table_id, &patch)?;
            owned_patches.push((table_id, patch));
        }
    }

    owned_patches.sort_by_key(|(table_id, patch)| (patch.page, patch.offset, *table_id));
    Ok(SessionRamWriteIntent {
        profile_generation: session_generation,
        draft_revision: revision,
        patches: owned_patches.into_iter().map(|(_, patch)| patch).collect(),
    })
}

fn decode_projection_values(
    bytes: &[u8],
    expected_generation: u64,
    expected_revision: u64,
    expected_table_id: u32,
) -> Result<Vec<f64>, SessionWriteIntentError> {
    if bytes.len() < TABLE_PROJECTION_HEADER_SIZE {
        return Err(SessionWriteIntentError::ProjectionTooShort(bytes.len()));
    }

    let version = read_u32(bytes, 0)?;
    if version != TABLE_PROJECTION_ABI_VERSION {
        return Err(SessionWriteIntentError::ProjectionAbiVersion(version));
    }
    let declared_size = read_u32(bytes, 4)? as usize;
    if declared_size != bytes.len() {
        return Err(SessionWriteIntentError::ProjectionSizeMismatch {
            declared: declared_size,
            actual: bytes.len(),
        });
    }

    let generation = read_u64(bytes, 8)?;
    if generation != expected_generation {
        return Err(
            SessionWriteIntentError::ProjectionProfileGenerationMismatch {
                expected: expected_generation,
                actual: generation,
            },
        );
    }
    let revision = read_u64(bytes, 16)?;
    if revision != expected_revision {
        return Err(SessionWriteIntentError::ProjectionRevisionMismatch {
            expected: expected_revision,
            actual: revision,
        });
    }
    let origin = read_u32(bytes, 24)?;
    if origin != LIVE_CONTROLLER_ORIGIN_CODE {
        return Err(SessionWriteIntentError::ProjectionOriginMismatch(origin));
    }
    let table_id = read_u32(bytes, 28)?;
    if table_id != expected_table_id {
        return Err(SessionWriteIntentError::ProjectionTableMismatch {
            expected: expected_table_id,
            actual: table_id,
        });
    }

    let rows = read_u32(bytes, 32)? as usize;
    let cols = read_u32(bytes, 36)? as usize;
    let value_count = rows
        .checked_mul(cols)
        .ok_or(SessionWriteIntentError::ProjectionShapeOverflow)?;
    let values_offset = read_u32(bytes, 104)? as usize;
    let editable_offset = read_u32(bytes, 108)? as usize;
    let values_len = value_count
        .checked_mul(size_of::<f64>())
        .ok_or(SessionWriteIntentError::ProjectionShapeOverflow)?;
    let values_end = values_offset
        .checked_add(values_len)
        .ok_or(SessionWriteIntentError::ProjectionShapeOverflow)?;
    if values_offset < TABLE_PROJECTION_HEADER_SIZE
        || values_end > bytes.len()
        || values_end > editable_offset
    {
        return Err(SessionWriteIntentError::ProjectionValuesOutOfBounds);
    }

    let mut values = Vec::with_capacity(value_count);
    for offset in (values_offset..values_end).step_by(size_of::<f64>()) {
        let raw: [u8; 8] = bytes[offset..offset + 8]
            .try_into()
            .map_err(|_| SessionWriteIntentError::ProjectionValuesOutOfBounds)?;
        values.push(f64::from_le_bytes(raw));
    }
    Ok(values)
}

fn require_unambiguous_ownership(
    existing: &[(u32, PageBytePatch)],
    table_id: u32,
    candidate: &PageBytePatch,
) -> Result<(), SessionWriteIntentError> {
    let candidate_end = candidate
        .offset
        .checked_add(candidate.after.len())
        .ok_or(SessionWriteIntentError::PatchRangeOverflow)?;
    for (existing_table, patch) in existing {
        if *existing_table == table_id || patch.page != candidate.page {
            continue;
        }
        let existing_end = patch
            .offset
            .checked_add(patch.after.len())
            .ok_or(SessionWriteIntentError::PatchRangeOverflow)?;
        let overlap_start = patch.offset.max(candidate.offset);
        let overlap_end = existing_end.min(candidate_end);
        if overlap_start < overlap_end {
            return Err(SessionWriteIntentError::OverlappingSemanticOwnership {
                first_table: *existing_table,
                second_table: table_id,
                page: candidate.page,
                offset: overlap_start,
            });
        }
    }
    Ok(())
}

fn read_u32(bytes: &[u8], offset: usize) -> Result<u32, SessionWriteIntentError> {
    let raw: [u8; 4] = bytes
        .get(offset..offset + 4)
        .ok_or(SessionWriteIntentError::ProjectionValuesOutOfBounds)?
        .try_into()
        .map_err(|_| SessionWriteIntentError::ProjectionValuesOutOfBounds)?;
    Ok(u32::from_le_bytes(raw))
}

fn read_u64(bytes: &[u8], offset: usize) -> Result<u64, SessionWriteIntentError> {
    let raw: [u8; 8] = bytes
        .get(offset..offset + 8)
        .ok_or(SessionWriteIntentError::ProjectionValuesOutOfBounds)?
        .try_into()
        .map_err(|_| SessionWriteIntentError::ProjectionValuesOutOfBounds)?;
    Ok(u64::from_le_bytes(raw))
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::profile_import::OwnedSemanticTableBindingSpec;
    use crate::tuning::TuneSnapshotOrigin;

    const DEFINITION: &str = r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI session-write-intent.test.1"

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

    fn bundle(generation: u64) -> CompiledEcuProfileBundle {
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "session-write-intent".to_string(),
            profile_generation: generation,
            definition_fingerprint: None,
            root_definition: DEFINITION.to_string(),
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: vec![OwnedSemanticTableBindingSpec {
                semantic_id: 42,
                editor_table_id: "valuesTable".to_string(),
            }],
        })
        .unwrap()
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

    fn live_session(bundle: &CompiledEcuProfileBundle, page: &[u8]) -> TuneSession {
        let snapshot = bundle
            .profile
            .resolve_pages(&[page], TuneSnapshotOrigin::LiveController)
            .unwrap();
        TuneSession::from_snapshot(snapshot)
    }

    #[test]
    fn unchanged_live_session_produces_no_ram_write_intent() {
        let bundle = bundle(7);
        let page = source_page();
        let source = TuneSourcePageImage::from_pages(&[&page]);
        let session = live_session(&bundle, &page);

        assert_eq!(
            plan_session_ram_write_intent(&bundle, &session, &source).unwrap(),
            SessionRamWriteIntent {
                profile_generation: 7,
                draft_revision: 0,
                patches: Vec::new(),
            }
        );
    }

    #[test]
    fn current_draft_values_become_source_relative_byte_patches() {
        let bundle = bundle(7);
        let page = source_page();
        let source = TuneSourcePageImage::from_pages(&[&page]);
        let mut session = live_session(&bundle, &page);
        session
            .apply_rectangle_numeric(42, 0, 0, 0, 0, 0, "101", 0)
            .unwrap();

        let intent = plan_session_ram_write_intent(&bundle, &session, &source).unwrap();
        assert_eq!(intent.profile_generation, 7);
        assert_eq!(intent.draft_revision, 1);
        assert_eq!(
            intent.patches,
            vec![PageBytePatch {
                page: 1,
                offset: 6,
                before: vec![100],
                after: vec![101],
            }]
        );
    }

    #[test]
    fn offline_or_imported_sessions_cannot_gain_ram_write_authority() {
        let bundle = bundle(7);
        let page = source_page();
        let source = TuneSourcePageImage::from_pages(&[&page]);
        let mut snapshot = bundle
            .profile
            .resolve_pages(&[&page], TuneSnapshotOrigin::ImportedFile)
            .unwrap();
        snapshot.origin = TuneSnapshotOrigin::ImportedFile;
        let session = TuneSession::from_snapshot(snapshot);

        assert_eq!(
            plan_session_ram_write_intent(&bundle, &session, &source),
            Err(SessionWriteIntentError::NonLiveControllerSource(
                TuneSnapshotOrigin::ImportedFile
            ))
        );
    }

    #[test]
    fn selected_bundle_generation_must_match_active_session() {
        let source_bundle = bundle(7);
        let wrong_bundle = bundle(8);
        let page = source_page();
        let source = TuneSourcePageImage::from_pages(&[&page]);
        let session = live_session(&source_bundle, &page);

        assert_eq!(
            plan_session_ram_write_intent(&wrong_bundle, &session, &source),
            Err(SessionWriteIntentError::ProfileGenerationMismatch {
                session: 7,
                bundle: 8,
            })
        );
    }

    #[test]
    fn projection_decoder_rejects_revision_or_table_mismatch() {
        let bundle = bundle(7);
        let page = source_page();
        let session = live_session(&bundle, &page);
        let projection = session.projection_bytes(42).unwrap();

        assert_eq!(
            decode_projection_values(&projection, 7, 1, 42),
            Err(SessionWriteIntentError::ProjectionRevisionMismatch {
                expected: 1,
                actual: 0,
            })
        );
        assert_eq!(
            decode_projection_values(&projection, 7, 0, 99),
            Err(SessionWriteIntentError::ProjectionTableMismatch {
                expected: 99,
                actual: 42,
            })
        );
    }

    #[test]
    fn overlap_detection_fails_closed_between_semantic_tables() {
        let existing = vec![(
            10,
            PageBytePatch {
                page: 1,
                offset: 5,
                before: vec![1, 2, 3],
                after: vec![4, 5, 6],
            },
        )];
        let candidate = PageBytePatch {
            page: 1,
            offset: 7,
            before: vec![3, 4],
            after: vec![7, 8],
        };

        assert_eq!(
            require_unambiguous_ownership(&existing, 11, &candidate),
            Err(SessionWriteIntentError::OverlappingSemanticOwnership {
                first_table: 10,
                second_table: 11,
                page: 1,
                offset: 7,
            })
        );
    }
}
