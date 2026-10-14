# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the actual head of `phase2/tuner-session-source-install`, PR #22, PR #21, and the latest Foundation quality run before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The active direction is now **real EpicHub Tuner product architecture**, not prototype-shell work and not further optimization of provisional placeholder UI.

The Tuner has progressed from a synthetic offline interaction lab into an engine-owned semantic tuning architecture with authentic Mega144H7 profile resolution and atomic tune-source installation. The next work should build on that authority rather than reintroducing fixture-specific state or a second mutation path.

---

# Current repository state — 2026-10-14

- integration branch: `main`
- long-lived native foundation branch: `phase2/performance-foundation`
- current active development branch: `phase2/tuner-session-source-install`
- active stacked draft PR: **#22 — Tuner: install resolved sources atomically into TuneSession**
- PR #22 base: qualified PR #21 head `9ac1d285e179e216da6e9209df58054f5e069a91`
- PR #22 exact qualified head: `83165ec3a9cf59f2a4b9737e8b2ce4d7c8d0daca`
- PR #22 is open, draft, unmerged and mergeable
- Foundation quality run for PR #22 exact head: **`38534675238` — PASS**
- PR #21 exact qualified head: `9ac1d285e179e216da6e9209df58054f5e069a91`
- PR #21 Foundation run: **`38532426383` — PASS**

The chat-limit interruption happened earlier in the PR #22 formatting/fix cycle at head `119d0e937b2a86a609d064d60fcea0db1c34c472`, where CI stopped only at rustfmt. That state is obsolete. The exact formatter corrections and a JUnit-4 catalog-test compatibility fix were subsequently committed, and `83165ec3...` is the only PR #22 head that should now be treated as qualified.

---

# Core architectural decisions retained

## D-022 — source reimplementation method

Inherited behavior is reviewed slice-by-slice and cleanly reimplemented. Do not bulk-port EpicDash-JZ, TunerStudio, MegaLogViewer or historical plugin subsystems.

## D-023 — runtime language and serialization

- production runtime: Kotlin / Jetpack Compose + Rust;
- no WebView/JavaScript/DOM production runtime architecture;
- Kotlin↔Rust uses semantic JNI operations and bounded/versioned packed results;
- JSON/CBOR are not universal runtime buses;
- serialization exists only at explicit persistence/export/interoperability boundaries.

## D-024 — native Tuning Tools

AE / PID / TCharge / Injection Angle become native Tuning Tools using shared Rust analysis infrastructure and the single semantic tuning authority. They are not runtime plugins.

## D-025 — navigation/domain structure

Primary workspaces:

```text
Dashboard | Tuner | Logger | Diagnostics
```

Leaf/domain routes:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

Grouping is presentation-only:

```text
Tuner workspace  → Tuner | Tuning Tools
Logger workspace → Logger | Analyzer
```

Do not make Analyzer depend on Logger UI/ViewModel/state and do not make Tuning Tools depend on Tuner UI/ViewModel/state.

## D-026 — direct 3D editing contract

D-016's original separation between 3D presentation state and calibration authority remains valid. Its condition for future direct 3D editing has now been satisfied by a separately accepted semantic edit contract.

3D may be an editable **input surface**, but it never owns tune values, calibration transforms, history or ECU authority.

```text
2D input ─┐
          ├─ TunerCommand / semantic edit → Rust TuneDraft
3D input ─┘
```

Camera state, hit-testing geometry and gesture state remain presentation/input concerns. All calibration math remains below the shared command boundary.

---

# Table Interaction Lab authority

The imported Table Interaction Lab research remains an important behavioral foundation, but it is **not** production architecture to copy blindly.

Canonical authority:

`EpicEFI_Table_Interaction_Lab_Portable_Developer_Reference_FINAL.html`

Corrected project rule:

- 2D and 3D are both CORE table functionality;
- both representations consume one authoritative table/draft state;
- 3D must never maintain a separate value array or tune authority;
- input devices invoke existing domain operations rather than reimplementing them;
- production styling, DOM/browser plumbing and generated demo data are replaceable;
- explicit ECU RAM write/Burn remains below and separate from the semantic tuning domain.

