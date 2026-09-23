# EpicHub Responsibility Inventory

## Purpose

This inventory records permanent behaviours/responsibilities, not historical classes. Historical files are evidence sources only: one old class may split across many target owners and several old mechanisms may merge into one clean responsibility.

Classification values: `PRESERVE`, `REDESIGN`, `MERGE`, `SPLIT`, `REFERENCE`, `REMOVE`.

Evidence levels: `PROVEN`, `SUPPORTED`, `PROPOSED`.

Phase 1 established this inventory and is closed. The inventory now acts as an **accepted responsibility map for Phase 2**. It is updated only when later evidence or a deliberate architecture decision changes/adds a responsibility. `Future owner` below means the intended permanent owner for responsibilities not yet implemented; it does not imply that the already-qualified foundation is still hypothetical.

Current Phase-2 foundation facts that refine the original inventory:

- Kotlin/Compose host exists and is qualified;
- coarse JNI `NativeEngine` lifecycle exists and is qualified;
- minimal Rust `ChannelStore` exists and is qualified with synthetic data;
- packed/versioned RuntimeSnapshot ABI v1 exists and is qualified on SM-A137F + SM-T500;
- D-023 locks production runtime to Kotlin/Compose + Rust and keeps JSON/WebView/browser-style serialization out of runtime/JNI state;
- UI shell and diagnostics capture responsibilities are now explicitly defined by `UI_FOUNDATION_LAYOUT.md` and `DIAGNOSTICS_CAPTURE_FOUNDATION.md`.

---

# Android application / presentation platform

## `android.lifecycle.application`

Purpose: own Android application/process lifecycle without owning ECU/BLE/tuning business logic.

Historical evidence: EpicDash-JZ application/activity lifecycle work and lifecycle diagnostics.

Classification: `REDESIGN`. Evidence: lifecycle/duplicate-owner need `PROVEN`; clean foundation owner now implemented at minimal scope.

Permanent owner: Kotlin app/platform layer.

## `android.navigation.presentation`

Purpose: route Dashboard, Tuner, Logger, Analyzer, Diagnostics and Settings without becoming data authority.

Classification: `REDESIGN`. Evidence: `SUPPORTED`; current concrete shell contract `PROPOSED`/accepted-design in `UI_FOUNDATION_LAYOUT.md` pending implementation.

Permanent owner: Compose navigation.

## `android.ui.global-diagnostic-capture`

Purpose: make recent-problem/reproduction capture reachable from the common application shell without navigating away from the failing surface.

Classification: `ADD`. Evidence: architecture need `PROPOSED` and current design accepted for the active slice.

Permanent owner: Compose shell/diagnostics presentation; capture data authority remains diagnostics owners below.

---

# Android USB platform

## `android.usb.discovery`

Purpose: enumerate Android USB devices and identify safe transport candidates before probing.

Invariant: `0483:5740` plus expected CDC shape is transport-candidate evidence only, not ECU/profile authority.

Classification: `PRESERVE` behaviour / `SPLIT` old implementation. Evidence: `PROVEN`/firmware-supported.

Future owner: Kotlin USB platform layer.

## `android.usb.permission`

Purpose: permission request/result lifecycle, duplicate-request prevention and stale-result rejection.

Classification: `PRESERVE` behaviour / `SPLIT`. Evidence: `SUPPORTED`.

Future owner: Kotlin USB platform layer.

## `android.usb.cdc-topology`

Purpose: resolve CDC communications/data interface relationship and bulk IN/OUT endpoints from actual descriptors rather than hardcoded interface numbers.

Evidence: EpicDash-JZ descriptor parsing; firmware optional MSD changes CDC interface numbering.

Classification: `PRESERVE` behaviour / `SPLIT`. Evidence: `PROVEN`/firmware-supported.

Future owner: Kotlin `CdcTopologyResolver`-type component.

## `android.usb.cdc-control`

Purpose: interface claiming/control setup such as `SET_LINE_CODING` and DTR/RTS independently of ECU command framing.

Classification: `PRESERVE` compatibility behaviour / `SPLIT`. Evidence: `SUPPORTED` by physically exercised current path.

Future owner: Kotlin Android CDC control component.

## `android.usb.connection-open`

