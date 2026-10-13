# EpicHub improvement backlog from TunerStudio behavioral research

This is a review backlog, not an implementation queue. Items remain evidence-derived proposals until explicitly accepted into EpicHub architecture or runtime work.

## Connection / identity / project authority

### EH-TS-001 — Explicit session identity state machine

**Marker:** ✗ ★

Separate discovered transport candidate, opened transport, queried ECU identity, resolved compatible semantic profile, mismatch/unsupported states, offline project session, and fully online session.

### EH-TS-002 — Durable project profile identity

**Marker:** △ ★

Persist enough profile identity/version/provenance with a project to reopen it under the same semantic interpretation after application updates. Do not silently reinterpret an existing project through the newest global definition.

### EH-TS-003 — Typed resolved ECU profile in Rust

**Marker:** ★

Compile/resolve firmware definition input into an immutable validated runtime model containing protocol geometry, runtime channels, tuning-memory semantics, limits, table/curve descriptors, and capabilities. UI consumers use semantic IDs/operations, not raw textual offsets.

### EH-TS-004 — Restricted unsupported/mismatch diagnostics mode

**Marker:** ★

If an ECU responds but compatibility is not proven, retain useful identity/transport diagnostics without granting normal tune-write authority.

### EH-TS-005 — Tune-sync state separate from connection state

**Marker:** ✗ ★

After identity/profile compatibility is established, independently determine whether local working tune and controller state agree.

## Persistence / recovery

### EH-TS-006 — Durable working tune

**Marker:** ✗ ★

Define local working tune persistence independently from controller RAM state, controller nonvolatile/Burn state, named tune exports, and restore/history checkpoints.

### EH-TS-007 — Immutable checkpoint before destructive reconciliation

**Marker:** ✗ ★

Before replacing local state with controller state, firmware/profile migration, or another bulk destructive transition, capture a recoverable pre-transition checkpoint and diff metadata.

### EH-TS-008 — Atomic project persistence

**Marker:** △ ★

Use transactional/atomic persistence so process death cannot leave project metadata, tune state, profile authority, or history from mixed generations.

## Failure / reconnect

### EH-TS-009 — Session-generation ownership for ECU operations

**Marker:** ★

Every multi-step ECU operation executes against one session generation and cancels if the connection is replaced or lost.

### EH-TS-010 — Bounded reconnect/backoff and reversible candidate suppression

**Marker:** △ ★

Prevent reconnect storms or repeated problematic automatic opens. Preserve an explicit user retry/reset path and adapt the policy to Android USB behavior.

### EH-TS-011 — Structured transport/session evidence in DiagnosticHub

**Marker:** ✓ ★

Feed bounded structured discovery, permission, endpoint, identity, profile, timeout/error, reconnect/backoff, and generation events into the existing diagnostics foundation.

## Offline workflow

### EH-TS-012 — First-class offline project mode

**Marker:** ✗ ★

Allow tune/project editing without a connected ECU while making live-only capabilities explicitly unavailable.

### EH-TS-013 — Revalidation after offline edits

**Marker:** ★

Reconnect after offline work must re-establish ECU identity/profile compatibility and tune-sync state before controller mutation.

## UX / runtime validity

### EH-TS-014 — Explain mismatch type and safe next actions

**Marker:** ★

Distinguish transport unavailable, permission denied, identity-query failure, unsupported firmware, profile mismatch, and tune-state difference instead of showing one generic connection error.

### EH-TS-015 — Distinguish stale runtime from valid zero

**Marker:** ★

A disconnect must invalidate/freeze/mark telemetry according to a deliberate data-validity policy. Never convert loss of data into plausible zero values.

## Tune write / Burn / persistence

### EH-TS-016 — Separate local, ECU-live, and ECU-persisted tune state

**Marker:** ✗ ★

Represent at least:

- local pending edit;
- ECU live/RAM applied;
- nonvolatile persistence pending;
- persisted/verified;
- persistence failed/unknown;
- RAM-only/non-persistable.

A single `dirty` or `saved` flag is insufficient.

### EH-TS-017 — Per-region persistence capability

**Marker:** ✗ ★

Resolved ECU profiles must express whether each semantic tune region is writable live, persistable, RAM-only, read-only, verification-capable, or subject to special persistence requirements.

### EH-TS-018 — Serialized semantic mutation coordinator

**Marker:** ✗ ★

