# Logging / Playback / Interoperability Evidence

## Status

Phase 1 architecture inventory for live acquisition, recording, playback and external log formats.

Primary current code evidence reviewed:

- EpicDash-JZ `MslLogPlayer.kt` at source baseline `45de2752811edbd66b01441c0cff8311d3e62647`;
- current EpicDash-JZ application/UI logging/playback contracts where they intersect playback and history;
- previously accepted EpicHub project decisions for live-logger-first development, MLG interoperability and later MDF4 evaluation.

This document is format-architecture evidence, not a final MDF/MLG implementation specification.

---

# Locked development sequence

The accepted project sequence remains:

```text
1. optimize/prove live acquisition + channel path
2. add MLG writer for fast TunerStudio/MegaLogViewer interoperability
3. optimize the MLG writer/file-size/I/O path
4. evaluate/implement MDF4 against the same acquisition/channel workload
5. build the larger analyzer on the resulting common measurement API
```

Reasoning:

- the acquisition path can be benchmarked independently from writer complexity;
- MLG is the fastest route to ecosystem-compatible test files and existing MegaLogViewer validation;
- MDF4 can then be compared against exactly the same samples/channel schemas/rates/devices;
- no file format becomes the live runtime data model.

User priorities remain:

1. file size on phones/tablets without expandable storage;
2. read/write efficiency and sustained performance;
3. interoperability and feature breadth after those constraints are satisfied.

---

# Fundamental architecture split

```text
ECU / other sources
      │
      ▼
acquisition / decode / timestamp
      │
      ▼
   ChannelStore
      │
      ├────────► display snapshot
      │
      ├────────► live calculations / diagnostics
      │
      └────────► recording sample batches
                    │
                    ▼
              MeasurementWriter
                 │       │
                 │       ├─ MLG writer
                 │       └─ MDF writer
                 ▼
               file
```

Playback reverses the source side:

```text
MSL / MLG / MDF / CSV reader
          │
          ▼
      ChannelSource
          │
          ├─► playback display snapshot
          └─► analysis query path
```

Live and file-backed sources share channel semantics; file codecs never become global runtime authority.

---

# Existing MSL player: useful implementation evidence

`MslLogPlayer.kt` is already written with several mobile-performance-conscious choices.

Observed strengths:

- parses on a dedicated single worker thread rather than the Android main thread;
- uses a generation token so an obsolete load can be abandoned when the user selects/stops another file;
- uses a 1 MiB `BufferedReader` buffer for large text files;
- scans each tab-separated row while only materializing a small selected subset of fields;
- stores time in `DoubleArray` and selected channels column-wise in `FloatArray` rather than retaining row objects/strings;
- grows primitive arrays geometrically rather than per-row object allocation;
- rejects non-monotonic/bad time samples;
- uses binary search to find the current playback row;
- skips repeated sample emission when the row index did not change;
- separates playback sample cadence (~20 ms) from position UI cadence (~100 ms);
- uses monotonic `elapsedRealtimeNanos()` for playback anchoring rather than wall clock;
- exposes pause/speed/loop/seek/row-step behavior.

These are worth preserving as **techniques**, not class/API shape.

---

# Existing MSL player: limitations not to inherit

The current player is Dashboard-oriented rather than a general log engine.

Observed limitations:

- hardcoded small `CHANNELS` list;
- hand-maintained aliases such as RPM/MAP/AFR/TPS/etc.;
- channels outside that list are scanned past but not retained for analysis;
- selected channel values are returned as `JSONObject`;
- playback callbacks are Activity/listener-oriented;
- entire selected-column log is held in memory after parse;
- no lazy block/range reader or multiresolution index;
- MSL-specific column matching is mixed with Dashboard semantic naming;
- no common reader/query interface shared with future MDF/MLG;
- no direct large-file analyzer path.

Therefore MSL should become an **interop reader**, not the architecture template for the native logger/analyzer.

---

# MSL clean-slate responsibility split

Suggested permanent responsibilities:

```text
epichub-interop/
├─ msl_reader.rs
├─ msl_schema.rs
└─ msl_channel_match.rs   # only compatibility/name resolution rules
```

`msl_reader.rs` should expose the generic measurement/channel source contract rather than Dashboard JSON.

Important design options:

- for ordinary playback, parse only requested channels where possible;
- for analysis/import, expose complete discoverable channel metadata rather than one hardcoded Dashboard set;
- preserve original channel names/units/source metadata and map aliases only as an optional semantic convenience layer;
- for very large MSL text logs, consider an index/cache/import-to-native format instead of requiring all requested data resident forever.

MSL remains inherently text-heavy and is not the target native high-rate recording format.

---

# Live acquisition is format-neutral

The first performance milestone should exercise:

```text
transport
→ protocol framing/CRC
→ runtime decode
→ timestamp/sequence
→ bounded sample-frame/ring path
→ ChannelStore
→ optional recorder sink
```

without requiring a final file writer.

