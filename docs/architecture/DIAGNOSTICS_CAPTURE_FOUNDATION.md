# EpicHub Diagnostics Capture Foundation

## Purpose

This document defines the practical diagnostic/bug-capture system that EpicHub should establish **before** real ECU, logger, tuner and analyzer complexity is added.

The goal is to make future bug hunting cheap, reproducible and evidence-driven from the beginning.

Diagnostics remain observational under D-017. The capture system records what happened; subsystem owners still decide connection, tuning, logging and recovery behavior.

D-023 / `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` also applies throughout this document: live diagnostic state is typed Kotlin/Rust state, Rust→Kotlin diagnostic transfer uses bounded typed/packed batches, and serialization exists only at the explicit capture persistence/export boundary.

---

# 1. Design goals

The diagnostic foundation must make it easy to answer:

```text
What happened?
When did it happen?
Which subsystem/session/generation owned it?
What was the app/device/runtime state?
What happened immediately before and after it?
Can the same issue be reproduced and compared later?
```

It should work for:

- Android lifecycle/UI bugs;
- JNI/native engine problems;
- USB/session/reconnect failures;
- protocol/timeouts/CRC/status errors;
- BLE/GATT generation bugs;
- logger stalls/drops/storage failures;
- Tuner read/write/Burn verification issues;
- analyzer query/cancellation problems;
- performance/jank/memory regressions;
- unexpected process exits/crashes.

---

# 2. Always-on bounded diagnostic flight recorder

EpicHub should maintain a small always-on in-memory diagnostic flight recorder.

It is **not** a hidden measurement logger.

The recorder contains bounded recent diagnostic evidence only:

```text
recent structured events
recent metric samples/summaries
lifecycle/owner transitions
session/generation changes
current/periodic subsystem health snapshots
UI route/window/orientation breadcrumbs
user diagnostic markers
```

Properties:

- fixed memory bounds;
- O(1) or bounded append behavior;
- no blocking disk writes on hot runtime paths;
- old entries overwrite/evict deterministically;
- every record has monotonic ordering information;
- domain state remains authoritative elsewhere.

The initial retention window should be chosen by measured memory/overhead rather than arbitrary history length. A practical starting target may be approximately the last 60–120 seconds of low-volume diagnostic context, but exact capacities are implementation/benchmark decisions.

---

# 3. Diagnostic event schema

Every event uses a stable typed machine-readable structure.

Conceptual shape:

```text
DiagnosticEvent
- sequence
- monotonicTime
- wallTime
- domain
- severity
- code
- sessionId?
- generation?
- correlationId?
- ownerId?
- summary
- typed bounded fields
```

Domains may include:

```text
AppLifecycle
ComposeUi
NativeEngine
Ffi
UsbPlatform
EcuSession
EcuProtocol
Profile
Tuning
BlePlatform
AccessoryBridge
Logger
MeasurementStore
Analyzer
Storage
Diagnostics
```

Stable event codes are preferred over free-form prose.

Example:

```text
USB_DEVICE_ATTACHED
USB_PERMISSION_DENIED
ECU_SESSION_STARTED
ECU_SIGNATURE_MISMATCH
ECU_RESPONSE_CRC_ERROR
RUNTIME_SNAPSHOT_ABI_MISMATCH
LOGGER_QUEUE_HIGH_WATER
ANALYZER_QUERY_CANCELLED
ENGINE_HANDLE_LATE_ACCESS
```

Human-readable summaries remain useful, but tools must not need to parse them to understand the event.

No event is represented internally as a JSON/CBOR document or arbitrary string-key map merely for transport convenience.

---

# 4. Metric registry

Metrics use stable descriptors rather than arbitrary string names.

Conceptual descriptor:

```text
MetricDescriptor
- id
- domain
- unit
- kind
- aggregation policy
```

Metric kinds:

```text
Counter
Gauge
Duration
Rate
Bytes
Depth
Ratio
```

Useful summaries:

