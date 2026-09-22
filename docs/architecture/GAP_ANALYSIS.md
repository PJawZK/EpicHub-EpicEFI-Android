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

## MDF4 writer / reader / recovery

Still required:

- streaming writer;
- metadata/channel mapping;
- block sizing/buffering;
- version/compression decision;
- crash/power-loss recovery/finalization;
- lazy reads/indexing;
- independent interoperability corpus;
- Android file-size/CPU/storage benchmarks.

Status: `PROPOSED`.

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

Still required:

- one responsive navigation shell;
- phone/tablet/portrait/landscape behavior;
- Dashboard and editor;
- Tuner parameters/curves/tables/3D visualization;
- Logger/Analyzer/Diagnostics/Settings;
- frame-rate-aware telemetry consumption.

Status: `PROPOSED`.

## Unified structured diagnostics

Still required:

- common event schema/correlation/session IDs;
- Kotlin platform adapter;
- Rust event ring;
- bounded history;
- performance counters;
- report/export model.

Status: `PROPOSED` from a `PROVEN` diagnostic need.

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

Need final clean-slate internal schema/path strategy for:

- exact source profile;
- complete verified TuneSnapshot;
- metadata/version/checksums;
- atomic project generation commit.

Preference: Rust owns serialization/integrity and app-private files after Kotlin supplies a base directory; Kotlin SAF is only user document transport. Validate this during Phase 2 platform PoC.

Status: `OPEN IMPLEMENTATION DESIGN`.

### Portable backup format

Do not inherit continuously mirrored huge JSON merely for compatibility with the old app.

Need a compact versioned archive/container preserving only project authority plus necessary metadata, with deterministic integrity checks and user-driven export/import.

Status: `OPEN FORMAT DESIGN`.

### Final generic operation-journal schema

Historical T3/T6 markers are scalar/proof-workflow specific. The clean product needs a batch-capable journal with operation kind/phase, exact ECU/profile/session identity, baseline/expected tune fingerprints, write-plan identity/affected pages and Burn evidence identity where relevant.

Status: `OPEN IMPLEMENTATION DESIGN`, authority/lifecycle already resolved.

### Offline draft/reconciliation semantics

Need product behavior for:

- editing a saved project offline;
- reconnecting to a same/different ECU/tune;
- conflict presentation/reconciliation;
- separating software draft dirty state from verified ECU dirty pages/Burn state.

`docs/FUTURE_TUNER_WORK.md` already identified this as a useful follow-up; clean-slate EpicHub should design it explicitly rather than infer it from WebView state.

Status: `OPEN PRODUCT/TUNING DESIGN`.

### Persistence fault-injection corpus

Need tests for interrupted project commit, stale cache candidate, corrupt/foreign backup, journal persistence/clear failure, crash before/after possible transmission, profile/session mismatch on recovery and fresh complete-read reclassification.

Status: `OPEN TEST GAP`.

---

# Phase 1 archaeology completed enough to leave deferred list

Stable responsibility-level decomposition now exists for:

- EpicDash-JZ USB/session/framing/runtime/tuning manager;
- generated-INI parser/compiler/condition authority;
- firmware request/response framing and `S/Q/O/R/C/B` paths;
- original ESP32 BLE↔CAN bridge and evolved Android BLE lifecycle;
- tuning project authority, derived caches, portable recovery and operation uncertainty journals.

This means ownership is known, not that Phase 2 implementation/qualification is complete.

---

# Deferred deeper source inventories

Next-highest-value Phase 1 reviews:

1. dashboard/editor/tuner UI interaction/responsive/performance lessons, including Epic-DashTune-UXUI without importing its visual language;
2. logging/playback/MLG investigations and transition path toward MDF4;
3. diagnostics-core/reusable diagnostics schemas;
4. Tune Viewer parsing/visualization lessons;
5. AE/PID/TCharge/Injection Angle plugin repositories for future analyzer/autotune responsibilities.