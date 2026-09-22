# EpicHub Current Handoff

## Continuation instruction

Read this file completely first. Then verify the current branch/head/PR state in `PJawZK/EpicHub-EpicEFI-Android` and read the specifically referenced architecture documents before modifying the project.

Do not infer current EpicHub architecture from EpicDash-JZ or other reference repositories when this repository establishes a newer deliberate contract.

## Repository state

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Permanent integration branch: `main`

Active Phase 1 branch: `phase1/architecture-inventory`

Open draft PR: **#1 — Phase 1: architecture and responsibility inventory**

Architecture branch head immediately before this handoff refresh: `be01d6411cf7bb4f73aef09fd6470ffbd20066a2`.

This handoff commit itself advances the branch, so always verify the actual current branch/PR head when resuming.

`main` still contains only the clean-slate repository bootstrap. Do not merge PR #1 merely because it exists; Phase 1 remains active until its review gate is deliberately accepted.

## Current phase

**Phase 1 — cross-repository responsibility inventory and codebase architecture.**

No production Android/Rust scaffold yet.

The authoritative Phase 1 scope/completion gate is `docs/architecture/PHASE1_SCOPE.md`. Progress is summarized in `docs/architecture/PHASE1_PROGRESS.md`, and `docs/architecture/REVIEW_CHECKLIST.md` is the closure checklist.

## Current objective

Use qualified/reference repositories as evidence to produce a complete behavior/responsibility model and clean target EpicHub topology before implementation.

Historical files are not migration units. Transfer behavior, protocol semantics, safety invariants, test evidence and useful interaction knowledge into permanent descriptive responsibilities.

## Locked high-level architecture

Read `docs/architecture/DECISIONS.md` for the durable list through D-018.

Key decisions:

- new clean-slate Android application; no EpicDash-JZ install/update continuity requirement;
- Kotlin + Jetpack Compose owns Android-facing shell/UI/platform integration;
- Rust owns sustained high-volume channel/logging/analysis work and eventually semantic tuning after parity;
- Kotlin↔Rust boundary is coarse semantic operations/bounded snapshots, never per-channel/per-sample chatter;
- Android BLE/GATT remains Kotlin-owned;
- legacy ESP32 BLE↔CAN bridge and any future direct relay/GPIO capability are distinct;
- presentation supplies semantic intent and never raw ECU page/offset/byte authority;
- MDF4 is the planned long-term native measurement-format candidate behind EpicHub's own abstraction;
- live acquisition/channel path is proven first, MLG writer second for interoperability, MDF4 evaluated on the same workload after that;
- mobile log format decisions optimize practical storage size first, then I/O/CPU/thermal/recovery cost;
- one domain-neutral ChannelStore feeds Dashboard, Logger, Analyzer, calculations/warnings and relevant tuner analysis;
- UI semantic state and presentation interaction state are distinct; 3D table inspection is view-only unless separately accepted for editing;
- diagnostics are observational, not subsystem control authority;
- full semantic tune comparison requires exact authoritative profile identity; cross-profile comparison is conservative/read-only and cannot authorize migration or writes;
- repository documentation is durable project memory and this is the single current handoff file.

## Architecture documents

Start with `docs/architecture/README.md`.

Core mapping/contracts:

- `SOURCE_INVENTORY.md`
- `RESPONSIBILITY_INVENTORY.md`
- `RESPONSIBILITY_DESTINATIONS.md`
- `PROTOCOL_AUTHORITIES.md`
- `GAP_ANALYSIS.md`
- `CODEBASE_TOPOLOGY.md`
- `DEPENDENCY_RULES.md`
- `STATE_OWNERSHIP.md`
- `DATA_FLOW.md`
- `THREADING_MODEL.md`
- `KOTLIN_RUST_BOUNDARY.md`
- `ACTION_MODEL.md`
- `SAFETY_BOUNDARIES.md`
- `NAMING_RULES.md`
- `TEST_STRATEGY.md`

