#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageProtocol {
    pub identifier: Vec<u8>,
    pub read_command: String,
    pub burn_command: Option<String>,
    pub value_write_command: Option<String>,
    pub chunk_write_command: Option<String>,
    pub crc32_check_command: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CompiledProtocol {
    pub pages: Vec<PageProtocol>,
    pub retrieve_config_error: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum ProtocolCompileError {
    UnresolvedDirective(String),
    MissingPageCount,
    InvalidPageCount(String),
    MissingPageIdentifiers,
    MissingReadCommands,
    CardinalityMismatch {
        field: &'static str,
        declared: usize,
        entries: usize,
    },
    EmptyRequiredCommand {
        field: &'static str,
        page: usize,
    },
    InvalidQuotedList(String),
    InvalidPageIdentifier(String),
}

pub fn compile_protocol(text: &str) -> Result<CompiledProtocol, ProtocolCompileError> {
    let mut section = String::new();
    let mut page_count = None;
    let mut identifiers = None;
    let mut reads = None;
    let mut burns = None;
    let mut value_writes = None;
    let mut chunk_writes = None;
    let mut crc32_checks = None;
    let mut retrieve_config_error = None;

    for raw_line in text.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with('#') {
            return Err(ProtocolCompileError::UnresolvedDirective(line.to_string()));
        }
        if line.starts_with('[') && line.ends_with(']') {
            section.clear();
            section.push_str(line[1..line.len() - 1].trim());
            continue;
        }
        if !section.eq_ignore_ascii_case("Constants") {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let key = key.trim();
        let value = value.trim();
        match key {
            "nPages" => page_count = Some(value.to_string()),
            "pageIdentifier" => identifiers = Some(parse_quoted_list(value)?),
            "pageReadCommand" => reads = Some(parse_quoted_list(value)?),
            "burnCommand" => burns = Some(parse_quoted_list(value)?),
            "pageValueWrite" => value_writes = Some(parse_quoted_list(value)?),
            "pageChunkWrite" => chunk_writes = Some(parse_quoted_list(value)?),
            "crc32CheckCommand" => crc32_checks = Some(parse_quoted_list(value)?),
            "retrieveConfigError" => {
                let values = parse_quoted_list(value)?;
                if values.len() != 1 {
                    return Err(ProtocolCompileError::CardinalityMismatch {
                        field: "retrieveConfigError",
                        declared: 1,
                        entries: values.len(),
                    });
                }
                retrieve_config_error = values.into_iter().next().filter(|value| !value.is_empty());
            }
            _ => {}
        }
    }

    let page_count_text = page_count.ok_or(ProtocolCompileError::MissingPageCount)?;
    let declared = page_count_text
        .trim()
        .parse::<usize>()
        .map_err(|_| ProtocolCompileError::InvalidPageCount(page_count_text.clone()))?;
    if declared == 0 {
        return Err(ProtocolCompileError::InvalidPageCount(page_count_text));
    }

    let identifiers = identifiers.ok_or(ProtocolCompileError::MissingPageIdentifiers)?;
    let reads = reads.ok_or(ProtocolCompileError::MissingReadCommands)?;
    validate_cardinality("pageIdentifier", declared, identifiers.len())?;
    validate_cardinality("pageReadCommand", declared, reads.len())?;
    validate_optional_cardinality("burnCommand", declared, burns.as_ref())?;
    validate_optional_cardinality("pageValueWrite", declared, value_writes.as_ref())?;
    validate_optional_cardinality("pageChunkWrite", declared, chunk_writes.as_ref())?;
    validate_optional_cardinality("crc32CheckCommand", declared, crc32_checks.as_ref())?;

    let identifiers = identifiers
        .into_iter()
        .map(|value| parse_page_identifier(&value))
        .collect::<Result<Vec<_>, _>>()?;

    let mut pages = Vec::with_capacity(declared);
    for index in 0..declared {
        if reads[index].is_empty() {
            return Err(ProtocolCompileError::EmptyRequiredCommand {
                field: "pageReadCommand",
                page: index,
            });
        }
        pages.push(PageProtocol {
            identifier: identifiers[index].clone(),
            read_command: reads[index].clone(),
            burn_command: optional_entry(burns.as_ref(), index),
            value_write_command: optional_entry(value_writes.as_ref(), index),
            chunk_write_command: optional_entry(chunk_writes.as_ref(), index),
            crc32_check_command: optional_entry(crc32_checks.as_ref(), index),
        });
    }

    Ok(CompiledProtocol {
        pages,
        retrieve_config_error,
    })
}

fn validate_cardinality(
    field: &'static str,
    declared: usize,
    entries: usize,
) -> Result<(), ProtocolCompileError> {
    if declared != entries {
        return Err(ProtocolCompileError::CardinalityMismatch {
            field,
            declared,
            entries,
        });
    }
    Ok(())
}

fn validate_optional_cardinality(
    field: &'static str,
    declared: usize,
    values: Option<&Vec<String>>,
) -> Result<(), ProtocolCompileError> {
    if let Some(values) = values {
        validate_cardinality(field, declared, values.len())?;
    }
    Ok(())
}

fn optional_entry(values: Option<&Vec<String>>, index: usize) -> Option<String> {
    values
        .and_then(|values| values.get(index))
        .filter(|value| !value.is_empty())
        .cloned()
}

fn parse_quoted_list(value: &str) -> Result<Vec<String>, ProtocolCompileError> {
    let mut result = Vec::new();
    let bytes = value.as_bytes();
    let mut index = 0usize;

    while index < bytes.len() {
        while index < bytes.len() && bytes[index].is_ascii_whitespace() {
            index += 1;
        }
        if index >= bytes.len() || bytes[index] != b'"' {
            return Err(ProtocolCompileError::InvalidQuotedList(value.to_string()));
        }
        index += 1;
        let mut item = String::new();
        let mut closed = false;
        while index < bytes.len() {
            let ch = bytes[index] as char;
            if ch == '"' {
                index += 1;
                closed = true;
                break;
            }
            if ch == '\\' {
                if index + 1 >= bytes.len() {
                    return Err(ProtocolCompileError::InvalidQuotedList(value.to_string()));
                }
                item.push('\\');
                index += 1;
                item.push(bytes[index] as char);
                index += 1;
                continue;
            }
            item.push(ch);
            index += 1;
        }
        if !closed {
            return Err(ProtocolCompileError::InvalidQuotedList(value.to_string()));
        }
        result.push(item);
        while index < bytes.len() && bytes[index].is_ascii_whitespace() {
            index += 1;
        }
        if index == bytes.len() {
            break;
        }
        if bytes[index] != b',' {
            return Err(ProtocolCompileError::InvalidQuotedList(value.to_string()));
        }
        index += 1;
    }

    if result.is_empty() {
        return Err(ProtocolCompileError::InvalidQuotedList(value.to_string()));
    }
    Ok(result)
}

fn parse_page_identifier(value: &str) -> Result<Vec<u8>, ProtocolCompileError> {
    let bytes = value.as_bytes();
    let mut result = Vec::new();
    let mut index = 0usize;
    while index < bytes.len() {
        if bytes[index] != b'\\' || index + 3 >= bytes.len() || bytes[index + 1] != b'x' {
            return Err(ProtocolCompileError::InvalidPageIdentifier(
                value.to_string(),
            ));
        }
        let hex = &value[index + 2..index + 4];
        let byte = u8::from_str_radix(hex, 16)
            .map_err(|_| ProtocolCompileError::InvalidPageIdentifier(value.to_string()))?;
        result.push(byte);
        index += 4;
    }
    if result.is_empty() {
        return Err(ProtocolCompileError::InvalidPageIdentifier(
            value.to_string(),
        ));
    }
    Ok(result)
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

    const MEGA_PROTOCOL: &str = r#"
[Constants]
nPages = 2
pageIdentifier = "\x00\x00", "\x00\x01"
pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
burnCommand = "B%2i", "B%2i"
pageValueWrite = "C%2i%2o%2c%v", "C%2i%2o%2c%v"
pageChunkWrite = "C%2i%2o%2c%v", "C%2i%2o%2c%v"
crc32CheckCommand = "k%2i%2o%2c", "k%2i%2o%2c"
retrieveConfigError = "e"
"#;

    const UAEFI_2025_PROTOCOL: &str = r#"
[Constants]
nPages = 1
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
burnCommand = "B"
pageValueWrite = "W%2o%v"
pageChunkWrite = "C%2o%2c%v"
crc32CheckCommand = "k%2o%2c"
retrieveConfigError = "e"
"#;

    #[test]
    fn compiles_real_mega_two_page_protocol() {
        let compiled = compile_protocol(MEGA_PROTOCOL).unwrap();
        assert_eq!(compiled.pages.len(), 2);
        assert_eq!(compiled.pages[0].identifier, vec![0x00, 0x00]);
        assert_eq!(compiled.pages[1].identifier, vec![0x00, 0x01]);
        assert_eq!(compiled.pages[0].read_command, "R%2i%2o%2c");
        assert_eq!(compiled.pages[1].burn_command.as_deref(), Some("B%2i"));
        assert_eq!(
            compiled.pages[0].value_write_command.as_deref(),
            Some("C%2i%2o%2c%v")
        );
        assert_eq!(compiled.retrieve_config_error.as_deref(), Some("e"));
    }

    #[test]
    fn compiles_real_uaefi_single_page_non_indexed_protocol() {
        let compiled = compile_protocol(UAEFI_2025_PROTOCOL).unwrap();
        assert_eq!(compiled.pages.len(), 1);
        let page = &compiled.pages[0];
        assert_eq!(page.read_command, "R%2o%2c");
        assert_eq!(page.burn_command.as_deref(), Some("B"));
        assert_eq!(page.value_write_command.as_deref(), Some("W%2o%v"));
        assert_eq!(page.crc32_check_command.as_deref(), Some("k%2o%2c"));
    }

    #[test]
    fn absent_optional_capability_becomes_none_per_page() {
        let text = r#"
[Constants]
nPages = 1
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
"#;
        let compiled = compile_protocol(text).unwrap();
        let page = &compiled.pages[0];
        assert_eq!(page.burn_command, None);
        assert_eq!(page.value_write_command, None);
        assert_eq!(page.chunk_write_command, None);
        assert_eq!(page.crc32_check_command, None);
    }

    #[test]
    fn empty_optional_entry_is_explicitly_absent_for_that_page() {
        let text = r#"
[Constants]
nPages = 2
pageIdentifier = "\x00\x00", "\x00\x01"
pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
burnCommand = "B%2i", ""
"#;
        let compiled = compile_protocol(text).unwrap();
        assert_eq!(compiled.pages[0].burn_command.as_deref(), Some("B%2i"));
        assert_eq!(compiled.pages[1].burn_command, None);
    }

    #[test]
    fn rejects_command_cardinality_mismatch_instead_of_replicating() {
        let bad = MEGA_PROTOCOL.replace(
            "pageReadCommand = \"R%2i%2o%2c\", \"R%2i%2o%2c\"",
            "pageReadCommand = \"R%2i%2o%2c\"",
        );
        assert_eq!(
            compile_protocol(&bad),
            Err(ProtocolCompileError::CardinalityMismatch {
                field: "pageReadCommand",
                declared: 2,
                entries: 1,
            })
        );
    }

    #[test]
    fn rejects_unresolved_directives() {
        let bad = format!("{MEGA_PROTOCOL}\n#if MODE\n");
        assert_eq!(
            compile_protocol(&bad),
            Err(ProtocolCompileError::UnresolvedDirective(
                "#if MODE".to_string()
            ))
        );
    }

    #[test]
    fn rejects_malformed_identifier_escape() {
        let bad = UAEFI_2025_PROTOCOL.replace("\\x00\\x00", "\\x00oops");
        assert_eq!(
            compile_protocol(&bad),
            Err(ProtocolCompileError::InvalidPageIdentifier(
                "\\x00oops".to_string()
            ))
        );
    }
}
