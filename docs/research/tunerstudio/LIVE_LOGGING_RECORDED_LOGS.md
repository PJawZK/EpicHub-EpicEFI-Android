# TunerStudio live logging and recorded-log behavioral reference

## Scope

This slice investigates TunerStudio as a mature reference for live data logging and recorded-log behavior, focusing on:

- log schema and field selection;
- runtime acquisition versus retained fields;
- automatic/manual start and stop;
- calculated channels and logging;
- project persistence of logging choices and log locations;
- specialized/high-speed logger definitions;
- file-format separation;
- disconnect/failure implications;
- buffering/backpressure and crash-recovery requirements;
- performance implications for long/high-rate sessions.

The goal is to extract behavioral contracts for EpicHub. It is not to reproduce TunerStudio's implementation or file formats.

---

# 1. User-visible logging model

## 1.1 Logging is a project workflow, not merely a file writer

**Observed behavior**

Bundled help exposes Data Logging as a project-level workflow with:

- normal/manual logging;
- Automatic Logging;
- Data Log Profiles;
- vehicle/performance/economy calculated fields;
- project-local DataLogs folders.

Example projects include a `DataLogs/` directory and project properties retaining log-path state.

**Mature lesson**

A logger session has policy and project context before bytes are ever written to disk.

**EpicHub conclusion — ★**

Treat Logger as a domain with explicit session state:

```text
Idle
Armed/WaitingForTrigger
Recording
Stopping/Finalizing
Completed
Failed/Interrupted
```

A file writer is one persistence backend under that domain, not the owner of logging semantics.

---

## 1.2 Automatic logging is condition driven

**Observed behavior**

`help/AutoLogging.html` documents automatic start and stop conditions. A condition can be configured from a channel/parameter comparison, and the user can also choose an expression-based condition. Start may be automatic while stop remains manual.

The bundled help's literal example expression appears internally inconsistent with the surrounding prose, so this research uses it only as evidence that compound expressions are supported, not as authoritative boolean syntax guidance.

**EpicHub conclusion — ★**

Logging triggers should be semantic, compiled conditions over ChannelStore samples, for example:

```text
StartCondition
StopCondition
ManualOverride
```

They should use the same validated channel/expression infrastructure as other derived/runtime logic, while remaining a separate policy domain from warnings or automation.

Trigger evaluation must carry normal channel validity/session-generation semantics. Stale or unavailable data must not accidentally satisfy a start/stop condition.

---

# 2. Log profiles and field selection

## 2.1 Default logging favors complete field coverage

**Observed behavior**

`help/datalogProfiles.html` states that the default Data Log Profile includes all fields defined by the firmware developer. This is intentionally broad to maximize later diagnostic usefulness.

A user can create named profiles that retain only a subset of fields. The Default profile is special and not user-editable.

When the profile editor is accepted, the selected profile becomes the profile used by logging sessions within that project.

**EpicHub conclusion — ★**

EpicHub should support named project-level log schemas, including an immutable/generated "all supported channels" baseline and user-created subsets.

A log profile should contain stable semantic ChannelIds plus metadata/version provenance, not UI labels alone.

---

## 2.2 Retained log fields and ECU acquisition are different concerns

**Observed behavior**

TunerStudio help explicitly says reducing the logged field set may reduce disk usage regardless of firmware, but only improves acquisition/data rate when the firmware supports partial runtime reads. Older firmware can require retrieving the complete runtime block even if only a subset is retained in the log.

The help specifically notes substantially higher rates on firmware that supports partial/scattered runtime reads.

**Mature invariant**

`channels written to the log` is not necessarily equal to `bytes/channels acquired from the ECU`.

**EpicHub conclusion — ★**

Keep three layers distinct:

```text
Transport acquisition plan
        ↓
ChannelStore / derived graph
        ↓
Log profile / retained schema
```

The logging profile may influence the acquisition planner when firmware capability permits, but it must not directly dictate transport reads.

This also prevents hidden dashboards, active logging, Diagnostics, Analyzer, and warning consumers from incorrectly fighting over one global subscription list.

---

## 2.3 Datalog schema is definition driven

**Observed behavior**

