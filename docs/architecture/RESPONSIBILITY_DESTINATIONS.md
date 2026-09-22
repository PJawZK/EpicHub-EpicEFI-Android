# EpicHub Responsibility Destinations

## Purpose

This maps behavior/knowledge from reference implementations into the clean-slate EpicHub architecture. It is deliberately **not** a file migration checklist. Historical files are evidence only.

---

## Android shell / WebView-era ownership

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `DashboardLabActivity.kt`, `MainActivity.kt` | Activity/window lifecycle | SPLIT + REDESIGN | `app/EpicHubActivity.kt`, `platform/lifecycle/*` |
| same | top-level navigation | SPLIT + REDESIGN | `navigation/EpicHubNavHost.kt`, `AppRoute.kt` |
| same | USB coordination | SPLIT | `platform/usb/*` + Rust ECU session |
| same | BLE coordination | SPLIT | `platform/bluetooth/*`, `accessory/*` |
| same | location coordination | SPLIT | `platform/location/*` |
| same | diagnostics exposure | SPLIT + MERGE | `ui/diagnostics/*`, `epichub-diagnostics` |
| same | MSL playback | SPLIT + REDESIGN | `epichub-interop/msl_reader.rs` + common ChannelSource |
| WebView/JS dashboard+tuner | presentation | REDESIGN | Compose `ui/dashboard/*`, `ui/tuner/*` |
| native→JSON→JS bridge | runtime telemetry transfer | REMOVE | packed `NativeSnapshotBuffer.kt` + Rust `runtime_snapshot.rs` |
| DOM/WebView renderer | normal driving/tuner renderer | REMOVE | Compose/custom draw; specialized renderer only if benchmarked need |

## Runtime data

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `DashboardDataHub.kt` | current runtime values | MERGE + REDESIGN | Rust `epichub-channels/channel_store.rs` |
| same | source/freshness/session projections | SPLIT | Rust validity/session + display snapshot |
| `DashboardRuntimeSnapshotStore.kt` | bounded immutable latest state | PRESERVE semantics / REDESIGN | Rust `runtime_snapshot.rs` + FFI snapshot buffer |
| `VariableRepository.kt`, `variables.json` | channel metadata | MERGE + REDESIGN | channel schema + exact-profile runtime schema |
| dashboard math | derived channels | REDESIGN | `epichub-expressions/*` |
| dashboard warning logic | warning rules | REDESIGN | typed channel/rule engine; UI displays results |

## USB / ECU transport

`UsbEcuManager.kt` is a multi-domain evidence bundle, never a future class template.

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `UsbDeviceSelectionPolicy.kt` | USB candidate gate | PRESERVE | `platform/usb/UsbTransportCandidatePolicy.kt` |
| permission tracker/receiver | permission lifecycle/dedup/stale-result protection | PRESERVE | `AndroidUsbPermission.kt` |
| attach/detach handling | Android device observation | SPLIT | `AndroidUsbDiscovery.kt` |
| CDC Union/Call Management parsing | actual CDC topology | SPLIT + PRESERVE | `CdcTopologyResolver.kt` |
| endpoint scoring/selection | bulk IN/OUT pipe selection | SPLIT | topology resolver + descriptor model |
| open/claim/release/close | Android USB resource lifecycle | SPLIT | `AndroidUsbConnection.kt` |
| line coding + DTR/RTS | CDC host setup | SPLIT | `AndroidCdcControl.kt` |
| generation/invalidation | stale work suppression | PRESERVE semantics / REDESIGN | Kotlin permission/open generation then Rust `session_generation.rs` |
| single USB worker | one sustained ECU owner | PRESERVE principle / REDESIGN | Rust `session.rs` if native I/O wins; otherwise one Kotlin transport worker behind same session contract |
| retry/failure caps | reconnect policy | SPLIT | ECU session policy |
| request `envelope()` | request CRC framing | SPLIT + REDESIGN | Rust `request_codec.rs`, `crc.rs` |
| response readers/status stripping | response framing/status | SPLIT + REDESIGN | Rust `response_codec.rs`, `protocol_response.rs` |
| plain/framed probe | synchronization/read-only compatibility | SPLIT | `sync_probe.rs` |
| output polling | `O` scheduling | SPLIT + REDESIGN | `runtime_poll.rs` |
| output decode aliases | exact-profile output decode | SPLIT + REDESIGN | profile `runtime_schema.rs` + ECU `runtime_decoder.rs` |
| USB frame/rate/CRC metrics | platform + protocol diagnostics | SPLIT | Kotlin USB diagnostics + Rust diagnostics |
| tuning-exclusive state | serialize Read/Write/Burn vs polling | PRESERVE | Rust ECU/tuning transaction gate |

