use crate::table_projection_abi;
use crate::tuning::{
    CellChange, CellCoord, TableEditCommand, TableEditOperation, TableProjection, TuneDraft,
    TuneError, TuneSnapshot,
};
use crate::tuning_brush_pull::{
    BrushPullMode, BrushPullParameters, preview_brush_path, preview_brush_pull,
};
use crate::tuning_interpolation::{TableInterpolationMode, TableRectangle, preview_interpolation};
use crate::tuning_smoothing::{TableSmoothingParameters, preview_smoothing};

#[derive(Clone, Debug)]
pub struct TuneSession {
    baseline: TuneSnapshot,
    draft: TuneDraft,
}

impl TuneSession {
    pub fn from_snapshot(snapshot: TuneSnapshot) -> Self {
        let draft = TuneDraft::from_snapshot(&snapshot);
        Self {
            baseline: snapshot,
            draft,
        }
    }

    pub fn profile_generation(&self) -> u64 {
        self.draft.profile_generation()
    }

    pub fn revision(&self) -> u64 {
        self.draft.revision()
    }

    pub fn baseline(&self) -> &TuneSnapshot {
        &self.baseline
    }

    pub fn projection_bytes(&self, table_id: u32) -> Result<Vec<u8>, TuneError> {
        self.encode_projection(table_id, None)
    }

