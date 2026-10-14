use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ecu_definition::Endianness;
use crate::ecu_profile::ResolvedEcuProfile;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageReadPlan {
    pub page_index: usize,
    pub offset: usize,
    pub count: usize,
    pub request_body: Vec<u8>,
    pub expected_payload_bytes: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum PageReadPlanError {
    PageOutOfRange {
        page_index: usize,
        page_count: usize,
    },
    ZeroCount,
    OffsetTooLarge(usize),
    CountTooLarge(usize),
    RangeOverflow,
    RangeOutOfBounds {
        page_index: usize,
        page_size: usize,
        offset: usize,
        count: usize,
    },
    InvalidChunkLimit(usize),
    UnsupportedTemplateToken(String),
    MissingOffsetToken,
    MissingCountToken,
    DuplicateOffsetToken,
    DuplicateCountToken,
    DuplicateIdentifierToken,
    InvalidIdentifierLength {
        page_index: usize,
        actual: usize,
    },
}

pub fn plan_page_read(
    bundle: &CompiledEcuProfileBundle,
    page_index: usize,
    offset: usize,
    count: usize,
) -> Result<PageReadPlan, PageReadPlanError> {
    let page_sizes = bundle.profile.page_sizes();
    let page_size = *page_sizes
        .get(page_index)
        .ok_or(PageReadPlanError::PageOutOfRange {
            page_index,
            page_count: page_sizes.len(),
        })?;
    let page_protocol =
        bundle
            .protocol
            .pages
            .get(page_index)
            .ok_or(PageReadPlanError::PageOutOfRange {
                page_index,
                page_count: bundle.protocol.pages.len(),
            })?;

    if count == 0 {
        return Err(PageReadPlanError::ZeroCount);
    }
    if offset > u16::MAX as usize {
        return Err(PageReadPlanError::OffsetTooLarge(offset));
    }
    if count > u16::MAX as usize {
        return Err(PageReadPlanError::CountTooLarge(count));
    }
    let end = offset
        .checked_add(count)
        .ok_or(PageReadPlanError::RangeOverflow)?;
    if end > page_size {
        return Err(PageReadPlanError::RangeOutOfBounds {
            page_index,
            page_size,
            offset,
            count,
        });
    }

    let request_body = render_read_template(
        &page_protocol.read_command,
        &page_protocol.identifier,
        page_index,
        offset as u16,
        count as u16,
        bundle.profile.definition().endianness,
    )?;

    Ok(PageReadPlan {
        page_index,
        offset,
        count,
        request_body,
        expected_payload_bytes: count,
    })
}

pub fn plan_full_page_reads(
    bundle: &CompiledEcuProfileBundle,
    page_index: usize,
    max_chunk_bytes: usize,
) -> Result<Vec<PageReadPlan>, PageReadPlanError> {
    if max_chunk_bytes == 0 || max_chunk_bytes > u16::MAX as usize {
        return Err(PageReadPlanError::InvalidChunkLimit(max_chunk_bytes));
    }

    let page_sizes = bundle.profile.page_sizes();
    let page_size = *page_sizes
        .get(page_index)
        .ok_or(PageReadPlanError::PageOutOfRange {
            page_index,
            page_count: page_sizes.len(),
        })?;

    let mut plans = Vec::new();
    let mut offset = 0usize;
    while offset < page_size {
        let count = (page_size - offset).min(max_chunk_bytes);
        plans.push(plan_page_read(bundle, page_index, offset, count)?);
        offset += count;
    }
    Ok(plans)
}

fn render_read_template(
    template: &str,
    identifier: &[u8],
    page_index: usize,
    offset: u16,
    count: u16,
    endianness: Endianness,
) -> Result<Vec<u8>, PageReadPlanError> {
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
            return Err(PageReadPlanError::UnsupportedTemplateToken(
                template[index..].to_string(),
            ));
        }
        let token = &template[index..index + 3];
        match token {
            "%2i" => {
                identifier_tokens += 1;
                if identifier_tokens > 1 {
                    return Err(PageReadPlanError::DuplicateIdentifierToken);
                }
                if identifier.len() != 2 {
                    return Err(PageReadPlanError::InvalidIdentifierLength {
                        page_index,
                        actual: identifier.len(),
                    });
                }
                rendered.extend_from_slice(identifier);
            }
            "%2o" => {
                offset_tokens += 1;
                if offset_tokens > 1 {
                    return Err(PageReadPlanError::DuplicateOffsetToken);
                }
                rendered.extend_from_slice(&encode_u16(offset, endianness));
            }
            "%2c" => {
                count_tokens += 1;
                if count_tokens > 1 {
                    return Err(PageReadPlanError::DuplicateCountToken);
                }
                rendered.extend_from_slice(&encode_u16(count, endianness));
            }
            other => {
                return Err(PageReadPlanError::UnsupportedTemplateToken(
                    other.to_string(),
                ));
            }
        }
        index += 3;
    }

    if offset_tokens == 0 {
        return Err(PageReadPlanError::MissingOffsetToken);
    }
    if count_tokens == 0 {
        return Err(PageReadPlanError::MissingCountToken);
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
    ) -> CompiledEcuProfileBundle {
        let pages = page_sizes.split(',').count();
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
versionInfo = "V"
signature = "rusEFI read-planner.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = {endianness}
nPages = {pages}
pageSize = {page_sizes}
pageIdentifier = {identifiers}
pageReadCommand = {reads}
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "read-planner".to_string(),
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
    fn indexed_read_inserts_raw_identifier_and_little_endian_range_fields() {
        let bundle = bundle(
            "little",
            "32, 64",
            r#""\x00\x00", "\x00\x01""#,
            r#""R%2i%2o%2c", "R%2i%2o%2c""#,
        );

        let plan = plan_page_read(&bundle, 1, 0x1234, 0x0056).unwrap_err();
        assert_eq!(
            plan,
            PageReadPlanError::RangeOutOfBounds {
                page_index: 1,
                page_size: 64,
                offset: 0x1234,
                count: 0x0056,
            }
        );

        let plan = plan_page_read(&bundle, 1, 0x0012, 0x0020).unwrap();
        assert_eq!(
            plan.request_body,
            vec![b'R', 0x00, 0x01, 0x12, 0x00, 0x20, 0x00]
        );
        assert_eq!(plan.expected_payload_bytes, 0x20);
    }

    #[test]
    fn non_indexed_read_uses_definition_endianness_without_family_routing() {
        let little = bundle("little", "64", r#""\x00\x00""#, r#""R%2o%2c""#);
        let big = bundle("big", "64", r#""\x00\x00""#, r#""R%2o%2c""#);

        assert_eq!(
            plan_page_read(&little, 0, 0x0012, 0x0020)
                .unwrap()
                .request_body,
            vec![b'R', 0x12, 0x00, 0x20, 0x00]
        );
        assert_eq!(
            plan_page_read(&big, 0, 0x0012, 0x0020)
                .unwrap()
                .request_body,
            vec![b'R', 0x00, 0x12, 0x00, 0x20]
        );
    }

    #[test]
    fn full_page_planning_splits_a_65536_byte_page_into_representable_counts() {
        let bundle = bundle("little", "65536", r#""\x00\x00""#, r#""R%2o%2c""#);
        let plans = plan_full_page_reads(&bundle, 0, u16::MAX as usize).unwrap();

        assert_eq!(plans.len(), 2);
        assert_eq!((plans[0].offset, plans[0].count), (0, 65_535));
        assert_eq!((plans[1].offset, plans[1].count), (65_535, 1));
        assert_eq!(plans[1].request_body, vec![b'R', 0xff, 0xff, 0x01, 0x00]);
    }

    #[test]
    fn invalid_ranges_and_chunk_limits_fail_before_request_rendering() {
        let bundle = bundle("little", "64", r#""\x00\x00""#, r#""R%2o%2c""#);

        assert_eq!(
            plan_page_read(&bundle, 0, 0, 0),
            Err(PageReadPlanError::ZeroCount)
        );
        assert_eq!(
            plan_page_read(&bundle, 1, 0, 1),
            Err(PageReadPlanError::PageOutOfRange {
                page_index: 1,
                page_count: 1,
            })
        );
        assert_eq!(
            plan_page_read(&bundle, 0, 65, 1),
            Err(PageReadPlanError::RangeOutOfBounds {
                page_index: 0,
                page_size: 64,
                offset: 65,
                count: 1,
            })
        );
        assert_eq!(
            plan_full_page_reads(&bundle, 0, 0),
            Err(PageReadPlanError::InvalidChunkLimit(0))
        );
        assert_eq!(
            plan_full_page_reads(&bundle, 0, 65_536),
            Err(PageReadPlanError::InvalidChunkLimit(65_536))
        );
    }

    #[test]
    fn unsupported_or_ambiguous_read_templates_fail_closed() {
        let unknown = bundle("little", "64", r#""\x00\x00""#, r#""R%2o%2v""#);
        assert_eq!(
            plan_page_read(&unknown, 0, 0, 1),
            Err(PageReadPlanError::UnsupportedTemplateToken(
                "%2v".to_string()
            ))
        );

        let missing_count = bundle("little", "64", r#""\x00\x00""#, r#""R%2o""#);
        assert_eq!(
            plan_page_read(&missing_count, 0, 0, 1),
            Err(PageReadPlanError::MissingCountToken)
        );

        let duplicate_offset = bundle("little", "64", r#""\x00\x00""#, r#""R%2o%2o%2c""#);
        assert_eq!(
            plan_page_read(&duplicate_offset, 0, 0, 1),
            Err(PageReadPlanError::DuplicateOffsetToken)
        );
    }

    #[test]
    fn indexed_template_requires_exact_two_byte_compiled_identifier() {
        let bundle = bundle("little", "64", r#""\x00""#, r#""R%2i%2o%2c""#);
        assert_eq!(
            plan_page_read(&bundle, 0, 0, 1),
            Err(PageReadPlanError::InvalidIdentifierLength {
                page_index: 0,
                actual: 1,
            })
        );
    }
}
