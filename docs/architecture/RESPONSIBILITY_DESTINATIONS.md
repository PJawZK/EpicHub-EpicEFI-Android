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

The current `UsbEcuManager.kt` is explicitly treated as a multi-domain evidence bundle, not a future class template.

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `UsbDeviceSelectionPolicy.kt` | Android USB candidate gate | PRESERVE semantics | `platform/usb/UsbTransportCandidatePolicy.kt` |
| `UsbPermissionRequestTracker.kt` + permission receiver | permission lifecycle/deduplication and stale-result rejection | PRESERVE | `platform/usb/AndroidUsbPermission.kt` |
| `UsbEcuManager.kt` attach/detach receiver | Android device arrival/removal observation | SPLIT | `platform/usb/AndroidUsbDiscovery.kt` / connection coordinator |
| `UsbEcuManager.kt` CDC Union/Call Management parsing | resolve actual control/data interface relationship | SPLIT + PRESERVE behaviour | `platform/usb/CdcTopologyResolver.kt` |
| same bulk endpoint scoring/selection | select bounded bulk IN/OUT data pipe while excluding unsuitable interfaces | SPLIT | `platform/usb/CdcTopologyResolver.kt` + `UsbDeviceDescriptor.kt` |
| same interface claim/release/open/close | Android `UsbDeviceConnection` resource lifecycle | SPLIT | `platform/usb/AndroidUsbConnection.kt` |
| same `SET_LINE_CODING` / `SET_CONTROL_LINE_STATE` | CDC host control setup, including proven DTR/DTR+RTS compatibility attempts | SPLIT | `platform/usb/AndroidCdcControl.kt` |
| `UsbGenerationAuthority.kt`, permission coordinator, detach handling | connection-attempt generation/invalidation and stale callback suppression | PRESERVE semantics / REDESIGN | Rust ECU `session_generation.rs` once session starts; Kotlin permission generation remains in Android setup |
| `UsbEcuManager.kt` single scheduled USB owner | serialize sustained ECU I/O and exclusive transactions | PRESERVE principle / REDESIGN | Rust ECU `session.rs` if native runtime I/O wins PoC; otherwise one Kotlin transport worker behind same Rust session contract |
| `UsbEcuManager.kt` retry scheduling/failure caps | transport reconnect/retry policy | SPLIT | ECU session policy; Android only reports attach/detach/permission facts |
| `UsbEcuManager.kt` request `envelope()` | CRC request encoding | SPLIT + REDESIGN | Rust `request_codec.rs`, `crc.rs` |
| `UsbEcuManager.kt` `readEnvelope*`/status stripping | CRC response accumulation, validation and status extraction | SPLIT + REDESIGN | Rust `response_codec.rs`, `protocol_response.rs` |
| `EcuCommandResponseCodec.kt` | typed response/status normalization | PRESERVE concept | Rust `protocol_response.rs` |
| probe sequence + framed preflight | protocol synchronization and read-only compatibility probing | SPLIT | Rust `sync_probe.rs`, ECU session state machine |
| `UsbEcuManager.kt` output polling | live `O` scheduling/request | SPLIT + REDESIGN | Rust `runtime_poll.rs` |
| `UsbEcuManager.kt` output decoding/decode-plan aliases | output schema lookup and primitive decode | SPLIT + REDESIGN | profile `runtime_schema.rs` + Rust `runtime_decoder.rs` |
| `UsbFrameRateTracker.kt`, byte/CRC/request counters | ECU transport/protocol metrics | MERGE | `epichub-diagnostics/performance_metric.rs` + Android USB diagnostics for platform-only facts |
| tuning-exclusive operation state | one ECU mutation/read/Burn operation slot; ordinary poll pause/resume | PRESERVE semantics | Rust ECU session transaction ownership |
| transport freshness JSON | session generation, packet age and exclusive-operation freshness | MERGE + REDESIGN | Rust session/channel validity snapshot, projected to UI |

### Transport ownership gate

`AndroidUsbConnection.kt` is permanent because permission/open/claim/close are Android responsibilities. Whether the **sustained bulk I/O loop** is Kotlin-owned or receives an Android-opened native FD for Rust ownership remains a Phase 1/PoC decision. No other architecture responsibility should depend on that choice.

