# TunerStudio dashboards, runtime channels, calculated channels, and layout persistence

## Scope

This slice investigates TunerStudio as a mature behavioral reference for:

- dashboard/gauge channel binding;
- runtime subscription lifetime;
- hidden-dashboard performance behavior;
- direct vs calculated output channels;
- expression/dependency handling;
- persisted/accumulated calculated values;
- gauge limits, warning bands, history, and presentation state;
- project-local dashboard persistence;
- compatibility between dashboards and ECU/profile channel definitions;
- runtime validity/stale-data implications.

The goal is to extract behavioral contracts for EpicHub, not to recreate TunerStudio's Swing dashboard classes, XML persistence format, or expression implementation.

---

# 1. User-visible behavior / promise

## 1.1 Dashboard components bind to named output channels

**Observed behavior**

Every one of the 59 bundled `.dash` files inspected contains `OutputChannel` bindings. Across those files there are 1,963 channel bindings referring to 286 distinct channel names.

Representative gauges bind to semantic names such as:

- `rpm`;
- `coolant`;
- `map`;
- `afr1`;
- `batteryVoltage`;
- `throttle`;
- `dutyCycle1`;
- status/application channels such as `controllerOnline` and `dataLoggingActive`.

The dashboard file does not encode the raw ECU byte offset for each gauge. It refers to a channel name resolved by the active controller/project definition.

**Mature lesson**

Dashboard definitions should consume semantic runtime channels, not transport geometry.

**EpicHub conclusion — ✓ ★**

This strongly aligns with EpicHub's Rust-owned ChannelStore direction. A dashboard widget should bind to a stable semantic `ChannelId`/descriptor and never own ECU offsets, frame decoding, or formula evaluation.

Dashboard presentation and ECU acquisition must remain independently replaceable.

---

## 1.2 Hidden dashboards can release channel subscriptions

**Observed behavior**

The shipped `TunerStudio.properties` contains:

`onlySubscibeActiveDash=true`

with the explicit comment:

> If true, will unsubscribe dashboards from all channels when not visible.

The public plugin API independently exposes an `OutputChannelServer` with explicit `subscribe(...)`, `unsubscribe(...)`, and `unsubscribeConfiguration(...)` operations.

**Implementation evidence**

This confirms that channel consumption is subscription-oriented rather than merely a global repaint loop in which every dashboard permanently consumes every channel.

**Mature lesson**

Invisible UI should not create unnecessary downstream work.

**EpicHub conclusion — ★**

EpicHub should use demand-driven presentation subscriptions or equivalent dependency activation:

```text
ECU acquisition / ChannelStore
       │
       ├─ always-required system consumers
       ├─ logger consumers
       ├─ diagnostics consumers
       └─ UI demand graph
            ├─ active dashboard
            ├─ active tuner overlay
            └─ active analyzer/tool
```

Do not interpret this as permission to stop acquiring ECU frames whenever a dashboard is hidden. Acquisition policy and presentation subscription are different concerns.

The engine may still need a base runtime set for logging, safety, diagnostics, or calculated dependencies even when no gauge is visible.

---

# 2. Runtime channel model

## 2.1 Raw, bitfield, alias, and expression channels share one namespace

**Observed behavior**

A representative MS3 `[OutputChannels]` section contains several channel forms:

- byte/word-backed `scalar` channels;
- `bits` channels extracted from a backing value;
- aliases such as `throttle = { tps }`;
- calculated expressions such as average EGO correction, injector duty cycle, boost/vacuum, fuel economy, AFR/lambda conversion, target error, and dynamic gauge limits.

In the inspected representative definition there were:

- 343 direct scalar declarations;
- 100 bitfield declarations;
- 101 expression-style declarations.

Expressions can depend on other output channels and configuration/tune values.

**Mature lesson**

From the user/dashboard perspective, a useful runtime channel need not correspond one-to-one with a field physically transmitted by the ECU.

**EpicHub conclusion — ★**

EpicHub's channel system should expose one typed semantic namespace while retaining source provenance internally, e.g.:

