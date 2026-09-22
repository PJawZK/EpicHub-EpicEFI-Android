# EpicHub Codebase Topology

## Status

**Phase 1 provisional target topology.**

This describes the intended finished source organization closely enough that every responsibility has a planned home before implementation. It is not authorization to scaffold production code yet.

Historical EpicDash/EpicDash-JZ filenames are evidence sources, not migration units.

## Repository shape

```text
EpicHub-EpicEFI-Android/
├── README.md
├── app/                         # Kotlin / Compose Android product
├── rust/                        # Rust data/ECU/logging/analysis engine
├── tests/                       # cross-system fixtures/integration evidence
├── tools/                       # repository/development validation only
└── docs/
    ├── HANDOFF.md
    └── architecture/
```

Source-domain packages are required from the start. Gradle/Cargo module proliferation is not: create build modules/crates only when dependency/build/test boundaries justify them.

---

# Kotlin / Android

Provisional namespace: `com.epicefi.epichub`. Exact application ID/namespace is locked before Phase 2.

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

- `EpicHubApplication.kt` — process/application initialization only.
- `EpicHubActivity.kt` — one primary Activity/Compose host; no transport/domain monolith.
- `EpicHubApp.kt` — theme/navigation/application surface.
- `AppSession.kt` — genuinely Android/app-scoped state only; never duplicates Rust ECU/channel/tune authority.

## `navigation/`

- `AppRoute.kt`
- `EpicHubNavHost.kt`

Presentation routing only.

---

# `platform/usb/`

Android USB owns Android host mechanics, never TunerStudio semantics.

- `AndroidUsbDiscovery.kt` — enumerate and attach/detach observations.
- `UsbTransportCandidatePolicy.kt` — safe shared-transport candidate gate; never board/profile authority.
- `AndroidUsbPermission.kt` — permission lifecycle, duplicate/stale protection.
- `UsbDeviceDescriptor.kt` — immutable device/interface/endpoint facts.
- `CdcTopologyResolver.kt` — CDC Union/Call Management + valid bulk IN/OUT resolution; no hardcoded interface numbers.
- `AndroidUsbConnection.kt` — open/close and claim/release interfaces.
- `AndroidCdcControl.kt` — line coding and DTR/RTS control setup.
- `UsbConnectionDiagnostics.kt` — permission/open/claim/endpoint/control-transfer observations only.

### Sustained-I/O gate

Two PoC candidates remain:

```text
A. Kotlin bulk I/O worker -> coarse batches to Rust
B. Android opens/claims -> proven native FD/handle -> Rust sustained I/O
```

Everything above the transport adapter remains identical. No architecture permits separate dashboard/tuner ECU owners.

---

# `platform/bluetooth/`

Android BLE/GATT remains Kotlin-owned.

- `AndroidBleScanner.kt` — scan lifecycle and candidate discovery.
- `Esp32GattSchema.kt` — supported legacy bridge service/characteristic UUIDs and later deliberately versioned schemas.
- `Esp32GattClient.kt` — one active GATT connection, discover/subscribe/MTU/priority, stale-GATT rejection and deterministic close.
- `BleReconnectPolicy.kt` — pure scan/retry/backoff decisions.
- `BleOperation.kt` — transport-level GATT operation description and class/priority/correlation metadata.
- `BleWriteScheduler.kt` — **one connection-wide serialized operation owner**. It replaces the historical three independent per-characteristic FIFO ownership model.
- `BleConnectionDiagnostics.kt` — scan/connect/retry/operation depth, latency, drops and Android GATT errors.

`BleWriteScheduler` accepts already encoded transport messages. It does not decide button/GPS/accessory semantics and does not claim downstream command confirmation.

---

# `platform/location/`

- `AndroidLocationSource.kt` — Android acquisition lifecycle.
- `LocationSample.kt` — immutable timestamped sample.
- `LocationQuality.kt` — explicit availability/quality model.

GPS→legacy ESP32 encoding does **not** live here as a GATT detail; location produces semantic location values, then an accessory encoder adapts them when needed.

---

# `platform/storage/`

- `AppFileStore.kt` — app-private files/directories only.
- `AndroidDocumentAccess.kt` — SAF import/export.
- `FileExportService.kt` — copy prepared logs/reports/backups to user-selected destinations.

No MDF/tune serialization logic belongs here.

---

# `platform/lifecycle/`

