# EpicHub Responsibility Destinations

## Purpose

This is the behaviour/knowledge-transfer map from reference implementations into the clean-slate EpicHub topology.

It is deliberately **not** a file migration checklist. Historical files are listed only to identify evidence. A historical file may split across many future owners; several historical mechanisms may merge into one new responsibility.

This map remains incomplete until the full Phase 1 repository archaeology is complete.

## Android shell / WebView-era ownership

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `DashboardLabActivity.kt`, `MainActivity.kt` | Android Activity lifecycle/window ownership | SPLIT + REDESIGN | `app/EpicHubActivity.kt`, `platform/lifecycle/*` |
| same | top-level navigation | SPLIT + REDESIGN | `navigation/EpicHubNavHost.kt`, `navigation/AppRoute.kt` |
| same | USB coordination | SPLIT | `platform/usb/*` + Rust engine session |
| same | BLE coordination | SPLIT | `platform/bluetooth/*`, `accessory/*` |
| same | location coordination | SPLIT | `platform/location/*` |
| same | diagnostics exposure | SPLIT + MERGE | `ui/diagnostics/*`, `epichub-diagnostics` |
| same | MSL playback | SPLIT + REDESIGN | `epichub-interop/msl_reader.rs` + common ChannelSource path |
| `dashboard_lab.html`, JS runtime bridge | dashboard/tuner presentation | REDESIGN | Compose `ui/dashboard/*`, `ui/tuner/*` |
| WebView JSON/JavaScript bridge | runtime telemetry transport to UI | REMOVE architecture | packed `NativeSnapshotBuffer.kt` + Rust `runtime_snapshot.rs` |
| DOM/WebView rendering | normal runtime renderer | REMOVE architecture | Compose/custom draw; specialized renderer only if benchmarked need |

## Runtime data

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `DashboardDataHub.kt` | current runtime value authority | MERGE + REDESIGN | Rust `epichub-channels/channel_store.rs` |
| `DashboardDataHub.kt` | source/freshness/session projections | SPLIT | Rust channel/session validity + display snapshot |
| `DashboardRuntimeSnapshotStore.kt` | bounded immutable latest-state delivery | PRESERVE semantics / REDESIGN | Rust `runtime_snapshot.rs` + FFI `snapshot_buffer.rs` |
| `VariableRepository.kt`, `variables.json` | variable/channel metadata | MERGE + REDESIGN | `epichub-channels/channel_schema.rs` + profile-derived runtime schema |
| dashboard math/custom logic | derived channels | REDESIGN | `epichub-expressions/*` feeding ChannelStore/analysis |
| dashboard warning logic | warning condition evaluation | REDESIGN | common expressions/rules over typed channels; presentation only displays result |

## USB / ECU transport

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `UsbDeviceSelectionPolicy.kt` | Android USB candidate gate | PRESERVE semantics | `platform/usb/UsbTransportCandidatePolicy.kt` |
| `UsbPermissionRequestTracker.kt` | permission lifecycle/deduplication | PRESERVE | `platform/usb/AndroidUsbPermission.kt` |
| `UsbEcuManager.kt` | Android USB discovery/open/close | SPLIT | `AndroidUsbDiscovery.kt`, `AndroidUsbConnection.kt` |
| `UsbEcuManager.kt` | single ECU session/generation owner | SPLIT + REDESIGN | Rust ECU `session.rs`; Kotlin only owns Android resource setup |
| `UsbGenerationAuthority.kt` | generation/session invalidation | PRESERVE semantics | Rust ECU/runtime session model |
| `UsbEcuManager.kt`, framing code | binary command framing/CRC | SPLIT + REDESIGN | Rust `frame_codec.rs`, `crc.rs`, protocol request/response files |
| `EcuCommandResponseCodec.kt` | protocol response normalization | PRESERVE concept | Rust `protocol_response.rs` |
| `UsbEcuManager.kt` | output polling | SPLIT + REDESIGN | Rust `runtime_poll.rs` |
| `UsbEcuManager.kt` | output decoding | SPLIT + REDESIGN | Rust `runtime_decoder.rs` + profile `runtime_schema.rs` |
| `UsbFrameRateTracker.kt` | telemetry rate measurement | MERGE | Rust/Kotlin diagnostics performance metrics as appropriate |
| USB manager tuning-exclusive operation state | exclusive ECU operation coordination | PRESERVE semantics | Rust ECU session transaction ownership |

