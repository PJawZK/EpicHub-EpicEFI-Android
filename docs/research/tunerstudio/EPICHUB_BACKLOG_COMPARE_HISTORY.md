# EpicHub backlog extension — tune compare/diff and restore history

This file extends `EPICHUB_BACKLOG.md` for the tune compare/diff and restore-history slice. It is intentionally separate until the next backlog-maintenance consolidation to avoid replacing or truncating the existing EH-TS-001…064 master backlog.

## Tune compare / diff / restore history

### EH-TS-065 — Reusable semantic TuneDiff service

**Marker:** ✗ ★

Provide one comparison engine for working draft, ECU readback, last verified ECU state, named saved tune, restore checkpoint, pre-migration snapshot, and proposal/bulk-edit preview where compatible.

### EH-TS-066 — Comparison is observational; mutation is a separate plan

**Marker:** ★

A difference report must never implicitly replace local state or mutate the ECU. Resolve an inspected `TuneDiff` into an explicit guarded mutation plan only after the user chooses an authority/action.

### EH-TS-067 — Typed authority/provenance labels

**Marker:** ✗ ★

Every comparison source must identify its authority, profile/version, provenance and ECU session generation where applicable. Avoid ambiguous generic labels such as `current`, `saved`, or `original` once several authorities coexist.

### EH-TS-068 — Object-aware scalar/table/curve differences

**Marker:** ✗ ★

Compare semantic tune objects rather than raw addresses. Tables/curves should preserve object summary, axis changes, value/cell mask, deltas and 2D/3D presentation from one underlying diff model.

### EH-TS-069 — Import/migration normalization provenance

**Marker:** ✗ ★

When load/import validation clamps, converts or defaults a value, record source value, resulting value, rule/reason and severity so a later ECU diff can explain why the states diverge.

### EH-TS-070 — Durable comparable TuneCheckpoint model

**Marker:** ✗ ★

A history checkpoint must be independently inspectable, comparable, loadable/recoverable and attributable to a semantic event. History is distinct from transient undo/redo.

### EH-TS-071 — Event-driven checkpoint policy with semantic deduplication

**Marker:** ★

Create automatic checkpoints around meaningful state transitions such as authority replacement, migration, tune load, analyzer bulk apply or major persistence events. Skip redundant automatic checkpoints when semantic tune content is unchanged.

### EH-TS-072 — Bounded history retention with protected checkpoints

**Marker:** ✗ ★

Apply explicit storage/count/age policy while preferentially preserving user-pinned, pre-migration, and pre-destructive-transition checkpoints. Routine autosaves must not evict the only useful recovery state.

### EH-TS-073 — Guarded checkpoint restore transaction

**Marker:** ✗ ★

Before loading a checkpoint: verify compatibility, compare against current state, capture a pre-restore checkpoint if needed, construct a mutation plan, and distinguish local replacement from ECU live apply and ECU persistence.

### EH-TS-074 — First-class firmware/profile migration mapping

**Marker:** ✗ ★

Model migration as OldProfile snapshot → typed mapping/conversion → validation report → NewProfile draft → explicit review/diff. Preserve the immutable pre-migration snapshot and mapping provenance.

### EH-TS-075 — Cross-profile comparability classification

**Marker:** ✗ ★

Classify settings as directly comparable, renamed/mapped, converted/scaled, newly defaulted, removed/not representable, conditionally inactive, or ambiguous/manual-review-required. Do not compare incompatible profiles by raw position.

### EH-TS-076 — Hierarchical hashes/content-addressed checkpoint optimization

**Marker:** ★

Use semantic hashes as fast equality/filter stages at snapshot/object/table levels and deduplicate immutable checkpoint payloads where practical. Exact comparison remains authoritative when hashes indicate change or comparability is uncertain.

## Regression focus

The detailed compare/history research document contains the full regression catalog. High-priority gates include:

- diff inspection causes zero mutation;
- ECU-vs-local authority resolution is explicit and session-safe;
- table axis and Z/value differences remain distinct;
- normalization-generated differences retain provenance;
- restore loading cannot implicitly Burn;
- automatic history deduplicates unchanged state;
- pre-migration/pre-restore snapshots survive destructive transitions;
- renamed/removed/converted settings are classified explicitly;
- incompatible or ambiguous migration cannot silently apply to the ECU.
