# EpicHub Gap Analysis

## Purpose

This document lists **current unresolved implementation, fixture, benchmark, compatibility and product-scope work** after Phase 1 architecture archaeology.

Resolved responsibility archaeology is intentionally not repeated here. See the evidence documents and `PHASE1_GATE_ASSESSMENT.md` for closure rationale.

A remaining gap does not necessarily block Phase 2 scaffolding. Each item is classified by when it becomes required.

---

# Phase 1 closure status

Broad cross-repository archaeology is complete enough to stop by default.

Resolved before the Phase 2 gate:

- Android-only Kotlin/Compose + Rust architecture;
- one ECU session and one runtime ChannelStore;
- exact generated-profile authority;
- complete verified TuneSnapshot / semantic Write / separate Burn authority;
- one connection-wide Android BLE/GATT scheduler;
- legacy ESP32 bridge vs future direct-output separation;
- format-neutral live measurement path;
- MLG-first then MDF4 evaluation sequence;
- diagnostics as observational state only;
- exact-profile tune-comparison authority;
- shared analyzer infrastructure + domain profiles;
- permanent application ID/namespace `com.epicefi.epichub`;
- initial Android API baseline `minSdk 26`, `targetSdk 36`, `compileSdk 37`;
- production native ABIs `armeabi-v7a` + `arm64-v8a`.

See D-001 through D-021 and `ANDROID_BUILD_BASELINE.md`.

The only Phase 1 review-checklist item still intentionally open is explicit user acceptance before production scaffolding.

---

# Early Phase 2 foundation work

These are implementation tasks, not unresolved architecture ownership.

## Kotlin/Rust engine boundary

Still required:

- lifecycle-safe opaque engine handle;
- small semantic JNI API;
- versioned packed/shared RuntimeSnapshot buffer;
- bounded event drain;
- stable error projection;
- JNI metrics/counters;
- no per-channel/per-sample JNI traffic;
- explicit lifetime contract for any future native USB resource handoff.

Status: `PHASE 2 FOUNDATION IMPLEMENTATION`.

## Rust ChannelStore

Still required:

- stable semantic ChannelId/schema;
- typed raw/physical values where appropriate;
- timestamp/revision/source/validity;
- live/file-backed source abstraction;
- bounded history/cache rules;
- coarse GPS/legacy bridge ingestion.

Diagnostics-Core contributes naming/adapter/validity/test-vector evidence but is not a second runtime authority.

Status: `PHASE 2 FOUNDATION IMPLEMENTATION`.

## Compose shell

Still required:

- one application/Activity/Compose host;
- typed navigation/state;
- responsive phone/tablet/portrait/landscape behavior;
- frame-rate-aware snapshot consumption;
- physical frame/jank/lifecycle qualification.

No WebView/JSON hot path returns in the clean architecture.

Status: `PHASE 2 FOUNDATION IMPLEMENTATION`.

---

# Measured architecture PoCs

These are deliberately unresolved until measurement. They do **not** reopen Phase 1 ownership.

## ECU sustained runtime I/O after Android open

Candidates:

```text
A. Kotlin bulk I/O worker → coarse Rust batches
B. Android open/claim → proven native FD/handle → Rust sustained I/O
```

Measure on identical ECU/profile/device workload:

- connect/signature/preflight latency;
- sustained bytes/frames per second;
- application CPU;
- allocations/GC;
- JNI calls/bytes;
- dropped/corrupt frames;
- unplug/replug/reconnect;
- lifecycle/background/foreground behavior;
- ownership/close correctness;
- extended logging temperature/battery.

Status: `EARLY PHASE 2 BENCHMARK DECISION`.

## Compose renderer/runtime snapshot performance

Need physical measurements for:

- high-rate dashboard drawing;
- inactive-page update suppression;
- 32×32 table interaction/heat map;
- pinch/long-press arbitration;
- orientation state preservation;
- view-only 3D rotation;
- plot pan/zoom;
- p50/p95/p99 frame time and jank under live engine/logging load.