## ECU identity / profile

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `EcuFirmwareIdentity.kt` | parse/report live firmware identity | PRESERVE semantics / REDESIGN | Rust `firmware_identity.rs` |
| `EcuRecognitionResult.kt` | recognition state separate from compatibility authority | PRESERVE | Rust identity/session projection |
| `EcuRecognitionRuntime.kt` | expose current recognition without second transport probe | PRESERVE principle | Rust engine/session query API |
| profileless probe path | identity-only connection then close | PRESERVE | Rust/Kotlin session flow after USB setup |
| `UsbTunerStudioProfile.kt` | exact generated-INI parse | SPLIT + REDESIGN | `epichub-profile/ini_parser.rs`, `ini_model.rs` |
| same | runtime output schema compile | SPLIT | `runtime_schema.rs` |
| same / `TuningIniDefinition.kt` | tune/calibration schema compile | MERGE + SPLIT | `tune_schema.rs` |
| same | menu/hierarchy/presentation definitions | SPLIT | `menu_schema.rs` |
| profile fingerprints | exact profile identity | PRESERVE | `profile_fingerprint.rs` |
| `IniConditionEvaluator.kt` | condition parse/evaluate | SPLIT | `condition_parser.rs`, `condition_evaluator.rs` |
| hardcoded board-specific geometry assumptions | historical risk | REMOVE | exact generated profile only |

## Tune read/model/persistence

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuneSnapshot.kt` | immutable complete tune image/fingerprint | PRESERVE | `epichub-tuning/tune_snapshot.rs` |
| `TuneSnapshotReadPlan.kt` | bounded complete read plan | PRESERVE | `snapshot_read_plan.rs` |
| `AuthoritativeTuneSnapshotReader.kt`, `T3SynchronousTuneSnapshotReader.kt` | complete authoritative read | MERGE | `snapshot_reader.rs` |
| `TuningCore.kt` | tune semantic model/safety orchestration | SPLIT | `tune_model.rs`, `tune_safety_policy.rs`, target/write components |
| `TuningIniDefinition.kt` | profile-backed target definitions | MERGE | `epichub-profile/tune_schema.rs` + Rust semantic tune model |
| `TuningScalarCodec.kt`, `UsbTuneReadCodec.kt` | raw/engineering primitive codecs | MERGE + REDESIGN | `value_codec.rs` plus protocol read primitives where separate |
| `TuningWorkspace.kt`, `T4TuningWorkspaceSurface.kt` | semantic workspace/projection | SPLIT + REDESIGN | Rust `tune_model.rs`; Compose Tuner UI state/navigation |
| `TunerPermanentProjectStore.kt` | persisted exact INI + verified snapshot authority | PRESERVE principle / REDESIGN storage | Rust `project_store.rs` + Kotlin platform storage adapter |
| recovery bundle/store UI | explicit recovery/export capability | REDESIGN | tuning recovery journal + platform export/Compose recovery UI if product still needs it |

## Tune RAM writes

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuningSemanticPreview.kt` | semantic proposed change preview | PRESERVE concept | Rust `edit_request.rs`, `write_plan.rs`; Compose preview UI |
| proposal factories / historical bench gates | target/value proposal semantics | MERGE or REFERENCE | generic Rust write plan; historical bench-only orchestration does not become production naming |
| `TuningWriteEngine.kt` | batch semantic target validation/planning | SPLIT | `target_resolver.rs`, `write_plan.rs`, `tune_safety_policy.rs` |
| `T3RamScalarTransaction.kt`, later normal write engine | bounded C write transaction | MERGE | `write_transaction.rs` |
| same | exact range read-back/full expected snapshot verification | SPLIT | `write_verifier.rs` |
| `T3RamRecoveryFileStore.kt` and later uncertainty handling | interrupted/uncertain operation recovery state | MERGE + REDESIGN | `recovery_journal.rs`, `uncertain_state.rs` |
| bench/proof-specific UI/controller names | qualification machinery | REFERENCE | test/fixture/bench harness, not production app topology |