```text
ChannelDescriptor
├─ id
├─ name/label
├─ units
├─ numeric type / precision
├─ source kind
│   ├─ ECU field
│   ├─ bit extraction
│   ├─ derived expression
│   ├─ application/system
│   ├─ accessory
│   └─ persisted accumulator
├─ dependency IDs
├─ validity policy
└─ update policy
```

Consumers should not need different APIs merely because one channel is raw and another derived.

---

## 2.2 Calculated channels are dependency-driven

**Observed behavior**

The representative ECU definition includes chained calculated values. Examples include duty-cycle calculations derived from RPM, pulse width, cylinder/injection configuration and intermediate calculated channels; fuel economy channels depend on fuel-flow and vehicle-speed channels; AFR/lambda aliases depend on fuel stoichiometry/configuration.

**Public API evidence**

The bundled public Plugin API exposes:

- `OutputChannel.getFormula()`;
- `OutputChannelServer.subscribe(...)`;
- `OutputChannelServer.getOutputChannel(...)`;
- `MathExpressionEvaluator.evaluateExpression(...)`.

**EpicHub conclusion — ★**

EpicHub should compile calculated channels into a validated dependency graph rather than evaluating arbitrary text independently inside widgets.

At profile/project load:

1. parse/resolve channel references;
2. detect missing dependencies;
3. reject or quarantine cycles;
4. infer/update units and precision where explicitly defined;
5. establish a deterministic evaluation order;
6. record dependency provenance for diagnostics.

At runtime, evaluate only nodes required by active consumers or other required derived nodes.

---

# 3. Persisted and accumulated channels

## 3.1 Some calculated values deliberately survive application restarts

**Observed behavior**

Bundled example projects contain `projectCfg/persistedChannelValues.properties` and a `.bak` copy.

The MS3 example contains persisted expressions for accumulated quantities based on vehicle speed and fuel flow. The project properties also contain corresponding `PersistedAccumulatedValue_...` entries.

One persisted fuel-flow expression explicitly guards against a large `deltatime` value with a conditional that contributes zero when the elapsed time is too large.

**Mature lesson**

Not all useful channels are instantaneous. Trip-like/integrated values need durable state, and accumulation must defend against discontinuities such as long pauses/restarts.

**EpicHub conclusion — ★**

EpicHub should distinguish:

- stateless derived channels;
- stateful filters/estimators;
- session accumulators;
- durable/project accumulators.

Persisted accumulators need explicit reset semantics, schema/version identity, atomic storage, and time-gap handling.

Do not persist arbitrary expression strings as the primary identity. Use stable semantic accumulator IDs plus a versioned definition/provenance record.

---

## 3.2 Backup evidence shows persisted channels are treated as recoverable project state

**Observed behavior**

The example project includes both:

- `persistedChannelValues.properties`;
- `persistedChannelValues.properties.bak`.

**EpicHub conclusion — △ ★**

The failure case is useful evidence: stateful runtime values must not be corrupted by an interrupted save.

EpicHub should use its stronger atomic/transactional persistence model rather than copying `.bak` file conventions.

---

# 4. Dashboard persistence and portability

## 4.1 Dashboard layouts are durable project assets

**Observed behavior**

Bundled example projects contain project-local `dashboard/` directories with multiple named `.dash` files.

`project.properties` stores named dashboard slots such as:

- `userDash0.DashName=Default`;
- `userDash0.FileName=Default.dash`;
- additional user dashboard names/files;
- `dashBoardFile=...dashboard.dash`;
- `useCommonDashboardDir=false` in multiple projects.

User-facing localization resources include explicit actions/messages for:

- Save Dashboard;
- Save Dashboard Layout;
- Load Dashboard;
- Add New Gauge Cluster Tab;
- notification that the project's dashboard layout has been modified.

**Mature lesson**

Dashboard customization is project/workflow state worth preserving, not temporary view state.

**EpicHub conclusion — ✗ ★**

EpicHub should persist dashboard layouts independently from tune calibration state but within project/profile context.

A dashboard save must not dirty the ECU tune. Conversely, loading a tune must not silently replace a customized dashboard unless the user explicitly requests a bundled/default layout.

---

## 4.2 Dashboard files carry compatibility identity

**Observed behavior**

