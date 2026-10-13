# EpicHub improvement backlog from TunerStudio behavioral research

This is a review backlog, not an implementation queue. Items remain evidence-derived proposals until explicitly accepted into EpicHub architecture or runtime work.

## Logging additions from `LIVE_LOGGING_RECORDED_LOGS.md`

The main backlog previously ran through EH-TS-051. The logging slice adds the following numbered review items.

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

## Prior backlog authority

EH-TS-001 through EH-TS-051 remain defined by the previous connection, persistence, table/editor, and dashboard/runtime-channel research slices. Their authoritative detailed rationale remains in the corresponding research documents and prior history of this file.

## Next research dependency

Proceed to tune compare/diff and restore history. Logging-specific runtime questions still requiring controlled validation include cable-loss continuation, exact flush cadence, partial-file repair behavior, normal-log bookmark/event representation, file rotation/naming, and active-session profile-change semantics.