Deep-dive evidence:

- `ESP32_ACCESSORY_EVIDENCE.md`
- `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md`
- `UI_INTERACTION_EVIDENCE.md`
- `LOGGING_INTEROP_EVIDENCE.md`
- `DIAGNOSTICS_EVIDENCE.md`
- `TUNE_VIEWER_EVIDENCE.md`

Workflow/review:

- `DEVELOPMENT_WORKFLOW.md`
- `DECISIONS.md`
- `PHASE1_SCOPE.md`
- `PHASE1_PROGRESS.md`
- `REVIEW_CHECKLIST.md`

## Completed Phase 1 source/domain deep dives

### ECU USB / protocol / generated INI

Stable responsibility decomposition exists for:

- Android USB discovery/permission/open/CDC topology/control;
- one authoritative ECU session/generation;
- asymmetric framed request/response codecs;
- signature/probe/compatibility policy;
- generated-INI syntax/support/runtime/tune/tuner schemas;
- condition authority;
- runtime polling/decoding;
- complete TuneSnapshot Read;
- semantic verified RAM Write;
- explicit separate Burn and uncertainty handling.

Remaining work is mainly fixture/benchmark qualification, especially protocol goldens, broader genuine INI corpus and Kotlin-vs-native sustained-I/O benchmarking.

### ESP32 legacy bridge / Android BLE

`ESP32_ACCESSORY_EVIDENCE.md` locks:

- original device is BLE↔CAN bridge with local inputs, not direct relay controller;
- generic variable-set path replaces misleading GPS-only naming;
- one connection-wide Android GATT operation scheduler is the clean target;
- GATT completion is transport evidence, never downstream CAN/action confirmation;
- direct GPIO/relay control would be a separately versioned future capability with firmware-owned failsafes.

### Tuning persistence / recovery

`TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md` separates:

- project authority = exact source profile + complete verified TuneSnapshot;
- derived exact-identity-bound caches;
- portable backup representation;
- pre-transmission uncertainty journal;
- local unverified UI draft.

No cache/backup/journal/draft becomes alternate ECU/tune authority.

### Dashboard / Tuner interaction / responsive / performance

`UI_INTERACTION_EVIDENCE.md` establishes:

- accepted EpicDash-JZ visual/layout references remain authority;
- Epic-DashTune-UXUI contributes behavior/responsive/performance techniques only;
- Rust owns semantic truth; Compose owns gesture/layout/presentation/camera state;
- X/Y display reversal never mutates tune ordering;
- gesture arbitration is explicit;
- 3D inspection is view-only by current contract;
- static geometry/minimal dynamic invalidation is the transferable performance principle;
- project/live/draft/RAM-dirty/Burn/uncertain states remain visibly distinct.

### Logging / playback / MLG / MDF transition

`LOGGING_INTEROP_EVIDENCE.md` establishes:

```text
format-neutral live acquisition/channel path
→ MLG writer + optimization for TS/MLV interoperability
→ MDF4 evaluation/implementation on identical workload
→ scalable large-log Analyzer
```

File size is the first mobile storage metric. Large analysis is lazy/range-based with rebuildable indexes and viewport-sized spike-preserving plot data.

### Diagnostics / performance / lifecycle

`DIAGNOSTICS_EVIDENCE.md` and D-017 establish:

- diagnostics observe but never control production state transitions;
- events/warnings/metrics/lifecycle-owner traces are typed separate evidence;
- histories are bounded;
- hot-path measurements remain cheap;
- p50/p95/p99/max are qualification metrics;
- monotonic vs wall-clock roles are explicit;
- Kotlin owns platform diagnostics, Rust owns engine diagnostics;
- bounded snapshot/batch transfer across JNI.

### EpicEFI Tune Viewer