All 59 bundled dashboard files contain a `firmwareSignature` in their `versionInfo` metadata.

**Inference**

This gives TunerStudio a way to associate a dashboard with the controller definition/profile context under which it was created, although the exact runtime rejection/migration behavior has not yet been fully reproduced.

**EpicHub conclusion — ★**

Persist dashboard compatibility metadata such as:

- dashboard schema version;
- source profile ID/version;
- required semantic channel IDs;
- optional/fallback channel bindings;
- migration provenance.

Do not make dashboard compatibility depend on a single opaque firmware-signature string, but preserve the mature principle that layout portability is profile-aware.

---

# 5. Gauge behavior and presentation state

## 5.1 Gauge thresholds are per-component persistent properties

**Observed behavior**

All 59 bundled dashboards contain gauge properties for warning/critical limits. Common fields include:

- `LowWarning`;
- `HighWarning`;
- `HighCritical`;
- frequently `LowCritical` in newer gauge files;
- warning/critical colors;
- min/max and value precision.

Gauge files also contain `ValueProvider` forms for limits, showing that limits can in some cases be dynamic rather than immutable numeric literals.

**Mature lesson**

A gauge is not only a number renderer. Users expect reusable display semantics such as units, range, precision, warning bands, and sometimes dynamic limits.

**EpicHub conclusion — △ ★**

EpicHub should keep dashboard warning/presentation thresholds distinct from the authoritative WarningEngine/safety-warning domain.

A gauge-local red band is presentation. A project warning incident is semantic system state. They may share threshold definitions where deliberately linked, but one must not silently acquire the other's authority.

---

## 5.2 Gauge history is a first-class presentation feature

**Observed behavior**

All 59 bundled dashboards contain `ShowHistory` and `HistoryDelay` fields. Localization resources include `History tell tail` and `Set History Timeout`.

Newer gauge templates also persist values such as `HistoricalPeakValue` and smoothing-related properties.

**EpicHub conclusion — ★**

Short-lived presentation history should be handled as bounded UI/runtime state rather than forcing every gauge to query a full logger database.

Useful categories:

- current value;
- short bounded trail/peak-hold state;
- logged historical data.

These have different retention and performance requirements.

---

## 5.3 Layout geometry is normalized/persistent

**Observed behavior**

All 59 bundled dashboard files persist relative geometry fields:

- `RelativeX`;
- `RelativeY`;
- `RelativeWidth`;
- `RelativeHeight`.

**Mature lesson**

Durable dashboards should survive window-size changes better than absolute pixel layouts.

**EpicHub conclusion — ★**

EpicHub layouts should persist semantic/responsive layout constraints rather than raw device pixels. Exact Compose representation can differ, but phone/tablet resizing and orientation must not destroy user layouts.

The project should support a migration layer when the layout schema evolves.

---

# 6. Runtime validity and stale-data handling

## 6.1 The package contains explicit invalid-state concepts, but exact disconnect rendering is not fully proven

**Implementation/resource evidence**

Gauge templates contain fields such as `InvalidState` and `GoingDead`. Dashboard channel sets also include application channels such as `controllerOnline`.

**Limitation**

Static package inspection does not prove the exact sequence/timing by which a gauge becomes visually stale/invalid during a USB disconnect or runtime timeout. `GoingDead=false` in saved files appears to be serialized component state, but its lifecycle semantics need runtime observation before being treated as authoritative.

**EpicHub conclusion — △ ★**

Do not copy unknown TunerStudio stale rendering semantics.

EpicHub should instead make data validity first-class and explicit in the channel sample:

```text
ChannelSample
├─ value
├─ source timestamp / sequence
├─ session generation
├─ validity
│   ├─ valid
│   ├─ stale
│   ├─ unavailable
│   ├─ invalid-calculation
│   └─ source-error
└─ optional diagnostic reason
```

Derived channels must propagate validity according to defined rules. A stale or unavailable dependency must never silently produce a plausible fresh zero.

This extends EH-TS-015 with a concrete runtime representation.

---

# 7. Performance / caching

## 7.1 Hidden-view unsubscribe is the strongest direct performance signal

**Observed behavior**

