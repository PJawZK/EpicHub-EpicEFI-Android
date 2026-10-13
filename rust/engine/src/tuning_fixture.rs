use std::sync::{LazyLock, Mutex};

use crate::table_projection_abi;
use crate::tuning::{
    CellCoord, TableDescriptor, TableEditCommand, TableEditOperation, TableProjection,
    TableSnapshot, TuneDraft, TuneError, TuneSnapshot, TuneSnapshotOrigin,
};
use crate::tuning_interpolation::{TableInterpolationMode, TableRectangle, preview_interpolation};
use crate::tuning_smoothing::{TableSmoothingParameters, preview_smoothing};

pub const OFFLINE_FIXTURE_PROFILE_GENERATION: u64 = 42;
pub const OFFLINE_FIXTURE_PRIMARY_TABLE_ID: u32 = 7;

static OFFLINE_FIXTURE_DRAFT: LazyLock<Mutex<TuneDraft>> =
    LazyLock::new(|| Mutex::new(offline_fixture_draft()));

pub fn offline_fixture_draft() -> TuneDraft {
    let snapshot = TuneSnapshot::new(
        OFFLINE_FIXTURE_PROFILE_GENERATION,
        TuneSnapshotOrigin::OfflineFixture,
        [TableSnapshot {
            descriptor: TableDescriptor {
                id: OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
                name: "VE Table 1".to_string(),
                rows: 4,
                cols: 5,
                minimum: 0.0,
                maximum: 200.0,
                quantum: 0.25,
                quantum_origin: 0.0,
                edit_increment: 0.5,
                display_decimals: 2,
            },
            x_axis: vec![1000.0, 2000.0, 3000.0, 4000.0, 5000.0],
            y_axis: vec![30.0, 60.0, 100.0, 150.0],
            values: vec![
                42.0, 46.0, 50.0, 54.0, 57.0, 50.0, 56.0, 62.0, 68.0, 72.0, 62.0, 70.0, 78.0, 86.0,
                92.0, 72.0, 82.0, 92.0, 102.0, 110.0,
            ],
            editable: vec![
                true, true, true, true, true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, false,
            ],
        }],
    )
    .expect("built-in offline fixture must remain valid");

    TuneDraft::from_snapshot(&snapshot)
}

pub(crate) fn with_offline_fixture<T>(operation: impl FnOnce(&mut TuneDraft) -> T) -> T {
    let mut guard = OFFLINE_FIXTURE_DRAFT
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut guard)
}

pub fn offline_fixture_projection_bytes() -> Vec<u8> {
    with_offline_fixture(|draft| encode_primary_projection(draft, None))
}

pub fn preview_offline_fixture_rectangle_step_bytes(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    direction: i32,
) -> Result<Vec<u8>, TuneError> {
    with_offline_fixture(|draft| {
        let command =
            rectangle_step_command(anchor_row, anchor_col, focus_row, focus_col, direction)?;
        let preview = draft.preview(&command)?;
        Ok(encode_primary_projection(draft, Some(&preview.changes)))
    })
}

pub fn apply_offline_fixture_rectangle_step(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    direction: i32,
    expected_revision: u64,
) -> Result<u64, TuneError> {
    with_offline_fixture(|draft| {
        if draft.revision() != expected_revision {
            return Err(TuneError::StalePreview);
        }
        draft.apply(&rectangle_step_command(
            anchor_row, anchor_col, focus_row, focus_col, direction,
        )?)
    })
}

pub fn preview_offline_fixture_rectangle_numeric_bytes(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    operation_code: i32,
    operand_text: &str,
) -> Result<Vec<u8>, TuneError> {
    with_offline_fixture(|draft| {
        let command = rectangle_numeric_command(
            anchor_row,
            anchor_col,
            focus_row,
            focus_col,
            operation_code,
            operand_text,
        )?;
        let preview = draft.preview(&command)?;
        Ok(encode_primary_projection(draft, Some(&preview.changes)))
    })
}

