# EpicHub Gap Analysis

## Purpose

This document lists **current unresolved implementation, fixture, benchmark, compatibility and product-scope work**.

Phase 1 is accepted/closed. The first Phase-2 Kotlin/Rust foundation is also implemented, CI-qualified and physically exercised on both reference devices. Completed work is not carried forward as a fake gap.

Authority for the active design slice:

- D-001 through D-023 in `DECISIONS.md`;
- `UI_FOUNDATION_LAYOUT.md`;
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md`;
- `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`;
- `docs/HANDOFF.md`.

---

# Completed foundation — not open gaps

The following are already accepted on `main`:

- Android/Compose application host;
- `com.epicefi.epichub` namespace;
- pinned reproducible Android/Rust build toolchain;
- `armeabi-v7a` + `arm64-v8a` native builds;
- coarse `NativeEngine` lifecycle;
- Rust panic containment at JNI;
- fixed/versioned packed RuntimeSnapshot ABI v1;
- reusable direct ByteBuffer snapshot transfer;
- minimal engine-owned Rust ChannelStore;
- reserved synthetic ChannelIds;
- synthetic Rust → RuntimeSnapshot → Kotlin → Compose path;
- committed Cargo.lock / `--locked` Rust builds;
- CI dual-ABI APK verification;
- physical execution on SM-A137F and SM-T500.

These items must not be described elsewhere as unimplemented Phase-2 foundation work.

---

# Current blocking design/implementation slice

Real ECU/USB integration is deliberately **not** next until the following foundation is accepted and qualified.

## Responsive application shell

Need production Compose implementation of the accepted structural model:

```text
Dashboard | Tuner | Logger | Analyzer | Diagnostics
```

with Settings globally accessible rather than a sixth high-frequency destination.

Need:

- wide/tablet rail layout;
- compact/bottom-navigation layout;
- shared status/header strip;
- placeholder-but-real surfaces for all primary destinations;
- route/subpage state that survives ordinary configuration changes;
- no separate phone/tablet application trees.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Ground screen layouts

Need initial functional shells for:

- Dashboard;
- Tuner;
- Logger with Live Logger + dedicated Trigger Logger;
- Analyzer with source/channel/workspace/inspector structure;
- Diagnostics with Overview/Events/Metrics/Sessions/Captures;
- Settings groups.

These shells establish regions/navigation/interaction ownership, not final decorative design or full feature depth.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Diagnostic event/metric foundation

Need:

- stable typed `DiagnosticEvent` schema;
- stable typed metric descriptors/IDs/units/kinds;
- bounded Kotlin event/metric storage;
- bounded Rust event/metric storage;
- lifecycle owner/session/generation tracing;
- correlation IDs;
- typed/packed bounded Rust→Kotlin diagnostic drain;
- invariant-violation events;
- diagnostics overhead metrics.

No JSON/CBOR runtime message bus is permitted under D-023.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Diagnostic problem capture

Need:

- always-on bounded pre-trigger flight recorder;
- recent-problem capture;
- manual reproduction capture;
- user markers;
- immutable finalized `DiagnosticCapture`;
- previous-exit/crash context where Android permits;
- global capture affordance reachable from every primary screen.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Diagnostic export

Canonical diagnostic export is locked to:

```text
EpicHub-Diagnostics-<captureId>.zip
├─ capture.cbor
├─ report.txt
└─ attachments/
```

Need:

- concrete versioned CBOR capture schema;
- bounds/integrity/version behavior;
- report formatter from the same immutable typed capture;
- explicit attachment selection/metadata/hashes;
- Android SAF/share flow;
- tests proving routine captures omit full logs/tunes/GPS/secrets.

CBOR is export/persistence only; live diagnostic state remains typed Kotlin/Rust state.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## UI/diagnostic physical qualification

Before real ECU integration, prove on SM-A137F + SM-T500:

- phone/tablet portrait/landscape reflow;
- navigation/state preservation;
- synthetic RuntimeSnapshot presentation through the new shell;
- diagnostics event/metric flow;
- recent-problem and reproduction capture;
- CBOR + report export;
- bounded memory/CPU overhead;
- capture/export does not block the UI or native engine.

Status: `CURRENT PHASE 2 PHYSICAL GATE`.

---

# Next inherited subsystem after current gate

## Read-only ECU acquisition

This begins only after the UI/diagnostics foundation above is accepted.

D-022 source-understanding gate must review and classify the relevant EpicDash-JZ/firmware behavior before implementation.

Candidate sustained-I/O strategies remain measurement-driven:

```text
A. Kotlin USB I/O/decode reference
B. Kotlin bulk I/O → coarse Rust batches/decode
C. Android open/claim → proven native FD/handle → Rust sustained I/O/decode
```

Measure:

- connect/signature/preflight latency;
- sustained bytes/frames per second;
- CPU/allocations/JNI cost;
- corrupt/dropped frames;
- unplug/replug/reconnect;
- lifecycle ownership/close correctness;
- thermal/battery behavior.

Status: `NEXT PHASE 2 SOURCE-REIMPLEMENTATION + BENCHMARK GATE`.

---

# ECU / generated-profile fixture gaps

## ECU protocol golden vectors

Need executable vectors for:

- plain/framed signature/query;
- runtime `O`;
- later tune `R/C/B`;
- typed status/errors;
- malformed CRC/length;
- page/range boundaries where applicable.

Status: `FEATURE-ENTRY FIXTURE GAP`.

## Generated-INI compatibility corpus

Need representative genuine generated INIs across supported boards/firmware generations covering runtime geometry, tune primitives, tables/curves/dialogs/menus, conditions, unsupported/malformed cases and provenance.

Status: `PROFILE/TUNER FEATURE-ENTRY FIXTURE GAP`.

---

# Tuning / project persistence gaps

Authority is resolved; implementation details remain future feature-entry work.

Need:

- versioned internal project store for exact profile source + complete verified TuneSnapshot + identity/generation metadata;
- portable backup format;
- batch-capable uncertainty journal schema;
- offline semantic draft/reconnect reconciliation behavior;
- persistence/process-death/fault-injection corpus.

All future codecs follow D-023: typed runtime/domain state first; serialization only at explicit persistence/export boundaries. JSON is not selected by default.

Status: `TUNER FEATURE-ENTRY WORK`.

---

# Tune comparison gaps

Need later:

- exact persisted comparison identity tuple;
- same-profile/cross-profile golden fixtures;
- optional transformed changed-axis comparison only under explicit regridding policy;
- optional compact compiled-profile cache only if benchmarks justify it.

Status: `COMPARE/ANALYSIS FEATURE-ENTRY WORK`.

---

# Logging / measurement gaps

The locked order remains:

```text
live acquisition path
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