Purpose: open/close `UsbDeviceConnection`, claim/release selected interfaces and expose the bounded resource needed by the sustained-I/O strategy.

Classification: `REDESIGN` boundary while preserving proven Android resource lifecycle.

Evidence: existing Kotlin path `PROVEN`; native-FD/handle sustained-I/O alternative `PROPOSED`.

Future owner: Kotlin setup/resource boundary. Sustained bulk-I/O owner remains a benchmark decision.

---

# ECU transport / protocol / identity

## `ecu.transport.session`

Purpose: one authoritative ECU communication session with protocol state, exclusivity and clean disconnect invalidation.

Invariant: no independent dashboard/tuner USB stacks and no second tune-snapshot authority.

Classification: `PRESERVE` behaviour / `SPLIT`. Evidence: `PROVEN`.

Future owner: Rust engine after transport handoff.

## `ecu.transport.generation`

Purpose: bind asynchronous work to a connection/session generation so stale permission/reconnect/probe work cannot revive a dead session.

Classification: `PRESERVE` semantics / `REDESIGN` placement. Evidence: `PROVEN` need.

Future owner: Kotlin before native session creation; Rust for active ECU session generation.

## `ecu.transport.retry-policy`

Purpose: distinguish retryable transport loss from profile/protocol/compatibility failures that must stop.

Classification: `PRESERVE` intent / `REDESIGN` pure policy. Evidence: `SUPPORTED`.

Future owner: ECU session policy.

## `ecu.protocol.request-framing`

Purpose: encode CRC-framed TunerStudio requests.

Contract: `BE length(command+data) + command/data + BE CRC32(command+data)`.

Classification: `PRESERVE` protocol / `REDESIGN` implementation. Evidence: `PROVEN` from firmware.

Future owner: Rust request codec.

## `ecu.protocol.response-framing`

Purpose: decode CRC responses and keep response status separate from payload.

Contract: `BE length(status+data) + status + data + BE CRC32(status+data)`.

Finding: request and response envelopes are asymmetric; one symmetric generic `envelope()` abstraction is rejected.

Classification: `PRESERVE` protocol / `REDESIGN`. Evidence: `PROVEN`.

Future owner: Rust response codec.

## `ecu.protocol.status-errors`

Purpose: represent OK/Burn-OK/error status as typed protocol state rather than heuristically stripping payload bytes.

Classification: `PRESERVE` semantics / `REDESIGN`. Evidence: `PROVEN` firmware-side.

Future owner: Rust protocol-response model.

## `ecu.protocol.sync-probe`

Purpose: establish/verify protocol synchronization using bounded read-only operations before runtime streaming.

Evidence: plain/framed `S/Q`, framed signature sync and bounded `O` probes; firmware `in_sync` state.

Invariant: profileless recognition ends after identity and close; no output/TuneSnapshot/mutation authority.

Classification: `PRESERVE` safety intent / `REDESIGN`. Evidence: `SUPPORTED`/firmware-backed.

Future owner: Rust ECU session/probe policy.

## `ecu.protocol.command-operations`

Purpose: expose typed supported operations rather than arbitrary raw command frames.

Current audited operations: `S/Q`, `O`, `R`, `C`, `B`.

Classification: `PRESERVE` vocabulary / `REDESIGN` API. Evidence: `PROVEN`.

Future owner: Rust ECU protocol layer.

## `ecu.identity.signature-query`

Purpose: perform read-only live TunerStudio signature query.

Classification: `PRESERVE`. Evidence: `PROVEN`.

Future owner: Rust identity/session layer.

## `ecu.identity.recognition`

Purpose: report firmware family/board/build diagnostically without granting profile/tuning authority.

Invariant: parsed board name is descriptive only.

Classification: `PRESERVE` semantics / `REDESIGN`. Evidence: `SUPPORTED`.

Future owner: Rust ECU identity layer.

## `ecu.profile.exact-match`

Purpose: require exact complete live signature equality with imported generated-INI signature before runtime/tuning authority.

Classification: `PRESERVE`. Evidence: `PROVEN`.

Future owner: Rust profile/session authority.

---

# Generated INI / profile

## `profile.ini.syntax-parse`

Purpose: parse sections, assignments, escaped strings, top-level lists, dimensions, defines and condition-source strings from the exact generated INI.