One Rust-owned mutation coordinator should order/coalesce tune writes, page/region transitions, persistence, verification, cancellation, and session-generation checks. Compose screens must not independently emit raw ECU write/Burn sequences.

### EH-TS-019 — Preserve Burn safety invariant without UI-lifecycle coupling

**Marker:** ★

Keep the mature invariant that required volatile changes are not left ambiguous across controller persistence boundaries, but reject `screen/dialog close -> Burn` as the architecture. Navigation and persistence are separate domains.

### EH-TS-020 — Explicit uncertain-persistence recovery

**Marker:** ✗ ★

If disconnect/timeout occurs after live write but before confirmed persistence, preserve local intent and mark controller persistence unknown. Reconnect must re-identify and reconcile before retrying.

### EH-TS-021 — Coarse semantic bulk writes

**Marker:** ★

Paste/interpolate/re-bin/analyzer bulk edits should be converted below the semantic API into bounded profile-valid write blocks rather than one UI/JNI/transport call per cell.

## Table / curve / 3D editing

### EH-TS-022 — One semantic tune object across 2D and 3D views

**Marker:** △ ★

Numeric table, curve, 3D surface, and analysis overlays must project the same authoritative working tune. No view gets an independent editable copy.

### EH-TS-023 — Separate editor selection from live lookup state

**Marker:** ✗ ★

Model user selection, live lookup coordinate, contributing lookup cells/weights, and recent lookup trail independently.

### EH-TS-024 — Definition-driven table/curve descriptors

**Marker:** ✓ ★

Extend the typed resolved profile to describe stable tune-object IDs, axes/value storage, shape, units, precision, limits, live-position channels, directional labels, persistence regions, and allowed transformations.

### EH-TS-025 — Semantic editor-command layer

**Marker:** ✗ ★

Create input-independent commands for navigation, selection, increment/decrement, explicit add/subtract/set/scale, interpolation modes, smoothing, copy/paste, undo/redo, jump-to-live, and follow mode. Keyboard, touch, rotary controls, and automation/tests invoke the same command semantics.

### EH-TS-026 — Atomic multi-cell edit transactions

**Marker:** ✗ ★

Multi-cell transforms must be one semantic transaction with one validation pass, diff, history entry, diagnostic event, and transport plan.

### EH-TS-027 — Explicit interpolation modes

**Marker:** ✗ ★

Keep horizontal, vertical, and 2D-area/corner interpolation as distinct commands rather than one ambiguous interpolation action.

### EH-TS-028 — Previewable smoothing

**Marker:** ✗ ★

Smoothing should have an explicit factor, affected selection, deterministic preview/diff, and one undo unit.

### EH-TS-029 — Axis edit and Re-Bin are different operations

**Marker:** ✗ ★

Direct axis-value edits must not silently resample Z. Re-Bin should explicitly validate new monotonic axes, resample the old surface onto the new coordinate system, preview the result, and commit atomically.

### EH-TS-030 — Central semantic tune history

**Marker:** ✗ ★

Undo/redo should be owned by the semantic tuning domain, not individual Compose widgets. History entries must retain affected tune objects, before/after state, source command, profile/session context, and whether controller mutation already occurred.

### EH-TS-031 — Explicit edit-session baseline and revert semantics

**Marker:** ✗ ★

Preserve TunerStudio's useful Apply/Cancel checkpoint concept but decouple local checkpoint, live ECU apply, ECU persistence, and UI close. Any `Revert` action must name the baseline when multiple authorities exist.

### EH-TS-032 — Plain-text table clipboard interoperability

**Marker:** ✗ ★

Support stable tabular copy/paste compatible with spreadsheets. Parse/validate complete input before atomic mutation; never partially apply malformed clipboard data.

### EH-TS-033 — Tune-object-level import/export

**Marker:** ✗ ★

Allow moving one compatible table/curve without replacing unrelated tune state. Validate semantic identity/profile compatibility, not dimensions alone.

### EH-TS-034 — Persist editor presentation separately from tune

**Marker:** ✗ ★

3D camera angle, color/theme, spacing mode, follow preference, trail length, increment preference, and other presentation state must never dirty the tune or enter ECU sync logic.

### EH-TS-035 — Weighted live-cell overlay and guarded expert editing

**Marker:** ✗ ★

Where firmware/profile semantics allow it, show interpolated contributing cells/weights. Any mode that edits multiple live-weighted vertices should be explicit, previewable, and guarded rather than a default mutation mode.

### EH-TS-036 — High-rate live overlay path independent of table storage

