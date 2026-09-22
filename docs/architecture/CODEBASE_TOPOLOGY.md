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

Kotlin-facing current application session state that is genuinely Android/app-scoped (for example whether a foreground session is active). It must not duplicate Rust ECU/channel/tune authority.

## `navigation/`

### `AppRoute.kt`

Typed route definitions for Dashboard/Tuner/Logger/Analyzer/Diagnostics/Settings and future top-level surfaces.

### `EpicHubNavHost.kt`

Compose navigation graph. Presentation routing only.

## `platform/usb/`

### `AndroidUsbDiscovery.kt`

Enumerates Android USB devices and exposes descriptors to the selection policy. Contains no ECU protocol logic.

### `UsbTransportCandidatePolicy.kt`

Applies the known safe Android-side candidate gate (including VID/PID/interface shape) before permission/probe. It must explicitly treat USB identity as transport-candidate evidence only.

### `AndroidUsbPermission.kt`

Owns USB permission request/result lifecycle and duplicate-request protection.

### `AndroidUsbConnection.kt`

Opens/closes `UsbDeviceConnection`, claims/releases interfaces/endpoints as required, and exposes the bounded resource needed by the selected ECU transport strategy. It does not parse ECU packets.

### `UsbDeviceDescriptor.kt`

Immutable Android-neutral projection of the USB device/interface/endpoint facts required by discovery/diagnostics/transport setup.

### `UsbConnectionDiagnostics.kt`

Android-side USB connection/permission timing and failure metrics only. ECU protocol diagnostics remain engine-side.

## `platform/bluetooth/`

### `AndroidBleScanner.kt`

Android BLE scan lifecycle and discovered-device facts. No accessory action semantics.

### `Esp32GattSchema.kt`

UUID/capability definitions for the supported ESP32 GATT protocol. This is transport schema, not accessory action mapping.

### `Esp32GattClient.kt`

One active GATT connection: connect/disconnect, service discovery, characteristic subscription/write dispatch, MTU/priority interaction where justified. No relay/action interpretation.

### `BleReconnectPolicy.kt`

Pure reconnect/backoff decisions based on connection failure/state. Testable without Android GATT.

### `BleWriteScheduler.kt`

Serializes/prioritizes GATT writes according to Android BLE constraints. Accepts already encoded messages; does not decide what an accessory action means.

### `BleConnectionDiagnostics.kt`

Connection/scan/write-queue metrics and structured Android BLE diagnostics.

## `platform/location/`

### `AndroidLocationSource.kt`

Owns Android location acquisition lifecycle/permission-aware sampling.

### `LocationSample.kt`

Immutable location sample with timestamp, coordinates, speed/course/altitude/accuracy and explicit availability/quality semantics.

### `LocationQuality.kt`

Pure quality/validity model used before fan-out to runtime channels/accessory forwarding.

## `platform/storage/`

### `AppFileStore.kt`

App-private file/directory location and safe file lifecycle primitives. It must not implement MDF/tune serialization itself.

### `AndroidDocumentAccess.kt`

Storage Access Framework open/import/export interaction.

### `FileExportService.kt`

Exports/copies prepared logs/reports/backups to user-selected destinations; content generation belongs to the owning domain.

## `platform/lifecycle/`

### `AppLifecycleObserver.kt`

Maps Android foreground/background/process lifecycle into explicit app lifecycle state/events.

### `ForegroundSessionService.kt`

If required by Android background/USB/logging behaviour, owns the platform foreground-service lifecycle/notification binding only. Exact need is decided before Phase 2.

## `platform/diagnostics/`

### `AndroidProcessExitReader.kt`

Reads Android historical process-exit reasons when supported.

### `AndroidFrameMetrics.kt`

Collects Android/Compose frame/jank measurements without claiming render-frequency values are display FPS when they are not.

### `AndroidResourceMetrics.kt`

Optional CPU/memory/thermal/process metrics that Android exposes reliably. Scope finalized when performance instrumentation is designed.