Specialized OpenGL/Vulkan rendering is not introduced unless these measurements justify it.

Status: `PHASE 2 PERFORMANCE GATE`.

## Dual-ABI native qualification

Need physical `Build.SUPPORTED_ABIS` capture plus JNI smoke/performance checks on at least:

- SM-A137F;
- SM-T500.

Both `armeabi-v7a` and `arm64-v8a` remain production-supported until evidence deliberately removes a requirement.

Status: `PHASE 2 BUILD/PHYSICAL GATE`.

---

# ECU / generated-profile fixture gaps

## ECU protocol golden vectors

Need executable vectors for:

- plain/framed signature/query;
- runtime `O`;
- tune read `R`;
- chunk write `C`;
- Burn `B`;
- typed status/errors;
- malformed CRC/length;
- boundary page/range cases.

Current firmware behavior has been directly verified in `ECU_PROTOCOL_DIRECT_VERIFICATION.md`; this gap is executable regression evidence, not protocol discovery.

Status: `FEATURE-ENTRY FIXTURE GAP`.

## Generated-INI compatibility corpus

Need representative genuine generated INIs across supported boards/firmware generations, including:

- exact signatures;
- differing runtime/output geometry;
- tune pages/primitives;
- tables/curves/dialogs/menus;
- condition constructs;
- malformed/unsupported fail-closed cases;
- provenance/redistribution status.

Status: `PROFILE/TUNER FEATURE-ENTRY FIXTURE GAP`.

---

# Tuning / project persistence implementation gaps

Authority is already resolved; concrete formats/behavior remain.

## Internal project store

Need a versioned schema/layout for:

```text
exact source profile
+ complete verified TuneSnapshot
+ identity/checksum/generation metadata
```

with transactional commit/rollback.

Status: `TUNER FEATURE-ENTRY IMPLEMENTATION DESIGN`.

## Portable backup format

Need a compact versioned archive preserving project authority and required integrity/provenance metadata.

Kotlin owns SAF/document transport; Rust owns schema/integrity/validation.

Status: `TUNER FEATURE-ENTRY FORMAT DESIGN`.

## Operation uncertainty journal

Need the final batch-capable schema for operation kind/phase, exact ECU/profile/session identity, baseline/expected tune fingerprints, write-plan/affected-page identity and Burn evidence where relevant.

Lifecycle/authority are already locked: durable before possible mutation, present/corrupt blocks mutation, no automatic replay.

Status: `TUNER FEATURE-ENTRY IMPLEMENTATION DESIGN`.

## Offline draft/reconciliation

Need product behavior for:

- saved-project edits while ECU is offline;
- reconnect comparison/conflict handling;
- software draft dirty state vs verified ECU RAM dirty pages vs Burn state.

Status: `TUNER PRODUCT DESIGN`.

## Persistence fault corpus

Need interrupted commit/cache/backup/journal/process-death/profile-mismatch tests.

Status: `TUNER FEATURE-ENTRY TEST GAP`.

---

# Tune comparison / profile projection gaps

## Exact persisted comparison identity

Define the tuple used to prove same semantic profile/project context, including source/compiled profile fingerprints and tune/project generation needed to invalidate stale derived results.

Status: `COMPARE FEATURE-ENTRY IMPLEMENTATION DESIGN`.

## Same-profile / cross-profile goldens

Need executable fixtures proving:

- scalar/bits/arrays/curves/table comparison for exact-profile cases;
- different scale/unit/storage/options/dimensions fail conservatively cross-profile.

Status: `COMPARE FEATURE-ENTRY FIXTURE GAP`.

## Changed-axis table regridding

Default remains `NON_COMPARABLE/AMBIGUOUS` when X/Y axes differ.

If transformed comparison is later implemented, it needs explicit interpolation/extrapolation/order/unit/tolerance/provenance rules. Transformed values remain derived analysis, never implicit tune/write data.

