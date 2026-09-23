# EpicHub Gap Analysis

## Purpose

This document lists **current unresolved implementation, fixture, benchmark, compatibility and product-scope work**.

Phase 1 is accepted/closed. PR #2's Kotlin/Rust foundation is accepted on `main`. PR #3's UI/diagnostics implementation is software-qualified on its branch but still awaits physical qualification/acceptance.

Authority for the active slice:

- D-001 through D-024 in `DECISIONS.md`;
- `UI_FOUNDATION_LAYOUT.md`;
- `TUNING_TOOLS_FOUNDATION.md`;
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md`;
- `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`;
- `docs/HANDOFF.md`.

---

# Completed foundation — not open gaps

Accepted on `main`:

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
- committed Cargo.lock / `--locked` builds;
- CI dual-ABI APK verification;
- physical execution on SM-A137F and SM-T500.

Do not relist these as unfinished work.

---

# Implemented on PR #3 — awaiting physical acceptance

The following are implemented and automated-test/CI qualified on `phase2/ui-diagnostics-foundation`; they are **not implementation gaps**, but they are not accepted onto `main` until the current physical gate passes.

## Responsive application shell

Implemented:

```text
Dashboard | Tuner | Tuning Tools | Logger | Analyzer | Diagnostics
```

plus global Settings.

Current shell includes:

- tablet/wide navigation rail;
- compact/phone bottom navigation;
- compact-safe two-row status/capture header;
- structural screens for all six destinations;
- synthetic RuntimeSnapshot presentation;
- common capture/settings affordances.

Compact six-destination navigation remains subject to physical usability review; grouping/secondary navigation may still be introduced if the phone layout proves crowded.

## Native Tuning Tools shell

Implemented structural home for:

- Acceleration Enrichment;
- PID Controller;
- TCharge;
- Injection Angle;

with explicit maturity/availability presentation.

Deep algorithms are intentionally not implemented yet.

## Diagnostic event/metric foundation

Implemented:

- process-scoped typed `DiagnosticHub`;
- bounded Kotlin event ring;
- explicit Kotlin dropped-event metric;
- bounded Rust engine event ring;
- explicit Rust dropped-event total;
- fixed/versioned 656-byte Rust→Kotlin diagnostic batch ABI v1;
- one coarse JNI drain, no per-event callbacks;
- typed native decoder/ingestion;
- metric current/min/max/p50/p95/p99;
- Activity and NativeEngine generation tracing;
- bounded destroyed-owner history;
- invariant/late-owner evidence.

## Diagnostic problem capture

Implemented:

- bounded pre-trigger flight-recorder evidence;
- recent-problem capture;
- post-trigger completion;
- reproduction capture;
- user markers;
- immutable finalized captures;
- process-scoped recorder so capture state/evidence survives Activity recreation.

## Diagnostic export

Implemented default routine bundle:

```text
EpicHub-Diagnostics-<captureId>.zip
├─ capture.cbor
└─ report.txt
```

with:

- versioned CBOR encoder;
- report generated from the same immutable capture;
- secure FileProvider sharing;
- tests proving default archive entry set;
- no JSON/CBOR live runtime/JNI path.

Automated qualification covers the above plus dual-ABI APK build/inspection.

---

# Current blocking gate

## UI/diagnostic physical qualification

Before PR #3 acceptance/merge, prove the exact qualified APK on both:

- Samsung SM-A137F;
- Samsung SM-T500.

Verify:

- install/launch;
- all six destinations reachable;
- compact phone header/navigation has no clipping or unusable controls;
- tablet rail/reflow behaves sensibly;
- route/state behavior across rotation/recreation;
- RuntimeSnapshot continues advancing;
- Diagnostics Overview/Events/Metrics/Sessions/Captures populate;
- recent-problem capture auto-finishes after its post-trigger window;
- reproduction capture + marker + stop;
- capture/export/share works;
- Activity recreation during capture preserves capture/evidence;
- no duplicate active NativeEngine after recreation;
- no obvious UI/native responsiveness regression;
- diagnostic memory/CPU overhead is reasonable for this synthetic workload.

Status: `CURRENT PHASE 2 PHYSICAL GATE`.

---

# Dashboard / Logger visual authority still open

The user is developing HTML/CSS prototypes for Dashboard and Logger.

Need later:

- review/accept explicit visual/layout references;
- translate accepted intent into native Compose;
- physically compare phone/tablet results;
- preserve D-023: prototype JS/mock data never becomes runtime authority.

Current structural Dashboard/Logger Compose surfaces are intentionally provisional.

Status: `CURRENT UI DESIGN INPUT`, not a blocker for testing PR #3 shell/diagnostics behavior.

---

# Diagnostics depth still open after PR #3

The current diagnostic foundation is sufficient for the upcoming subsystem work, but these deeper items remain deliberately open:

- previous-process/crash/exit context where Android actually provides evidence;
- explicit optional attachment selection;
- attachment provenance/hash metadata;
- richer archive integrity/version migration policy;
- longer-duration event-storm/memory/thermal benchmarks;
- richer subsystem-specific event schemas as USB/logger/tuner/analyzer arrive.

Do not claim these are already implemented merely because the basic capture/archive exists.

Status: `DIAGNOSTICS FOLLOW-ON WORK`.

---

# Next inherited subsystem after current gate

## Read-only ECU acquisition

Begins only after PR #3 is physically qualified/accepted unless the user deliberately chooses to integrate accepted Dashboard/Logger visual prototypes first.

D-022 source-understanding gate must review/classify relevant EpicDash-JZ/firmware behavior before production implementation.

Candidate sustained-I/O strategies remain measurement-driven:

```text
A. Kotlin USB I/O/decode reference
B. Kotlin USB I/O → coarse Rust batches/decode
C. Android open/claim → proven native FD/handle → Rust I/O/decode
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