- `AppLifecycleObserver.kt` — explicit lifecycle events.
- `ForegroundSessionService.kt` — only if required for long-running USB/logging; owns Android service/notification behavior, not ECU logic.

---

# `platform/diagnostics/`

- `AndroidProcessExitReader.kt`
- `AndroidFrameMetrics.kt`
- `AndroidResourceMetrics.kt`

---

# Kotlin ↔ Rust boundary: `nativeengine/`

Only this normal production package calls Rust FFI directly.

- `NativeEngine.kt` — small semantic façade; no per-channel/per-sample JNI calls.
- `NativeEngineHandle.kt` — opaque lifetime guard.
- `NativeEngineError.kt` — stable Kotlin error projection.
- `NativeSnapshotBuffer.kt` — versioned packed runtime snapshot.
- `NativePlotBuffer.kt` — packed viewport-sized analysis result.
- `NativeEventReader.kt` — bounded structured event drain.
- `NativeEngineMetrics.kt` — coarse engine/JNI metrics.

---

# Semantic actions: `actions/`

- `AppAction.kt` — stable semantic action definition.
- `ActionTarget.kt` — `Local`, `AccessoryBridge`, `AccessoryOutput` (only when supported), `Ecu`, deliberately extended only.
- `ActionRequest.kt` — requested semantic value/source/correlation.
- `ActionResult.kt` — accepted/rejected/pending/completed/failed; result semantics state whether this is transport completion or real domain confirmation.
- `ActionSource.kt` — user/hardware/rule/internal source.
- `ActionRouter.kt` — routes directly to owning domain.
- `ActionPolicy.kt` — pure source/action guard policy.

Raw characteristic UUIDs, button masks, GPIOs, CAN IDs and ECU page/offsets never become layout action identity.

---

# Legacy bridge / accessory domain: `accessory/`

The original ESP32 is modeled as a **legacy BLE↔CAN bridge with local inputs**. Direct relay/GPIO output control is a separate future capability.

## Legacy bridge protocol files

- `Esp32GattProtocol.kt` — high-level association between supported message kind and characteristic; no Android GATT objects.
- `LegacyButtonBridgeCodec.kt` — 16-bit LE BLE mask and corresponding legacy bridge semantics.
- `LegacyVariableRequestCodec.kt` — BE hash request batch encoding.
- `LegacyVariableResponseCodec.kt` — batched `[hash BE4][float BE4]` decode.
- `Esp32VariableSetCodec.kt` — generic `[hash BE4][raw32 BE4]` entries. This replaces historical GPS-specific naming for characteristic `...ab`.
- `GpsVariableEncoder.kt` — GPS-specific float/packed hash/value producer feeding the generic variable-set codec.
- `VirtualInputEncoder.kt` — only if phone virtual ADC/input compatibility remains product-relevant.

## Bridge state/gateway

- `Esp32BridgeGateway.kt` — semantic legacy bridge operations ↔ codecs ↔ connection-wide BLE scheduler.
- `BridgeRequest.kt` — explicit app-side variable request identity/correlation.
- `BridgeRequestState.kt` — queued, in-flight, partial, timed-out, completed, failed.
- `BridgeTransportState.kt` — BLE/GATT transport state distinct from downstream ECU/CAN state.
- `BridgeButtonState.kt` — requested/last-sent legacy mask and explicit downstream-unknown state where no independent feedback exists.
- `BridgeCompatibilityPolicy.kt` — exact legacy compatibility decisions, including disconnect/release behavior once physically/consumer-verified.
- `AccessoryChannelPublisher.kt` — batches returned bridge values/local-input observations into Rust channel ingestion.

### Critical legacy rule

A successful Android GATT write means only transport completion. It is **not** proof that the downstream CAN consumer applied the action.

The reviewed firmware does not automatically send a zero/release button mask on disconnect; `BridgeCompatibilityPolicy` must not invent one until the consumer semantics are proven.

## Future direct-output extension

Only if EpicHub deliberately adds ESP32-controlled relays/GPIO:

- `AccessoryCapability.kt` — negotiated version/capabilities.
- `DirectOutputDefinition.kt` — semantic hardware output identity, never raw GPIO in UI.
- `DirectOutputRequest.kt`
- `DirectOutputState.kt` — requested/confirmed/fault state backed by application-level protocol evidence.
- `Esp32DirectOutputGateway.kt` — versioned extension only.
- `DirectOutputSafetyPolicy.kt` — app-side expected guards; physical boot/default/interlock/watchdog/disconnect behavior remains firmware authority.

