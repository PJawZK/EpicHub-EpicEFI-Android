# EpicHub Gap Analysis

## Purpose

This document lists **current unresolved implementation, fixture, benchmark, compatibility and product-scope work**.

Phase 1 is accepted/closed. The first Phase-2 Kotlin/Rust foundation is also implemented, CI-qualified and physically exercised on both reference devices. Completed work is not carried forward as a fake gap.

Authority for the active design slice:

- D-001 through D-024 in `DECISIONS.md`;
- `UI_FOUNDATION_LAYOUT.md`;
- `TUNING_TOOLS_FOUNDATION.md`;
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md`;
- `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`;
- `docs/HANDOFF.md`.

---

# Completed foundation — not open gaps

Already accepted on `main`:

- Android/Compose application host;
- `com.epicefi.epichub` namespace;
- pinned reproducible Android/Rust toolchain;
- `armeabi-v7a` + `arm64-v8a` native builds;
- coarse `NativeEngine` lifecycle;
- Rust panic containment at JNI;
- fixed/versioned RuntimeSnapshot ABI v1;
- reusable direct ByteBuffer transfer;
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

Need production Compose implementation of the structural model:

```text
Dashboard | Tuner | Tuning Tools | Logger | Analyzer | Diagnostics
```

with Settings globally accessible.

Need:

- wide/tablet rail layout;
- compact navigation that keeps all six functional destinations reachable without undersized controls;
- shared status/header strip;
- placeholder-but-real surfaces for all functional destinations;
- route/subpage state surviving ordinary configuration changes;
- no separate phone/tablet application trees.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Ground screen layouts

Need initial functional shells for:

- Dashboard;
- Tuner;
- Tuning Tools with visible AE/PID/TCharge/Injection-Angle entries and honest maturity states;
- Logger with Live Logger + dedicated Trigger Logger;
- Analyzer with source/channel/workspace/inspector structure;
- Diagnostics with Overview/Events/Metrics/Sessions/Captures;
- Settings groups.

These establish regions/navigation/interaction ownership, not full feature depth.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Dashboard / Logger visual authority

The user is developing HTML/CSS prototypes for Dashboard and Logger to iterate visual design quickly.

Need:

- allow those prototypes to mature before independently inventing large amounts of final Compose styling;
- review/accept explicit visual/layout references;
- translate accepted intent into Compose rather than embedding a WebView/browser runtime;
- physically compare Compose results against the accepted references on phone/tablet.

Prototype JavaScript/mock data is design-only and gains no runtime authority under D-023.

Status: `CURRENT UI DESIGN INPUT`.

## Native Tuning Tools shell

D-024 resolves the architecture: historical plugins become native tools, not a plugin runtime.

Need now:

- real Tuning Tools destination/workspace in the shell;
- initial tool cards for AE/PID/TCharge/Injection Angle;
- maturity/unavailable state presentation;
- shared source/profile/session context strip;
- tool workspace + evidence/details layout;
- diagnostics hooks for tool workflow state.

Deep algorithms are not required for this shell gate.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Diagnostic event/metric foundation

Need:

- typed `DiagnosticEvent` schema;
- typed metric descriptors/IDs/units/kinds;
- bounded Kotlin and Rust event/metric storage;
- lifecycle owner/session/generation tracing;
- correlation IDs;
- typed/packed bounded Rust→Kotlin diagnostic drain;
- invariant-violation events;
- diagnostics overhead metrics.

No JSON/CBOR runtime message bus under D-023.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Diagnostic problem capture

Need:

- always-on bounded pre-trigger flight recorder;
- recent-problem capture;
- manual reproduction capture;
- user markers;
- immutable finalized `DiagnosticCapture`;
- previous-exit/crash context where Android permits;
- global capture affordance reachable from every normal destination.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## Diagnostic export

Canonical export:

```text
EpicHub-Diagnostics-<captureId>.zip
├─ capture.cbor
├─ report.txt
└─ attachments/
```

Need concrete versioned CBOR schema, bounds/integrity/version behavior, report formatting from the same immutable capture, explicit attachment selection/hashes, Android SAF/share flow and privacy tests.

CBOR remains export/persistence only.

Status: `CURRENT PHASE 2 IMPLEMENTATION`.

## UI/diagnostic physical qualification

Before real ECU integration, prove on SM-A137F + SM-T500:

- phone/tablet portrait/landscape reflow;
- navigation/state preservation across all six destinations;
- synthetic RuntimeSnapshot presentation through the new shell;
- Tuning Tools placeholder/workspace usability;
- diagnostics event/metric flow;
- recent-problem and reproduction capture;
- CBOR + report export;
- bounded memory/CPU overhead;
- capture/export does not block UI/native engine;
- accepted Dashboard/Logger visual references translate acceptably to Compose.

Status: `CURRENT PHASE 2 PHYSICAL GATE`.

---

# Next inherited subsystem after current gate

## Read-only ECU acquisition

Begins only after the UI/Tuning-Tools/diagnostics foundation above is accepted.

D-022 source-understanding gate must review/classify relevant EpicDash-JZ/firmware behavior first.

Candidate sustained-I/O strategies remain measurement-driven:

```text
A. Kotlin USB I/O/decode reference
B. Kotlin bulk I/O → coarse Rust batches/decode
C. Android open/claim → proven native FD/handle → Rust sustained I/O/decode
```

Measure connect/signature/preflight latency, sustained throughput, CPU/allocations/JNI cost, corrupt/dropped frames, unplug/replug/reconnect, lifecycle/close correctness and thermal/battery behavior.

Status: `NEXT PHASE 2 SOURCE-REIMPLEMENTATION + BENCHMARK GATE`.

---

# ECU / generated-profile fixture gaps

Need executable protocol golden vectors for plain/framed signature/query, runtime `O`, later tune `R/C/B`, typed status/errors, malformed CRC/length and relevant range boundaries.

Need representative genuine generated-INIs across supported boards/firmware generations covering runtime geometry, tune primitives, tables/curves/dialogs/menus, conditions, unsupported/malformed cases and provenance.

Status: `FEATURE-ENTRY FIXTURE GAPS`.

---

# Tuning / project persistence gaps

Authority is resolved; implementation remains future feature-entry work.

Need versioned project store for exact profile source + complete verified TuneSnapshot + identity/generation metadata, portable backup, uncertainty journal, offline draft/reconnect reconciliation and persistence/process-death/fault-injection corpus.

All codecs follow D-023: typed runtime first, serialization only at explicit boundaries.

Status: `TUNER FEATURE-ENTRY WORK`.

---

# Tune comparison gaps

Need later exact persisted comparison identity, same-/cross-profile goldens, optional changed-axis transforms only under explicit policy and optional compiled-profile cache only if benchmarks justify it.

Status: `COMPARE/ANALYSIS FEATURE-ENTRY WORK`.

---

# Logging / measurement gaps

Locked order remains:

```text
live acquisition path
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