## ECU identity / profile

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| plain/framed `S/Q` probe | read-only signature query | PRESERVE | Rust `signature_query.rs` via the sole session |
| `EcuFirmwareIdentity.kt` | parse/report live firmware identity | PRESERVE semantics / REDESIGN | Rust `firmware_identity.rs` |
| `EcuRecognitionResult.kt` | recognition state separate from compatibility authority | PRESERVE | Rust identity/session projection |
| `EcuRecognitionRuntime.kt` | expose current recognition without second transport probe | PRESERVE principle | Rust engine/session query API |
| profileless probe path | identity-only connection then close | PRESERVE | session state machine; no output/tune authority |
| exact signature gate | live full signature must equal imported INI signature | PRESERVE | Rust profile/session authority |
| `UsbTunerStudioProfile.kt` line/comment/quote/list/string helpers | INI lexical/syntactic parsing | SPLIT + REDESIGN | `epichub-profile/ini_parser.rs` |
| same section dispatch (`TunerStudio`, `Constants`, `OutputChannels`, menu/dialog/table/curve) | typed source-model extraction | SPLIT | `ini_model.rs` |
| same parser validation (`msEnvelope_1.0`, little-endian, `O`, block size, channel presence) | explicit EpicHub supported-profile subset gate | SPLIT / ADD FORMAL OWNER | `profile_support.rs` |
| same primitive scalar/array/bit raw decode helpers | profile-defined primitive representation | SPLIT | `primitive.rs`; tuning engineering↔raw mutation conversion remains `epichub-tuning/value_codec.rs` |
| same page identifier/size/read/Burn list correlation | tune-page transport/storage schema compile | SPLIT | `tune_schema.rs` |
| same output channel definitions | runtime output schema compile | SPLIT | `runtime_schema.rs` |
| same table/curve/menu/dialog definitions | semantic tuner surface schema compile | SPLIT | `tuner_schema.rs` / `menu_schema.rs` |
| `TuningIniDefinition.kt` | canonicalize ambiguous/duplicate definitions, verify page/array/table/curve geometry, produce structural semantic model without values | MERGE + SPLIT | `profile_compiler.rs`, `tuner_schema.rs`; typed result consumed by tuning/UI projection |
| profile fingerprints | exact source identity | PRESERVE | `profile_fingerprint.rs` |
| `IniConditionEvaluator.kt` lexer/parser | generated-INI condition grammar | SPLIT | `condition_parser.rs` |
| same expression execution | evaluate conditions against verified current tune values | SPLIT | `condition_evaluator.rs` |
| `IniConditionAuthority` menu/dialog traversal | derive active/hidden/disabled semantic target gates | SPLIT | `condition_authority.rs` |
| same write policy | fail-closed write authorization for condition-constrained targets | PRESERVE | profile condition authority consumed by `epichub-tuning/tune_safety_policy.rs` |
| hardcoded board-specific geometry assumptions | historical risk | REMOVE | exact generated profile only |

Important rule: the INI condition grammar is compatibility logic and must not be casually replaced by the future general log-analysis expression language. Sharing low-level parser utilities is optional; semantic compatibility remains independently tested.

