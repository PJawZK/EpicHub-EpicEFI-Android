use std::collections::HashMap;

use crate::ecu_calibration::{CalibrationPrimitive, CalibrationShape};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct AxisBinding {
    pub primitive_name: String,
    pub runtime_channel: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TableDescriptor {
    pub table_id: String,
    pub map3d_id: String,
    pub title: String,
    pub page: Option<usize>,
    pub x_bins: AxisBinding,
    pub y_bins: AxisBinding,
    pub z_bins: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CurveDescriptor {
    pub curve_id: String,
    pub title: String,
    pub x_bins: AxisBinding,
    pub y_bins: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CompiledEditorDescriptors {
    pub tables: Vec<TableDescriptor>,
    pub curves: Vec<CurveDescriptor>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EditorCompileError {
    UnresolvedDirective(String),
    MalformedDescriptor(String),
    MissingProperty {
        descriptor: String,
        property: &'static str,
    },
    InvalidPage(String),
    UnknownPrimitive {
        descriptor: String,
        primitive: String,
    },
    InvalidAxisShape {
        descriptor: String,
        primitive: String,
    },
    InvalidTableShape {
        descriptor: String,
        expected: Vec<usize>,
        actual: Vec<usize>,
    },
    CurveLengthMismatch {
        descriptor: String,
        x: usize,
        y: usize,
    },
}

#[derive(Default)]
struct PendingTable {
    table_id: String,
    map3d_id: String,
    title: String,
    page: Option<usize>,
    x_bins: Option<AxisBinding>,
    y_bins: Option<AxisBinding>,
    z_bins: Option<String>,
}

#[derive(Default)]
struct PendingCurve {
    curve_id: String,
    title: String,
    x_bins: Option<AxisBinding>,
    y_bins: Option<String>,
}

enum PendingDescriptor {
    Table(PendingTable),
    Curve(PendingCurve),
}

pub fn compile_editor_descriptors(
    text: &str,
    primitives: &[CalibrationPrimitive],
) -> Result<CompiledEditorDescriptors, EditorCompileError> {
    let primitive_map: HashMap<&str, &CalibrationPrimitive> = primitives
        .iter()
        .map(|primitive| (primitive.name.as_str(), primitive))
        .collect();
    let mut section = String::new();
    let mut pending: Option<PendingDescriptor> = None;
    let mut result = CompiledEditorDescriptors {
        tables: Vec::new(),
        curves: Vec::new(),
    };

    for raw_line in text.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with('#') {
            return Err(EditorCompileError::UnresolvedDirective(line.to_string()));
        }
        if line.starts_with('[') && line.ends_with(']') {
            flush_pending(&mut pending, &primitive_map, &mut result)?;
            section.clear();
            section.push_str(line[1..line.len() - 1].trim());
            continue;
        }

        if section.eq_ignore_ascii_case("TableEditor") {
            if let Some(value) = assignment_value(line, "table") {
                flush_pending(&mut pending, &primitive_map, &mut result)?;
                pending = Some(PendingDescriptor::Table(parse_table_header(value)?));
                continue;
            }
            if let Some(PendingDescriptor::Table(table)) = pending.as_mut() {
                parse_table_property(table, line)?;
            }
            continue;
        }

        if section.eq_ignore_ascii_case("CurveEditor") {
            if let Some(value) = assignment_value(line, "curve") {
                flush_pending(&mut pending, &primitive_map, &mut result)?;
                pending = Some(PendingDescriptor::Curve(parse_curve_header(value)?));
                continue;
            }
            if let Some(PendingDescriptor::Curve(curve)) = pending.as_mut() {
                parse_curve_property(curve, line)?;
            }
        }
    }

    flush_pending(&mut pending, &primitive_map, &mut result)?;
    Ok(result)
}

fn assignment_value<'a>(line: &'a str, expected_key: &str) -> Option<&'a str> {
    let (key, value) = line.split_once('=')?;
    if key.trim() == expected_key {
        Some(value.trim())
    } else {
        None
    }
}

fn parse_table_header(value: &str) -> Result<PendingTable, EditorCompileError> {
    let fields = split_top_level(value);
    if fields.len() < 3 {
        return Err(EditorCompileError::MalformedDescriptor(value.to_string()));
    }
    let page = match fields.get(3).map(|value| value.trim()) {
        Some("") | None => None,
        Some(value) => Some(
            value
                .parse::<usize>()
                .map_err(|_| EditorCompileError::InvalidPage(value.to_string()))?,
        ),
    };

    Ok(PendingTable {
        table_id: fields[0].trim().to_string(),
        map3d_id: fields[1].trim().to_string(),
        title: unquote(fields[2].trim()).to_string(),
        page,
        ..PendingTable::default()
    })
}

fn parse_curve_header(value: &str) -> Result<PendingCurve, EditorCompileError> {
    let fields = split_top_level(value);
    if fields.len() < 2 {
        return Err(EditorCompileError::MalformedDescriptor(value.to_string()));
    }

    Ok(PendingCurve {
        curve_id: fields[0].trim().to_string(),
        title: unquote(fields[1].trim()).to_string(),
        ..PendingCurve::default()
    })
}

fn parse_table_property(
    table: &mut PendingTable,
    line: &str,
) -> Result<(), EditorCompileError> {
    let Some((key, value)) = line.split_once('=') else {
        return Ok(());
    };
    match key.trim() {
        "xBins" => table.x_bins = Some(parse_axis_binding(value)),
        "yBins" => table.y_bins = Some(parse_axis_binding(value)),
        "zBins" => table.z_bins = Some(first_field(value)),
        _ => {}
    }
    Ok(())
}

fn parse_curve_property(
    curve: &mut PendingCurve,
    line: &str,
) -> Result<(), EditorCompileError> {
    let Some((key, value)) = line.split_once('=') else {
        return Ok(());
    };
    match key.trim() {
        "xBins" => curve.x_bins = Some(parse_axis_binding(value)),
        "yBins" => curve.y_bins = Some(first_field(value)),
        _ => {}
    }
    Ok(())
}

fn parse_axis_binding(value: &str) -> AxisBinding {
    let fields = split_top_level(value);
    AxisBinding {
        primitive_name: fields
            .first()
            .map(|value| value.trim().to_string())
            .unwrap_or_default(),
        runtime_channel: fields
            .get(1)
            .map(|value| value.trim())
            .filter(|value| !value.is_empty())
            .map(str::to_string),
    }
}

fn first_field(value: &str) -> String {
    split_top_level(value)
        .first()
        .map(|value| value.trim().to_string())
        .unwrap_or_default()
}

fn flush_pending(
    pending: &mut Option<PendingDescriptor>,
    primitive_map: &HashMap<&str, &CalibrationPrimitive>,
    result: &mut CompiledEditorDescriptors,
) -> Result<(), EditorCompileError> {
    let Some(pending) = pending.take() else {
        return Ok(());
    };

    match pending {
        PendingDescriptor::Table(table) => {
            let descriptor = finish_table(table)?;
            validate_table(&descriptor, primitive_map)?;
            result.tables.push(descriptor);
        }
        PendingDescriptor::Curve(curve) => {
            let descriptor = finish_curve(curve)?;
            validate_curve(&descriptor, primitive_map)?;
            result.curves.push(descriptor);
        }
    }
    Ok(())
}

fn finish_table(table: PendingTable) -> Result<TableDescriptor, EditorCompileError> {
    let descriptor = table.table_id.clone();
    Ok(TableDescriptor {
        table_id: table.table_id,
        map3d_id: table.map3d_id,
        title: table.title,
        page: table.page,
        x_bins: table
            .x_bins
            .ok_or_else(|| EditorCompileError::MissingProperty {
                descriptor: descriptor.clone(),
                property: "xBins",
            })?,
        y_bins: table
            .y_bins
            .ok_or_else(|| EditorCompileError::MissingProperty {
                descriptor: descriptor.clone(),
                property: "yBins",
            })?,
        z_bins: table
            .z_bins
            .ok_or_else(|| EditorCompileError::MissingProperty {
                descriptor,
                property: "zBins",
            })?,
    })
}

fn finish_curve(curve: PendingCurve) -> Result<CurveDescriptor, EditorCompileError> {
    let descriptor = curve.curve_id.clone();
    Ok(CurveDescriptor {
        curve_id: curve.curve_id,
        title: curve.title,
        x_bins: curve
            .x_bins
            .ok_or_else(|| EditorCompileError::MissingProperty {
                descriptor: descriptor.clone(),
                property: "xBins",
            })?,
        y_bins: curve
            .y_bins
            .ok_or_else(|| EditorCompileError::MissingProperty {
                descriptor,
                property: "yBins",
            })?,
    })
}

fn validate_table(
    table: &TableDescriptor,
    primitive_map: &HashMap<&str, &CalibrationPrimitive>,
) -> Result<(), EditorCompileError> {
    let x = primitive(table.table_id.as_str(), &table.x_bins.primitive_name, primitive_map)?;
    let y = primitive(table.table_id.as_str(), &table.y_bins.primitive_name, primitive_map)?;
    let z = primitive(table.table_id.as_str(), &table.z_bins, primitive_map)?;

    let x_len = one_dimensional_len(table.table_id.as_str(), x)?;
    let y_len = one_dimensional_len(table.table_id.as_str(), y)?;
    let actual = match &z.shape {
        CalibrationShape::Array(dimensions) => dimensions.clone(),
        CalibrationShape::Scalar => {
            return Err(EditorCompileError::InvalidAxisShape {
                descriptor: table.table_id.clone(),
                primitive: z.name.clone(),
            });
        }
    };
    let expected = vec![x_len, y_len];
    if actual != expected {
        return Err(EditorCompileError::InvalidTableShape {
            descriptor: table.table_id.clone(),
            expected,
            actual,
        });
    }
    Ok(())
}

fn validate_curve(
    curve: &CurveDescriptor,
    primitive_map: &HashMap<&str, &CalibrationPrimitive>,
) -> Result<(), EditorCompileError> {
    let x = primitive(curve.curve_id.as_str(), &curve.x_bins.primitive_name, primitive_map)?;
    let y = primitive(curve.curve_id.as_str(), &curve.y_bins, primitive_map)?;
    let x_len = one_dimensional_len(curve.curve_id.as_str(), x)?;
    let y_len = one_dimensional_len(curve.curve_id.as_str(), y)?;
    if x_len != y_len {
        return Err(EditorCompileError::CurveLengthMismatch {
            descriptor: curve.curve_id.clone(),
            x: x_len,
            y: y_len,
        });
    }
    Ok(())
}

fn primitive<'a>(
    descriptor: &str,
    name: &str,
    primitive_map: &HashMap<&str, &'a CalibrationPrimitive>,
) -> Result<&'a CalibrationPrimitive, EditorCompileError> {
    primitive_map
        .get(name)
        .copied()
        .ok_or_else(|| EditorCompileError::UnknownPrimitive {
            descriptor: descriptor.to_string(),
            primitive: name.to_string(),
        })
}

