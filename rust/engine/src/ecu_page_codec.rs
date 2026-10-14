use crate::ecu_calibration::{
    CalibrationCompileError, CalibrationPrimitive, CalibrationShape, StorageType,
    compile_calibration_primitives,
};
use crate::ecu_definition::Endianness;

#[derive(Clone, Debug, PartialEq)]
pub struct PagedCalibrationPrimitive {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub primitive: CalibrationPrimitive,
}

#[derive(Clone, Debug, PartialEq)]
pub enum PageCodecError {
    CalibrationCompile(CalibrationCompileError),
    MissingPageForPrimitive(String),
    InvalidPageNumber(String),
    PageUnavailable { page: usize, available: usize },
    OutOfBounds {
        name: String,
        page: usize,
        offset: usize,
        byte_count: usize,
        page_size: usize,
    },
    ElementCountMismatch { expected: usize, actual: usize },
    InvalidScale(String),
    NonFiniteValue { name: String, value: f64 },
    OutsideDeclaredRange { name: String, value: f64 },
    UnrepresentableValue { name: String, value: f64 },
}

impl From<CalibrationCompileError> for PageCodecError {
    fn from(value: CalibrationCompileError) -> Self {
        Self::CalibrationCompile(value)
    }
}

pub fn compile_paged_calibration_primitives(
    text: &str,
) -> Result<Vec<PagedCalibrationPrimitive>, PageCodecError> {
    let mut section = String::new();
    let mut current_page = None;
    let mut primitives = Vec::new();

    for raw_line in text.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with('#') {
            return Err(PageCodecError::CalibrationCompile(
                CalibrationCompileError::UnresolvedDirective(line.to_string()),
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
        let value = value.trim();
        if name.eq_ignore_ascii_case("page") {
            let page = value
                .parse::<usize>()
                .map_err(|_| PageCodecError::InvalidPageNumber(value.to_string()))?;
            if page == 0 {
                return Err(PageCodecError::InvalidPageNumber(value.to_string()));
            }
            current_page = Some(page);
            continue;
        }

        let kind = value.split(',').next().map(str::trim).unwrap_or_default();
        if kind != "scalar" && kind != "array" {
            continue;
        }
        let page = current_page
            .ok_or_else(|| PageCodecError::MissingPageForPrimitive(name.to_string()))?;
        let mini_definition = format!("[Constants]\n{line}\n");
        let mut compiled = compile_calibration_primitives(&mini_definition)?;
        let primitive = compiled
            .pop()
            .ok_or_else(|| PageCodecError::MissingPageForPrimitive(name.to_string()))?;
        primitives.push(PagedCalibrationPrimitive { page, primitive });
    }

    Ok(primitives)
}

pub fn decode_primitive(
    pages: &[&[u8]],
    paged: &PagedCalibrationPrimitive,
    endianness: Endianness,
) -> Result<Vec<f64>, PageCodecError> {
    let primitive = &paged.primitive;
    validate_scale(primitive)?;
    let count = element_count(&primitive.shape)?;
    let width = storage_width(primitive.storage_type);
    let byte_count = count
        .checked_mul(width)
        .ok_or_else(|| out_of_bounds(paged, 0, pages))?;
    let page = page_slice(pages, paged.page)?;
    let end = primitive
        .offset
        .checked_add(byte_count)
        .ok_or_else(|| out_of_bounds_for_page(paged, byte_count, page.len()))?;
    let bytes = page
        .get(primitive.offset..end)
        .ok_or_else(|| out_of_bounds_for_page(paged, byte_count, page.len()))?;

    let mut values = Vec::with_capacity(count);
    for chunk in bytes.chunks_exact(width) {
        let raw = decode_raw(chunk, primitive.storage_type, endianness);
        values.push(raw * primitive.scale + primitive.translate);
    }
    Ok(values)
}

pub fn encode_primitive(
    pages: &mut [&mut [u8]],
    paged: &PagedCalibrationPrimitive,
    endianness: Endianness,
    values: &[f64],
) -> Result<(), PageCodecError> {
    let primitive = &paged.primitive;
    validate_scale(primitive)?;
    let count = element_count(&primitive.shape)?;
    if values.len() != count {
        return Err(PageCodecError::ElementCountMismatch {
            expected: count,
            actual: values.len(),
        });
    }

    let width = storage_width(primitive.storage_type);
    let byte_count = count
        .checked_mul(width)
        .ok_or_else(|| out_of_bounds_mut(paged, 0, pages.len()))?;
    let available = pages.len();
    let page = pages
        .get_mut(paged.page - 1)
        .ok_or(PageCodecError::PageUnavailable {
            page: paged.page,
            available,
        })?;
    let page_size = page.len();
    let end = primitive
        .offset
        .checked_add(byte_count)
        .ok_or_else(|| out_of_bounds_for_page(paged, byte_count, page_size))?;
    let bytes = page
        .get_mut(primitive.offset..end)
        .ok_or_else(|| out_of_bounds_for_page(paged, byte_count, page_size))?;

    for (value, chunk) in values.iter().copied().zip(bytes.chunks_exact_mut(width)) {
        validate_engineering_value(primitive, value)?;
        let raw = (value - primitive.translate) / primitive.scale;
        encode_raw(
            chunk,
            primitive.storage_type,
            endianness,
            raw,
            &primitive.name,
            value,
        )?;
    }
    Ok(())
}

fn validate_scale(primitive: &CalibrationPrimitive) -> Result<(), PageCodecError> {
    if !primitive.scale.is_finite() || primitive.scale == 0.0 || !primitive.translate.is_finite() {
        return Err(PageCodecError::InvalidScale(primitive.name.clone()));
    }
    Ok(())
}

fn validate_engineering_value(
    primitive: &CalibrationPrimitive,
    value: f64,
) -> Result<(), PageCodecError> {
    if !value.is_finite() {
        return Err(PageCodecError::NonFiniteValue {
            name: primitive.name.clone(),
            value,
        });
    }
    if primitive.minimum.is_some_and(|minimum| value < minimum)
        || primitive.maximum.is_some_and(|maximum| value > maximum)
    {
        return Err(PageCodecError::OutsideDeclaredRange {
            name: primitive.name.clone(),
            value,
        });
    }
    Ok(())
}

fn element_count(shape: &CalibrationShape) -> Result<usize, PageCodecError> {
    match shape {
        CalibrationShape::Scalar => Ok(1),
        CalibrationShape::Array(dimensions) => dimensions.iter().try_fold(1usize, |total, size| {
            total.checked_mul(*size).ok_or(PageCodecError::ElementCountMismatch {
                expected: usize::MAX,
                actual: 0,
            })
        }),
    }
}

fn storage_width(storage_type: StorageType) -> usize {
    match storage_type {
        StorageType::U08 | StorageType::S08 => 1,
        StorageType::U16 | StorageType::S16 => 2,
        StorageType::U32 | StorageType::S32 | StorageType::F32 => 4,
    }
}

fn page_slice<'a>(pages: &'a [&[u8]], page_number: usize) -> Result<&'a [u8], PageCodecError> {
    pages
        .get(page_number - 1)
        .copied()
        .ok_or(PageCodecError::PageUnavailable {
            page: page_number,
            available: pages.len(),
        })
}

