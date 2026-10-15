use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ecu_definition::Endianness;
use crate::ecu_profile::ResolvedEcuProfile;
use crate::write_reconciliation_plan::PageBytePatch;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageWritePlan {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub offset: usize,
    pub before: Vec<u8>,
    pub after: Vec<u8>,
    pub request_body: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum PageWritePlanError {
    InvalidPageNumber(usize),
    PageOutOfRange {
        page: usize,
        page_count: usize,
    },
    MissingChunkWriteCapability {
        page: usize,
    },
    EmptyPatch,
    PatchLengthMismatch {
        before: usize,
        after: usize,
    },
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
    MissingValueToken,
    DuplicateOffsetToken,
    DuplicateCountToken,
    DuplicateValueToken,
    DuplicateIdentifierToken,
    InvalidIdentifierLength {
        page: usize,
        actual: usize,
    },
}

pub fn plan_page_patch_writes(
    bundle: &CompiledEcuProfileBundle,
    patch: &PageBytePatch,
) -> Result<Vec<PageWritePlan>, PageWritePlanError> {
    if patch.page == 0 {
        return Err(PageWritePlanError::InvalidPageNumber(patch.page));
    }
    if patch.before.is_empty() || patch.after.is_empty() {
        return Err(PageWritePlanError::EmptyPatch);
    }
    if patch.before.len() != patch.after.len() {
        return Err(PageWritePlanError::PatchLengthMismatch {
            before: patch.before.len(),
            after: patch.after.len(),
        });
    }

    let page_index = patch.page - 1;
    let page_sizes = bundle.profile.page_sizes();
    let page_size = *page_sizes
        .get(page_index)
        .ok_or(PageWritePlanError::PageOutOfRange {
            page: patch.page,
            page_count: page_sizes.len(),
        })?;
    let protocol = bundle
        .protocol
        .pages
        .get(page_index)
        .ok_or(PageWritePlanError::PageOutOfRange {
            page: patch.page,
            page_count: bundle.protocol.pages.len(),
        })?;
    let template = protocol
        .chunk_write_command
        .as_deref()
        .ok_or(PageWritePlanError::MissingChunkWriteCapability { page: patch.page })?;

    let patch_end = patch
        .offset
        .checked_add(patch.after.len())
        .ok_or(PageWritePlanError::RangeOverflow)?;
    if patch_end > page_size {
        return Err(PageWritePlanError::RangeOutOfBounds {
            page: patch.page,
            page_size,
            offset: patch.offset,
            count: patch.after.len(),
        });
    }

    let mut plans = Vec::new();
    let mut consumed = 0usize;
    while consumed < patch.after.len() {
        let offset = patch
            .offset
            .checked_add(consumed)
            .ok_or(PageWritePlanError::RangeOverflow)?;
        if offset > u16::MAX as usize {
            return Err(PageWritePlanError::OffsetTooLarge(offset));
        }
        let count = (patch.after.len() - consumed).min(u16::MAX as usize);
        if count == 0 || count > u16::MAX as usize {
            return Err(PageWritePlanError::CountTooLarge(count));
        }
        let before = patch.before[consumed..consumed + count].to_vec();
        let after = patch.after[consumed..consumed + count].to_vec();
        let request_body = render_chunk_write_template(
            template,
            &protocol.identifier,
            patch.page,
            offset as u16,
            count as u16,
            &after,
            bundle.profile.definition().endianness,
        )?;
        plans.push(PageWritePlan {
            page: patch.page,
            offset,
            before,
            after,
            request_body,
        });
        consumed += count;
    }

    Ok(plans)
}

fn render_chunk_write_template(
    template: &str,
    identifier: &[u8],
    page: usize,
    offset: u16,
    count: u16,
    values: &[u8],
    endianness: Endianness,
) -> Result<Vec<u8>, PageWritePlanError> {
    let bytes = template.as_bytes();
    let mut rendered = Vec::with_capacity(bytes.len() + values.len() + 4);
    let mut index = 0usize;
    let mut identifier_tokens = 0usize;
    let mut offset_tokens = 0usize;
    let mut count_tokens = 0usize;
    let mut value_tokens = 0usize;

    while index < bytes.len() {
        if bytes[index] != b'%' {
            rendered.push(bytes[index]);
            index += 1;
            continue;
        }

        if index + 1 >= bytes.len() {
            return Err(PageWritePlanError::UnsupportedTemplateToken(
                template[index..].to_string(),
            ));
        }
        if bytes[index + 1] == b'v' {
            value_tokens += 1;
            if value_tokens > 1 {
                return Err(PageWritePlanError::DuplicateValueToken);
            }
            rendered.extend_from_slice(values);
            index += 2;
            continue;
        }
        if index + 2 >= bytes.len() {
            return Err(PageWritePlanError::UnsupportedTemplateToken(
                template[index..].to_string(),
            ));
        }

        let token = &template[index..index + 3];
        match token {
            "%2i" => {
                identifier_tokens += 1;
                if identifier_tokens > 1 {
                    return Err(PageWritePlanError::DuplicateIdentifierToken);
                }
                if identifier.len() != 2 {
                    return Err(PageWritePlanError::InvalidIdentifierLength {
                        page,
                        actual: identifier.len(),
                    });
                }
                rendered.extend_from_slice(identifier);
            }
            "%2o" => {
                offset_tokens += 1;
                if offset_tokens > 1 {
                    return Err(PageWritePlanError::DuplicateOffsetToken);
                }
                rendered.extend_from_slice(&encode_u16(offset, endianness));
            }
            "%2c" => {
                count_tokens += 1;
                if count_tokens > 1 {
                    return Err(PageWritePlanError::DuplicateCountToken);
                }
                rendered.extend_from_slice(&encode_u16(count, endianness));
            }
            other => {
                return Err(PageWritePlanError::UnsupportedTemplateToken(
                    other.to_string(),
                ));
            }
        }
        index += 3;
    }

    if offset_tokens == 0 {
        return Err(PageWritePlanError::MissingOffsetToken);
    }
    if count_tokens == 0 {
        return Err(PageWritePlanError::MissingCountToken);
    }
    if value_tokens == 0 {
        return Err(PageWritePlanError::MissingValueToken);
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
        page_size: usize,
        identifier: &str,
        chunk_write: &str,
    ) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI write-command.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = {endianness}
nPages = 1
pageSize = {page_size}
pageIdentifier = "{identifier}"
pageReadCommand = "R%2o%2c"
pageChunkWrite = "{chunk_write}"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "write-command".to_string(),
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
    fn indexed_chunk_write_inserts_identifier_range_and_payload() {
        let bundle = bundle("little", 64, r#"\x00\x01"#, "C%2i%2o%2c%v");
        let patch = PageBytePatch {
            page: 1,
            offset: 0x12,
            before: vec![0x10, 0x20, 0x30],
            after: vec![0xaa, 0xbb, 0xcc],
        };

        let plans = plan_page_patch_writes(&bundle, &patch).unwrap();
        assert_eq!(plans.len(), 1);
        assert_eq!(
            plans[0].request_body,
            vec![b'C', 0x00, 0x01, 0x12, 0x00, 0x03, 0x00, 0xaa, 0xbb, 0xcc]
        );
        assert_eq!(plans[0].before, vec![0x10, 0x20, 0x30]);
        assert_eq!(plans[0].after, vec![0xaa, 0xbb, 0xcc]);
    }

    #[test]
    fn non_indexed_big_endian_chunk_write_uses_definition_endianness() {
        let bundle = bundle("big", 64, r#"\x00\x00"#, "C%2o%2c%v");
        let patch = PageBytePatch {
            page: 1,
            offset: 0x12,
            before: vec![0x10, 0x20],
            after: vec![0xaa, 0xbb],
        };

        let plan = plan_page_patch_writes(&bundle, &patch).unwrap().remove(0);
        assert_eq!(
            plan.request_body,
            vec![b'C', 0x00, 0x12, 0x00, 0x02, 0xaa, 0xbb]
        );
    }

    #[test]
    fn large_patch_splits_into_representable_u16_chunks() {
        let bundle = bundle("little", 65_536, r#"\x00\x00"#, "C%2o%2c%v");
        let patch = PageBytePatch {
            page: 1,
            offset: 0,
            before: vec![0; 65_536],
            after: vec![1; 65_536],
        };

        let plans = plan_page_patch_writes(&bundle, &patch).unwrap();
        assert_eq!(plans.len(), 2);
        assert_eq!((plans[0].offset, plans[0].after.len()), (0, 65_535));
        assert_eq!((plans[1].offset, plans[1].after.len()), (65_535, 1));
        assert_eq!(&plans[1].request_body[..5], &[b'C', 0xff, 0xff, 0x01, 0x00]);
        assert_eq!(plans[1].request_body[5], 1);
    }

    #[test]
    fn missing_chunk_capability_and_invalid_patch_shape_fail_closed() {
        let missing = bundle("little", 64, r#"\x00\x00"#, "");
        let patch = PageBytePatch {
            page: 1,
            offset: 0,
            before: vec![1],
            after: vec![2],
        };
        assert_eq!(
            plan_page_patch_writes(&missing, &patch),
            Err(PageWritePlanError::MissingChunkWriteCapability { page: 1 })
        );

        let bundle = bundle("little", 64, r#"\x00\x00"#, "C%2o%2c%v");
        assert_eq!(
            plan_page_patch_writes(
                &bundle,
                &PageBytePatch {
                    page: 1,
                    offset: 0,
                    before: vec![1],
                    after: vec![2, 3],
                }
            ),
            Err(PageWritePlanError::PatchLengthMismatch {
                before: 1,
                after: 2,
            })
        );
    }

    #[test]
    fn page_range_is_validated_before_template_rendering() {
        let bundle = bundle("little", 8, r#"\x00\x00"#, "C%2o%2c%v");
        let patch = PageBytePatch {
            page: 1,
            offset: 7,
            before: vec![1, 2],
            after: vec![3, 4],
        };
        assert_eq!(
            plan_page_patch_writes(&bundle, &patch),
            Err(PageWritePlanError::RangeOutOfBounds {
                page: 1,
                page_size: 8,
                offset: 7,
                count: 2,
            })
        );
    }

    #[test]
    fn unsupported_or_ambiguous_templates_fail_closed() {
        let patch = PageBytePatch {
            page: 1,
            offset: 0,
            before: vec![1],
            after: vec![2],
        };

        let unknown = bundle("little", 8, r#"\x00\x00"#, "C%2o%2c%q%v");
        assert_eq!(
            plan_page_patch_writes(&unknown, &patch),
            Err(PageWritePlanError::UnsupportedTemplateToken(
                "%2q".to_string()
            ))
        );

        let missing_values = bundle("little", 8, r#"\x00\x00"#, "C%2o%2c");
        assert_eq!(
            plan_page_patch_writes(&missing_values, &patch),
            Err(PageWritePlanError::MissingValueToken)
        );

        let duplicate_values = bundle("little", 8, r#"\x00\x00"#, "C%2o%2c%v%v");
        assert_eq!(
            plan_page_patch_writes(&duplicate_values, &patch),
            Err(PageWritePlanError::DuplicateValueToken)
        );
    }

    #[test]
    fn indexed_write_requires_exact_two_byte_identifier() {
        let bundle = bundle("little", 8, r#"\x00"#, "C%2i%2o%2c%v");
        let patch = PageBytePatch {
            page: 1,
            offset: 0,
            before: vec![1],
            after: vec![2],
        };
        assert_eq!(
            plan_page_patch_writes(&bundle, &patch),
            Err(PageWritePlanError::InvalidIdentifierLength { page: 1, actual: 1 })
        );
    }
}