## Tune read/model/persistence

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuneSnapshot.kt` | immutable complete tune image/fingerprint | PRESERVE | `epichub-tuning/tune_snapshot.rs` |
| `TuneSnapshotReadPlan.kt` | bounded complete `R` read plan from current profile | PRESERVE | `snapshot_read_plan.rs` |
| `AuthoritativeTuneSnapshotReader.kt`, `T3SynchronousTuneSnapshotReader.kt`, manager bootstrap read | complete authoritative read | MERGE | `snapshot_reader.rs` using one typed ECU session |
| `TuningCore.kt` | tune semantic model/safety orchestration | SPLIT | `tune_model.rs`, `tune_safety_policy.rs`, target/write components |
| `TuningIniDefinition.kt` | profile-backed semantic target/surface definition | MERGE | profile compiler + Rust semantic tune model |
| `TuningScalarCodec.kt`, `UsbTuneReadCodec.kt`, profile primitive methods | raw/engineering primitive codecs | MERGE + REDESIGN | profile primitive representation + tuning `value_codec.rs` |
| `TuningWorkspace.kt`, `T4TuningWorkspaceSurface.kt` | semantic workspace/projection | SPLIT + REDESIGN | Rust `tune_model.rs`; Compose Tuner UI state/navigation |
| manager semantic workspace/JSON caches | derived profile+tune projection caching | REDESIGN | Rust identity-keyed caches; no JSON cache in the native hot path |
| `TunerPermanentProjectStore.kt` | persisted exact INI + verified snapshot authority | PRESERVE principle / REDESIGN storage | Rust `project_store.rs` + Kotlin platform storage adapter |
| recovery bundle/store UI | explicit recovery/export capability | REDESIGN | tuning recovery journal + platform export/Compose recovery UI if product still needs it |

## Tune RAM writes

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuningSemanticPreview.kt` | semantic proposed change preview | PRESERVE concept | Rust `edit_request.rs`, `write_plan.rs`; Compose preview UI |
| proposal factories / historical bench gates | target/value proposal semantics | MERGE or REFERENCE | generic Rust write plan; historical bench-only orchestration does not become production naming |
| `TuningWriteEngine.kt` | batch semantic target validation/planning | SPLIT | `target_resolver.rs`, `write_plan.rs`, `tune_safety_policy.rs` |
| manager pre-write full baseline reread | reject edits if tune changed after proposal | PRESERVE | `write_transaction.rs` preflight |
| `T3RamScalarTransaction.kt`, later normal manager write engine | bounded `C` write transaction | MERGE | `write_transaction.rs` |
| same | typed write ACK | SPLIT | ECU `protocol_response.rs` + `write_verifier.rs` |
| same exact `R` range readback | byte-exact per-target verification | SPLIT | `write_verifier.rs` |
| same post-batch complete reread | complete expected-image verification | PRESERVE | `write_verifier.rs` |
| dirty-page tracking | verified unsaved calibration pages | PRESERVE | `dirty_pages.rs` |
| manager possible-transmission failure handling | fail-closed mutation uncertainty; no automatic replay | PRESERVE | `uncertain_state.rs` + `recovery_journal.rs` where durability is required |
| bench/proof-specific UI/controller names | qualification machinery | REFERENCE | test/fixture/bench harness, not production app topology |

## Burn / persistence to ECU flash

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuneBurnPrimitives.kt` | Burn-page command and flash-status interpretation | PRESERVE semantics / SPLIT | profile Burn metadata + Rust `burn_plan.rs` / status model |
| manager initial live output flash-status preflight | require ready state before Burn | PRESERVE | `burn_verifier.rs` |
| `T6PersistentBurn.kt` + normal manager path | explicit `B` Burn state machine | SPLIT + REDESIGN | `burn_transaction.rs` |
| firmware distinct Burn ACK | protocol response semantics | PRESERVE | ECU `protocol_response.rs` + `burn_verifier.rs` |
| output-channel request progression (`tuneWriteId`, request count/pending evidence) | prove Burn request started/completed | PRESERVE | `burn_verifier.rs` |
| full post-Burn reread unchanged + no pending/unsaved state | persistence verification | PRESERVE | `burn_verifier.rs` |
| `T6PersistentBurnRecoveryFileStore.kt` / possible-B transmission failure | uncertain/interrupted Burn recovery | MERGE | common tuning `recovery_journal.rs`, `uncertain_state.rs` |
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
| manager USB probe/interface/control transfer diagnostics | Android transport setup diagnostics | SPLIT | `UsbConnectionDiagnostics.kt` |
| manager CRC/status/request/frame/failure diagnostics | ECU protocol/session diagnostics | SPLIT | Rust `epichub-diagnostics` |
| manager tuning state/workspace/write/Burn diagnostics | tuning diagnostics | SPLIT | Rust tuning result/state events + diagnostics projection |
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
- a future monolithic `UsbEcuManager` equivalent;
- a future monolithic generated-INI object that simultaneously parses text, decodes bytes, compiles UI structure, evaluates conditions and authorizes writes;
- one symmetric protocol `envelope()` abstraction that hides request/response differences;
- permanent stage-oriented source names (`T3`, `T4`, `T6`, `W4`, etc.);
- automatic Burn;
- board-name-based profile geometry;
- numbered handoff files;
- old package/signing/update-continuity machinery required only by EpicDash-JZ.