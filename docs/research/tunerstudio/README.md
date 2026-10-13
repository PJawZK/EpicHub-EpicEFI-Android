# TunerStudio behavioral reference research

## Purpose

This research treats TunerStudio as a mature behavioral/stability reference for EpicHub.

It does **not** treat TunerStudio as a design or implementation authority. The goal is to identify user-visible contracts, edge-case handling, persistence expectations, safety behavior, recovery behavior, and mature UX details that years of ECU-tuning use have made valuable.

Useful findings are translated into EpicHub requirements, regression tests, UX improvements, architecture notes, and explicit keep/improve/reject decisions.

No proprietary TunerStudio source is copied into this repository.

## Evidence discipline

Each finding is classified as one of:

- **Observed behavior** — directly visible in the supplied package, bundled help/resources, example projects, or an executed TunerStudio session.
- **Implementation evidence** — limited implementation inspection used only to resolve behavior, ambiguity, edge-case handling, or lifecycle ownership.
- **Inference** — plausible interpretation that still needs direct runtime validation.
- **EpicHub conclusion** — a requirement or design decision derived from the evidence.

Comparison markers:

- **✓** EpicHub already equivalent or better.
- **△** behavior differs and should be validated.
- **✗** useful missing capability.
- **★** deliberate EpicHub improvement opportunity.

## Reference package

Initial package inspected:

`TunerStudioMS_v3.6.00.06_Beta.tar.gz`

Package characteristics relevant to this research:

- Linux TunerStudioMS distribution, version 3.6.00.06 Beta;
- Java application package with bundled serial/USB support libraries;
- more than 100 ECU definition files;
- bundled dashboard, gauge, TuneView, analyzer, help, localization, and include resources;
- complete example projects containing tune, project-local ECU definition, dashboard, persisted values, restore points, and project settings.

The package is particularly useful because a large amount of mature behavior is externally defined and documented. Static implementation inspection is therefore a secondary tool, not the primary source of truth.

## EpicHub baseline used for comparison

Research starts from the current native EpicHub architecture, not the historical EpicDash-JZ implementation.

The active development branch at the start of this research is `phase2/performance-foundation`. The locked product direction remains Kotlin/Jetpack Compose + Rust with semantic JNI operations, a single semantic tuning authority, bounded diagnostics, and clean subsystem reimplementation under D-022.

TunerStudio findings must be mapped onto those existing ownership boundaries instead of recreating its historical architecture.

## Prior EpicHub table-interaction evidence

The TunerStudio table/editor research must also be reconciled with the pre-existing **EpicEFI Table Interaction Lab** work now preserved under:

`docs/research/table-interaction-lab/`

That prior work already establishes/project-prototypes several important EpicHub behaviors, including:

- 2D and 3D as peer **CORE** Tuner functionality;
- one authoritative `TuneDraft`/table state feeding both representations;
- shared selection, preview, protection/editability, and history semantics;
- semantic edit commands shared by 2D and 3D;
- input adapters calling shared domain operations;
- rich 3D inspection layers;
- explicit separation between table editing and ECU RAM-write/Burn ownership.

Therefore TunerStudio findings must distinguish:

1. **new mature behavior EpicHub had not yet modeled**;
2. **independent validation of an existing EpicHub design/prototype decision**;
3. **behavior where TunerStudio is intentionally weaker or more legacy-coupled than EpicHub's existing design**.

See:

`../table-interaction-lab/TUNERSTUDIO_RECONCILIATION.md`

for the explicit crosswalk.

## Research order

High-value subsystems are investigated progressively rather than by dumping a generic feature list.

Current order:

1. project/INI authority, ECU identity, connection/session, reconnect/recovery;
2. tune read/write state, difference handling, Burn/persistence safeguards;
3. table/curve/3D editing and keyboard/mouse workflows;
4. runtime dashboards, channel subscription, calculated channels, and layout persistence;
5. live logging and recorded-log behavior;
6. tune compare/diff and restore history;
7. project migration/compatibility and firmware upgrades;
8. large-project/table/log performance and cache behavior.

## Completed research slices

### 1. Connection / project / INI / session authority

`CONNECTION_PROJECT_INI_SESSION.md`

