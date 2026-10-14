use crate::ecu_profile::ProfileIdentity;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Endianness {
    Little,
    Big,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CompiledEcuDefinition {
    pub profile_name: String,
    pub signature: String,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<String>,
    pub query_command: String,
    pub version_info_command: Option<String>,
    pub message_envelope_format: String,
    pub endianness: Endianness,
    pub page_sizes: Vec<usize>,
}

impl CompiledEcuDefinition {
    pub fn identity(&self) -> ProfileIdentity<'_> {
        ProfileIdentity {
            profile_name: &self.profile_name,
            signature: &self.signature,
            profile_generation: self.profile_generation,
            definition_fingerprint: self.definition_fingerprint.as_deref(),
        }
    }

    pub fn exact_signature_matches(&self, live_signature: &str) -> bool {
        self.signature == live_signature
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum DefinitionCompileError {
    UnresolvedDirective(String),
    MissingSignature,
    MissingQueryCommand,
    MissingEnvelopeFormat,
    MissingEndianness,
    UnsupportedEndianness(String),
    MissingPageCount,
    InvalidPageCount(String),
    MissingPageSizes,
    InvalidPageSize(String),
    PageCountMismatch { declared: usize, sizes: usize },
}

pub struct DefinitionCompileRequest<'a> {
    pub profile_name: &'a str,
    pub profile_generation: u64,
    pub definition_fingerprint: Option<&'a str>,
    pub text: &'a str,
}

pub fn compile_definition(
    request: DefinitionCompileRequest<'_>,
) -> Result<CompiledEcuDefinition, DefinitionCompileError> {
    let mut section = String::new();
    let mut signature = None;
    let mut query_command = None;
    let mut version_info = None;
    let mut envelope = None;
    let mut endianness = None;
    let mut page_count = None;
    let mut page_sizes = None;

    for raw_line in request.text.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with('#') {
            return Err(DefinitionCompileError::UnresolvedDirective(
                line.to_string(),
            ));
        }
        if line.starts_with('[') && line.ends_with(']') {
            section.clear();
            section.push_str(line[1..line.len() - 1].trim());
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let key = key.trim();
        let value = value.trim();

        if section.eq_ignore_ascii_case("TunerStudio") {
            match key {
                "signature" => signature = Some(unquote(value).to_string()),
                "queryCommand" => query_command = Some(unquote(value).to_string()),
                "versionInfo" => version_info = Some(unquote(value).to_string()),
                _ => {}
            }
        }

        if section.eq_ignore_ascii_case("Constants") {
            match key {
                "messageEnvelopeFormat" => envelope = Some(unquote(value).to_string()),
                "endianness" => endianness = Some(value.to_string()),
                "nPages" => page_count = Some(value.to_string()),
                "pageSize" => page_sizes = Some(value.to_string()),
                _ => {}
            }
        }
    }

    let signature = signature.ok_or(DefinitionCompileError::MissingSignature)?;
    let query_command = query_command.ok_or(DefinitionCompileError::MissingQueryCommand)?;
    let message_envelope_format = envelope.ok_or(DefinitionCompileError::MissingEnvelopeFormat)?;
    let endianness = match endianness
        .ok_or(DefinitionCompileError::MissingEndianness)?
        .trim()
    {
        "little" => Endianness::Little,
        "big" => Endianness::Big,
        other => {
            return Err(DefinitionCompileError::UnsupportedEndianness(
                other.to_string(),
            ));
        }
    };
    let page_count_text = page_count.ok_or(DefinitionCompileError::MissingPageCount)?;
    let declared_pages = page_count_text
        .trim()
        .parse::<usize>()
        .map_err(|_| DefinitionCompileError::InvalidPageCount(page_count_text.clone()))?;
    let page_sizes_text = page_sizes.ok_or(DefinitionCompileError::MissingPageSizes)?;
    let page_sizes = parse_page_sizes(&page_sizes_text)?;
    if declared_pages != page_sizes.len() {
        return Err(DefinitionCompileError::PageCountMismatch {
            declared: declared_pages,
            sizes: page_sizes.len(),
        });
    }

    Ok(CompiledEcuDefinition {
        profile_name: request.profile_name.to_string(),
        signature,
        profile_generation: request.profile_generation,
        definition_fingerprint: request.definition_fingerprint.map(str::to_string),
        query_command,
        version_info_command: version_info,
        message_envelope_format,
        endianness,
        page_sizes,
    })
}

fn parse_page_sizes(value: &str) -> Result<Vec<usize>, DefinitionCompileError> {
    let mut result = Vec::new();
    for item in value.split(',') {
        let item = item.trim();
        let size = item
            .parse::<usize>()
            .map_err(|_| DefinitionCompileError::InvalidPageSize(item.to_string()))?;
        if size == 0 {
            return Err(DefinitionCompileError::InvalidPageSize(item.to_string()));
        }
        result.push(size);
    }
    if result.is_empty() {
        return Err(DefinitionCompileError::MissingPageSizes);
    }
    Ok(result)
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

    const MEGA144H7_DEFINITION_EXCERPT: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V" ; firmware version
signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
; nPages = 3
nPages = 2
pageIdentifier = "\x00\x00", "\x00\x01"
; pageSize = 55764, 4, 5000
pageSize = 55764, 5000
pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
"#;

    const UAEFI_DEFINITION_EXCERPT: &str = r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI master.2025.05.23.uaefi.3590163173"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
pageSize = 23824
"#;

    #[test]
    fn compiles_real_mega144h7_identity_and_two_page_geometry() {
        let compiled = compile_definition(DefinitionCompileRequest {
            profile_name: "353",
            profile_generation: 2_273_317_132,
            definition_fingerprint: Some("mega-fingerprint"),
            text: MEGA144H7_DEFINITION_EXCERPT,
        })
        .unwrap();

        assert_eq!(
            compiled.signature,
            "rusEFI master.2026.08.26.MEGA144H7.2273317132"
        );
        assert_eq!(compiled.query_command, "S");
        assert_eq!(compiled.endianness, Endianness::Little);
        assert_eq!(compiled.page_sizes, vec![55_764, 5_000]);
    }

    #[test]
    fn compiles_real_uaefi_identity_and_single_page_geometry() {
        let compiled = compile_definition(DefinitionCompileRequest {
            profile_name: "uaefi-fixture",
            profile_generation: 3_590_163_173,
            definition_fingerprint: None,
            text: UAEFI_DEFINITION_EXCERPT,
        })
        .unwrap();

        assert_eq!(
            compiled.signature,
            "rusEFI master.2025.05.23.uaefi.3590163173"
        );
        assert_eq!(compiled.page_sizes, vec![23_824]);
        assert!(compiled.exact_signature_matches(&compiled.signature));
        assert!(!compiled.exact_signature_matches("rusEFI master.2025.05.23.uaefi"));
    }

    #[test]
    fn rejects_page_count_geometry_mismatch() {
        let bad = MEGA144H7_DEFINITION_EXCERPT.replace("nPages = 2", "nPages = 1");
        assert_eq!(
            compile_definition(DefinitionCompileRequest {
                profile_name: "bad",
                profile_generation: 1,
                definition_fingerprint: None,
                text: &bad,
            }),
            Err(DefinitionCompileError::PageCountMismatch {
                declared: 1,
                sizes: 2,
            })
        );
    }

    #[test]
    fn rejects_missing_signature() {
        let bad = UAEFI_DEFINITION_EXCERPT.replace(
            "signature = \"rusEFI master.2025.05.23.uaefi.3590163173\"",
            "",
        );
        assert_eq!(
            compile_definition(DefinitionCompileRequest {
                profile_name: "bad",
                profile_generation: 1,
                definition_fingerprint: None,
                text: &bad,
            }),
            Err(DefinitionCompileError::MissingSignature)
        );
    }

    #[test]
    fn rejects_unresolved_preprocessor_directives() {
        let bad = format!("{MEGA144H7_DEFINITION_EXCERPT}\n#if HUNDREDTH_VE\n");
        assert_eq!(
            compile_definition(DefinitionCompileRequest {
                profile_name: "bad",
                profile_generation: 1,
                definition_fingerprint: None,
                text: &bad,
            }),
            Err(DefinitionCompileError::UnresolvedDirective(
                "#if HUNDREDTH_VE".to_string()
            ))
        );
    }

    #[test]
    fn trailing_semicolon_comment_does_not_damage_quoted_command() {
        assert_eq!(
            strip_comment("queryCommand = \"S;still-command\" ; comment").trim(),
            "queryCommand = \"S;still-command\""
        );
    }
}
