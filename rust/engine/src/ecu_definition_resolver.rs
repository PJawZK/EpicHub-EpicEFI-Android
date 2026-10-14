use std::collections::{HashMap, HashSet};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResolvedDefinition {
    pub text: String,
    pub defines: HashMap<String, String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum DefinitionResolveError {
    UnsupportedDirective(String),
    UnsupportedCondition(String),
    InvalidDefine(String),
    ElseWithoutIf,
    DuplicateElse,
    EndifWithoutIf,
    UnterminatedIf,
    IncludeNotFound(String),
    IncludeCycle(String),
}

#[derive(Clone, Copy)]
struct ConditionalFrame {
    parent_active: bool,
    condition_true: bool,
    else_seen: bool,
}

pub struct DefinitionResolveRequest<'a> {
    pub text: &'a str,
    pub enabled_symbols: &'a HashSet<String>,
    pub includes: &'a HashMap<String, String>,
}

pub fn resolve_definition(
    request: DefinitionResolveRequest<'_>,
) -> Result<ResolvedDefinition, DefinitionResolveError> {
    let mut output = String::new();
    let mut defines = HashMap::new();
    let mut include_stack = Vec::new();
    resolve_text(
        request.text,
        request.enabled_symbols,
        request.includes,
        &mut include_stack,
        &mut defines,
        &mut output,
    )?;
    Ok(ResolvedDefinition {
        text: output,
        defines,
    })
}

fn resolve_text(
    text: &str,
    enabled_symbols: &HashSet<String>,
    includes: &HashMap<String, String>,
    include_stack: &mut Vec<String>,
    defines: &mut HashMap<String, String>,
    output: &mut String,
) -> Result<(), DefinitionResolveError> {
    let mut condition_stack: Vec<ConditionalFrame> = Vec::new();
    let mut active = true;

    for raw_line in text.lines() {
        let trimmed = raw_line.trim();
        if !trimmed.starts_with('#') {
            if active {
                output.push_str(raw_line);
                output.push('\n');
            }
            continue;
        }

        let directive = trimmed[1..].trim();
        if let Some(condition) = directive.strip_prefix("if ") {
            let symbol = parse_condition_symbol(condition)?;
            let condition_true = enabled_symbols.contains(symbol);
            condition_stack.push(ConditionalFrame {
                parent_active: active,
                condition_true,
                else_seen: false,
            });
            active = active && condition_true;
            continue;
        }

        if directive == "else" {
            let frame = condition_stack
                .last_mut()
                .ok_or(DefinitionResolveError::ElseWithoutIf)?;
            if frame.else_seen {
                return Err(DefinitionResolveError::DuplicateElse);
            }
            frame.else_seen = true;
            active = frame.parent_active && !frame.condition_true;
            continue;
        }

        if directive == "endif" {
            let frame = condition_stack
                .pop()
                .ok_or(DefinitionResolveError::EndifWithoutIf)?;
            active = frame.parent_active;
            continue;
        }

        if let Some(rest) = directive.strip_prefix("define ") {
            if active {
                let (name, value) = parse_define(rest)?;
                defines.insert(name.to_string(), value.to_string());
            }
            continue;
        }

        if let Some(rest) = directive.strip_prefix("include ") {
            if active {
                let include_name = parse_include_name(rest)?;
                if include_stack.iter().any(|entry| entry == include_name) {
                    return Err(DefinitionResolveError::IncludeCycle(
                        include_name.to_string(),
                    ));
                }
                let include_text = includes.get(include_name).ok_or_else(|| {
                    DefinitionResolveError::IncludeNotFound(include_name.to_string())
                })?;
                include_stack.push(include_name.to_string());
                resolve_text(
                    include_text,
                    enabled_symbols,
                    includes,
                    include_stack,
                    defines,
                    output,
                )?;
                include_stack.pop();
            }
            continue;
        }

        return Err(DefinitionResolveError::UnsupportedDirective(
            trimmed.to_string(),
        ));
    }

    if !condition_stack.is_empty() {
        return Err(DefinitionResolveError::UnterminatedIf);
    }
    Ok(())
}

fn parse_condition_symbol(condition: &str) -> Result<&str, DefinitionResolveError> {
    let condition = condition.trim();
    if condition.is_empty()
        || !condition
            .chars()
            .all(|ch| ch.is_ascii_alphanumeric() || ch == '_')
        || condition.as_bytes()[0].is_ascii_digit()
    {
        return Err(DefinitionResolveError::UnsupportedCondition(
            condition.to_string(),
        ));
    }
    Ok(condition)
}

fn parse_define(value: &str) -> Result<(&str, &str), DefinitionResolveError> {
    let value = value.trim();
    let Some((name, macro_value)) = value.split_once('=') else {
        return Err(DefinitionResolveError::InvalidDefine(value.to_string()));
    };
    let name = name.trim();
    if name.is_empty()
        || !name
            .chars()
            .all(|ch| ch.is_ascii_alphanumeric() || ch == '_')
        || name.as_bytes()[0].is_ascii_digit()
    {
        return Err(DefinitionResolveError::InvalidDefine(value.to_string()));
    }
    Ok((name, macro_value.trim()))
}

