# EpicHub Responsibility Inventory

## Purpose

This inventory records behaviours/responsibilities, not historical classes. A historical file may contribute to many entries, and one future responsibility may draw evidence from several historical files/repositories.

Status values:

- `PRESERVE`
- `REDESIGN`
- `MERGE`
- `SPLIT`
- `REFERENCE`
- `REMOVE`

Evidence levels:

- `PROVEN`
- `SUPPORTED`
- `PROPOSED`

This is an active Phase 1 inventory. Entries are refined as source archaeology continues.

---

## Android application and lifecycle

### `android.lifecycle.application`

Purpose: own Android application/process lifecycle and expose lifecycle state without owning ECU/BLE/domain logic.

Historical evidence: EpicDash-JZ application/Activity lifecycle work and lifecycle diagnostics.

Finding: historical Activities accumulated transport, WebView, playback, diagnostics and navigation ownership. Those responsibilities must not remain Activity-owned.

Classification: `REDESIGN`

Evidence: `PROVEN` that lifecycle/duplicate-owner behaviour matters; future structure `PROPOSED`.

Future owner: Kotlin platform/app layer.

### `android.navigation.presentation`

Purpose: route between Dashboard, Tuner, Logger, Analyzer, Diagnostics and Settings without becoming data authority.

Historical evidence: MainActivity/DashboardLabActivity/WebView shell navigation and tuner UI work.

Classification: `REDESIGN`

Evidence: `SUPPORTED`.

Future owner: Compose navigation layer.

---

## Android USB platform responsibilities

### `android.usb.discovery`

Purpose: enumerate Android USB devices and identify transport candidates before probing.

Historical evidence: EpicDash-JZ `UsbDeviceSelectionPolicy`, `UsbEcuManager`, USB selection correction audit.

Invariant: do not request/probe arbitrary CDC devices; `0483:5740` + expected CDC shape is a firmware transport-candidate gate, not ECU model authority.

Classification: `PRESERVE` behaviour / `SPLIT` historical implementation.

Evidence: `PROVEN`/firmware-supported.

Future owner: Kotlin Android USB platform layer.

### `android.usb.permission`

Purpose: Android USB permission request/result lifecycle, duplicate request protection and stale-result rejection.

Historical evidence: `UsbPermissionRequestTracker`, manager permission receiver/coordinator.

Classification: `PRESERVE` behaviour / `SPLIT` implementation.

Evidence: `SUPPORTED`.

Future owner: Kotlin Android USB platform layer.

### `android.usb.cdc-topology`

Purpose: resolve the actual CDC communications/data interface relationship and bulk IN/OUT pipe from descriptors rather than hardcoded interface numbers.

Historical evidence: `UsbEcuManager` CDC Union and Call Management parsing, endpoint enumeration/scoring; firmware `usbcfg.cpp` with optional MSD changing CDC interface numbers.

Invariant: interface IDs/endpoints are discovered facts, not ECU-model constants.

Classification: `PRESERVE` behaviour / `SPLIT` implementation.

Evidence: `PROVEN`/firmware-supported.

Future owner: Kotlin `CdcTopologyResolver`-type component.

### `android.usb.cdc-control`

Purpose: perform host-side CDC interface claiming/control setup such as `SET_LINE_CODING` and `SET_CONTROL_LINE_STATE` independently of ECU command framing.

Historical evidence: `UsbEcuManager` open/probe path; proven DTR and DTR+RTS compatibility attempts.

Classification: `PRESERVE` compatibility behaviour / `SPLIT`.

Evidence: `SUPPORTED` by physically exercised current path.

Future owner: Kotlin Android CDC control component.

### `android.usb.connection-open`

Purpose: open/close `UsbDeviceConnection`, claim/release interfaces and expose a bounded transport resource to the engine.

Historical evidence: EpicDash-JZ shared USB owner.

