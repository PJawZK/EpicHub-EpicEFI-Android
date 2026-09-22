# EpicHub Gap Analysis

## Purpose

This document separates architecture/responsibility questions already resolved by evidence from work that still requires design, fixtures, benchmarking or physical qualification.

A gap does not necessarily mean a missing product feature. It may be the remaining proof needed before a known responsibility can be implemented safely.

---

# Major clean-slate implementation gaps

## Kotlin/Rust engine boundary

No predecessor provides the target final boundary.

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

Historical dashboard/data hubs exist, but no domain-neutral native channel authority shared by Dashboard, Logger, Analyzer and calculations.

Still required:

- stable channel IDs/schema;
- typed raw/physical values;
- timestamps/revisions/source/validity;
- live/file-backed source abstraction;
- bounded cache/history ownership;
- GPS/legacy bridge ingestion.

Status: `PROPOSED`.

## MDF4 writer / reader / recovery

No EpicDash predecessor supplies the intended native MDF4 path.

Still required:

- streaming writer;
- metadata/channel mapping;
- block sizing/buffering;
- version/compression decision;
- crash/power-loss recovery/finalization;
- lazy partial reads and indexing;
- independent interoperability corpus;
- Android file-size/CPU/storage benchmarks.

Status: `PROPOSED`.

## Native analyzer / plot-resolution engine

Still required:

- bounded time/channel query API;
- statistics/search/events;
- histogram/scatter/heatmap;
- derived channels;
- tuning analysis reuse;
- min/max multiresolution pyramid preserving spikes;
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
- Kotlin platform event adapter;
- Rust event ring;
- bounded history;
- performance counters;
- export/report model.

Status: `PROPOSED` from a `PROVEN` diagnostic need.

## Clean project/storage model

No install continuity is required.

Still required:

- exact profile + verified TuneSnapshot project storage;
- layouts/settings/rules;
- logs/indexes;
- recovery journals;
- explicit external backups;
- schema/version/checksum rules.

Status: `PROPOSED`, with tuning authority invariants already locked.

---

# Important unresolved technical decisions

## ECU sustained runtime I/O after Android open

Responsibility ownership above transport is resolved. Remaining benchmark choice:

1. Kotlin bulk I/O with coarse batches into Rust;
2. Android permission/open/claim then proven native FD/handle to Rust sustained I/O.

Decision criteria:

- attach/open latency;
- detach/replug correctness;
- close/ownership semantics;
- sustained rate/CPU/allocation;
- JNI crossings/bytes;
- failure recovery;
- Samsung A13/SM-A137F and SM-T500-class performance plus later targets.

Status: `OPEN BENCHMARK DECISION`.

## MDF4 version/compression baseline

Current candidates:

- MDF 4.2 + Deflate for compatibility;
- MDF 4.3 + LZ4 for live performance;
- MDF 4.3 + Zstd for storage/archive.

Do not lock until reader interoperability and Android cost are measured.

Status: `OPEN BENCHMARK/INTEROP DECISION`.

## Minimum Android/API/ABI/device target

Lock before Phase 2 scaffold:

- minimum API;
- ABI set, likely starting with `arm64-v8a` unless evidence requires more;
- low/mid hardware performance target;
- phone/tablet/head-unit resolution/orientation matrix.

Status: `OPEN PRODUCT/BUILD DECISION`.

## New Android package/application ID

No continuity requirement. `com.epicefi.epichub` is the current architectural placeholder/preference but must be formally locked before scaffold.

Status: `OPEN NAMING/BUILD DECISION`.

---

# Evidence/fixture gaps after completed ECU/profile archaeology

## Generated-INI compatibility corpus breadth

Responsibility split is resolved; remaining gap is evidence breadth.

Need:

- genuine generated INIs across representative EpicEFI/rusEFI boards/firmware generations;
- catalogue of constructs actually present;
- explicit must-support vs intentionally unsupported feature list;
- malformed/ambiguous/unsupported fail-closed fixtures;
- genuine complete epicECU generated INI when trustworthy evidence becomes available.

Status: `OPEN FIXTURE GAP`.

## ECU protocol golden-vector corpus

Request/response framing and `S/Q/O/R/C/B` semantics are source-verified.

