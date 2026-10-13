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

## Next research dependencies

The next TunerStudio slice should focus on **runtime dashboards, channel subscription, calculated channels, and layout persistence**, particularly:

- hidden-dashboard subscription/unsubscription behavior;
- stale/invalid data across connection changes;
- calculated-channel dependency evaluation and failure propagation;
- dashboard/gauge persistence and project portability;
- runtime update throttling/caching;
- user-defined warnings/limits;
- compatibility when profile/channel definitions change.

Still-open table/runtime questions that require execution or deeper targeted tracing include exact live-write timing during rapid edits, key-repeat/debounce/coalescing, exact undo retention scope, and behavior of large paste/re-bin operations against a real/simulated controller.