Future investigation: compare Kotlin-owned sustained bulk I/O with Android-opened native-FD handoff to Rust; do not assume FD/native ownership until bench/device evidence proves lifecycle/reconnect parity.

Classification: `REDESIGN` boundary, with current behaviour to preserve.

Evidence: existing Kotlin path `PROVEN`; Rust-FD alternative `PROPOSED`.

Future owner: Kotlin setup/resource boundary; sustained runtime I/O owner intentionally unresolved pending PoC.

---

## ECU transport/session/protocol

### `ecu.transport.session`

Purpose: one authoritative ECU communication session, including connection/session identity, protocol state, exclusivity and clean disconnect invalidation.

Historical evidence: `UsbEcuManager`, `UsbGenerationAuthority`, tuning exclusive-operation behaviour.

Invariant: no independent dashboard/tuner USB stacks and no second tune snapshot authority.

Classification: `PRESERVE` behaviour / `SPLIT` implementation.

Evidence: `PROVEN`.

Future owner: Rust engine after transport decision, coordinated by Kotlin Android connection layer.

### `ecu.transport.generation`

Purpose: make attach/detach/reconnect/permission/probe results generation-bound so obsolete asynchronous work cannot revive a dead session.

Historical evidence: `UsbGenerationAuthority`, permission generation coordinator and immediate detach invalidation.

Classification: `PRESERVE` semantics / `REDESIGN` placement.

Evidence: `PROVEN` need.

Future owner: Kotlin for permission/open attempt tokens before native session creation; Rust for active ECU session generation thereafter.

### `ecu.transport.retry-policy`

Purpose: bound reconnect attempts and distinguish retryable transport loss from compatibility/protocol/profile failure that must stop and require deliberate user action.

Historical evidence: manager retry wait/failure cap and no-retry first-output/profile mismatch behaviour.

Classification: `PRESERVE` intent / `REDESIGN` pure policy.

Evidence: `SUPPORTED`.

Future owner: ECU session policy, not UI.

### `ecu.protocol.request-framing`

Purpose: encode CRC-framed TunerStudio requests.

Firmware authority: `tunerstudio.cpp::tsProcessOne()`.

Contract: two-byte big-endian body length, then `command + request data`, then big-endian CRC32 over `command + request data` only.

Classification: `PRESERVE` protocol / `REDESIGN` implementation.

Evidence: `PROVEN` by direct firmware source plus qualified Android behaviour.

Future owner: Rust ECU request codec.

### `ecu.protocol.response-framing`

Purpose: accumulate/decode CRC responses and keep response status distinct from payload.

Firmware authority: `tunerstudio_io.cpp` / `tunerstudio_io.h`.

Contract: two-byte big-endian body length where body is `status + payload`, followed by status, payload, and big-endian CRC32 over `status + payload`.

Finding: request and response envelopes are deliberately asymmetric; a single generic symmetric `envelope()` abstraction is rejected for the new architecture.

Classification: `PRESERVE` protocol / `REDESIGN` implementation.

Evidence: `PROVEN`.

Future owner: Rust ECU response codec + typed protocol response model.

### `ecu.protocol.status-errors`

Purpose: represent OK/Burn-OK and firmware error responses as typed protocol status rather than ad-hoc stripped leading bytes.

Historical evidence: manager response helpers; firmware `sendErrorCode`, `writeCrcResponse` and command handlers.

Classification: `PRESERVE` semantics / `REDESIGN`.

Evidence: `PROVEN` firmware-side.

Future owner: Rust `protocol_response` layer.

### `ecu.protocol.sync-probe`

Purpose: establish/verify protocol synchronization using bounded read-only operations before runtime streaming.

Historical evidence: plain/framed `S/Q` compatibility sequence, framed `S` CRC sync, bounded `O` probes.

Firmware evidence: `TsChannelBase::in_sync` and `tsProcessOne` synchronization rules.

Invariant: profileless recognition stops after signature identity and closes before output/TuneSnapshot/mutation authority.