---

# Kotlin ↔ Rust boundary

## `nativeengine/`

This package is deliberately small. It is the only normal Kotlin package allowed to call the production Rust FFI directly.

### `NativeEngine.kt`

Small semantic façade over JNI. Expected responsibilities include engine creation/close, session commands, logger/analyzer requests, semantic tuning operations and access to bounded snapshots/events.

It must not expose one JNI method per channel.

### `NativeEngineHandle.kt`

Opaque native handle/lifetime guard. Prevents use-after-close and accidental multiple owners.

### `NativeEngineError.kt`

Stable Kotlin representation of engine error category/code/metadata. Rust panics must never be used as application error handling across JNI.

### `NativeSnapshotBuffer.kt`

Reads the versioned packed/direct runtime snapshot produced by Rust. Owns layout/version validation on the Kotlin side, not channel computation.

### `NativePlotBuffer.kt`

Reads packed viewport-sized graph result data returned by analysis/plot queries.

### `NativeEventReader.kt`

Drains bounded structured engine events. Avoids many callback types crossing JNI.

### `NativeEngineMetrics.kt`

Kotlin projection of coarse engine/JNI performance counters where useful for diagnostics.

---

# Semantic actions and accessory domain

## `actions/`

### `AppAction.kt`

Stable semantic action identifier/definition used by UI/hardware inputs/rules.

### `ActionTarget.kt`

Target domain enum/sealed type: `Local`, `Accessory`, `Ecu` (and only deliberately added future domains).

### `ActionRequest.kt`

Immutable request including action ID, desired semantic value/state, source and correlation identity where needed.

### `ActionResult.kt`

Accepted/rejected/pending/confirmed/failure result model appropriate to the target.

### `ActionSource.kt`

Identifies trusted origin such as user touch, physical key/rotary, approved rule automation or internal app operation. Used by guard policies.

### `ActionRouter.kt`

Routes a semantic action request directly to its owning domain. It must not force Accessory traffic through Rust or ECU traffic through BLE.

### `ActionPolicy.kt`

Pure allow/guard decisions for which action sources may invoke which semantic operations. This is not a substitute for lower-level ECU/ESP32 safety authority.

## `accessory/`

### `AccessoryDefinition.kt`

Semantic accessory definition: stable ID, display metadata, command/state type and policy references. Contains no Android GATT objects.

### `AccessoryRegistry.kt`

Validated set/index of available semantic accessory definitions/capabilities.

### `AccessoryState.kt`

Immutable requested/confirmed/fault/unavailable state model.

### `AccessoryStateStore.kt`

Sole Kotlin mutable authority for current accessory request/acknowledgement state. It is not the physical hardware authority.

### `Esp32AccessoryGateway.kt`

Adapts semantic accessory requests/state to/from the ESP32 protocol client/codec. Coordinates transport and domain semantics without absorbing GATT mechanics.

### `Esp32MessageCodec.kt`

Pure encode/decode of supported ESP32 protocol payloads (buttons, variables, acknowledgements/version/capabilities as eventually defined). No Android BLE calls.

### `Esp32GpsEncoder.kt`

Encodes location-derived ESP32/CAN payloads when this compatibility feature remains required. Kept separate because packed GPS representation is not generic accessory-action encoding.

### `AccessoryChannelPublisher.kt`

Batches accessory-returned variables/states into the coarse Rust channel-ingestion boundary where logging/analysis/runtime channels require them.

### `AccessoryFailsafePolicy.kt`

Android-side semantic policy describing expected/disconnect behaviour for presentation/requests. It does not replace the ESP32 firmware's physical failsafe implementation.

---

# Compose UI topology

UI packages consume typed UI/domain models and semantic action/native-engine façades. They never import raw ECU framing, MDF blocks or raw tune addresses.

## `ui/dashboard/`

### `DashboardScreen.kt`
Primary dashboard composition.

### `DashboardViewModel.kt`
Screen-facing state/orchestration; reads display-rate runtime snapshots and dispatches semantic actions.

