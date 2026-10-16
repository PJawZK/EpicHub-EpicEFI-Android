use crate::compiled_profile_bundle::CompiledEcuProfileBundle;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PageBurnPlan {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub request_body: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum PageBurnPlanError {
    InvalidPageNumber(usize),
    PageOutOfRange { page: usize, page_count: usize },
    MissingBurnCapability { page: usize },
    UnsupportedTemplateToken(String),
    DuplicateIdentifierToken,
    InvalidIdentifierLength { page: usize, actual: usize },
}

/// Plans one definition-owned nonvolatile burn request for a single definition page.
///
/// This function only renders protocol authority already present in the selected compiled bundle.
/// It does not execute transport, imply that RAM is current, or claim that persistence succeeded.
pub fn plan_page_burn(
    bundle: &CompiledEcuProfileBundle,
    page: usize,
) -> Result<PageBurnPlan, PageBurnPlanError> {
    if page == 0 {
        return Err(PageBurnPlanError::InvalidPageNumber(page));
    }

    let page_index = page - 1;
    let page_count = bundle.protocol.pages.len();
    let protocol = bundle
        .protocol
        .pages
        .get(page_index)
        .ok_or(PageBurnPlanError::PageOutOfRange { page, page_count })?;

    // Keep protocol/profile cardinality coherent even if a malformed bundle is ever constructed
    // outside the normal compiler path.
    if page_index >= bundle.profile.page_sizes().len() {
        return Err(PageBurnPlanError::PageOutOfRange {
            page,
            page_count: bundle.profile.page_sizes().len(),
        });
    }

    let template = protocol
        .burn_command
        .as_deref()
        .ok_or(PageBurnPlanError::MissingBurnCapability { page })?;
    let request_body = render_burn_template(template, &protocol.identifier, page)?;

    Ok(PageBurnPlan { page, request_body })
}

fn render_burn_template(
    template: &str,
    identifier: &[u8],
    page: usize,
) -> Result<Vec<u8>, PageBurnPlanError> {
    let bytes = template.as_bytes();
    let mut rendered = Vec::with_capacity(bytes.len() + identifier.len());
    let mut index = 0usize;
    let mut identifier_tokens = 0usize;

    while index < bytes.len() {
        if bytes[index] != b'%' {
            rendered.push(bytes[index]);
            index += 1;
            continue;
        }

        if index + 2 >= bytes.len() {
            return Err(PageBurnPlanError::UnsupportedTemplateToken(
                template[index..].to_string(),
            ));
        }

        let token = &template[index..index + 3];
        match token {
            "%2i" => {
                identifier_tokens += 1;
                if identifier_tokens > 1 {
                    return Err(PageBurnPlanError::DuplicateIdentifierToken);
                }
                if identifier.len() != 2 {
                    return Err(PageBurnPlanError::InvalidIdentifierLength {
                        page,
                        actual: identifier.len(),
                    });
                }
                rendered.extend_from_slice(identifier);
            }
            other => {
                return Err(PageBurnPlanError::UnsupportedTemplateToken(
                    other.to_string(),
                ));
            }
        }
        index += 3;
    }

    Ok(rendered)
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};

    fn bundle(
        page_sizes: &str,
        identifiers: &str,
        reads: &str,
        burns: Option<&str>,
    ) -> CompiledEcuProfileBundle {
        let pages = page_sizes.split(',').count();
        let burn_line = burns
            .map(|value| format!("burnCommand = {value}\n"))
            .unwrap_or_default();
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI burn-command.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = {pages}
pageSize = {page_sizes}
pageIdentifier = {identifiers}
pageReadCommand = {reads}
{burn_line}
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "burn-command".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            nested_definitions: HashMap::new(),
        })
        .unwrap()
    }

    #[test]
    fn plans_indexed_mega_style_burn_from_definition() {
        let bundle = bundle(
            "64, 64",
            r#""\x00\x00", "\x00\x01""#,
            r#""R%2i%2o%2c", "R%2i%2o%2c""#,
            Some(r#""B%2i", "B%2i""#),
        );

        assert_eq!(
            plan_page_burn(&bundle, 2),
            Ok(PageBurnPlan {
                page: 2,
                request_body: vec![b'B', 0x00, 0x01],
            })
        );
    }

    #[test]
    fn plans_non_indexed_burn_literal() {
        let bundle = bundle("64", r#""\x00\x00""#, r#""R%2o%2c""#, Some(r#""B""#));

        assert_eq!(
            plan_page_burn(&bundle, 1),
            Ok(PageBurnPlan {
                page: 1,
                request_body: vec![b'B'],
            })
        );
    }

    #[test]
    fn rejects_page_without_burn_capability() {
        let bundle = bundle(
            "64, 64",
            r#""\x00\x00", "\x00\x01""#,
            r#""R%2i%2o%2c", "R%2i%2o%2c""#,
            Some(r#""B%2i", """#),
        );

        assert_eq!(
            plan_page_burn(&bundle, 2),
            Err(PageBurnPlanError::MissingBurnCapability { page: 2 })
        );
    }

    #[test]
    fn rejects_burn_when_protocol_has_no_burn_capability() {
        let bundle = bundle("64", r#""\x00\x00""#, r#""R%2o%2c""#, None);

        assert_eq!(
            plan_page_burn(&bundle, 1),
            Err(PageBurnPlanError::MissingBurnCapability { page: 1 })
        );
    }

    #[test]
    fn rejects_invalid_or_out_of_range_page() {
        let bundle = bundle("64", r#""\x00\x00""#, r#""R%2o%2c""#, Some(r#""B""#));

        assert_eq!(
            plan_page_burn(&bundle, 0),
            Err(PageBurnPlanError::InvalidPageNumber(0))
        );
        assert_eq!(
            plan_page_burn(&bundle, 2),
            Err(PageBurnPlanError::PageOutOfRange {
                page: 2,
                page_count: 1,
            })
        );
    }

    #[test]
    fn rejects_tokens_not_proven_for_burn_commands() {
        let bundle = bundle("64", r#""\x00\x00""#, r#""R%2o%2c""#, Some(r#""B%2o""#));

        assert_eq!(
            plan_page_burn(&bundle, 1),
            Err(PageBurnPlanError::UnsupportedTemplateToken(
                "%2o".to_string()
            ))
        );
    }

    #[test]
    fn rejects_duplicate_identifier_token() {
        let bundle = bundle("64", r#""\x00\x00""#, r#""R%2o%2c""#, Some(r#""B%2i%2i""#));

        assert_eq!(
            plan_page_burn(&bundle, 1),
            Err(PageBurnPlanError::DuplicateIdentifierToken)
        );
    }
}
