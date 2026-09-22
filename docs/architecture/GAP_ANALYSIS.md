# EpicHub Gap Analysis

## Purpose

This document separates architecture/responsibility questions already resolved by evidence from work that still requires design, fixtures, benchmarking or physical qualification.

A gap does not necessarily mean a missing product feature. It may be the remaining proof needed before a known responsibility can be implemented safely.

---

# Major clean-slate implementation gaps

## Kotlin/Rust engine boundary

Still required:

- small semantic JNI API;
- lifecycle-safe engine handle;
- packed/shared runtime snapshot buffer;
- bounded structured event transfer;
- stable error mapping;
- no per-channel/per-sample JNI calls;
- explicit ownership/lifetime rules for any native USB handle handoff.

Status: `PROPOSED`.

## Rust runtime ChannelStore

Still required:

- stable channel IDs/schema;
- typed raw/physical values;
- timestamps/revisions/source/validity;
- live/file-backed source abstraction;
- bounded cache/history ownership;
- GPS/legacy bridge ingestion.

Status: `PROPOSED`.

## Logging / measurement writers

Responsibility ownership is now resolved by `LOGGING_INTEROP_EVIDENCE.md`; implementation/proof remains.

Still required:

- format-neutral bounded sample frame/batch path;
- live acquisition benchmark before writer coupling;
- production MLG writer/reader with executable compatibility corpus;
- MDF4 streaming writer/reader;
- metadata/channel mapping;
- block sizing/buffering;
- crash/power-loss recovery/finalization;
- independent interoperability qualification;
- identical-workload Android file-size/CPU/storage/read/write benchmarks.

Status: `PROPOSED IMPLEMENTATION`, not an ownership-discovery gap.

## Native analyzer / plot-resolution engine

Still required:

- bounded time/channel queries;
- statistics/search/events;
- histogram/scatter/heatmap;
- derived channels;
- tuning analysis reuse;
- spike-preserving multires min/max data;
- viewport-sized plot packets/cache;
- Compose rendering first, specialized GPU only if measured need.

Status: `PROPOSED`.

## Compose application shell

Responsibility ownership is substantially resolved by the UI interaction archaeology, but the actual clean Compose implementation remains Phase 2 work.

Still required:

- one responsive navigation shell;
- phone/tablet/portrait/landscape implementation;
- Dashboard and editor;
- Tuner parameters/curves/tables/3D visualization;
- Logger/Analyzer/Diagnostics/Settings;
- frame-rate-aware telemetry consumption;
- physical interaction/performance qualification.

Status: `PROPOSED IMPLEMENTATION`, not an ownership-discovery gap.

## Structured diagnostics implementation

Responsibility ownership is now resolved by `DIAGNOSTICS_EVIDENCE.md`.

Still required:

- typed event schema;
- typed metric descriptors/units/kinds;
- cross-domain correlation/session IDs;
- Kotlin platform adapter;
- Rust bounded event ring/metric windows;
- warning-incident persistence model;
- report snapshot/export schema;
- machine-readable qualification output.

Status: `PROPOSED IMPLEMENTATION`, not an ownership-discovery gap.

---

# Important unresolved technical decisions

## ECU sustained runtime I/O after Android open

Candidates:

1. Kotlin bulk I/O with coarse batches into Rust;
2. Android open/claim then proven native FD/handle to Rust sustained I/O.

Decision criteria: attach/open latency, detach/replug correctness, ownership/close semantics, sustained rate/CPU/allocation, JNI crossings/bytes, failure recovery and low/mid Android hardware behavior.

Status: `OPEN BENCHMARK DECISION`.

## MDF4 version/compression baseline

Candidates:

- MDF 4.2 + Deflate for compatibility;
- MDF 4.3 + LZ4 for live performance;
- MDF 4.3 + Zstd for storage/archive.

Must be compared on the same acquisition/channel workload, with mobile file size as the first decision metric and CPU/thermal/read/write/interoperability costs measured directly.

Status: `OPEN BENCHMARK/INTEROP DECISION`.

## Minimum Android/API/ABI/device target