Useful derivative documents already incorporated into EpicHub planning:

- `EpicEFI_Table_Interaction_Lab_CORE_COMPLETE_2D_3D.html`
- `EpicEFI_Table_Interaction_Lab_COMPLETE_FEATURE_INVENTORY_2D_3D_CORE.md`
- `EpicEFI_Table_Interaction_Lab_PORTABILITY_APPENDIX_2D_3D_CORE.md`
- `EpicHub_TABLE_INTERACTION_INTEGRATION_MAP_2D_3D_CORE.md`

Do not use the older 2D-only/core-only derivatives as implementation authority.

---

# Tuner implementation progression already completed

The following stacked Tuner slices are qualified and should be treated as established architecture rather than reimplemented.

## Semantic/editing foundation

- **PR #6** — offline semantic `TuneDraft` foundation
  - qualified head: `66fa1d898d69fa744fb85b256a441cba617b6998`
  - immutable `TuneSnapshot` baseline;
  - atomic semantic table edits;
  - preview/Apply separation;
  - revisioning;
  - Rust-owned Undo/Redo;
  - legal range / quantum / display precision separation.

- **PR #7** — typed read-only 2D/3D table projection
  - qualified head: `83bbdce431efffa6ced310b6a85beade42053ccb`
  - versioned variable-sized packed table ABI;
  - one representation-neutral projection used by both 2D and 3D.

- **PR #8** — shared selection + semantic preview transactions
  - qualified head: `b7aa4583b1590f44f81bf9fa479c0b15b136251a`
  - selection does not mutate tune state;
  - preview generated by Rust;
  - Apply is explicit and revision guarded.

- **PR #9** — rectangular multi-cell atomic selection/editing
  - qualified head: `e770a036a6caa7a97ec06d6ef2c12f57045aff42`
  - anchor/focus rectangle;
  - one rectangle becomes one semantic transaction/history entry;
  - invalid/protected target can reject the whole operation atomically.

- **PR #10** — typed numeric Set / Add / Scale
  - qualified head: `c31097d9ab29c1062309eb89ac5dbf27e098f937`
  - raw numeric text crosses JNI;
  - Rust parses and validates the operand;
  - Kotlin never becomes calibration-math authority.

## Table transforms

- **PR #11** — horizontal / vertical / 2D interpolation
  - qualified head: `f50c1bd6fde9e0869aa30bd1ac2d3f9b88b2ba8b`
  - interpolation uses actual X/Y axis coordinates, not equal grid index spacing;
  - 2D bilinear interpolation derives from selected corners;
  - one preview / one Apply / one history entry.

- **PR #12** — deterministic smoothing
  - qualified head: `1d2e1594842096df54c7e3491387d1daea008d66`
  - explicit 3×3 `[1 2 1] × [1 2 1]` kernel;
  - physical edge renormalization;
  - 0–100% blend strength;
  - immutable pre-transform source surface;
  - protected neighbors may contribute read-only data while selected protected targets reject mutation.

- **PR #13** — Brush/Pull semantic transforms
  - qualified head: `8282a2d4402ceec761f57246c19a7501e3e696ff`
  - Brush = swept additive stroke with radius/falloff;
  - Pull = focus-centered rubber-sheet deformation;
  - protected/pinned cells resist deformation;
  - 2D/3D preview highlighting changed to actual changed cells rather than selected rectangle.

## Shared input architecture

- **PR #14** — central Tuner command/input router
  - qualified head: `f5ff31146ee82542251e2c0dff7e68ea20d5b4ce`
  - keyboard, touch/buttons and later hardware controls converge on one intent model;
  - arrows / extend-selection / +/- / Apply / Cancel / Undo / Redo route through the same command layer;
  - text-input fields retain their own keyboard behavior.

- **PR #15** — continuous 2D Brush stroke capture
  - qualified head: `7da1572e06aeafd4c0592766f63e757fb9cf06d4`
  - raw motion is reduced to logical cell transitions;
  - router removes duplicates / collapses straight runs;
  - Rust independently canonicalizes geometry;
  - dense vs sparse samples of the same stroke produce the same semantic preview;
  - pointer release still does not auto-Apply.