The live benchmark should measure at least:

- source frame/sample rate;
- payload bytes/sec;
- acquisition/decode CPU;
- allocation/GC where relevant;
- queue depth/high-water mark;
- dropped/coalesced samples;
- timestamp jitter;
- UI snapshot cadence and JNI cost;
- engine/thread contention;
- thermal behavior over sustained sessions.

UI is not in the recorder critical path.

---

# Frame / batch model

Do not make the writer pull one channel at a time from UI-style state.

Conceptual acquisition unit:

```text
SampleFrame
├─ source/session sequence
├─ monotonic acquisition timestamp
├─ optional ECU/source timestamp
├─ validity/loss metadata
└─ typed channel values / source block reference
```

Writer-facing unit should be batched:

```text
SampleBatch
├─ schema/source identity
├─ contiguous time range
├─ N frames or column chunks
└─ loss/event metadata
```

Exact internal layout should be chosen by measurement, but it must avoid object-per-value architecture.

---

# Backpressure / loss policy

The logger must never allow slow file/UI work to silently stall the ECU protocol owner indefinitely.

Use bounded queues/rings and explicit counters.

Possible source-policy distinction:

- live telemetry may allow bounded drop/coalescing under exceptional overload, with sequence/loss evidence;
- tune/configuration transactions are reliable/ordered and are **not** handled by this droppable logging queue;
- recorder writer failure stops/flags recording rather than pretending a complete log exists.

No data-loss policy is hidden.

---

# MLG role

MLG is an interoperability/transition writer and reader, not EpicHub's internal runtime model.

Why it comes first after live acquisition:

- quickest validation with existing TunerStudio/MegaLogViewer workflow;
- known ECU/tuning community format;
- existing logs provide a test corpus;
- lets writer mechanics be optimized before MDF complexity;
- provides a direct size/performance comparison baseline for MDF4.

Architecture rule:

```text
ChannelStore/SampleBatch
      │
      ▼
   MlgWriter
```

Never:

```text
ECU -> MLG-shaped objects -> Dashboard/Analyzer
```

The writer should own buffered sequential binary output and format metadata/record encoding. It should not own transport acquisition or channel truth.

---

# MLG implementation evidence still required

Before a production writer is accepted, build a format corpus and executable tests for:

- valid header/signature/version examples;
- channel schema/metadata representation;
- record timestamp/order semantics;
- numeric type/scale representation;
- record/block boundaries;
- EOF/finalization behavior;
- incomplete/truncated file behavior;
- compatibility with MegaLogViewer/TunerStudio readers;
- large-file size/read/write performance.

Existing project experience with malformed/repeated-block `.mlg` data reinforces that a file having an `.mlg` extension or fixed-size blocks is not enough; validation must prove the real format structure.

Do not reverse-engineer a production writer from one log file.

---

# MDF4 role

MDF4 is the intended native long-term measurement format candidate because it offers:

- structured channel metadata;
- partial/lazy access to large datasets;
- standardized measurement organization;
- column-oriented features in newer MDF4 versions;
- standard compression mechanisms;
- a path to multi-GB analyzer workloads without inventing a proprietary log ecosystem.

It remains behind EpicHub's own `MeasurementStore`/format abstraction.

No third-party Rust MDF crate becomes architectural authority by itself.

---

# MDF version/compression candidates

Current investigation direction remains:

- MDF 4.2 + Deflate: broad compatibility baseline;
- MDF 4.3 + LZ4: live write/read throughput candidate;
- MDF 4.3 + Zstd: storage/archive candidate.

Do not lock 4.3/LZ4/Zstd until the actual target reader ecosystem and Android-device benchmarks validate them.

For live acquisition, row/record-oriented buffering may minimize source-side reordering. For analysis, column-oriented storage/indexing may improve per-channel reads. The writer design can bridge these concerns internally rather than forcing acquisition to be column-shaped from the first byte.

---

# MDF interoperability authority

Validation should use independent implementations/corpora, for example an established independent MDF reader such as Python `asammdf` during development qualification.

Required cross-checks:

```text
EpicHub writes MDF
→ independent reader opens
→ channel names/types/units/count/time/value equality

Independent tool/corpus writes MDF
→ EpicHub reader opens
→ same semantic equality checks
```

Compression/version cases are tested independently.

---

# Measurement format abstraction

Conceptual Rust contract:

```text
trait MeasurementStore {
    channels(...)
    read_range(...)
    read_events(...)
}

trait MeasurementWriter {
    begin(schema, metadata)
    write_batch(batch)
    write_event(event)
    checkpoint_if_supported(...)
    finalize(...)
}
```

The exact trait shape remains Phase 2 design. Important Phase 1 rule: readers/writers are replaceable format implementations behind a common measurement model.

---

# Playback architecture

Playback should not manually push a JSON object into Dashboard every 20 ms.

Preferred model:

```text
PlaybackClock
    │
    ▼
ChannelSource / MeasurementStore
    │
    ├─ latest display projection
    ├─ graph/query requests
    └─ analyzer access
```

Playback time state is separate from measurement data:

```text
PlaybackState
├─ source/file identity
├─ current position
├─ speed
├─ paused/running
├─ loop/range
└─ generation/cancel token
```

Seeking changes the query cursor, not the underlying log.

A source switch creates a new source/session identity so stale playback/live updates cannot contaminate the active UI.

---

# Live versus playback source authority

Use explicit source/session identity such as:

- `LiveEcu`;
- `MslFile`;
- `MlgFile`;
- `MdfFile`;
- `CsvFile`;
- demo/self-test where retained.

A display snapshot reports source/session/revision/validity. It cannot silently switch from live to playback under the same session identity.

This carries forward the good stale-session/revision lesson from prior UI state work while moving the actual authority into Rust/native channel state.

---

# Large-log analyzer direction

Do not require full file materialization to open a large log.

Target path:

```text
open file
→ parse metadata/block tree
→ load/build time/channel index
→ request channel + time viewport
→ decode/decompress only needed blocks
→ calculations/filtering
→ min/max downsample
→ viewport-sized plot packet
```

A disposable sidecar such as:

```text
session.mf4
session.mf4.idx
```

may hold rebuildable acceleration:

- channel directory;
- time→block offsets;
- min/max resolution pyramid;
- event locations;
- expression/cache metadata.

The sidecar is never measurement authority and can be deleted/rebuilt.

---

# Plot data reduction

For time-series display, prefer spike-preserving min/max envelopes/pyramids over simple averaging.

Conceptually:

```text
L0 native
L1 2-sample buckets
L2 4-sample buckets
L3 8-sample buckets
...
```

Each bucket can retain min/max and optionally first/last as needed.

Choose a level that returns roughly screen-scale data (for example a few thousand points per requested series), then return native values at close zoom.

This keeps plot cost proportional to viewport width rather than total file size.

---

# File-size/performance benchmark matrix

Every candidate writer should be tested on identical acquisition data and device/storage conditions.

Measure at least:

- bytes/sample and final bytes/minute/hour;
- write throughput;
- CPU time;
- allocations/memory high-water mark;
- thermal/battery effect;
- dropped samples;
- finalize latency;
- power-loss/truncated-file recoverability;
- reopen latency;
- random time-range/channel query latency;
- graph viewport latency;
- independent-tool interoperability.

Compression decisions should be based on **size saved per CPU/thermal cost** on target Android hardware.

---

# Writer crash / power-loss safety

The recording subsystem should separate:

- active recording identity;
- main measurement file;
- small recovery marker/journal if required;
- periodically completed/checkpointed format structures where the format permits it.

A writer should never claim a clean finalized file if finalization failed.

Recovery can attempt to reconstruct/finalize a partially written file, but recovery metadata itself is not measurement truth.

This mirrors the broader project principle already proven in tuning persistence: small journals tell us what operation may have been in progress; they do not fabricate the missing authoritative data.

---

# Android storage boundary

Normal high-rate recording should target app-controlled file access with predictable sequential I/O. User-selected export/share remains a separate Kotlin Android document operation.

Avoid putting Storage Access Framework callbacks into the hot writer loop.

The exact internal/external directory policy is locked with Android storage/product requirements before Phase 2.

---

# Threading ownership

Preferred high-level model:

```text
ECU acquisition worker
      │
      ▼
decode/channel publish
      │
      ├─► latest display snapshot
      └─► bounded recorder queue
               │
               ▼
          writer worker

file analysis workers
      │
      └─► independent from UI/main thread
```

The writer never waits for Compose. Compose never waits for disk writes.

Avoid introducing async/task machinery into every primitive operation; explicit long-lived workers and bounded queues are often easier to reason about for a sustained telemetry pipeline.

---

# Phase 1 responsibility conclusions

The current evidence is sufficient to lock these architecture rules:

1. **Live acquisition/channel authority is format-neutral.**
2. **Writer work begins only after the live path is proven/measured.**
3. **MLG is the first interoperability writer, not the internal model.**
4. **MDF4 remains the long-term native-format candidate and is compared against the same workload.**
5. **File size is the first mobile writer decision metric; I/O/read/write efficiency is next.**
6. **MSL/CSV/MLG are isolated interoperability adapters.**
7. **Playback and live data share channel/source semantics; playback clock is separate state.**
8. **No JSON/per-channel JNI hot path in the clean implementation.**
9. **Large-file analysis is lazy/range-based with rebuildable indexes/caches.**
10. **Plot downsampling preserves spikes and is viewport-driven.**
11. **Writer/UI/analyzer threads are independent and communicate through bounded data contracts.**
12. **Recovery/journal metadata never fabricates measurement data.**

Remaining gaps are concrete MLG format fixtures, MDF interoperability/version/compression benchmarks, storage policy and implementation proof—not basic ownership ambiguity.