Reviewed source: `PJawZK/PJawZK-EpicEFI-Tune-Viewer` main `abfa325597d16cb362623c260ec92ed5bf5f37f1`, package `1.0.0-rc.1`.

`TUNE_VIEWER_EVIDENCE.md` establishes:

- exact definition/profile identity gates semantic interpretation; no nearest-version fallback;
- explicit scalar/bits/array/table/curve/dialog/menu types prevent accidental UI-surface guessing;
- viewer MSQ/INI/condition code is independent capability evidence, **not** a second EpicHub parser/tuning authority;
- compact gzip + SHA-256 exact-signature definition packs are a useful pattern for optional derived compiled-profile projections only;
- async definition/project-style loading rejects stale results by generation/active guards;
- full semantic comparison is exact-profile only;
- cross-profile comparison is informational and conservative, never edit/migration/write authority;
- changed table axes make positional cell deltas ambiguous; EpicHub defaults those to non-comparable unless an explicit qualified transform/regridding policy exists;
- definition structural diff is useful diagnostics, not migration authorization;
- comparison reports are derived evidence only;
- current Tune Viewer uses 2D calibration tables + SVG curves and has **no separate Plotly/3D renderer to port**.

D-018 locks the permanent comparison authority rule.

## Important unresolved decisions/evidence

Read `GAP_ANALYSIS.md` for the complete list. Major remaining categories include:

- Kotlin sustained USB bulk-I/O vs Android-opened native FD/handle to Rust benchmark;
- generated-INI/profile compatibility corpus breadth;
- ECU and BLE/CAN golden vectors;
- minimum Android API/ABI/device target;
- permanent Android application ID/namespace;
- MLG executable compatibility corpus;
- MDF4 version/compression interoperability and target-device benchmarks;
- project/backup/journal concrete formats;
- offline draft/reconciliation semantics;
- structured diagnostics schema/retention/export;
- exact saved-project tune-comparison identity tuple;
- changed-axis table regridding policy;
- optional compact compiled-profile projection performance decision;
- Compose physical/performance qualification;
- product scope for rich table edits/dashboard customization/accessory compatibility.

## Constraints / do not regress

- Do not add production Android/Rust scaffold during Phase 1.
- Do not copy historical large classes wholesale.
- Do not create a second INI parser/condition authority for viewer/comparison convenience.
- Do not use stage/version labels as permanent production names.
- Do not create alternate ECU Write/Burn paths.
- Do not treat USB VID/PID, board name, similar firmware version or matching parameter name as profile/tune authority.
- Do not route ESP32 GATT through Rust merely for architectural centralization.
- Do not make UI/dashboard the runtime telemetry authority.
- Do not treat diagnostics as hidden reconnect/queue/tuning control policy.
- Do not compare changed-axis table cells positionally as if their semantics were proven equal.
- Do not treat Tune Viewer as a 3D-renderer source; its useful evidence is semantic/identity/comparison behavior.
- Do not create additional handoff files.

## Exact next action

Continue Phase 1 with the **AE / PID / TCharge / Injection Angle plugin repositories**.

For each plugin family:

1. identify algorithm/workflow purpose independently of its TunerStudio UI shell;
2. inventory required input channels, sample/log assumptions, stable/invalid data gates and captured evidence;
3. identify generic analyzer primitives that should be shared by Rust rather than reimplemented per tuner;
4. separate measured evidence, derived statistics, proposed recommendations and any eventual write authority;
5. identify offline-log replay/golden fixtures so deterministic historical logs can qualify the same algorithm used for live/guided workflows;
6. preserve the user's preferred ability to reuse the same recorded session for related positive/negative analyses where technically valid;
7. update Phase 1 evidence/progress/gap documents without importing plugin-specific architecture or hardcoded context.

After that plugin review, stop broad archaeology by default and evaluate the explicit Phase 1 review gate. Remaining work should primarily be the documented design/fixture/benchmark gaps required before Phase 2 scaffolding.