Status: `DEFERRED ANALYSIS FEATURE`.

## Compact compiled-profile projection

Only persist a compact hashed compiled-profile cache if Android startup/search/project-open measurements justify it.

If used: versioned, exact-source-bound, integrity-checked, rebuildable and never alternate authority.

Status: `DEFERRED PERFORMANCE/FORMAT DECISION`.

---

# Logging / measurement gaps

## Format-neutral writer path

Need bounded sample/event batches, explicit pressure/drop metrics and independent writer lifecycle.

Status: `LOGGER IMPLEMENTATION`.

## MLG executable compatibility corpus

Need authoritative/genuine fixtures for:

- structure/metadata;
- channel definitions;
- record/timestamp behavior;
- finalization;
- truncation/recovery behavior where applicable;
- TunerStudio/MegaLogViewer compatibility.

Do not derive the writer from one log file or repeated-block appearance alone.

Status: `MLG FEATURE-ENTRY FIXTURE/INTEROP GAP`.

## Android hot recording storage policy

Need app-controlled sequential hot-storage path, retention/quota behavior and user export/share policy. SAF stays outside the hot writer loop.

Status: `LOGGER PRODUCT/PLATFORM DESIGN`.

## Writer power-loss recovery

Need actual checkpoint/finalization/recovery implementation and fault-injection tests.

Status: `LOGGER IMPLEMENTATION/TEST GAP`.

## MDF4 version/compression baseline

Candidates remain measurement-driven:

- MDF 4.2 + Deflate;
- MDF 4.3 + LZ4;
- MDF 4.3 + Zstd.

Evaluate on the exact same sample/channel workload after the live path and MLG writer are proven.

Primary metric: practical mobile file size. Then sustained write/read rate, CPU/thermal/battery, recovery and independent interoperability.

Status: `DELIBERATELY DEFERRED BENCHMARK/INTEROP DECISION`.

---

# Analyzer / autotune gaps

Responsibility architecture is resolved by D-019/D-020, `ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md` and `ANALYSIS_RESPONSIBILITY_MAP.md`.

## AnalysisSession schema

Need concrete versioned representation of:

- immutable source/live session identity;
- exact profile identity;
- relevant calibration fingerprint;
- semantic channel bindings;
- algorithm/profile version;
- query/capture window;
- accepted/rejected evidence summaries;
- derived results/model state.

Status: `ANALYZER FEATURE-ENTRY IMPLEMENTATION DESIGN`.

## Domain-profile interface

Need the concrete trait/API for:

- required/optional channels;
- causal prerequisites;
- validity/rejection;
- segment/event extraction;
- comparability;
- metrics/model;
- confidence/identifiability;
- optional recommendation/experiment proposal.

Status: `ANALYZER FEATURE-ENTRY IMPLEMENTATION DESIGN`.

## Typed analysis status vocabulary

Need a stable common subset without flattening domain-specific semantics. Candidate concepts include:

- evidence readiness;
- comparability;
- identifiability;
- validation support;
- recommendation eligibility;
- improved/similar/regressed/insufficient/not-comparable.

Status: `ANALYZER FEATURE-ENTRY MODEL DESIGN`.

## Analysis-session persistence/indexing

Decide whether/how completed immutable analysis sessions are stored beside source logs versus rebuilt deterministically.

Status: `ANALYZER PRODUCT/PERSISTENCE DESIGN`.

## ExperimentProposal contract

Need the semantic handoff from analysis to sole tuning authority. Analyzer never receives raw page/offset/byte or Burn authority.

Status: `ACTIVE-EXPERIMENT FEATURE-ENTRY DESIGN`.

## Deterministic replay corpus

Build replay/golden fixtures from validated historical logs/evidence where provenance/storage permits.

Include missing/stale/contaminated, non-comparable, boundary-limited, quantized/unreachable, timeout and regression/Restore cases.