fn parse_include_name(value: &str) -> Result<&str, DefinitionResolveError> {
    let value = value.trim();
    if let Some(inner) = value
        .strip_prefix('"')
        .and_then(|rest| rest.strip_suffix('"'))
        .filter(|inner| !inner.is_empty())
    {
        return Ok(inner);
    }
    Err(DefinitionResolveError::UnsupportedDirective(format!(
        "#include {value}"
    )))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn symbols(values: &[&str]) -> HashSet<String> {
        values.iter().map(|value| (*value).to_string()).collect()
    }

    #[test]
    fn resolves_real_ve_and_lambda_branches() {
        let text = r#"
[Constants]
#if HUNDREDTH_VE
veTable = array, U16, 16448, [32x32], "%", 0.01, 0, 0, 650, 2
#else
veTable = array, U16, 16448, [32x32], "%", 0.01, 0, 0, 650, 1
#endif
#if LAMBDA
lambdaTable = array, U08, 18848, [32x32], "lambda", 0.006802721088435374, 0, 0.6, 1.5, 2
#else
lambdaTable = array, U08, 18848, [32x32], "afr", 0.1, 0, 0, 25, 1
#endif
"#;
        let enabled = symbols(&["HUNDREDTH_VE", "LAMBDA"]);
        let includes = HashMap::new();
        let resolved = resolve_definition(DefinitionResolveRequest {
            text,
            enabled_symbols: &enabled,
            includes: &includes,
        })
        .unwrap();

        assert!(resolved.text.contains("650, 2"));
        assert!(!resolved.text.contains("650, 1"));
        assert!(resolved.text.contains("\"lambda\""));
        assert!(!resolved.text.contains("\"afr\""));
        assert!(!resolved.text.contains("#if"));
    }

    #[test]
    fn supports_nested_conditions() {
        let text = "#if A\nouter\n#if B\ninner\n#else\nother\n#endif\n#endif\n";
        let enabled = symbols(&["A", "B"]);
        let includes = HashMap::new();
        let resolved = resolve_definition(DefinitionResolveRequest {
            text,
            enabled_symbols: &enabled,
            includes: &includes,
        })
        .unwrap();
        assert!(resolved.text.contains("outer"));
        assert!(resolved.text.contains("inner"));
        assert!(!resolved.text.contains("other"));
    }

    #[test]
    fn collects_active_simple_defines_without_emitting_directives() {
        let text = "#define gpio_list=\"NONE\", \"PA0\"\n[Constants]\nvalue = scalar, U08, 0, \"\", 1, 0\n";
        let enabled = HashSet::new();
        let includes = HashMap::new();
        let resolved = resolve_definition(DefinitionResolveRequest {
            text,
            enabled_symbols: &enabled,
            includes: &includes,
        })
        .unwrap();
        assert_eq!(
            resolved.defines.get("gpio_list").map(String::as_str),
            Some("\"NONE\", \"PA0\"")
        );
        assert!(!resolved.text.contains("#define"));
    }

    #[test]
    fn define_presence_does_not_enable_conditions() {
        let text = "#define FEATURE=1\n#if FEATURE\nenabled\n#else\ndisabled\n#endif\n";
        let enabled = HashSet::new();
        let includes = HashMap::new();
        let resolved = resolve_definition(DefinitionResolveRequest {
            text,
            enabled_symbols: &enabled,
            includes: &includes,
        })
        .unwrap();
        assert!(resolved.text.contains("disabled"));
        assert!(!resolved.text.contains("enabled\n"));
    }

    #[test]
    fn resolves_supplied_include_and_detects_cycles() {
        let enabled = HashSet::new();
        let mut includes = HashMap::new();
        includes.insert("child.inc".to_string(), "child-line\n".to_string());
        let resolved = resolve_definition(DefinitionResolveRequest {
            text: "root\n#include \"child.inc\"\nend\n",
            enabled_symbols: &enabled,
            includes: &includes,
        })
        .unwrap();
        assert_eq!(resolved.text, "root\nchild-line\nend\n");

        includes.insert(
            "child.inc".to_string(),
            "#include \"child.inc\"\n".to_string(),
        );
        assert_eq!(
            resolve_definition(DefinitionResolveRequest {
                text: "#include \"child.inc\"\n",
                enabled_symbols: &enabled,
                includes: &includes,
            }),
            Err(DefinitionResolveError::IncludeCycle(
                "child.inc".to_string()
            ))
        );
    }

    #[test]
    fn rejects_unproven_condition_expressions_and_directives() {
        let enabled = HashSet::new();
        let includes = HashMap::new();
        assert_eq!(
            resolve_definition(DefinitionResolveRequest {
                text: "#if A && B\nvalue\n#endif\n",
                enabled_symbols: &enabled,
                includes: &includes,
            }),
            Err(DefinitionResolveError::UnsupportedCondition(
                "A && B".to_string()
            ))
        );
        assert_eq!(
            resolve_definition(DefinitionResolveRequest {
                text: "#elif A\n",
                enabled_symbols: &enabled,
                includes: &includes,
            }),
            Err(DefinitionResolveError::UnsupportedDirective(
                "#elif A".to_string()
            ))
        );
    }

    #[test]
    fn semicolon_commented_directives_remain_inert_text() {
        let enabled = HashSet::new();
        let includes = HashMap::new();
        let text = ";#if CONN_SLOW\n;#else\nmaxUnusedRuntimeRange = 1000\n;#endif\n";
        let resolved = resolve_definition(DefinitionResolveRequest {
            text,
            enabled_symbols: &enabled,
            includes: &includes,
        })
        .unwrap();
        assert_eq!(resolved.text, text);
    }
}
