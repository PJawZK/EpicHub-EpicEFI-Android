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

## Research order

High-value subsystems are investigated progressively rather than by dumping a generic feature list.

Current order:

1. project/INI authority, ECU identity, connection/session, reconnect/recovery;
2. tune read/write state, difference handling, Burn/persistence safeguards;
3. table/curve/3D editing and keyboard/mouse workflows;
4. runtime dashboards, channel subscription, calculated channels, and layout persistence;
5. logging and recorded-log behavior;
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

Covers shared 2D/3D tune-object semantics, definition-driven table/curve capabilities, selected vs live-active position, weighted lookup overlays, keyboard-first editing, multi-cell transforms, interpolation, smoothing, re-binning, undo/redo, Apply/Cancel baselines, clipboard/import/export, 3D preference persistence, performance implications, and 50 regression-test ideas.

## Running backlog

`EPICHUB_BACKLOG.md`

This is a review backlog rather than an implementation queue. Findings remain proposals until explicitly accepted into EpicHub architecture/runtime work.

## Next research slice

Proceed to **runtime dashboards, channel subscription, calculated channels, and dashboard/layout persistence**.

High-value questions include:

- what a gauge subscribes to and when subscription is released;
- stale/invalid runtime behavior across disconnect/reconnect;
- calculated-channel dependency and failure propagation;
- dashboard layout persistence and compatibility;
- runtime update throttling/caching;
- per-gauge warning/limit behavior;
- whether hidden dashboards stop consuming update work;
- how user-defined dashboards survive profile/project upgrades.

The research branch intentionally contains documentation/specification only until a reviewed finding is explicitly accepted for implementation.