### `DashboardUiState.kt`
Immutable presentation state only.

### `DashboardLayout.kt`
Typed validated editable/runtime dashboard layout model on Kotlin side where presentation-specific geometry belongs.

### `DashboardPage.kt`
One rendered page definition/selection state.

### `DashboardRenderer.kt`
Compose/custom-draw retained presentation orchestration. Does not own channel history or calculations.

### `dashboard/widgets/`

Likely focused files such as:

- `GaugeWidget.kt`
- `NumericWidget.kt`
- `BarWidget.kt`
- `IndicatorWidget.kt`
- `ActionWidget.kt`
- `GraphWidget.kt`
- `WidgetGeometry.kt`
- `WidgetBinding.kt`
- `WidgetStyle.kt`

Avoid one universal widget file with all rendering/editing/serialization logic.

### `dashboard/editor/`

Likely:

- `DashboardEditorScreen.kt`
- `DashboardEditorViewModel.kt`
- `WidgetPalette.kt`
- `WidgetInspector.kt`
- `LayoutSelection.kt`
- `LayoutEditCommand.kt`

Editor state is not runtime telemetry authority.

## `ui/tuner/`

### `TunerScreen.kt`
Top-level tuner composition.

### `TunerViewModel.kt`
Coordinates UI selection/draft state and semantic NativeEngine operations. No raw pages/offsets/protocol frames.

### `TunerUiState.kt`
Immutable UI projection.

### `TunerNavigation.kt`
System/category/feature navigation derived from the current profile/tune model.

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

These present engine-authoritative plans/results; they do not calculate raw write payloads.

## `ui/logger/`

- `LoggerScreen.kt`
- `LoggerViewModel.kt`
- `LoggerUiState.kt`
- `LogSessionCard.kt`

Logger UI controls/observes a Rust logging session. It never becomes the sample writer.

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

UI requests bounded analysis/plot results rather than loading entire logs into Compose state.

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

Only genuinely reusable visual primitives. Do not create a generic dumping ground.

Examples may include:

- `EpicButton.kt`
- `StatusIndicator.kt`
- `AdaptivePane.kt`
- `ConfirmationDialog.kt`

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

Crate boundaries remain provisional until Phase 1 dependency analysis confirms they earn independent build/dependency boundaries. The listed file responsibilities are more important than the exact number of crates.

## `epichub-engine`

### `engine.rs`
Top-level engine composition/orchestration. Delegates domain work; must not implement protocols/analysis itself.

### `session.rs`
Engine-level current ECU/log/runtime session composition and lifecycle.

### `command.rs`
Internal semantic engine commands, not ECU wire command bytes.

### `event.rs`
Engine-level structured event fan-in/projection.

### `error.rs`
Stable engine error taxonomy.

## `epichub-ffi`

### `jni_api.rs`
Tiny exported JNI entry surface only.

### `handle.rs`
Opaque engine handle registry/lifetime validation.

### `snapshot_buffer.rs`
Versioned packed/direct runtime snapshot ABI and buffer publication.

### `plot_buffer.rs`
Versioned packed plot/result ABI.

### `event_buffer.rs`
Bounded engine-event ABI.

### `error_bridge.rs`
Maps Rust engine errors to stable FFI error data.

## `epichub-channels`

### `channel_id.rs`
Stable channel identity/index type.

### `channel_schema.rs`
Channel metadata: name/type/unit/conversion/source semantics.

### `channel_value.rs`
Typed value representation; avoid universal boxed/f64 storage where unnecessary.

### `sample_frame.rs`
Timestamped batch of observed channel values from one acquisition/source update.

### `channel_store.rs`
Sole high-volume mutable current-channel authority.

### `runtime_snapshot.rs`
Immutable bounded projection for display/consumers.

### `channel_source.rs`
Common source abstraction for live/file-backed analysis where appropriate.

### `validity.rs`
Validity/freshness/source flags independent of presentation.

## `epichub-ecu`