Classification: `PRESERVE` safety intent / `REDESIGN` session state machine.

Evidence: `SUPPORTED` by qualified current implementation and firmware source.

Future owner: Rust ECU session/probe policy.

### `ecu.protocol.command-operations`

Purpose: expose typed supported firmware operations rather than raw arbitrary command frames to higher domains.

Current audited operations: signature `S/Q`, output `O`, tune read `R`, RAM write `C`, Burn `B`.

Classification: `PRESERVE` protocol vocabulary / `REDESIGN` API.

Evidence: `PROVEN` by direct firmware command dispatch.

Future owner: Rust ECU protocol operation layer.

### `ecu.identity.signature-query`

Purpose: perform the read-only live TunerStudio signature query.

Invariant: transport identity is not board/profile authority.

Classification: `PRESERVE`.

Evidence: `PROVEN`/firmware-supported.

Future owner: Rust ECU identity/session layer.

### `ecu.identity.recognition`

Purpose: report firmware family/board/build identity diagnostically without granting profile/tune authority.

Historical evidence: `EcuFirmwareIdentity`, `EcuRecognitionResult`, firmware-evidence audit.

Invariant: board-name parsing is descriptive only.

Classification: `PRESERVE` semantics / `REDESIGN` implementation.

Evidence: `SUPPORTED` with qualified EpicDash-JZ implementation.

Future owner: Rust ECU identity layer.

### `ecu.profile.exact-match`

Purpose: require exact complete live signature equality with imported generated-INI signature before runtime-output decoding/tuning authority.

Classification: `PRESERVE`.

Evidence: `PROVEN`.

Future owner: Rust ECU profile/session authority.

---

## Generated INI/profile responsibilities

### `profile.ini.syntax-parse`

Purpose: parse the exact generated TunerStudio INI text into typed source facts: sections, assignments, escaped strings, top-level comma fields, dimensions, defines and conditions.

Historical evidence: `UsbTunerStudioProfile.kt` one-pass parser and helpers.

Classification: `PRESERVE` required behaviour / `SPLIT` + `REDESIGN` implementation.

Evidence: `PROVEN` for current supported profiles.

Future owner: Rust profile parser/model.

### `profile.support-policy`

Purpose: explicitly decide whether a parsed generated INI is within EpicHub's supported protocol/schema subset.

Current evidence: current path requires `msEnvelope_1.0`, little-endian primitive interpretation, non-empty query, `O` output command, valid output block and parsed output channels.

Invariant: unsupported constructs fail explicitly; parser success alone does not grant compatibility authority.

Classification: `ADD FORMAL OWNER` from previously embedded parser checks.

Evidence: `PROVEN` that checks exist; clean separation `PROPOSED`.

Future owner: Rust profile support policy/compiler gate.

### `profile.runtime-schema`

Purpose: compile output-channel offsets/types/scales/bits and output command/block geometry from the exact generated INI.

Classification: `PRESERVE` semantics / `SPLIT`.

Evidence: `PROVEN`.

Future owner: Rust profile runtime schema.

### `profile.tune-storage-schema`

Purpose: compile calibration pages, identifiers, sizes, read/Burn commands, scalar/array/bit storage metadata, bounds and scale/translate.

Classification: `PRESERVE` / `SPLIT`.

Evidence: `PROVEN`.

Future owner: Rust profile tune schema.

### `profile.tuner-surface-schema`

Purpose: compile table/curve/menu/dialog structure from INI without inventing values.

Historical evidence: `UsbTunerStudioProfile.kt` + `TuningIniDefinition.kt`.

Classification: `PRESERVE` structural authority / `MERGE` + `REDESIGN` implementation.

Evidence: `SUPPORTED`/regression tested.

Future owner: Rust profile compiler/tuner schema; Compose consumes typed projection.

### `profile.schema-canonicalization`

