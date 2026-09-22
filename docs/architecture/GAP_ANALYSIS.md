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

### ECU runtime I/O owner after Android USB open

Candidates to benchmark:

1. Kotlin USB I/O + Kotlin decode baseline/reference;
2. Kotlin USB I/O + batched Rust decode/processing;
3. Kotlin permission/open → native FD → Rust/native I/O.

Decision must be based on real Android device and ECU lifecycle/reconnect/throughput evidence, not architectural aesthetics.

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

## Deferred deeper source inventories

The following references still need dedicated Phase 1 review before their responsibilities are considered complete:

- EpicDash-JZ huge USB manager: split all transport/session/framing/output/tuning coordination responsibilities explicitly;
- EpicDash-JZ INI parser/compiler/conditions;
- complete ESP32 original firmware behavior including physical inputs/ADC/watchdog/reconnect/failure semantics;
- firmware fork command/framing/write/Burn source paths;
- Epic-DashTune-UXUI interaction/performance lessons;
- Tune Viewer parsing/visualization lessons;
- diagnostics-core architecture;
- AE/PID/TCharge/Injection Angle plugins for future analyzer/autotune responsibilities.