Status: `ANALYZER FIXTURE GAP`.

## Profile rollout priority

Choose which AE/PID/TCharge/Injection Angle analyses enter early product releases only after generic log/analyzer foundations are proven.

Status: `DEFERRED PRODUCT PRIORITIZATION`.

---

# Diagnostics gaps

## Event/metric schema

Need stable event codes, typed fields, metric IDs/units/kinds, correlation/session identity and ABI/export representation.

Status: `DIAGNOSTICS IMPLEMENTATION DESIGN`.

## Retention/persistence

Need exact event-ring/metric-window sizes, warning/incident persistence scope and previous-run crash/uncertainty summary policy.

Status: `DIAGNOSTICS PRODUCT/IMPLEMENTATION DESIGN`.

## Report/export schema

Need versioned machine-readable snapshot/export plus human-readable rendering and Android export flow.

Status: `DIAGNOSTICS FORMAT/IMPLEMENTATION DESIGN`.

Diagnostics-Core may seed semantic IDs/warning vectors after deliberate review; it never becomes runtime/control authority.

---

# ESP32 legacy bridge gaps

## Legacy compatibility product scope

Decide which compatibility features remain user-facing requirements:

- button bridge;
- CAN variable request/response;
- generic variable-set;
- virtual ADC;
- local ESP32 inputs.

Status: `DEFERRED PRODUCT-SCOPE DECISION` unless needed by an early target build.

## Button disconnect/release semantics

Verify downstream ECU/button-box consumer behavior before changing the legacy disconnect behavior.

Status: `COMPATIBILITY/PHYSICAL GAP`.

## BLE/CAN golden vectors

Need vectors for button, variable request/response, partial timeout, variable-set and malformed cases.

Status: `ACCESSORY FEATURE-ENTRY FIXTURE GAP`.

## Direct GPIO/relay control

Not inherited from the legacy bridge.

If ever added, requires a separate versioned capability, semantic IDs, application ACK/state/fault feedback and firmware-owned boot/interlock/watchdog/disconnect failsafe.

Status: `DEFERRED NEW CAPABILITY`.

---

# UI product-scope decisions

These do not block the core Compose architecture.

## Rich table-edit operations

Candidates include arithmetic/fill/flatten, smoothing, interpolation, copy/paste, undo/redo, baseline revert and CSV import/export.

Status: `DEFERRED PRODUCT-SCOPE DECISION`.

## Dashboard customization

Candidates include per-gauge display limits/precision, favorites/Quick Tuning and logging presets.

Status: `DEFERRED PRODUCT-SCOPE DECISION`.

---

# What no longer counts as a Phase 1 gap

The following are deliberately closed and must not be reopened without new contradictory evidence:

- whether Kotlin/Compose or Rust owns Android APIs;
- whether UI may own raw tune memory;
- whether board name/VID/PID may replace exact profile authority;
- whether dashboard owns telemetry;
- whether request/response framing is symmetric;
- whether Burn is an ordinary edit side effect;
- whether the legacy ESP32 is already a direct relay controller;
- whether GATT completion proves downstream action;
- whether diagnostics can silently control transport/tuning;
- whether Tune Viewer supplies a 3D engine;
- whether cross-profile same-name tables/curves are automatically comparable;
- whether AE/PID/TCharge/Injection Angle should be ported as independent mini-app/plugin architectures;
- whether guided capture is required when an existing immutable log already contains qualifying evidence;
- whether interpolated/model guidance may count as direct observation;
- whether arm64-only is a safe initial build assumption for the existing device matrix;
- whether application ID/API/ABI support is still an unmade pre-Phase-2 decision.

## Current Phase 1 gate

See `PHASE1_GATE_ASSESSMENT.md` and `REVIEW_CHECKLIST.md`.

Architecture/source/test review is ready for explicit user acceptance. Production scaffolding remains blocked until that final acceptance is recorded.