### `transport.rs`
Transport trait/contract independent of protocol semantics.

### `usb_transport.rs`
Selected native ECU USB transport implementation if Phase 1/PoC chooses Rust runtime I/O.

### `session.rs`
One ECU protocol session/generation/exclusive-operation owner.

### `frame_codec.rs`
Firmware framing/envelope implementation.

### `crc.rs`
CRC implementation and test vectors.

### `protocol_command.rs`
Typed supported ECU protocol operations; not user semantic tuning targets.

### `protocol_response.rs`
Response decode/status/error primitives.

### `signature_query.rs`
Read-only firmware signature query.

### `firmware_identity.rs`
Diagnostic parsing/projection of live signature/build identity.

### `runtime_poll.rs`
Live output polling scheduling/protocol operation.

### `runtime_decoder.rs`
Decodes runtime block using current exact profile schema.

## `epichub-profile`

### `profile.rs`
Immutable accepted profile aggregate.

### `profile_fingerprint.rs`
Exact identity/fingerprint computation/validation.

### `ini_parser.rs`
INI lexical/section parse responsibilities.

### `ini_model.rs`
Typed generated-INI source model.

### `runtime_schema.rs`
Compiled output-channel layout.

### `tune_schema.rs`
Compiled calibration scalar/array/table/curve/storage metadata.

### `menu_schema.rs`
Profile-derived tuner hierarchy/presentation metadata that legitimately comes from INI.

### `condition_parser.rs`
Condition expression parsing.

### `condition_evaluator.rs`
Fail-closed evaluation against explicit context.

Profile parsing/compiler caches are derived and must remain exact-source-fingerprint-bound.

## `epichub-tuning`

### `tune_snapshot.rs`
Immutable complete verified tune image + identity.

### `snapshot_read_plan.rs`
Profile-derived bounded complete read plan.

### `snapshot_reader.rs`
Executes complete tune read using sole ECU session.

### `tune_model.rs`
Semantic scalar/curve/table/enum/bit model exposed upward.

### `target_resolver.rs`
Resolves semantic edit identity into current tune schema storage target.

### `value_codec.rs`
Engineering↔raw primitive conversion from profile metadata.

### `edit_request.rs`
Semantic requested tune changes.

### `write_plan.rs`
Validated non-overlapping changes + complete expected snapshot.

### `write_transaction.rs`
Executes bounded RAM mutation sequence.

### `write_verifier.rs`
Exact ACK/readback/complete expected-image verification.

### `tune_safety_policy.rs`
Identity/session/fingerprint/precondition decisions independent of UI.

### `dirty_pages.rs`
Verified unsaved calibration-page state.

### `burn_plan.rs`
Resolves required explicit page Burn operations from current profile/state.

### `burn_transaction.rs`
Executes explicit Burn lifecycle.

### `burn_verifier.rs`
Flash progression/readback/post-Burn tune verification.

### `recovery_journal.rs`
Bounded durable recovery marker/state for operations where interruption can create uncertainty.

### `uncertain_state.rs`
Fail-closed mutation uncertainty and deliberate resynchronization rules.

### `project_store.rs`
Semantic project persistence of exact source profile + verified TuneSnapshot identity. Physical Android file access may be supplied through a platform adapter rather than embedding Android APIs in Rust.

## `epichub-measurement`

### `measurement_store.rs`
Internal trait/API for channel/time-range measurement access, insulating analyzer/logger from one concrete file format.

### `time_range.rs`
Shared time-range/query primitives.

### `sample_batch.rs`
Efficient typed batch transfer between measurement subsystems.

## `epichub-mdf`

### `format.rs`
Supported MDF version/features contract.

### `block.rs`
MDF block primitives/validated headers/links.

### `metadata.rs`
MDF channel/group/source metadata mapping.

### `writer.rs`
Streaming MDF serialization.

### `reader.rs`
Validated lazy MDF reader.

### `index.rs`
Native/sidecar time/channel lookup acceleration.

