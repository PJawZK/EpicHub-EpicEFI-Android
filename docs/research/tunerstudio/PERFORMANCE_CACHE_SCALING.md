# TunerStudio performance, cache, and scaling behavioral reference

## Scope

This final planned TunerStudio slice investigates performance-relevant behavior that can inform EpicHub without copying implementation details:

- runtime acquisition rate and partial-read behavior;
- hidden-dashboard subscription release;
- communication-history caching;
- block/chunk write geometry;
- bounded table/runtime history;
- large ECU definitions/projects;
- separation of acquisition, derivation, logging and presentation work;
- long-log and large-table scaling implications;
- where performance policy belongs in EpicHub.

The goal is not to benchmark TunerStudio itself. The supplied package does not provide a trustworthy cross-platform benchmark corpus for that. The useful evidence is architectural/behavioral: where mature software avoids unnecessary work, bounds retention, exposes rate controls, and separates expensive domains.

---

# 1. Runtime acquisition rate is a project/runtime policy

**Observed behavior**

Example projects persist `recordsPerSec` values rather than using one universal hard-coded rate. The bundled examples include materially different values, including 15, 100 and 1000 records/s.

This demonstrates that requested runtime acquisition/publication cadence is treated as configuration and can differ by project/firmware/use case.

**EpicHub conclusion — ★**

Do not encode one global telemetry rate into the Android UI or ChannelStore.

EpicHub should model distinct rates/budgets for:

- ECU acquisition;
- decoded/raw ChannelStore publication;
- derived-channel evaluation;
- logger retention;
- Analyzer consumption;
- Dashboard/Compose presentation;
- specialized high-speed capture.

A high ECU acquisition rate must not imply equivalent UI recomposition frequency.

---

# 2. Partial runtime reads can remove work at the source

**Observed behavior**

Bundled Data Log Profile help explicitly explains that selecting fewer logged fields can improve data rate only on firmware that supports partial runtime reads. Older firmware may still require retrieving all runtime data on every read even when the log retains fewer fields.

The same help notes substantially higher rates for firmware with partial-read support.

**Mature lesson**

Reducing downstream fields and reducing ECU transport work are different optimizations.

**EpicHub conclusion — ★**

Separate:

```text
Consumer demand
      ↓
Required semantic channels
      ↓
Derived dependency closure
      ↓
ECU acquisition plan allowed by firmware/profile
      ↓
Transport reads
```

If a firmware can request only the required runtime ranges/channels, demand can reduce transport cost. If it cannot, acquisition may still need the complete runtime block while downstream publication/logging remains selective.

Do not promise bandwidth savings merely because the Logger or Dashboard displays fewer channels.

---

# 3. Hidden dashboards release channel demand

**Observed behavior**

`TunerStudio.properties` contains:

`onlySubscibeActiveDash=true`

with the explicit comment that invisible dashboards unsubscribe from all channels.

**Mature lesson**

Inactive presentation should not continue consuming all presentation-specific update work.

**EpicHub conclusion — ✓/★**

Generalize this beyond dashboards through the demand graph already identified in `DASHBOARDS_RUNTIME_CHANNELS_CALCULATED_CHANNELS.md`.

A hidden Dashboard should release its presentation demand, but acquisition must remain active if the same channels are still required by:

- Logger;
- Analyzer;
- warning/incidents evaluation;
- live table lookup overlay;
- diagnostics;
- another visible consumer.

Do **not** reproduce the old performance mistake of retaining hidden Compose destinations simply to make switching look faster. EpicHub's physical performance work already proved retained/deactivated route composition could crash and was removed.

---

# 4. Acquisition, derivation, retention and rendering are separate workloads

**Combined evidence**

Across the prior slices, the package separates:

- ECU runtime acquisition;
- raw/bitfield/expression output channels;
- dashboard subscriptions;
- Data Log Profiles;
- specialized high-speed logger commands;
- gauge history;
- table lookup/history trails.

**EpicHub conclusion — ★**

The production pipeline should remain layered:

```text
ECU Transport
   ↓
Decode / ChannelStore
   ↓
Derived-channel graph
   ├─ Logger
   ├─ Analyzer
   ├─ WarningEngine
   ├─ Dashboard
   └─ LiveLookupOverlay
```