Need later:

- versioned project store;
- exact profile source + complete verified TuneSnapshot + identity/generation metadata;
- portable backup;
- uncertainty journal;
- offline draft/reconnect reconciliation;
- process-death/fault-injection corpus.

All codecs follow D-023: typed runtime first, serialization only at explicit boundaries.

Status: `TUNER FEATURE-ENTRY WORK`.

---

# Tune comparison gaps

Need later exact persisted comparison identity, same-/cross-profile goldens, optional changed-axis transforms only under explicit policy and optional compiled-profile cache only if benchmarks justify it.

Status: `COMPARE/ANALYSIS FEATURE-ENTRY WORK`.

---

# Logging / measurement gaps

Locked order:

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

Need later:

- concrete versioned `AnalysisSession` representation;
- domain-profile trait/interface;
- typed readiness/comparability/identifiability/result vocabulary;
- session persistence/indexing policy;
- semantic `ExperimentProposal` handoff;
- deterministic replay/golden corpus;
- per-tool D-022 source review and clean Rust implementation;
- AE/PID/TCharge/Injection Angle qualification depth.

The tools are native EpicHub functionality, not dynamically loaded plugins.

Status: `TUNING TOOLS / ANALYSIS FEATURE-ENTRY WORK`.

---

# ESP32 legacy bridge gaps

Need later as product scope requires: retained compatibility decisions, physical disconnect/button-release verification, BLE/CAN goldens, and a separate versioned direct-output protocol only if direct relay/GPIO control is deliberately introduced.

Status: `DEFERRED ACCESSORY WORK`.

---

# Closed architecture questions

Do not reopen without contradictory evidence:

- Kotlin/Compose owns Android/UI; Rust owns native high-volume engine work;
- runtime state is typed Kotlin/Rust state;
- design-only HTML/CSS/JavaScript prototypes are allowed but translated to Compose;
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

> Physical qualification and acceptance of PR #3's implemented UI/diagnostics foundation on SM-A137F and SM-T500. Real ECU acquisition begins only after that gate unless the user explicitly changes the sequencing.
