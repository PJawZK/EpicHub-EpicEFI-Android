# EpicHub Codebase Topology

## Status

**Phase 1 provisional target topology.**

This document describes the intended finished source organization closely enough that production code has a planned home before implementation begins. It will be refined by the remaining responsibility inventory; it is not authorization to scaffold production code yet.

The topology is designed from EpicHub responsibilities, not from historical EpicDash/EpicDash-JZ filenames.

## Top-level repository

```text
EpicHub-EpicEFI-Android/
├── README.md
├── app/                         # Android/Kotlin/Compose product shell
├── rust/                        # Rust engine workspace
├── tests/                       # Cross-domain fixtures/integration material where useful
├── tools/                       # Repository/development/validation tools only
└── docs/
    ├── HANDOFF.md
    └── architecture/
```

Build-system modules should remain fewer than source-domain packages unless evidence justifies more. Source modularity is required from the start; Gradle module proliferation is not.

---

# Kotlin / Android topology

Proposed root namespace:

`com.epicefi.epichub`

The exact Android namespace/application ID is locked before Phase 2 scaffold creation.

```text
app/src/main/kotlin/com/epicefi/epichub/
├── app/
├── navigation/
├── platform/
│   ├── usb/
│   ├── bluetooth/
│   ├── location/
│   ├── storage/
│   ├── lifecycle/
│   └── diagnostics/
├── nativeengine/
├── actions/
├── accessory/
└── ui/
    ├── dashboard/
    ├── tuner/
    ├── logger/
    ├── analyzer/
    ├── diagnostics/
    ├── settings/
    └── components/
```

## `app/`

### `EpicHubApplication.kt`

Owns Android `Application` initialization only: process-wide platform wiring that truly belongs at application lifetime. Must not own ECU/BLE/tuner business logic.

### `EpicHubActivity.kt`

Single primary Activity/Compose host. Owns Android Activity lifecycle, window/screen integration and attachment of the Compose root. Must not become the replacement for historical `DashboardLabActivity`/`MainActivity` multi-domain ownership.

### `EpicHubApp.kt`

Top-level Compose application surface: theme + navigation host + app-level presentation wiring.

### `AppSession.kt`

Kotlin-facing application state that is genuinely Android/app-scoped. It must not duplicate Rust ECU/channel/tune authority.

## `navigation/`

### `AppRoute.kt`

Typed top-level route definitions.

### `EpicHubNavHost.kt`

Compose navigation graph. Presentation routing only.

## `platform/usb/`

The Android USB package owns Android host mechanics only. It must not implement TunerStudio command semantics, output decoding or tune mutation.

### `AndroidUsbDiscovery.kt`

Enumerates Android USB devices and attach/detach facts.

### `UsbTransportCandidatePolicy.kt`

Applies the safe transport-candidate gate, including known VID/PID/interface shape. USB identity is explicitly not ECU/profile authority.

### `AndroidUsbPermission.kt`

Owns permission request/result lifecycle, duplicate-request protection and stale-result rejection.

### `UsbDeviceDescriptor.kt`

Immutable Android-neutral projection of device/interface/endpoint facts needed by selection, setup and diagnostics.

### `CdcTopologyResolver.kt`

Resolves CDC communications/data relationships from Union/Call Management descriptors and actual interfaces/endpoints. Chooses a valid bulk IN/OUT pipe without hardcoded interface numbers.

### `AndroidUsbConnection.kt`

Opens/closes `UsbDeviceConnection` and claims/releases selected interfaces. It exposes only the bounded resource required by the chosen sustained-I/O strategy.

### `AndroidCdcControl.kt`

Owns CDC class control operations such as line coding and DTR/RTS control-line state. These are Android/CDC setup concerns, not ECU binary protocol operations.

### `UsbConnectionDiagnostics.kt`

Android-side device/permission/open/claim/endpoint/control-transfer timing and errors only. CRC/command/tuning diagnostics are engine-owned.

### Sustained-I/O boundary

The permanent Android package ends at a usable USB resource. Phase 1/PoC still decides between:

```text
A. Kotlin bulk I/O adapter -> coarse Rust session calls
B. Android opens/claims -> passes proven native FD/handle -> Rust sustained I/O
```

The rest of the architecture must not change depending on which option wins. The app must never create separate dashboard/tuner transport owners.

## `platform/bluetooth/`

### `AndroidBleScanner.kt`
Android BLE scan lifecycle and discovered-device facts.

### `Esp32GattSchema.kt`
UUID/capability definitions for the supported ESP32 GATT protocol.

### `Esp32GattClient.kt`
One active GATT connection: connect/disconnect, service discovery, characteristic subscription/write dispatch, MTU/priority interaction where justified.

### `BleReconnectPolicy.kt`
Pure reconnect/backoff decisions.

### `BleWriteScheduler.kt`
Serializes/prioritizes GATT writes. Accepts encoded messages; does not decide action semantics.

### `BleConnectionDiagnostics.kt`
Connection/scan/write-queue metrics and structured Android BLE diagnostics.

## `platform/location/`

### `AndroidLocationSource.kt`
Android location acquisition lifecycle/permission-aware sampling.

### `LocationSample.kt`
Immutable timestamped location sample with explicit availability/quality semantics.

### `LocationQuality.kt`
Pure quality/validity model.

## `platform/storage/`

### `AppFileStore.kt`
App-private file/directory lifecycle primitives; no MDF/tune serialization.

### `AndroidDocumentAccess.kt`
Storage Access Framework import/export interaction.

### `FileExportService.kt`
Copies prepared logs/reports/backups to user-selected destinations.

## `platform/lifecycle/`

### `AppLifecycleObserver.kt`
Maps Android lifecycle into explicit app lifecycle events.

### `ForegroundSessionService.kt`
If required, owns only foreground-service/notification platform behaviour for long-running USB/logging sessions.

## `platform/diagnostics/`

### `AndroidProcessExitReader.kt`
Reads supported Android historical process-exit reasons.

### `AndroidFrameMetrics.kt`
Collects Compose/Android frame and jank measurements.

### `AndroidResourceMetrics.kt`
Optional CPU/memory/thermal/process metrics exposed reliably by Android.

---

# Kotlin ↔ Rust boundary

## `nativeengine/`

This package is deliberately small. It is the only normal Kotlin package allowed to call production Rust FFI directly.

### `NativeEngine.kt`
Small semantic façade: engine/session commands, logging/analyzer requests, semantic tuning operations and bounded result access. No one-JNI-call-per-channel API.

### `NativeEngineHandle.kt`
Opaque native handle/lifetime guard.

### `NativeEngineError.kt`
Stable Kotlin error projection. Rust panics never cross JNI as application error handling.

### `NativeSnapshotBuffer.kt`
Reads the versioned packed/direct runtime snapshot produced by Rust.

### `NativePlotBuffer.kt`
Reads packed viewport-sized analysis/plot result data.

### `NativeEventReader.kt`
Drains bounded structured engine events.

### `NativeEngineMetrics.kt`
Coarse engine/JNI performance counters where useful.

---

# Semantic actions and accessory domain

## `actions/`

- `AppAction.kt` — stable semantic action definition.
- `ActionTarget.kt` — target domain (`Local`, `Accessory`, `Ecu`, deliberately extended only).
- `ActionRequest.kt` — immutable request/source/correlation data.
- `ActionResult.kt` — accepted/rejected/pending/confirmed/failure.
- `ActionSource.kt` — user/hardware/rule/internal origin.
- `ActionRouter.kt` — direct routing to the owning domain.
- `ActionPolicy.kt` — pure source/action guard policy.

Accessory traffic must not be forced through Rust merely for centralization; ECU traffic must not be forced through BLE.

## `accessory/`

- `AccessoryDefinition.kt` — semantic definition, no GATT objects.
- `AccessoryRegistry.kt` — validated capability/index set.
- `AccessoryState.kt` — requested/confirmed/fault/unavailable state.
- `AccessoryStateStore.kt` — sole Kotlin mutable accessory request/ack state.
- `Esp32AccessoryGateway.kt` — semantic↔protocol adapter.
- `Esp32MessageCodec.kt` — pure ESP32 payload codec.
- `Esp32GpsEncoder.kt` — exact GPS/variable-set wire representation where retained.
- `AccessoryChannelPublisher.kt` — batches returned values/states into Rust channel ingestion.
- `AccessoryFailsafePolicy.kt` — Android expected semantics; physical failsafe remains ESP32 firmware authority.