Purpose: detect duplicate/ambiguous definitions, verify page bounds and table/curve dimensional compatibility, and produce a deterministic usable semantic schema.

Historical evidence: `TuningIniDefinitionBuilder` canonical scalar/array/table/curve/bit logic.

Classification: `PRESERVE` behaviour / `SPLIT` from presentation JSON generation.

Evidence: `SUPPORTED`.

Future owner: Rust profile compiler.

### `profile.primitive-decode`

Purpose: decode profile-defined little-endian raw scalar/bit/array primitives independent of presentation.

Historical evidence: primitive methods embedded in `UsbOutputChannel`, `UsbTuneScalar`, `UsbTuneArray`, `UsbTuneBitField`.

Classification: `MERGE` + `REDESIGN`.

Evidence: `PROVEN` current path.

Future owner: Rust profile primitive module; tuning mutation conversion uses tuning value codec built on the same type metadata.

### `profile.condition-parse`

Purpose: parse the generated-INI condition expression grammar used by menu/dialog enable/visibility rules.

Classification: `PRESERVE` capability / `SPLIT`.

Evidence: `SUPPORTED` by current parser/tests.

Future owner: Rust profile condition parser.

### `profile.condition-evaluate`

Purpose: evaluate supported condition expressions against explicitly decoded values from a complete verified TuneSnapshot.

Invariant: unsupported/unresolved expressions fail closed for affected controls/targets.

Classification: `PRESERVE` safety semantics / `SPLIT`.

Evidence: `SUPPORTED`.

Future owner: Rust profile condition evaluator.

### `profile.condition-target-authority`

Purpose: traverse menu/dialog/table/curve routing to determine whether a semantic tuning target is currently unconstrained/active/hidden/disabled/unsupported and therefore whether a write is allowed.

Historical evidence: `IniConditionAuthority`.

Finding: INI conditions are not presentation-only metadata; they participate in write authorization.

Classification: `PRESERVE` safety behaviour / `REDESIGN` API.

Evidence: `SUPPORTED`/tested.

Future owner: Rust profile condition authority consumed by tuning safety policy.

### `profile.fingerprint`

Purpose: exact identity binding for compiled/cached profile state.

Classification: `PRESERVE`.

Evidence: `PROVEN` current architecture.

Future owner: Rust profile crate.

Invariant across all profile responsibilities: do not hardcode geometry by board name; exact generated INI remains structural/schema authority.

---

## Runtime channels and telemetry

### `runtime.output.poll`

Purpose: request/receive live ECU output blocks under the current exact profile/session authority.

Firmware evidence: `cmdOutputChannels()` validates offset/count against current generated output size and returns requested live-data fragments.

Classification: `PRESERVE` behaviour / `REDESIGN` implementation.

Evidence: `PROVEN`.

Future owner: Rust ECU/runtime engine.

### `runtime.output.decode`

Purpose: decode raw output bytes according to exact profile metadata without board-name geometry assumptions.

Classification: `PRESERVE` semantics / `REDESIGN` implementation.

Evidence: `PROVEN`.

Future owner: Rust channel/profile engine.

### `runtime.channel.store`

Purpose: sole high-volume mutable authority for current channel values, timestamp/revision/source/validity.

Historical evidence: `DashboardDataHub`, `DashboardRuntimeSnapshotStore`, variable repositories and native runtime state.

Finding: telemetry must not belong specifically to Dashboard.

Classification: `MERGE` + `REDESIGN`.

Evidence: old behaviours `SUPPORTED`; new owner `PROPOSED`.

Future owner: Rust channel engine.

### `runtime.snapshot.display`

Purpose: expose bounded immutable latest-state snapshots to Compose at display cadence, independent of ECU sample rate.

Historical evidence: bounded latest-state WebView bridge and runtime snapshot store.

Classification: `REDESIGN`.

Evidence: old bounded-latest-state semantics `PROVEN`; direct shared/packed native snapshot `PROPOSED`.

