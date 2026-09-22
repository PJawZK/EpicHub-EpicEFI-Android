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

The Kotlin/Rust boundary must operate on semantic operations and bounded snapshots/results, not per-sample, per-channel or per-rendered-primitive calls.

A high-rate ECU stream may remain entirely inside Rust while Compose receives only the latest display-rate snapshot. Analyzer queries should return viewport-sized result packets rather than raw entire datasets.

## D-007 — ESP32/accessory control is a separate domain

The ESP32 path is not part of ECU tune-memory authority.

Android BLE/GATT transport remains Kotlin-owned. User/app actions are semantic and routed to Local, Accessory or ECU owners. The ESP32 firmware remains the final hardware authority for physical relay/GPIO mapping and failsafe behaviour.

Accessory state/sensor data may enter the Rust channel engine for dashboard, logging, analysis, warnings and calculations.

## D-008 — Semantic authority over raw UI access

Presentation code expresses intent. It does not gain raw ECU page/offset/width/byte authority or raw ESP32 GPIO authority.

ECU mutation resolves semantic targets against current firmware/profile/tune authority. ESP32 accessory requests resolve semantic accessory actions against the accessory protocol/firmware.

## D-009 — MDF4 is the planned native measurement format

EpicHub is designed around MDF4 as the long-term native logging/analysis format, behind an internal measurement-store/file-format abstraction so one third-party crate does not become architectural authority.

MSL, MLG and CSV are interoperability/import/export concerns rather than the native internal data model.

## D-010 — One runtime channel model

Dashboard, logger, analyzer, calculations, warnings and relevant tuning tools should consume one authoritative runtime channel model rather than maintain independent dashboard/log-analysis data representations.

## D-011 — Behaviour transfer, not file migration

Phase 1 migrates knowledge: requirements, protocol semantics, safety invariants, test evidence and useful interaction behaviour.

No historical source file/class is presumed to survive wholesale. Large historical classes are decomposed into responsibilities and assigned to permanent descriptive owners.

## D-012 — Permanent descriptive naming

Accepted production code does not use development-stage names such as `T3`, `T4`, `T6`, `B2`, `W4`, `V2` or `New` merely to describe implementation history.

Temporary alternative names are allowed only during an explicit experiment and are removed/renamed before acceptance.

## D-013 — Repository is durable project memory

Current project truth belongs in repository documentation/source, not giant continuation prompts. `docs/HANDOFF.md` is the single current chat continuation index and is replaced/updated at each explicit handoff; Git history provides historical versions.