Classification: `PRESERVE` required behaviour / `SPLIT` + `REDESIGN`. Evidence: `PROVEN` for current supported profiles.

Future owner: Rust profile parser/model.

## `profile.support-policy`

Purpose: explicitly decide whether a parsed INI is within EpicHub's supported TunerStudio/profile subset.

Current evidence includes `msEnvelope_1.0`, little endian, valid query/output command geometry and supported output/tune primitive constructs.

Invariant: syntax success alone does not grant compatibility; unsupported constructs fail explicitly.

Classification: `ADD FORMAL OWNER`. Evidence: embedded checks `PROVEN`; clean split `PROPOSED`.

Future owner: Rust profile support policy/compiler gate.

## `profile.runtime-schema`

Purpose: compile output channel types/offsets/scales/bits plus output request/block geometry.

Classification: `PRESERVE` / `SPLIT`. Evidence: `PROVEN`.

Future owner: Rust runtime schema.

## `profile.tune-storage-schema`

Purpose: compile calibration pages, IDs, sizes, read/Burn commands, primitive storage metadata, bounds and scale/translate.

Classification: `PRESERVE` / `SPLIT`. Evidence: `PROVEN`.

Future owner: Rust tune schema.

## `profile.tuner-surface-schema`

Purpose: compile tables/curves/menus/dialogs into semantic structure without inventing tune values.

Classification: `PRESERVE` structural authority / `MERGE` + `REDESIGN`. Evidence: `SUPPORTED`/tested.

Future owner: Rust profile compiler/tuner schema.

## `profile.schema-canonicalization`

Purpose: reject ambiguity, canonicalize equivalent definitions, validate page bounds and table/curve dimensions.

Classification: `PRESERVE` behaviour / `SPLIT`. Evidence: `SUPPORTED`.

Future owner: Rust profile compiler.

## `profile.primitive-decode`

Purpose: decode profile-defined little-endian primitive scalar/bit/array values independently of presentation.

Classification: `MERGE` + `REDESIGN`. Evidence: `PROVEN` current path.

Future owner: Rust profile primitive module.

## `profile.condition-parse`

Purpose: parse generated-INI compatibility condition grammar.

Classification: `PRESERVE` capability / `SPLIT`. Evidence: `SUPPORTED`.

Future owner: Rust profile condition parser.

## `profile.condition-evaluate`

Purpose: evaluate supported conditions against explicitly decoded values from a complete verified TuneSnapshot.

Invariant: unsupported/unresolved expressions fail closed for affected controls/targets.

Classification: `PRESERVE` safety semantics / `SPLIT`. Evidence: `SUPPORTED`.

Future owner: Rust profile condition evaluator.

## `profile.condition-target-authority`

Purpose: derive active/hidden/disabled/unsupported semantic target state and write eligibility from menu/dialog/surface conditions.

Finding: conditions are not presentation-only metadata; they participate in write authorization.

Classification: `PRESERVE` safety behaviour / `REDESIGN` API. Evidence: `SUPPORTED`/tested.

Future owner: Rust profile condition authority consumed by tuning policy.

## `profile.fingerprint`

Purpose: exact source identity binding for compiled/cached profile state.

Classification: `PRESERVE`. Evidence: `PROVEN`.

Future owner: Rust profile domain.

Invariant across the profile domain: exact generated INI is structural/schema authority; board name does not own geometry.

---

# Runtime channels / telemetry

## `runtime.output.poll`

Purpose: request live ECU output bytes under exact profile/session authority.

Classification: `PRESERVE` behaviour / `REDESIGN`. Evidence: `PROVEN`.

Future owner: Rust ECU/runtime engine.

## `runtime.output.decode`

Purpose: decode output bytes strictly from exact profile metadata.

Classification: `PRESERVE` semantics / `REDESIGN`. Evidence: `PROVEN`.

Future owner: Rust channel/profile engine.

## `runtime.channel.store`

Purpose: sole high-volume mutable authority for current channel values, timestamps, revisions, sources and validity.

Finding: telemetry is domain-neutral, not Dashboard-owned.

Classification: `MERGE` + `REDESIGN`.

Evidence: need `SUPPORTED`; **minimal Rust owner now implemented and device-qualified with synthetic channels**. Live ECU/file-backed schemas and full source/validity breadth remain later implementation work.

Permanent owner: Rust channel engine.