fn one_dimensional_len(
    descriptor: &str,
    primitive: &CalibrationPrimitive,
) -> Result<usize, EditorCompileError> {
    match &primitive.shape {
        CalibrationShape::Array(dimensions) if dimensions.len() == 1 => Ok(dimensions[0]),
        _ => Err(EditorCompileError::InvalidAxisShape {
            descriptor: descriptor.to_string(),
            primitive: primitive.name.clone(),
        }),
    }
}

fn split_top_level(value: &str) -> Vec<String> {
    let mut fields = Vec::new();
    let mut start = 0;
    let mut quoted = false;
    let mut escaped = false;
    let mut brace_depth = 0usize;
    let mut paren_depth = 0usize;

    for (index, ch) in value.char_indices() {
        if escaped {
            escaped = false;
            continue;
        }
        if ch == '\\' && quoted {
            escaped = true;
            continue;
        }
        if ch == '"' {
            quoted = !quoted;
            continue;
        }
        if quoted {
            continue;
        }

        match ch {
            '{' => brace_depth += 1,
            '}' => brace_depth = brace_depth.saturating_sub(1),
            '(' => paren_depth += 1,
            ')' => paren_depth = paren_depth.saturating_sub(1),
            ',' if brace_depth == 0 && paren_depth == 0 => {
                fields.push(value[start..index].trim().to_string());
                start = index + ch.len_utf8();
            }
            _ => {}
        }
    }

    fields.push(value[start..].trim().to_string());
    fields
}