Need executable vectors for:

- plain/framed signature;
- `O` full/chunk reads;
- `R` page reads;
- `C` write + success/error;
- `B` success/error;
- CRC failure, underrun, out-of-range, unrecognized command;
- response sizes around buffer/chunk boundaries.

Status: `OPEN FIXTURE GAP`.

---

# ESP32 legacy bridge: responsibility archaeology completed

The reviewed original ESP32 firmware and evolved EpicDash-JZ Android BLE path are now decomposed sufficiently for permanent ownership decisions.

Resolved findings:

- original ESP32 is a BLE↔CAN bridge with local ADC/digital inputs, not a proven direct relay/GPIO output controller;
- characteristic `...ab` is generic variable-set transport, not GPS-specific;
- legacy button/variable-request/variable-response/variable-set wire formats are documented;
- evolved Android scan/reconnect/GATT lifecycle behavior is useful compatibility evidence;
- one connection-wide GATT operation scheduler replaces the historical three independent per-characteristic queue owners;
- Android GATT completion is not downstream CAN/action confirmation;
- original BLE disconnect does not send a proven zero/release button frame;
- ESP task watchdog is liveness protection, not output-state failsafe;
- future direct ESP32 relay/GPIO output control is a separate versioned capability requiring its own acknowledgement/state/interlock/failsafe contract.

These are no longer responsibility-discovery gaps.

## Remaining legacy bridge product/qualification decisions

### Keep/retire legacy bridge compatibility surface

Decide which legacy capabilities remain product requirements in the clean app:

- button-box mask forwarding;
- CAN-backed variable request/response;
- generic variable-set forwarding;
- phone virtual ADC compatibility;
- ESP32 local ADC/digital input forwarding.

Do not implement every historical capability merely because it exists.

Status: `OPEN PRODUCT-SCOPE DECISION`.

### Button disconnect/release semantics

Need to verify the actual downstream ECU/button-box consumer behavior before changing disconnect semantics.

Questions to resolve on bench/source:

- does the ECU consumer treat mask as state or momentary event?
- is an explicit zero-mask release required?
- can a disconnect after a non-zero mask create a sticky semantic state?
- what reconnect behavior is expected?

Status: `OPEN COMPATIBILITY/PHYSICAL GAP`.

### ESP32 BLE/CAN golden vectors

Create fixtures for:

- 16-bit LE button masks and CAN payload conversion;
- BE variable hash request batches;
- hash+float response batches;
- partial/time-out response cases;
- generic variable-set entries including packed/raw32 values;
- malformed/trailing byte cases;
- reconnect/session reset behavior where representable.

Status: `OPEN FIXTURE GAP`.

### Versioned direct-output extension

Only needed if EpicHub is to directly control ESP32 relays/GPIO.

Would require:

- protocol/capability versioning;
- semantic output IDs;
- app-level command acknowledgement;
- confirmed state/fault feedback;
- boot/default state;
- interlocks;
- disconnect/watchdog fail-safe behavior.

Status: `DEFERRED NEW CAPABILITY`, not legacy parity work.

---

# Phase 1 archaeology completed enough to leave deferred list

The following now have stable responsibility-level decomposition:

- EpicDash-JZ `UsbEcuManager` platform/session/framing/runtime/tuning split;
- generated-INI parser/compiler/condition split;
- firmware request/response framing and `S/Q/O/R/C/B` paths;
- original ESP32 BLE↔CAN bridge responsibilities and evolved Android BLE connection/queue behavior.

This does **not** mean their Phase 2 implementations are complete; it means their permanent ownership boundaries are known.

---

# Deferred deeper source inventories

Next-highest-value remaining Phase 1 reviews:

1. tuning project persistence/recovery stores and uncertainty/recovery behavior beyond the already locked exact-profile/TuneSnapshot authority;
2. dashboard/editor/tuner UI interaction/responsive/performance lessons, including Epic-DashTune-UXUI without importing its visual language;
3. logging/playback/MLG implementation investigations and transition path toward MDF4;
4. diagnostics-core architecture/reusable schemas if present;
5. Tune Viewer parsing/visualization lessons;
6. AE/PID/TCharge/Injection Angle plugin repositories for future analyzer/autotune responsibilities.