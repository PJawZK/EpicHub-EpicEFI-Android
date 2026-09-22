# Phase 1 Progress

## Status

Phase 1 is active on `phase1/architecture-inventory`.

The repository-backed architecture foundation has been established. Production Android/Rust scaffolding has not started.

## Completed architecture foundation

- clean-slate development workflow and handoff rules;
- durable architecture decision log;
- permanent naming rules;
- cross-repository source inventory;
- normalized responsibility inventory;
- protocol-authority map;
- gap analysis;
- detailed provisional codebase topology;
- responsibility-to-destination mapping;
- dependency/state/data/threading/FFI/action/safety contracts;
- test strategy and Phase 1 review checklist.

## Completed deep-dive tranche: ECU USB / protocol / generated INI

The current EpicDash-JZ ECU/profile/runtime path has now been decomposed far enough to establish permanent responsibility boundaries.

### `UsbEcuManager` decomposition completed

The historical manager is confirmed to combine responsibilities that EpicHub will keep separate:

- Android USB discovery/permission/attach-detach lifecycle;
- CDC descriptor topology and bulk endpoint resolution;
- interface claim/release and CDC line/control state;
- session generation/invalidation/retry;
- plain/framed signature probing and protocol synchronization;
- request framing/CRC;
- response accumulation/status/CRC;
- output polling/decoding/freshness;
- complete tune reads;
- exclusive normal tuning operations;
- RAM write transactions and exact verification;
- Burn transactions and flash-state evidence;
- workspace/profile caches;
- multi-domain diagnostics.

The destination map and target topology now assign these to Android platform USB, Rust ECU/session/protocol, profile, tuning, channels and diagnostics owners instead of creating a replacement monolith.

### Generated-INI decomposition completed

`UsbTunerStudioProfile`, `TuningIniDefinition` and `IniConditionEvaluator` establish distinct future responsibilities for:

- INI syntax/source parsing;
- explicit supported-profile subset validation;
- primitive representation/decode;
- runtime output schema compilation;
- calibration/tune storage schema compilation;
- table/curve/menu/dialog semantic schema compilation;
- duplicate/ambiguity/geometry canonicalization;
- exact profile fingerprinting;
- compatibility-sensitive condition grammar/evaluation;
- condition-derived semantic target/write authority.

INI conditions are not treated as presentation-only state. Unsupported/unresolved conditions fail closed for affected writes.

### Firmware protocol verification completed

Direct firmware source now anchors:

- shared `0483:5740` CDC transport identity;
- generated `TS_SIGNATURE` live identity;
- asymmetric request/response CRC framing;
- firmware synchronization state;
- typed `S/Q`, `O`, `R`, `C`, `B` command semantics;
- output range validation and live-data response;
- page read/write range validation;
- write lock/preset-refresh behavior and board overrides;
- distinct Burn acknowledgement.

The architecture now explicitly uses separate request and response codecs rather than a generic symmetric `envelope()` abstraction.

### Remaining implementation-evidence gaps from this tranche

- build protocol golden-vector fixtures for request/response/status/error cases;
- broaden genuine generated-INI corpus and explicit supported/unsupported construct fixtures;
- benchmark/physically prove sustained Android USB I/O ownership: Kotlin-batched vs Android-opened FD/handle handed to Rust.

These are no longer responsibility-discovery gaps.

## Current inventory tranche

Primary next target: ESP32 accessory firmware and evolved Android BLE path.

- fully inventory original ESP32 physical inputs/outputs/ADC/CAN/BLE behavior;
- identify disconnect/reconnect/watchdog/failsafe behavior or its absence;
- inventory original wire messages independently of GATT lifecycle;
- compare EpicDash-JZ BLE reconnect/write-queue evolution against original semantics;
- decide which behavior is compatibility requirement, which is historical implementation detail, and which needs a deliberately versioned EpicHub accessory extension;
- refine action/accessory/failsafe/protocol responsibilities accordingly.

## Subsequent tranches

- tune project persistence/recovery;
- dashboard/editor/tuner interaction inventory;
- logging/playback/interoperability;
- diagnostics/performance;
- UXUI/Tune Viewer and tuning-plugin future features.
