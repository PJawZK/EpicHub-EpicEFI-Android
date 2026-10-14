# ECU definition compiler foundation

## Purpose

This slice begins the data-driven side of EpicHub's generic ECU-profile architecture.

The previous qualified slice established that tuning/session code no longer needs board-specific routing. This slice moves one layer outward: controller identity and protocol/page geometry must come from an ECU definition rather than from a Rust type written for each board.

The first compiler target is the generated TunerStudio ECU-definition format used by EpicEFI/rusEFI. TunerStudio INI is an input/interoperability format, not EpicHub's permanent runtime object model.

---

## Evidence

Two real generated definitions already demonstrate meaningful profile variation while retaining the same broad protocol family.

Mega144H7 profile `353`:

- signature `rusEFI master.2026.08.26.MEGA144H7.2273317132`;
- `messageEnvelopeFormat = msEnvelope_1.0`;
- little endian;
- two pages;
- page sizes 55,764 and 5,000 bytes;
- indexed page read commands.

Representative uaEFI definition:

- signature `rusEFI master.2025.05.23.uaefi.3590163173`;
- `messageEnvelopeFormat = msEnvelope_1.0`;
- little endian;
- one page;
- page size 23,824 bytes;
- single-page read/write command forms.

These differences belong in compiled definition/profile data. They do not justify a different Tuner application or board-name branch.

---

## Compiler boundary

```text
raw ECU definition text
        |
        v
DefinitionCompiler
  - lexical section/key parsing
  - active-line/comment handling
  - required-field validation
  - normalized protocol/page geometry
        |
        v
CompiledEcuDefinition
  - exact firmware signature
  - query/version commands
  - message envelope format
  - endianness
  - page count
  - page identifiers
  - page sizes
  - page read/write/burn/CRC command templates
        |
        v
ProfileProvider / exact-signature match
```

Later slices extend the same compiled object with runtime-output channels, constants, arrays, tables, curves, conditions and dialogs. They must not create another authority path.

---

## Important parser rules

The initial compiler is deliberately strict and small:

- ignore blank lines and semicolon-prefixed comments;
- recognize section headers;
- parse active `key = value` assignments;
- strip trailing semicolon comments only when outside quotes;
- preserve quoted command contents;
- require an exact TunerStudio signature;
- require page count and page-size geometry to agree;
- fail closed on missing or malformed required geometry;
- retain unknown keys for future compiler stages only if explicitly needed; do not silently invent defaults for required authority data.

Preprocessor directives, setting groups, includes and conditional blocks are not silently interpreted by this first compiler. A later conditional-resolution stage must handle them explicitly before such definitions are considered fully compiled for tuning authority.

---

## Identity integration

The generic profile identity contract must support values owned by a dynamically compiled definition. It therefore cannot require all profile names/signatures/page arrays to be `'static` Rust data.

The intended API is borrowed from the profile instance:

```text
ResolvedEcuProfile::identity() -> ProfileIdentity<'_>
ResolvedEcuProfile::page_sizes() -> &[usize]
```

This retains zero-copy access for built-in/static profiles while allowing compiled definitions to own normal `String` / `Vec` data.

---

## Acceptance criteria

1. `ProfileIdentity`/page geometry no longer require `'static` storage.
2. `CompiledEcuDefinition` is produced from definition text without board-name routing.
3. Exact signature matching works against dynamically compiled identities.
4. Real Mega144H7 definition excerpts compile to two-page geometry `55_764, 5_000`.
5. Real uaEFI definition excerpts compile to one-page geometry `23_824`.
6. Missing signature, malformed counts and page-count/page-size disagreement fail closed.
7. Existing Mega144H7 `ResolvedEcuProfile` and source-install behavior remains valid.
8. No live transport, TuneSnapshot fabrication, ECU RAM write or Burn authority is introduced.

---

## Non-goals

This slice does not yet compile:

- full calibration scalar/array layouts;
- semantic table/curve descriptors;
- runtime output channels;
- arbitrary `#if` / setting-group conditions;
- include-file graphs;
- dialogs/menus;
- MSQ files;
- ECU writes or Burn.

Those are subsequent compiler stages built on the same normalized definition authority.