- current;
- count;
- average;
- p50;
- p95;
- p99;
- max;
- lifetime totals where meaningful.

High-frequency paths record cheap primitive samples/counters. Expensive percentile/report work happens outside the hot path.

---

# 5. Lifecycle and generation tracing

The diagnostic system should make stale-owner bugs easy to see.

Lifecycle-bearing resources register an owner identity:

```text
ownerId
ownerType
parentOwnerId?
generation
createdAt
currentState
destroyedAt?
lateEventCount
```

Initial owner classes:

- Activity / app process lifecycle;
- `NativeEngine` instance;
- USB connection attempt/resource;
- ECU session;
- BLE/GATT generation;
- logger recording session;
- analyzer query/capture;
- long-running export/import task.

Events arriving after owner destruction are recorded explicitly instead of disappearing into generic logs.

---

# 6. Correlation IDs

Correlation IDs tie one operation together across Kotlin and Rust without merging ownership.

Use them for:

- USB attach/connect attempt;
- signature/profile negotiation;
- runtime acquisition session;
- BLE request batch;
- Logger session;
- Tuner Read;
- Tuner Write;
- Burn;
- Analyzer query;
- diagnostic capture/export;
- selected high-level user semantic actions.

Example timeline:

```text
correlationId = usb-connect-42

Kotlin: USB candidate discovered
Kotlin: permission granted
Kotlin: device opened
Rust: ECU session created
Rust: query sent
Rust: signature accepted
Rust: runtime stream started
```

The report can reconstruct the operation without scraping messages.

---

# 7. Problem-capture modes

EpicHub should support three complementary capture workflows.

## A. Capture recent problem

For a problem that has already happened.

User taps the global capture icon and selects:

```text
Capture recent problem
```

Behavior:

1. snapshot/freeze the current pre-trigger flight-recorder window;
2. add a user trigger marker;
3. continue collecting a short bounded post-trigger window;
4. assemble an immutable capture session;
5. open review/note screen.

This is essential because many bugs are noticed only **after** the wrong behavior occurred.

## B. Reproduction capture

For a bug the user can deliberately reproduce.

```text
Start reproduction capture
→ perform steps
→ Add marker one or more times
→ Stop & review
```

The capture stores explicit user markers with optional short labels such as:

```text
opened tuner
pressed connect
problem appeared
screen froze
wrong value shown
```

A maximum duration/storage bound prevents accidental endless capture.

## C. Previous-exit/crash recovery

On next launch, where Android/platform evidence is available, EpicHub should present a bounded previous-process-exit summary.

Potential evidence:

- last persisted high-level breadcrumbs;
- last known route/session/engine state;
- Android process-exit reason where available;
- caught Kotlin exception information;
- caught Rust panic event/backtrace where configured;
- last lifecycle owner transitions.

Do **not** pretend all native hard crashes can be fully recovered in-process. Preserve the best pre-exit evidence available and use platform exit diagnostics where supported.

---

# 8. CaptureSession model

A finished capture is immutable.

Conceptual structure:

```text
DiagnosticCapture
- captureId
- startedAt
- triggerAt?
- stoppedAt
- captureMode
- appBuildIdentity
- git/source identity where available
- device/Android/ABI identity
- current UI route/window/orientation
- current source/session/profile identity
- subsystem health snapshots
- lifecycle owners
- structured events
- metric summaries
- diagnostic markers
- user note
- expectedBehavior?
- actualBehavior?
- attachment references
```

Capture state is separate from normal live diagnostic rings.

Once finalized, later live events do not mutate the saved capture.

The capture exists as typed Kotlin/Rust data until it is explicitly finalized/persisted/exported.

---

# 9. What a capture should include by default

Default capture context should be useful without becoming enormous.

## App/build

- app version/build;
- Git commit/build fingerprint where available;
- build type;
- architecture ABI;
- feature/schema versions relevant to diagnostics.

## Device/platform

- manufacturer/model;
- Android version/API;
- supported ABIs;
- screen/window size/density/orientation;
- memory/thermal/process state where reliably available.

