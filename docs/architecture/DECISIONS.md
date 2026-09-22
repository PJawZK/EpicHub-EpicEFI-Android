# EpicHub Architecture Decisions

This log records durable architectural decisions. It is not a chronological chat transcript and does not use development-stage labels as code names.

## D-001 — New clean-slate application

EpicHub is implemented in `PJawZK/EpicHub-EpicEFI-Android` as a new application. It does not require package/signing/install/update continuity with EpicDash-JZ.

Earlier repositories are evidence/reference sources. Their directory structures and historical class names are not architectural authority.

## D-002 — Android-only product target

The primary application target is Android. Linux portability is not a design requirement for the application shell.

## D-003 — Kotlin/Compose + Rust hybrid

EpicHub uses Kotlin and Jetpack Compose for Android-facing application/UI work and Rust for sustained high-volume engine work.

The goal is not maximum Rust usage. The goal is to minimize unnecessary runtime crossings and put each responsibility in the environment best suited to it.

## D-004 — Kotlin owns Android-facing integration

Kotlin owns Android framework interactions including application lifecycle, UI, navigation, USB discovery/permission/opening, Android BLE/GATT, location, Storage Access Framework, notifications and other Android services.

## D-005 — Rust owns the high-volume data engine

Rust is the intended owner for the runtime channel engine, high-volume ECU processing where technically justified, MDF4, logging internals, analysis, indexing, calculations/expressions, downsampling/plot preparation and eventually the semantic tuning engine after behavioural parity is proven.

## D-006 — Coarse Kotlin/Rust boundary

The Kotlin/Rust boundary operates on semantic operations and bounded snapshots/results, not per-sample, per-channel or per-rendered-primitive calls.

A high-rate ECU stream may remain entirely inside Rust while Compose receives only the latest display-rate snapshot. Analyzer queries return viewport-sized result packets rather than raw entire datasets.

## D-007 — Legacy ESP32 bridge and future direct outputs are separate capabilities

The reviewed legacy ESP32 is a BLE↔CAN bridge with local inputs, not an existing direct relay/GPIO output controller.

Android BLE/GATT transport remains Kotlin-owned. Legacy bridge actions/variable traffic are implemented through a clean bridge protocol/gateway and one connection-wide GATT operation scheduler. Android GATT completion is transport evidence, not downstream CAN/action confirmation.

If EpicHub later adds direct ESP32 relay/GPIO control, that is a deliberately versioned new capability with explicit acknowledgement/state/fault semantics. For such direct physical outputs, ESP32 firmware owns physical mapping, boot/default state, hardware interlocks and disconnect/watchdog failsafe behavior.

Accessory/bridge state and sensor values may enter the Rust channel engine for dashboard, logging, analysis, warnings and calculations.

## D-008 — Semantic authority over raw UI access

Presentation code expresses intent. It does not gain raw ECU page/offset/width/byte authority, raw BLE characteristic authority as semantic identity, or raw ESP32 GPIO authority.

ECU mutation resolves semantic targets against current firmware/profile/tune authority. Legacy bridge actions resolve through the bridge gateway. Any future direct-output action resolves through its versioned accessory capability.

## D-009 — MDF4 is the planned long-term native measurement format

EpicHub is designed around MDF4 as the long-term native logging/analysis format candidate, behind an internal measurement-store/file-format abstraction so one third-party crate does not become architectural authority.

MSL, MLG and CSV are interoperability/import/export concerns rather than the internal runtime channel model.

MDF version/compression support is accepted only after independent interoperability and Android performance/file-size validation.

## D-010 — One runtime channel model

Dashboard, logger, analyzer, calculations, warnings and relevant tuning tools consume one authoritative runtime channel model rather than independent dashboard/log-analysis representations.

Live and file-backed sources expose compatible channel/source semantics even though their acquisition mechanisms differ.

## D-011 — Behaviour transfer, not file migration

Phase 1 migrates knowledge: requirements, protocol semantics, safety invariants, test evidence and useful interaction behaviour.

No historical source file/class is presumed to survive wholesale. Large historical classes are decomposed into responsibilities and assigned to permanent descriptive owners.

## D-012 — Permanent descriptive naming

Accepted production code does not use development-stage names such as `T3`, `T4`, `T6`, `B2`, `W4`, `V2` or `New` merely to describe implementation history.

Temporary alternative names are allowed only during an explicit experiment and are removed/renamed before acceptance.

## D-013 — Repository is durable project memory

Current project truth belongs in repository documentation/source, not giant continuation prompts. `docs/HANDOFF.md` is the single current chat continuation index and is replaced/updated at each explicit handoff; Git history provides historical versions.

## D-014 — Prove format-neutral live acquisition before file-format implementation

Logger implementation order is deliberate:

```text
1. prove/optimize acquisition → decode → timestamp → ChannelStore/sample-batch path
2. add and optimize MLG writer for rapid TunerStudio/MegaLogViewer interoperability
3. evaluate/implement MDF4 against the exact same sample/channel workload
4. build large-log analyzer capabilities on the common measurement API
```

The live acquisition/channel model is never shaped around MLG or MDF internals.

## D-015 — Mobile logging optimizes storage size first, then I/O efficiency

For native mobile recording choices, the first decision metric is practical log size on devices without expandable storage. Sustained write/read efficiency, CPU/thermal cost and recoverability follow immediately after.

Compression/version choices are selected from measured **size saved per resource cost** on target Android hardware, not desktop expectations.

## D-016 — UI semantic state and presentation interaction state are distinct

Rust/profile/tuning/channel domains own semantic truth such as tune targets/values, runtime channels, selection-resolved edit commands and source/session validity.

Compose owns presentation/interaction state such as responsive layout, gesture arbitration, zoom/pan, panel folding, table display orientation, modal ownership and 3D camera state.

Presentation transformations do not reorder or mutate authoritative tune data. Three-dimensional table inspection remains view-only unless a future separately accepted semantic edit contract is added.