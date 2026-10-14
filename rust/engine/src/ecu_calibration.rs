#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StorageType {
    U08,
    S08,
    U16,
    S16,
    U32,
    S32,
    F32,
}

impl StorageType {
    fn parse(value: &str) -> Result<Self, CalibrationCompileError> {
        match value.trim() {
            "U08" => Ok(Self::U08),
            "S08" => Ok(Self::S08),
            "U16" => Ok(Self::U16),
            "S16" => Ok(Self::S16),
            "U32" => Ok(Self::U32),
            "S32" => Ok(Self::S32),
            "F32" => Ok(Self::F32),
            other => Err(CalibrationCompileError::UnsupportedStorageType(
                other.to_string(),
            )),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum CalibrationShape {
    Scalar,
    Array(Vec<usize>),
}

#[derive(Clone, Debug, PartialEq)]
pub struct CalibrationPrimitive {
    pub name: String,
    pub storage_type: StorageType,
    pub offset: usize,
    pub shape: CalibrationShape,
    pub units: String,
    pub scale: f64,
    pub translate: f64,
    pub minimum: Option<f64>,
    pub maximum: Option<f64>,
    pub decimals: Option<u8>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum CalibrationCompileError {
    UnresolvedDirective(String),
    MalformedAssignment(String),
    UnsupportedStorageType(String),
    InvalidOffset(String),
    InvalidDimensions(String),
    InvalidNumber(String),
    InvalidDecimals(String),
    MissingField {
        name: String,
        field: &'static str,
    },
}

pub fn compile_calibration_primitives(
    text: &str,
) -> Result<Vec<CalibrationPrimitive>, CalibrationCompileError> {
    let mut section = String::new();
    let mut primitives = Vec::new();

    for raw_line in text.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with('#') {
            return Err(CalibrationCompileError::UnresolvedDirective(
                line.to_string(),
            ));
        }
        if line.starts_with('[') && line.ends_with(']') {
            section.clear();
            section.push_str(line[1..line.len() - 1].trim());
            continue;
        }
        if !section.eq_ignore_ascii_case("Constants") {
            continue;
        }

        let Some((name, value)) = line.split_once('=') else {
            continue;
        };
        let name = name.trim();
        let fields = split_top_level(value.trim());
        let Some(kind) = fields.first().map(|field| field.trim()) else {
            continue;
        };

        match kind {
            "scalar" => primitives.push(parse_scalar(name, &fields)?),
            "array" => primitives.push(parse_array(name, &fields)?),
            _ => {}
        }
    }

    Ok(primitives)
}

fn parse_scalar(
    name: &str,
    fields: &[String],
) -> Result<CalibrationPrimitive, CalibrationCompileError> {
    require_len(name, fields, 6)?;

    Ok(CalibrationPrimitive {
        name: name.to_string(),
        storage_type: StorageType::parse(field(name, fields, 1, "storage type")?)?,
        offset: parse_usize(field(name, fields, 2, "offset")?)?,
        shape: CalibrationShape::Scalar,
        units: unquote(field(name, fields, 3, "units")?).to_string(),
        scale: parse_f64(field(name, fields, 4, "scale")?)?,
        translate: parse_f64(field(name, fields, 5, "translate")?)?,
        minimum: optional_f64(fields.get(6))?,
        maximum: optional_f64(fields.get(7))?,
        decimals: optional_u8(fields.get(8))?,
    })
}

fn parse_array(
    name: &str,
    fields: &[String],
) -> Result<CalibrationPrimitive, CalibrationCompileError> {
    require_len(name, fields, 7)?;

    Ok(CalibrationPrimitive {
        name: name.to_string(),
        storage_type: StorageType::parse(field(name, fields, 1, "storage type")?)?,
        offset: parse_usize(field(name, fields, 2, "offset")?)?,
        shape: CalibrationShape::Array(parse_dimensions(field(
            name,
            fields,
            3,
            "dimensions",
        )?)?),
        units: unquote(field(name, fields, 4, "units")?).to_string(),
        scale: parse_f64(field(name, fields, 5, "scale")?)?,
        translate: parse_f64(field(name, fields, 6, "translate")?)?,
        minimum: optional_f64(fields.get(7))?,
        maximum: optional_f64(fields.get(8))?,
        decimals: optional_u8(fields.get(9))?,
    })
}

fn require_len(
    name: &str,
    fields: &[String],
    minimum: usize,
) -> Result<(), CalibrationCompileError> {
    if fields.len() < minimum {
        return Err(CalibrationCompileError::MalformedAssignment(
            name.to_string(),
        ));
    }
    Ok(())
}

fn field<'a>(
    name: &str,
    fields: &'a [String],
    index: usize,
    field_name: &'static str,
) -> Result<&'a str, CalibrationCompileError> {
    fields
        .get(index)
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .ok_or_else(|| CalibrationCompileError::MissingField {
            name: name.to_string(),
            field: field_name,
        })
}

fn parse_usize(value: &str) -> Result<usize, CalibrationCompileError> {
    value
        .trim()
        .parse::<usize>()
        .map_err(|_| CalibrationCompileError::InvalidOffset(value.to_string()))
}

fn parse_f64(value: &str) -> Result<f64, CalibrationCompileError> {
    value
        .trim()
        .parse::<f64>()
        .map_err(|_| CalibrationCompileError::InvalidNumber(value.to_string()))
}

fn optional_f64(value: Option<&String>) -> Result<Option<f64>, CalibrationCompileError> {
    let Some(value) = value else {
        return Ok(None);
    };
    let value = value.trim();
    if value.is_empty() {
        return Ok(None);
    }
    parse_f64(value).map(Some)
}

fn optional_u8(value: Option<&String>) -> Result<Option<u8>, CalibrationCompileError> {
    let Some(value) = value else {
        return Ok(None);
    };
    let value = value.trim();
    if value.is_empty() {
        return Ok(None);
    }
    value
        .parse::<u8>()
        .map(Some)
        .map_err(|_| CalibrationCompileError::InvalidDecimals(value.to_string()))
}

fn parse_dimensions(value: &str) -> Result<Vec<usize>, CalibrationCompileError> {
    let value = value.trim();
    let Some(inner) = value.strip_prefix('[').and_then(|v| v.strip_suffix(']')) else {
        return Err(CalibrationCompileError::InvalidDimensions(
            value.to_string(),
        ));
    };

    let mut dimensions = Vec::new();
    for part in inner.split('x') {
        let dimension = part
            .trim()
            .parse::<usize>()
            .map_err(|_| CalibrationCompileError::InvalidDimensions(value.to_string()))?;
        if dimension == 0 {
            return Err(CalibrationCompileError::InvalidDimensions(
                value.to_string(),
            ));
        }
        dimensions.push(dimension);
    }

    if dimensions.is_empty() {
        return Err(CalibrationCompileError::InvalidDimensions(
            value.to_string(),
        ));
    }

    Ok(dimensions)
}

fn split_top_level(value: &str) -> Vec<String> {
    let mut fields = Vec::new();
    let mut start = 0;
    let mut quoted = false;
    let mut escaped = false;
    let mut square_depth = 0usize;
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
            '[' => square_depth += 1,
            ']' => square_depth = square_depth.saturating_sub(1),
            '{' => brace_depth += 1,
            '}' => brace_depth = brace_depth.saturating_sub(1),
            '(' => paren_depth += 1,
            ')' => paren_depth = paren_depth.saturating_sub(1),
            ',' if square_depth == 0 && brace_depth == 0 && paren_depth == 0 => {
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

    const RESOLVED_REAL_EXCERPT: &str = r#"
[Constants]
curveSensors8_inputMin = scalar, F32, 53092, "V", 1, 0, 0, 5.5, 3
curveSensors8_inputBins = array, F32, 53100, [8], "V", 1, 0, 0, 5.5, 3
aeCltCorrBins = array, S08, 53316, [6], "C", 2.0, 0, -100, 250, 0
gppwm4_table = array, U08, 8662, [8x8], "duty", 0.5, 0, 0, 100, 1
veTable = array, U16, 16448, [32x32], "%", 0.01, 0, 0, 650, 2
veLoadBins = array, U16, 18496, [32], {bitStringValue(fuelUnits, fuelAlgorithm) }, 1, 0, 0, 650, 0
"#;

    #[test]
    fn compiles_real_scalar_and_array_primitive_shapes() {
        let compiled = compile_calibration_primitives(RESOLVED_REAL_EXCERPT).unwrap();

        let scalar = compiled
            .iter()
            .find(|primitive| primitive.name == "curveSensors8_inputMin")
            .unwrap();
        assert_eq!(scalar.storage_type, StorageType::F32);
        assert_eq!(scalar.offset, 53_092);
        assert_eq!(scalar.shape, CalibrationShape::Scalar);
        assert_eq!(scalar.units, "V");
        assert_eq!(scalar.maximum, Some(5.5));
        assert_eq!(scalar.decimals, Some(3));

        let table = compiled
            .iter()
            .find(|primitive| primitive.name == "veTable")
            .unwrap();
        assert_eq!(table.storage_type, StorageType::U16);
        assert_eq!(table.offset, 16_448);
        assert_eq!(table.shape, CalibrationShape::Array(vec![32, 32]));
        assert_eq!(table.scale, 0.01);
        assert_eq!(table.minimum, Some(0.0));
        assert_eq!(table.maximum, Some(650.0));
    }

    #[test]
    fn preserves_expression_units_with_inner_comma() {
        let compiled = compile_calibration_primitives(RESOLVED_REAL_EXCERPT).unwrap();
        let load_bins = compiled
            .iter()
            .find(|primitive| primitive.name == "veLoadBins")
            .unwrap();

        assert_eq!(
            load_bins.units,
            "{bitStringValue(fuelUnits, fuelAlgorithm) }"
        );
        assert_eq!(load_bins.shape, CalibrationShape::Array(vec![32]));
    }

    #[test]
    fn rejects_unresolved_preprocessor_branch() {
        let text = r#"
[Constants]
#if HUNDREDTH_VE
veTable = array, U16, 16448, [32x32], "%", 0.01, 0, 0, 650, 2
#endif
"#;

        assert_eq!(
            compile_calibration_primitives(text),
            Err(CalibrationCompileError::UnresolvedDirective(
                "#if HUNDREDTH_VE".to_string()
            ))
        );
    }

    #[test]
    fn ignores_bits_until_bitfield_compiler_stage() {
        let text = r#"
[Constants]
enableFan1WithAc = bits, U32, 532, [0:0], "false", "true"
curveSensors8_inputMin = scalar, F32, 53092, "V", 1, 0, 0, 5.5, 3
"#;

        let compiled = compile_calibration_primitives(text).unwrap();
        assert_eq!(compiled.len(), 1);
        assert_eq!(compiled[0].name, "curveSensors8_inputMin");
    }

    #[test]
    fn rejects_zero_dimension() {
        let text = r#"
[Constants]
bad = array, U08, 10, [8x0], "", 1, 0, 0, 100, 0
"#;

        assert_eq!(
            compile_calibration_primitives(text),
            Err(CalibrationCompileError::InvalidDimensions(
                "[8x0]".to_string()
            ))
        );
    }
}