Each consumer owns its own publication/retention/render budget.

One 250 Hz transport stream must not become:

- 250 JNI object graphs/s;
- 250 full dashboard recompositions/s;
- 250 full 3D table rebuilds/s;
- duplicate formula evaluation in every consumer.

This aligns directly with EpicHub D-023's typed/packed semantic boundary and existing reusable RuntimeSnapshot buffer approach.

---

# 5. Derived channels should be cached per update generation, not per widget

**Evidence carried from calculated-channel slice**

The output-channel namespace supports chained expressions and multiple consumers can reference the same derived channel.

**EpicHub conclusion — ★**

For a given source sample/update generation, evaluate an active derived node at most once and publish the result through ChannelStore.

Appropriate cache key semantics are closer to:

```text
(profile generation,
 ECU session generation,
 source sample generation,
 ChannelId)
```

than to UI widget identity.

Do not build an unbounded cross-session memoization cache for live telemetry. Live derived caching should normally die with the source generation/session.

Stateful filters/accumulators remain separate from ordinary memoized stateless expressions.

---

# 6. Communication debug history is explicitly bounded

**Observed behavior**

`TunerStudio.properties` exposes `numOfCommDebugToCache` with comments stating that this controls how many historical controller interactions are retained in memory and dumped when a communication exception occurs when full Comm Debug is disabled.

The property comments also acknowledge performance overhead and characterize it as limited.

**Mature lesson**

Useful failure diagnostics do not require recording every transport interaction forever.

**EpicHub conclusion — ✓ ★**

This validates EpicHub's bounded `DiagnosticHub` / Rust diagnostic rings.

Keep diagnostic transport/session evidence as bounded structured rings with explicit capture promotion around failures. Full verbose tracing should be a deliberate temporary diagnostic mode, not normal steady-state behavior.

The cache should preserve enough pre-failure history to explain a timeout/reconnect without turning normal operation into continuous disk logging.

---

# 7. Short visual histories are bounded independently

**Observed behavior**

The 2D table history trail exposes a configurable number of previous reads. Dashboard/gauge history and peak behavior are similarly separate from full datalog retention.

**EpicHub conclusion — ★**

Use separate bounded ring buffers for short visual history such as:

- table lookup trail;
- gauge peak/trail;
- short diagnostic trend;
- recent warning pre-roll.

Do not query long-term Logger storage to draw every short UI trail, and do not retain full-session samples merely because a widget can show the latest N points.

Capacity should be expressed in time/sample budget appropriate to the feature rather than an unbounded list.

---

# 8. Block/chunk write capability belongs below semantic tuning edits

**Observed/implementation evidence from ECU definitions**

The inspected definitions support write geometry such as `tsWriteBlocks`, `pageChunkWrite`, conditional `blockingFactor`, page sizes and per-page Burn behavior.

Representative MS3 definitions allow large block factors compared with individual cell size.

**Mature lesson**

The semantic edit unit and transport write unit need not be the same.

**EpicHub conclusion — ★**

Large paste/interpolation/re-bin/analyzer edits should remain one semantic `TuneDraft` transaction while the mutation coordinator converts the resulting diff into bounded profile-valid transport blocks.

Do not cross JNI or USB once per edited cell.

The write planner should account for:

- contiguous changed ranges;
- profile maximum block size;
- page/region boundaries;
- regions requiring Burn before crossing;
- verification capability;
- cancellation/session generation.

Transport batching must never weaken semantic undo/history granularity.

---

# 9. Large tables should use revisioned sparse/delta publication where possible

**Inference from mature behavior + EpicHub architecture**

TunerStudio exposes rich table editing while runtime active-cell/history state updates independently. Static package evidence does not reveal its exact internal table repaint/caching implementation, so no claim is made about its algorithm.

The behavioral requirement is clear: runtime marker movement must not semantically mutate the table itself.

**EpicHub conclusion — ★**

Use a slow-changing table model plus small high-rate overlays.

For edits, prefer publishing something like:

```text
TableRevision
changed ranges/cells
selection revision
preview revision
```

rather than repeatedly serializing the complete table when one or a few cells changed.