fn unquote(value: &str) -> &str {
    let value = value.trim();
    value
        .strip_prefix('"')
        .and_then(|v| v.strip_suffix('"'))
        .unwrap_or(value)
}

fn strip_comment(line: &str) -> &str {
    let mut quoted = false;
    let mut escaped = false;
    for (index, ch) in line.char_indices() {
        if escaped {
            escaped = false;
            continue;
        }
        if ch == '\\' && quoted {
            escaped = true;
            continue;
        }
        if ch == '"' {
            quoted = !quoted;
        } else if ch == ';' && !quoted {
            return &line[..index];
        }
    }
    line
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_calibration::compile_calibration_primitives;

    const RESOLVED_DEFINITION: &str = r#"
[Constants]
veRpmBins = array, U16, 18560, [32], "RPM", 1, 0, 0, 32000, 0
veLoadBins = array, U16, 18496, [32], "Load", 1, 0, 0, 650, 0
veTable = array, U16, 16448, [32x32], "%", 0.01, 0, 0, 650, 2
cltFuelCorrBins = array, S08, 11150, [8], "C", 2.0, 0, -100, 250, 2
cltFuelCorr = array, U08, 11158, [8], "ratio", 0.02, 0, 0, 5, 2

[TableEditor]
table = veTableTbl, veTableMap, "VE Table", 1
    xyLabels = "RPM", {bitStringValue(veAxisLabels, veOverrideMode)}
    xBins = veRpmBins, RPMValue
    yBins = veLoadBins, veTableYAxis
    zBins = veTable
    gridOrient = 250, 0, 340

[CurveEditor]
curve = cltFuelCorrCurve, "Warmup fuel manual Multiplier"
    columnLabel = "Coolant", "Multiplier"
    xAxis = -40, 120, 9
    yAxis = 0, 3, 10
    xBins = cltFuelCorrBins, coolant
    yBins = cltFuelCorr
    gauge = CLTGauge
"#;

    #[test]
    fn compiles_real_ve_table_bindings() {
        let primitives = compile_calibration_primitives(RESOLVED_DEFINITION).unwrap();
        let editors = compile_editor_descriptors(RESOLVED_DEFINITION, &primitives).unwrap();

        assert_eq!(editors.tables.len(), 1);
        let table = &editors.tables[0];
        assert_eq!(table.table_id, "veTableTbl");
        assert_eq!(table.map3d_id, "veTableMap");
        assert_eq!(table.title, "VE Table");
        assert_eq!(table.page, Some(1));
        assert_eq!(table.x_bins.primitive_name, "veRpmBins");
        assert_eq!(table.x_bins.runtime_channel.as_deref(), Some("RPMValue"));
        assert_eq!(table.y_bins.primitive_name, "veLoadBins");
        assert_eq!(table.z_bins, "veTable");
    }

    #[test]
    fn compiles_real_warmup_curve_bindings() {
        let primitives = compile_calibration_primitives(RESOLVED_DEFINITION).unwrap();
        let editors = compile_editor_descriptors(RESOLVED_DEFINITION, &primitives).unwrap();

        assert_eq!(editors.curves.len(), 1);
        let curve = &editors.curves[0];
        assert_eq!(curve.curve_id, "cltFuelCorrCurve");
        assert_eq!(curve.x_bins.primitive_name, "cltFuelCorrBins");
        assert_eq!(curve.x_bins.runtime_channel.as_deref(), Some("coolant"));
        assert_eq!(curve.y_bins, "cltFuelCorr");
    }

    #[test]
    fn rejects_unknown_primitive_reference() {
        let bad = RESOLVED_DEFINITION.replace("zBins = veTable", "zBins = missingTable");
        let primitives = compile_calibration_primitives(&bad).unwrap();
        assert_eq!(
            compile_editor_descriptors(&bad, &primitives),
            Err(EditorCompileError::UnknownPrimitive {
                descriptor: "veTableTbl".to_string(),
                primitive: "missingTable".to_string(),
            })
        );
    }

    #[test]
    fn rejects_table_dimension_mismatch() {
        let bad = RESOLVED_DEFINITION.replace("[32x32]", "[32x16]");
        let primitives = compile_calibration_primitives(&bad).unwrap();
        assert_eq!(
            compile_editor_descriptors(&bad, &primitives),
            Err(EditorCompileError::InvalidTableShape {
                descriptor: "veTableTbl".to_string(),
                expected: vec![32, 32],
                actual: vec![32, 16],
            })
        );
    }

    #[test]
    fn rejects_unresolved_editor_directive() {
        let bad = format!("{RESOLVED_DEFINITION}\n#if LAMBDA\n");
        let primitives = compile_calibration_primitives(RESOLVED_DEFINITION).unwrap();
        assert_eq!(
            compile_editor_descriptors(&bad, &primitives),
            Err(EditorCompileError::UnresolvedDirective(
                "#if LAMBDA".to_string()
            ))
        );
    }
}
