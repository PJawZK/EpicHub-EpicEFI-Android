use crate::tuning::{
    CellCoord, TableEditCommand, TableEditOperation, TablePreview, TuneDraft, TuneError,
};

pub const MAX_BRUSH_RADIUS_CELLS: usize = 32;
pub const MAX_FALLOFF_EXPONENT: f64 = 8.0;
pub const MAX_BRUSH_PATH_POINTS: usize = 256;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BrushPullMode {
    BrushStroke,
    PullDeform,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct BrushPullParameters {
    pub delta: f64,
    pub radius_cells: usize,
    pub strength_percent: f64,
    pub falloff_exponent: f64,
}

pub fn preview_brush_pull(
    draft: &TuneDraft,
    table_id: u32,
    anchor: CellCoord,
    focus: CellCoord,
    mode: BrushPullMode,
    parameters: BrushPullParameters,
) -> Result<TablePreview, TuneError> {
    match mode {
        BrushPullMode::BrushStroke => {
            preview_brush_path(draft, table_id, &[anchor, focus], parameters)
        }
        BrushPullMode::PullDeform => preview_pull(draft, table_id, focus, parameters),
    }
}

pub fn preview_brush_path(
    draft: &TuneDraft,
    table_id: u32,
    path: &[CellCoord],
    parameters: BrushPullParameters,
) -> Result<TablePreview, TuneError> {
    validate_parameters(parameters)?;
    let projection = draft.table_projection(table_id)?;
    let path = canonicalize_brush_path(path)?;
    for point in &path {
        validate_cell(&projection, *point)?;
    }

    preview_weighted_cells(draft, table_id, parameters, |coord| {
        point_to_path_distance(coord, &path)
    })
}

fn preview_pull(
    draft: &TuneDraft,
    table_id: u32,
    focus: CellCoord,
    parameters: BrushPullParameters,
) -> Result<TablePreview, TuneError> {
    validate_parameters(parameters)?;
    let projection = draft.table_projection(table_id)?;
    validate_cell(&projection, focus)?;
    let focus_index = focus.row * projection.descriptor.cols + focus.col;
    if !projection.editable[focus_index] {
        return Err(TuneError::ProtectedCell);
    }

    preview_weighted_cells(draft, table_id, parameters, |coord| {
        point_distance(coord, focus)
    })
}

fn preview_weighted_cells(
    draft: &TuneDraft,
    table_id: u32,
    parameters: BrushPullParameters,
    distance_for: impl Fn(CellCoord) -> f64,
) -> Result<TablePreview, TuneError> {
    let projection = draft.table_projection(table_id)?;
    let mut changes = Vec::new();
    for row in 0..projection.descriptor.rows {
        for col in 0..projection.descriptor.cols {
            let coord = CellCoord { row, col };
            let Some(weight) = influence_weight(
                distance_for(coord),
                parameters.radius_cells,
                parameters.falloff_exponent,
            ) else {
                continue;
            };

            let index = row * projection.descriptor.cols + col;
            if !projection.editable[index] {
                continue;
            }

            let current = projection.values[index];
            let target =
                current + parameters.delta * (parameters.strength_percent / 100.0) * weight;
            let cell_preview = draft.preview(&TableEditCommand {
                table_id,
                cells: vec![coord],
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

fn canonicalize_brush_path(path: &[CellCoord]) -> Result<Vec<CellCoord>, TuneError> {
    if path.is_empty() {
        return Err(TuneError::EmptySelection);
    }

    let mut canonical = Vec::with_capacity(path.len().min(MAX_BRUSH_PATH_POINTS));
    for point in path.iter().copied() {
        if canonical.last().copied() == Some(point) {
            continue;
        }
        if canonical.len() >= 2 {
            let a = canonical[canonical.len() - 2];
            let b = canonical[canonical.len() - 1];
            if same_forward_line(a, b, point) {
                let last = canonical.len() - 1;
                canonical[last] = point;
                continue;
            }
        }
        if canonical.len() >= MAX_BRUSH_PATH_POINTS {
            return Err(TuneError::ValueOutOfRange);
        }
        canonical.push(point);
    }
    Ok(canonical)
}

fn same_forward_line(a: CellCoord, b: CellCoord, c: CellCoord) -> bool {
    let ab_row = b.row as i64 - a.row as i64;
    let ab_col = b.col as i64 - a.col as i64;
    let bc_row = c.row as i64 - b.row as i64;
    let bc_col = c.col as i64 - b.col as i64;
    let cross = ab_row * bc_col - ab_col * bc_row;
    let dot = ab_row * bc_row + ab_col * bc_col;
    cross == 0 && dot >= 0
}

fn point_to_path_distance(point: CellCoord, path: &[CellCoord]) -> f64 {
    if path.len() == 1 {
        return point_distance(point, path[0]);
    }
    path.windows(2)
        .map(|segment| point_to_segment_distance(point, segment[0], segment[1]))
        .fold(f64::INFINITY, f64::min)
}

fn validate_parameters(parameters: BrushPullParameters) -> Result<(), TuneError> {
    if !parameters.delta.is_finite()
        || !parameters.strength_percent.is_finite()
        || !parameters.falloff_exponent.is_finite()
    {
        return Err(TuneError::NonFiniteValue);
    }
    if parameters.radius_cells > MAX_BRUSH_RADIUS_CELLS
        || parameters.strength_percent <= 0.0
        || parameters.strength_percent > 100.0
        || parameters.falloff_exponent < 0.0
        || parameters.falloff_exponent > MAX_FALLOFF_EXPONENT
    {
        return Err(TuneError::ValueOutOfRange);
    }
    Ok(())
}

fn validate_cell(
    projection: &crate::tuning::TableProjection,
    coord: CellCoord,
) -> Result<(), TuneError> {
    if coord.row >= projection.descriptor.rows || coord.col >= projection.descriptor.cols {
        return Err(TuneError::CellOutOfBounds);
    }
    Ok(())
}

fn influence_weight(distance: f64, radius_cells: usize, falloff_exponent: f64) -> Option<f64> {
    let radius = radius_cells as f64;
    if distance > radius + 1e-9 {
        return None;
    }
    if falloff_exponent == 0.0 || radius_cells == 0 {
        return Some(1.0);
    }

    // Radius is inclusive. Adding one cell keeps the outermost ring non-zero;
    // with linear falloff a radius-4 outer cell receives 20% influence.
    let base = 1.0 - distance / (radius + 1.0);
    Some(base.clamp(0.0, 1.0).powf(falloff_exponent))
}

fn point_distance(point: CellCoord, center: CellCoord) -> f64 {
    let row = point.row as f64 - center.row as f64;
    let col = point.col as f64 - center.col as f64;
    row.hypot(col)
}

fn point_to_segment_distance(point: CellCoord, start: CellCoord, end: CellCoord) -> f64 {
    let px = point.col as f64;
    let py = point.row as f64;
    let ax = start.col as f64;
    let ay = start.row as f64;
    let bx = end.col as f64;
    let by = end.row as f64;
    let dx = bx - ax;
    let dy = by - ay;
    let length_squared = dx * dx + dy * dy;
    if length_squared <= f64::EPSILON {
        return (px - ax).hypot(py - ay);
    }
    let t = (((px - ax) * dx + (py - ay) * dy) / length_squared).clamp(0.0, 1.0);
    let closest_x = ax + t * dx;
    let closest_y = ay + t * dy;
    (px - closest_x).hypot(py - closest_y)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tuning::{TableDescriptor, TableSnapshot, TuneSnapshot, TuneSnapshotOrigin};

    fn draft_with(editable: Vec<bool>, values: Vec<f64>) -> TuneDraft {
        let snapshot = TuneSnapshot::new(
            88,
            TuneSnapshotOrigin::OfflineFixture,
            [TableSnapshot {
                descriptor: TableDescriptor {
                    id: 21,
                    name: "Brush Pull fixture".to_string(),
                    rows: 5,
                    cols: 5,
                    minimum: 0.0,
                    maximum: 200.0,
                    quantum: 0.25,
                    quantum_origin: 0.0,
                    edit_increment: 0.5,
                    display_decimals: 2,
                },
                x_axis: vec![500.0, 900.0, 1600.0, 3200.0, 6400.0],
                y_axis: vec![20.0, 35.0, 70.0, 120.0, 200.0],
                values,
                editable,
            }],
        )
        .unwrap();
        TuneDraft::from_snapshot(&snapshot)
    }

    fn flat_draft() -> TuneDraft {
        draft_with(vec![true; 25], vec![50.0; 25])
    }

    fn parameters(delta: f64, radius_cells: usize) -> BrushPullParameters {
        BrushPullParameters {
            delta,
            radius_cells,
            strength_percent: 100.0,
            falloff_exponent: 1.0,
        }
    }

    #[test]
    fn brush_is_a_swept_stroke_from_anchor_to_focus() {
        let draft = flat_draft();
        let preview = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 2, col: 0 },
            CellCoord { row: 2, col: 4 },
            BrushPullMode::BrushStroke,
            parameters(10.0, 0),
        )
        .unwrap();

        assert_eq!(preview.changes.len(), 5);
        for change in &preview.changes {
            assert_eq!(change.coord.row, 2);
            assert_eq!(change.after, 60.0);
        }
        assert_eq!(draft.revision(), 0);
    }

    #[test]
    fn polyline_brush_follows_turns_without_sampling_density_amplification() {
        let draft = flat_draft();
        let sparse = preview_brush_path(
            &draft,
            21,
            &[
                CellCoord { row: 0, col: 0 },
                CellCoord { row: 0, col: 4 },
                CellCoord { row: 4, col: 4 },
            ],
            parameters(10.0, 0),
        )
        .unwrap();
        let dense = preview_brush_path(
            &draft,
            21,
            &[
                CellCoord { row: 0, col: 0 },
                CellCoord { row: 0, col: 1 },
                CellCoord { row: 0, col: 2 },
                CellCoord { row: 0, col: 3 },
                CellCoord { row: 0, col: 4 },
                CellCoord { row: 1, col: 4 },
                CellCoord { row: 2, col: 4 },
                CellCoord { row: 3, col: 4 },
                CellCoord { row: 4, col: 4 },
            ],
            parameters(10.0, 0),
        )
        .unwrap();

        assert_eq!(sparse, dense);
        assert_eq!(sparse.changes.len(), 9);
        assert_eq!(draft.revision(), 0);
    }

    #[test]
    fn brush_path_canonicalization_drops_duplicates_and_forward_collinear_points() {
        assert_eq!(
            canonicalize_brush_path(&[
                CellCoord { row: 1, col: 0 },
                CellCoord { row: 1, col: 0 },
                CellCoord { row: 1, col: 1 },
                CellCoord { row: 1, col: 2 },
                CellCoord { row: 2, col: 2 },
            ])
            .unwrap(),
            vec![
                CellCoord { row: 1, col: 0 },
                CellCoord { row: 1, col: 2 },
                CellCoord { row: 2, col: 2 },
            ]
        );
    }

    #[test]
    fn brush_path_rejects_empty_and_out_of_bounds_samples() {
        let draft = flat_draft();
        assert_eq!(
            preview_brush_path(&draft, 21, &[], parameters(1.0, 0)),
            Err(TuneError::EmptySelection)
        );
        assert_eq!(
            preview_brush_path(
                &draft,
                21,
                &[CellCoord { row: 9, col: 0 }],
                parameters(1.0, 0),
            ),
            Err(TuneError::CellOutOfBounds)
        );
    }

    #[test]
    fn brush_radius_and_linear_falloff_are_in_grid_index_space() {
        let draft = flat_draft();
        let preview = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 0, col: 0 },
            CellCoord { row: 0, col: 0 },
            BrushPullMode::BrushStroke,
            parameters(10.0, 4),
        )
        .unwrap();

        let outer = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 4, col: 0 })
            .unwrap();
        assert_eq!(outer.after, 52.0);
    }

    #[test]
    fn pull_is_centered_on_focus_and_deforms_neighbors_by_falloff() {
        let draft = flat_draft();
        let preview = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 0, col: 0 },
            CellCoord { row: 2, col: 2 },
            BrushPullMode::PullDeform,
            parameters(20.0, 1),
        )
        .unwrap();

        let center = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 2, col: 2 })
            .unwrap();
        let neighbor = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 2, col: 3 })
            .unwrap();
        assert_eq!(center.after, 70.0);
        assert_eq!(neighbor.after, 60.0);
    }

    #[test]
    fn strength_scales_the_requested_value_displacement() {
        let draft = flat_draft();
        let mut params = parameters(20.0, 0);
        params.strength_percent = 25.0;
        let preview = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 2, col: 2 },
            CellCoord { row: 2, col: 2 },
            BrushPullMode::PullDeform,
            params,
        )
        .unwrap();
        assert_eq!(preview.changes[0].after, 55.0);
    }

    #[test]
    fn hard_falloff_keeps_full_weight_inside_radius() {
        let draft = flat_draft();
        let mut params = parameters(10.0, 2);
        params.falloff_exponent = 0.0;
        let preview = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 2, col: 2 },
            CellCoord { row: 2, col: 2 },
            BrushPullMode::PullDeform,
            params,
        )
        .unwrap();
        let edge = preview
            .changes
            .iter()
            .find(|change| change.coord == CellCoord { row: 2, col: 4 })
            .unwrap();
        assert_eq!(edge.after, 60.0);
    }

    #[test]
    fn pinned_cells_resist_brush_and_pull_instead_of_becoming_partial_mutations() {
        let mut editable = vec![true; 25];
        editable[12] = false;
        let draft = draft_with(editable, vec![50.0; 25]);

        let brush = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 2, col: 0 },
            CellCoord { row: 2, col: 4 },
            BrushPullMode::BrushStroke,
            parameters(10.0, 0),
        )
        .unwrap();
        assert!(
            !brush
                .changes
                .iter()
                .any(|change| change.coord == CellCoord { row: 2, col: 2 })
        );

        assert_eq!(
            preview_brush_pull(
                &draft,
                21,
                CellCoord { row: 0, col: 0 },
                CellCoord { row: 2, col: 2 },
                BrushPullMode::PullDeform,
                parameters(10.0, 1),
            ),
            Err(TuneError::ProtectedCell)
        );
    }

    #[test]
    fn invalid_editable_target_rejects_whole_preview_without_mutation() {
        let mut values = vec![50.0; 25];
        values[12] = 199.0;
        let draft = draft_with(vec![true; 25], values);
        let before = draft.table_projection(21).unwrap();

        assert_eq!(
            preview_brush_pull(
                &draft,
                21,
                CellCoord { row: 2, col: 2 },
                CellCoord { row: 2, col: 2 },
                BrushPullMode::PullDeform,
                parameters(10.0, 1),
            ),
            Err(TuneError::ValueOutOfRange)
        );
        assert_eq!(draft.table_projection(21).unwrap(), before);
        assert_eq!(draft.revision(), 0);
    }

    #[test]
    fn brush_pull_commit_is_one_history_entry() {
        let mut draft = flat_draft();
        let preview = preview_brush_pull(
            &draft,
            21,
            CellCoord { row: 1, col: 1 },
            CellCoord { row: 3, col: 3 },
            BrushPullMode::BrushStroke,
            parameters(5.0, 1),
        )
        .unwrap();
        assert_eq!(draft.commit_preview(preview).unwrap(), 1);
        assert!(draft.can_undo());
        assert_eq!(draft.undo().unwrap(), 2);
        assert!(!draft.can_undo());
        assert!(draft.can_redo());
    }

    #[test]
    fn parameters_are_bounded_and_finite() {
        let draft = flat_draft();
        let anchor = CellCoord { row: 1, col: 1 };
        let focus = CellCoord { row: 2, col: 2 };

        for params in [
            BrushPullParameters {
                delta: f64::NAN,
                ..parameters(1.0, 1)
            },
            BrushPullParameters {
                strength_percent: 0.0,
                ..parameters(1.0, 1)
            },
            BrushPullParameters {
                strength_percent: 101.0,
                ..parameters(1.0, 1)
            },
            BrushPullParameters {
                falloff_exponent: -0.1,
                ..parameters(1.0, 1)
            },
            BrushPullParameters {
                falloff_exponent: MAX_FALLOFF_EXPONENT + 0.1,
                ..parameters(1.0, 1)
            },
            BrushPullParameters {
                radius_cells: MAX_BRUSH_RADIUS_CELLS + 1,
                ..parameters(1.0, 1)
            },
        ] {
            assert!(
                preview_brush_pull(
                    &draft,
                    21,
                    anchor,
                    focus,
                    BrushPullMode::BrushStroke,
                    params,
                )
                .is_err()
            );
        }
    }
}
