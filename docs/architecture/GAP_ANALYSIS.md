# EpicHub Gap Analysis

## Purpose

This document separates responsibilities that have strong historical implementations/evidence from capabilities that EpicHub must design substantially from first principles.

A gap is not necessarily a missing product feature. It may be a new architecture required to unify previously separate behaviours cleanly.

## High-priority clean-slate gaps

### Kotlin/Rust engine boundary

No predecessor provides the desired final boundary.

Needed design:

- small semantic JNI API;
- lifecycle-safe native engine handle;
- packed/shared runtime snapshot buffer;
- bounded engine event transfer;
- explicit error mapping;
- no per-channel/per-sample JNI API;
- ownership/lifetime rules for any USB native descriptor handoff.

Status: `PROPOSED`.

### Rust runtime ChannelStore

Historical apps have dashboard/data hubs and runtime snapshots, but no domain-neutral native channel authority shared by Dashboard, Logger, Analyzer and calculations.

Needed design:

- stable channel IDs/schema;
- typed raw/physical values;
- timestamps/revisions/source/validity;
- live and file-backed source abstraction;
- bounded current/history/cache ownership;
- accessory/GPS ingestion path.

Status: `PROPOSED`.

### MDF4 native logger

No existing EpicDash implementation provides native MDF4 acquisition/writing.

Needed design:

- streaming writer;
- metadata/channel schema mapping;
- block sizing/buffering;
- compression selection;
- crash/power-loss recovery;
- file finalization;
- independent interoperability tests;
- storage/size benchmarks on Android hardware.

Status: `PROPOSED`.

### MDF4 reader/index

Needed design:

- lazy/memory-mapped access where useful;
- time→block/channel indexes;
- decompression cache;
- disposable/rebuildable sidecar index where justified;
- partial range reads;
- validation against independent MDF tooling/corpus.

Status: `PROPOSED`.

### Native analyzer

Existing EpicDash analysis/history features and separate tuner plugins provide useful concepts, but there is no common large-log analyzer engine.

Needed design:

- time-range/channel query API;
- statistics;
- search/filter/event detection;
- histogram/scatter/heatmap primitives;
- expression/derived-channel integration;
- reusable tuning-analysis algorithms;
- live/log source parity.

Status: `PROPOSED`.

### Plot-resolution engine

Needed design:

- min/max multiresolution pyramid preserving spikes;
- viewport-driven level selection;
- screen-sized plot packets;
- native-data fallback at close zoom;
- cache strategy;
- Compose rendering first, specialized GPU path only if measurements justify it.

Status: `PROPOSED`.

### Compose application shell

EpicDash-JZ WebView/native hybrid and old Android Views provide behavioural/UI references but not the target implementation.

Needed design:

- one Compose navigation shell;
- phone/tablet/portrait/landscape responsive behavior;
- dashboard runtime and editor;
- tuner parameters/curves/tables/3D visualization;
- logger/analyzer/diagnostics/settings surfaces;
- frame-rate-aware telemetry state handling.

Status: `PROPOSED`.

### Semantic action system

Original EpicDash exposes button transport semantics directly. EpicHub needs a product-level action model.

Needed design:

- `Local`, `Accessory`, `ECU` targets;
- semantic action IDs/definitions;
- allowed sources and guard policy;
- requested/confirmed/fault accessory state;
- event logging;
- future approved automation actions without exposing GPIO/tune bytes.

Status: `PROPOSED`.

### Accessory profile/protocol evolution

The original ESP32 protocol is known, but EpicHub may eventually need richer relay/output acknowledgement, hardware state, version negotiation and explicit failsafe contracts.

Needed decision:

- compatibility-only reimplementation versus deliberately versioned EpicHub accessory protocol extension;
- discovery/capability/version handshake;
- requested vs confirmed state feedback;
- output/failsafe definition ownership.

Status: `PROPOSED`; do not alter protocol until Phase 1 has inventoried existing firmware behavior fully.

### Unified structured diagnostics

EpicDash-JZ has substantial diagnostics but they are distributed across Android/WebView/transports.

Needed design:

- common structured event schema;
- Android platform event adapter;
- Rust engine event ring;
- correlation/session IDs;
- bounded history;
- performance counters;
- export/report model.

Status: `PROPOSED` based on `PROVEN` diagnostic need.

### New-project storage model

No install continuity is required, so EpicHub can define a clean storage model.

Needed design:

- imported/generated profile storage;
- verified TuneSnapshot/project state;
- layouts/settings/rules;
- logs/indexes;
- recovery journals;
- explicit externally exported backups;
- schema/version/checksum strategy.

Status: `PROPOSED` while preserving tuning authority invariants.

## Important unresolved technical decisions

### ECU sustained runtime I/O owner after Android USB open

The source archaeology now cleanly separates Android USB permission/device/CDC setup from TunerStudio protocol/session semantics. The remaining decision is only who performs the sustained bulk transfer after Android has opened and claimed the device.

Candidates to benchmark:

1. Kotlin bulk I/O with coarse batched transfer into Rust;
2. Android permission/open/claim then proven native FD/handle handoff to Rust sustained I/O.

A pure Kotlin decode architecture no longer needs to be a final candidate, but it may remain a benchmark/reference implementation if useful.

Decision criteria:

- attach/permission/open latency;
- reconnect correctness after detach/replug;
- ownership/close semantics;
- sustained packet rate/CPU/allocation;
- JNI crossings/bytes;
- failure recovery;
- compatibility on Samsung A13/SM-A137F and SM-T500-class hardware plus future target devices.

Decision must be based on real Android device and ECU evidence, not architectural aesthetics.

### MDF4 version/compression baseline

Current direction:

- MDF 4.2 + Deflate for broad compatibility;
- investigate MDF 4.3 + LZ4 for live performance;
- investigate MDF 4.3 + Zstd for archive/storage.

Do not lock until interoperability/performance tests establish the supported reader ecosystem and Android costs.

### Minimum Android/API/ABI/device target

Must be locked before production scaffold:

- minimum API level;
- primary ABI, expected to begin with `arm64-v8a` unless evidence requires more;
- low/mid device performance target;
- phone/tablet/head-unit resolution/orientation requirements.

### New package/application ID

No continuity requirement. A clean namespace such as `com.epicefi.epichub` is preferred but must be formally locked before implementation scaffold.

### Generated-INI compatibility corpus breadth

The parser/profile architecture is now decomposed, and the current supported subset is understood. Remaining gap is corpus breadth, not responsibility ownership.

Needed before Phase 2 implementation is considered mature:

- retain several genuine generated INIs across representative EpicEFI/rusEFI boards/firmware generations;
- catalogue constructs actually present in those files;
- distinguish must-support constructs from intentionally unsupported TunerStudio features;
- include malformed/ambiguous/unsupported fixtures for fail-closed tests;
- add a genuine complete epicECU generated INI fixture when trustworthy evidence becomes available.

### Protocol golden-vector corpus

Request/response framing, status semantics and `S/Q/O/R/C/B` command dispatch are now directly verified in firmware. Remaining gap is to preserve this as executable fixture evidence before implementation:

- plain signature responses;
- framed signature request/response;
- `O` full/chunk requests;
- `R` page reads;
- `C` write + OK/error responses;
- `B` accepted/error responses;
- CRC failure, underrun/out-of-range/unrecognized status cases;
- response payload lengths around buffer/chunk boundaries.

These vectors should be generated from documented/controlled firmware interactions or independently constructed from the verified source contract and cross-checked against real firmware.

## Phase 1 archaeology completed enough to leave the deferred list

The following former deferred items now have sufficient responsibility-level decomposition for architecture work to continue:

- EpicDash-JZ `UsbEcuManager` platform/session/framing/output/tuning coordination split;
- EpicDash-JZ generated-INI parser/compiler/condition split;
- firmware request/response framing and `S/Q/O/R/C/B` read/write/Burn command paths.

This does **not** mean their Phase 2 implementations are complete; it means Phase 1 now knows their permanent responsibilities and authority boundaries.

## Deferred deeper source inventories

The following references still need dedicated Phase 1 review before their responsibilities are considered complete:

- complete original ESP32 firmware behavior including physical buttons/outputs/ADC/CAN/watchdog/reconnect/failure/failsafe semantics;
- EpicDash-JZ BLE evolution/reconnect/write-queue behavior against the original ESP32 protocol;
- tuning project persistence/recovery stores beyond the already captured exact-profile/TuneSnapshot authority;
- Epic-DashTune-UXUI interaction/responsive/performance lessons without importing its visual language;
- Tune Viewer parsing/visualization lessons;
- diagnostics-core architecture and reusable diagnostics schemas if present;
- logging-format/MLG implementation investigations already performed elsewhere in the project;
- AE/PID/TCharge/Injection Angle plugins for future analyzer/autotune responsibilities.