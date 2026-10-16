# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the exact `main`, `current-work`, and Foundation state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Branch model after the 2026-10-16 consolidation:

- `main` = latest fully qualified baseline.
- `current-work` = the only active development branch and must stay based on / ahead of `main`.
- Historical PRs, workflow runs, and commit SHAs remain the audit trail; historical development branches are no longer part of the working model and are to be removed after consolidation.

Do not recreate a long-lived stack of per-slice branches. New work is performed on `current-work`, qualified there, and accepted milestones advance `main`.

---

# Qualified baseline — 2026-10-16

`main` was advanced to exact qualified SHA:

`8f7bd82dfaf967d7576b37482af5f9a4b3699fc7`

Foundation quality run:

`38858098167`

That run passed the complete current Foundation gate:

- exact source SHA verification;
- Rust format;
- Rust Clippy with warnings denied;
- all Rust tests;
- Android JVM tests;
- Android debug build;
- Android performance build;
- Android Macrobenchmark harness build;
- APK signature verification;
- production ARM native-library verification;
- physical benchmark-kit preparation;
- qualified artifact publication.

This qualified baseline contains the complete stacked Phase-2 history through former PR #81, including the guarded RAM-write chain and the pure verified retained-source reconciliation primitive.

---

# Current product/architecture boundary

EpicHub is a native Kotlin/Jetpack Compose + Rust application. Kotlin owns Android/UI/platform integration. Rust owns semantic tuning state, ECU definition/profile authority, write geometry, reconciliation rules, and other native engine responsibilities.

The current deep vertical slice is:

```text
profile package / ECU definition
    ↓
coherent compiled profile + protocol bundle
    ↓
explicit USB candidate + permission + connection
    ↓
definition-owned runtime identity probe
    ↓
exact full runtime signature
    ↓
exact bundle/profile selection
    ↓
verified live page acquisition
    ↓
retained exact TuneSourcePageImage
    +
atomic semantic TuneSession install
    ↓
engine-owned TuneDraft / 2D + 3D editor
    ↓
source-relative write intent
    ↓
guarded native RAM-write batch
    ↓
Android USB transport execution
    ↓
pre-read exact verification
    ↓
write + firmware ACK verification
    ↓
mandatory readback exact verification
    ↓
pure verified-prefix retained-source reconciliation
```

Keep the following representations distinct:

1. exact retained source-page bytes from the last proven load/readback;
2. engine-owned semantic `TuneSession` and current `TuneDraft`;
3. planned source-relative ECU RAM mutations;
4. guarded write transactions and their verified contiguous prefix;
5. persisted/Burned ECU state.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

A successful RAM-write transaction proves only the exact readback-verified range. It does not imply whole-session synchronization and does not imply Burn/persistence.

---

# What former PR #81 added

`reconcile_verified_transaction_prefix()` is implemented and qualified as a pure rule over retained source images and guarded transactions.

It:

- advances only an exact contiguous prefix reported as fully readback-verified;
- requires valid 1-based page geometry, bounded offsets/ranges, non-empty equal before/after lengths, and exact retained `before` bytes;
- returns a new owned `TuneSourcePageImage` rather than mutating the original;
- fails closed without partially advancing the retained image if any transaction in the requested prefix is invalid or stale;
- permits partial-success recovery when earlier transactions are proven but a later transaction fails.

It does **not** yet mutate active engine state or consume Android USB execution results directly.

---

# Immediate next implementation work

The next slice is **engine-owned post-write retained-source reconciliation**.

Implement the smallest authority-preserving integration that:

1. takes the exact native guarded batch and the exact number of fully verified transactions produced by transport execution;
2. reacquires the selected engine/profile/source authority under native synchronization;
3. revalidates exact runtime/profile generation, draft revision, and retained-source currentness before changing engine state;
4. invokes the already-qualified pure verified-prefix reconciliation rule;
5. atomically replaces only the retained `TuneSourcePageImage` with the reconciled image;
6. preserves the current `TuneSession`, `TuneDraft`, edit history, and draft revision;
7. makes no claim that unverified/later transactions reached RAM;
8. makes no whole-session synchronization claim unless separately proven;
9. does not add Burn or persistence behavior;
10. does not add user-facing write controls yet.

After that, the likely sequence is:

- explicit RAM synchronization/provenance state;
- stronger mutation/write serialization across the authorize → USB-transfer boundary;
- user-facing explicit write-to-RAM workflow;
- Burn/persist later as a separate capability/action.

---

# Proven invariants — do not regress

- exact full runtime signature is profile authority;
- USB VID/PID, product strings, board/family names, endpoints, and signature prefixes are never profile/write authority;
- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision/history/Undo/Redo;
- 2D and 3D are peers over the same semantic state;
- preview remains non-mutating until Apply;
- raw retained source pages live beside, not inside, the semantic tune model;
- failed source/profile replacement preserves the previous valid state;
- write geometry comes from the exact selected definition/bundle, not Android/UI offsets;
- guarded writes use pre-read exact verification, ACK verification, and mandatory readback verification;
- partial write success is represented as a verified prefix, never rounded up to whole-batch success;
- disconnect/reconnect invalidates any unsupported live synchronization claim;
- Burn remains separate from RAM write.

---

# Development/qualification rule after consolidation

- Make implementation/process/documentation changes on `current-work`.
- Every meaningful current-work checkpoint must pass Foundation on its exact SHA before it can be called qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- Do not append new work directly to an older qualified SHA.
- Git history, closed PRs, and workflow runs are the archive; do not keep historical feature branches merely as documentation.

The current Foundation workflow is intentionally being moved from `phase2/**` push qualification to `current-work` push qualification as part of this consolidation.
