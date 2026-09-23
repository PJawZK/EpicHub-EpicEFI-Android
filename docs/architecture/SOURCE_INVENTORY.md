# EpicHub Source Inventory

## Purpose

This document records what each reference source is useful/authoritative for. It does not make any historical source-tree structure authoritative for EpicHub.

Evidence is consumed by domain and normalized into `RESPONSIBILITY_INVENTORY.md`.

## Authority vocabulary

- **Protocol authority** — source can define actual wire/file/firmware behaviour.
- **Qualified behaviour evidence** — implementation has meaningful software/physical qualification and is strong behavioural evidence.
- **Reference implementation** — useful example but implementation structure is not retained.
- **Interaction/design reference** — useful for UX/interaction behaviour only.
- **Future-feature reference** — useful algorithms/workflows that may later enter EpicHub.

## Provenance / licence handling rule

Phase 1 records protocol/behaviour semantics and tested evidence. Reference-source code is **not** presumed transplantable merely because it can be read.

- private/user-controlled `PJawZK` repositories are treated as project evidence, while actual copied/dependency code still needs licence review at adoption time;
- `AKCore/EpicEFIVirtualDash` currently exposes no declared repository licence in the reviewed GitHub metadata, so use is limited to behavioural/protocol evidence unless permission/licensing is established separately;
- directly reviewed firmware files contain upstream licence headers, including GPL-licensed TunerStudio protocol implementation and Apache-licensed ChibiOS-derived USB descriptor source; EpicHub uses them to verify protocol reality, not as clean-slate source to transplant;
- external specifications/libraries retain their own licence/usage terms and are reviewed when selected;
- fixture provenance must identify its source and redistribution constraints before committing third-party or private captured artifacts.

## `PJawZK/EpicHub-EpicEFI-Android`

Role: **new authoritative EpicHub implementation repository**.

Current state: Phase 1 architecture/repository archaeology only. No production Android/Rust scaffold yet.

Authority:

- final EpicHub architecture and implementation once accepted;
- current decisions, topology and handoff documents.

Do not import assumptions from older repositories when this repository establishes a newer deliberate contract.

## `PJawZK/EpicDash-JZ`

Role: **primary qualified behavioural/reference source** for the evolved Android application.

Current examined baseline: `main` at `45de2752811edbd66b01441c0cff8311d3e62647` during Phase 1 inventory.

High-value evidence includes:

- Android lifecycle/permissions/USB/BLE/location lessons;
- Mega144H7/shared STM32 USB candidate selection;
- live TunerStudio signature recognition;
- exact generated-INI/profile authority;
- TunerStudio-compatible framed transport behaviour;
- runtime output polling and source/session/generation/freshness semantics;
- complete TuneSnapshot read/persistence model;
- semantic scalar/array/table/curve-cell tuning;
- exact acknowledgement/read-back/full-snapshot verification;
- explicit separate Burn workflow and uncertainty handling;
- lifecycle/reconnect/diagnostics characterisation;
- MSL playback, dashboard/editor, warning/math and tuner interactions;
- extensive regression, fault-injection, bench and physical-acceptance evidence.

Strong safety invariant to preserve: presentation supplies semantic intent; raw ECU page/offset/type/bytes/protocol frames remain below presentation authority.

Structural lessons to **not** preserve wholesale:

- broad Activities/controllers owning unrelated systems;
- `UsbEcuManager.kt` combining many USB/ECU responsibilities;
- dashboard-oriented global state aggregation;
- WebView/JSON/JavaScript/DOM hot path;
- permanent development-stage naming (`T3`, `T4`, `T6`, `W4`, etc.);
- large mixed-purpose tuning/burn classes.

Useful evidence locations include:

- `docs/PROJECT_STATE.md`;
- `docs/ARCHITECTURE.md`;
- `docs/ECU_WRITE_SAFETY_AND_TEST_PLAN.md`;
- `docs/audits/ECU_RECOGNITION_FIRMWARE_EVIDENCE.md`;
- `docs/audits/TUNER_READ_WRITE_IMPLEMENTATION.md`;
- USB/tuning/GPS/BLE regression tests;
- `reference/Mega144H7-usb-descriptors.txt`;
- `reference/ecu/mainController-*.ini`.