Need later format-neutral writer path, genuine MLG corpus, Android recording storage/retention policy, power-loss/recovery qualification and MDF4 interoperability/compression benchmarks.

Status: `LOGGER/MEASUREMENT FEATURE-ENTRY WORK`.

---

# Analyzer / Tuning Tools depth gaps

Responsibility architecture is resolved by D-019/D-020/D-024, `ANALYSIS_RESPONSIBILITY_MAP.md` and `TUNING_TOOLS_FOUNDATION.md`.

Need later:

- concrete versioned `AnalysisSession` representation;
- domain-profile trait/interface;
- typed readiness/comparability/identifiability/result vocabulary;
- session persistence/indexing policy;
- semantic `ExperimentProposal` handoff to tuning authority;
- deterministic replay/golden corpus;
- per-tool D-022 source review and clean Rust implementation;
- product rollout/qualification depth for AE/PID/TCharge/Injection Angle.

The tools are native EpicHub functionality, not dynamically loaded plugins.

Status: `TUNING TOOLS / ANALYSIS FEATURE-ENTRY WORK`.

---

# ESP32 legacy bridge gaps

Need later as product scope requires: retained compatibility decisions, physical disconnect/button-release verification, BLE/CAN goldens, and a separate versioned direct-output protocol only if direct relay/GPIO control is deliberately introduced.

Status: `DEFERRED ACCESSORY WORK`.

---

# Deferred UI/product scope

Not required for the current shell/diagnostics gate:

- rich table arithmetic/fill/smoothing/interpolation depth;
- dashboard customization/favorites depth beyond accepted visual shell;
- specialized GPU renderer unless Compose measurements justify it;
- cross-profile table regridding;
- deep Tuning Tool algorithm rollout order.

---

# Closed architecture questions

Do not reopen without contradictory evidence:

- Kotlin/Compose owns Android/UI; Rust owns native high-volume engine work;
- runtime state is typed Kotlin/Rust state;
- design-only HTML/CSS/JavaScript prototypes are allowed but are translated to Compose;
- no WebView/JavaScript production runtime architecture;
- no JSON/CBOR runtime/JNI message bus;
- serialization is explicit-boundary-only under D-023;
- one ECU session and one ChannelStore;
- UI cannot own raw tune memory or semantic tuning authority;
- exact profile/signature authority rules;
- Burn is separate/explicit;
- legacy ESP32 is not a direct relay controller;
- GATT completion is not downstream action confirmation;
- diagnostics/warnings are observational;
- historical code is reimplemented cleanly under D-022;
- historical TunerStudio plugins become native Tuning Tools under D-024;
- Analyzer is generic exploration while Tuning Tools owns guided domain workflows;
- logging/analyzer architecture is format-neutral rather than MDF-specific.

## Current gate

The current development gate is **PR #3 UI + Tuning Tools + diagnostics design acceptance**, followed by implementation/physical qualification on the existing synthetic foundation. Only then proceed to read-only live ECU acquisition.