---

# Compose UI topology

UI consumes typed UI/domain models and semantic action/native-engine façades. It never imports raw ECU framing, MDF blocks or raw tune addresses.

## `ui/dashboard/`

- `DashboardScreen.kt`
- `DashboardViewModel.kt`
- `DashboardUiState.kt`
- `DashboardLayout.kt`
- `DashboardPage.kt`
- `DashboardRenderer.kt`

### `dashboard/widgets/`

Likely focused files:

- `GaugeWidget.kt`
- `NumericWidget.kt`
- `BarWidget.kt`
- `IndicatorWidget.kt`
- `ActionWidget.kt`
- `GraphWidget.kt`
- `WidgetGeometry.kt`
- `WidgetBinding.kt`
- `WidgetStyle.kt`

Avoid one universal widget class containing rendering, editing and serialization.

### `dashboard/editor/`

- `DashboardEditorScreen.kt`
- `DashboardEditorViewModel.kt`
- `WidgetPalette.kt`
- `WidgetInspector.kt`
- `LayoutSelection.kt`
- `LayoutEditCommand.kt`

## `ui/tuner/`

- `TunerScreen.kt`
- `TunerViewModel.kt`
- `TunerUiState.kt`
- `TunerNavigation.kt`

### `tuner/parameter/`
- `ParameterPanel.kt`
- `ParameterEditor.kt`
- `ParameterUiModel.kt`

### `tuner/curve/`
- `CurvePanel.kt`
- `CurveEditor.kt`
- `CurveUiModel.kt`

### `tuner/table/`
- `TablePanel.kt`
- `TableGrid.kt`
- `TableSelection.kt`
- `TableEditCommand.kt`
- `Table3dView.kt`
- `TableUiModel.kt`

The 3D view is visual interpretation/rotation unless editing is deliberately added later.

### `tuner/write/`
- `WritePreviewDialog.kt`
- `BurnDialog.kt`
- `TuneOperationStatus.kt`

These display engine-authoritative plans/results; they never calculate raw write payloads or re-evaluate INI write conditions independently.

## `ui/logger/`

- `LoggerScreen.kt`
- `LoggerViewModel.kt`
- `LoggerUiState.kt`
- `LogSessionCard.kt`

## `ui/analyzer/`

- `AnalyzerScreen.kt`
- `AnalyzerViewModel.kt`
- `AnalyzerUiState.kt`
- `ChannelBrowser.kt`
- `PlotSurface.kt`
- `CursorOverlay.kt`
- `EventMarkers.kt`
- `AnalysisToolbar.kt`
- `StatisticsPanel.kt`
- `ScatterPanel.kt`
- `HeatmapPanel.kt`

UI requests bounded prepared data rather than storing whole logs in Compose state.

## `ui/diagnostics/`

- `DiagnosticsScreen.kt`
- `DiagnosticsViewModel.kt`
- `DiagnosticsUiState.kt`
- `ConnectionDiagnosticsPanel.kt`
- `PerformanceDiagnosticsPanel.kt`
- `EventLogPanel.kt`

## `ui/settings/`

- `SettingsScreen.kt`
- `SettingsViewModel.kt`
- `SettingsUiState.kt`

## `ui/components/`

Only genuinely reusable visual primitives; not a dumping ground.

---

# Rust workspace topology

```text
rust/
├── Cargo.toml
└── crates/
    ├── epichub-engine/
    ├── epichub-ffi/
    ├── epichub-channels/
    ├── epichub-ecu/
    ├── epichub-profile/
    ├── epichub-tuning/
    ├── epichub-measurement/
    ├── epichub-mdf/
    ├── epichub-logging/
    ├── epichub-analysis/
    ├── epichub-expressions/
    ├── epichub-plotting/
    ├── epichub-diagnostics/
    └── epichub-interop/
```

