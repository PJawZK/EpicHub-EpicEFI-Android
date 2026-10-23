//! Strict first-pass TunerStudio OutputChannels compiler.
//! Only fixed-offset numeric scalar channels are authorized here. Unsupported
//! output expressions must be explicitly handled by a later compiler stage.
use std::collections::HashSet;

use crate::runtime_channel_decode::{
    CompiledRuntimeChannelCatalog, RuntimeChannelDescriptor, RuntimeDecodeError, RuntimeEndian,
    RuntimeValueType,
};

#[derive(Clone, Debug, PartialEq)]
pub struct NamedRuntimeChannel {
    pub name: String,
    pub units: String,
    pub id: u32,
}

#[derive(Clone, Debug)]
pub struct RuntimeDefinitionCatalog {
    pub channel_info: Vec<NamedRuntimeChannel>,
    pub decoder: CompiledRuntimeChannelCatalog,
}

#[derive(Clone, Debug, PartialEq)]
pub enum RuntimeDefinitionError {
    MissingOutputChannels,
    MissingBlockSize,
    InvalidBlockSize,
    InvalidAssignment(String),
    DuplicateName(String),
    UnsupportedChannel(String),
    UnsupportedType(String),
    InvalidScalar(String),
    InvalidScaling(String),
    InvalidName(String),
    Decode(RuntimeDecodeError),
}

fn unquote(value: &str) -> Option<&str> {
    let inner = value.trim().strip_prefix('"')?.strip_suffix('"')?;
    if inner.contains('"') {
        return None;
    }
    Some(inner)
}

fn without_comment(line: &str) -> &str {
    let mut quoted = false;
    for (index, byte) in line.bytes().enumerate() {
        if byte == b'"' {
            quoted = !quoted;
        } else if byte == b';' && !quoted {
            return &line[..index];
        }
    }
    line
}

fn fields(text: &str) -> Vec<&str> {
    let mut result = Vec::new();
    let mut quoted = false;
    let mut start = 0;
    for (index, byte) in text.bytes().enumerate() {
        if byte == b'"' {
            quoted = !quoted;
        } else if byte == b',' && !quoted {
            result.push(text[start..index].trim());
            start = index + 1;
        }
    }
    result.push(text[start..].trim());
    result
}

fn stable_name_id(name: &str) -> u32 {
    // Fixed, reproducible FNV-1a identity. Collision handling is fail-closed.
    let mut hash = 2_166_136_261u32;
    for byte in name.as_bytes() {
        hash = (hash ^ u32::from(*byte)).wrapping_mul(16_777_619);
    }
    hash
}