## `runtime.snapshot.display`

Purpose: bounded immutable latest-state snapshots to Compose at display cadence, independent of ECU sample rate.

Classification: `REDESIGN`.

Evidence: bounded-latest semantics `PROVEN`; **packed RuntimeSnapshot ABI v1 and direct-buffer Kotlin decode now implemented, CI-qualified and physically exercised on both reference devices**. Future expansion remains versioned and measurement-driven.

Permanent owner: Rust producer + Kotlin typed native-buffer adapter.

## `runtime.source.freshness`

Purpose: distinguish live/demo/playback/disconnected/stale state using generation/revision/timestamps and deliberate exclusive operations.

Classification: `PRESERVE` semantics / `REDESIGN`. Evidence: `PROVEN`.

Future owner: Rust runtime/session model projected to UI.

## `runtime.math.derived-channels`

Purpose: one calculation system over the same channel model used by live/log analysis.

Classification: `PRESERVE` capability / `REDESIGN`. Evidence: existing feature `SUPPORTED`; unified engine `PROPOSED`.

Future owner: Rust expression engine.

## `runtime.language.serialization-boundary`

Purpose: prevent serialization/browser-era implementation mechanisms from becoming runtime architecture.

Rules:

- production runtime languages are Kotlin/Jetpack Compose + Rust;
- runtime/JNI state uses typed values and packed/versioned ABI structures;
- JSON is not a default internal message/state representation;
- serialization exists only at explicit persistence/export/interoperability boundaries with a named schema/codec owner.

Classification: `ADD` via D-023. Evidence: historical performance/complexity issues `SUPPORTED`; current RuntimeSnapshot implementation `PROVEN` at foundation scope.

Permanent owners: each domain owns its typed runtime state; boundary codecs belong to the explicit persistence/export/interop owner.

---

# ESP32 legacy bridge / Android BLE

## `accessory.ble.discovery`

Purpose: discover candidate ESP32 bridge devices using Android BLE APIs, then validate service identity before declaring compatibility.

Evidence: original/evolved BLE managers. Legacy name `ESP32 Dashboard` is a hint; service UUID is stronger protocol evidence.

Classification: `PRESERVE` capability / `SPLIT`. Evidence: `PROVEN` in evolved app.

Future owner: Kotlin Android BLE layer.

## `accessory.ble.connection`

Purpose: own GATT connect/disconnect, service discovery, required-characteristic validation, notification subscription, connection priority/MTU and stale-GATT cleanup.

Classification: `PRESERVE` capability / `SPLIT`. Evidence: `PROVEN`/well characterized in EpicDash-JZ.

Future owner: Kotlin `Esp32GattClient`-type component.

## `accessory.ble.reconnect-policy`

Purpose: decide scan/retry/backoff separately from GATT mechanics.

Evidence: guarded scan windows, adaptive scan mode, connection timeout, 5/10/20/30-second backoff and manual/automatic reconnect behavior.

Classification: `SPLIT` + `REDESIGN`. Evidence: need `PROVEN`; pure-policy form `PROPOSED`.

Future owner: Kotlin BLE policy.

## `accessory.ble.operation-scheduler`

Purpose: provide **one connection-wide serialized GATT operation owner** for characteristic writes/descriptors and future operations, with message class/priority/backpressure metadata where justified.

Historical evidence: EpicDash-JZ has three separate FIFO queues/`isWriting` flags for button, variable-request and variable-set/GPS traffic.

Finding: separate per-characteristic queues are useful evidence for buffering need, but are rejected as the final ownership model because they do not express one connection-wide GATT operation order.

Classification: `MERGE` + `REDESIGN`. Evidence: serialization need `SUPPORTED`; final scheduler `PROPOSED`.

Future owner: Kotlin `BleWriteScheduler`/GATT operation scheduler.

## `accessory.protocol.legacy-button-bridge`

Purpose: cleanly encode/decode legacy BLE button-mask compatibility and its CAN forwarding semantics.

Wire evidence:

- BLE preferred form: 16-bit little-endian mask;
- original firmware also accepts one-byte button ID as legacy shortcut;
- CAN ID `0x711`;
- CAN payload `[0x5A, 0x00, 27, maskHi, maskLo]`;
- ESP32 forwards only when mask changes.