    pub fn preview_rectangle_step_bytes(
        &self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        direction: i32,
    ) -> Result<Vec<u8>, TuneError> {
        let command = rectangle_step_command(
            table_id, anchor_row, anchor_col, focus_row, focus_col, direction,
        )?;
        let preview = self.draft.preview(&command)?;
        self.encode_projection(table_id, Some(&preview.changes))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_rectangle_step(
        &mut self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        direction: i32,
        expected_revision: u64,
    ) -> Result<u64, TuneError> {
        self.require_revision(expected_revision)?;
        self.draft.apply(&rectangle_step_command(
            table_id, anchor_row, anchor_col, focus_row, focus_col, direction,
        )?)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn preview_rectangle_numeric_bytes(
        &self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        operation_code: i32,
        operand_text: &str,
    ) -> Result<Vec<u8>, TuneError> {
        let command = rectangle_numeric_command(
            table_id,
            anchor_row,
            anchor_col,
            focus_row,
            focus_col,
            operation_code,
            operand_text,
        )?;
        let preview = self.draft.preview(&command)?;
        self.encode_projection(table_id, Some(&preview.changes))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_rectangle_numeric(
        &mut self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        operation_code: i32,
        operand_text: &str,
        expected_revision: u64,
    ) -> Result<u64, TuneError> {
        self.require_revision(expected_revision)?;
        self.draft.apply(&rectangle_numeric_command(
            table_id,
            anchor_row,
            anchor_col,
            focus_row,
            focus_col,
            operation_code,
            operand_text,
        )?)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn preview_interpolation_bytes(
        &self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        mode_code: i32,
    ) -> Result<Vec<u8>, TuneError> {
        let preview = preview_interpolation(
            &self.draft,
            table_id,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            interpolation_mode(mode_code)?,
        )?;
        self.encode_projection(table_id, Some(&preview.changes))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_interpolation(
        &mut self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        mode_code: i32,
        expected_revision: u64,
    ) -> Result<u64, TuneError> {
        self.require_revision(expected_revision)?;
        let preview = preview_interpolation(
            &self.draft,
            table_id,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            interpolation_mode(mode_code)?,
        )?;
        self.draft.commit_preview(preview)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn preview_smoothing_bytes(
        &self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        strength_text: &str,
    ) -> Result<Vec<u8>, TuneError> {
        let preview = preview_smoothing(
            &self.draft,
            table_id,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            smoothing_parameters(strength_text)?,
        )?;
        self.encode_projection(table_id, Some(&preview.changes))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_smoothing(
        &mut self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        strength_text: &str,
        expected_revision: u64,
    ) -> Result<u64, TuneError> {
        self.require_revision(expected_revision)?;
        let preview = preview_smoothing(
            &self.draft,
            table_id,
            table_rectangle(anchor_row, anchor_col, focus_row, focus_col),
            smoothing_parameters(strength_text)?,
        )?;
        self.draft.commit_preview(preview)
    }

    #[allow(clippy::too_many_arguments)]
    pub fn preview_brush_pull_bytes(
        &self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        mode_code: i32,
        delta_text: &str,
        radius_text: &str,
        strength_text: &str,
        falloff_text: &str,
    ) -> Result<Vec<u8>, TuneError> {
        let preview = preview_brush_pull(
            &self.draft,
            table_id,
            CellCoord {
                row: anchor_row,
                col: anchor_col,
            },
            CellCoord {
                row: focus_row,
                col: focus_col,
            },
            brush_pull_mode(mode_code)?,
            brush_pull_parameters(delta_text, radius_text, strength_text, falloff_text)?,
        )?;
        self.encode_projection(table_id, Some(&preview.changes))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_brush_pull(
        &mut self,
        table_id: u32,
        anchor_row: usize,
        anchor_col: usize,
        focus_row: usize,
        focus_col: usize,
        mode_code: i32,
        delta_text: &str,
        radius_text: &str,
        strength_text: &str,
        falloff_text: &str,
        expected_revision: u64,
    ) -> Result<u64, TuneError> {
        self.require_revision(expected_revision)?;
        let preview = preview_brush_pull(
            &self.draft,
            table_id,
            CellCoord {
                row: anchor_row,
                col: anchor_col,
            },
            CellCoord {
                row: focus_row,
                col: focus_col,
            },
            brush_pull_mode(mode_code)?,
            brush_pull_parameters(delta_text, radius_text, strength_text, falloff_text)?,
        )?;
        self.draft.commit_preview(preview)
    }

    pub fn preview_brush_path_bytes(
        &self,
        table_id: u32,
        path: &[CellCoord],
        delta_text: &str,
        radius_text: &str,
        strength_text: &str,
        falloff_text: &str,
    ) -> Result<Vec<u8>, TuneError> {
        let preview = preview_brush_path(
            &self.draft,
            table_id,
            path,
            brush_pull_parameters(delta_text, radius_text, strength_text, falloff_text)?,
        )?;
        self.encode_projection(table_id, Some(&preview.changes))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn apply_brush_path(
        &mut self,
        table_id: u32,
        path: &[CellCoord],
        delta_text: &str,
        radius_text: &str,
        strength_text: &str,
        falloff_text: &str,
        expected_revision: u64,
    ) -> Result<u64, TuneError> {
        self.require_revision(expected_revision)?;
        let preview = preview_brush_path(
            &self.draft,
            table_id,
            path,
            brush_pull_parameters(delta_text, radius_text, strength_text, falloff_text)?,
        )?;
        self.draft.commit_preview(preview)
    }

    pub fn undo(&mut self) -> Result<u64, TuneError> {
        self.draft.undo()
    }

    pub fn redo(&mut self) -> Result<u64, TuneError> {
        self.draft.redo()
    }

    fn require_revision(&self, expected_revision: u64) -> Result<(), TuneError> {
        if self.draft.revision() != expected_revision {
            return Err(TuneError::StalePreview);
        }
        Ok(())
    }

    fn encode_projection(
        &self,
        table_id: u32,
        preview_changes: Option<&[CellChange]>,
    ) -> Result<Vec<u8>, TuneError> {
        let mut projection = self.draft.table_projection(table_id)?;
        if let Some(changes) = preview_changes {
            apply_preview_changes(&mut projection, changes);
        }
        Ok(table_projection_abi::encode(
            self.draft.profile_generation(),
            self.draft.origin(),
            &projection,
        ))
    }
}

fn apply_preview_changes(projection: &mut TableProjection, changes: &[CellChange]) {
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

fn rectangle_step_command(
    table_id: u32,
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
        table_id,
        cells: rectangle_cells(anchor_row, anchor_col, focus_row, focus_col),
        operation,
    })
}

#[allow(clippy::too_many_arguments)]
fn rectangle_numeric_command(
    table_id: u32,
    anchor_row: usize,
    anchor_col: usize,
    focus_row: usize,
    focus_col: usize,
    operation_code: i32,
    operand_text: &str,
) -> Result<TableEditCommand, TuneError> {
    let operand = parse_f64(operand_text)?;
    let operation = match operation_code {
        0 => TableEditOperation::Set(operand),
        1 => TableEditOperation::Add(operand),
        2 => TableEditOperation::Scale(operand),
        _ => return Err(TuneError::EmptySelection),
    };
    Ok(TableEditCommand {
        table_id,
        cells: rectangle_cells(anchor_row, anchor_col, focus_row, focus_col),
        operation,
    })
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
    Ok(TableSmoothingParameters {
        strength_percent: parse_f64(strength_text)?,
    })
}

fn brush_pull_mode(code: i32) -> Result<BrushPullMode, TuneError> {
    match code {
        0 => Ok(BrushPullMode::BrushStroke),
        1 => Ok(BrushPullMode::PullDeform),
        _ => Err(TuneError::EmptySelection),
    }
}

fn brush_pull_parameters(
    delta_text: &str,
    radius_text: &str,
    strength_text: &str,
    falloff_text: &str,
) -> Result<BrushPullParameters, TuneError> {
    let radius_raw = radius_text
        .trim()
        .parse::<i64>()
        .map_err(|_| TuneError::NonFiniteValue)?;
    let radius_cells = usize::try_from(radius_raw).map_err(|_| TuneError::ValueOutOfRange)?;
    Ok(BrushPullParameters {
        delta: parse_f64(delta_text)?,
        radius_cells,
        strength_percent: parse_f64(strength_text)?,
        falloff_exponent: parse_f64(falloff_text)?,
    })
}

fn parse_f64(text: &str) -> Result<f64, TuneError> {
    let value = text
        .trim()
        .parse::<f64>()
        .map_err(|_| TuneError::NonFiniteValue)?;
    if !value.is_finite() {
        return Err(TuneError::NonFiniteValue);
    }
    Ok(value)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tuning_fixture::{OFFLINE_FIXTURE_PRIMARY_TABLE_ID, offline_fixture_snapshot};

    fn session() -> TuneSession {
        TuneSession::from_snapshot(offline_fixture_snapshot())
    }

    #[test]
    fn session_owns_independent_draft_and_immutable_baseline() {
        let mut first = session();
        let second = session();
        let table_id = OFFLINE_FIXTURE_PRIMARY_TABLE_ID;

        assert_eq!(
            first.profile_generation(),
            first.baseline().profile_generation
        );
        assert_eq!(first.revision(), 0);
        assert_eq!(second.revision(), 0);

        first
            .apply_rectangle_step(table_id, 0, 0, 0, 0, 1, 0)
            .unwrap();
        assert_eq!(first.revision(), 1);
        assert_eq!(second.revision(), 0);
        assert_eq!(first.baseline().tables[&table_id].values[0], 42.0);
    }

    #[test]
    fn stale_revision_is_session_local() {
        let mut session = session();
        let table_id = OFFLINE_FIXTURE_PRIMARY_TABLE_ID;
        session
            .apply_rectangle_step(table_id, 0, 0, 0, 0, 1, 0)
            .unwrap();
        assert_eq!(
            session.apply_rectangle_step(table_id, 0, 0, 0, 0, 1, 0),
            Err(TuneError::StalePreview)
        );
    }

    #[test]
    fn projection_is_addressed_by_semantic_table_id() {
        let session = session();
        assert!(
            session
                .projection_bytes(OFFLINE_FIXTURE_PRIMARY_TABLE_ID)
                .is_ok()
        );
        assert_eq!(
            session.projection_bytes(u32::MAX),
            Err(TuneError::UnknownTable)
        );
    }
}