pub fn compile_runtime_output_channels(
    resolved_definition: &str,
) -> Result<RuntimeDefinitionCatalog, RuntimeDefinitionError> {
    let mut section = String::new();
    let mut saw_section = false;
    let mut block_size = None;
    let mut names = HashSet::new();
    let mut ids = HashSet::new();
    let mut descriptors = Vec::new();
    let mut channel_info = Vec::new();
    let mut endian = RuntimeEndian::Little;

    // Endianness for the same resolved INI is part of the profile authority.
    for raw_line in resolved_definition.lines() {
        let line = without_comment(raw_line).trim();
        if line.starts_with('[') && line.ends_with(']') {
            section = line[1..line.len() - 1].trim().to_string();
        } else if section.eq_ignore_ascii_case("Constants")
            && let Some((key, value)) = line.split_once('=')
            && key.trim() == "endianness"
        {
            endian = match value.trim() {
                "little" => RuntimeEndian::Little,
                "big" => RuntimeEndian::Big,
                _ => {
                    return Err(RuntimeDefinitionError::InvalidScalar(
                        "endianness".to_string(),
                    ));
                }
            };
        }
    }

    section.clear();
    for raw_line in resolved_definition.lines() {
        let line = without_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with('#') {
            return Err(RuntimeDefinitionError::InvalidAssignment(line.to_string()));
        }
        if line.starts_with('[') && line.ends_with(']') {
            section = line[1..line.len() - 1].trim().to_string();
            if section.eq_ignore_ascii_case("OutputChannels") {
                saw_section = true;
            }
            continue;
        }
        if !section.eq_ignore_ascii_case("OutputChannels") {
            continue;
        }
        let Some((name, value)) = line.split_once('=') else {
            return Err(RuntimeDefinitionError::InvalidAssignment(line.to_string()));
        };
        let name = name.trim();
        let value = value.trim();
        if name == "ochBlockSize" {
            let size = value
                .parse::<usize>()
                .map_err(|_| RuntimeDefinitionError::InvalidBlockSize)?;
            if block_size.replace(size).is_some() {
                return Err(RuntimeDefinitionError::InvalidBlockSize);
            }
            continue;
        }
        // Command and metadata interpretation is intentionally deferred; no
        // transport read can be issued based on this decoder-only stage.
        if matches!(
            name,
            "ochGetCommand" | "ochBlockShape" | "ochBlockCount" | "ochBlockSizeSelector"
        ) {
            continue;
        }
        if name.is_empty()
            || !name
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
        {
            return Err(RuntimeDefinitionError::InvalidName(name.to_string()));
        }
        if !names.insert(name.to_string()) {
            return Err(RuntimeDefinitionError::DuplicateName(name.to_string()));
        }
        let parts = fields(value);
        if parts.first().copied() != Some("scalar") {
            return Err(RuntimeDefinitionError::UnsupportedChannel(name.to_string()));
        }
        if parts.len() != 6 {
            return Err(RuntimeDefinitionError::InvalidScalar(name.to_string()));
        }
        let value_type = match parts[1] {
            "U08" => RuntimeValueType::U8,
            "S08" => RuntimeValueType::S8,
            "U16" => RuntimeValueType::U16,
            "S16" => RuntimeValueType::S16,
            "U32" => RuntimeValueType::U32,
            "S32" => RuntimeValueType::S32,
            "F32" => RuntimeValueType::F32,
            other => return Err(RuntimeDefinitionError::UnsupportedType(other.to_string())),
        };
        let offset = parts[2]
            .parse::<usize>()
            .map_err(|_| RuntimeDefinitionError::InvalidScalar(name.to_string()))?;
        let units = unquote(parts[3])
            .ok_or_else(|| RuntimeDefinitionError::InvalidScalar(name.to_string()))?;
        let scale = parts[4]
            .parse::<f64>()
            .map_err(|_| RuntimeDefinitionError::InvalidScaling(name.to_string()))?;
        let translate = parts[5]
            .parse::<f64>()
            .map_err(|_| RuntimeDefinitionError::InvalidScaling(name.to_string()))?;
        let id = stable_name_id(name);
        if id == 0 || !ids.insert(id) {
            return Err(RuntimeDefinitionError::InvalidName(name.to_string()));
        }
        channel_info.push(NamedRuntimeChannel {
            name: name.to_string(),
            units: units.to_string(),
            id,
        });
        descriptors.push(RuntimeChannelDescriptor {
            id,
            offset,
            value_type,
            endian,
            scale,
            translate,
        });
    }
    if !saw_section {
        return Err(RuntimeDefinitionError::MissingOutputChannels);
    }
    let size = block_size.ok_or(RuntimeDefinitionError::MissingBlockSize)?;
    let decoder = CompiledRuntimeChannelCatalog::compile(size, descriptors)
        .map_err(RuntimeDefinitionError::Decode)?;
    Ok(RuntimeDefinitionCatalog {
        channel_info,
        decoder,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const DEFINITION: &str = r#"
[Constants]
endianness = little
[OutputChannels]
ochBlockSize = 12
ochGetCommand = "O"
rpm = scalar, U16, 0, "RPM", 1, 0
clt = scalar, S16, 2, "C", 0.1, -10
pressure = scalar, U32, 4, "kPa", 0.01, 0
afr = scalar, F32, 8, "AFR", 1, 0
"#;

    #[test]
    fn generic_definition_compiles_and_decodes_four_channels() {
        let catalog = compile_runtime_output_channels(DEFINITION).unwrap();
        assert_eq!(catalog.decoder.frame_length(), 12);
        assert_eq!(catalog.decoder.channel_count(), 4);
        assert_eq!(catalog.channel_info[0].name, "rpm");
        assert_eq!(catalog.channel_info[0].units, "RPM");
        assert_eq!(catalog.channel_info[0].id, stable_name_id("rpm"));
        let frame = [0x20, 0x03, 0x64, 0, 0x10, 0x27, 0, 0, 0, 0, 0x70, 0x41];
        let samples = catalog.decoder.decode(&frame).unwrap();
        assert_eq!(
            samples
                .iter()
                .map(|sample| sample.value)
                .collect::<Vec<_>>(),
            vec![800.0, 0.0, 100.0, 15.0]
        );
    }

    #[test]
    fn unsupported_channels_fail_closed_instead_of_partial_catalog() {
        let unsupported = DEFINITION.replace(
            "afr = scalar, F32, 8, \"AFR\", 1, 0",
            "afr = bits, U08, 8, [0:2]",
        );
        assert_eq!(
            compile_runtime_output_channels(&unsupported).unwrap_err(),
            RuntimeDefinitionError::UnsupportedChannel("afr".to_string())
        );
    }

    #[test]
    fn validates_block_bounds_and_duplicate_channel_names() {
        let bad = DEFINITION.replace("ochBlockSize = 12", "ochBlockSize = 2");
        assert_eq!(
            compile_runtime_output_channels(&bad).unwrap_err(),
            RuntimeDefinitionError::Decode(RuntimeDecodeError::OffsetOutsideFrame(stable_name_id(
                "clt"
            )))
        );
        let duplicate = format!("{DEFINITION}\nrpm = scalar, U16, 0, \"RPM\", 1, 0\n");
        assert_eq!(
            compile_runtime_output_channels(&duplicate).unwrap_err(),
            RuntimeDefinitionError::DuplicateName("rpm".to_string())
        );
    }

    #[test]
    fn supports_big_endian_and_resolved_definition_text() {
        let big = DEFINITION.replace("endianness = little", "endianness = big");
        let compiled = compile_runtime_output_channels(&big).unwrap();
        let frame = [0x03, 0x20, 0, 100, 0, 0, 0x27, 0x10, 0x41, 0x70, 0, 0];
        let sample = compiled.decoder.decode(&frame).unwrap();
        assert_eq!(sample[0].value, 800.0);
        assert_eq!(sample[2].value, 100.0);
        assert_eq!(sample[3].value, 15.0);
    }
}
