# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the exact `main`, `current-work`, and Foundation state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Branch model:

- `main` = latest fully qualified baseline.
- `current-work` = the only active development branch and must remain based on / ahead of `main`.
- Historical commits, closed PRs, and workflow runs are the audit trail; do not recreate long-lived phase/feature branches.
- Every meaningful `current-work` checkpoint must pass the complete Foundation workflow on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.

---

# Latest qualified implementation — 2026-10-16

The latest qualified implementation checkpoint is:

`4ea7a08f77224399cce2518e7b80e3de42321ada`

Foundation quality run:

`38926787327`

That exact SHA passed the complete Foundation gate:

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
- all qualified artifact publication.

`main` was advanced to that exact qualified SHA before this documentation update.

Important preceding checkpoints remain part of the same qualified history:

- `79f27827f4fa6588141e7a196907c3bc4c319a50`, Foundation `38900643084` — source-aware Tuner authority presentation and direct active-session Tuner usage;
- `a5ea66476561390e6f70775449c28c013fbdf7ce`, Foundation `38895119391` — visible volatile Write-to-RAM workflow;
- earlier qualified history contains guarded write planning/execution/reconciliation, RAM provenance, zero-prefix interruption handling, and transfer serialization.

This handoff update creates a newer `current-work` SHA and must itself pass Foundation before `main` advances again.

---

# Current architecture boundary

EpicHub is a native Kotlin/Jetpack Compose + Rust application. Kotlin owns Android/UI/platform integration and presentation state. Rust owns semantic tuning state, ECU definition/profile authority, table catalog authority, write geometry, reconciliation rules, retained RAM-source provenance, RAM synchronization classification, and native engine responsibilities.

Current deep vertical slice:

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
verified live page acquisition OR imported/offline source
    ↓
atomic engine-owned TuneSession install
    ↓
native semantic table catalog + source-aware projection ABI
    ↓
TunerTableSelectionOwner
    ↓
selected semantic table ID from the current native catalog
    ↓
2D + 3D peer editor over the same engine-owned TuneDraft/history
    ↓
local Apply / Undo / Redo
    ↓
Rust-owned current draft revision
    ↓
explicit Write draft to RAM action when verified live authority exists
    ↓
opaque guarded native RAM-write batch
    ↓
serialized USB precondition/write/ACK/readback
    ↓
verified-prefix reconciliation + RAM provenance
    ↓
