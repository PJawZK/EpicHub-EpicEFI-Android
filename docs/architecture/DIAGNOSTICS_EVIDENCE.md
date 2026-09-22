# Diagnostics / Performance Evidence

## Status

Phase 1 source archaeology against the mature EpicDash-JZ diagnostic/performance instrumentation.

Primary reviewed sources:

- `DiagnosticStore.kt`;
- `DiagnosticReportFormatter.kt`;
- `LifecycleDiagnostics.kt`;
- `PerformanceMetrics.kt`;
- `RollingPerformanceMetricSeries.kt`;
- subsystem-specific USB/BLE/tuning diagnostics already inventoried in their respective Phase 1 tranches.

The clean app should preserve diagnostic discipline and evidence quality, not its JSON/SharedPreferences implementation shape.

---

# Primary rule: diagnostics observe, domains decide

`LifecycleDiagnostics` explicitly states that it is observational only and never starts/stops/reconnects/selects/invalidates transports, managers, Activities or sessions.

That principle becomes global:

```text
production domain
   ├─ owns state/decision/action
   └─ emits structured diagnostic facts

Diagnostics
   ├─ record/summarize/export
   └─ never become hidden control authority
```

A protective domain policy may deliberately consume a validated health fact, but the policy belongs to the domain and is tested as production behavior. Instrumentation does not casually mutate behavior to make diagnostics convenient.

---

# Diagnostic event model

The old diagnostic store already contains useful concepts:

- bounded event history;
- session IDs;
- categories;
- timestamps;
- bounded message sizes;
- warning incidents distinct from ordinary events;
- warning acknowledgment state;
- incident grouping/repeat counts within a time window;
- source identity captured with warning values;
- a monotonic revision for cheap snapshot caching.

The clean model should formalize these as typed data instead of arbitrary category/message JSON.

Conceptual event:

```text
DiagnosticEvent
├─ eventId / sequence
├─ wallTime
├─ monotonicTime
├─ sessionId
├─ correlationId?          # transaction/request/action when available
├─ domain                  # UsbPlatform, EcuProtocol, Tuning, Ble, Logger, ...
├─ severity                # Trace/Info/Warning/Error/Fatal or bounded equivalent
├─ code                    # stable machine-readable identifier
├─ summary/message         # bounded human-readable context
└─ typed/bounded fields    # values needed to diagnose this event
```

Stable `code` + typed fields are preferable to trying to parse prose later.

---

# Domain-local diagnostics, common schema

The clean app should not centralize all instrumentation into one giant manager.

Examples:

## Kotlin Android/platform events

- USB candidate/permission/open/claim/control-transfer;
- BLE scan/connect/discover/MTU/retry/GATT operation;
- lifecycle/foreground-service/process exit;
- Android storage/export/import;
- frame/jank/resource metrics.

## Rust engine events

- ECU protocol sync/request/status/CRC/timeouts;
- runtime decode/rate/loss;
- tuning Read/Write/Burn transactions and uncertainty;
- logger queue/writer/checkpoint/recovery;
- MDF reader/writer/index/compression;
- analyzer query/cache/downsample;
- FFI snapshot/result transfer metrics.

Each owner emits facts through a common event/metric contract. Diagnostics never recreates subsystem state from message strings when a typed snapshot already exists.

---

# Lifecycle/owner tracing

`LifecycleTrace` demonstrates useful ownership debugging:

- explicit owner registration;
- generation-numbered owner IDs;
- parent/child relationship;
- create/state/destroy events;
- active/inactive state;
- late-event counting after destruction;
- repeated-state-event suppression;
- bounded owners/events;
- both wall and monotonic timestamps.

These techniques are valuable during lifecycle/reconnect development.

## Clean consequence

Keep owner/session/generation tracing available, particularly for:

- Android Activity/service instances;
- USB connection attempts/resources;
- BLE GATT generations;
- Rust engine handles;
- ECU sessions;
- logging sessions;
- analyzer file/query lifetimes.

