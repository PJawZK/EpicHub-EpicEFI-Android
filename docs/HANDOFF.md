# EpicHub Current Handoff

## Continuation instruction

Read this file completely first. Then verify the current branch/head/PR state in `PJawZK/EpicHub-EpicEFI-Android` and read the specifically referenced architecture documents before modifying the project.

Do not infer current EpicHub architecture from EpicDash-JZ when this repository establishes a newer deliberate contract.

## Repository state

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Permanent integration branch: `main`

Active Phase 1 branch: `phase1/architecture-inventory`

Open draft PR: **#1 — Phase 1: architecture and responsibility inventory**

Architecture branch head immediately before this handoff refresh: `7f3b1a8af01de99a32ecb4a6cf4f96e39be5da74`

This handoff commit itself advances the branch, so always verify the actual current branch/PR head when resuming.

`main` currently contains only the clean-slate repository bootstrap. Do not merge PR #1 merely because it exists; Phase 1 remains an active inventory until its review gate is accepted.

## Current phase

**Phase 1 — cross-repository responsibility inventory and codebase architecture.**

No production Android/Rust scaffold yet.

The authoritative Phase 1 scope/completion gate is `docs/architecture/PHASE1_SCOPE.md`. Progress is summarized in `docs/architecture/PHASE1_PROGRESS.md`, and `docs/architecture/REVIEW_CHECKLIST.md` is the closure checklist.

## Current objective

Use EpicDash-JZ, original EpicDash/EpicEFIVirtualDash, the EpicEFI firmware fork and later relevant UI/tuning/diagnostics repositories as evidence sources to produce a complete behaviour/responsibility model and a clean target EpicHub topology before implementation.

Historical files are not migration units. Behaviour, protocol semantics, safety invariants, test evidence and useful interaction knowledge are transferred into new descriptive responsibilities.

## Locked decisions

Read `docs/architecture/DECISIONS.md` for the durable list. Key decisions include:

- new clean-slate Android application; no EpicDash-JZ install/update continuity;
- Kotlin + Jetpack Compose Android layer;
- Rust high-volume data/ECU/logging/analysis engine;
- coarse semantic/snapshot Kotlin↔Rust boundary;
- Android BLE/GATT remains Kotlin-owned;
- ESP32 accessory control is a separate semantic domain and firmware owns physical output failsafes;
- MDF4 is the planned native measurement format;
- one domain-neutral runtime ChannelStore;
- no permanent development-stage names (`T3`, `T4`, `T6`, `B2`, etc.);
- repository documentation is durable project memory; this is the only current handoff file.

## Architecture documents established so far

Start with `docs/architecture/README.md`, which indexes the Phase 1 documents.

Core evidence/mapping:

- `SOURCE_INVENTORY.md`
- `RESPONSIBILITY_INVENTORY.md`
- `RESPONSIBILITY_DESTINATIONS.md`
- `PROTOCOL_AUTHORITIES.md`
- `GAP_ANALYSIS.md`

Target contracts:

- `CODEBASE_TOPOLOGY.md`
- `DEPENDENCY_RULES.md`
- `STATE_OWNERSHIP.md`
- `DATA_FLOW.md`
- `THREADING_MODEL.md`
- `KOTLIN_RUST_BOUNDARY.md`
- `ACTION_MODEL.md`
- `SAFETY_BOUNDARIES.md`
- `NAMING_RULES.md`
- `TEST_STRATEGY.md`

Workflow/review:

- `DEVELOPMENT_WORKFLOW.md`
- `DECISIONS.md`
- `PHASE1_SCOPE.md`
- `PHASE1_PROGRESS.md`
- `REVIEW_CHECKLIST.md`

## Evidence reviewed so far

### EpicDash-JZ

Main examined during this inventory: `45de2752811edbd66b01441c0cff8311d3e62647`.

High-value reviewed evidence includes:

- `docs/PROJECT_STATE.md`;
- `docs/ARCHITECTURE.md`;
- `docs/audits/ECU_RECOGNITION_FIRMWARE_EVIDENCE.md`;
- `docs/audits/TUNER_READ_WRITE_IMPLEMENTATION.md`;
- repository tree including the large USB/tuning/WebView implementation and extensive tests.

Important preserved evidence:

- shared USB transport candidate is not board/profile identity;
- exact live TunerStudio signature + exact matching generated INI gate runtime/tuning authority;
- one ECU USB/session owner;
- complete TuneSnapshot authority;
- semantic tune edit requests only;
- exact ACK/range-readback/full-snapshot verification;
- fail-closed uncertainty;
- separate explicit Burn;
- generated INI owns dynamic runtime/tune geometry.

### Original `AKCore/EpicEFIVirtualDash`

Reviewed README/tree, original Android BLE manager and ESP32 firmware.

Observed original protocol includes:

- BLE service `4fafc201-1fb5-459e-8fcc-c5c9c331914b`;
- button/variable-request/variable-data/GPS characteristics;
- little-endian 16-bit button mask;
- big-endian variable hashes/hash+float responses;
- CAN button and variable request/response/set mappings;
- batched variable operations.

Original structure is compact but large-class/one-main.cpp oriented and is not a topology template.

GitHub metadata currently reports no declared repository license, so treat source as behavioural/protocol evidence; do not assume wholesale source transplantation.

### `PJawZK/epicefi_fw_jzfork`

Directly verified:

- `firmware/hw_layer/ports/stm32/serial_over_usb/usbcfg.cpp`: `0483:5740`, shared CDC descriptor/interface shape;
- `firmware/console/binary/signature.cpp`: generated board-specific signature header and `getTsSignature() -> TS_SIGNATURE`;
- `firmware/console/binary/tunerstudio.cpp`: common TunerStudio query/output/configuration protocol implementation and profile-defined output block concept.

The firmware repository is very large and not code-search indexed, so deeper Phase 1 review should use known paths from EpicDash-JZ audits plus targeted directory traversal.

## Current target split

Read `CODEBASE_TOPOLOGY.md` for detailed proposed files/responsibilities.

High-level:

```text
Kotlin/Compose
├─ Android lifecycle/navigation/UI
├─ USB discovery/permission/open
├─ BLE/GATT ESP32 transport
├─ location/storage
├─ semantic ActionRouter/accessory state
└─ tiny NativeEngine façade

Rust
├─ ECU protocol/session
├─ generated profile/INI model
├─ ChannelStore/runtime snapshots
├─ tuning
├─ measurement/MDF4/logging
├─ analysis/expressions/plot preparation
└─ engine diagnostics

ESP32 firmware
└─ physical relay/GPIO/CAN mapping and failsafe authority
```

## Important unresolved questions

Read `GAP_ANALYSIS.md` for the full list. Highest priority:

- final ECU USB runtime-I/O ownership: Kotlin vs batched Rust vs Android-opened FD handed to Rust;
- full decomposition/inventory of current EpicDash-JZ `UsbEcuManager` and INI/profile parser responsibilities;
- deeper firmware command/write/Burn path verification;
- whether/how to evolve original ESP32 protocol for explicit command acknowledgement/state/capability/failsafe reporting;
- minimum Android API/ABI/device baseline;
- permanent Android application ID/namespace;
- MDF4 version/compression baseline after interoperability/performance testing;
- deeper UXUI/Tune Viewer/diagnostics/plugin inventories.

## Constraints / do not regress

- Do not add production Android/Rust scaffold during Phase 1.
- Do not copy historical large classes wholesale.
- Do not use stage/version labels as permanent production names.
- Do not create alternate ECU write/Burn paths.
- Do not treat USB VID/PID or board name as profile/tune authority.
- Do not route ESP32 accessory commands through Rust merely for architectural centralization.
- Do not make UI/dashboard the runtime telemetry authority.
- Do not create additional handoff files.

## Exact next action

Continue the Phase 1 domain inventory with the **current EpicDash-JZ USB/profile/runtime path**:

1. decompose `UsbEcuManager.kt` into all distinct responsibilities and map each to the target topology;
2. inventory `UsbTunerStudioProfile.kt`, generated-INI parsing/compilation and condition evaluation responsibilities;
3. verify corresponding firmware framing/query/output/read/write/Burn source paths directly in `epicefi_fw_jzfork`;
4. update the responsibility inventory/destinations, protocol authorities and codebase topology where evidence changes/refines the current model.

Then proceed domain-by-domain through ESP32/accessory, tuning persistence/recovery, UI/editor, logging/interoperability, diagnostics and future analyzer/plugin sources.
