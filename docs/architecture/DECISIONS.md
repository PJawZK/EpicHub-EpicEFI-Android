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

## D-017 — Diagnostics are observational, not control authority

Diagnostics may observe, timestamp, correlate, count, summarize and export subsystem behavior, but the subsystem owner remains the authority that decides state transitions and actions.

Instrumentation must not reconnect transports, invalidate sessions, alter queue/loss policy, change tuning safety decisions or otherwise modify production behavior merely to simplify reporting or improve metrics.

Android/platform diagnostics remain Kotlin-owned; engine diagnostics remain Rust-owned. They share stable event/metric/correlation contracts and cross the native boundary in bounded snapshots/batches rather than per-event or per-sample callbacks.

## D-018 — Full semantic tune comparison requires exact authoritative profile identity

Full semantic comparison of tune settings, arrays, tables or curves is allowed only when both sides resolve through the same exact authoritative profile identity. EpicHub uses its profile/source fingerprints and compiled-profile identity for this gate rather than assuming that equal board names, parameter names or similar firmware versions imply equal semantics.

When profile identities differ, any comparison is explicitly informational and conservative. A shared-name scalar/bit intersection may be presented only when both definitions describe the item compatibly; it never grants edit, migration or ECU-write authority. Cross-profile arrays/tables/curves remain non-comparable unless a separately specified and qualified compatibility or transformation contract exists.

If table axes differ, direct positional cell deltas are considered ambiguous by default. Any future regridding/interpolation comparison is derived analysis with explicit transform policy and cannot silently become source tune data or migration/write authority.

Compact compiled-profile projections and comparison reports are derived acceleration/evidence only. They remain exact-identity-bound, versioned/rebuildable where appropriate, and never replace the source profile, TuneSnapshot, project authority or live ECU verification.

## D-019 — Observation, analysis, recommendation and ECU mutation are separate authority levels

An analyzer consumes identity-bound evidence and may produce derived measurements/models, confidence/identifiability state and an optional semantic recommendation. None of those stages implicitly grants ECU mutation authority.

Missing/stale/unsupported evidence remains unknown rather than numerical zero. Rejected/contaminated samples and non-comparable events remain explicit. Direct observations, values derived from observations, interpolated guidance, model predictions and reference-only evidence retain distinct provenance; interpolation or prediction may guide collection but cannot silently create observed evidence or raise mutation eligibility.

Completed analysis results bind to their immutable source/live-session identity, exact profile identity, relevant calibration fingerprint, channel/schema binding and analysis-profile version. A domain profile defines which upstream tune/configuration state is causally relevant; changing that state invalidates dependent evidence rather than silently pooling it.

Any candidate test that changes ECU RAM is handed to the single semantic tuning authority. The analyzer never owns raw page/offset/byte writes and never owns Burn. Current live identity, stale baseline, target resolution/quantization, exact verification and Restore/uncertainty semantics remain tuning-domain responsibilities.

## D-020 — One shared analysis platform, multiple domain analysis profiles

EpicHub does not port AE, PID, TCharge and Injection Angle as independent plugin architectures. They become domain profiles/models on common Rust analysis infrastructure.

Shared infrastructure owns source/provenance handling, semantic channel requirements, validity/rejection accounting, event/segment/visit blocks, grouped statistics, comparability, coverage, blocked validation, generic bounded search helpers, replay and report structures. Each domain profile retains its own causal prerequisites, event semantics, contamination rules, metrics/model equations, comparability policy, safety gates and recommendation policy.

A profile's maturity is explicit: observing or measuring correctly does not imply that a numerical recommendation is qualified. Live passive capture, guided capture, existing-log analysis and bounded active experiment are distinct acquisition capabilities over the same analysis contract.

A qualifying immutable recorded session may be reused by multiple compatible analyses rather than forcing a new drive/capture for every task. New evidence is required only when required channels/events/coverage are absent, the relevant calibration fingerprint changed, causal prerequisites differ, or a deliberate candidate change creates a new physical state whose outcome must be measured.

## D-021 — Initial Android identity/support baseline is dual-ARM and API 26+

The clean EpicHub Android application starts with application ID and namespace `com.epicefi.epichub`.

The initial support contract is `minSdk 26`, `targetSdk 36` and `compileSdk 37`, with target/compile SDK expected to advance as Android distribution/platform requirements evolve.

Production native support includes both `armeabi-v7a` and `arm64-v8a`. Arm64-only is explicitly rejected for the initial product because the existing SM-A137F reference phone may run a 32-bit Android userspace even though its SoC is 64-bit-capable. The first physical scaffold must record `Build.SUPPORTED_ABIS` on every reference device and qualify JNI/native behavior on both ARM ABIs.

Prefer ABI-split artifacts: App Bundle splitting for store distribution and ABI-specific APKs for direct distribution. A universal dual-ABI APK is a bench convenience, not the preferred size-efficient user artifact.

Exact AGP/NDK/Rust/Compose patch versions are reproducibly pinned when Phase 2 scaffolds the build. Toolchain patch/version maintenance does not reopen this architecture decision unless it changes the supported API/ABI/product contract.