Do not require every small value object to become a diagnostic owner. Ownership traces are for lifecycle-bearing resources/components.

---

# Performance metrics

The old metrics system provides:

- bounded rolling windows;
- lifetime count/average/max independent of rolling-window eviction;
- p50/p95;
- counters;
- cached snapshots invalidated by mutation revision;
- thread-safe writers/readers.

This is useful architecture evidence.

## Clean metric model

Avoid metric meaning encoded only by arbitrary string names plus `unitFor(name)` conditionals.

Prefer typed/registered metric descriptors:

```text
MetricDescriptor
├─ stable id
├─ unit
├─ kind: Counter | Gauge | Duration | Bytes | Rate | Depth | Ratio
└─ domain
```

Rolling summaries may include:

- current/window count;
- p50;
- p95;
- p99 where latency/jank tails matter;
- maximum;
- average;
- lifetime count/bytes as appropriate.

Do not compute percentiles in high-frequency hot paths. Record cheap samples/counters; summarize out of band or on bounded windows.

---

# Metrics required by the new architecture

At minimum, qualification/debug builds should be capable of observing:

## Android/Compose

- startup to first frame;
- startup/attach to first valid ECU snapshot;
- frame time p50/p95/p99/max;
- jank count/rate;
- Compose/custom draw timing where needed;
- UI snapshot receive/render cadence;
- memory/PSS/RSS where available;
- process/thermal state where reliable.

## FFI

- JNI calls/sec by coarse operation class;
- bytes/sec Kotlin↔Rust;
- snapshot packet size;
- plot/result packet size;
- FFI call latency p50/p95/p99 where material.

## ECU

- frames/requests/sec;
- payload bytes/sec;
- read/write latency;
- CRC/status/timeouts;
- reconnect count/latency;
- decode timing;
- dropped/coalesced runtime samples where policy permits it.

## BLE bridge

- scan/connect/reconnect counts/latency;
- one global GATT operation queue depth/high-water mark;
- operation latency/errors;
- request timeout/partial-batch counts;
- malformed payload counts.

## Logger

- samples/batches in;
- samples/batches written/dropped;
- queue depth/high-water mark;
- bytes/sec and final file growth rate;
- compression ratio/time;
- flush/checkpoint/finalize time;
- writer stalls/errors;
- recovery events.

## Analyzer

- file-open metadata/index latency;
- block read/decompression time;
- cache hit/miss;
- range-query latency;
- expression/filter/downsample time;
- plot points returned;
- memory/cache high-water mark.

## Tuning

- snapshot read duration/bytes;
- semantic write-plan size;
- baseline reread time;
- per-write ACK/readback latency;
- complete verification time;
- Burn request/completion time;
- uncertainty/recovery incidents.

---

# Warning/incident history versus raw diagnostics

The old app keeps a user-facing warning incident history distinct from general events and groups repeats.

Keep that conceptual distinction:

```text
DiagnosticEvent stream
    = engineering/system evidence

UserIncident / Warning history
    = bounded product-facing alert occurrence/acknowledgment history
```

A warning rule can reference channel values/source/session at trigger time, but warning acknowledgment does not modify channel truth or the rule's underlying condition.

Repeated identical warnings may be grouped for presentation while preserving first/last timestamp and repeat count.

---

# Persistent versus process-local diagnostics

Not every event needs disk persistence.

Suggested categories:

## Process-local high-volume

- detailed protocol traces;
- render timings;
- queue depth samples;
- analyzer query timings.

Keep bounded in memory and include recent context in export.

## Persistent bounded incidents

- prior crash/process-exit summary;
- tuning uncertainty/recovery required;
- logger unrecovered file/session;
- repeated user warnings where product history is useful;
- high-level reconnect/fatal transport incidents if needed.

Do not persist every runtime sample/trace as diagnostic JSON. Measurement data belongs in measurement logs.

---

# Diagnostic report assembly

The old report formatter separates sections for:

- app/device/Android identity;
- BLE/session;
- USB ECU transport;
- lifecycle owner trace;
- dashboard/source;
- recent events;
- warning history.

The clean report should preserve the **sectioned domain snapshot** approach while removing JSON-string-in-JSON coupling.

Suggested report input:

```text
DiagnosticReportSnapshot
├─ generatedAt
├─ app/build/runtime identity
├─ Android/device capabilities
├─ current session/source identity
├─ platform snapshots
├─ engine snapshots
├─ recent structured events
├─ current metrics summaries
├─ recovery/uncertainty state
└─ user warning incidents
```

Formatter/export code consumes immutable typed snapshots. It does not call mutable production managers while formatting.

Kotlin owns Android user export/file picker. Domain snapshots originate from their actual owners.

---

# Sensitive / oversized data boundary

Diagnostic exports should be useful without becoming hidden full dumps of huge logs/tunes or personal data.

Rules:

- bound message/field sizes;
- include hashes/identity summaries where full artifacts are unnecessary;
- do not embed full measurement logs in diagnostic reports;
- do not embed complete tune/profile files by default;
- location history is measurement/user data, not routine diagnostic text;
- secrets/signing credentials/tokens never enter events/reports.

If a full artifact is needed for diagnosis, export it explicitly as a separate user-selected artifact.

---

# Correlation IDs

A common weakness of free-form logs is reconstructing which events belong to one operation.

Use correlation IDs for:

- USB connection attempt;
- BLE GATT generation/request batch;
- tune Read/Write/Burn transaction;
- logger recording session;
- MDF recovery;
- analyzer query;
- user semantic action where useful.

A single operation can then be followed across Kotlin/Rust layers without sharing ownership.

---

# Clock model

Where useful, events/metrics carry both:

- wall-clock time for human reports/cross-process chronology;
- monotonic elapsed time for durations/order inside a process/session.

Duration calculations never depend on wall-clock adjustments.

---

# Diagnostics UI

Compose Diagnostics is a projection, not the storage/control authority.

Useful panels can include:

- connection/session summary;
- ECU protocol health;
- BLE bridge health;
- logger/storage health;
- tuning authority/uncertainty;
- performance/jank/queue metrics;
- bounded recent events/warnings;
- export actions.

Buttons such as reconnect/rescan/clear history are **semantic actions to the owning subsystem**. The diagnostic store itself does not execute the control operation merely because the button is displayed there.

---

# Test/qualification use

Diagnostics must be designed to support repeatable qualification, not only ad-hoc troubleshooting.

A test run can mark phases/events and compare before/after metric snapshots.

Important regression metrics can be machine-read from a stable schema rather than scraping human text.

The clean project should preserve the existing discipline of baseline collection/comparison scripts while moving the underlying metrics to the new structured schema.

---

# Phase 1 responsibility conclusions

1. **Diagnostics are observational; production domains remain decision authorities.**
2. **Use one shared event/metric schema, but let each domain emit its own facts.**
3. **Lifecycle generation/late-event tracing is worth preserving for ownership-bearing resources.**
4. **Metrics need stable IDs, explicit units/kinds and bounded rolling summaries.**
5. **Tail latency matters: p95/p99/max are more useful than average alone for UI/transport/analyzer responsiveness.**
6. **Warnings/incidents are product-facing history distinct from raw diagnostic events.**
7. **High-volume traces are bounded/process-local; only selected incidents need persistent storage.**
8. **Diagnostic reports assemble immutable domain snapshots and recent events; they do not own live managers.**
9. **Use correlation IDs across Kotlin/Rust boundaries rather than merging ownership.**
10. **Measurement/tune/profile artifacts remain separate from routine diagnostic text exports.**
11. **Diagnostic UI controls route semantic actions back to subsystem owners.**
12. **Qualification tooling should consume stable structured metrics/events instead of prose scraping where possible.**

Remaining work is final event/metric schema/API design and persistence/export format choices, not responsibility ownership.