Future owner: Rust snapshot producer + Kotlin native-buffer adapter.

### `runtime.source.freshness`

Purpose: distinguish live/demo/playback/disconnected/stale state using session/generation/revision/timestamps and exclusive-operation awareness.

Historical evidence: EpicDash-JZ freshness/stale-warning and tuning-exclusive-operation corrections.

Classification: `PRESERVE` semantics / `REDESIGN` representation.

Evidence: `PROVEN`.

Future owner: Rust runtime session/channel model, projected to Kotlin UI.

### `runtime.math.derived-channels`

Purpose: compute user/system derived channels against the same channel model used by live/log analysis.

Historical evidence: EpicDash-JZ math channels; future analyzer requirements.

Classification: `PRESERVE` capability / `REDESIGN` implementation.

Evidence: existing feature `SUPPORTED`; unified engine `PROPOSED`.

Future owner: Rust expression engine.

---

## ESP32 accessory / BLE domain

### `accessory.ble.discovery`

Purpose: discover intended ESP32 accessory devices using Android BLE APIs.

Historical evidence: original EpicDash and EpicDash-JZ BLE scanning/reconnect work.

Classification: `PRESERVE` capability / `SPLIT` implementation.

Evidence: `PROVEN` in evolved app.

Future owner: Kotlin Android BLE layer.

### `accessory.ble.connection`

Purpose: own GATT connection/service discovery/characteristic subscription and connection state.

Historical evidence: original/EpicDash-JZ `BleManager`.

Classification: `PRESERVE` capability / `SPLIT` implementation.

Evidence: `PROVEN`.

Future owner: Kotlin `Esp32GattClient`-type component.

### `accessory.ble.reconnect-policy`

Purpose: decide retry/backoff behaviour independently of GATT mechanics.

Historical evidence: EpicDash-JZ reconnect improvements.

Classification: `SPLIT` + `REDESIGN`.

Evidence: `PROVEN` need; future pure-policy form `PROPOSED`.

Future owner: Kotlin BLE policy.

### `accessory.ble.write-scheduling`

Purpose: serialize/queue writes according to Android GATT constraints without mixing message semantics into transport scheduling.

Historical evidence: distinct button, variable-request and GPS FIFO queues; queue diagnostics/characterisation.

Classification: `PRESERVE` requirement / `MERGE` transport mechanism + semantic priority policy to be designed.

Evidence: `SUPPORTED`/partly physically characterized.

Future owner: Kotlin BLE write scheduler.

### `accessory.protocol.message-codec`

Purpose: encode/decode the ESP32 BLE payloads independently of GATT lifecycle.

Known protocol evidence: button mask LE; variable hashes/data BE; batched variable responses; phone-to-ESP32 variable-set/GPS entries.

Classification: `PRESERVE` required wire compatibility where still used / `SPLIT` implementation.

Evidence: `SUPPORTED` by upstream Android+ESP32 source and EpicDash-JZ implementation.

Future owner: Kotlin accessory protocol codec.

### `accessory.action.semantic-routing`

Purpose: route semantic actions such as accessory output toggles without exposing BLE characteristics/GPIO numbers to UI layouts.

Classification: `PROPOSED`.

Evidence: `PROPOSED`.

Future owner: Kotlin ActionRouter + accessory gateway.

### `accessory.state.requested-confirmed`

Purpose: distinguish requested output state from confirmed/accessory-reported state and failure/no-response state.

Classification: `PROPOSED`.

Evidence: `PROPOSED`.

Future owner: Kotlin accessory state store; mirrored as channels/events where useful.

### `accessory.hardware.failsafe`

Purpose: ensure physical relay/GPIO outputs have safe behaviour if Android/BLE fails or disconnects.

Classification: `PRESERVE` product requirement / implementation to be defined in ESP32 firmware.

Evidence: `PROPOSED` as formalized EpicHub contract.

Future owner: ESP32 firmware, not Android UI/Rust measurement engine.