Lock before Phase 2: minimum API, ABI set, low/mid hardware target, resolution/orientation matrix.

Status: `OPEN PRODUCT/BUILD DECISION`.

## New Android package/application ID

No continuity requirement. `com.epicefi.epichub` is provisional/preferred but not yet formally locked.

Status: `OPEN NAMING/BUILD DECISION`.

---

# Evidence/fixture gaps after completed ECU/profile archaeology

## Generated-INI compatibility corpus breadth

Need genuine generated INIs across representative boards/firmware generations, actual construct inventory, explicit supported/unsupported list, malformed/ambiguous fail-closed cases and a trustworthy complete epicECU fixture when available.

Status: `OPEN FIXTURE GAP`.

## ECU protocol golden vectors

Need executable fixtures for plain/framed signature, `O`, `R`, `C`, `B`, status/error and boundary-size cases.

Status: `OPEN FIXTURE GAP`.

---

# ESP32 legacy bridge: responsibility archaeology completed

Resolved:

- bridge with local inputs, not a direct relay controller;
- generic variable-set characteristic, not GPS-only;
- legacy wire formats documented;
- evolved Android reconnect/GATT lifecycle captured;
- one global GATT operation owner selected for target architecture;
- no invented downstream confirmation;
- disconnect does not provide proven zero/release behavior;
- task watchdog is liveness only;
- direct relay/GPIO output is a separate future versioned capability.

## Remaining bridge decisions/evidence

### Legacy compatibility product scope

Decide which remain requirements: button bridge, CAN variable request/response, generic variable-set, virtual ADC, local ESP32 inputs.

Status: `OPEN PRODUCT-SCOPE DECISION`.

### Button disconnect/release semantics

Verify downstream ECU/button-box consumer behavior before changing disconnect/reconnect behavior.

Status: `OPEN COMPATIBILITY/PHYSICAL GAP`.

### BLE/CAN golden vectors

Need fixtures for button, variable request/response, partial timeout, variable-set and malformed cases.

Status: `OPEN FIXTURE GAP`.

### Versioned direct-output extension

Only if product requires direct ESP32 relay/GPIO control. Requires capabilities/versioning, semantic IDs, application ACK/state/fault feedback and firmware-owned interlocks/failsafe.

Status: `DEFERRED NEW CAPABILITY`.

---

# Tuning persistence/recovery: responsibility archaeology completed

The current source establishes stable permanent categories:

```text
project authority
= exact generated profile source + last complete verified TuneSnapshot

derived acceleration
= compiled profile/schema/projection caches, exact-identity-bound and rebuildable

portable backup
= validated transport/archive representation of project authority, not new authority

operation uncertainty journal
= tiny durable evidence written before possible C/B transmission

UI draft
= local unverified requested/edit state
```

Resolved architectural rules:

- no semantic-values sidecar becomes alternate tune authority;
- derived caches are disposable and exact source/profile identity-bound;
- project authority updates are transactional generations, with rollback on incomplete commit/verification;
- saved/offline project state is read-only relative to ECU hardware until a new live verified session exists;
- portable backup import validates exact profile + optional complete TuneSnapshot before local commit;
- user document/SAF transport is Kotlin-owned, while backup/project schema/integrity remains tuning-domain authority;
- operation journals are fsynced and reread **before** a command may cross the point of possible hardware mutation;
- present/corrupt journal blocks mutation and requires deliberate resynchronization;
- recovery metadata never proves ECU state and never authorizes automatic replay;
- pilot-specific candidate-restore/power-cycle proof flows are qualification history, not ordinary product UX.

These are no longer responsibility-discovery gaps.

## Remaining project-persistence design decisions/evidence

### Internal project container/layout

Need final clean-slate internal schema/path strategy for exact source profile, complete verified TuneSnapshot, metadata/checksums and atomic generation commit.

Preference: Rust owns serialization/integrity and app-private files after Kotlin supplies a base directory; Kotlin SAF is only user document transport.

Status: `OPEN IMPLEMENTATION DESIGN`.

### Portable backup format

