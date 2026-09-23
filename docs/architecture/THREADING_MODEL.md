# EpicHub Threading Model

## Purpose

EpicHub keeps UI work, acquisition, logging, diagnostics and heavy analysis independent so one subsystem cannot stall another. Threading is explicit and bounded; asynchronous work is not introduced merely because a framework supports it.

This is the current Phase-2 threading contract. Exact executors/tasks/runtimes remain implementation details selected by measurement, but ownership and non-blocking boundaries below are authoritative.

## Android main thread

Owns:

- Compose rendering/state application;
- Android UI interaction;
- framework callbacks that require main-thread handling;
- lightweight routing to platform/native owners.

Must not perform:

- blocking USB reads/writes;
- measurement-file serialization/compression;
- full INI parsing;
- complete TuneSnapshot I/O;
- large-log analysis;
- large data copies/conversions;
- diagnostic archive generation.

## Kotlin platform/background work

Android USB/BLE/location/storage APIs may impose their own callback/handler/executor behavior. Platform adapters normalize those events without leaking framework threading models into domain/UI code.

BLE writes remain serialized according to Android GATT requirements. Reconnect timers/policies must not block UI.

Diagnostic capture assembly/export is background work. Event/metric recording remains cheap and bounded on the emitting path.

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
- runtime output decode using the current exact profile;
- conversion into typed sample/channel updates;
- derived-channel evaluation required at acquisition rate;
- publication to ChannelStore;
- fan-out of immutable/batched observations to logger/runtime-snapshot consumers.

This path must not wait for Compose rendering, diagnostics export or file-analysis work.

## Rust logging writer

The logging worker is format-neutral at its input boundary.

Responsibilities:

- consume bounded sample/event batches;
- pass batches to the selected `MeasurementWriter` implementation;
- perform/coordinate sequential storage writes through that writer;
- surface writer checkpoint/finalization/recovery state;
- expose coarse status/metrics.

Format-specific responsibilities remain inside the selected writer:

- MLG record/header/finalization mechanics in the MLG interoperability writer;
- MDF block/compression/index/recovery mechanics in the MDF implementation when MDF4 is introduced.

The locked implementation order remains live path first → MLG → MDF4 evaluation/implementation. The threading model must not make MDF a prerequisite for recording.

The writer never depends on UI frame delivery. A slow writer produces explicit pressure/drop/failure metrics rather than permit unbounded RAM growth.

## Rust analysis worker pool

Responsibilities:

- lazy/range reads through `MeasurementStore` / source-specific readers;
- source-specific decompression/decoding where required;
- expression evaluation;
- statistics/search/event detection;
- histogram/scatter/heatmap work;
- plot-resolution/downsampling preparation;
- cache/index construction/rebuild.

MDF4 may become the long-term native large-log source, but analyzer scheduling is not MDF-specific.

Analysis must be independently cancellable/prioritizable so background work cannot starve live acquisition/logging.

## Diagnostics workers

Diagnostics use bounded in-memory rings/metric accumulators on the emitting side.

Rules:

- normal event/metric publication must be non-blocking or bounded;
- Rust diagnostic events cross to Kotlin through coarse bounded drains, never one callback per event;
- capture finalization, CBOR encoding, ZIP assembly, report rendering, hashing and Android document export run away from acquisition/UI hot paths;
- diagnostic backpressure/overflow is observable and never changes the production subsystem's decisions merely to preserve diagnostics.

D-017 and D-023 remain authoritative: diagnostics are observational, and serialization exists only at the explicit capture/export boundary.

## Tuning transaction execution

Tune read/write/Burn operations require exclusive ownership of the ECU protocol session when their protocol sequence demands it.

Normal telemetry polling may be paused intentionally during an exclusive tuning operation. That pause is explicit session state and must suppress false stale warnings while still allowing real disconnect/session failures to surface.

Tune operations run off the Android main thread and provide coarse progress/state events to UI.

## Runtime snapshot publication

ECU/channel update rate is independent of display rate.

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
                  Compose display cadence
```

Do not trigger Compose state mutation for every raw sample.

The accepted foundation already proves a fixed/versioned 96-byte little-endian RuntimeSnapshot ABI v1 over one reusable direct `ByteBuffer` on both reference devices. That is current measured foundation evidence, not a claim that every future production channel projection fits forever into 96 bytes.

Any later snapshot expansion or double/triple-buffer publication must preserve explicit ABI versioning, stable complete-snapshot publication and bounded JNI cost, and is adopted only when actual live-load measurement justifies it.

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
3. lossless logging within the supported rate contract;
4. UI latest-state responsiveness;
5. foreground analyzer interaction;
6. diagnostic export/report generation;
7. speculative/background analysis/cache work.

Exact Android/Linux scheduler priorities must not be altered without measurements and device evidence.

## Shutdown/disconnect

Lifecycle/disconnect has deterministic ownership ordering:

- stop accepting new semantic operations;
- invalidate current session/generation;
- stop/cancel acquisition;
- finish/abort transactions according to uncertainty rules;
- flush/finalize logging if possible without lying about success;
- finalize or mark diagnostic captures according to their own bounded state;
- release native/platform resources once;
- publish final disconnected/error state.

No stale callback may resurrect a superseded session.