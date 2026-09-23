# EpicHub Codebase Topology

## Status

**Phase 1 target responsibility topology.**

This describes the intended source organization closely enough that every known responsibility has a planned home before implementation. Exact crate/module/file splits may be refined when implementation evidence justifies it, but ownership/dependency boundaries must remain consistent with the architecture documents.

This is not authorization to scaffold production code before Phase 1 is explicitly accepted.

Historical EpicDash/EpicDash-JZ filenames are evidence sources, not migration units. `SOURCE_REIMPLEMENTATION_METHOD.md` governs every inherited subsystem implementation.

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

Locked application ID and namespace:

```text
com.epicefi.epichub
```

See `ANDROID_BUILD_BASELINE.md` / D-021.

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
├── settings/
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

PoC candidates include:

```text
A. Kotlin USB I/O/decode reference
B. Kotlin bulk I/O worker → coarse Rust batches/decode
C. Android opens/claims → proven native FD/handle → Rust sustained I/O/decode
```

The selected implementation is measurement-driven. Everything above the transport adapter remains identical. No architecture permits separate dashboard/tuner ECU owners.

---

# `platform/bluetooth/`

Android BLE/GATT remains Kotlin-owned.

- `AndroidBleScanner.kt` — scan lifecycle and candidate discovery.
- `Esp32GattSchema.kt` — supported legacy bridge service/characteristic UUIDs and later deliberately versioned schemas.
- `Esp32GattClient.kt` — one active GATT connection, discover/subscribe/MTU/priority, stale-GATT rejection and deterministic close.
- `BleReconnectPolicy.kt` — pure scan/retry/backoff decisions.
- `BleOperation.kt` — transport-level GATT operation description and class/priority/correlation metadata.
- `BleWriteScheduler.kt` — **one connection-wide serialized operation owner**. It replaces the historical independent per-characteristic FIFO ownership model.
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

No measurement-format/tune/profile serialization logic belongs here.

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

- `NativeEngine.kt` — small semantic façade; no high-rate per-channel/per-sample JNI traffic.
- `NativeEngineHandle.kt` — opaque lifetime guard.
- `NativeEngineError.kt` — stable Kotlin error projection.
- `NativeSnapshotBuffer.kt` — versioned packed runtime snapshot.
- `NativePlotBuffer.kt` — packed viewport-sized analysis result.
- `NativeEventReader.kt` — bounded structured event drain.
- `NativeEngineMetrics.kt` — coarse engine/JNI metrics.

Low-rate discrete Android-source ingestion may cross individually when justified; burst/high-rate ingestion is batched. See `KOTLIN_RUST_BOUNDARY.md`.

---

# Semantic actions: `actions/`

- `AppAction.kt` — stable semantic action definition.
- `ActionTarget.kt` — `Local`, `AccessoryBridge`, `AccessoryOutput` (only when supported), `Ecu`.
- `ActionRequest.kt` — requested semantic value/source/correlation.
- `ActionResult.kt` — accepted/rejected/pending/completed/failed; result semantics distinguish transport completion from real domain confirmation.
- `ActionSource.kt` — user/hardware/rule/internal source.
- `ActionRouter.kt` — routes directly to owning domain.
- `ActionPolicy.kt` — pure source/action guard policy.

If automation is later implemented, it is an explicit policy/rule layer that observes state/warning incidents and emits permitted `ActionRequest`s. Warning evaluation itself remains observational.

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
- `GpsVariableEncoder.kt` — GPS-specific producer feeding the generic variable-set codec.
- `VirtualInputEncoder.kt` — only if phone virtual ADC/input compatibility remains product-relevant.

There is deliberately no generic `Esp32MessageCodec` that becomes semantic authority for all present/future accessory protocols.

## Bridge state/gateway

- `Esp32BridgeGateway.kt` — semantic legacy bridge operations ↔ message-specific codecs ↔ connection-wide BLE scheduler.
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

# `settings/`

- `AppSettings.kt` — typed application/user preference model only.
- `SettingsStore.kt` — sole Kotlin settings owner/API.

The concrete Android persistence backend is an implementation choice. Settings never store or override authoritative ECU/profile/tune/channel state.

---

# Compose UI