### `accessory.telemetry.to-channels`

Purpose: publish returned ESP32 variables/inputs/relay states into the common runtime channel model.

Classification: `REDESIGN`.

Evidence: existing variable return path `SUPPORTED`; unified channel integration `PROPOSED`.

Future owner: Kotlin accessory gateway → coarse Rust channel ingestion.

---

## Location/GPS

### `location.acquire`

Purpose: acquire Android location samples with explicit quality/timestamp semantics.

Historical evidence: `LocationDataHub`, GPS characterization and regression tests.

Classification: `PRESERVE` capability / `REDESIGN` implementation boundary.

Evidence: `PROVEN` aspects in EpicDash-JZ.

Future owner: Kotlin Android location source.

### `location.map-runtime-channels`

Purpose: expose GPS values as typed runtime channels without conflating acquisition and display.

Classification: `PRESERVE` capability / `REDESIGN`.

Evidence: `PROVEN` mapping behaviour exists.

Future owner: Kotlin→Rust channel ingestion boundary.

### `location.forward-esp32`

Purpose: encode/send selected GPS data through ESP32 for CAN transmission where firmware use requires it.

Historical evidence: original ESP32 characteristic/CAN path and EpicDash-JZ GPS mapping.

Classification: `PRESERVE` protocol capability pending product review.

Evidence: `SUPPORTED`/historically implemented.

Future owner: Kotlin location + accessory protocol domains, not generic BLE code.

---

## Tuning authority and persistence

### `tuning.operation.exclusive-owner`

Purpose: serialize tune Read/RAM-write/Burn against ordinary output polling on the one ECU session.

Historical evidence: current manager's exclusive TUNER operation owner and deliberate poll pause/resume.

Invariant: UI does not create a second transport owner; deliberate exclusive operation is reflected in freshness state so stale warnings do not misreport it as a failed stream.

Classification: `PRESERVE` semantics / `REDESIGN` implementation.

Evidence: `PROVEN` by prior physical correction.

Future owner: Rust ECU/tuning session transaction gate.

### `tuning.snapshot.read-complete`

Purpose: read a complete bounded TuneSnapshot using current exact profile/session authority and INI-derived `R` plan.

Classification: `PRESERVE`.

Evidence: `PROVEN` physically.

Future owner: Rust tuning/session engine after parity qualification.

### `tuning.snapshot.immutable-authority`

Purpose: represent the complete verified tune image as immutable authority for semantic interpretation/write verification.

Classification: `PRESERVE`.

Evidence: `PROVEN`.

Future owner: Rust tuning model.

### `tuning.project.persist-profile-snapshot`

Purpose: persist exact imported INI plus matching complete verified TuneSnapshot as project authority, with compiled projections only as identity-bound acceleration.

Classification: `PRESERVE` principle / `REDESIGN` storage implementation for new app.

Evidence: `PROVEN` current architecture.

Future owner: Rust tuning/project model plus Kotlin Android file/storage boundary where necessary.

### `tuning.semantic.target-resolution`

Purpose: resolve scalar/array/table/curve/bit-enum semantic targets into current profile storage metadata below the UI boundary.

Classification: `PRESERVE`.

Evidence: `PROVEN` physically for mixed scalar/table-backed edits.

Future owner: Rust tuning engine.

### `tuning.value.codec`

Purpose: convert engineering values to/from supported raw calibration primitive representations according to current INI metadata.

Classification: `PRESERVE` semantics / `REDESIGN` implementation.

Evidence: `PROVEN`/regression tested.

Future owner: Rust tuning codec, reusing profile primitive type metadata without duplicating profile parsing.

### `tuning.write.condition-authority`

Purpose: require the current profile condition authority to allow a semantic target before a write plan can include it.

Historical evidence: `IniConditionAuthority.requireWriteAllowed`.

Classification: `PRESERVE` safety semantic.

