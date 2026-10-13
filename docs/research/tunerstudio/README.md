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

See `../table-interaction-lab/TUNERSTUDIO_RECONCILIATION.md` for the explicit crosswalk.

## Research order

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

### 5. Live logging / recorded logs

`LIVE_LOGGING_RECORDED_LOGS.md`

Covers manual and automatic logging, named Data Log Profiles, definition-driven log schemas, the distinction between ECU acquisition and retained log fields, calculated/stateful channels in logs, specialized/high-speed logger definitions, multiple recorded-log representations, project-local log storage, failure/reconnect requirements, buffering/backpressure, crash recovery, and long/high-rate regression cases.

### 6. Tune compare / difference reporting / restore history

`TUNE_COMPARE_DIFF_RESTORE_HISTORY.md`

Covers reusable semantic comparison between working tune, ECU, saved tune and historical checkpoints; non-mutating difference reports followed by explicit authority resolution; object/table-aware diff requirements; validation-generated differences; event-driven full-state restore points; deduplication and storage retention; guarded restore loading; pre-firmware-upgrade preservation; and typed cross-profile migration classifications.

### 7. Project migration / compatibility / firmware upgrades

`PROJECT_MIGRATION_FIRMWARE_UPGRADES.md`

Covers project-local profile authority across firmware changes, explicit separation of flashing from project migration, project-local overrides, pre-upgrade archives and firmware-provenance boundaries, compatibility-aware tune restoration, migration/validation provenance, separate sensor-calibration handling, partial/object-level migration, non-tune project-state migration, atomic source/destination project generations, and ordered rollback requirements.

Important conclusions include:

- flashing firmware does not itself migrate the project or prove tune compatibility;
- a project must retain its own profile generation until an explicit migration succeeds;
- project archives preserve broad application/project state but must not be represented as firmware-image backups unless the binary is actually included;
- migration needs per-setting/object classification and provenance for all automatic transforms;
- sensor/controller calibration assets can have lifecycle semantics distinct from ordinary tune settings;
- dashboards, log profiles, calculated channels and history need independent schema migration and may be partially compatible even when the tune itself migrates successfully;
- the source project/profile generation must remain available for rollback and comparison.

## Running backlog

`EPICHUB_BACKLOG.md` contains EH-TS-001 through EH-TS-064.

Additional reviewed backlog extensions are currently kept separate to avoid destructive large-file rewrites during research:

- `EPICHUB_BACKLOG_COMPARE_HISTORY.md` — EH-TS-065 through EH-TS-076;
- `EPICHUB_BACKLOG_MIGRATION.md` — EH-TS-077 through EH-TS-090.

They should be consolidated into one ordered backlog during the final research-maintenance pass.

## Next research slice

Proceed to **large-project/table/log performance and cache behavior**.

High-value questions include:

- project-open/profile-parse cost and caching;
- large tune/table editor responsiveness;
- runtime-channel/calculated-channel scaling;
- dashboard hidden/visible work suppression;
- long-log memory and indexing behavior;
- comparison/diff cost for large tunes;
- restore/history storage growth;
- resource/cache invalidation after profile or project changes;
- whether any mature TunerStudio cache behavior reveals failure modes EpicHub should explicitly avoid;
- concrete performance/regression budgets that fit EpicHub's existing `phase2/performance-foundation` work.

The research branch intentionally contains documentation/specification only until a reviewed finding is explicitly accepted for implementation.