TunerStudio explicitly ships with hidden dashboards unsubscribed from channels.

**EpicHub conclusion — ★**

UI demand should be reference-counted or dependency-counted. When no active consumer needs a presentation-only channel/derived node, its expensive transformation/render publication can stop.

However:

- ECU frame acquisition may remain fixed-rate;
- Logger may independently require the channel;
- Diagnostics may independently require it;
- dependent calculated channels can keep a source active;
- safety/warning rules can keep a source active.

Therefore the optimization belongs in a dependency/demand graph, not simply `if screen hidden then stop data`.

---

## 7.2 Dashboard rendering rate need not equal acquisition rate

**Inference supported by subscription architecture and EpicHub's existing performance baseline**

The package proves selective subscription but does not, from static inspection alone, establish every repaint/coalescing rule.

**EpicHub conclusion — ★**

Keep three rates separable:

1. ECU acquisition/sample rate;
2. derived-channel evaluation/publication rate;
3. Compose visual refresh rate.

A 200–250 Hz runtime stream does not imply every gauge must recompose 200–250 times/s.

Use latest-value snapshots/coalescing for visual consumers while logger/analysis consumers can retain higher-rate samples as required.

---

# 8. Relevant implementation evidence

Implementation inspection was intentionally limited.

Useful evidence came from:

- public Plugin API method signatures for output-channel subscription and expression evaluation;
- external ECU-definition output-channel declarations;
- external dashboard/gauge XML resources;
- project-local dashboard files/properties;
- persisted-channel project files;
- global TunerStudio properties.

No proprietary implementation source or algorithm is copied.

---

# 9. Limitations / quirks / keep-improve-reject

## Keep

- semantic named-channel dashboard binding;
- demand-driven subscriptions;
- project-persistent dashboard customization;
- raw and derived channels in one consumer-facing namespace;
- stateful/persisted runtime channels where useful;
- bounded gauge history/peak behavior;
- responsive/relative layout persistence.

## Improve

- compile definitions into typed ChannelDescriptors and a dependency graph;
- explicit sample validity/session generation instead of relying on visual component state;
- atomic persisted accumulator storage;
- schema-aware dashboard migration;
- separate gauge presentation thresholds from authoritative warning incidents;
- rate separation between acquisition, calculation, publication, and Compose rendering.

## Reject / do not copy blindly

- expression strings as long-term semantic identity;
- Java/Swing component class names in persisted product layouts;
- firmware-signature string as the sole dashboard compatibility mechanism;
- any implicit assumption that a numeric zero is valid when source data is absent;
- tying application data acquisition directly to dashboard visibility.

---

# 10. EpicHub mapping summary

| Finding | EpicHub status | Decision |
|---|---|---|
| Named semantic output-channel bindings | ✓ | Keep/strengthen typed ChannelId |
| Hidden dashboards unsubscribe | △ | ★ Demand graph, not acquisition shutdown |
| Raw + bits + expressions share channel namespace | △ | ★ Typed source-kind abstraction |
| Derived channels can chain dependencies | ✗ | ★ Compiled dependency DAG |
| Persisted accumulated channels | ✗ | ★ Stateful channel/accumulator domain |
| Project-local dashboard files | ✗ | ★ Independent dashboard persistence |
| Dashboard/profile compatibility metadata | ✗ | ★ Typed profile/schema provenance |
| Per-gauge warnings/limits/history | △ | Keep presentation; separate semantic warnings |
| Relative geometry persistence | ✗ | ★ Responsive layout schema |
| Explicit stale/invalid runtime state | △ | ★ First-class ChannelSample validity |
| Visual update rate separate from data rate | △ | ★ Coalesced Compose publication |

---

# 11. Suggested regression tests

## Subscription / visibility

1. **Active dashboard subscribes required channels**
   - opening a dashboard activates only its required presentation dependencies plus shared/system consumers.

2. **Hidden dashboard releases presentation-only dependencies**
   - switching dashboards removes demand from channels no longer needed;
   - logger/safety/diagnostic consumers keep their dependencies active.

3. **Two dashboards share a channel**
   - reference count remains nonzero while either active consumer still requires it.