Representative project `mainController.ini` files contain `[Datalog]` sections whose ordered entries bind:

- semantic runtime channel;
- exported column label;
- numeric type;
- formatting;
- optional enable condition.

The definition comments explicitly state that entries are saved in datalog order.

**EpicHub conclusion — ★**

The resolved ECU/profile should expose a default logging schema independent from dashboard layout and independent from raw memory geometry.

Ordering, units, data type and optional availability belong to schema metadata. File writers consume that schema rather than rediscovering channels ad hoc.

---

# 3. Calculated channels in logs

## 3.1 Logged fields can include application-calculated channels

**Observed behavior**

The Performance and Economy help documents calculated power/economy fields becoming selectable through the Data Log Profile editor. Some fields are application-derived rather than direct ECU bytes.

The earlier dashboard/runtime-channel slice already established a mixed namespace of raw, bitfield, alias, expression and application channels.

**EpicHub conclusion — ✓ ★**

Logger should consume the same authoritative semantic ChannelStore/derived graph used by dashboards and other consumers.

Do not create a second logger-specific formula engine.

Each recorded sample must retain enough metadata/provenance to know the channel definition/version that produced it.

---

## 3.2 Stateful calculated channels require explicit reset/persistence rules

**Observed behavior**

Performance/economy features include accumulated/integrated measurements such as distance and fuel consumption, while example-project persisted channel state demonstrates that some runtime-derived state survives sessions.

**EpicHub conclusion — ★**

A log session must define whether each stateful channel is:

- continuous project state;
- reset at log start;
- reset at engine/session generation;
- merely sampled from an independently persisted accumulator.

The logger must not silently own or reset persistent trip-state just because recording started.

---

# 4. Specialized / high-speed loggers

## 4.1 TunerStudio supports logger types outside ordinary runtime logging

**Observed behavior**

Representative MS3 ECU definitions contain `[LoggerDefinition]` entries with logger types such as `csv`, alongside comments listing specialized types including composite, tooth and trigger loggers.

A logger definition can declare independently:

- a unique logger identity and display name;
- read command;
- read timeout;
- readiness condition;
- binary record geometry;
- bit-level record fields;
- per-field update conditions;
- derived/calculated fields.

**Mature lesson**

A high-speed/specialized diagnostic logger is not merely the normal runtime logger with a faster timer.

**EpicHub conclusion — ★**

Model logging sources/capture modes explicitly, for example:

```text
RuntimeLogSession
SpecializedLoggerSession
OnEcu/SDLogImport
```

They may share storage/export infrastructure and analysis tools, but acquisition semantics can differ fundamentally.

Do not force trigger/tooth/composite/high-speed acquisition through the normal RuntimeSnapshot cadence.

---

# 5. File representation and interoperability

## 5.1 Recorded-log representation is not one fixed format

**Observed behavior**

The supplied `LogViewer.properties` declares readable extensions:

```text
msl;mlg;csv
```

The package also includes an `MlgToCsv` reader/converter class and bundled MegaLogViewer functionality.

**EpicHub conclusion — ✓ ★**

Maintain the existing EpicHub direction of a format-neutral logging domain with format adapters at persistence/interoperability boundaries.

A conceptual architecture remains:

```text
LogSession / semantic samples & events
            ↓
       LogRecordSink
       /     |      \
     MLG    MDF4    CSV/export
```

No one container format should determine runtime ownership, channel identity, trigger policy or buffering semantics.

---

## 5.2 Project-local default log location is mature behavior

**Observed behavior**

Example projects include `DataLogs/ReadMe.txt`, and project properties include `logFileDir` / recent SD-log directory state.

**EpicHub conclusion — ★**

Logging location and recent-log state belong to project/application persistence, not tune calibration.

On Android, storage implementation will differ, but the user-facing expectation remains: logs are associated with the project/vehicle context and remain discoverable later.

---

# 6. Failure, disconnect and recovery

## 6.1 Static package evidence does not prove exact disconnect behavior

**Evidence limit**

The supplied help/resources do not establish exactly whether a normal active datalog is automatically finalized, paused, continued after reconnect, or split when ECU communication disappears.

That behavior requires a runtime experiment or more targeted implementation tracing.