Crate boundaries remain provisional until Phase 1 dependency analysis confirms they earn independent build/dependency boundaries. File responsibilities are more important than crate count.

## `epichub-engine`

- `engine.rs` — top-level composition only.
- `session.rs` — engine-level current ECU/log/runtime session composition.
- `command.rs` — semantic internal engine commands, not ECU wire bytes.
- `event.rs` — structured engine event fan-in/projection.
- `error.rs` — stable engine error taxonomy.

## `epichub-ffi`

- `jni_api.rs` — tiny exported JNI entry surface.
- `handle.rs` — opaque engine-handle registry/lifetime validation.
- `snapshot_buffer.rs` — versioned packed runtime snapshot ABI.
- `plot_buffer.rs` — versioned packed plot/result ABI.
- `event_buffer.rs` — bounded event ABI.
- `error_bridge.rs` — stable error mapping.

## `epichub-channels`

- `channel_id.rs` — stable channel identity/index.
- `channel_schema.rs` — name/type/unit/conversion/source metadata.
- `channel_value.rs` — typed value representation; avoid universal boxed/f64 storage.
- `sample_frame.rs` — timestamped batch from one source acquisition.
- `channel_store.rs` — sole high-volume mutable current-channel authority.
- `runtime_snapshot.rs` — immutable bounded display/consumer projection.
- `channel_source.rs` — common live/file-backed source abstraction where appropriate.
- `validity.rs` — validity/freshness/source flags independent of presentation.

## `epichub-ecu`

The historical `UsbEcuManager` responsibilities are intentionally decomposed here. This crate contains **no Android permission/device enumeration APIs**.

### `transport.rs`
Transport trait/contract independent of command semantics.

### `usb_transport.rs`
Selected sustained ECU USB transport implementation only if the native-FD PoC proves it. Otherwise a transport adapter accepts coarse I/O from the Kotlin-owned transport worker without changing upper layers.

### `session.rs`
One ECU protocol session and the only owner allowed to serialize runtime polling with tune transactions.

### `session_generation.rs`
Active native session generation/invalidation and stale-work rejection after a transport has been handed to the engine.

### `retry_policy.rs`
Retry/stop decisions for transport failures versus compatibility/profile/protocol failures.

### `request_codec.rs`
Encodes request frames only:

```text
BE length(command+data) + command/data + BE CRC32(command+data)
```

### `response_codec.rs`
Accumulates/validates response frames only:

```text
BE length(status+data) + status + data + BE CRC32(status+data)
```

### `crc.rs`
CRC32 primitives + golden vectors from firmware-compatible traffic.

### `protocol_command.rs`
Typed supported operations (`S/Q`, `O`, `R`, `C`, `B`, deliberately extended only).

### `protocol_response.rs`
Typed response status/error/Burn-ack representation. Payload access occurs only after frame/status validation.

### `sync_probe.rs`
Read-only plain/framed signature and bounded output preflight/session synchronization policy. This is not part of the pure frame codec.

### `signature_query.rs`
Read-only signature operation.

### `firmware_identity.rs`
Diagnostic parsing/projection of live signature/build identity without compatibility authority.

### `runtime_poll.rs`
Live output polling schedule/protocol operation.

### `runtime_decoder.rs`
Decodes current runtime bytes from the exact profile `runtime_schema` and publishes sample frames.

### `exclusive_operation.rs`
One explicit owner/gate for operations that pause ordinary polling, initially tune Read/Write/Burn. Freshness/diagnostics can distinguish this from transport loss.

## `epichub-profile`

The historical generated-INI implementation is also deliberately decomposed. Parsing text, deciding support, compiling schema, decoding primitives and evaluating write-affecting conditions are related but distinct responsibilities.

### `profile.rs`
Immutable accepted compiled profile aggregate with exact source identity.

### `profile_fingerprint.rs`
Exact source fingerprint computation/validation.

### `ini_parser.rs`
Lexical/syntactic parsing: sections, comments, quoted strings, escaped bytes, top-level list splitting, dimensions, defines and condition-source strings.