## Application state

- active route/subpage;
- navigation state identifiers;
- native engine handle/session generation;
- current source type;
- logging active/inactive;
- profile/signature fingerprint summaries;
- pending uncertainty/recovery state;
- current diagnostic capture state.

## Recent engineering evidence

- bounded event history;
- metric summaries;
- lifecycle/owner history;
- queue depths/high-water marks;
- reconnect/retry counters;
- error/status counters.

---

# 10. Measurement/tune data boundary

Routine diagnostic bundles do **not** silently embed:

- complete measurement logs;
- complete TuneSnapshot/project;
- imported profile/INI contents;
- GPS/location history;
- user documents;
- access tokens/secrets/credentials.

When full artifacts are needed, Diagnostics shows them as optional **explicit attachments/references**.

Example:

```text
Diagnostic capture #A42
Attachments available:
[ ] include current measurement log
[ ] include selected tune/project export
[ ] include trigger capture
```

The user explicitly chooses those artifacts.

This keeps routine bug reports small and privacy-bounded.

---

# 11. Bundle format

Diagnostic export contains one canonical machine-readable capture plus one human-readable projection.

Locked versioned archive shape:

```text
EpicHub-Diagnostics-<captureId>.zip
├─ capture.cbor
├─ report.txt
└─ attachments/
```

`capture.cbor` is the canonical versioned diagnostic capture and contains the complete typed capture schema, including schema/build/device/source identity, subsystem snapshots, events, metrics, lifecycle records, markers, user notes and attachment metadata/hashes.

`report.txt` is generated from the same immutable typed capture and is never a competing source of truth.

Only explicitly selected full artifacts are placed in `attachments/`.

CBOR exists only at this persistence/export boundary. Kotlin/Rust diagnostic rings and JNI diagnostic transfer do **not** serialize through CBOR.

JSON/NDJSON is not part of the canonical bundle. If a future developer/external tool needs JSON, it may explicitly convert `capture.cbor` to a derived JSON projection outside the runtime authority path.

---

# 12. Bug fingerprinting / repeat detection

Where practical, repeated incidents can receive a stable diagnostic fingerprint derived from facts such as:

```text
domain + event code + normalized error/status + top stack/call-site identity
```

The fingerprint is for grouping/searching, not proof that two bugs are identical.

Useful UI behavior:

```text
ECU_RESPONSE_CRC_ERROR
fingerprint 7F21...
seen 14 times this session
first 10:42:11
last 10:47:03
```

---

# 13. Diagnostics UI workflow

Diagnostics uses the layout defined in `UI_FOUNDATION_LAYOUT.md`.

## Overview

Health cards link directly to filtered evidence.

Example card:

```text
USB / ECU
Connected
runtime 198.7 req/s
CRC errors: 0
timeouts: 1
reconnects: 2
[Open details]
```

## Events

Filter by:

- domain;
- severity;
- code;
- correlation ID;
- session/generation;
- time range.

## Metrics

Search/group metrics and show current/tail statistics.

## Sessions

Visual ownership tree and generation transitions.

## Captures

Create/review/export/delete diagnostic captures.

---

# 14. Global capture control

The capture affordance is present in the global status strip.

Idle state:

```text
bug icon
→ Capture recent problem
→ Start reproduction capture
```

Active state:

```text
recording indicator + elapsed
→ Add marker
→ Stop & review
```

The action must be reachable without leaving the screen where the bug is happening.

---

# 15. Diagnostic assertions / invariant violations

The clean architecture should actively record broken invariants rather than only visible exceptions.

Examples:

- result arrives for stale session generation;
- lifecycle event arrives after destroyed owner;
- RuntimeSnapshot version/size mismatch;
- queue exceeds expected high-water threshold;
- bounded ring cannot accept according to its configured policy;
- tuning operation baseline identity changes mid-transaction;
- analyzer result belongs to retired request generation;
- writer reports accepted samples but persisted count diverges.

These emit stable diagnostic events and, in development/test builds where safe, may also fail assertions/tests.