A complete snapshot remains useful for initial load, restore, comparison baseline and recovery, but not as the only update mechanism.

2D and 3D continue to consume the same authoritative table revision.

---

# 10. 3D performance policy must preserve correctness first

**Project-native evidence**

The preserved Table Interaction Lab requires 3D as CORE and includes rich inspection behavior. Its browser implementation choices are not production architecture.

**EpicHub conclusion — ★**

Do not reduce 3D to an occasional static image to meet performance targets.

Instead separate:

- tune surface geometry revision;
- camera/view revision;
- selection/hover revision;
- live lookup overlay revision;
- inspection overlay revision.

Only recompute the portions invalidated by each revision.

For example, RPM/load movement should update the live marker/trail without regenerating surface geometry. Changing one table cell should invalidate affected geometry, not unrelated app state.

GPU/Compose implementation remains an engineering choice to benchmark; the behavioral contract is revision separation and shared semantic state.

---

# 11. Large definitions/projects justify compile-once resolved metadata

**Observed evidence**

The supplied package contains 105 ECU definition files. Several individual definitions approach or exceed roughly one megabyte of textual metadata and include large menus, constants, output channels, tables, curves, log schemas and commands.

Example projects also preserve a project-local copy of the selected definition.

**EpicHub conclusion — ★**

Raw definition parsing must not occur in hot UI paths.

Resolve/validate profile input once into immutable typed structures, for example:

```text
ResolvedProfileGeneration
  ChannelDescriptors
  DerivedGraph
  TuneObjectDescriptors
  MemoryRegions
  WritePlanCapabilities
  LoggerSchemas
  CalibrationDescriptors
  Compatibility/Migration metadata
```

UI and runtime code consume stable IDs/indexes from the resolved generation.

Cache invalidation is simple and explicit: replacing/migrating the profile creates a new profile generation.

Do not keep mutable parser objects as application-global truth.

---

# 12. Project-local profile caching is semantic authority, not merely an optimization

**Observed behavior**

TunerStudio copies the selected ECU definition into the project and reuses that project-local copy on open instead of always resolving against the current installation library.

**Mature lesson**

Reusing the previously selected definition avoids both reinterpretation and unnecessary rediscovery.

**EpicHub conclusion — ★**

The resolved profile attached to a project should be versioned/durable and reusable across opens when its provenance remains valid.

This is primarily a correctness rule and secondarily a performance benefit.

If the application gains a newer global definition, that should trigger an explicit migration opportunity—not silent cache invalidation and reinterpretation.

---

# 13. File/log history must not be loaded wholly into UI memory by default

**Evidence/inference boundary**

The package supports long recorded logs, restore history and project-local logs, but static evidence does not reveal exact internal paging/indexing algorithms.

No claim is made that TunerStudio uses a specific database, mmap, paging structure or cache implementation.

**EpicHub conclusion — ★**

Long logs should be treated as queryable persisted datasets, not giant UI lists.

Analyzer should request bounded windows/aggregates/downsampled series appropriate to the current viewport.

Recommended ownership:

```text
Log storage/index
   ↓ bounded query
Rust analysis/downsampling
   ↓ packed result
Compose plot
```

Do not materialize every channel × every sample into Kotlin objects simply to render a zoomed plot.

MDF4/MLG/other adapters may expose different indexing capabilities, but Analyzer consumes one semantic query contract.

---

# 14. Downsampling must preserve diagnostic/tuning meaning

**EpicHub conclusion — ★**

For large plotted logs, visual downsampling should not be a naïve every-Nth-sample drop that can erase spikes.

Use aggregation appropriate to the graph/query, e.g. min/max envelopes or other bounded representative sampling, while retaining exact source samples for cursor/detail queries and analysis algorithms.

The display dataset and analytical source dataset are separate concerns.

This is an EpicHub design conclusion; the supplied package does not establish TunerStudio's precise downsampling algorithm.

---

# 15. Cache ownership must follow semantic lifetime

**EpicHub synthesis — ★**

Different caches/history structures need different invalidation keys:

| State | Appropriate lifetime/key |
|---|---|
| decoded profile metadata | project/profile generation |
| tune-object descriptors | profile generation |
| table surface geometry | tune-object revision + view parameters |
| derived channel sample | session/sample generation |
| live lookup overlay | session/sample generation |
| short gauge/table history | session + bounded feature policy |
| diagnostics pre-roll | process/session bounded ring |
| analyzer plot window | log identity + query/window + channel set |
| tune diff result | authority/checkpoint revisions + profile mapping |
| migration plan | source profile generation + destination generation |

Avoid one generic global cache with mixed lifetimes.

A stale cache is more dangerous in tuning software than a cache miss.

---

# 16. Performance observability must remain cheap in normal operation

**Combined evidence**

TunerStudio's bounded comm cache and optional debug behavior show deliberate observability levels.

EpicHub already has bounded diagnostics and performance tracing infrastructure.

**EpicHub conclusion — ✓ ★**

Keep always-on metrics cheap and aggregate:

- acquisition rate;
- decode latency;
- ChannelStore publication rate;
- derived nodes evaluated;
- consumer demand counts;
- logger queue depth/drops;
- JNI packed bytes/call rate;
- table command latency;
- render/route timing;
- log query/downsample latency;
- cache hits/misses only where actionable.

Detailed per-sample/per-command traces should be activated only for bounded captures/debug sessions.

---

# 17. Performance is workload-specific

**Observed/project evidence**

TunerStudio example projects persist widely different `recordsPerSec` values, and its feature set includes normal runtime dashboards, table editing, live analysis, datalogging and specialized high-speed captures.

EpicHub's own physical foundation tests likewise showed real provisional content costs substantially more than trivial placeholders, while JNI/runtime cost was comparatively small.

**EpicHub conclusion — ★**

Do not define one synthetic "EpicHub performance score."

Qualify representative journeys separately:

1. Dashboard-only high-rate telemetry;
2. Tuner 2D table + live lookup;
3. Tuner 3D table + live lookup/inspection;
4. rapid keyboard/touch multi-cell editing;
5. large paste/interpolate/re-bin + live ECU write plan;
6. Logger full schema;
7. Logger + Dashboard;
8. Logger + Analyzer live/near-live;
9. long recorded-log pan/zoom/query;
10. large tune compare/diff;
11. project/profile migration;
12. diagnostics capture during transport faults;
13. specialized trigger/tooth/high-speed capture.

Performance acceptance should protect user-visible responsiveness and data integrity for each workload rather than chasing one microbenchmark.

---

# 18. Keep / improve / reject decisions

## KEEP / adapt

- configurable acquisition/runtime rates;
- partial runtime reads where firmware supports them;
- inactive presentation releasing demand;
- bounded recent transport diagnostics;
- bounded short visual histories;
- block/chunk transport operations;
- project-local stable profile authority;
- separate normal/high-speed logger acquisition modes.

## IMPROVE for EpicHub

- generalize dashboard unsubscription into a whole-app demand graph;
- compile definitions once into typed immutable Rust profile generations;
- share derived-channel computation across all consumers;
- use explicit cache lifetimes/revision keys;
- keep slow tune/table state separate from high-rate overlays;
- query/downsample logs in Rust before packed JNI publication;
- publish sparse/revisioned table changes instead of whole-table hot-loop copies;
- make backpressure/dropped data observable;
- benchmark real product journeys on reference devices.

## REJECT as production direction

- retaining inactive Compose route trees as a generic navigation optimization;
- one universal telemetry/render rate;
- one formula evaluation per gauge/widget;
- unbounded diagnostic/history/log memory;
- one JNI call per sample field or table cell;
- reparsing raw profile definitions in hot paths;
- treating fewer retained log fields as guaranteed lower ECU transport bandwidth;
- loading complete long logs into Kotlin/UI memory by default;
- one global cache with implicit invalidation.

---

# 19. Mapping to current EpicHub performance foundation

The current `phase2/performance-foundation` handoff already established:

- Kotlin/Compose + Rust with coarse semantic JNI;
- fixed/versioned packed RuntimeSnapshot transfer;
- Rust-owned ChannelStore foundation;
- bounded diagnostics;
- real-content route transitions are more expensive than placeholders;
- retained inactive route compositions were removed after deterministic physical crashes;
- performance infrastructure should remain a regression/profiling tool rather than block real product UI.