These files must **not** exist merely because the legacy bridge exists; scaffold them only when the capability is actually designed/approved.

---

# Compose UI

UI consumes typed state/models and semantic actions. It never imports raw ECU frames, MDF blocks, raw tune addresses or BLE characteristic details.

## `ui/dashboard/`

- `DashboardScreen.kt`
- `DashboardViewModel.kt`
- `DashboardUiState.kt`
- `DashboardLayout.kt`
- `DashboardPage.kt`
- `DashboardRenderer.kt`

### `dashboard/widgets/`

- `GaugeWidget.kt`
- `NumericWidget.kt`
- `BarWidget.kt`
- `IndicatorWidget.kt`
- `ActionWidget.kt`
- `GraphWidget.kt`
- `WidgetGeometry.kt`
- `WidgetBinding.kt`
- `WidgetStyle.kt`

Avoid one universal widget class combining render/edit/serialization.

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

3D is initially visual interpretation/rotation, not an alternate edit authority.

### `tuner/write/`
- `WritePreviewDialog.kt`
- `BurnDialog.kt`
- `TuneOperationStatus.kt`

These display engine plans/results only; they never create raw payloads or independently override profile condition authority.

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

UI requests bounded prepared data; whole logs do not live in Compose state.

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

---

# Rust workspace

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

Crate count remains provisional. Responsibility/file boundaries are the authority; crates are earned by dependency/build boundaries.

## `epichub-engine`

- `engine.rs` — composition only.
- `session.rs` — engine-level ECU/log/runtime session composition.
- `command.rs` — internal semantic commands, never wire bytes.
- `event.rs` — event fan-in/projection.
- `error.rs` — stable engine errors.

## `epichub-ffi`

- `jni_api.rs`
- `handle.rs`
- `snapshot_buffer.rs`
- `plot_buffer.rs`
- `event_buffer.rs`
- `error_bridge.rs`

## `epichub-channels`

- `channel_id.rs`
- `channel_schema.rs`
- `channel_value.rs`
- `sample_frame.rs`
- `channel_store.rs`
- `runtime_snapshot.rs`
- `channel_source.rs`
- `validity.rs`

Typed storage; avoid universal boxed/f64 current-value representation.

## `epichub-ecu`

No Android permission/enumeration APIs.

- `transport.rs` — transport contract.
- `usb_transport.rs` — only if native-FD sustained I/O wins PoC; otherwise adapter to coarse Kotlin I/O.
- `session.rs` — sole protocol session; serializes polling and exclusive transactions.
- `session_generation.rs`
- `retry_policy.rs`
- `request_codec.rs` — `BE len(command+data) + body + CRC(body)`.
- `response_codec.rs` — `BE len(status+data) + status/data + CRC(status+data)`.
- `crc.rs`
- `protocol_command.rs` — typed `S/Q`, `O`, `R`, `C`, `B`.
- `protocol_response.rs` — typed status/error/Burn ACK.
- `sync_probe.rs` — read-only synchronization/compatibility preflight.
- `signature_query.rs`
- `firmware_identity.rs`
- `runtime_poll.rs`
- `runtime_decoder.rs`
- `exclusive_operation.rs`

## `epichub-profile`

Parsing, support policy, schema compilation and condition authority are distinct.

- `profile.rs`
- `profile_fingerprint.rs`
- `ini_parser.rs`
- `ini_model.rs`
- `profile_support.rs`
- `primitive.rs`
- `runtime_schema.rs`
- `tune_schema.rs`
- `tuner_schema.rs`
- `menu_schema.rs`
- `profile_compiler.rs`
- `condition_parser.rs`
- `condition_evaluator.rs`
- `condition_authority.rs`

TunerStudio condition compatibility remains independently tested from the general log-analysis expression language.

## `epichub-tuning`

- `tune_snapshot.rs`
- `snapshot_read_plan.rs`
- `snapshot_reader.rs`
- `tune_model.rs`
- `target_resolver.rs`
- `value_codec.rs`
- `edit_request.rs`
- `write_plan.rs`
- `write_transaction.rs`
- `write_verifier.rs`
- `tune_safety_policy.rs`
- `dirty_pages.rs`
- `burn_plan.rs`
- `burn_transaction.rs`
- `burn_verifier.rs`
- `recovery_journal.rs`
- `uncertain_state.rs`
- `project_store.rs`