UI consumes typed state/models and semantic actions. It never imports raw ECU frames, measurement-format blocks/records, raw tune addresses or BLE characteristic details.

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

Compose owns selection/text-entry/gesture/viewport/modal state. The Rust tuning domain owns semantic tune draft state/history once staged editing is introduced.

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
- `TableEditCommand.kt` — presentation intent only; resulting semantic edits are resolved/applied to the Rust draft model.
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

These consume the typed `SettingsStore`; they are not the settings authority.

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

Crate count remains provisional. Responsibility/file boundaries are the authority; crates are earned by dependency/build boundaries. The initial scaffold may start with fewer physical crates while preserving these logical domains.

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
- `tune_draft.rs` — semantic unverified staged edits/history; sole Rust semantic draft authority.
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

- `measurement_store.rs` — common reader/query contract for file-backed measurement sources.
- `measurement_writer.rs` — common writer contract consumed by the logger.
- `time_range.rs`
- `sample_batch.rs`
- `measurement_event.rs`

This domain defines format-neutral measurement contracts, not a proprietary file format.

## `epichub-mdf`

- `format.rs`
- `block.rs`
- `metadata.rs`
- `writer.rs`
- `reader.rs`
- `index.rs`
- `compression.rs`
- `recovery.rs`

MDF4 is the planned long-term native candidate. This crate/module is introduced when the MDF phase begins; it is not a prerequisite for the first logger or MLG writer. MDF never knows Compose or tune semantics.

## `epichub-logging`

- `log_config.rs`
- `log_session.rs`
- `recorder.rs`
- `log_event.rs`
- `recovery_marker.rs`

`recorder.rs` consumes format-neutral sample/event batches and a selected `MeasurementWriter`; it does not build MLG records or MDF blocks itself.

## `epichub-analysis`

Shared infrastructure:

- `analysis_session.rs`
- `analysis_identity.rs`
- `channel_contract.rs`
- `evidence.rs`
- `rejection.rs`
- `segment.rs`
- `event.rs`
- `visit_block.rs`
- `statistics.rs`
- `coverage.rs`
- `comparability.rs`
- `validation.rs`
- `identifiability.rs`
- `query.rs`
- `search.rs`
- `histogram.rs`
- `scatter.rs`
- `heatmap.rs`
- `cache.rs`
- `experiment_proposal.rs`

Domain profiles/models are added deliberately, for example:

```text
profiles/
├── ae/
├── pid/
├── tcharge/
└── injection_phasing/
```

These are not ports of historical plugin UI/controller structures. See `ANALYSIS_RESPONSIBILITY_MAP.md`.

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
- `warning_incident.rs`
- `report.rs`

Diagnostics/warning state remains observational. Any action triggered by observed warning state belongs to a separate explicit automation/action policy path.

## `epichub-interop`

- `msl_reader.rs`
- `csv_reader.rs`
- `mlg_reader.rs`
- `mlg_writer.rs`

MLG is the first planned ecosystem-compatible writer after the format-neutral live path is proven. Exact MLG implementation proceeds only after the real format contract/corpus is established.

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
│   ├── mlg/
│   ├── mdf/
│   ├── analysis/
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
- MLG ecosystem interoperability tests;
- MDF independent interoperability tests;
- analysis replay/golden fixtures;
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
                        ┌────────────┴───────────────┐
                        ▼                            ▼
                      Logging                    Analysis
                        │                            │
                        ▼                            ├─► Plotting
                 MeasurementWriter                  │
                  /            \                    ▼
              MLG/interop       MDF            MeasurementStore
                                              /            \
                                         interop readers   MDF
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
- MLG/MDF format implementations do not own runtime ChannelStore or logging-session truth.
- MDF never imports Compose/ECU tuning semantics.
- analysis consumes common measurement/channel contracts rather than requiring MDF-specific structures.
- semantic tuning drafts live in Rust; Compose owns edit interaction/presentation state only.
- settings are Kotlin-owned user/application preferences only and never duplicate ECU/profile/tune authority.

Before any inherited subsystem implementation is accepted, apply `SOURCE_REIMPLEMENTATION_METHOD.md` and record the old behaviors/bugs deliberately preserved or rejected.