**EpicHub conclusion — ★ required regardless**

EpicHub must define this explicitly rather than inheriting accidental transport behavior.

Recommended invariant:

- an active log keeps one immutable session identity;
- samples carry session-generation/validity;
- a transport disconnect creates a logged gap/event rather than fabricated samples;
- reconnect to a new ECU/session generation cannot silently splice semantically unrelated data into the same continuous segment;
- policy may continue the same user log as a new segment only after identity/profile validation.

---

## 6.2 Partial-file/crash recovery is not established by the package

**Evidence limit**

Static resources confirm project persistence and supported log formats but do not prove how TunerStudio repairs a partially written normal datalog after process or power failure.

**EpicHub conclusion — ★**

Because EpicHub targets an in-vehicle Android device, recoverable/append-safe recording should be a first-class requirement rather than inferred later.

The storage layer should support bounded-loss crash recovery, for example by chunking/periodic durable checkpoints and an explicit finalization/index step appropriate to each writer.

The exact MLG/MDF4 strategy remains a format-adapter concern.

---

# 7. Buffering, backpressure and performance

## 7.1 Reduced retained schemas can lower disk cost

**Observed behavior**

Data Log Profiles are explicitly documented as a way to reduce disk usage, independent of whether transport data rate improves.

**EpicHub conclusion — ★**

The logger must not serialize/store channels that the active log schema does not retain unless required for derived-channel dependencies or explicit diagnostic metadata.

Derived dependency inputs may be acquired/evaluated without being written as visible log columns.

---

## 7.2 Acquisition must not block on disk or UI

**Inference from mature separation, not directly documented scheduling behavior**

The package exposes independent acquisition, log-profile and viewer concepts but static evidence does not reveal exact thread/queue implementation.

**EpicHub conclusion — ★**

Implement a bounded producer/consumer pipeline:

```text
ECU/acquisition
    ↓
ChannelStore snapshot/event
    ↓
bounded logger queue/chunks
    ↓
storage writer
```

Disk I/O, compression or export must never stall the ECU transport/session owner.

Backpressure policy must be explicit and observable. If sustained storage lag forces loss, record a dropped-sample/gap event and diagnostics rather than silently pretending continuity.

---

## 7.3 High-rate/specialized capture needs its own performance budget

**Observed behavior**

Specialized logger definitions carry their own read commands, record sizes and timeouts. Runtime log profiles can also influence acquisition efficiency on capable firmware.

**EpicHub conclusion — ★**

Benchmark separately:

- normal runtime logging at full schema;
- reduced schema;
- high-rate specialized logger;
- logging + dashboard simultaneously;
- logging + analyzer simultaneously;
- long-duration log with file growth;
- temporary slow storage/backpressure.

Do not derive logger performance thresholds from Compose UI frame budgets.

---

# 8. Events and markers

## 8.1 The package does not provide sufficient static evidence for normal-log bookmark semantics

**Evidence limit**

This package contains logger UI/resources and specialized logging definitions, but the inspected help does not sufficiently establish the exact normal-datalog bookmark/marker model, timestamp semantics or persistence representation.

**EpicHub conclusion — △ ★**

EpicHub already has a strong reason to make semantic events first-class in logs because diagnostics/actions/warnings/tuning changes benefit from correlation.

Continue with the design direction:

```text
sample stream
+ timestamped semantic events
```

Possible events include logging start/stop, connection generation changes, user markers, tuning actions, Burn/persistence outcomes, warnings and diagnostic captures.

However, do not claim TunerStudio behavior here until dynamically verified.

---

# 9. EpicHub mapping summary

