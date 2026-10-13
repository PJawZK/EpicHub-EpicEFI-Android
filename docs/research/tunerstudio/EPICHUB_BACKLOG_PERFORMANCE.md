# EpicHub backlog extension — performance/cache/scaling

This file extends `EPICHUB_BACKLOG.md` with reviewed findings from `PERFORMANCE_CACHE_SCALING.md`.

It intentionally remains separate during the research phase to avoid destructive full-file backlog rewrites. Consolidate it with the other backlog extensions during the final research-maintenance pass.

## Performance / cache / scaling

### EH-TS-091 — Multi-rate runtime pipeline

**Marker:** ★

Model ECU acquisition, ChannelStore publication, derived-channel evaluation, Logger retention, Analyzer consumption, Dashboard rendering and specialized high-speed capture as independently budgeted rates. A high source rate must not imply one UI update per source sample.

### EH-TS-092 — Demand-derived ECU acquisition planning

**Marker:** ★

Build the active acquisition plan from semantic consumer demand plus derived dependency closure, then reduce transport work only where firmware/profile capabilities allow partial reads. Reduced retained/logged fields must never be misrepresented as guaranteed lower ECU traffic.

### EH-TS-093 — Generation-scoped derived-channel cache

**Marker:** ★

Evaluate each active stateless derived channel once per relevant source/session generation and fan the result out through ChannelStore. Do not cache live results across ECU-session/profile-generation boundaries.

### EH-TS-094 — Bounded diagnostic pre-failure history

**Marker:** ✓ ★

Keep always-on transport/session evidence in fixed-size structured rings and promote bounded pre-failure history into captures when needed. Full verbose communication tracing remains an explicit temporary diagnostic mode.

### EH-TS-095 — Bounded feature-local visual history

**Marker:** ★

Use small bounded rings for table lookup trails, gauge peaks/trails, warning pre-roll and similar UI history. Do not use long-term Logger storage as the hot path for short UI histories.

### EH-TS-096 — Revisioned sparse table publication

**Marker:** ★

Publish table revision/delta information for ordinary edits and keep high-rate lookup/selection overlays separate from the slow tune surface. Full-table snapshots remain valid for initial load, restore, comparison baselines and recovery—not as the only hot-path update mechanism.

### EH-TS-097 — Profile-valid transport batching below semantic edits

**Marker:** ★

Convert semantic bulk table/tune changes into contiguous bounded write blocks respecting profile block limits, page/region boundaries, persistence requirements and verification. Never make UI/JNI/USB call count scale directly with edited-cell count.

### EH-TS-098 — Compile-once immutable resolved profile generation

**Marker:** ★

Parse/validate large textual definition/profile inputs outside hot UI/runtime paths and expose immutable typed metadata keyed by project/profile generation. A newer global definition creates a migration opportunity, not silent reinterpretation/cache replacement.

### EH-TS-099 — Query/downsample long logs below the UI boundary

**Marker:** ★

Treat long logs as indexed persisted datasets. Analyzer requests bounded windows/aggregates/downsampled series from Rust/storage and receives bounded packed results; do not materialize all samples/channels into Kotlin objects for viewport rendering.

### EH-TS-100 — Meaning-preserving visual downsampling

**Marker:** ★

Use plotting reductions that preserve extrema/spikes or otherwise retain tuning/diagnostic meaning. Keep exact source samples available for cursor/detail queries and analysis; visual downsampling must not become analytical data loss.

### EH-TS-101 — Cache lifetime follows semantic authority

**Marker:** ★

Define explicit invalidation keys/lifetimes for profile metadata, tune/table revisions, derived samples, live lookup overlays, diagnostics, Analyzer queries, TuneDiff results and migration plans. Reject one generic global cache with implicit invalidation.

### EH-TS-102 — Workload-specific production performance qualification

**Marker:** ★

Benchmark real user journeys independently: Dashboard telemetry, 2D/3D Tuner live use, rapid editing, large transforms, Logger, Logger+Dashboard, Logger+Analyzer, long-log browsing, large tune compare, migration, diagnostics under faults and specialized high-speed capture. Preserve the existing physical benchmark foundation, but do not resume placeholder-UI optimization as a project phase.

## Consolidation status

The numbered research backlog now spans:

- `EPICHUB_BACKLOG.md`: EH-TS-001…064
- `EPICHUB_BACKLOG_COMPARE_HISTORY.md`: EH-TS-065…076
- `EPICHUB_BACKLOG_MIGRATION.md`: EH-TS-077…090
- this file: EH-TS-091…102

No production implementation is implied by numbering; these remain reviewed research proposals until accepted into implementation work.