Classification: `PRESERVE` compatibility where product requires it / `SPLIT` from GATT lifecycle.

Evidence: `SUPPORTED` by original Android+firmware source.

Future owner: Kotlin accessory protocol/gateway.

## `accessory.protocol.legacy-variable-request`

Purpose: encode batched BE variable hashes, bridge them sequentially to CAN requests and decode returned batched hash+float entries.

Legacy evidence:

- up to 16 hashes per ESP32 batch;
- CAN request ID `0x701` for current ECU ID 1;
- CAN response ID `0x721`;
- response entry `[hash BE4][float BE4]`;
- 100 ms per-variable timeout;
- final notification may contain a partial batch.

Classification: `PRESERVE` compatibility semantics where retained / `REDESIGN` app request/correlation model.

Evidence: `SUPPORTED`.

Future owner: Kotlin accessory bridge codec/gateway.

## `accessory.protocol.legacy-variable-set`

Purpose: send generic `[hash BE4][raw-value BE4]` entries from Android through ESP32 to CAN variable-set ID `0x781` (current ECU ID 1).

Finding: the characteristic historically called `GPS_DATA` is **not GPS-specific**. GPS, packed GPS and phone virtual ADC values all use this generic wire path.

Classification: `PRESERVE` wire compatibility / `RENAME` + `SPLIT` semantics.

Evidence: `PROVEN` by direct firmware source.

Future owner: Kotlin `Esp32VariableSetCodec`-type component; GPS and other producers remain separate domains.

## `accessory.bridge.request-correlation-status`

Purpose: make app-side request identity, pending/timeout/partial-result state explicit instead of copying accidental legacy assumptions.

Historical implementation limitations:

- new variable batch can be dropped while one is active;
- returned CAN hash is not explicitly checked against the expected pending hash before advancing;
- BLE notification has no application-level delivery acknowledgement.

Classification: `REDESIGN`. Evidence: legacy limitations `PROVEN`; improved app model `PROPOSED`.

Future owner: Kotlin accessory gateway/state.

## `accessory.bridge.disconnect-release-semantics`

Purpose: explicitly define what a BLE disconnect means for the legacy button mask/downstream CAN consumer.

Finding: reviewed original firmware does not clear `lastButtonMask` and does not send a zero/release CAN frame on disconnect.

Invariant: do not invent a release behavior until the downstream ECU/button-box consumer semantics are verified. Do not present transport disconnect as confirmed downstream release.

Classification: `ADD FORMAL CONTRACT`.

Evidence: absence in legacy firmware `PROVEN`; correct product behavior remains `PROPOSED` pending verification.

Future owner: compatibility policy + ESP32 bridge firmware if a change is deliberately made.

## `accessory.bridge.local-adc-input`

Purpose: preserve/evaluate the ESP32 local ADC bridge capability independently of BLE action semantics.

Legacy behavior: GPIO 5, nominal 100 Hz, 12-bit read scaled to 0–1023, change threshold 5, published through the CAN variable-set path.

Classification: `REFERENCE` / `PRESERVE` only if still product-relevant.

Evidence: `PROVEN` source behavior.

Future owner: ESP32 bridge firmware; returned/observed value may enter common runtime channels.

## `accessory.bridge.local-digital-input`

Purpose: preserve/evaluate local digital input forwarding independently of UI actions.

Legacy behavior: GPIO 1 active-low, nominal 50 Hz sampling, 50 ms debounce, current bit 0 only.

Caution: reviewed source marks the digital-input variable hash as TODO; that numeric hash is not permanent protocol authority without independent ECU/profile evidence.

Classification: `REFERENCE` / `PRESERVE` only if still product-relevant.

Evidence: source behavior `PROVEN`; final hash/contract not established.

Future owner: ESP32 bridge firmware.

## `accessory.bridge.firmware-liveness`

Purpose: keep the bridge firmware responsive/recoverable.

Legacy evidence: 5-second ESP task watchdog and restart of BLE advertising after disconnect.

Finding: watchdog is firmware-liveness protection, **not** proof of a downstream output-safe state.

Classification: `PRESERVE` concept / `REDESIGN` exact firmware implementation as needed.

Evidence: `PROVEN` source behavior.

Future owner: ESP32 firmware.

## `accessory.action.semantic-routing`