**Marker:** △ ★

Runtime marker/weight/trail updates must not rebuild, serialize, or cross JNI with the entire table on every sample. Keep slow table state and high-rate lookup state on separate update paths.

### EH-TS-037 — Single command registry for shortcuts/help/accessibility

**Marker:** ★

Generate shortcut help, menus, accessibility labels, and key bindings from one semantic command registry so documentation cannot drift from actual behavior.

## Dashboards / runtime channels / calculated channels

### EH-TS-038 — Semantic ChannelDescriptor model with source provenance

**Marker:** △ ★

Expose one consumer-facing channel namespace while retaining typed source provenance: ECU field, bit extraction, derived expression, application/system, accessory, or persisted accumulator. Widgets bind semantic ChannelIds, never raw offsets.

### EH-TS-039 — Compiled calculated-channel dependency graph

**Marker:** ✗ ★

Resolve derived-channel dependencies at profile/project load, reject/quarantine cycles and missing references, establish deterministic evaluation order, and record provenance for diagnostics. Do not evaluate arbitrary expression strings independently in each gauge.

### EH-TS-040 — Demand-driven runtime dependency activation

**Marker:** △ ★

Model UI/channel demand separately from ECU acquisition. Hidden dashboards should release presentation-only demand, while logger, diagnostics, warnings, analyzers, or shared derived channels may keep dependencies active.

### EH-TS-041 — First-class ChannelSample validity and session generation

**Marker:** △ ★

Runtime samples should carry value plus sequence/timestamp, session generation, and validity such as valid, stale, unavailable, invalid-calculation, or source-error. Derived channels propagate validity deliberately; missing data must not become a plausible fresh zero.

### EH-TS-042 — Separate acquisition, derived-publication, and visual refresh rates

**Marker:** △ ★

Keep ECU sampling, calculated-channel evaluation/publication, and Compose rendering independently rate-limited/coalesced. High-rate acquisition must not imply one recomposition per sample per gauge.

### EH-TS-043 — Evaluate shared derived channels once, fan out to consumers

**Marker:** ✗ ★

A derived channel referenced by multiple gauges/tools should be computed once per relevant update cycle and distributed through ChannelStore rather than re-evaluated for every widget.

### EH-TS-044 — Stateful channel taxonomy

**Marker:** ✗ ★

Distinguish stateless derived channels, stateful filters/estimators, session accumulators, and durable/project accumulators. Each category needs explicit initialization/reset/reconnect behavior.

### EH-TS-045 — Durable accumulators with time-gap guards and versioned identity

**Marker:** ✗ ★

Persist trip/integrated values atomically using stable semantic IDs and definition versions. Do not integrate offline/restart time gaps and do not use raw expression text as the only persisted identity.

### EH-TS-046 — Dashboard persistence independent from tune calibration

**Marker:** ✗ ★

Save user layouts, widget properties, and dashboard slots as project/UI state without dirtying the ECU tune. Loading/saving calibration must not silently overwrite dashboard customization.

### EH-TS-047 — Dashboard compatibility/schema provenance and migration

**Marker:** ✗ ★

Persist layout schema version, source profile identity/version, required semantic channels, optional fallbacks, and migration provenance. Missing/renamed channels should degrade per widget rather than crashing or silently rebinding to unrelated data.

### EH-TS-048 — Responsive semantic dashboard layout persistence

**Marker:** ✗ ★

Persist normalized/responsive placement constraints rather than raw device pixels so layouts can survive phone/tablet/orientation changes and future Compose layout-schema revisions.

### EH-TS-049 — Separate gauge presentation thresholds from WarningEngine authority

**Marker:** △ ★

Gauge-local warning/critical bands are presentation state. Authoritative warning incidents remain owned by the warning domain. They may share configured thresholds only through an explicit linkage, never by accidental UI authority.

### EH-TS-050 — Bounded short-term gauge history distinct from logger history

**Marker:** △ ★

Support peak-hold/trailing short history as bounded runtime/UI state without requiring logger-database queries. Logged history remains a separate long-retention domain.

### EH-TS-051 — Atomic dashboard and stateful-channel persistence

**Marker:** ★

Dashboard layouts and persisted accumulators must survive process death/interrupted writes using transactional storage. An interrupted UI-state save must never corrupt tune/profile authority.

## Live logging / recorded logs

### EH-TS-052 — Explicit LogSession state machine

**Marker:** △ ★

