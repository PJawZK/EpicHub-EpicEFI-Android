# EpicHub Threading Model

## Purpose

EpicHub should keep UI work, acquisition, logging and heavy analysis independent so one subsystem cannot stall another. Threading is explicit and bounded; asynchronous work is not introduced merely because a framework supports it.

This is a Phase 1 target model. Exact executors/runtimes are finalized with implementation benchmarks.

## Android main thread

Owns:

- Compose rendering/state application;
- Android UI interaction;
- framework callbacks that require main-thread handling;
- lightweight routing to platform/native owners.

Must not perform:

- blocking USB reads/writes;
- MDF serialization/compression;
- full INI parsing;
- complete TuneSnapshot I/O;
- large-log analysis;
- large data copies/conversions.

## Kotlin platform/background work

Android USB/BLE/location APIs may impose their own callback/handler/executor behavior. Platform adapters should normalize those events without leaking their threading models into domain/UI code.

BLE writes remain serialized according to Android GATT requirements. Reconnect timers/policies should not block UI.

## Rust acquisition thread/task

Intended responsibilities when Rust owns runtime ECU I/O:

- blocking/native USB read/write;
- frame acquisition;
- monotonic receive timestamp capture;
- transport error detection;
- feeding a bounded decode/processing path.

If Kotlin remains the selected USB I/O owner after benchmarking, the same architectural separation still applies: Kotlin acquisition batches data across JNI rather than invoking Rust per byte/sample/channel.

## Rust decode/runtime processing

Responsibilities:

- protocol frame validation/response parsing;
- runtime output decode using current exact profile;
- conversion into typed sample/channel updates;
- derived-channel evaluation required at acquisition rate;
- publication to ChannelStore;
- fan-out of immutable/batched observations to logger/runtime snapshot consumers.

This path must not wait for Compose rendering.

## Rust logging writer

Responsibilities:

- consume bounded sample/event batches;
- build MDF blocks;
- compression where enabled;
- sequential storage writes;
- periodic recovery/finalization markers as required;
- expose coarse status/metrics.

The writer must never depend on UI frame delivery.

A slow writer must produce explicit pressure/drop/failure metrics rather than permit unbounded RAM growth.

## Rust analysis worker pool

Responsibilities:

- MDF lazy reads/decompression;
- expression evaluation;
- statistics/search/event detection;
- histogram/scatter/heatmap work;
- plot-resolution/downsampling preparation;
- cache construction/rebuild.

Analysis must be independently cancellable/prioritizable so background work cannot starve live acquisition/logging.

## Tuning transaction execution

Tune read/write/Burn operations require exclusive ownership of the ECU protocol session when their protocol sequence demands it.

Normal telemetry polling may be paused intentionally during an exclusive tuning operation. That pause is explicit session state and must suppress false stale warnings while still allowing real disconnect/session failures to surface.

Tune operations run off the Android main thread and provide coarse progress/state events to UI.

## Runtime snapshot publication

ECU/channel update rate is independent of display rate.

Example:

```text
ECU / source updates: potentially hundreds of Hz
        │
        ▼
Rust ChannelStore
        │
        ├─► Logger/analysis at required data rate
        │
        └─► latest immutable RuntimeSnapshot
                         │
                         ▼
                  Compose ~display cadence
```

Do not trigger Compose state mutation for every raw sample.

Target implementation should investigate double/triple-buffered packed snapshots with atomic sequence/version publication so the producer never blocks on UI reading.

## Bounded communication

Cross-thread queues/ring buffers must be bounded or otherwise have an explicit maximum-memory contract.

Each queue defines:

- producer;
- consumer;
- capacity;
- ordering requirements;
- backpressure/drop policy;
- diagnostics counters;
- shutdown behavior.

Dropping must never be silent. Safety/tuning command paths generally require fail/abort rather than drop/coalesce semantics.

## Priority principle

When simultaneous work competes for resources, intended priority is broadly:

1. ECU/session correctness and safety-critical transaction integrity;
2. live acquisition needed for active logging/runtime truth;
3. lossless logging within supported rate contract;
4. UI latest-state responsiveness;
5. foreground analyzer interaction;
6. speculative/background analysis/cache work.

Exact Android/Linux scheduler priorities should not be altered without measurements and device evidence.

## Shutdown/disconnect

Lifecycle/disconnect must have deterministic ownership ordering:

- stop accepting new semantic operations;
- invalidate current session/generation;
- stop/cancel acquisition;
- finish/abort transactions according to uncertainty rules;
- flush/finalize logging if possible without lying about success;
- release native/platform resources once;
- publish final disconnected/error state.

No stale callback may resurrect a superseded session.