### `ini_model.rs`
Typed raw generated-INI source model before EpicHub support validation/compilation.

### `profile_support.rs`
Explicit supported-subset gate. Current evidence includes `msEnvelope_1.0`, little endian, valid query/output command geometry, output block and supported primitive/schema constructs. Unsupported constructs return explicit compatibility errors.

### `primitive.rs`
Profile-defined primitive metadata/read decoding shared by runtime/tune schema compilation. It does not decide semantic tuning writes.

### `runtime_schema.rs`
Compiled output-channel names/types/offsets/scales/bits and request/block geometry.

### `tune_schema.rs`
Compiled calibration pages, identifiers, sizes, read/Burn metadata, scalar/array/bit storage metadata, bounds and conversions.

### `tuner_schema.rs`
Compiled table/curve/dialog surface relationships and deterministic semantic target structure.

### `menu_schema.rs`
Profile-derived menu/group/hierarchy metadata that legitimately comes from INI.

### `profile_compiler.rs`
Canonicalizes duplicate/equivalent definitions, rejects ambiguity where necessary, validates page bounds and table/curve dimensions, and produces the accepted deterministic schemas.

### `condition_parser.rs`
Parser for the TunerStudio generated-INI condition compatibility grammar. It is not automatically the same grammar/API as EpicHub's future analysis expression engine.

### `condition_evaluator.rs`
Fail-closed condition evaluation against explicit verified tune values.

### `condition_authority.rs`
Maps evaluated menu/dialog/surface routing into semantic target availability/write constraints. Compose may display its result but may not independently authorize a write.

Profile parsing/compiler caches are derived and exact-source-fingerprint-bound.

## `epichub-tuning`

### `tune_snapshot.rs`
Immutable complete verified tune image + identity.

### `snapshot_read_plan.rs`
Exact-profile-derived bounded `R` plan for all tune pages.

### `snapshot_reader.rs`
Executes complete tune read through the sole ECU session.

### `tune_model.rs`
Semantic scalar/curve/table/enum/bit model exposed upward.

### `target_resolver.rs`
Resolves semantic edit identity into current tune schema storage target.

### `value_codec.rs`
Engineering↔raw conversion using profile primitive/storage metadata.

### `edit_request.rs`
Semantic requested tune changes only; no raw page/offset API from UI.

### `write_plan.rs`
Validates identity, uniqueness, range, overlap, condition authority and constructs the complete expected post-write TuneSnapshot.

### `write_transaction.rs`
Owns the full mutation lifecycle: fresh complete baseline reread, bounded `C`, per-target `R` verification, final complete reread.

### `write_verifier.rs`
Typed ACK/readback/expected-image verification helpers.

### `tune_safety_policy.rs`
Session/profile/tune/condition/precondition authority independent of UI.

### `dirty_pages.rs`
Verified unsaved calibration-page state.

### `burn_plan.rs`
Resolves required explicit Burn pages from exact profile metadata and current dirty/firmware status.

### `burn_transaction.rs`
Executes explicit `B` operations through the sole ECU session.

### `burn_verifier.rs`
Requires ready flash state, accepted Burn response, request progression/completion evidence, unchanged complete post-Burn tune and no remaining unsaved/pending state.

### `recovery_journal.rs`
Durable bounded marker/state for operations whose interruption can create uncertainty.

### `uncertain_state.rs`
Fail-closed mutation/Burn uncertainty and deliberate complete-read resynchronization rules. No automatic replay.

### `project_store.rs`
Persists exact source profile + verified TuneSnapshot identity. Android file access is supplied through a platform adapter where needed.

## `epichub-measurement`

- `measurement_store.rs` — internal channel/time-range measurement API.
- `time_range.rs` — shared time-range/query primitives.
- `sample_batch.rs` — efficient typed batch transfer.

## `epichub-mdf`

- `format.rs` — supported MDF version/features contract.
- `block.rs` — validated MDF block primitives.
- `metadata.rs` — channel/group/source metadata mapping.
- `writer.rs` — streaming serialization.
- `reader.rs` — validated lazy reader.
- `index.rs` — native/sidecar lookup acceleration.
- `compression.rs` — supported codec/policy primitives.
- `recovery.rs` — interrupted-file recovery/finalization.