The instrumentation still does not take control action itself.

---

# 16. Release versus development diagnostics

The diagnostic foundation exists in normal builds because field bugs happen outside developer builds.

Different verbosity levels are appropriate:

## Always available

- critical structured events;
- lifecycle/session identity;
- bounded metrics;
- problem capture;
- crash/previous-exit summary;
- export.

## Development/qualification verbosity

- detailed protocol traces;
- extra render timing;
- verbose queue snapshots;
- fine-grained JNI measurements;
- additional invariant assertions.

Verbosity changes evidence volume, not production behavior.

---

# 17. Performance/overhead rules

Diagnostics must not become the performance problem it is intended to diagnose.

Rules:

- bounded allocation/storage;
- avoid formatting strings until display/export where practical;
- no filesystem write for every event;
- metric recording stays cheap;
- event export/report generation happens off hot paths;
- high-volume protocol byte dumps are opt-in/development only;
- ring capacities and diagnostic cadence are measurable metrics themselves;
- do not serialize/parse JSON, CBOR or another document format merely to move live events between Kotlin and Rust.

Qualification should measure diagnostics enabled versus minimal verbosity on both SM-A137F and SM-T500.

---

# 18. Initial implementation slice

Before real ECU acquisition is introduced, implement enough diagnostic plumbing to exercise it with the existing synthetic foundation.

Initial slice:

```text
DiagnosticEvent typed schema
MetricDescriptor/registry
bounded Kotlin event ring
bounded Rust event ring
bounded metric summaries
owner/session/generation tracing
coarse typed/packed Rust → Kotlin diagnostic batch drain
Diagnostics Overview/Events/Metrics/Sessions screens
problem-capture session + markers
versioned CBOR capture export + report.txt
```

Use the existing synthetic RuntimeSnapshot/native engine to create test events and lifecycle transitions.

Do not wait for USB/BLE/logger/tuner code to exist before establishing these contracts.

---

# 19. Qualification tests

At minimum test:

- ring wrap/eviction is deterministic;
- memory remains bounded under event storms;
- metric aggregation is deterministic;
- p50/p95/p99 calculations match reference vectors;
- events preserve monotonic ordering;
- Kotlin/Rust correlation IDs survive typed/batched transfer;
- stale generation events remain visible but cannot mutate current domain state;
- capture includes pre-trigger and post-trigger evidence;
- manual markers preserve ordering;
- CBOR schema/version round-trip is deterministic and unsupported versions fail explicitly;
- `report.txt` is generated from the same immutable capture;
- optional tooling conversion, if later added, cannot become capture authority;
- routine export does not contain forbidden full logs/tunes/GPS/secrets;
- activity/process recreation does not duplicate capture ownership;
- capture/export work does not block UI/native acquisition threads;
- diagnostics actions route to owners rather than mutating subsystem state internally;
- architecture tests/review prevent JSON/CBOR runtime-message-bus regression.

Physical qualification should include deliberately induced benign failures, such as synthetic stale-generation events and forced capture-ring wrap, so the user can inspect whether the exported evidence is actually understandable.

---

# 20. Acceptance criteria before real ECU integration

The diagnostics foundation is ready when:

- one user action can capture a recent problem without first navigating away;
- reproduction capture can add markers and finalize an immutable report;
- Android and Rust emit typed events into one correlated view;
- owner/session/generation history is visible;
- core metrics expose current and tail behavior;
- diagnostic capture/export remains bounded under stress;
- the exported `capture.cbor` can reconstruct event order and subsystem identity;
- both reference devices can create and export a capture;
- diagnostics remain observational under tests;
- no runtime JSON/CBOR bridge exists;
- the next USB/ECU slice can add domain events/metrics without inventing a new logging/debug system.

## Summary

The intended result is a practical engineering flight recorder built into the application from the beginning:

> when a future USB, tuner, logger, UI or analyzer bug appears, the first question should be “open the capture” rather than “can we reproduce it again with extra debug logging added?”