### Transport ownership gate

`AndroidUsbConnection.kt` remains permanent because permission/open/claim/close are Android responsibilities. Sustained bulk I/O is the remaining Kotlin-batched-vs-native-FD benchmark decision; no upper responsibility may depend on which wins.

## ECU identity / generated profile

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `S/Q` probe | read-only signature query | PRESERVE | `signature_query.rs` |
| `EcuFirmwareIdentity.kt` | diagnostic firmware identity | PRESERVE semantics / REDESIGN | `firmware_identity.rs` |
| recognition result/runtime | recognition distinct from compatibility authority | PRESERVE | Rust session identity projection |
| profileless path | identify then close | PRESERVE | session state machine |
| exact signature gate | profile authorization | PRESERVE | Rust profile/session authority |
| `UsbTunerStudioProfile.kt` syntax helpers | INI syntax parsing | SPLIT + REDESIGN | `ini_parser.rs` |
| section dispatch | typed raw source model | SPLIT | `ini_model.rs` |
| envelope/endian/output checks | supported-subset gate | SPLIT / FORMALIZE | `profile_support.rs` |
| primitive raw decode | primitive representation | SPLIT | `primitive.rs` |
| page IDs/sizes/read/Burn | tune storage schema | SPLIT | `tune_schema.rs` |
| output definitions | runtime schema | SPLIT | `runtime_schema.rs` |
| table/curve/menu/dialog | tuner semantic schema | SPLIT | `tuner_schema.rs`, `menu_schema.rs` |
| `TuningIniDefinition.kt` | canonicalization/geometry validation | MERGE + SPLIT | `profile_compiler.rs` |
| profile fingerprints | exact source identity | PRESERVE | `profile_fingerprint.rs` |
| INI condition parser/evaluator | compatibility condition grammar | SPLIT | `condition_parser.rs`, `condition_evaluator.rs` |
| `IniConditionAuthority` | target enable/visibility/write authority | PRESERVE safety semantics | `condition_authority.rs` + tuning policy |
| board-name geometry assumptions | unsafe shortcut | REMOVE | exact generated profile only |

INI compatibility conditions remain independently tested from the general analyzer expression language.

## Tune read/model/persistence

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `TuneSnapshot.kt` | immutable complete tune image/fingerprint | PRESERVE | `tune_snapshot.rs` |
| `TuneSnapshotReadPlan.kt` | bounded exact-profile read plan | PRESERVE | `snapshot_read_plan.rs` |
| snapshot readers + manager bootstrap | authoritative complete `R` read | MERGE | `snapshot_reader.rs` |
| `TuningCore.kt` | tune model/safety orchestration | SPLIT | tune model + safety/write components |
| `TuningIniDefinition.kt` | semantic tuner structure | MERGE | profile compiler + tune model |
| scalar/read codecs | raw/engineering conversions | MERGE + REDESIGN | profile primitives + `value_codec.rs` |
| workspace/surface | semantic editing projection | SPLIT + REDESIGN | Rust tune model + Compose tuner |
| workspace/JSON caches | derived acceleration | REDESIGN | Rust exact-identity-bound caches |
| `TunerPermanentProjectStore.kt` | exact INI + verified snapshot persistence | PRESERVE principle / REDESIGN | `project_store.rs` + Kotlin file adapter |
| recovery bundles/stores | recovery/export | REDESIGN | recovery journal + export/recovery UI where retained |