Evidence: `SUPPORTED`/tested.

Future owner: Rust tuning safety policy consuming profile condition state.

### `tuning.write.plan`

Purpose: validate semantic edits, uniqueness/range/non-overlap/identity and construct complete expected post-write snapshot before transmission.

Classification: `PRESERVE`.

Evidence: `PROVEN`.

Future owner: Rust tuning planner.

### `tuning.write.baseline-reread`

Purpose: immediately before any possible `C` transmission, reread the complete tune and require its fingerprint to match the edit plan's baseline.

Classification: `PRESERVE`.

Evidence: `PROVEN` physically in normal tuning workflow.

Future owner: Rust write transaction preflight.

### `tuning.write.transaction`

Purpose: bounded RAM-write transaction with `C` write ACK, exact `R` range read-back, complete TuneSnapshot verification and dirty-page update.

Classification: `PRESERVE` safety semantics / `REDESIGN` implementation.

Evidence: `PROVEN` physically.

Future owner: Rust tuning transaction, using sole ECU transport session.

### `tuning.write.uncertainty`

Purpose: enter fail-closed uncertain state after possible transmission with incomplete verification; prohibit automatic retry/replay until deliberate complete resynchronization.

Classification: `PRESERVE`.

Evidence: `PROVEN` safety contract.

Future owner: Rust tuning transaction/session state.

### `tuning.burn.transaction`

Purpose: explicit separate `B` Burn using current INI Burn-page authority, flash readiness/progression evidence, complete post-Burn reread and dirty-page clearing.

Classification: `PRESERVE`.

Evidence: `PROVEN` physically.

Future owner: Rust tuning/Burn module.

### `tuning.ui.semantic-editing`

Purpose: present parameters/curves/tables and submit semantic edits without raw memory/protocol authority.

Historical evidence: WebView tuner and accepted hierarchy/content modes.

Classification: `PRESERVE` semantic boundary / `REDESIGN` presentation in Compose.

Evidence: behavioural requirements `SUPPORTED`/some physically qualified; new UI implementation `PROPOSED`.

Future owner: Kotlin Compose tuner UI/ViewModels; Rust supplies typed tune models and executes semantic operations.

---

## Logging and interoperability

### `interop.msl.playback`

Purpose: read/play TunerStudio MSL logs as an interoperability source feeding the common channel interface.

Historical evidence: `MslLogPlayer` and playback work.

Classification: `PRESERVE` useful capability / `REDESIGN` implementation.

Evidence: `SUPPORTED`.

Future owner: Rust interop reader or isolated import layer after format evaluation.

### `interop.csv.import`

Purpose: import compatible CSV measurement data without making CSV the internal runtime/log model.

Classification: `PRESERVE` capability.

Evidence: `SUPPORTED` historically.

Future owner: Rust interop/import layer.

### `interop.mlg`

Purpose: initial TunerStudio/MegaLogViewer-compatible logging/import/export path where useful during transition/testing.

Classification: `PROPOSED`/partly based on existing separate investigations.

Evidence: `PROPOSED` for new app.

Future owner: Rust interop layer.

### `logging.session.record`

Purpose: own a live measurement recording session independently of the concrete file-format writer.

Classification: `PROPOSED` clean-slate responsibility.

Future owner: Rust logging crate.

### `logging.format.mdf4`

Purpose: write/read/recover native MDF4 measurement files behind an internal abstraction.

Classification: `PROPOSED`.

Future owner: Rust MDF crate.

---

## Analyzer / plotting

### `analysis.query`

Purpose: query channels/time ranges against live or file-backed sources without loading entire datasets into the UI.

Classification: `PROPOSED`.

Future owner: Rust analysis engine.

### `analysis.events-search`

Purpose: search/filter/detect conditions and events over recorded/live channel data.

Classification: `PROPOSED`, with future algorithms informed by existing tuning plugins/tools.

Future owner: Rust analysis/expression engine.