pub fn apply_offline_fixture_rectangle_numeric(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    operation_code: i32,
    operand_text: &str,
    expected_revision: u64,
) -> Result<u64, TuneError> {
    with_offline_fixture(|draft| {
        if draft.revision() != expected_revision {
            return Err(TuneError::StalePreview);
        }
        draft.apply(&rectangle_numeric_command(
            anchor_row,
            anchor_col,
            focus_row,
            focus_col,
            operation_code,
            operand_text,
        )?)
    })
}

pub fn preview_offline_fixture_interpolation_bytes(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    mode_code: i32,
) -> Result<Vec<u8>, TuneError> {
    with_offline_fixture(|draft| {
        let preview = preview_interpolation(
            draft,
            OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            interpolation_mode(mode_code)?,
        )?;
        Ok(encode_primary_projection(draft, Some(&preview.changes)))
    })
}

pub fn apply_offline_fixture_interpolation(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    mode_code: i32,
    expected_revision: u64,
) -> Result<u64, TuneError> {
    with_offline_fixture(|draft| {
        if draft.revision() != expected_revision {
            return Err(TuneError::StalePreview);
        }
        let preview = preview_interpolation(
            draft,
            OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            interpolation_mode(mode_code)?,
        )?;
        draft.commit_preview(preview)
    })
}

pub fn preview_offline_fixture_smoothing_bytes(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    strength_text: &str,
) -> Result<Vec<u8>, TuneError> {
    with_offline_fixture(|draft| {
        let preview = preview_smoothing(
            draft,
            OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            smoothing_parameters(strength_text)?,
        )?;
        Ok(encode_primary_projection(draft, Some(&preview.changes)))
    })
}

pub fn apply_offline_fixture_smoothing(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    strength_text: &str,
    expected_revision: u64,
) -> Result<u64, TuneError> {
    with_offline_fixture(|draft| {
        if draft.revision() != expected_revision {
            return Err(TuneError::StalePreview);
        }
        let preview = preview_smoothing(
            draft,
            OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            smoothing_parameters(strength_text)?,
        )?;
        draft.commit_preview(preview)
    })
}

pub fn undo_offline_fixture() -> Result<u64, TuneError> {
    with_offline_fixture(TuneDraft::undo)
}

pub fn redo_offline_fixture() -> Result<u64, TuneError> {
    with_offline_fixture(TuneDraft::redo)
}

pub(crate) fn encode_primary_projection(
    draft: &TuneDraft,
    preview_changes: Option<&[crate::tuning::CellChange]>,
) -> Vec<u8> {
    let mut projection = draft
        .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
        .expect("built-in offline fixture table must exist");
    if let Some(changes) = preview_changes {
        apply_preview_changes(&mut projection, changes);
    }
    table_projection_abi::encode(draft.profile_generation(), draft.origin(), &projection)
}

fn apply_preview_changes(projection: &mut TableProjection, changes: &[crate::tuning::CellChange]) {
    for change in changes {
        let index = change.coord.row * projection.descriptor.cols + change.coord.col;
        projection.values[index] = change.after;
    }
}

fn rectangle_cells(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
) -> Vec<CellCoord> {
    let first_row = anchor_row.min(focus_row);
    let last_row = anchor_row.max(focus_row);
    let first_col = anchor_col.min(focus_col);
    let last_col = anchor_col.max(focus_col);
    let mut cells = Vec::new();
    for row in first_row..=last_row {
        for col in first_col..=last_col {
            cells.push(CellCoord { row, col });
        }
    }
    cells
}

fn table_rectangle(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
) -> TableRectangle {
    TableRectangle {
        anchor_row,
        anchor_col,
        focus_row,
        focus_col,
    }
}