4. **Calculated dependency remains active**
   - visible derived channel keeps all required upstream sources active.

5. **Rapid dashboard switching**
   - no leaked subscriptions;
   - no duplicate subscriptions;
   - no stale session generation continues publishing.

## Calculated channels

6. **Raw scalar channel**
   - scale/offset/units resolve correctly.

7. **Bitfield channel**
   - updates from the backing raw field without duplicate acquisition.

8. **Alias channel**
   - mirrors dependency validity/value without creating an independent transport read.

9. **Chained expression**
   - topological evaluation produces deterministic result.

10. **Missing dependency**
    - derived channel becomes invalid with diagnostic reason;
    - project/profile load reports the unresolved reference.

11. **Dependency cycle**
    - cycle is rejected/quarantined at definition resolution;
    - runtime evaluator never recursively loops.

12. **Division-by-zero / non-finite result**
    - derived sample becomes invalid according to defined policy;
    - NaN/Inf does not silently enter gauges/logs unless explicitly supported.

13. **Stale dependency**
    - derived validity becomes stale/unavailable according to rule;
    - old numerical result is not labeled fresh.

## Runtime validity

14. **Disconnect while value nonzero**
    - last value may remain visually available if product policy chooses freeze-last;
    - validity clearly changes to stale/unavailable;
    - it never becomes a fresh zero.

15. **Reconnect creates new generation**
    - old-generation samples cannot overwrite new session data.

16. **Calculated channel across reconnect**
    - stateful evaluator resets or restores according to declared policy;
    - no delta-time spike is integrated.

## Persistent accumulators

17. **Trip accumulator restart**
    - durable value restores atomically.

18. **Large time gap**
    - accumulation does not integrate the whole offline gap.

19. **Reset accumulator**
    - explicit reset is one recoverable semantic action;
    - project persistence updates atomically.

20. **Definition version changes**
    - incompatible accumulator definition does not reinterpret old persisted value silently.

## Dashboard persistence

21. **Save customized layout**
    - geometry/style/channel bindings persist;
    - tune dirty state remains unchanged.

22. **Open project after app upgrade**
    - dashboard schema migration is deterministic;
    - original file/state is recoverable if migration fails.

23. **Missing channel after profile upgrade**
    - affected widget is marked unresolved;
    - rest of dashboard loads;
    - no crash or binding to wrong same-named/raw-offset field.

24. **Channel renamed with migration alias**
    - explicit migration maps old semantic ID to new ID and records provenance.

25. **Dashboard from another profile**
    - compatible semantic bindings load;
    - incompatible widgets are reported individually;
    - ECU tuning authority is unaffected.

26. **Responsive layout**
    - phone/tablet/orientation changes preserve semantic placement without pixel-coordinate corruption.

## Performance

27. **Large dashboard channel count**
    - subscription setup is bounded;
    - no one-coroutine/one-JNI-call per gauge architecture.

28. **High-rate source, 60 Hz UI**
    - visual consumer receives coalesced latest values;
    - acquisition/logger sample rate remains unaffected.

29. **Hidden expensive calculated channel**
    - evaluation stops when no active dependency requires it.

30. **Shared expensive calculated channel**
    - expression evaluates once per source update/publication cycle, not once per gauge.

31. **Dashboard with many gauges bound to one channel**
    - one channel publication fans out efficiently;
    - no duplicate ECU reads or expression evaluations.

32. **Process death during dashboard save**
    - previous valid layout remains recoverable;
    - tune/project calibration files are unaffected.

---

# 12. Open questions for runtime execution/deeper tracing

Static package evidence is not sufficient yet to claim exact behavior for:

- precise gauge visual treatment and timeout timing after ECU disconnect;
- exact repaint/throttle frequency for visible gauges;
- whether expression evaluation is globally memoized per runtime tick;
- exact retained-history implementation and memory limits;
- how TunerStudio migrates a saved dashboard when a required output channel disappears or changes type;
- exact semantics of `GoingDead` / `InvalidState` lifecycle;
- whether dashboard `firmwareSignature` causes rejection, warning, remapping, or merely metadata storage in every load path.

These remain explicitly unresolved rather than inferred as facts.