## 3D editing and camera

- **PR #16** — accepted direct-3D semantic editing contract
  - qualified head: `892e4d52d5eb4633fe757e5183007fa878215cec`
  - 3D becomes editable input, never a second tune authority.

- **PR #17** — deterministic 3D hit testing + shared selection
  - qualified head: `65ba44d4b01f991e53e41fb29c72405d1c7fc679`
  - bounded nearest projected table vertex;
  - deterministic tie behavior;
  - `3D hit → SelectCell → TunerCommandRouter` parity with 2D.

- **PR #18** — 3D camera orbit / zoom
  - qualified head: `b1664a4130ece1785963eddd3053fe02553ab324`
  - bounded yaw/pitch/zoom;
  - drawing and hit testing share the same transformed geometry;
  - Inspect owns camera gestures; Select/Edit owns semantic selection taps;
  - camera state never emits tune mutation commands.

- **PR #19** — direct 3D Brush/Pull input
  - qualified head: `49b3dc965ced4e83038825445088894a4b2505f5`
  - 3D Brush reuses existing routed Brush commands;
  - Pull vertical drag maps to whole `editIncrement` steps;
  - 18 dp = one edit increment;
  - displacement bounded to ±64 steps;
  - sub-step movement produces no request;
  - returning to zero deadband clears prospective Pull preview;
  - gesture capture geometry remains committed/stable while the prospective preview surface may move visually;
  - no release-time auto commit.

---

# Engine-owned tune session — PR #20

**PR #20 — engine-owned TuneSession/TuneDraft authority** is qualified.

Exact qualified head:

`39bf98404e587af2ea0a81df85560dc4ea92b61d`

Important state:

- the process-global synthetic mutation fixture is no longer production authority;
- each `EngineState` owns its own `TuneSession`;
- `TuneSession` owns immutable baseline + mutable `TuneDraft` + revision/history;
- engine sessions are isolated from one another;
- the small synthetic table remains only as startup/fallback source data;
- old fixture JNI mutation modules are no longer compiled as production authority;
- native table operations are parameterized by semantic table ID rather than hardwiring fixture table `7`;
- the historical Kotlin `OfflineTunerFixture` name is only a compatibility facade into the active native engine/session.

Authority model:

```text
TuneSnapshot source
        ↓
    EngineState
        ↓
    TuneSession
    ├─ baseline
    └─ TuneDraft
        ↓
preview / Apply / Undo / Redo
```

Still explicitly:

```text
TuneDraft Apply != ECU RAM write != Burn
```

---

# Real Mega144H7 profile resolver — PR #21

**PR #21 — Tuner: add Mega144H7 resolved tune-page source** is qualified and remains open/draft/unmerged/mergeable.

Exact qualified head:

`9ac1d285e179e216da6e9209df58054f5e069a91`

Foundation run:

`38532426383` — PASS

## Profile identity

Initial authoritative resolver target:

- profile name: `353`
- signature: `rusEFI master.2026.08.26.MEGA144H7.2273317132`
- profile fingerprint: `1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa`
- page 1: 55,764 bytes
- page 2: 5,000 bytes
- little-endian tune storage
- `profile_generation` derives from stable firmware/profile build identity `2273317132`, not a particular tune capture.

## First real semantic tables

Stable EpicHub semantic IDs are independent of raw firmware offsets:

- `1001` — VE Table — 32×32 U16 values + U16 RPM/load axes;
- `1002` — Ignition Table — 32×32 signed S16 values + U16 RPM/load axes;
- `1003` — Target Lambda / AFR — 32×32 U8 values + U8 RPM / U16 load axes;
- `1004` — MAP Estimate — 16×16 U16 values + U8 RPM/TPS axes.

The resolver carries definition-authentic scaling/precision, explicit Lambda-vs-AFR target mode, bounds, quantum, axes and provenance into the existing semantic `TuneSnapshot` model.

The resolver is deliberately **source-neutral**: the caller supplies `TuneSnapshotOrigin`; decoder code does not guess whether page bytes came from an imported artifact or a future verified controller read.

The profile decoder does not fabricate unavailable table values.

## Evidence/source history