Covers transport candidate vs verified ECU identity, project-local profile authority, signature mismatch handling, offline mode, tune reconciliation, reconnect/failure implications, and diagnostic requirements.

### 2. Tune write / Burn / persistence safety

`TUNE_WRITE_BURN_SAFETY.md`

Covers local working tune vs ECU RAM/live state vs ECU nonvolatile state, page/region-specific persistence capability, Burn ordering, restore/checkpoint behavior, batching, verification, and failure cases.

### 3. Table / curve / 3D editing

`TABLE_CURVE_3D_EDITING.md`

Covers shared 2D/3D tune-object semantics, definition-driven table/curve capabilities, selected vs live-active position, weighted lookup overlays, keyboard-first editing, multi-cell transforms, interpolation, smoothing, re-binning, undo/redo, Apply/Cancel baselines, clipboard/import/export, 3D preference persistence, performance implications, and regression-test ideas.

This slice is explicitly reconciled with the earlier Table Interaction Lab. The prior Lab remains stronger EpicHub-specific design evidence for several interaction/3D decisions, while TunerStudio supplies maturity validation and additional edge cases.

### 4. Dashboards / runtime channels / calculated channels / persistence

`DASHBOARDS_RUNTIME_CHANNELS_CALCULATED_CHANNELS.md`

Covers semantic output-channel binding, hidden-dashboard subscription release, raw/bitfield/alias/expression channels, calculated-channel dependency graphs, persisted accumulators, dashboard project persistence, compatibility metadata, gauge limits/history, responsive geometry, stale/invalid data requirements, and separation of acquisition/calculation/render rates.

Important conclusions include:

- dashboard widgets consume semantic channels, not ECU memory geometry;
- hidden presentation should release unnecessary channel demand without implicitly stopping ECU acquisition;
- derived channels should be compiled into a validated dependency graph and shared across consumers;
- runtime samples need explicit validity and session-generation state;
- stateful and durable accumulators require explicit lifecycle/persistence semantics;
- dashboard layouts are independent project/UI state, not tune calibration state;
- acquisition, derived publication, and Compose rendering rates should remain independent.

### 5. Live logging / recorded logs

`LIVE_LOGGING_RECORDED_LOGS.md`

Covers manual and automatic logging, named Data Log Profiles, definition-driven log schemas, the distinction between ECU acquisition and retained log fields, calculated/stateful channels in logs, specialized/high-speed logger definitions, multiple recorded-log representations, project-local log storage, failure/reconnect requirements, buffering/backpressure, crash recovery, and long/high-rate regression cases.

Important conclusions include:

- logging is a stateful project workflow rather than a file-writer call;
- automatic start/stop conditions should use validated channel/expression semantics;
- log schemas and ECU acquisition plans are separate layers;
- logger consumes the shared ChannelStore/derived-channel graph rather than owning a second formula engine;
- specialized trigger/tooth/composite/high-speed loggers need acquisition semantics separate from ordinary RuntimeSnapshot logging;
- the logging domain must remain format-neutral so MLG, MDF4 and CSV/export are persistence adapters rather than architecture;
- disk I/O/backpressure must never block the ECU session owner;
- disconnects and dropped samples need explicit gaps/events rather than fabricated continuity;
- exact TunerStudio disconnect, flush, crash-repair and bookmark behavior remains intentionally unclaimed until dynamic validation.

## Running backlog

`EPICHUB_BACKLOG.md`

This is a review backlog rather than an implementation queue. Findings remain proposals until explicitly accepted into EpicHub architecture/runtime work.

The backlog currently runs through **EH-TS-064**.

## Next research slice

Proceed to **tune compare/diff and restore history**.

High-value questions include:

- comparison granularity and presentation;
- local tune vs ECU vs saved tune authority labels;
- ignored/non-comparable settings;
- table/curve difference visualization;
- whether comparisons operate offline;
- restore-point creation/retention and naming;
- firmware/profile compatibility across comparisons;
- rollback after bulk tuning or migration;
- performance on large tunes;
- how compare/history should integrate with the semantic `TuneDraft` history already planned for EpicHub.

The research branch intentionally contains documentation/specification only until a reviewed finding is explicitly accepted for implementation.
