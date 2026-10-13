use crate::tuning::{CellCoord, TableEditCommand, TableEditOperation, TablePreview, TuneDraft, TuneError};
use crate::tuning_interpolation::TableRectangle;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct TableSmoothingParameters {
    pub strength_percent: f64,
}

pub fn preview_smoothing(
    draft: &TuneDraft,
    table_id: u32,
    rectangle: TableRectangle,
    parameters: TableSmoothingParameters,
) -> Result<TablePreview, TuneError> {
    if !parameters.strength_percent.is_finite() {
        return Err(TuneError::NonFiniteValue);
    }
    if parameters.strength_percent <= 0.0 || parameters.strength_percent > 100.0 {
        return Err(TuneError::ValueOutOfRange);
    }

    let projection = draft.table_projection(table_id)?;
    let first_row = rectangle.anchor_row.min(rectangle.focus_row);
    let last_row = rectangle.anchor_row.max(rectangle.focus_row);
    let first_col = rectangle.anchor_col.min(rectangle.focus_col);
    let last_col = rectangle.anchor_col.max(rectangle.focus_col);
    if last_row >= projection.descriptor.rows || last_col >= projection.descriptor.cols {
        return Err(TuneError::CellOutOfBounds);
    }

    let strength = parameters.strength_percent / 100.0;
    let mut changes = Vec::new();
    for row in first_row..=last_row {
        for col in first_col..=last_col {
            let current = value_at(&projection, row, col);
            let mean = weighted_neighborhood_mean(&projection, row, col);
            let target = current + (mean - current) * strength;

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

fn weighted_neighborhood_mean(
    projection: &crate::tuning::TableProjection,
    row: usize,
    col: usize,
) -> f64 {
    const WEIGHTS: [f64; 3] = [1.0, 2.0, 1.0];
    let mut weighted_sum = 0.0;
    let mut total_weight = 0.0;

    for row_offset in -1isize..=1 {
        let sample_row = row as isize + row_offset;
        if sample_row < 0 || sample_row >= projection.descriptor.rows as isize {
            continue;
        }
        for col_offset in -1isize..=1 {
            let sample_col = col as isize + col_offset;
            if sample_col < 0 || sample_col >= projection.descriptor.cols as isize {
                continue;
            }
            let row_weight = WEIGHTS[(row_offset + 1) as usize];
            let col_weight = WEIGHTS[(col_offset + 1) as usize];
            let weight = row_weight * col_weight;
            weighted_sum += value_at(projection, sample_row as usize, sample_col as usize) * weight;
            total_weight += weight;
        }
    }

    weighted_sum / total_weight
}

fn value_at(projection: &crate::tuning::TableProjection, row: usize, col: usize) -> f64 {
    projection.values[row * projection.descriptor.cols + col]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tuning::{TableDescriptor, TableSnapshot, TuneSnapshot, TuneSnapshotOrigin};

    fn smoothing_draft(editable: Vec<bool>) -> TuneDraft {
        let snapshot = TuneSnapshot::new(
            77,
            TuneSnapshotOrigin::OfflineFixture,
            [TableSnapshot {
                descriptor: TableDescriptor {
                    id: 13,
                    name: "Smoothing fixture".to_string(),
                    rows: 3,
                    cols: 3,
                    minimum: 0.0,
                    maximum: 200.0,
                    quantum: 0.25,
                    quantum_origin: 0.0,
                    edit_increment: 0.5,
                    display_decimals: 2,
                },
                x_axis: vec![1000.0, 2000.0, 3000.0],
                y_axis: vec![20.0, 60.0, 100.0],
                values: vec![0.0, 0.0, 0.0, 0.0, 160.0, 0.0, 0.0, 0.0, 80.0],
                editable,
            }],
        )
        .unwrap();
        TuneDraft::from_snapshot(&snapshot)
    }

    fn single(row: usize, col: usize) -> TableRectangle {
        TableRectangle {
            anchor_row: row,
            anchor_col: col,
            focus_row: row,
            focus_col: col,
        }
    }

    #[test]
    fn full_strength_center_uses_weighted_three_by_three_kernel() {
        let draft = smoothing_draft(vec![true; 9]);
        let preview = preview_smoothing(
            &draft,
            13,
            single(1, 1),
            TableSmoothingParameters {
                strength_percent: 100.0,
            },
        )
        .unwrap();
        assert_eq!(preview.changes.len(), 1);
        assert_eq!(preview.changes[0].after, 45.0);
        assert_eq!(draft.revision(), 0);
    }

    #[test]
    fn table_edge_renormalizes_missing_neighbors() {
        let draft = smoothing_draft(vec![true; 9]);
        let preview = preview_smoothing(
            &draft,
            13,
            single(2, 2),
            TableSmoothingParameters {
                strength_percent: 100.0,
            },
        )
        .unwrap();
        assert_eq!(preview.changes[0].after, 71.0);
    }

    #[test]
    fn strength_blends_original_toward_smoothed_value_before_quantization() {
        let draft = smoothing_draft(vec![true; 9]);
        let preview = preview_smoothing(
            &draft,
            13,
            single(1, 1),
            TableSmoothingParameters {
                strength_percent: 50.0,
            },
        )
        .unwrap();
        assert_eq!(preview.changes[0].after, 102.5);
    }

    #[test]
    fn all_targets_are_derived_from_same_committed_surface() {
        let draft = smoothing_draft(vec![true; 9]);
        let preview = preview_smoothing(
            &draft,
            13,
            TableRectangle {
                anchor_row: 1,
                anchor_col: 1,
                focus_row: 2,
                focus_col: 2,
            },
            TableSmoothingParameters {
                strength_percent: 100.0,
            },
        )
        .unwrap();
        let lower_right = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 2, col: 2 })
            .unwrap();
        assert_eq!(lower_right.after, 71.0);
    }

    #[test]
    fn protected_neighbor_can_contribute_but_selected_protected_target_rejects_atomically() {
        let mut editable = vec![true; 9];
        editable[4] = false;
        let draft = smoothing_draft(editable);

        let neighbor_preview = preview_smoothing(
            &draft,
            13,
            single(2, 2),
            TableSmoothingParameters {
                strength_percent: 100.0,
            },
        )
        .unwrap();
        assert_eq!(neighbor_preview.changes[0].after, 71.0);

        let before = draft.table_projection(13).unwrap();
        assert_eq!(
            preview_smoothing(
                &draft,
                13,
                single(1, 1),
                TableSmoothingParameters {
                    strength_percent: 100.0,
                },
            ),
            Err(TuneError::ProtectedCell)
        );
        assert_eq!(draft.table_projection(13).unwrap(), before);
    }

    #[test]
    fn smoothing_commit_is_one_history_entry() {
        let mut draft = smoothing_draft(vec![true; 9]);
        let preview = preview_smoothing(
            &draft,
            13,
            TableRectangle {
                anchor_row: 0,
                anchor_col: 0,
                focus_row: 1,
                focus_col: 1,
            },
            TableSmoothingParameters {
                strength_percent: 50.0,
            },
        )
        .unwrap();
        assert_eq!(draft.commit_preview(preview).unwrap(), 1);
        assert!(draft.can_undo());
        assert_eq!(draft.undo().unwrap(), 2);
        assert!(!draft.can_undo());
        assert!(draft.can_redo());
    }

    #[test]
    fn smoothing_rejects_invalid_strength() {
        let draft = smoothing_draft(vec![true; 9]);
        for strength_percent in [0.0, -1.0, 100.25] {
            assert_eq!(
                preview_smoothing(
                    &draft,
                    13,
                    single(1, 1),
                    TableSmoothingParameters { strength_percent },
                ),
                Err(TuneError::ValueOutOfRange)
            );
        }
        assert_eq!(
            preview_smoothing(
                &draft,
                13,
                single(1, 1),
                TableSmoothingParameters {
                    strength_percent: f64::NAN,
                },
            ),
            Err(TuneError::NonFiniteValue)
        );
    }
}
