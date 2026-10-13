use crate::tuning::{
    CellCoord, TableEditCommand, TableEditOperation, TablePreview, TuneDraft, TuneError,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TableInterpolationMode {
    Horizontal,
    Vertical,
    TwoDimensional,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TableRectangle {
    pub anchor_row: usize,
    pub anchor_col: usize,
    pub focus_row: usize,
    pub focus_col: usize,
}

impl TableRectangle {
    fn bounds(self) -> (usize, usize, usize, usize) {
        (
            self.anchor_row.min(self.focus_row),
            self.anchor_row.max(self.focus_row),
            self.anchor_col.min(self.focus_col),
            self.anchor_col.max(self.focus_col),
        )
    }
}

pub fn preview_interpolation(
    draft: &TuneDraft,
    table_id: u32,
    rectangle: TableRectangle,
    mode: TableInterpolationMode,
) -> Result<TablePreview, TuneError> {
    let projection = draft.table_projection(table_id)?;
    let (first_row, last_row, first_col, last_col) = rectangle.bounds();
    if last_row >= projection.descriptor.rows || last_col >= projection.descriptor.cols {
        return Err(TuneError::CellOutOfBounds);
    }

    match mode {
        TableInterpolationMode::Horizontal if first_col == last_col => {
            return Err(TuneError::EmptySelection);
        }
        TableInterpolationMode::Vertical if first_row == last_row => {
            return Err(TuneError::EmptySelection);
        }
        TableInterpolationMode::TwoDimensional
            if first_row == last_row || first_col == last_col =>
        {
            return Err(TuneError::EmptySelection);
        }
        _ => {}
    }

    let mut changes = Vec::new();
    for row in first_row..=last_row {
        for col in first_col..=last_col {
            let target = match mode {
                TableInterpolationMode::Horizontal => {
                    horizontal_target(&projection, row, col, first_col, last_col)?
                }
                TableInterpolationMode::Vertical => {
                    vertical_target(&projection, row, col, first_row, last_row)?
                }
                TableInterpolationMode::TwoDimensional => bilinear_target(
                    &projection,
                    row,
                    col,
                    first_row,
                    last_row,
                    first_col,
                    last_col,
                )?,
            };

            let cell_preview = draft.preview(&TableEditCommand {
                table_id,
                cells: vec![CellCoord { row, col }],
                operation: TableEditOperation::Set(target),
            })?;
            let change = cell_preview
                .changes
                .into_iter()
                .next()
                .ok_or(TuneError::EmptySelection)?;
            if change.after != change.before {
                changes.push(change);
            }
        }
    }

    if changes.is_empty() {
        return Err(TuneError::EmptySelection);
    }

    Ok(TablePreview {
        table_id,
        base_revision: draft.revision(),
        changes,
    })
}

fn horizontal_target(
    projection: &crate::tuning::TableProjection,
    row: usize,
    col: usize,
    first_col: usize,
    last_col: usize,
) -> Result<f64, TuneError> {
    let t = axis_fraction(
        projection.x_axis[first_col],
        projection.x_axis[last_col],
        projection.x_axis[col],
    )?;
    let left = value_at(projection, row, first_col);
    let right = value_at(projection, row, last_col);
    Ok(lerp(left, right, t))
}

fn vertical_target(
    projection: &crate::tuning::TableProjection,
    row: usize,
    col: usize,
    first_row: usize,
    last_row: usize,
) -> Result<f64, TuneError> {
    let t = axis_fraction(
        projection.y_axis[first_row],
        projection.y_axis[last_row],
        projection.y_axis[row],
    )?;
    let bottom = value_at(projection, first_row, col);
    let top = value_at(projection, last_row, col);
    Ok(lerp(bottom, top, t))
}

#[allow(clippy::too_many_arguments)]
fn bilinear_target(
    projection: &crate::tuning::TableProjection,
    row: usize,
    col: usize,
    first_row: usize,
    last_row: usize,
    first_col: usize,
    last_col: usize,
) -> Result<f64, TuneError> {
    let tx = axis_fraction(
        projection.x_axis[first_col],
        projection.x_axis[last_col],
        projection.x_axis[col],
    )?;
    let ty = axis_fraction(
        projection.y_axis[first_row],
        projection.y_axis[last_row],
        projection.y_axis[row],
    )?;

    let q00 = value_at(projection, first_row, first_col);
    let q10 = value_at(projection, first_row, last_col);
    let q01 = value_at(projection, last_row, first_col);
    let q11 = value_at(projection, last_row, last_col);
    let lower = lerp(q00, q10, tx);
    let upper = lerp(q01, q11, tx);
    Ok(lerp(lower, upper, ty))
}

fn value_at(projection: &crate::tuning::TableProjection, row: usize, col: usize) -> f64 {
    projection.values[row * projection.descriptor.cols + col]
}

fn axis_fraction(start: f64, end: f64, value: f64) -> Result<f64, TuneError> {
    let span = end - start;
    if !span.is_finite() || span.abs() <= f64::EPSILON {
        return Err(TuneError::InvalidDescriptor);
    }
    let fraction = (value - start) / span;
    if !fraction.is_finite() || !(-1e-9..=1.0 + 1e-9).contains(&fraction) {
        return Err(TuneError::InvalidDescriptor);
    }
    Ok(fraction.clamp(0.0, 1.0))
}

fn lerp(start: f64, end: f64, fraction: f64) -> f64 {
    start + (end - start) * fraction
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tuning::{TableDescriptor, TableSnapshot, TuneSnapshot, TuneSnapshotOrigin};

    fn non_uniform_draft() -> TuneDraft {
        let snapshot = TuneSnapshot::new(
            99,
            TuneSnapshotOrigin::OfflineFixture,
            [TableSnapshot {
                descriptor: TableDescriptor {
                    id: 11,
                    name: "Interpolation fixture".to_string(),
                    rows: 3,
                    cols: 3,
                    minimum: 0.0,
                    maximum: 200.0,
                    quantum: 0.25,
                    quantum_origin: 0.0,
                    edit_increment: 0.5,
                    display_decimals: 2,
                },
                x_axis: vec![1000.0, 1500.0, 3000.0],
                y_axis: vec![20.0, 40.0, 100.0],
                values: vec![0.0, 90.0, 100.0, 80.0, 90.0, 120.0, 100.0, 110.0, 200.0],
                editable: vec![true; 9],
            }],
        )
        .unwrap();
        TuneDraft::from_snapshot(&snapshot)
    }

    fn whole_table() -> TableRectangle {
        TableRectangle {
            anchor_row: 0,
            anchor_col: 0,
            focus_row: 2,
            focus_col: 2,
        }
    }

    #[test]
    fn horizontal_interpolation_uses_real_x_axis_spacing() {
        let draft = non_uniform_draft();
        let preview = preview_interpolation(
            &draft,
            11,
            whole_table(),
            TableInterpolationMode::Horizontal,
        )
        .unwrap();
        let middle = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 0, col: 1 })
            .unwrap();
        assert_eq!(middle.after, 25.0);
        assert_eq!(draft.revision(), 0);
    }

    #[test]
    fn vertical_interpolation_uses_real_y_axis_spacing() {
        let draft = non_uniform_draft();
        let preview =
            preview_interpolation(&draft, 11, whole_table(), TableInterpolationMode::Vertical)
                .unwrap();
        let middle = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 1, col: 0 })
            .unwrap();
        assert_eq!(middle.after, 25.0);
    }

    #[test]
    fn bilinear_interpolation_uses_real_xy_axis_spacing() {
        let draft = non_uniform_draft();
        let preview = preview_interpolation(
            &draft,
            11,
            whole_table(),
            TableInterpolationMode::TwoDimensional,
        )
        .unwrap();
        let center = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 1, col: 1 })
            .unwrap();
        assert_eq!(center.after, 50.0);
    }

    #[test]
    fn interpolation_preview_commits_as_one_history_entry() {
        let mut draft = non_uniform_draft();
        let preview = preview_interpolation(
            &draft,
            11,
            whole_table(),
            TableInterpolationMode::TwoDimensional,
        )
        .unwrap();
        assert_eq!(preview.base_revision, 0);
        assert_eq!(draft.commit_preview(preview).unwrap(), 1);
        assert!(draft.can_undo());
        assert_eq!(draft.undo().unwrap(), 2);
        assert!(!draft.can_undo());
        assert!(draft.can_redo());
    }

    #[test]
    fn interpolation_rejects_protected_cells_atomically() {
        let draft = non_uniform_draft();
        let mut projection = draft.table_projection(11).unwrap();
        projection.editable[4] = false;

        let snapshot = TuneSnapshot::new(
            99,
            TuneSnapshotOrigin::OfflineFixture,
            [TableSnapshot {
                descriptor: projection.descriptor,
                x_axis: projection.x_axis,
                y_axis: projection.y_axis,
                values: projection.values,
                editable: projection.editable,
            }],
        )
        .unwrap();
        let protected = TuneDraft::from_snapshot(&snapshot);
        let before = protected.table_projection(11).unwrap();
        assert_eq!(
            preview_interpolation(
                &protected,
                11,
                whole_table(),
                TableInterpolationMode::TwoDimensional,
            ),
            Err(TuneError::ProtectedCell)
        );
        assert_eq!(protected.table_projection(11).unwrap(), before);
    }
}