## Burn / persistence to ECU flash

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuneBurnPrimitives.kt` | burn-page/flash protocol primitives | PRESERVE semantics / SPLIT | profile burn metadata + Rust burn protocol/plan |
| `T6PersistentBurn.kt` | explicit Burn state machine | SPLIT + REDESIGN | `burn_plan.rs`, `burn_transaction.rs`, `burn_verifier.rs` |
| same | flash status/progression verification | SPLIT | `burn_verifier.rs` |
| `T6PersistentBurnRecoveryFileStore.kt` | uncertain/interrupted Burn recovery | MERGE | common tuning `recovery_journal.rs`, Burn-specific recovery state as needed |
| automatic Burn | prohibited behavior | REMOVE | no destination |

## ESP32 BLE/accessory path

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| original/EpicDash-JZ `BleManager.kt` | BLE scanning | SPLIT | `AndroidBleScanner.kt` |
| same | GATT connect/discover/subscribe | SPLIT | `Esp32GattClient.kt` |
| EpicDash-JZ reconnect additions | reconnect/backoff policy | SPLIT | `BleReconnectPolicy.kt` |
| same | characteristic UUID schema | SPLIT | `Esp32GattSchema.kt` |
| button/variable/GPS queues | serialize GATT writes | MERGE transport mechanism | `BleWriteScheduler.kt` with explicit class/priority policy |
| original button mask encoding | ESP32 wire codec | PRESERVE protocol where compatibility required | `Esp32MessageCodec.kt` |
| original batched variable hash/request response | ESP32 variable protocol | PRESERVE protocol where required | `Esp32MessageCodec.kt`, `Esp32AccessoryGateway.kt` |
| GPS payload mapping/queue | GPS→ESP32 protocol | SPLIT | `Esp32GpsEncoder.kt` + `Esp32AccessoryGateway.kt` |
| callback `onVariableData` | returned accessory/ECU-bridge values | REDESIGN | `AccessoryStateStore.kt` and/or `AccessoryChannelPublisher.kt` |
| UI button numbers/masks as semantic identity | transport leakage into UI | REMOVE architecture | `AppAction`, `AccessoryDefinition`, `ActionRouter` |
| no explicit requested-vs-confirmed relay model | missing responsibility | ADD | `AccessoryState.kt`, `AccessoryStateStore.kt` |
| ESP32 hardware GPIO/relay failsafe | physical authority | ADD/formalize | ESP32 firmware, with Android `AccessoryFailsafePolicy` only describing expected semantics |

## GPS/location

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `LocationDataHub.kt` | Android location acquisition | SPLIT + REDESIGN | `AndroidLocationSource.kt` |
| `GpsSampleMapper.kt` | sample normalization/mapping | SPLIT | `LocationSample.kt`, `LocationQuality.kt`, channel publisher |
| `GpsListenerDispatcher.kt` | fan-out/listener delivery | REMOVE mechanism | explicit consumers/batched channel+accessory gateways |
| `LegacyGpsPayloadMapper.kt` | exact ESP32/CAN packed representation | PRESERVE protocol if still required / RENAME | `Esp32GpsEncoder.kt` |
| GPS runtime display values | telemetry channels | MERGE | Rust ChannelStore |

## Logging/playback/interoperability

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `MslLogPlayer.kt` | MSL parsing/playback timing/source | SPLIT + REDESIGN | `epichub-interop/msl_reader.rs` + ChannelSource/playback session |
| CSV import | CSV interoperability | PRESERVE useful capability | `epichub-interop/csv_reader.rs` |
| existing TS/MLV logging ecosystem work | MLG read/write interoperability | ADD/REDESIGN | `mlg_reader.rs`, `mlg_writer.rs` after format work is defined |
| no native MDF4 implementation | native high-performance measurement storage | ADD | `epichub-mdf/*` |
| logging UI/state mixed with format concerns | recording orchestration | ADD | `epichub-logging/*` + Compose `ui/logger/*` |

## Analysis/history/graphs

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| WebView analysis/history features | time-history analysis UX | REFERENCE + REDESIGN | Rust analyzer + Compose Analyzer |
| historical graph rendering | graph interaction/visual behaviour | REFERENCE | Compose `PlotSurface.kt` |
| no large-file lazy query engine | analysis query system | ADD | `epichub-analysis/query.rs` etc. |
| no multires spike-preserving data pyramid | scalable plot preparation | ADD | `epichub-plotting/resolution_pyramid.rs`, `minmax_envelope.rs` |
| separate tuning plugin algorithms | tuning analysis/autotune | REFERENCE then MERGE | future domain analyzers using common `epichub-analysis` + channels |

## Diagnostics / performance

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `DiagnosticStore.kt` | bounded diagnostic state/history | MERGE + REDESIGN | Rust `event_ring.rs` + Kotlin platform diagnostics where Android-owned |
| `DiagnosticReportFormatter.kt` | report formatting/export | SPLIT | engine `report.rs` + Kotlin export/presentation |
| `DiagnosticHistoryCodec.kt` | persisted diagnostic history format | REFERENCE | new explicit diagnostics schema only if persistence remains needed |
| `LifecycleDiagnostics.kt` | lifecycle ownership/trace evidence | PRESERVE need / REDESIGN | Android lifecycle diagnostics + structured event schema |
| `PerformanceMetrics.kt`, rolling metrics | performance measurement | PRESERVE discipline | `AndroidFrameMetrics.kt`, Rust `performance_metric.rs` |
| BLE write queue diagnostics | queue depth/progress/latency | PRESERVE concept | `BleConnectionDiagnostics.kt` + structured diagnostics |

## Settings/editor/local persistence

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `SettingsManager.kt`, Settings Activity/WebView settings | user preferences | REDESIGN | Compose settings + typed settings repository/store to be defined |
| old dashboard layout editor | editable dashboard layout | PRESERVE product capability / REDESIGN | Compose dashboard editor + versioned layout schema |
| old app/package migration | install continuity | REMOVE | none; new EpicHub has no EpicDash-JZ update continuity requirement |

## Explicit no-destination items

The following historical architecture is intentionally not migrated:

- WebView/DOM/JSON as the ordinary driving/tuner runtime path;
- raw UI-to-ECU memory writes;
- separate dashboard and tuner USB stacks;
- dashboard-specific telemetry as global data authority;
- permanent stage-oriented source names (`T3`, `T4`, `T6`, `W4`, etc.);
- automatic Burn;
- board-name-based profile geometry;
- numbered handoff files;
- old package/signing/update-continuity machinery required only by EpicDash-JZ.
