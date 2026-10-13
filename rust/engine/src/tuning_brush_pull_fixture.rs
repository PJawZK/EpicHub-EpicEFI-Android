use crate::tuning::{CellCoord, TuneError};
use crate::tuning_brush_pull::{BrushPullMode, BrushPullParameters, preview_brush_pull};
use crate::tuning_fixture::{
    OFFLINE_FIXTURE_PRIMARY_TABLE_ID, encode_primary_projection, with_offline_fixture,
};

#[allow(clippy::too_many_arguments)]
pub fn preview_offline_fixture_brush_pull_bytes(
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
    let mode = brush_pull_mode(mode_code)?;
    let parameters = brush_pull_parameters(delta_text, radius_text, strength_text, falloff_text)?;
    with_offline_fixture(|draft| {
        let preview = preview_brush_pull(
            draft,
            OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
            CellCoord {
                row: anchor_row,
                col: anchor_col,
            },
            CellCoord {
                row: focus_row,
                col: focus_col,
            },
            mode,
            parameters,
        )?;
        Ok(encode_primary_projection(draft, Some(&preview.changes)))
    })
}

#[allow(clippy::too_many_arguments)]
pub fn apply_offline_fixture_brush_pull(
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
    let mode = brush_pull_mode(mode_code)?;
    let parameters = brush_pull_parameters(delta_text, radius_text, strength_text, falloff_text)?;
    with_offline_fixture(|draft| {
        if draft.revision() != expected_revision {
            return Err(TuneError::StalePreview);
        }
        let preview = preview_brush_pull(
            draft,
            OFFLINE_FIXTURE_PRIMARY_TABLE_ID,
            CellCoord {
                row: anchor_row,
                col: anchor_col,
            },
            CellCoord {
                row: focus_row,
                col: focus_col,
            },
            mode,
            parameters,
        )?;
        draft.commit_preview(preview)
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
    let delta = parse_f64(delta_text)?;
    let radius_raw = radius_text
        .trim()
        .parse::<i64>()
        .map_err(|_| TuneError::NonFiniteValue)?;
    let radius_cells = usize::try_from(radius_raw).map_err(|_| TuneError::ValueOutOfRange)?;
    let strength_percent = parse_f64(strength_text)?;
    let falloff_exponent = parse_f64(falloff_text)?;

    Ok(BrushPullParameters {
        delta,
        radius_cells,
        strength_percent,
        falloff_exponent,
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

    #[test]
    fn mode_codes_are_explicit() {
        assert_eq!(brush_pull_mode(0), Ok(BrushPullMode::BrushStroke));
        assert_eq!(brush_pull_mode(1), Ok(BrushPullMode::PullDeform));
        assert_eq!(brush_pull_mode(2), Err(TuneError::EmptySelection));
    }

    #[test]
    fn raw_parameters_are_parsed_in_rust() {
        assert_eq!(
            brush_pull_parameters(" -4.5 ", "3", "75", "1.5"),
            Ok(BrushPullParameters {
                delta: -4.5,
                radius_cells: 3,
                strength_percent: 75.0,
                falloff_exponent: 1.5,
            })
        );
        assert_eq!(
            brush_pull_parameters("x", "3", "75", "1.5"),
            Err(TuneError::NonFiniteValue)
        );
        assert_eq!(
            brush_pull_parameters("1", "-1", "75", "1.5"),
            Err(TuneError::ValueOutOfRange)
        );
    }
}