Purpose: bind UI/input to stable semantic actions rather than characteristic UUIDs, button bits, CAN IDs or GPIOs.

For legacy bridge actions the result may only be `queued`/`transport-completed`; downstream state remains unknown unless independently observed.

Classification: `PROPOSED`. Evidence: `PROPOSED` clean architecture need.

Future owner: Kotlin ActionRouter + bridge gateway.

## `accessory.state.legacy-transport-vs-downstream`

Purpose: distinguish BLE/GATT transport observations from downstream ECU/CAN state.

Invariant: GATT acceptance/completion is not an application-level acknowledgement that the ECU/CAN consumer applied a button/action.

Classification: `ADD`.

Evidence: acknowledgement absence `PROVEN`.

Future owner: Kotlin accessory/bridge state model.

## `accessory.output.versioned-direct-control`

Purpose: represent **future** direct ESP32 relay/GPIO outputs as a separate versioned capability rather than pretending they exist in the legacy bridge.

Required before such a capability exists:

- protocol version/capability negotiation;
- semantic output IDs independent of raw GPIO;
- application-level command acknowledgement;
- confirmed hardware state/fault reporting as required;
- boot/default state;
- electrical/interlock policy;
- watchdog/disconnect fallback.

Classification: `PROPOSED NEW CAPABILITY`.

Evidence: legacy absence `PROVEN`; design `PROPOSED`.

Future owner: ESP32 firmware + Kotlin accessory gateway. Physical fail-safe authority remains firmware-side.

## `accessory.telemetry.to-channels`

Purpose: publish returned bridge variables/local-input states into the common runtime channel model without making the BLE manager a telemetry authority.

Classification: `REDESIGN`. Evidence: legacy returned-value path `SUPPORTED`; unified channel integration `PROPOSED`.

Future owner: Kotlin bridge gateway → coarse Rust channel ingestion.

---

# Location / GPS

## `location.acquire`

Purpose: acquire Android location samples with explicit timestamps/quality.

Classification: `PRESERVE` capability / `REDESIGN` boundary. Evidence: `PROVEN` aspects in EpicDash-JZ.

Future owner: Kotlin Android location source.

## `location.map-runtime-channels`

Purpose: expose GPS as typed runtime channels without conflating acquisition and display.

Classification: `PRESERVE` capability / `REDESIGN`. Evidence: `PROVEN` mapping behavior.

Future owner: Kotlin→Rust channel ingestion.

## `location.encode-legacy-variable-set`

Purpose: encode GPS-specific float/packed values into the **generic legacy variable-set** bridge format when that compatibility path remains required.

Classification: `PRESERVE` compatibility capability / `RENAME` + `SPLIT` old GPS-specific transport naming.

Evidence: `SUPPORTED`/historically implemented.

Future owner: Kotlin `GpsVariableEncoder` feeding the accessory variable-set gateway.

---

# Tuning authority / persistence

## `tuning.operation.exclusive-owner`

Purpose: serialize tune Read/RAM-write/Burn against ordinary output polling on one ECU session.

Invariant: deliberate exclusivity is represented in freshness state so it does not look like failed streaming.

Classification: `PRESERVE` semantics / `REDESIGN`. Evidence: `PROVEN` physically.

Future owner: Rust ECU/tuning transaction gate.

## `tuning.snapshot.read-complete`

Purpose: read a complete bounded TuneSnapshot from exact profile/session authority and INI-derived `R` plan.

Classification: `PRESERVE`. Evidence: `PROVEN` physically.

Future owner: Rust tuning/session engine.

## `tuning.snapshot.immutable-authority`

Purpose: complete verified tune image/fingerprint as immutable semantic/write-verification authority.

Classification: `PRESERVE`. Evidence: `PROVEN`.

Future owner: Rust tuning model.

## `tuning.project.persist-profile-snapshot`

Purpose: persist exact imported INI plus matching complete verified TuneSnapshot, with compiled projections only as identity-bound acceleration.

Classification: `PRESERVE` principle / `REDESIGN` storage. Evidence: `PROVEN` current architecture.

Future owner: Rust project/tuning model plus Kotlin storage adapter where needed.

## `tuning.semantic.target-resolution`

Purpose: resolve scalar/array/table/curve/bit-enum semantic targets into current exact-profile storage metadata below UI.