Classification: **qualified behaviour evidence + reference implementation**.

## `AKCore/EpicEFIVirtualDash`

Role: **original EpicDash function/protocol intent and ESP32 bridge reference**.

Default branch examined: `master`.

Repository overview establishes the original product as a BLE virtual dashboard with ESP32-S3 BLE-to-CAN bridge, real-time variables and wireless button inputs.

Important protocol evidence currently observed:

- BLE service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`;
- Button characteristic `...a8`: Android → ESP32, 16-bit little-endian button mask;
- VarData characteristic `...a9`: ESP32 → Android, batched `[int32 hash + float32 value]`, big-endian;
- VarRequest characteristic `...aa`: Android → ESP32, batched big-endian hashes;
- GPS/variable-set characteristic `...ab`: phone → ESP32 → CAN;
- CAN button frame at `0x711`, hardware-button-box category 27;
- variable request/response bases `0x700` / `0x720` + ECU ID;
- variable-set/GPS base `0x780` + ECU ID;
- batched variable request/response behaviour.

The original Android application is deliberately compact and places multiple responsibilities in a small set of large classes. The original ESP32 firmware similarly concentrates BLE, CAN, button, variable, GPS/ADC, watchdog and diagnostics behaviour in one `main.cpp`. These are protocol/behaviour references, not desired EpicHub structure.

Provenance note: GitHub repository metadata currently reports no declared repository license. Treat source as behavioural/protocol evidence and do not assume it may be transplanted wholesale into the new clean-slate implementation.

Classification: **protocol/function-intent evidence + reference implementation**.

## `PJawZK/epicefi_fw_jzfork`

Role: **firmware/protocol reality** for the EpicEFI/rusEFI side used by EpicHub.

Default branch: `master`.

Direct Phase 1 verification head: `6a300295650ed131e57d8881afb2f00fd5291d47`. Detailed verification: `ECU_PROTOCOL_DIRECT_VERIFICATION.md`.

Highest-value authority areas:

- shared STM32 serial-over-USB descriptors and CDC topology;
- TunerStudio signature generation/query behaviour;
- common TunerStudio command vocabulary and framing;
- runtime output definitions;
- calibration-page geometry and generated INI data;
- RAM write and Burn semantics;
- board/build identity;
- CAN variable/button support where applicable.

Directly verified during Phase 1:

- VID/PID `0483:5740` and the CDC descriptor/Union/Call-Management topology, including optional-MSD interface-number shift;
- generated `TS_SIGNATURE` returned by `getTsSignature()` and query handling;
- plain and CRC-framed query behavior;
- request framing and CRC over body excluding length;
- response framing as `length(status+payload) + status + payload + CRC(status+payload)`;
- typed read/chunk-write/Burn dispatch, bounded page/range handling and distinct Burn acknowledgement.

Authority conclusions:

- `0483:5740` identifies a shared STM32 firmware transport candidate, not a Mega144H7 model;
- live complete TunerStudio signature is firmware identity evidence;
- exact complete live signature equality with the imported generated-INI signature is the compatibility/tuning authority gate;
- representative current STM32 families use the common TunerStudio command path (`S/Q`, `O`, `R`, `C`, `B`) while generated runtime/page geometry remains profile-specific;
- board name alone must not own runtime/tune geometry.

Classification: **protocol authority**.

## `PJawZK/Epic-DashTune-UXUI`

Role: **interaction/responsive/performance reference** for tuner UI behaviour already explored separately.

Use for:

- interaction patterns;
- responsive phone/tablet behaviour;
- proven editor/graph/table interaction ideas;
- performance techniques worth adapting.

Do not use as:

- visual authority when it conflicts with accepted EpicHub visual direction;
- ECU/profile/protocol authority;
- a source-tree template.

Classification: **interaction/design reference**.

## `PJawZK/PJawZK-EpicEFI-Tune-Viewer`

Role: **qualified read-only reference for tune/profile interpretation, semantic comparison, profile-definition evolution and derived definition packaging**.

Reviewed `main`: `abfa325597d16cb362623c260ec92ed5bf5f37f1` (`1.0.0-rc.1`). Detailed evidence: `TUNE_VIEWER_EVIDENCE.md`.

High-value evidence includes:

- exact-signature definition gating with no nearest-version fallback;
- explicit scalar/bits/array/table/curve/dialog/menu semantic kinds;
- conservative cross-firmware scalar-only comparison and same-firmware table/curve gating;
- structural definition diff with stable property-order-insensitive comparison;
- exact-signature compact definition registry with SHA-256, compression and provenance/count checks;
- asynchronous load-generation/stale-result rejection;
- read-only comparison/reporting boundaries;
- table shape validation and presentation-only axis ordering;
- useful parser/condition construct inventory for independent cross-check fixtures.

Important limits:

- its TypeScript MSQ/INI parsers are not EpicHub's target profile/tune authority;
- its limited condition evaluator is not sufficient for write authorization;
- React/browser structure and SVG renderer do not migrate as the Compose architecture;
- the reviewed viewer contains 2D calibration grids and SVG curves, not a reusable Plotly/3D engine;
- positional deltas with changed table axes are viewer evidence only and are deliberately tightened to non-comparable-by-default in EpicHub;
- catalog/index/browser caches are convenience state, not local project authority.

Classification: **qualified read-only behaviour evidence + reference implementation**.

## Tuning plugin repositories

Reviewed sources and heads:

- `PJawZK/AE-Tuner-EPICEFI-` — `9ac4f5be9508972bb6c2be6396dc9527ca16842b`;
- `PJawZK/epicefi-pid-autotune-plugin_Private` — `a224e75aeb10f36939b34f33db244daab56cc7bf`;
- `PJawZK/epicefi-tcharge-autotune-plugin` — `79777ff4318027f2a874a365cfc78a3ee9bf3dc5`;
- `PJawZK/Injection-Angle-TS-Plugin_Private` — `bedee364528bfa5fe87afe4584d0ddaf9c4cc1f3`.

Detailed evidence: `ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md`.

Role: **future-feature references** for analysis/autotune algorithms, evidence qualification and bounded experimental workflows.

Transferred architecture lessons include:

- evidence/recommendation/mutation authority separation;
- exact source/profile/relevant-calibration provenance;
- direct vs derived/interpolated evidence;
- rejection accounting and unknown-vs-zero semantics;
- independent visit/event blocks and blocked validation;
- segmentation/settling/comparability;
- confidence/identifiability beyond a single score;
- quantization-aware bounded experiments;
- immutable reusable analysis sessions;
- one shared analysis foundation with domain profiles;
- one existing semantic tuning authority for any candidate Apply/Restore.

They are not base application architecture and are not ported as independent plugin mini-apps.

Classification: **future-feature evidence + qualified algorithm/workflow reference at repository-specific maturity levels**.

## `PJawZK/Diagnostics-Core`

Role: **cross-project diagnostic/channel/warning-definition reference**.

Reviewed `main`: `8b497461ebcdef70e9cc0a406e7b987988df7aa3`. Detailed evidence: `DIAGNOSTICS_CORE_EVIDENCE.md`.

Useful lessons:

- stable canonical semantic channel IDs;
- consumer/source alias adapters;
- explicit units/type/freshness/missing validity;
- declarative warning rules with debounce/latching;
- versioned machine-readable schemas;
- executable diagnostic test vectors;
- consumer/report provenance identity.

Limits:

- it is not a second EpicHub runtime ChannelStore;
- generic validity ranges/stale timings are not automatically authoritative for every ECU/vehicle;
- warning definitions remain observational and do not gain hidden reconnect/write/shutdown authority;
- exact generated profile remains the authority for live ECU channel geometry/scaling;
- its KiCad/container tooling is outside the EpicHub Android runtime architecture.

Classification: **reference implementation + definition/test-vector evidence**.

## External specifications/libraries

The following categories require their own authoritative external sources rather than being inferred from old EpicDash code:

- ASAM MDF specification and independently interoperable MDF tooling for MDF4;
- current Android platform/NDK/JNI/USB/BLE/Compose contracts;
- Rust crate documentation for selected dependencies;
- any third-party library licences.

These sources are reviewed when implementation choices become concrete; Phase 1 does not lock a third-party Rust MDF/JNI/etc. library merely to close architecture ownership.