fn decode_raw(bytes: &[u8], storage_type: StorageType, endianness: Endianness) -> f64 {
    match storage_type {
        StorageType::U08 => f64::from(bytes[0]),
        StorageType::S08 => f64::from(i8::from_ne_bytes([bytes[0]])),
        StorageType::U16 => f64::from(match endianness {
            Endianness::Little => u16::from_le_bytes([bytes[0], bytes[1]]),
            Endianness::Big => u16::from_be_bytes([bytes[0], bytes[1]]),
        }),
        StorageType::S16 => f64::from(match endianness {
            Endianness::Little => i16::from_le_bytes([bytes[0], bytes[1]]),
            Endianness::Big => i16::from_be_bytes([bytes[0], bytes[1]]),
        }),
        StorageType::U32 => f64::from(match endianness {
            Endianness::Little => u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
            Endianness::Big => u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
        }),
        StorageType::S32 => f64::from(match endianness {
            Endianness::Little => i32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
            Endianness::Big => i32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
        }),
        StorageType::F32 => f64::from(match endianness {
            Endianness::Little => f32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
            Endianness::Big => f32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
        }),
    }
}

fn encode_raw(
    output: &mut [u8],
    storage_type: StorageType,
    endianness: Endianness,
    raw: f64,
    name: &str,
    engineering_value: f64,
) -> Result<(), PageCodecError> {
    match storage_type {
        StorageType::F32 => {
            if !raw.is_finite() || raw < f64::from(f32::MIN) || raw > f64::from(f32::MAX) {
                return unrepresentable(name, engineering_value);
            }
            let encoded = match endianness {
                Endianness::Little => (raw as f32).to_le_bytes(),
                Endianness::Big => (raw as f32).to_be_bytes(),
            };
            output.copy_from_slice(&encoded);
        }
        _ => {
            let rounded = raw.round();
            let tolerance = 1e-9_f64.max(raw.abs() * 1e-12);
            if !raw.is_finite() || (raw - rounded).abs() > tolerance {
                return unrepresentable(name, engineering_value);
            }
            match storage_type {
                StorageType::U08 if (0.0..=f64::from(u8::MAX)).contains(&rounded) => {
                    output[0] = rounded as u8;
                }
                StorageType::S08
                    if (f64::from(i8::MIN)..=f64::from(i8::MAX)).contains(&rounded) =>
                {
                    output.copy_from_slice(&(rounded as i8).to_ne_bytes());
                }
                StorageType::U16 if (0.0..=f64::from(u16::MAX)).contains(&rounded) => {
                    let encoded = match endianness {
                        Endianness::Little => (rounded as u16).to_le_bytes(),
                        Endianness::Big => (rounded as u16).to_be_bytes(),
                    };
                    output.copy_from_slice(&encoded);
                }
                StorageType::S16
                    if (f64::from(i16::MIN)..=f64::from(i16::MAX)).contains(&rounded) =>
                {
                    let encoded = match endianness {
                        Endianness::Little => (rounded as i16).to_le_bytes(),
                        Endianness::Big => (rounded as i16).to_be_bytes(),
                    };
                    output.copy_from_slice(&encoded);
                }
                StorageType::U32 if (0.0..=f64::from(u32::MAX)).contains(&rounded) => {
                    let encoded = match endianness {
                        Endianness::Little => (rounded as u32).to_le_bytes(),
                        Endianness::Big => (rounded as u32).to_be_bytes(),
                    };
                    output.copy_from_slice(&encoded);
                }
                StorageType::S32
                    if (f64::from(i32::MIN)..=f64::from(i32::MAX)).contains(&rounded) =>
                {
                    let encoded = match endianness {
                        Endianness::Little => (rounded as i32).to_le_bytes(),
                        Endianness::Big => (rounded as i32).to_be_bytes(),
                    };
                    output.copy_from_slice(&encoded);
                }
                _ => return unrepresentable(name, engineering_value),
            }
        }
    }
    Ok(())
}