Research identified the real Mega144H7 `mainController.ini` and its TunerStudio table mappings. A historical stored tune was also located in project evidence as:

`CurrentTune(2)(1).msq` — 708,944 bytes — July 15, 2026.

The resolver itself does not make that MSQ a hidden runtime authority. Real page bytes must be supplied explicitly through a source-provider/import path.

---

# Atomic real-source installation — PR #22

**PR #22 — Tuner: install resolved sources atomically into TuneSession** is the current active qualified slice.

Exact qualified head:

`83165ec3a9cf59f2a4b9737e8b2ce4d7c8d0daca`

Foundation run:

`38534675238` — PASS

The full Foundation pipeline passed on this exact SHA:

- exact source-SHA verification;
- Rust format;
- Clippy with warnings-as-errors;
- Rust tests;
- Android JVM tests;
- debug APK;
- performance APK;
- Macrobenchmark harness;
- APK signature verification;
- both production ARM libraries;
- physical benchmark kit;
- all artifact publication gates.

## Implemented transaction semantics

New atomic source-install flow:

```text
source bytes
    ↓
profile decoder + full validation
    ↓
TuneSnapshot candidate
    ↓
atomic session replacement
    ↓
new TuneSession
 ├─ immutable baseline
 └─ new TuneDraft revision 0
```

Properties:

- full page decode/validation completes before mutation authority is obtained;
- successful install replaces baseline + `TuneDraft` together;
- successful source replacement resets draft revision to `0` and starts a fresh Undo/Redo history;
- failed decode preserves the previous active session, baseline, revision and history unchanged;
- source replacement is engine-local;
- imported Mega144H7 pages are tagged `TuneSnapshotOrigin::ImportedFile` rather than mislabeled as live controller state;
- after successful real-source install the synthetic semantic table ID `7` disappears from the active session;
- no partial source switch is permitted.

## JNI/source boundary

- coarse two-page JNI install;
- direct buffers;
- exact native capacity validation for 55,764-byte + 5,000-byte Mega144H7 pages;
- no per-value / per-cell JNI source transfer;
- invalid engine handles cannot create hidden sessions.

## Semantic table catalog

A packed, versioned catalog ABI now publishes the active session's:

- profile generation;
- current draft revision;
- source origin;
- semantic table ID;
- table dimensions;
- display precision;
- UTF-8 table name.

Ordering is deterministic by semantic table ID.

Kotlin includes a catalog decoder with malformed-range validation.

First resolved catalog after Mega144H7 source installation contains semantic IDs:

```text
1001 VE Table
1002 Ignition Table
1003 Target Lambda / AFR
1004 MAP Estimate
```

## Explicit non-goals retained

PR #22 does **not** add:

- controller RAM writes;
- Burn;
- live controller tune-page acquisition;
- MSQ XML parsing;
- automatic startup import;
- profile migration;
- source checkpoint/restore UI.

Source installation is not a table edit and is not placed in the old source's Undo stack.

Authority boundary:

```text
source install != TuneDraft Apply != ECU RAM write != Burn
```

---

# Current Tuner UX/runtime state

The Tuner remains safe to exercise without ECU-write authority.

Established functionality includes:

- shared 2D/3D table values;
- shared selection and rectangular multi-selection;
- protected/pinned cells;
- Set / Add / Scale;
- increment / decrement;
- H / V / 2D interpolation;
- deterministic smoothing;
- Brush / Pull;
- preview-before-Apply;
- revision-guarded Apply;
- Undo / Redo;
- shared 2D/3D prospective surface;
- central keyboard/touch command router;
- continuous 2D Brush gesture coalescing;
- 3D cell hit testing;
- 3D Inspect vs Select/Edit modes;
- 3D orbit / zoom;
- direct 3D Brush / Pull through the same semantic commands.

The previous downloadable PR #19 build intentionally showed:

`OFFLINE FIXTURE · NOT ECU APPLIED`

PR #20–#22 move the internal source authority beyond that fixture, but ECU RAM write/Burn still do not exist and must not be implied by UI wording.

---

# Performance foundation remains valid

Do not reopen the old placeholder-performance investigation unless a real product regression requires it.

Authoritative baseline:

`docs/PERFORMANCE_BASELINE_2026-10-10.md`

Benchmark methodology:

`docs/PERFORMANCE_BENCHMARKING.md`

Important retained rule:

**Do not reintroduce `ReusableContentHost` or retained inactive route composition.** Physical testing showed deterministic Compose `measure is called on a deactivated node` crashes. Conditional active-route composition is the qualified architecture.

Performance infrastructure remains a regression/profiling tool and should be re-run against real Tuner/Logger/Analyzer/Dashboard scenarios as those screens become production-real.

---

# Immediate next development priority

Continue from qualified PR #22 rather than revisiting earlier fixture or table-interaction work.

Recommended immediate slice:

## 1. Explicit source-provider / project import flow

Build an explicit user/project-owned source provider that can supply verified Mega144H7 tune pages into the qualified atomic source-install transaction.

Requirements:

- source acquisition/import is a separate authority from profile decoding;
- no hidden automatic fallback pretending historical MSQ bytes are current ECU state;
- provenance must remain explicit;
- import must fail closed;
- failed import must leave the active `TuneSession` untouched;
- source selection/import must not imply ECU RAM Apply/Burn.

Potential first providers:

- explicitly selected local/project tune source;
- an MSQ parser/converter that produces verified profile page bytes;
- later, verified live-controller page acquisition.

Do not make all three one slice.

## 2. Active semantic table browser

Expose the qualified semantic table catalog in Tuner UI and let the user select among the active real tables by semantic ID.

First expected Mega144H7 tables:

```text
1001 VE Table
1002 Ignition Table
1003 Target Lambda / AFR
1004 MAP Estimate
```

The existing 2D/3D table editor must then consume whichever table is active without a special code path per table.

## 3. Real-source editing qualification

Once explicit source import + table selection are wired, prove the existing editor against the real decoded tables:

- 32×32 VE;
- 32×32 signed ignition;
- Lambda/AFR scaling;
- 16×16 MAP Estimate;
- non-uniform axes;
- quantum/display-precision differences;
- protected/editability rules where present;
- 2D/3D state parity;
- preview / Apply / Undo / Redo after source install.

## 4. Only then begin controller-write authority

Controller RAM Apply and Burn must be a separate review/specification phase.

Required conceptual boundary:

```text
TuneDraft
   ↓
TuneDiff / mutation plan
   ↓
explicit guarded ECU RAM apply
   ↓
readback / reconciliation
   ↓
Burn
```

Do not conflate local `TuneDraft Apply` with controller Apply.

---

# Deferred / later work

- live ECU tune-page acquisition;
- guarded ECU RAM writes;
- readback/reconciliation after RAM write;
- Burn/persistence authority;
- MSQ XML import/export once isolated as an interoperability boundary;
- project source checkpoints / restore UI;
- profile migration across incompatible generations;
- production logger file formats;
- deep Analyzer implementation;
- native AE/PID/TCharge/Injection-Angle algorithms;
- BLE/accessory migration;
- physical wide/tablet qualification on SM-T500 after real UI exists;
- targeted performance requalification against real production screens.

---

# Do not regress these invariants

1. **One semantic tuning authority in Rust.**
2. **One engine-owned TuneSession per native engine.**
3. **2D and 3D are peer CORE representations of the same draft.**
4. **Input adapters emit semantic commands; they do not implement calibration math.**
5. **Preview is prospective and non-mutating until explicit Apply.**
6. **Apply is revision guarded and history is Rust-owned.**
7. **Profile definition, source acquisition, draft editing, ECU RAM write and Burn are separate authorities.**
8. **Source installation is atomic and resets source-specific draft/history only after complete validation.**
9. **Failed source installation preserves the previous session exactly.**
10. **Semantic table IDs are stable EpicHub identifiers, not raw firmware offsets.**
11. **Do not fabricate unavailable ECU/tune values.**
12. **TuneDraft Apply is not ECU RAM write and not Burn.**
13. **Do not reintroduce retained inactive Compose route trees / `ReusableContentHost`.**
14. **Do not treat TunerStudio/MLV/Table Interaction Lab quirks as design authority; preserve proven behavior while improving architecture.**