Need later:

- format-neutral `MeasurementWriter` sample/event batch path;
- genuine MLG compatibility corpus;
- Android hot-recording storage/retention policy;
- writer power-loss/recovery qualification;
- MDF4 version/compression/interoperability benchmarks.

Status: `LOGGER/MEASUREMENT FEATURE-ENTRY WORK`.

---

# Analyzer / autotune gaps

Responsibility architecture is already resolved by D-019/D-020 and `ANALYSIS_RESPONSIBILITY_MAP.md`.

Need later:

- concrete versioned `AnalysisSession` representation;
- domain-profile trait/interface;
- typed readiness/comparability/identifiability/result vocabulary;
- session persistence/indexing policy;
- semantic `ExperimentProposal` handoff to the sole tuning authority;
- deterministic replay/golden corpus;
- product rollout priority for AE/PID/TCharge/Injection Angle profiles.

Status: `ANALYZER FEATURE-ENTRY WORK`.

---

# ESP32 legacy bridge gaps

Need later as product scope requires:

- decide retained bridge compatibility features;
- physically verify disconnect/button-release behavior before altering it;
- BLE/CAN golden vectors;
- separate versioned direct-output protocol only if direct relay/GPIO control is deliberately introduced.

Status: `DEFERRED ACCESSORY WORK`.

---

# Deferred UI/product scope

Not required for the current shell/diagnostics gate:

- rich table arithmetic/fill/smoothing/interpolation feature set;
- dashboard customization/favorites scope;
- specialized GPU renderer unless Compose measurements justify it;
- cross-profile table regridding;
- later analyzer profile rollout order.

---

# Closed architecture questions

Do not reopen without new contradictory evidence:

- Kotlin/Compose owns Android/UI; Rust owns native high-volume engine work;
- runtime state is typed Kotlin/Rust state;
- no WebView/JavaScript runtime architecture;
- no JSON/CBOR runtime/JNI message bus;
- serialization is explicit-boundary-only under D-023;
- one ECU session and one ChannelStore;
- UI cannot own raw tune memory or semantic tune authority;
- exact profile/signature authority rules;
- Burn is separate/explicit;
- legacy ESP32 is not a direct relay controller;
- GATT completion is not downstream action confirmation;
- diagnostics/warnings are observational;
- historical code is reimplemented cleanly under D-022;
- logging/analyzer architecture is format-neutral rather than MDF-specific.

## Current gate

The current development gate is **PR #3 UI + diagnostics design acceptance**, followed by implementation/physical qualification on the existing synthetic foundation. Only then proceed to read-only live ECU acquisition.