fn interpolation_mode(code: i32) -> Result<TableInterpolationMode, TuneError> {
    match code {
        0 => Ok(TableInterpolationMode::Horizontal),
        1 => Ok(TableInterpolationMode::Vertical),
        2 => Ok(TableInterpolationMode::TwoDimensional),
        _ => Err(TuneError::EmptySelection),
    }
}

fn smoothing_parameters(strength_text: &str) -> Result<TableSmoothingParameters, TuneError> {
    let strength_percent = strength_text
        .trim()
        .parse::<f64>()
        .map_err(|_| TuneError::NonFiniteValue)?;
    if !strength_percent.is_finite() {
        return Err(TuneError::NonFiniteValue);
    }
    Ok(TableSmoothingParameters { strength_percent })
}

fn rectangle_step_command(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    direction: i32,
) -> Result<TableEditCommand, TuneError> {
    let operation = match direction {
        1 => TableEditOperation::Increment,
        -1 => TableEditOperation::Decrement,
        _ => return Err(TuneError::EmptySelection),
    };

    Ok(TableEditCommand {
        table_id: OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
        cells: rectangle_cells(anchor_row, anchor_col, focus_row, focus_col),
        operation,
    })
}

fn rectangle_numeric_command(
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    operation_code: i32,
    operand_text: &str,
) -> Result<TableEditCommand, TuneError> {
    let operand = operand_text
        .trim()
        .parse::<f64>()
        .map_err(|_| TuneError::NonFiniteValue)?;
    if !operand.is_finite() {
        return Err(TuneError::NonFiniteValue);
    }
    let operation = match operation_code {
        0 => TableEditOperation::Set(operand),
        1 => TableEditOperation::Add(operand),
        2 => TableEditOperation::Scale(operand),
        _ => return Err(TuneError::EmptySelection),
    };

    Ok(TableEditCommand {
        table_id: OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
        cells: rectangle_cells(anchor_row, anchor_col, focus_row, focus_col),
        operation,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::table_projection_abi::TABLE_PROJECTION_ABI_VERSION;

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    #[test]
    fn offline_fixture_is_explicit_and_profile_bound() {
        let draft = offline_fixture_draft();
        let projection = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();

        assert_eq!(
            draft.profile_generation(),
            OFFLINE_FIXTURE_PROFILE_GENERATION
        );
        assert_eq!(draft.origin(), TuneSnapshotOrigin::OfflineFixture);
        assert_eq!(projection.descriptor.rows, 4);
        assert_eq!(projection.descriptor.cols, 5);
        assert_eq!(projection.values.len(), 20);
        assert!(!projection.editable[19]);
    }

    #[test]
    fn fixture_projection_adapter_uses_the_same_versioned_abi() {
        let draft = offline_fixture_draft();
        let bytes = encode_primary_projection(&draft, None);
        assert_eq!(read_u32(&bytes, 0), TABLE_PROJECTION_ABI_VERSION);
        assert_eq!(read_u32(&bytes, 28), OFFLINE_FIXTURE_PRIMARY_TABLE_ID);
    }

    #[test]
    fn rectangle_command_expands_in_row_major_order() {
        let command = rectangle_step_command(2, 3, 1, 1, 1).unwrap();
        assert_eq!(
            command.cells,
            vec![
                CellCoord { row: 1, col: 1 },
                CellCoord { row: 1, col: 2 },
                CellCoord { row: 1, col: 3 },
                CellCoord { row: 2, col: 1 },
                CellCoord { row: 2, col: 2 },
                CellCoord { row: 2, col: 3 },
            ]
        );
    }

    #[test]
    fn rectangle_preview_is_non_mutating_and_apply_is_one_history_entry() {
        let mut draft = offline_fixture_draft();
        let command = rectangle_step_command(0, 0, 1, 1, 1).unwrap();
        let preview = draft.preview(&command).unwrap();

        assert_eq!(preview.base_revision, 0);
        assert_eq!(preview.changes.len(), 4);
        assert_eq!(preview.changes[0].after, 42.5);
        assert_eq!(
            draft
                .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                .unwrap()
                .values[0],
            42.0
        );

        let revision = draft.apply(&command).unwrap();
        assert_eq!(revision, 1);
        let applied = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();
        assert_eq!(applied.values[0], 42.5);
        assert_eq!(applied.values[1], 46.5);
        assert_eq!(applied.values[5], 50.5);
        assert_eq!(applied.values[6], 56.5);

        assert_eq!(draft.undo().unwrap(), 2);
        assert_eq!(
            draft
                .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                .unwrap()
                .values[0],
            42.0
        );
        assert_eq!(
            draft
                .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                .unwrap()
                .values[6],
            56.0
        );
    }

    #[test]
    fn rectangle_preview_rejects_entire_selection_when_one_cell_is_protected() {
        let draft = offline_fixture_draft();
        let before = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();
        let command = rectangle_step_command(2, 3, 3, 4, 1).unwrap();

        assert_eq!(draft.preview(&command), Err(TuneError::ProtectedCell));
        assert_eq!(
            draft
                .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                .unwrap(),
            before
        );
    }

    #[test]
    fn rectangle_command_rejects_invalid_operation() {
        assert_eq!(
            rectangle_step_command(0, 0, 1, 1, 0),
            Err(TuneError::EmptySelection)
        );
    }

    #[test]
    fn numeric_operations_are_parsed_and_quantized_in_rust() {
        let mut draft = offline_fixture_draft();

        let set = rectangle_numeric_command(0, 0, 0, 1, 0, " 51.13 ").unwrap();
        let preview = draft.preview(&set).unwrap();
        assert_eq!(preview.changes[0].after, 51.25);
        assert_eq!(preview.changes[1].after, 51.25);
        assert_eq!(draft.apply(&set).unwrap(), 1);

        let add = rectangle_numeric_command(0, 0, 0, 1, 1, "0.26").unwrap();
        let preview = draft.preview(&add).unwrap();
        assert_eq!(preview.changes[0].after, 51.5);
        assert_eq!(preview.changes[1].after, 51.5);
        assert_eq!(draft.apply(&add).unwrap(), 2);

        let scale = rectangle_numeric_command(0, 0, 0, 1, 2, "1.1").unwrap();
        let preview = draft.preview(&scale).unwrap();
        assert_eq!(preview.changes[0].after, 56.75);
        assert_eq!(preview.changes[1].after, 56.75);
    }

    #[test]
    fn numeric_operation_rejects_bad_text_and_out_of_range_atomically() {
        let draft = offline_fixture_draft();
        assert_eq!(
            rectangle_numeric_command(0, 0, 0, 1, 0, "not-a-number"),
            Err(TuneError::NonFiniteValue)
        );

        let before = draft
            .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
            .unwrap();
        let command = rectangle_numeric_command(0, 0, 0, 1, 0, "999").unwrap();
        assert_eq!(draft.preview(&command), Err(TuneError::ValueOutOfRange));
        assert_eq!(
            draft
                .table_projection(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                .unwrap(),
            before
        );
    }

    #[test]
    fn interpolation_mode_codes_are_typed_in_rust() {
        assert_eq!(
            interpolation_mode(0),
            Ok(TableInterpolationMode::Horizontal)
        );
        assert_eq!(interpolation_mode(1), Ok(TableInterpolationMode::Vertical));
        assert_eq!(
            interpolation_mode(2),
            Ok(TableInterpolationMode::TwoDimensional)
        );
        assert_eq!(interpolation_mode(3), Err(TuneError::EmptySelection));
    }

    #[test]
    fn smoothing_strength_is_parsed_in_rust() {
        assert_eq!(
            smoothing_parameters(" 50.5 "),
            Ok(TableSmoothingParameters {
                strength_percent: 50.5,
            })
        );
        assert_eq!(
            smoothing_parameters("not-a-number"),
            Err(TuneError::NonFiniteValue)
        );
    }
}