## `epichub-measurement`

- `measurement_store.rs`
- `time_range.rs`
- `sample_batch.rs`

## `epichub-mdf`

- `format.rs`
- `block.rs`
- `metadata.rs`
- `writer.rs`
- `reader.rs`
- `index.rs`
- `compression.rs`
- `recovery.rs`

MDF never knows Compose or tune semantics.

## `epichub-logging`

- `log_config.rs`
- `log_session.rs`
- `recorder.rs`
- `log_event.rs`
- `recovery_marker.rs`

## `epichub-analysis`

- `query.rs`
- `statistics.rs`
- `search.rs`
- `event_detector.rs`
- `histogram.rs`
- `scatter.rs`
- `heatmap.rs`
- `cache.rs`

## `epichub-expressions`

- `parser.rs`
- `ast.rs`
- `compiler.rs`
- `evaluator.rs`
- `function_registry.rs`

General runtime/log-analysis expressions; not automatically the profile compatibility grammar.

## `epichub-plotting`

- `viewport.rs`
- `plot_series.rs`
- `resolution_pyramid.rs`
- `minmax_envelope.rs`
- `plot_packet.rs`
- `plot_cache.rs`

## `epichub-diagnostics`

- `diagnostic_event.rs`
- `event_ring.rs`
- `performance_metric.rs`
- `diagnostic_snapshot.rs`
- `report.rs`

## `epichub-interop`

- `msl_reader.rs`
- `csv_reader.rs`
- `mlg_reader.rs`
- `mlg_writer.rs`

Exact MLG implementation responsibility is added only when the format investigation is turned into concrete contract/tests.

---

# ESP32 firmware topology requirement

The final accessory firmware repository/file structure is not yet chosen, but future work must stop using one `main.cpp` as the permanent ownership model.

If the legacy bridge is reimplemented/maintained, firmware responsibilities should separate at least:

```text
firmware/
├── ble_service.*
├── ble_protocol.*
├── can_bridge.*
├── button_bridge.*
├── variable_bridge.*
├── local_inputs.*
├── reconnect_liveness.*
└── diagnostics.*
```

If a direct-output extension is added, its output/interlock/failsafe modules are separate from the legacy bridge and versioned capability handling.

---

# Tests / fixtures

```text
tests/
├── fixtures/
│   ├── ecu-protocol/
│   │   ├── requests/
│   │   └── responses/
│   ├── generated-ini/
│   ├── tune-snapshots/
│   ├── esp32-protocol/
│   │   ├── button/
│   │   ├── variable-request/
│   │   ├── variable-response/
│   │   └── variable-set/
│   ├── mdf/
│   └── logs/
└── interoperability/
```

ESP32 fixtures must distinguish transport completion from downstream state and include disconnect/reconnect/partial-timeout cases where applicable.

Expected qualification layers:

- pure Kotlin unit tests;
- pure Rust unit/property tests;
- ECU request/response golden vectors;
- generated-INI accepted/unsupported corpus;
- ESP32 BLE/CAN wire golden vectors;
- MDF independent interoperability tests;
- JNI tests;
- Android instrumentation;
- simulator/fault injection;
- ECU/ESP32 bench tests;
- physical phone/tablet tests;
- vehicle acceptance where hardware behavior requires it.

---

# Dependency direction

```text
Compose UI
   │
   ├─ semantic actions ──────────► Kotlin bridge/accessory/platform owners
   │
   └─ NativeEngine façade ───────► FFI
                                     │
                                     ▼
                                 Rust Engine
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
                 ECU/Profile       Channels         Tuning
                                     │
                        ┌────────────┼───────────────┐
                        ▼            ▼               ▼
                      Logging       MDF           Analysis
                                                      │
                                                      ▼
                                                   Plotting
```

Hard rules:

- Android USB permission/device/CDC setup never imports tune semantics.
- ECU codecs do not know Compose, tuner menus or tuning policy.
- profile compiler does not own USB or mutate ECU state.
- tuning consumes typed profile authority and the sole ECU session.
- Compose displays profile condition/write state but does not authorize around it.
- BLE/GATT transport does not interpret downstream CAN success as confirmation.
- GPS-specific code feeds generic variable-set transport; generic transport does not depend on location.
- future direct ESP32 outputs do not share semantic state with the legacy button bridge simply because both may use BLE.
- MDF never imports Compose/ECU tuning semantics.