Classification: `PRESERVE`. Evidence: `PROVEN` physically for mixed target types.

Future owner: Rust tuning engine.

## `tuning.value.codec`

Purpose: engineering↔raw calibration conversion according to exact INI metadata.

Classification: `PRESERVE` semantics / `REDESIGN`. Evidence: `PROVEN`/tested.

Future owner: Rust tuning codec reusing profile primitive metadata.

## `tuning.write.condition-authority`

Purpose: require profile condition authority to allow a target before a write plan may include it.

Classification: `PRESERVE` safety semantic. Evidence: `SUPPORTED`/tested.

Future owner: Rust tuning safety policy.

## `tuning.write.plan`

Purpose: validate identity, semantic targets, uniqueness/range/non-overlap and construct complete expected post-write snapshot before transmission.

Classification: `PRESERVE`. Evidence: `PROVEN`.

Future owner: Rust planner.

## `tuning.write.baseline-reread`

Purpose: immediately before any possible `C`, reread complete tune and require exact planned baseline fingerprint.

Classification: `PRESERVE`. Evidence: `PROVEN` physically.

Future owner: Rust transaction preflight.

## `tuning.write.transaction`

Purpose: bounded `C` write + ACK + exact `R` range readback + final complete expected TuneSnapshot verification + dirty-page update.

Classification: `PRESERVE` safety semantics / `REDESIGN`. Evidence: `PROVEN` physically.

Future owner: Rust tuning transaction through sole ECU session.

## `tuning.write.uncertainty`

Purpose: fail closed after possible transmission with incomplete proof; no automatic replay until deliberate complete resynchronization.

Classification: `PRESERVE`. Evidence: `PROVEN`.

Future owner: Rust tuning/session state.

## `tuning.burn.transaction`

Purpose: separate explicit `B` Burn with current INI page authority, flash readiness/progression evidence, complete post-Burn reread and dirty-page clearing.

Classification: `PRESERVE`. Evidence: `PROVEN` physically.

Future owner: Rust tuning/Burn module.

## `tuning.ui.semantic-editing`

Purpose: present parameters/curves/tables and submit semantic edits with no raw memory/protocol authority.

Classification: `PRESERVE` boundary / `REDESIGN` Compose presentation. Evidence: behavior `SUPPORTED`; new UI `PROPOSED`.

Future owner: Compose tuner UI + Rust typed tune model/operations.

---

# Logging / interoperability

## `interop.msl.playback`
Purpose: MSL playback/import as a source feeding common channels. Classification: `PRESERVE` capability / `REDESIGN`. Future owner: Rust interop.

## `interop.csv.import`
Purpose: CSV import without making CSV the internal model. Classification: `PRESERVE`. Future owner: Rust interop.

## `interop.mlg`
Purpose: initial TunerStudio/MegaLogViewer compatibility path where useful during transition/testing. Classification: `PROPOSED`. Future owner: Rust interop.

## `logging.session.record`
Purpose: live recording session independently of concrete file writer. Classification: `PROPOSED`. Future owner: Rust logging.

## `logging.format.mdf4`
Purpose: MDF4 read/write/recovery behind the internal measurement abstraction after interoperability/performance qualification. Classification: `PROPOSED`. Future owner: Rust measurement/MDF implementation; exact crate split remains implementation-driven.

---

# Analyzer / plotting

## `analysis.query`
Purpose: bounded channel/time queries against live/file-backed sources. Classification: `PROPOSED`. Future owner: Rust analysis.

## `analysis.events-search`
Purpose: search/filter/event detection over channel data. Classification: `PROPOSED`. Future owner: Rust analysis/expression engine.

## `plot.downsample-resolution`
Purpose: spike-preserving multires min/max viewport data. Classification: `PROPOSED`. Future owner: Rust plotting.

## `plot.compose-render`
Purpose: render prepared plot packets in Compose; specialized GPU only if measurements justify it. Classification: `PROPOSED`.

---

# Diagnostics / performance

## `diagnostics.android-usb`
Purpose: Android permission/device/interface/endpoint/open/claim/control-transfer facts. Classification: `SPLIT`. Future owner: Kotlin USB diagnostics.

## `diagnostics.ecu-protocol-session`
Purpose: sync, command/status/CRC/rates/timeouts/session-generation facts. Classification: `SPLIT` + `REDESIGN`. Future owner: Rust diagnostics.

