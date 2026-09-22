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

Highest-value authority areas:

- shared STM32 serial-over-USB descriptors and CDC topology;
- TunerStudio signature generation/query behaviour;
- common TunerStudio command vocabulary and framing;
- runtime output definitions;
- calibration-page geometry and generated INI data;
- RAM write and Burn semantics;
- board/build identity;
- CAN variable/button support where applicable.

Already-established firmware evidence from the EpicDash-JZ audit:

- VID/PID `0483:5740` identifies a shared STM32 firmware transport candidate, not a Mega144H7 model;
- live complete TunerStudio signature is firmware identity evidence;
- exact complete live signature equality with the imported generated-INI signature is the compatibility/tuning authority gate;
- representative current STM32 families use the common TunerStudio command path (`S/Q`, `O`, `R`, `C`, `B`) and materially different generated runtime/page geometry;
- board name alone must not own runtime/tune geometry.

Direct Phase 1 firmware traversal remains in progress because the repository is large and code search is not indexed. Existing EpicDash-JZ firmware-evidence audits provide known source paths that will be verified directly.

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

Known relevant repositories include:

- `PJawZK/AE-Tuner-EPICEFI-` / public counterpart;
- `PJawZK/epicefi-pid-autotune-plugin_Private` / public counterpart;
- `PJawZK/epicefi-tcharge-autotune-plugin`;
- `PJawZK/Injection-Angle-TS-Plugin_Private`.

Role: **future-feature references** for analyzer/autotune workflows and algorithms.

They are not base architecture authority. Their useful algorithms should later be translated into the common EpicHub channel/log-analysis model rather than embedded as independent mini-app architectures.

## `PJawZK/Diagnostics-Core`

Role: potential **diagnostics architecture/reference** source.

Status: not yet inventoried in Phase 1.

Classification: **reference implementation** pending review.

## External specifications/libraries

The following categories require their own authoritative external sources rather than being inferred from old EpicDash code:

- ASAM MDF specification and independently interoperable MDF tooling for MDF4;
- current Android platform/NDK/JNI/USB/BLE/Compose contracts;
- Rust crate documentation for selected dependencies;
- any third-party library licences.

These sources will be added to protocol/file-format authority documentation as implementation choices become concrete.