## RAM writes / Burn

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| semantic preview | proposed semantic edits | PRESERVE concept | `edit_request.rs`, `write_plan.rs` + Compose preview |
| `TuningWriteEngine.kt` | semantic batch planning | SPLIT | target resolver + write plan + safety policy |
| pre-write full reread | baseline identity proof | PRESERVE | `write_transaction.rs` preflight |
| bounded `C` path | RAM write transaction | MERGE | `write_transaction.rs` |
| typed ACK | protocol response verification | SPLIT | ECU protocol response + write verifier |
| exact `R` readback | edited-range proof | PRESERVE | `write_verifier.rs` |
| complete expected-image reread | final RAM-state proof | PRESERVE | `write_verifier.rs` |
| dirty page tracking | unsaved verified pages | PRESERVE | `dirty_pages.rs` |
| possible-transmission failure | fail-closed uncertainty | PRESERVE | `uncertain_state.rs`, recovery journal |
| Burn primitives/status | page command + flash state | SPLIT | profile Burn metadata + burn plan/verifier |
| normal/T6 Burn paths | explicit Burn state machine | MERGE + REDESIGN | `burn_transaction.rs` |
| Burn ACK/progression/full reread | persistence proof | PRESERVE | `burn_verifier.rs` |
| automatic Burn | prohibited | REMOVE | none |

## ESP32 legacy bridge / BLE

The reviewed original firmware is a BLE↔CAN bridge with local inputs, **not** a proven relay/GPIO output controller.

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| original/EpicDash-JZ BLE scan | BLE discovery | SPLIT | `AndroidBleScanner.kt` |
| evolved GATT lifecycle | connect/discover/subscribe/MTU/priority/stale-GATT cleanup | SPLIT + PRESERVE behaviour | `Esp32GattClient.kt` |
| reconnect evolution | scan/retry/backoff policy | SPLIT | `BleReconnectPolicy.kt` |
| UUID constants | legacy bridge service schema | SPLIT | `Esp32GattSchema.kt` |
| three per-characteristic FIFOs | need for serialized GATT operations | MERGE + REDESIGN | **one connection-wide** `BleWriteScheduler.kt` / operation scheduler |
| button characteristic `...a8` | LE 16-bit button-mask bridge | PRESERVE compatibility where required | `LegacyButtonBridgeCodec.kt` / `Esp32BridgeGateway.kt` |
| CAN button forwarding | ID `0x711`, `[5A,00,27,maskHi,maskLo]` | PRESERVE compatibility evidence | bridge protocol fixture/codec |
| variable request `...aa` | batched BE hash requests → sequential CAN `0x701` | PRESERVE compatibility / REDESIGN request model | `LegacyVariableRequestCodec.kt`, bridge gateway |
| variable response `...a9` | CAN `0x721` hash+float → batched notification | PRESERVE compatibility | `LegacyVariableResponseCodec.kt` |
| original timeout/partial-batch behavior | implicit request status | REDESIGN | explicit bridge request correlation/status model |
| characteristic `...ab` historically named GPS | generic BE hash+raw32 variable-set → CAN `0x781` | PRESERVE wire / RENAME | `Esp32VariableSetCodec.kt` |
| `LegacyGpsPayloadMapper.kt` | GPS-specific producer for generic variable-set | PRESERVE where required / RENAME | `GpsVariableEncoder.kt` |
| phone virtual ADC values | another generic variable-set producer | REFERENCE / preserve if product-relevant | virtual input producer feeding variable-set gateway |
| ESP32 GPIO5 ADC | local ADC→CAN variable-set | REFERENCE / preserve if needed | ESP32 bridge firmware |
| ESP32 GPIO1 digital input | local digital→CAN variable-set | REFERENCE / preserve if needed | ESP32 bridge firmware; current TODO hash not permanent authority |
| ESP task watchdog | firmware liveness | PRESERVE concept | ESP32 firmware |
| BLE advertising restart | firmware reconnect availability | PRESERVE concept | ESP32 firmware |
| no disconnect zero-mask send | release semantics absent | ADD FORMAL CONTRACT | compatibility policy + physical/consumer verification before any change |
| GATT write callback | transport completion only | PRESERVE observation / REMOVE as confirmation | bridge transport state, never downstream confirmed state |
| no version/capability/ack/state characteristic | legacy limitation | ADD if future hardware needs it | versioned extension, not legacy compatibility codec |
| UI raw button bits/UUIDs | transport leakage | REMOVE | `AppAction` + gateway mapping |
| direct relay/GPIO output/failsafe assumption | not present in reviewed legacy firmware | REMOVE assumption | separate future versioned direct-output capability |
| future direct output ack/state/interlocks/failsafe | new capability | ADD / PROPOSED | ESP32 firmware + Kotlin accessory gateway; physical authority firmware-side |
| returned bridge variables/local inputs | telemetry ingestion | REDESIGN | `AccessoryChannelPublisher.kt` → Rust ChannelStore |

### Legacy bridge state rule