## `diagnostics.tuning-transaction`
Purpose: semantic tune Read/Write/Burn state, uncertainty, fingerprints and verification evidence. Classification: `SPLIT`. Future owner: Rust tuning diagnostics/events.

## `diagnostics.ble-connection-queue`
Purpose: scan/connect/retry/GATT operation depth/latency/drop/error metrics without embedding action semantics. Classification: `PRESERVE` diagnostic need / `REDESIGN` around one global scheduler. Future owner: Kotlin BLE diagnostics.

## `diagnostics.structured-events`
Purpose: bounded structured events across Android and Rust with correlation/session IDs. Classification: `MERGE` + `REDESIGN`. Evidence: need `PROVEN`; unified model `PROPOSED`.

## `diagnostics.performance-metrics`
Purpose: frame/jank, ECU rates, JNI crossings/bytes, queue/drop, logging throughput, analyzer latency, CPU/memory/thermal metrics. Classification: `PRESERVE` measurement discipline / `REDESIGN` architecture.

## `diagnostics.flight-recorder`

Purpose: bounded always-on recent diagnostic evidence with deterministic eviction and no blocking disk I/O on hot paths.

Classification: `ADD`. Evidence: `PROPOSED` active Phase-2 design based on mature historical diagnostic evidence.

Permanent owners: bounded Rust engine ring + bounded Kotlin platform ring/aggregation as appropriate; no second domain authority.

## `diagnostics.capture-session`

Purpose: immutable recent-problem/reproduction/previous-exit capture containing correlated typed events, metric summaries, lifecycle owners, markers and bounded contextual state.

Classification: `ADD`. Evidence: `PROPOSED` active Phase-2 design.

Permanent owner: diagnostics capture model; UI only controls capture interaction/review.

## `diagnostics.capture-export`

Purpose: explicit export boundary for a finalized diagnostic capture.

Rules:

- canonical machine representation is versioned compact binary CBOR;
- human `report.txt` is rendered from the same immutable typed capture;
- archive manifest/integrity are versioned;
- routine captures exclude full logs/tunes/GPS/secrets unless explicitly attached;
- JSON is not runtime authority and is not the canonical diagnostic archive format.

Classification: `ADD` under D-023.

Permanent owner: typed diagnostics schema/codec + Kotlin Android document/share transport.

---

# Historical ownership models explicitly removed

## `presentation.webview-json-dom-hot-path`
Classification: `REMOVE`. Replacement: Compose + typed packed native state.

## `runtime.json-message-bus`
Classification: `REMOVE`. Replacement: typed Kotlin/Rust domain state + versioned packed ABI/results; serialization only at explicit boundaries under D-023.

## `dashboard-specific-telemetry-authority`
Classification: `REMOVE`. Replacement: domain-neutral Rust ChannelStore.

## `usb-manager-monolith`
Classification: `REMOVE`. Replacement: Android CDC platform pieces + sole ECU session + typed protocol/runtime/tuning/diagnostic owners.

## `profile-parser-monolith`
Classification: `REMOVE`. Replacement: parser/model/support policy/schema compiler/primitive/condition responsibilities under one exact profile identity.

## `ble-per-characteristic-operation-owners`
Classification: `REMOVE` as final architecture. Replacement: one connection-wide GATT operation scheduler with semantic producers above it.

## `gps-named-generic-variable-transport`
Classification: `REMOVE` naming leakage. Replacement: generic variable-set transport + GPS-specific producer/encoder.

## `legacy-bridge-implied-relay-failsafe`
Classification: `REMOVE` assumption. Replacement: legacy bridge compatibility model; any direct relay/GPIO output is a separately versioned new capability with firmware-owned safety contract.

## `development-stage-production-names`
Historical examples: `T3*`, `T4*`, `T6*`, `W4*`. Classification: `REMOVE`. Replacement: permanent responsibility-based names.

## Current interpretation

This inventory is not a build checklist. `CODEBASE_TOPOLOGY.md` records what physically exists today; `RESPONSIBILITY_DESTINATIONS.md` maps not-yet-implemented responsibilities; `DECISIONS.md` and cross-domain authority documents override any older wording in this inventory when a later deliberate decision changes the contract.