Model logging as a stateful domain such as Idle, Armed/WaitingForTrigger, Recording, Stopping/Finalizing, Completed, and Failed/Interrupted. File writers remain persistence backends, not owners of logging semantics.

### EH-TS-053 — Validated automatic logging trigger policy

**Marker:** ✗ ★

Compile automatic start/stop conditions against semantic ChannelIds and the shared expression engine. Stale/unavailable inputs must not spuriously trigger logging.

### EH-TS-054 — Named semantic LogSchema profiles

**Marker:** ✗ ★

Support an immutable/generated all-fields baseline plus user-defined project profiles using stable semantic channel identity, metadata and provenance.

### EH-TS-055 — Separate retained log schema from ECU acquisition plan

**Marker:** △ ★

Channels written to a log are not necessarily equal to bytes/channels acquired from the ECU. Let firmware capability and total consumer demand drive acquisition; let the log profile drive retention.

### EH-TS-056 — Definition-driven default logging schema

**Marker:** △ ★

Compile ordered default log entries, units, types, formatting and conditional availability from the resolved profile rather than rediscovering fields in Logger UI code.

### EH-TS-057 — Reuse ChannelStore/derived graph for logging

**Marker:** △ ★

Calculated channels logged by EpicHub must come from the same authoritative dependency graph used by dashboards/analyzers/other consumers. Do not implement a second logger formula engine.

### EH-TS-058 — Explicit stateful-channel behavior at log boundaries

**Marker:** ✗ ★

Define whether stateful values are continuous project state, log-session state, ECU-session state, or independently persisted accumulators. Starting/stopping a log must not implicitly reset unrelated persistent state.

### EH-TS-059 — Separate specialized/high-speed logger acquisition modes

**Marker:** ✗ ★

Trigger/tooth/composite/high-speed captures can have independent commands, readiness, timeouts, record geometry and cadence. Do not force them through normal RuntimeSnapshot scheduling.

### EH-TS-060 — Keep logger format neutral

**Marker:** ✓ ★

Preserve semantic samples/events above storage adapters so MLG, MDF4 and CSV/export do not own runtime channel identity, trigger policy, buffering or session lifecycle.

### EH-TS-061 — Explicit disconnect/gap/segment semantics

**Marker:** ✗ ★

An active log retains one user/log identity, but transport loss creates an explicit gap/event. Reconnect must revalidate session/profile identity before data can continue, and incompatible generations must never be silently spliced into one continuous segment.

### EH-TS-062 — Recoverable partial logs with bounded loss

**Marker:** ✗ ★

Design append/chunk/checkpoint/finalization behavior so process or power loss leaves a recoverable recording with bounded recent data loss. Exact strategy belongs to each file-format adapter.

### EH-TS-063 — Bounded logger backpressure with observable loss

**Marker:** ✗ ★

ECU acquisition must not block on storage, compression or export. Use bounded producer/consumer queues; if overload forces sample loss, record explicit dropped-sample/gap metadata and diagnostics.

### EH-TS-064 — Dedicated logger performance qualification

**Marker:** ★

Benchmark full-schema runtime logging, reduced profiles, specialized/high-rate captures, long sessions, logger+dashboard, logger+analyzer, and slow-storage/backpressure independently from Compose rendering budgets.

## Next research dependencies

The next TunerStudio slice should focus on **tune compare/diff and restore history**, particularly:

- comparison granularity and presentation;
- local tune vs ECU vs saved tune authority labels;
- ignored/non-comparable settings;
- table/curve difference visualization;
- offline comparison behavior;
- restore-point creation, retention and naming;
- firmware/profile compatibility across comparisons;
- rollback after bulk tuning or migration;
- large-tune comparison performance;
- integration with EpicHub's semantic `TuneDraft` history.

Still-open logging questions requiring controlled validation include exact normal-log behavior on cable removal/reconnect, writer flush cadence, partial-file repair behavior, normal-log bookmark/event representation, file naming/rotation, active-session profile-change semantics, and exact calculated-channel scheduling during logging.

Still-open dashboard/runtime questions include exact gauge invalid/stale rendering and timeout timing, exact visible-gauge repaint throttling, exact expression memoization behavior, history memory limits, dashboard migration when channels disappear/change type, and exact `GoingDead` / `InvalidState` lifecycle semantics.

Still-open table/runtime questions include exact live-write timing during rapid edits, key-repeat/debounce/coalescing, exact undo retention scope, and behavior of large paste/re-bin operations against a real/simulated controller.