This TunerStudio slice supports that direction rather than overturning it.

The next implementation-era performance work should therefore happen only as real product subsystems arrive, with subsystem-specific budgets and traces.

---

# 20. Suggested regression/performance qualification cases

## Runtime/channel path

1. 250 Hz acquisition does not cause 250 Hz Compose recomposition.
2. five consumers of one derived channel evaluate it once per source generation.
3. hiding a dashboard releases only its demand.
4. Logger keeps required channels active while Dashboard is hidden.
5. firmware without partial reads continues full acquisition correctly with reduced log retention.
6. firmware with partial reads generates a minimal legal acquisition plan.
7. profile generation change invalidates resolved channel/derived caches atomically.
8. reconnect/session generation invalidates live-sample caches without losing durable project state.

## Table/Tuner

9. live lookup motion does not republish the complete table.
10. switching 2D↔3D does not duplicate table state.
11. editing one cell invalidates only required view/geometry state.
12. large paste is one semantic command and bounded transport plan.
13. re-bin on a large table remains one history transaction and avoids per-cell JNI calls.
14. high-rate lookup overlay plus 3D inspection stays responsive on reference phone.
15. inactive Tuner leaf routes are not kept alive solely for transition speed.

## Logging/Analyzer

16. slow disk writer cannot block ECU acquisition.
17. bounded logger queue reports drops/gaps under overload.
18. long log open does not materialize full sample history in Compose/Kotlin.
19. plot zoom requests bounded query windows.
20. downsampled plot retains narrow spikes in its envelope.
21. cursor/detail query retrieves exact source data after downsampled overview.
22. Logger + Dashboard shares ChannelStore/derived work.
23. Logger + Analyzer does not evaluate duplicate derived graphs.
24. specialized high-speed logger does not starve ordinary app lifecycle/session handling.

## Diagnostics/cache

25. bounded transport diagnostic history has fixed memory growth.
26. failure capture includes pre-failure context without normal full comm tracing.
27. enabling verbose debug has measurable/visible mode status.
28. cache hits never cross ECU session generations.
29. stale profile/tune/query caches are rejected by revision/generation mismatch.

## Project/compare/migration

30. opening a project reuses its durable resolved profile when valid.
31. installing a newer global definition does not silently reinterpret project state.
32. large tune diff is computed outside Compose and published as bounded semantic results.
33. migration creates a new profile generation and invalidates dependent caches atomically.
34. old project generation remains immediately reopenable after failed migration.

## Device qualification

35. compact phone Dashboard high-rate path.
36. compact phone 2D table editing.
37. compact phone 3D live overlay/inspection.
38. SM-T500 wide/tablet Dashboard/Tuner/Logger after real wide UI exists.
39. long Logger + Dashboard thermal/sustained run.
40. Analyzer large-log pan/zoom sustained run.

---

# 21. Open questions requiring real runtime measurement

Static package evidence does not establish:

- TunerStudio's exact internal repaint coalescing policy;
- exact calculated-channel memoization implementation;
- exact log-reader paging/cache algorithm;
- exact long-log plotting/downsampling algorithm;
- exact large-table rendering invalidation strategy;
- exact GC/allocation characteristics;
- exact CPU/memory costs of large projects;
- exact buffer depths and file flush cadence.

Do not infer these from behavior names or class structure.

For EpicHub these are better answered by implementing the clean architecture above and measuring it on the actual reference devices and ECU/protocol workload.

---

# 22. Final research conclusion

The mature performance lesson from TunerStudio is not a specific cache implementation. It is **work avoidance through explicit ownership and bounded state**:

- read only what firmware allows/consumers need;
- compute shared semantics once;
- unsubscribe inactive presentation;
- batch controller writes;
- bound diagnostic and visual histories;
- persist/query long history rather than keeping it all live;
- preserve project-local semantic authority;
- separate high-rate overlays from slow calibration objects;
- expose configurable rates and workload-specific behavior.

That is directly compatible with EpicHub's existing native Kotlin/Compose + Rust performance foundation and should guide the real Tuner, Logger, Analyzer and Dashboard implementations rather than trigger another placeholder-UI optimization phase.
