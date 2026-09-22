# EpicHub Kotlin ↔ Rust Boundary

## Goal

Use Kotlin where Android framework integration is native and direct; use Rust where sustained high-volume data/ECU/file/analysis work benefits from native control. The boundary must be narrow enough that using two languages does not create two competing application architectures.

## Ownership summary

Kotlin owns:

- Compose UI/navigation;
- Android lifecycle/services/permissions;
- USB discovery/permission/opening;
- Android BLE/GATT/ESP32 transport;
- Android location;
- SAF/file picker/export UI;
- semantic ActionRouter and accessory state.

Rust owns or is intended to own:

- ECU protocol/session processing after the selected transport handoff;
- runtime ChannelStore;
- profile/INI compilation;
- MDF4;
- logger internals;
- analysis/expressions/plot preparation;
- semantic tuning authority after parity/qualification.

## Boundary rule

Cross-language communication occurs at:

- engine/session lifecycle boundaries;
- semantic operation boundaries;
- bounded immutable snapshots;
- viewport-sized analysis/plot results;
- batched external-source channel ingestion;
- bounded structured-event drains.

It does **not** occur per sample, per channel or per drawing primitive.

## Intended semantic API shape

Conceptual operations, not final JNI signatures:

```text
Engine.create(config)
Engine.close()

Engine.attachEcuTransport(resource/config)
Engine.connectEcu(profile)
Engine.disconnectEcu()

Engine.runtimeSnapshotBuffer()
Engine.pollEvents()

Engine.startLogging(config)
Engine.stopLogging()

Engine.openMeasurement(file/resource)
Engine.queryAnalysis(query)
Engine.queryPlot(viewport)

Engine.readTune()
Engine.previewTuneEdit(request)
Engine.writeTune(request/plan identity)
Engine.burnTune(request/plan identity)

Engine.publishExternalSamples(batch)   # GPS/ESP32/etc when needed
```

A normal UI must never need APIs such as:

```text
getRpm()
getMap()
getAfr()
getChannelValue(name)
```

when that would create repeated JNI crossings.

## Runtime snapshot

Rust maintains full-rate runtime/channel truth. Kotlin reads a bounded display snapshot at frame/UI cadence.

A snapshot ABI should contain:

- ABI/schema version;
- sequence/revision;
- monotonic timestamp;
- source/session identity or compact validity flags;
- fixed/indexed values needed by the active UI;
- connection/quality flags required for presentation.

Preferred direction: direct/packed buffer rather than JSON/object graphs.

Double/triple-buffer publication should be evaluated so Rust can publish a complete snapshot and atomically advance a sequence/index while Compose reads the previous/current stable buffer.

## Plot/analysis results

Kotlin sends semantic query information:

- file/source identity;
- channel IDs;
- time range;
- viewport width/resolution;
- requested analysis operation.

Rust returns only the bounded result needed for the UI, e.g. a few thousand prepared points per visible series rather than millions of raw samples.

## External Android-source ingestion

ESP32/GPS originate from Android APIs and therefore begin in Kotlin.

When their values need logging/analysis/common-channel participation, Kotlin sends a **batch** of typed external-source updates into Rust. One JNI call per GPS sample or accessory state transition is acceptable at their low rates, but variable bursts should still be batched where natural.

Accessory command transmission itself does not pass through Rust:

```text
Compose → ActionRouter → Esp32AccessoryGateway → Android BLE → ESP32
```

## USB resource handoff

Phase 1/early PoC must compare:

1. Kotlin USB I/O + Kotlin decode reference;
2. Kotlin USB I/O + batched Rust decode;
3. Kotlin opens `UsbDeviceConnection` → native FD/wrapped resource → Rust I/O/decode.

The chosen model must prove:

- sustained throughput;
- no sample loss at supported rate;
- reconnect/unplug/replug correctness;
- Activity/process lifecycle correctness;
- descriptor/resource lifetime ownership;
- no double-close/dangling FD;
- acceptable CPU/latency on reference hardware.

Architecture preference alone does not authorize the native-FD path.

## Error handling

No Rust panic may unwind across JNI.

FFI operations return stable status/error data that maps to `NativeEngineError`.

Engine errors should include:

- stable category/code;
- operation/session correlation where useful;
- bounded diagnostic metadata;
- whether state is safe/recoverable/uncertain.

## Events vs callbacks

Avoid many Rust→Kotlin callback interfaces.

Preferred model:

- Kotlin invokes coarse commands;
- current state is sampled from snapshots;
- one bounded event drain surfaces one-time events/progress/errors.

A specialized callback is justified only if Android integration cannot reasonably use this model.

## ABI versioning

All packed/shared structures must contain explicit ABI/schema versions and fixed endian/layout rules. Kotlin validates the version before reading.

A release must never silently reinterpret an old/native buffer layout.

## Performance metrics

The boundary should expose counters sufficient to detect architectural regression, including where practical:

- JNI calls/sec by category;
- bytes crossing JNI/sec;
- snapshot publication/read rate;
- plot result bytes/query latency;
- external-source batch rate;
- native queue depth/drop counts.

The objective is not zero JNI calls. The objective is eliminating high-frequency semantic ping-pong and redundant translation/copying.
