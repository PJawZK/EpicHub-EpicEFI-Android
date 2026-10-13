# EpicHub improvement backlog from TunerStudio behavioral research

This is a review backlog, not an implementation queue. Items remain evidence-derived proposals until explicitly accepted into EpicHub architecture or runtime work.

## Connection / identity / project authority

### EH-TS-001 — Explicit session identity state machine

**Marker:** ✗ ★

Separate:

- discovered transport candidate;
- opened transport;
- queried ECU identity;
- compatible semantic profile resolved;
- project/profile mismatch;
- unsupported profile;
- offline project session;
- fully online session.

**Reason:** TunerStudio mature behavior treats firmware mismatch as a first-class workflow rather than a generic connection error.

### EH-TS-002 — Durable project profile identity

**Marker:** △ ★

Persist enough profile identity/version/provenance with a project to reopen it under the same semantic interpretation after application updates.

Do not silently reinterpret an existing project through the newest global definition.

### EH-TS-003 — Typed resolved ECU profile in Rust

**Marker:** ★

Compile/resolve firmware definition input into an immutable validated runtime model containing protocol geometry, runtime channels, tuning-memory semantics, limits, and capabilities.

UI consumers use semantic IDs/operations, not raw textual offsets.

### EH-TS-004 — Restricted unsupported/mismatch diagnostics mode

**Marker:** ★

If an ECU responds but compatibility is not proven, retain useful identity/transport diagnostics without granting normal tune-write authority.

Prefer this over a generic unrestricted "connect anyway" path.

### EH-TS-005 — Tune-sync state separate from connection state

**Marker:** ✗ ★

After identity/profile compatibility is established, independently determine whether local working tune and controller state agree.

Connection success must not imply tune equality.

## Persistence / recovery

### EH-TS-006 — Durable working tune

**Marker:** ✗ ★

Define local working tune persistence independently from:

- controller RAM state;
- controller nonvolatile/Burn state;
- named tune exports;
- restore/history checkpoints.

Avoid a single ambiguous "saved" state.

### EH-TS-007 — Immutable checkpoint before destructive reconciliation

**Marker:** ✗ ★

Before replacing local state with controller state, capture a recoverable pre-transition checkpoint and diff metadata.

### EH-TS-008 — Atomic project persistence

**Marker:** △ ★

Use transactional/atomic persistence so process death cannot leave project metadata, tune state, or profile authority from mixed generations.

TunerStudio's `.bak`/`.bkup` evidence motivates the failure case; EpicHub should solve it with stronger primitives.

## Failure / reconnect

### EH-TS-009 — Session-generation ownership for ECU operations

**Marker:** ★

Every multi-step ECU operation executes against one session generation and cancels if the connection is replaced or lost.

An operation started against ECU/session A must never silently continue against session B after reconnect.

### EH-TS-010 — Bounded reconnect/backoff and reversible candidate suppression

**Marker:** △ ★

Prevent reconnect storms or repeated problematic automatic opens. Preserve an explicit user retry/reset path.

Adapt this to Android USB permission/device behavior rather than copying desktop COM-port heuristics.

### EH-TS-011 — Structured transport/session evidence in DiagnosticHub

**Marker:** ✓ ★

Feed bounded structured events into the existing Kotlin/Rust diagnostic rings:

- discovery;
- permission;
- endpoint selection;
- open/close;
- identity query;
- profile resolution;
- runtime read timeout/error;
- reconnect/backoff;
- session generation changes.

Do not require unbounded raw packet logging for normal post-failure diagnosis.

## Offline workflow

### EH-TS-012 — First-class offline project mode

**Marker:** ✗ ★

Allow tune/project editing without a connected ECU while making live-only capabilities explicitly unavailable.

Offline values must not masquerade as fresh runtime data.

### EH-TS-013 — Revalidation after offline edits

**Marker:** ★

After offline changes, reconnect must re-establish ECU identity/profile compatibility and tune-sync state before any controller mutation.

## UX

### EH-TS-014 — Explain mismatch type and safe next actions

**Marker:** ★

When connection cannot become fully online, UI should say *why*:

- transport unavailable;
- permission denied;
- ECU did not answer identity query;
- unsupported firmware;
- project profile mismatch;
- tune state differs.

Offer actions appropriate to that state instead of one generic "connection failed" dialog.

### EH-TS-015 — Distinguish stale runtime from valid zero

**Marker:** ★

A disconnect must invalidate/freeze/mark telemetry according to a deliberate data-validity policy. Never convert loss of data into plausible zero values.

## Next research dependencies

The following backlog areas require the next TunerStudio slices before they can be specified confidently:

- exact RAM-write vs Burn/nonvolatile transaction model;
- auto-Burn safety invariant and its UI coupling;
- controller/local diff granularity and comparison UX;
- restore-point retention policy;
- reconnect timing/backoff details;
- dashboard stale-data presentation;
- page-level write ordering and cross-page Burn behavior;
- table-edit undo/redo and apply semantics.
