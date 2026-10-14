# ECU definition resolution and protocol compilation

This stage sits before the already-qualified identity, calibration and editor compilers.

Its purpose is to turn an unresolved TunerStudio/rusEFI definition bundle into deterministic text plus normalized protocol capabilities without guessing unsupported source grammar.

## Definition resolution

`ecu_definition_resolver` currently supports the source grammar proven by the reference definitions:

- `#if SYMBOL`
- `#else`
- `#endif`
- nested condition blocks
- `#define NAME=value`
- `#include "name"` when the caller supplies the include text explicitly

Semicolon-commented lines such as `;#if CONN_SLOW` remain ordinary inert comments.

Simple `#define` directives are collected into a macro map and removed from resolved output. They are **not** silently expanded into arbitrary expressions in this stage. A later compiler stage may add bounded macro expansion once the required use cases are demonstrated.

Unsupported conditions or directives fail closed. Examples that currently reject compilation include compound expressions such as `#if A && B`, `#elif`, malformed defines, missing includes and include cycles.

The resolver does not search the filesystem or guess include paths. The caller must provide an explicit include map/bundle, keeping project/profile provenance under the source-provider boundary.

## Protocol compilation

`ecu_protocol` compiles the authority-bearing communication geometry in `[Constants]`:

- `nPages`
- `pageIdentifier`
- `pageReadCommand`
- `burnCommand`
- `pageValueWrite`
- `pageChunkWrite`
- `crc32CheckCommand`
- `retrieveConfigError`

Each compiled page owns:

- identifier bytes;
- required read-command template;
- optional Burn command;
- optional value-write command;
- optional chunk-write command;
- optional CRC32-check command.

### Cardinality is strict

When a per-page field is present, its entry count must equal `nPages` exactly.

EpicHub does **not** infer that one supplied command should be replicated across multiple pages. This prevents a board-specific or TunerStudio-specific convenience rule from becoming hidden protocol authority.

An absent optional field means the capability is absent for all pages. An explicit empty string in an optional command list means that capability is absent for that page. A read command is required for every page and may not be empty.

### Page identifiers

The first protocol slice accepts the observed binary identifier form using `\xNN` byte escapes. Invalid or ambiguous identifier syntax fails closed.

## Why exact definitions matter

The reference data already demonstrates materially different protocol geometry:

- Mega144H7 2026 definition: two pages and `%2i` page-indexed read/write/Burn/CRC templates;
- uaEFI 2025 definition: one page with non-indexed `R`, `W`, `C`, `B` and `k` templates;
- a later uaEFI 2026 definition: two pages with page-indexed templates.

Therefore protocol behavior cannot be selected from a board-family name. It belongs to the exact resolved firmware definition associated with the live signature/profile.

## Authority boundary

This slice still does not:

- open or probe a transport;
- expand arbitrary macro expressions;
- interpret arbitrary TunerStudio preprocessor grammar;
- assign constants to physical pages;
- decode/encode raw calibration bytes;
- create a complete `ResolvedEcuProfile`;
- grant ECU RAM write or Burn authority.

The next compiler step should attach normalized calibration primitives to page ownership and implement bounded raw byte codecs. After that, deterministic semantic table IDs can be assigned and the compiled Mega144H7 path can be compared directly against the existing hand-coded reference resolver.