atomic RAM synchronization-state snapshot
```

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

The existing Write-to-RAM workflow changes volatile ECU RAM only. It does not Burn/persist the tune.

---

# Catalog-driven multi-table Tuner — qualified

The previous single-table limitation is complete. Do **not** rebuild this slice.

`TunerTableSelectionOwner` now provides presentation-level semantic table selection over the one active engine-owned `TuneSession`.

Rules:

- available tables come only from the Rust/native `TunerTableCatalog`;
- selection keys are semantic native table IDs, never hardcoded ECU-family IDs, raw offsets, names, or transport metadata;
- selected table identity is Kotlin presentation state only and does not create a second tune authority;
- every projection/edit operation resolves the current native engine handle and validates the selected semantic table against the current native catalog;
- if a replacement TuneSession no longer contains the previous selection, the owner falls back deterministically to the current native catalog default entry;
- explicit selection of a table ID outside the current native catalog fails closed;
- reordering catalog entries does not change a valid semantic selection;
- all tables share the same Rust-owned draft revision/history; switching tables does not create per-table drafts or histories;
- Undo/Redo remains TuneSession-wide and the currently selected table is reprojected afterward;
- 2D and 3D remain peer views of the selected semantic table.

The Tuner UI exposes the catalog progressively:

- one compact table chip shows the selected native table name and dimensions;
- the dropdown is shown only when more than one catalog table exists;
- the dropdown lists only native catalog entries and marks the current entry active;
- the Tuner is not turned into a permanently expanded table list.

Table-switch safety is explicit:

- selection rectangle is cleared;
- persistent extend-selection mode is cleared;
- an active brush stroke is cleared;
- pending preview is discarded;
- Apply additionally checks that the preview semantic table ID still equals the currently displayed table ID and rejects stale previews if not.

Focused JVM tests prove semantic selection, invalid-ID rejection, fallback after session replacement, catalog reorder stability, interaction reset, and stale-preview table-ID rejection.

`OfflineTunerFixture` remains compatibility-only. New Tuner UI work must not depend on it.

---

# Tune source presentation authority

The Tuner must not infer source authority from USB VID/PID, family labels, table names, or connection-looking UI state.

The projection ABI carries the Rust-owned TuneSession origin and Kotlin validates exact source/flag combinations before UI presentation:

- offline fixture;
- imported file;
- verified live controller.

Unknown source origins, unknown source bits, or contradictory combinations fail closed.

A live-controller projection means the semantic session came from a verified live load. Local Apply still changes only the draft. RAM status and Write-to-RAM availability come from the separate qualified RAM provenance/lifecycle path.

---

# Generic ECU / authority invariants

Do not regress these:

- exact full runtime signature is profile authority;
- USB VID/PID, product strings, family/board labels, endpoints, table names, and signature prefixes are not write/profile authority;
- generic ECU support remains definition/profile/protocol driven;
- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision/history/Undo/Redo;
- 2D and 3D are peers over the same semantic state;
- previews are non-mutating until local Apply;
- exact retained pages remain raw byte authority beside semantic state;
- write geometry comes from the exact selected definition/bundle, never Android/UI offsets;
- Android cannot construct arbitrary page/offset writes;
- stale profile generation, draft revision, retained source, or semantic table identity fails closed;
- source presentation comes from validated native semantic authority, not transport heuristics.

---

# Volatile RAM workflow invariants

Rust-owned RAM provenance remains authoritative. Important rules:

- fresh verified live acquisition establishes the acquired RAM baseline;
- a fully readback-verified write can confirm the exact draft revision represented by that batch;
- partial success advances only the exact verified prefix/ranges;
- zero-prefix interruption invalidates unsupported whole-draft confirmation;
- ACK alone never proves final RAM truth;
- mandatory readback is required for confirmation;
- disconnect/reconnect cannot make retained provenance appear freshly live without a new exact-authorized load;
- Burn/persistence is not implied by any RAM state.

`EngineAuthoritySerialization` continues to span JIT native authorization through physical USB transfer and verified-prefix reconciliation, while active tune/profile/source mutation paths share the same gate.

---

# Repository/process state

- Only `main` and `current-work` should exist as branch refs.
- Historical PRs are closed; Git history and Actions are the audit trail.
- Foundation is push-triggered on `current-work`; query Actions REST directly for authoritative run state.
- Advance `main` only after the exact `current-work` head has a complete green Foundation run.

---

# Immediate next work

The source-aware Tuner authority slice and catalog-driven multi-table selection are complete. Do **not** rebuild them.

The next correctness boundary is **TuneSession/source lifecycle coherence while the Tuner remains composed**.

Current observation:

- `TunerScreen` remembers its initial `TunerTableSelectionOwner`, catalog selection, and projection when the composable is created;
- a verified live load, imported-source replacement, or other successful TuneSession replacement can occur while the Tuner workspace remains mounted;
- native authority changes atomically, but the already-composed Tuner presentation does not yet have an explicit session-generation/source-change signal that forces it to refresh its catalog/projection and discard presentation state from the previous session.

Proceed with the smallest lifecycle-coherence slice before Burn work:

1. trace the exact successful TuneSession/source replacement paths and identify an existing native/session generation or introduce the smallest read-only generation token needed for presentation invalidation;
2. make Tuner catalog/projection presentation refresh when the active TuneSession authority changes while the screen remains composed;
3. preserve semantic table selection only when the same semantic ID exists in the replacement native catalog; otherwise fall back to the catalog default;
4. always discard pending preview, rectangle selection, active brush stroke, and other session-relative transient edit state on a TuneSession replacement;
5. do not reset state merely because unrelated runtime telemetry changes;
6. do not create a second Kotlin tune/session authority or poll raw ECU geometry;
7. add focused tests proving same-session edits do not spuriously reset presentation, while genuine session replacement does;
8. preserve source-aware presentation and volatile RAM lifecycle gating after the refresh.

After that coherence boundary is qualified, reassess the remaining prerequisites for **Burn/persistence**. Burn must remain a separate definition-owned capability with explicit safety UX, verification, failure semantics, and no combined Apply/Write/Burn action.

---

# Development / qualification rule

- Make implementation, process, and documentation changes on `current-work`.
- Every meaningful `current-work` checkpoint must pass Foundation on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- Do not append new work directly to an older qualified SHA.
- Git history, closed PRs, and workflow runs are the archive; do not keep historical feature branches merely as documentation.