`writeCharacteristic()` acceptance or GATT completion may mean queued/transport-completed, but does **not** prove the downstream CAN consumer applied a button/action. EpicHub must keep downstream state `unknown` unless independently observed.

### Future direct-output rule

If EpicHub later controls relays/GPIO directly on ESP32, the protocol is deliberately versioned and must define capabilities, semantic output IDs, app-level ack, confirmed state/faults, boot defaults, interlocks and disconnect/watchdog behavior. None of that is inherited from the legacy bridge.

## GPS/location

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `LocationDataHub.kt` | Android location acquisition | SPLIT + REDESIGN | `AndroidLocationSource.kt` |
| `GpsSampleMapper.kt` | sample normalization/quality | SPLIT | `LocationSample.kt`, `LocationQuality.kt` |
| listener fan-out | delivery mechanism | REMOVE | explicit channel + bridge consumers |
| packed GPS mapping | GPS-specific variable-set encoding | PRESERVE where needed / RENAME | `GpsVariableEncoder.kt` |
| GPS display values | telemetry channels | MERGE | Rust ChannelStore |

## Logging / interoperability

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| `MslLogPlayer.kt` | MSL parse/playback | SPLIT + REDESIGN | `epichub-interop/msl_reader.rs` + ChannelSource |
| CSV import | CSV interoperability | PRESERVE | `csv_reader.rs` |
| TS/MLV ecosystem work | MLG read/write interoperability | ADD/REDESIGN | `mlg_reader.rs`, `mlg_writer.rs` when format work is specified |
| no native MDF4 | high-performance native measurement storage | ADD | `epichub-mdf/*` |
| logging UI mixed with format | recording orchestration | ADD | `epichub-logging/*` + Compose logger |

## Analyzer / plotting

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| WebView analysis/history | analyzer UX concepts | REFERENCE + REDESIGN | Rust analyzer + Compose Analyzer |
| historical graph behavior | graph interaction ideas | REFERENCE | Compose `PlotSurface.kt` |
| no large-file lazy engine | bounded analysis query | ADD | `epichub-analysis/*` |
| no spike-preserving multires pyramid | scalable plot prep | ADD | `epichub-plotting/*` |
| tuning plugin algorithms | domain analysis/autotune | REFERENCE then MERGE | future analyzers over common channels/analysis engine |

## Diagnostics / performance

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| diagnostic stores/reporting | bounded structured diagnostics | MERGE + REDESIGN | Rust event ring + Kotlin platform diagnostics |
| lifecycle diagnostics | owner/lifetime tracing | PRESERVE need / REDESIGN | Kotlin lifecycle diagnostics + shared event schema |
| performance metrics | measurement discipline | PRESERVE | Android frame/resource metrics + Rust engine metrics |
| USB platform diagnostics | permission/interface/control facts | SPLIT | `UsbConnectionDiagnostics.kt` |
| ECU protocol diagnostics | CRC/status/rates/session facts | SPLIT | Rust diagnostics |
| tuning diagnostics | Read/Write/Burn state/evidence | SPLIT | Rust tuning events/snapshot |
| BLE queue diagnostics | GATT operation rate/depth/latency/drop | PRESERVE concept / REDESIGN | diagnostics around one global GATT scheduler |

## Settings / editor / local persistence

| Historical evidence | Responsibility extracted | Classification | EpicHub destination |
|---|---|---|---|
| Settings manager/UI | preferences | REDESIGN | Compose settings + typed settings store |
| dashboard layout editor | editable dashboard layout | PRESERVE product capability / REDESIGN | Compose editor + versioned layout schema |
| old package migration | install continuity | REMOVE | none |

## Explicit no-destination items

The following historical architecture is intentionally not migrated:

- WebView/DOM/JSON as ordinary runtime presentation;
- raw UI-to-ECU memory writes;
- separate dashboard/tuner USB stacks;
- dashboard-specific telemetry authority;
- a replacement monolithic `UsbEcuManager`;
- a monolithic generated-INI parser/model/condition/write-authority object;
- symmetric request/response `envelope()` abstraction;
- three independent BLE characteristic write owners;
- GPS-specific naming for generic variable-set transport;
- assumed legacy ESP32 relay-output/failsafe authority;
- permanent stage names (`T3`, `T4`, `T6`, `W4`, etc.);
- automatic Burn;
- board-name-based tune geometry;
- numbered handoffs;
- old package/signing/update-continuity machinery.