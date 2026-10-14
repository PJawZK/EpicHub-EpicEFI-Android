# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the current head of `phase2/tuner-definition-condition-protocol-compiler`, draft PR #26 if it exists, PR #25, and the latest Foundation quality run before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The active direction is **definition-driven multi-ECU Tuner architecture**. Do not return to Mega144H7-specific Android source-provider UI until the compiled profile path can reproduce the proven Mega144H7 `TuneSnapshot` semantics.

---

# Current checkpoint — 2026-10-14

## Qualified stacked PRs

- **PR #23 — Tuner: generalize ECU profile resolution and source install**
  - branch: `phase2/tuner-generic-ecu-profile-architecture`
  - exact qualified head: `7b0cb2d4144e01ecc4fec51f8559ee92a0bb52b8`
  - generic `ResolvedEcuProfile` contract;
  - generic imported-page install path;
  - Mega144H7 becomes an adapter rather than a hard-coded product architecture.

- **PR #24 — Tuner: compile ECU identity and page geometry from definitions**
  - branch: `phase2/tuner-ecu-definition-compiler`
  - exact qualified head: `374629e70feeee3186d6fdc0aeb1f0193dee193d`
  - Foundation run `38542649432` / #536 — PASS;
  - dynamic profile identity and page geometry;
  - exact signature matching;
  - first normalized definition compiler fields: query/version command, envelope, endianness, page count and sizes.

- **PR #25 — Tuner: compile calibration primitives and editor descriptors**
  - branch: `phase2/tuner-definition-calibration-compiler`
  - exact qualified head: `1b55955e9abb9ed94d0a6f712c76c31a039b4b5e`
  - Foundation run `38545563759` / #546 — PASS;
  - PR remains open/draft/unmerged/mergeable;
  - normalized scalar/array calibration storage;
  - normalized `[TableEditor]` and `[CurveEditor]` bindings;
  - reference/shape validation;
  - unresolved `#...` definition directives fail closed.

## Active branch

`phase2/tuner-definition-condition-protocol-compiler`

This branch starts exactly from qualified PR #25 head `1b55955e...`.

Next slice: resolve definition conditions/includes sufficiently to make full generated definitions safe input, and normalize per-page protocol command geometry. This branch may become PR #26 stacked on PR #25.

---

# Product architecture authority

The target architecture is:

```text
transport candidate
    ↓
read-only runtime identity probe
    ↓
exact live signature
    ↓
ProfileRegistry / definition provider
    ↓
resolved + compiled ECU definition
    ↓
protocol capability / page geometry validation
    ↓
complete TuneSnapshot
    ↓
engine-owned TuneSession / TuneDraft
```

Three identities must remain separate:

1. **Transport candidate identity** — USB VID/PID, serial endpoint, TCP, CAN adapter. Only answers whether a controller could be reachable.
2. **Runtime controller identity** — full live firmware signature plus optional descriptive firmware/board data.
3. **Resolved profile identity** — exact expected signature, definition fingerprint/generation and compiled schema/capabilities.

Only an exact resolved profile match plus validated schema grants interpretation/tuning authority.

USB VID/PID is never ECU identity. Board-name matching and signature-prefix matching are not authority.

Unknown firmware, recognized controller without profile, profile mismatch and malformed/incomplete profile all fail closed.

---

# Definition compiler direction

TunerStudio/rusEFI INI is an **input/compiler source**, not runtime architecture to copy.

Preferred flow:

```text
mainController.ini + resolved includes/conditions
        ↓
EpicHub definition compiler
        ↓
CompiledEcuDefinition / CompiledEcuProfile
        ↓
production engine
```

Do not scatter live INI string lookups throughout Android or Rust runtime code.

Current compiled stages:

## Identity / geometry

- exact signature;
- query/version command;
- message envelope;
- endianness;
- declared page count;
- page sizes.

## Calibration primitives

`scalar` and `array` definitions normalize to:

- name;
- storage type (`U08`, `S08`, `U16`, `S16`, `U32`, `S32`, `F32`);
- byte offset;
- scalar/array dimensions;
- units/expression text;
- scale/translate;
- optional bounds;
- display precision.

Top-level field splitting preserves commas inside quoted strings, braces and function expressions.

Bitfields/enums are deliberately not compiled yet.

## Editor semantics

`[TableEditor]` compiles table/map/title/page plus X/Y/Z primitive references and runtime-channel bindings.

`[CurveEditor]` compiles curve/title plus X/Y primitive references and runtime-channel binding.

Validation currently rejects:

- unknown primitive references;
- non-1D table axes;
- table Z dimensions that do not match axis lengths;
- curve X/Y length mismatch;
- unresolved preprocessor directives.

The current Mega144H7 hand-coded resolver remains reference authority until a compiled definition reproduces the same semantic snapshot exactly.

---

# Real definition evidence already established

## Mega144H7

- profile name: `353`
- signature: `rusEFI master.2026.08.26.MEGA144H7.2273317132`
- fingerprint: `1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa`
- little endian;
- 2 pages: 55,764 and 5,000 bytes;
- `pageIdentifier = "\x00\x00", "\x00\x01"`
- `pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"`
- `burnCommand = "B%2i", "B%2i"`

Current hand-coded semantic table IDs:

- 1001 VE;
- 1002 ignition;
- 1003 target lambda/AFR;
- 1004 MAP estimate.

These IDs are stable semantic IDs for the current Mega profile, not evidence that unrelated ECUs should reuse the same numeric IDs automatically.

## uaEFI comparison evidence

- signature: `rusEFI master.2025.05.23.uaefi.3590163173`
- little endian;
- 1 page: 23,824 bytes;
- `pageIdentifier = "\x00\x00"`
- `pageReadCommand = "R%2o%2c"`
- `burnCommand = "B"`
- `pageValueWrite = "W%2o%v"`
- `pageChunkWrite = "C%2o%2c%v"`
- `crc32CheckCommand = "k%2o%2c"`

The Mega vs uaEFI command differences are direct proof that command framing belongs in definition data, not board-specific control flow.

---

# Proven engine/tuning architecture that must not regress

Earlier qualified Phase 2 work established:

- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine instance;
- immutable baseline + mutable `TuneDraft`;
- revision guard and Rust-owned Undo/Redo;
- 2D and 3D are peer representations of the same table state;
- all input surfaces emit semantic commands;
- preview is non-mutating until explicit Apply;
- table transforms operate on semantic calibration values, not UI-local arrays;
- source install is atomic and validates completely before session replacement;
- failed source install preserves prior baseline, draft, revision and history;
- source/profile/draft/write/Burn remain separate;
- stable semantic IDs are independent of raw byte offsets;
- no fabricated tune values.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

Do not reintroduce `ReusableContentHost`; retained inactive Compose route trees caused a physical crash in earlier work.

TunerStudio, MegaLogViewer and the Table Interaction Lab are behavioral references, not architecture authority.

---

# Immediate next work

1. Inspect real Mega144H7/uaEFI directive grammar (`#if`, `#else`, `#endif`, `#include`, nested conditions, negation/expressions) before implementing a resolver.
2. Implement a deterministic resolved-definition stage; unsupported directive grammar must fail closed rather than guess.
3. Normalize per-page protocol data: page identifiers, read commands, Burn commands and write/CRC command templates.
4. Validate command cardinality against `nPages` without inventing undocumented replication rules.
5. Add tests using real Mega144H7 and uaEFI definition excerpts.
6. Qualify the exact branch head with the full Foundation pipeline.
7. Next after that: page ownership + raw-byte decode/encode + deterministic semantic-ID mapping, then prove compiled Mega output equals the current hand-coded resolver.

Do not build generic Android live/source-provider UI before the compiled definition/profile authority is ready.