### `compression.rs`
Supported compression codecs/policy primitives.

### `recovery.rs`
Interrupted-file recovery/finalization support.

MDF code must not know Compose or ECU tune semantics.

## `epichub-logging`

### `log_config.rs`
Immutable requested recording configuration/channel selection/format policy.

### `log_session.rs`
Recording-session lifetime/status/identity.

### `recorder.rs`
Consumes channel/sample batches and feeds the selected measurement writer. Does not implement MDF blocks itself.

### `log_event.rs`
Timestamped semantic events recorded alongside measurement data where supported.

### `recovery_marker.rs`
Small active-session marker if required for power-loss recovery orchestration.

## `epichub-analysis`

### `query.rs`
Analysis request model (channels/ranges/operations).

### `statistics.rs`
Descriptive/statistical operations.

### `search.rs`
Condition/search execution over channel ranges.

### `event_detector.rs`
Produces timestamped detected events from expressions/rules.

### `histogram.rs`
Histogram calculation.

### `scatter.rs`
Scatter dataset calculation/downselection.

### `heatmap.rs`
2D binning/heatmap calculation useful to tuning analysis.

### `cache.rs`
Bounded analysis result/cache policy.

Domain-specific future tuning analyzers should build on this common engine rather than invent independent log representations.

## `epichub-expressions`

### `parser.rs`
Expression grammar parser.

### `ast.rs`
Typed expression representation.

### `compiler.rs`
Compile/validate expressions to efficient instruction/evaluation form.

### `evaluator.rs`
Vectorized/chunked/live evaluation over channel values.

### `function_registry.rs`
Explicit allowlisted functions/operators, not arbitrary script execution.

## `epichub-plotting`

### `viewport.rs`
Visible time/range/pixel-density request.

### `plot_series.rs`
Prepared semantic trace data.

### `resolution_pyramid.rs`
Multiresolution levels for large histories.

### `minmax_envelope.rs`
Spike-preserving min/max/first/last bucket calculation.

### `plot_packet.rs`
Bounded result packet for Kotlin rendering.

### `plot_cache.rs`
Viewport/resolution result cache.

## `epichub-diagnostics`

### `diagnostic_event.rs`
Structured event schema/category/severity/correlation metadata.

### `event_ring.rs`
Bounded engine event history.

### `performance_metric.rs`
Engine-side rate/latency/queue/drop/cache/IO metrics.

### `diagnostic_snapshot.rs`
Coarse current engine diagnostics projection.

### `report.rs`
Engine portion of exportable diagnostic report content.

## `epichub-interop`

Interoperability is intentionally quarantined from the native channel/measurement model.

Likely files:

- `msl_reader.rs`
- `csv_reader.rs`
- `mlg_reader.rs`
- `mlg_writer.rs`

Exact MLG writer/reader responsibilities are added only after the existing logging-format investigation is converted into concrete requirements.

---

# Tests and fixtures

Production-domain tests should live near their crates/packages when normal language tooling benefits from that. Repository-level fixtures/cross-system tests may use:

```text
tests/
├── fixtures/
│   ├── ecu-protocol/
│   ├── generated-ini/
│   ├── tune-snapshots/
│   ├── esp32-protocol/
│   ├── mdf/
│   └── logs/
└── interoperability/
```

Fixtures must have clear provenance/licensing and must not contain personal/device secrets.

## Expected qualification layers

The finished project should support appropriate subsets of:

- pure Kotlin unit tests;
- pure Rust unit/property tests;
- protocol golden vectors;
- parser corpus tests;
- independent MDF interoperability tests;
- JNI boundary tests;
- Android instrumentation tests;
- simulator/fault injection;
- bench ECU tests;
- physical phone/tablet tests;
- vehicle acceptance where hardware behaviour requires it.

---

# Dependency direction summary

Intended high-level direction:

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

Reverse dependencies such as MDF importing Compose, ECU protocol importing Dashboard, or UI calculating raw tune memory payloads are architectural violations.