fn unrepresentable<T>(name: &str, value: f64) -> Result<T, PageCodecError> {
    Err(PageCodecError::UnrepresentableValue {
        name: name.to_string(),
        value,
    })
}

fn out_of_bounds(
    paged: &PagedCalibrationPrimitive,
    byte_count: usize,
    pages: &[&[u8]],
) -> PageCodecError {
    let page_size = paged
        .page
        .checked_sub(1)
        .and_then(|index| pages.get(index))
        .map(|page| page.len())
        .unwrap_or(0);
    out_of_bounds_for_page(paged, byte_count, page_size)
}

fn out_of_bounds_mut(
    paged: &PagedCalibrationPrimitive,
    byte_count: usize,
    _available: usize,
) -> PageCodecError {
    out_of_bounds_for_page(paged, byte_count, 0)
}

fn out_of_bounds_for_page(
    paged: &PagedCalibrationPrimitive,
    byte_count: usize,
    page_size: usize,
) -> PageCodecError {
    PageCodecError::OutOfBounds {
        name: paged.primitive.name.clone(),
        page: paged.page,
        offset: paged.primitive.offset,
        byte_count,
        page_size,
    }
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

    const REAL_PAGE_EXCERPT: &str = r#"
[Constants]
page = 2
luaScript = string, ASCII, 0, 5000
page = 1
veTable = array, U16, 16448, [2x2], "%", 0.01, 0, 0, 650, 2
ignition = scalar, S16, 20, "deg", 0.01, 0, -90, 90, 2
voltage = scalar, F32, 24, "V", 1, 0, 0, 20, 3
"#;

    fn primitive(name: &str) -> PagedCalibrationPrimitive {
        compile_paged_calibration_primitives(REAL_PAGE_EXCERPT)
            .unwrap()
            .into_iter()
            .find(|entry| entry.primitive.name == name)
            .unwrap()
    }

    #[test]
    fn compiles_real_page_switch_semantics() {
        let compiled = compile_paged_calibration_primitives(REAL_PAGE_EXCERPT).unwrap();
        assert_eq!(compiled.len(), 3);
        assert!(compiled.iter().all(|entry| entry.page == 1));
    }

    #[test]
    fn rejects_calibration_before_page_assignment() {
        let text = "[Constants]\nvalue = scalar, U08, 0, \"\", 1, 0\n";
        assert_eq!(
            compile_paged_calibration_primitives(text),
            Err(PageCodecError::MissingPageForPrimitive("value".to_string()))
        );
    }

    #[test]
    fn little_endian_decode_and_encode_round_trip_scaled_values() {
        let ve = primitive("veTable");
        let mut page = vec![0_u8; 16_456];
        page[16_448..16_450].copy_from_slice(&7_825_u16.to_le_bytes());
        page[16_450..16_452].copy_from_slice(&10_000_u16.to_le_bytes());
        page[16_452..16_454].copy_from_slice(&12_345_u16.to_le_bytes());
        page[16_454..16_456].copy_from_slice(&65_000_u16.to_le_bytes());

        let decoded = decode_primitive(&[&page], &ve, Endianness::Little).unwrap();
        assert_eq!(decoded, vec![78.25, 100.0, 123.45, 650.0]);

        let mut encoded = vec![0_u8; 16_456];
        encode_primitive(
            &mut [&mut encoded],
            &ve,
            Endianness::Little,
            &decoded,
        )
        .unwrap();
        assert_eq!(&encoded[16_448..16_456], &page[16_448..16_456]);
    }

    #[test]
    fn signed_and_f32_storage_are_supported() {
        let ignition = primitive("ignition");
        let voltage = primitive("voltage");
        let mut page = vec![0_u8; 28];
        page[20..22].copy_from_slice(&(-1_250_i16).to_le_bytes());
        page[24..28].copy_from_slice(&13.75_f32.to_le_bytes());

        assert_eq!(
            decode_primitive(&[&page], &ignition, Endianness::Little).unwrap(),
            vec![-12.5]
        );
        assert_eq!(
            decode_primitive(&[&page], &voltage, Endianness::Little).unwrap(),
            vec![13.75]
        );
    }

    #[test]
    fn big_endian_is_definition_driven() {
        let ignition = primitive("ignition");
        let mut page = vec![0_u8; 22];
        page[20..22].copy_from_slice(&2_500_i16.to_be_bytes());
        assert_eq!(
            decode_primitive(&[&page], &ignition, Endianness::Big).unwrap(),
            vec![25.0]
        );
    }

    #[test]
    fn wrong_page_and_bounds_fail_closed() {
        let ve = primitive("veTable");
        assert_eq!(
            decode_primitive(&[], &ve, Endianness::Little),
            Err(PageCodecError::PageUnavailable {
                page: 1,
                available: 0
            })
        );
        let short = vec![0_u8; 100];
        assert_eq!(
            decode_primitive(&[&short], &ve, Endianness::Little),
            Err(PageCodecError::OutOfBounds {
                name: "veTable".to_string(),
                page: 1,
                offset: 16_448,
                byte_count: 8,
                page_size: 100,
            })
        );
    }

    #[test]
    fn encode_rejects_off_quantum_and_declared_range_values() {
        let ve = primitive("veTable");
        let mut page = vec![0_u8; 16_456];
        assert_eq!(
            encode_primitive(
                &mut [&mut page],
                &ve,
                Endianness::Little,
                &[78.255, 100.0, 123.45, 650.0],
            ),
            Err(PageCodecError::UnrepresentableValue {
                name: "veTable".to_string(),
                value: 78.255,
            })
        );
        assert_eq!(
            encode_primitive(
                &mut [&mut page],
                &ve,
                Endianness::Little,
                &[78.25, 100.0, 123.45, 651.0],
            ),
            Err(PageCodecError::OutsideDeclaredRange {
                name: "veTable".to_string(),
                value: 651.0,
            })
        );
    }
}