Need a compact versioned archive/container preserving only project authority plus necessary metadata, with deterministic integrity checks and user-driven export/import.

Status: `OPEN FORMAT DESIGN`.

### Final generic operation-journal schema

Need a batch-capable journal with operation kind/phase, exact ECU/profile/session identity, baseline/expected tune fingerprints, write-plan identity/affected pages and Burn evidence identity where relevant.

Status: `OPEN IMPLEMENTATION DESIGN`, authority/lifecycle already resolved.

### Offline draft/reconciliation semantics

Need product behavior for offline saved-project editing, reconnect conflict/reconciliation, and separation of software-draft dirty state from verified ECU dirty pages/Burn state.

Status: `OPEN PRODUCT/TUNING DESIGN`.

### Persistence fault-injection corpus

Need interrupted commit/cache/backup/journal/process-death/profile-mismatch recovery tests.

Status: `OPEN TEST GAP`.

---

# Dashboard / Tuner UI interaction: responsibility archaeology completed

Evidence document: `UI_INTERACTION_EVIDENCE.md`.

Resolved ownership/principles:

- accepted EpicDash-JZ tablet/phone references remain visual/layout authority;
- UXUI contributes only feature/interaction/responsive/performance evidence, not its visual language;
- one semantic/component model reflows by geometry; no separate phone/tablet application implementations;
- Rust owns tune/runtime semantic truth while Compose owns interaction/presentation state;
- responsive changes may alter rails, panel placement, viewport and density but must preserve semantic selection/drafts/authority;
- only active presentation surfaces should consume high-rate display updates where practical;
- static gauge/editor geometry should be cached/constructed once and dynamic layers updated minimally;
- table semantic selection is separate from pointer-gesture state;
- pinch/range selection conflicts need explicit gesture arbitration/rollback;
- X/Y reversal changes display order only, never tune array order;
- zoom/pan/heat-map/panel folds/3D camera are presentation state;
- 3D table inspection remains view-only unless a future accepted edit contract is introduced;
- modal/fullscreen surfaces own their pointer streams and cannot trigger underlying actions;
- haptics do not signal hardware success before verified domain results;
- undo/redo is local semantic edit history, not automatic ECU rollback;
- saved-project/live/draft/RAM-dirty/Burn/uncertain states need explicit distinctions in presentation.

## Remaining UI product/physical decisions

### Rich table-edit feature scope

UXUI proves feasibility for arithmetic/fill/flatten, smoothing, interpolation, copy/paste, undo/redo, baseline revert and CSV text import/export. These are candidates, not automatically locked scope.

Status: `OPEN PRODUCT-SCOPE DECISION`.

### Dashboard presentation customization scope

Per-gauge display limits/precision, favorites/Quick Tuning and logging presets are useful interaction candidates but remain product-scope decisions.

Status: `OPEN PRODUCT-SCOPE DECISION`.

### Compose renderer/performance qualification

Need real device benchmarks for:

- high-rate dashboard custom drawing;
- 32×32 table scrolling/selection/heat map;
- pinch/long-press gesture arbitration;
- orientation state preservation;
- view-only 3D rotation;
- inactive-page render suppression;
- p50/p95/p99 frame time and jank on low/mid hardware.

The UXUI ~90% synthetic SVG update reduction is a transferable invalidation lesson, not a Compose speed forecast.

Status: `OPEN PHYSICAL/PERFORMANCE GAP`.

---

# Logging / playback / interoperability: responsibility archaeology completed

Evidence document: `LOGGING_INTEROP_EVIDENCE.md`.

Resolved rules:

- live acquisition/channel authority is format-neutral;
- live path is proven/optimized before writer implementation becomes the focus;
- MLG is the first writer for ecosystem interoperability, not the internal data model;
- MDF4 remains the long-term native-format candidate behind EpicHub's own measurement abstraction;
- file size is the primary mobile format metric, followed by sustained I/O/CPU/thermal/recovery behavior;
- MSL/CSV/MLG/MDF are isolated readers/writers feeding common channel/source semantics;
- playback clock/state is separate from measurement data;
- current MSL primitive columnar parsing/generation cancellation/binary-search playback are useful techniques, while hardcoded alias/JSON/full-memory design is not;
- writer/UI/analyzer threads are independent and communicate through bounded batches/queries;
- large-log analysis is lazy/range-based with rebuildable indexes/caches;
- plot reduction preserves spikes and is viewport-driven;
- writer recovery metadata never fabricates measurement data.