### `plot.downsample-resolution`

Purpose: produce viewport-sized traces using spike-preserving min/max multiresolution data rather than transferring full raw history.

Classification: `PROPOSED`.

Future owner: Rust plotting/downsample engine.

### `plot.compose-render`

Purpose: render prepared plot packets responsively in Compose; use more specialized rendering only if measurement proves necessary.

Classification: `PROPOSED`.

Future owner: Kotlin Compose initially.

---

## Diagnostics and performance

### `diagnostics.android-usb`

Purpose: record Android-owned permission/device/interface/endpoint/open/claim/control-transfer facts without absorbing ECU protocol state.

Classification: `SPLIT` from historical manager diagnostics.

Evidence: `SUPPORTED`.

Future owner: Kotlin platform USB diagnostics.

### `diagnostics.ecu-protocol-session`

Purpose: record protocol sync, command/status/CRC/request-rate/timeout/session-generation facts in the engine domain.

Classification: `SPLIT` + `REDESIGN`.

Evidence: existing instrumentation need `PROVEN`.

Future owner: Rust diagnostics.

### `diagnostics.tuning-transaction`

Purpose: expose bounded semantic tune Read/Write/Burn state/results, uncertainty, fingerprints and verification evidence without exposing raw mutation authority.

Classification: `SPLIT` from `UsbEcuManager` diagnostic JSON.

Evidence: `PROVEN` useful in current physical validation.

Future owner: Rust tuning events/diagnostic snapshot.

### `diagnostics.structured-events`

Purpose: maintain bounded structured diagnostic events across Android/platform/Rust-engine domains and expose/export them without random ad-hoc logs as the sole evidence.

Historical evidence: `DiagnosticStore`, lifecycle/USB/BLE diagnostics and report formatting.

Classification: `MERGE` + `REDESIGN`.

Evidence: need `PROVEN`; unified model `PROPOSED`.

Future owner: shared schema; native Rust event ring for engine events plus Kotlin Android diagnostics adapter/store.

### `diagnostics.performance-metrics`

Purpose: measure frame/jank, ECU rates, queue depth/drop, JNI crossings/bytes, logging throughput, analyzer latency, memory/CPU/thermal behaviour.

Historical evidence: `PerformanceMetrics`, rolling metrics, performance measurement protocol.

Classification: `PRESERVE` measurement discipline / `REDESIGN` metrics architecture.

Evidence: `PROVEN` need.

Future owner: split Kotlin Android metrics and Rust engine metrics, aggregated diagnostically.

---

## Historical architecture explicitly not carried forward

### `presentation.webview-json-dom-hot-path`

Historical purpose: render dashboard/tuner through native→JSON→JavaScript→DOM/WebView path.

Classification: `REMOVE` from final runtime architecture.

Reason: Compose/native typed state replaces this translation/rendering path; useful UI behaviour is inventoried separately.

### `dashboard-specific-telemetry-authority`

Historical purpose: dashboard-centric hub/store owns runtime data.

Classification: `REMOVE` as an ownership model.

Replacement: domain-neutral Rust ChannelStore consumed by Dashboard/Logger/Analyzer/etc.

### `usb-manager-monolith`

Historical purpose: one large Android class owns platform USB, protocol, session, runtime, tuning and diagnostics.

Classification: `REMOVE` as an ownership model.

Replacement: Android CDC platform pieces + one ECU session + typed protocol/runtime/tuning/diagnostic owners.

### `profile-parser-monolith`

Historical purpose: one compiled profile object/parser also owns raw codecs, surface compilation and condition semantics.

Classification: `REMOVE` as a future structure.

Replacement: separate parser/model/support policy/schema compiler/primitive/condition responsibilities under one exact profile identity.

### `development-stage-production-names`

Historical examples: `T3*`, `T4*`, `T6*`, `W4*`.

Classification: `REMOVE` from new production vocabulary.

Replacement: permanent responsibility-based names.