MDF code knows neither Compose nor ECU tune semantics.

## `epichub-logging`

- `log_config.rs` — requested recording configuration.
- `log_session.rs` — recording lifetime/status/identity.
- `recorder.rs` — consumes sample batches into selected measurement writer.
- `log_event.rs` — timestamped semantic events.
- `recovery_marker.rs` — small active-session marker if required.

## `epichub-analysis`

- `query.rs`
- `statistics.rs`
- `search.rs`
- `event_detector.rs`
- `histogram.rs`
- `scatter.rs`
- `heatmap.rs`
- `cache.rs`

Domain-specific tuning analyzers build on the common channel/measurement system rather than independent log representations.

## `epichub-expressions`

- `parser.rs`
- `ast.rs`
- `compiler.rs`
- `evaluator.rs`
- `function_registry.rs`

This is the general runtime/log-analysis expression language. Compatibility-sensitive TunerStudio INI conditions stay in `epichub-profile` unless parity evidence later justifies a shared grammar layer.

## `epichub-plotting`

- `viewport.rs`
- `plot_series.rs`
- `resolution_pyramid.rs`
- `minmax_envelope.rs`
- `plot_packet.rs`
- `plot_cache.rs`

## `epichub-diagnostics`

- `diagnostic_event.rs` — category/severity/correlation schema.
- `event_ring.rs` — bounded engine history.
- `performance_metric.rs` — rate/latency/queue/drop/cache/I/O metrics.
- `diagnostic_snapshot.rs` — coarse engine diagnostics projection.
- `report.rs` — engine report material.

## `epichub-interop`

Interoperability is quarantined from the native channel/measurement model.

Likely files:

- `msl_reader.rs`
- `csv_reader.rs`
- `mlg_reader.rs`
- `mlg_writer.rs`

Exact MLG responsibilities are added only after the existing format investigation becomes concrete requirements.

---

# Tests and fixtures

Production-domain tests live near their packages/crates where normal tooling benefits. Cross-system fixtures may use:

```text
tests/
├── fixtures/
│   ├── ecu-protocol/
│   │   ├── requests/
│   │   └── responses/
│   ├── generated-ini/
│   ├── tune-snapshots/
│   ├── esp32-protocol/
│   ├── mdf/
│   └── logs/
└── interoperability/
```

Protocol fixtures must distinguish request and response framing. Generated-INI fixtures should include accepted examples plus deliberately unsupported/ambiguous constructs. Fixtures require clear provenance/licensing and no personal/device secrets.

## Expected qualification layers

- pure Kotlin unit tests;
- pure Rust unit/property tests;
- request/response protocol golden vectors;
- generated-INI parser/compiler/condition corpus tests;
- independent MDF interoperability tests;
- JNI boundary tests;
- Android instrumentation tests;
- simulator/fault injection;
- bench ECU tests;
- physical phone/tablet tests;
- vehicle acceptance where hardware behaviour requires it.

---

# Dependency direction summary

```text
Compose UI
   │
   ├── semantic actions ─────► Kotlin accessory/platform owners
   │
   └── NativeEngine façade ──► FFI
                                  │
                                  ▼
                              Rust Engine
                                  │
                 ┌────────────────┼────────────────┐
                 ▼                ▼                ▼
               ECU/Profile      Channels         Tuning
                                  │
                     ┌────────────┼───────────────┐
                     ▼            ▼               ▼
                   Logging       MDF           Analysis
                                                   │
                                                   ▼
                                                Plotting
```

Additional hard rules:

- Android USB permission/device/CDC setup never imports tune/profile semantics.
- ECU request/response codecs do not know Compose, generated-menu structure or tuning semantics.
- Profile compiler does not own USB or mutate ECU state.
- Tuning uses the profile compiler's typed authority and the sole ECU session; it does not construct a second transport.
- Compose displays profile condition/write state but does not re-authorize writes.
- Reverse dependencies such as MDF importing Compose, ECU protocol importing Dashboard, or UI calculating raw tune memory payloads are architectural violations.