## Remaining logging/format evidence and implementation

### MLG executable compatibility corpus

Need authoritative/genuine fixtures for real MLG structure, metadata, records, timestamps, finalization/truncation and MegaLogViewer/TunerStudio compatibility.

Do not derive the production writer from one log file or repeated-block appearance alone.

Status: `OPEN FIXTURE/INTEROP GAP`.

### MDF interoperability/version/compression

Need independent-reader cross-checks, version/compression corpus and identical-workload Android measurements.

Status: `OPEN BENCHMARK/INTEROP GAP`.

### Android recording storage policy

Need app-controlled hot sequential recording location, retention/quota behavior and user export/share policy. SAF remains outside the hot writer loop.

Status: `OPEN PRODUCT/PLATFORM DESIGN`.

### Writer power-loss recovery

Need actual checkpoint/finalization/recovery implementation and fault-injection qualification.

Status: `OPEN IMPLEMENTATION/TEST GAP`.

---

# Diagnostics / performance: responsibility archaeology completed

Evidence document: `DIAGNOSTICS_EVIDENCE.md`.

Resolved rules:

- diagnostics are observational and never become hidden subsystem control authority;
- structured events, warning incidents, performance metrics and lifecycle/owner traces are separate types;
- event/metric/history storage is bounded;
- metric hot paths record cheap primitive samples/counters and aggregate on reads;
- metric descriptors own explicit units/kinds/stable IDs;
- p50/p95/p99/max are first-class for latency/jank/queue qualification;
- monotonic and wall clocks have distinct responsibilities;
- warning acknowledgement does not mutate channel/rule truth;
- correlation/session/generation IDs span Kotlin/Rust evidence without merging ownership;
- Kotlin owns Android/platform diagnostics while Rust owns engine diagnostics;
- diagnostics cross JNI in bounded snapshots/batches;
- measurement logging and diagnostics remain distinct;
- support/qualification export should have a stable machine-readable form in addition to human text.

## Remaining diagnostics implementation decisions

### Event/metric schema

Need final stable event codes, typed fields, metric IDs/units/kinds and ABI/export representation.

Status: `OPEN IMPLEMENTATION DESIGN`.

### Retention/persistence policy

Need exact in-memory ring sizes, warning/incident persistence scope and previous-run crash/uncertainty summaries.

Status: `OPEN PRODUCT/IMPLEMENTATION DESIGN`.

### Report/export schema

Need versioned structured diagnostic snapshot/export plus human-readable rendering and Android user export path.

Status: `OPEN FORMAT/IMPLEMENTATION DESIGN`.

---

# Phase 1 archaeology completed enough to leave deferred list

Stable responsibility-level decomposition now exists for:

- EpicDash-JZ USB/session/framing/runtime/tuning manager;
- generated-INI parser/compiler/condition authority;
- firmware request/response framing and `S/Q/O/R/C/B` paths;
- original ESP32 BLE↔CAN bridge and evolved Android BLE lifecycle;
- tuning project authority, derived caches, portable recovery and operation uncertainty journals;
- Dashboard/Tuner interaction, responsive and rendering responsibility boundaries;
- logging/playback/MLG/MDF transition responsibilities;
- diagnostics/performance/lifecycle tracing responsibilities.

This means ownership is known, not that Phase 2 implementation/qualification is complete.

---

# Deferred deeper source inventories

Next-highest-value Phase 1 reviews:

1. Tune Viewer parsing/visualization lessons;
2. AE/PID/TCharge/Injection Angle plugin repositories for future analyzer/autotune responsibilities.

After these reviews, Phase 1 should primarily close the explicit design, fixture and benchmark gaps above rather than continue broad archaeology indefinitely.