| Finding | EpicHub status | Decision |
|---|---|---|
| Logging is a stateful project workflow | △ | ★ Explicit LogSession state machine |
| Automatic channel/expression triggers | ✗ | ★ Compiled valid-data trigger policy |
| Named per-project log profiles | ✗ | ★ Typed semantic LogSchema |
| Retained fields != ECU acquisition | △ | ★ Keep acquisition planner separate |
| Default log schema definition driven | △ | ★ Compile from resolved profile |
| Derived channels can be logged | △ | ★ Reuse ChannelStore dependency graph |
| Stateful derived channels need lifecycle rules | ✗ | ★ Explicit taxonomy/reset semantics |
| Specialized high-speed loggers differ from runtime log | ✗ | ★ Separate acquisition-mode abstraction |
| Multiple file representations exist | ✓ | Keep format-neutral logger architecture |
| Project-local log location | △ | ★ Persist outside tune state |
| Disconnect/reconnect behavior | △ | Explicit segmented/session-generation policy |
| Crash/partial-file recovery | △ | ★ Design bounded-loss recovery |
| Disk backpressure policy | ✗ | ★ Bounded queue + visible gaps |
| Marker/bookmark specifics | ? | Needs runtime verification |

---

# 10. Suggested regression tests

## Profiles / schema

1. Generated/default profile contains every currently available default log field.
2. User profile can include a subset without changing dashboard demand.
3. User profile persists per project.
4. Missing channel after profile migration is reported explicitly.
5. Conditional profile field is absent/unavailable when its capability condition is false.
6. Derived channel can be retained without separately retaining every dependency column.
7. Schema ordering and metadata remain deterministic across restart.

## Acquisition separation

8. Reduced log schema on full-block firmware does not incorrectly reduce required transport read.
9. Reduced schema on partial-read-capable firmware permits acquisition planner optimization.
10. Dashboard hidden while logger active does not unsubscribe channels still demanded by logging.
11. Logger stops; dependencies used only by logger are released.
12. Logger + warning consumer share a derived dependency without duplicate evaluation.

## Triggering

13. Manual start/manual stop.
14. Automatic start from one valid channel threshold.
15. Compound expression start.
16. Automatic start plus manual stop.
17. Stop condition independent from start condition.
18. Stale/unavailable trigger input cannot spuriously start/stop.
19. Reconnect/new session generation reinitializes trigger evaluation deterministically.

## Recording validity / failures

20. Disconnect during recording creates an explicit gap/event and no fabricated zero samples.
21. Reconnect to same validated profile creates a deliberate new segment or documented continuation.
22. Reconnect to incompatible profile cannot silently continue the old semantic stream.
23. Process death during recording leaves a recoverable partial log with bounded loss.
24. Storage-write failure transitions logger to explicit failed/interrupted state.
25. Full disk / permission loss is surfaced without blocking transport owner.
26. Backpressure/drop policy produces observable dropped-sample metadata.

## Derived/stateful channels

27. Derived channel recorded once per source update policy and shared with dashboard consumers.
28. Invalid dependency yields invalid calculated sample, not zero.
29. Durable accumulator does not reset merely because a log starts.
30. Session-local accumulator resets exactly at configured lifecycle boundary.
31. Large time gap/restart does not integrate offline wall time into a trip quantity.

## Specialized loggers

32. Specialized logger can use its own command/readiness/timeout without disturbing normal RuntimeSnapshot scheduling.
33. Specialized bitfield/conditional record parsing produces correct semantic fields.
34. Specialized calculated fields are deterministic and metadata-tagged.
35. Specialized logger timeout fails its capture without corrupting the normal ECU session.

## Performance

36. Long full-schema runtime log has bounded memory growth.
37. Reduced-schema log measurably lowers writer/storage load.
38. High-rate acquisition remains stable while storage writes occur.
39. Logging + dashboard does not multiply derived-channel work per consumer.
40. Temporary storage slowdown exercises bounded queue/backpressure policy.
41. Finalization/export does not block the live transport owner.

---

# 11. Open questions requiring dynamic validation

Do not turn these into TunerStudio-derived requirements yet:

- exact normal datalog behavior on cable removal and reconnect;
- whether normal logs are continued, segmented or finalized after communication loss;
- exact writer flush cadence;
- exact in-memory buffering size;
- whether MSL/MLG writing is transactional/chunked and how partial files recover;
- exact auto-logging expression lifecycle/hysteresis behavior;
- exact user bookmark/marker semantics and file representation;
- exact file naming/rotation policy for PC-side normal logs;
- exact interaction between logging profile changes and an already active logging session;
- exact sample scheduling/memoization of calculated channels during logging.

These are good candidates for later controlled runtime tests if needed.
