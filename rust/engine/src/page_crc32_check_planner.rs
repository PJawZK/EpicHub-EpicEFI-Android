use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ecu_definition::Endianness;
use crate::ecu_profile::ResolvedEcuProfile;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageCrc32CheckPlan {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub offset: usize,
    pub count: usize,
    pub request_body: Vec<u8>,
    pub expected_payload_bytes: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum PageCrc32CheckPlanError {
    InvalidPageNumber(usize),
    PageOutOfRange { page: usize, page_count: usize },
    MissingCrc32CheckCapability { page: usize },
    ZeroCount,
    OffsetTooLarge(usize),
    CountTooLarge(usize),
    RangeOverflow,
    RangeOutOfBounds {
        page: usize,
        page_size: usize,
        offset: usize,
        count: usize,
    },
    UnsupportedTemplateToken(String),
    MissingOffsetToken,
    MissingCountToken,
    DuplicateOffsetToken,
    DuplicateCountToken,
    DuplicateIdentifierToken,
    InvalidIdentifierLength { page: usize, actual: usize },
}

/// Plans one definition-owned CRC32 verification request for a page range.
///
/// This is request planning only. It does not execute transport, interpret a returned checksum,
/// prove nonvolatile persistence, or authorize a Burn operation.
pub fn plan_page_crc32_check(
    bundle: &CompiledEcuProfileBundle,
    page: usize,
    offset: usize,
    count: usize,
) -> Result<PageCrc32CheckPlan, PageCrc32CheckPlanError> {
    if page == 0 {
        return Err(PageCrc32CheckPlanError::InvalidPageNumber(page));
    }

    let page_index = page - 1;
    let page_sizes = bundle.profile.page_sizes();
    let page_size = *page_sizes
        .get(page_index)
        .ok_or(PageCrc32CheckPlanError::PageOutOfRange {
            page,
            page_count: page_sizes.len(),
        })?;
    let protocol = bundle
        .protocol
        .pages
        .get(page_index)
        .ok_or(PageCrc32CheckPlanError::PageOutOfRange {
            page,
            page_count: bundle.protocol.pages.len(),
        })?;
    let template = protocol
        .crc32_check_command
        .as_deref()
        .ok_or(PageCrc32CheckPlanError::MissingCrc32CheckCapability { page })?;

    if count == 0 {
        return Err(PageCrc32CheckPlanError::ZeroCount);
    }
    if offset > u16::MAX as usize {
        return Err(PageCrc32CheckPlanError::OffsetTooLarge(offset));
    }
    if count > u16::MAX as usize {
        return Err(PageCrc32CheckPlanError::CountTooLarge(count));
    }
    let end = offset
        .checked_add(count)
        .ok_or(PageCrc32CheckPlanError::RangeOverflow)?;
    if end > page_size {
        return Err(PageCrc32CheckPlanError::RangeOutOfBounds {
            page,
            page_size,
            offset,
            count,
        });
    }

    let request_body = render_crc32_check_template(
        template,
        &protocol.identifier,
        page,
        offset as u16,
        count as u16,
        bundle.profile.definition().endianness,
    )?;

    Ok(PageCrc32CheckPlan {
        page,
        offset,
        count,
        request_body,
        expected_payload_bytes: 4,
    })
}

pub fn plan_full_page_crc32_check(
    bundle: &CompiledEcuProfileBundle,
    page: usize,
) -> Result<PageCrc32CheckPlan, PageCrc32CheckPlanError> {
    if page == 0 {
        return Err(PageCrc32CheckPlanError::InvalidPageNumber(page));
    }
    let page_sizes = bundle.profile.page_sizes();
    let page_size = *page_sizes
        .get(page - 1)
        .ok_or(PageCrc32CheckPlanError::PageOutOfRange {
            page,
            page_count: page_sizes.len(),
        })?;
    plan_page_crc32_check(bundle, page, 0, page_size)
}

fn render_crc32_check_template(
    template: &str,
    identifier: &[u8],
    page: usize,
    offset: u16,
    count: u16,
    endianness: Endianness,
) -> Result<Vec<u8>, PageCrc32CheckPlanError> {
    let bytes = template.as_bytes();
    let mut rendered = Vec::with_capacity(bytes.len() + 4);
    let mut index = 0usize;
    let mut identifier_tokens = 0usize;
    let mut offset_tokens = 0usize;
    let mut count_tokens = 0usize;

    while index < bytes.len() {
        if bytes[index] != b'%' {
            rendered.push(bytes[index]);
            index += 1;
            continue;
        }
        if index + 2 >= bytes.len() {
            return Err(PageCrc32CheckPlanError::UnsupportedTemplateToken(
                template[index..].to_string(),
            ));
        }

        let token = &template[index..index + 3];
        match token {
            "%2i" => {
                identifier_tokens += 1;
                if identifier_tokens > 1 {
                    return Err(PageCrc32CheckPlanError::DuplicateIdentifierToken);
                }
                if identifier.len() != 2 {
                    return Err(PageCrc32CheckPlanError::InvalidIdentifierLength {
                        page,
                        actual: identifier.len(),
                    });
                }
                rendered.extend_from_slice(identifier);
            }
            "%2o" => {
                offset_tokens += 1;
                if offset_tokens > 1 {
                    return Err(PageCrc32CheckPlanError::DuplicateOffsetToken);
                }
                rendered.extend_from_slice(&encode_u16(offset, endianness));
            }
            "%2c" => {
                count_tokens += 1;
                if count_tokens > 1 {
                    return Err(PageCrc32CheckPlanError::DuplicateCountToken);
                }
                rendered.extend_from_slice(&encode_u16(count, endianness));
            }
            other => {
                return Err(PageCrc32CheckPlanError::UnsupportedTemplateToken(
                    other.to_string(),
                ));
            }
        }
        index += 3;
    }

    if offset_tokens == 0 {
        return Err(PageCrc32CheckPlanError::MissingOffsetToken);
    }
    if count_tokens == 0 {
        return Err(PageCrc32CheckPlanError::MissingCountToken);
    }

    Ok(rendered)
}

fn encode_u16(value: u16, endianness: Endianness) -> [u8; 2] {
    match endianness {
        Endianness::Little => value.to_le_bytes(),
        Endianness::Big => value.to_be_bytes(),
    }
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};

    fn bundle(
        endianness: &str,
        page_sizes: &str,
        identifiers: &str,
        reads: &str,
        crc_checks: Option<&str>,
    ) -> CompiledEcuProfileBundle {
        let pages = page_sizes.split(',').count();
        let crc_line = crc_checks
            .map(|value| format!("crc32CheckCommand = {value}\n"))
            .unwrap_or_default();
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI crc32-check.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = {endianness}
nPages = {pages}
pageSize = {page_sizes}
pageIdentifier = {identifiers}
pageReadCommand = {reads}
{crc_line}
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "crc32-check".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
    }

    #[test]
    fn plans_indexed_mega_crc_check_with_little_endian_range() {
        let bundle = bundle(
            "little",
            "64, 256",
            r#""\x00\x00", "\x00\x01""#,
            r#""R%2i%2o%2c", "R%2i%2o%2c""#,
            Some(r#""k%2i%2o%2c", "k%2i%2o%2c""#),
        );

        assert_eq!(
            plan_page_crc32_check(&bundle, 2, 16, 32),
            Ok(PageCrc32CheckPlan {
                page: 2,
                offset: 16,
                count: 32,
                request_body: vec![b'k', 0x00, 0x01, 0x10, 0x00, 0x20, 0x00],
                expected_payload_bytes: 4,
            })
        );
    }

    #[test]
    fn plans_non_indexed_uaefi_full_page_crc_check() {
        let bundle = bundle(
            "little",
            "256",
            r#""\x00\x00""#,
            r#""R%2o%2c""#,
            Some(r#""k%2o%2c""#),
        );

        assert_eq!(
            plan_full_page_crc32_check(&bundle, 1),
            Ok(PageCrc32CheckPlan {
                page: 1,
                offset: 0,
                count: 256,
                request_body: vec![b'k', 0x00, 0x00, 0x00, 0x01],
                expected_payload_bytes: 4,
            })
        );
    }

    #[test]
    fn respects_big_endian_definition_range_fields() {
        let bundle = bundle(
            "big",
            "512",
            r#""\x00\x00""#,
            r#""R%2o%2c""#,
            Some(r#""k%2o%2c""#),
        );

        let plan = plan_page_crc32_check(&bundle, 1, 0x0102, 0x0030).unwrap();
        assert_eq!(plan.request_body, vec![b'k', 0x01, 0x02, 0x00, 0x30]);
    }

    #[test]
    fn rejects_missing_crc_capability() {
        let bundle = bundle("little", "64", r#""\x00\x00""#, r#""R%2o%2c""#, None);
        assert_eq!(
            plan_full_page_crc32_check(&bundle, 1),
            Err(PageCrc32CheckPlanError::MissingCrc32CheckCapability { page: 1 })
        );
    }

    #[test]
    fn rejects_invalid_ranges() {
        let bundle = bundle(
            "little",
            "64",
            r#""\x00\x00""#,
            r#""R%2o%2c""#,
            Some(r#""k%2o%2c""#),
        );

        assert_eq!(
            plan_page_crc32_check(&bundle, 1, 0, 0),
            Err(PageCrc32CheckPlanError::ZeroCount)
        );
        assert_eq!(
            plan_page_crc32_check(&bundle, 1, 48, 32),
            Err(PageCrc32CheckPlanError::RangeOutOfBounds {
                page: 1,
                page_size: 64,
                offset: 48,
                count: 32,
            })
        );
    }

    #[test]
    fn rejects_template_without_required_range_tokens() {
        let bundle = bundle(
            "little",
            "64",
            r#""\x00\x00""#,
            r#""R%2o%2c""#,
            Some(r#""k%2o""#),
        );

        assert_eq!(
            plan_page_crc32_check(&bundle, 1, 0, 64),
            Err(PageCrc32CheckPlanError::MissingCountToken)
        );
    }

    #[test]
    fn rejects_unproven_crc_template_tokens() {
        let bundle = bundle(
            "little",
            "64",
            r#""\x00\x00""#,
            r#""R%2o%2c""#,
            Some(r#""k%2o%2c%v""#),
        );

        assert_eq!(
            plan_page_crc32_check(&bundle, 1, 0, 64),
            Err(PageCrc32CheckPlanError::UnsupportedTemplateToken(
                "%v".to_string()
            ))
        );
    }
}
