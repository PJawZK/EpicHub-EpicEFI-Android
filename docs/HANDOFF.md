# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the exact `main`, `current-work`, and Foundation state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Branch model:

- `main` = latest fully qualified baseline.
- `current-work` = the only active development branch and must remain based on / ahead of `main`.
- Historical commits, closed PRs, and workflow runs are the audit trail; do not recreate a long-lived stack of phase/feature branches.

Every meaningful `current-work` checkpoint must pass the complete Foundation workflow on its exact SHA before it can be called qualified. Advance `main` only to an already-qualified `current-work` SHA.

---

# Latest qualified implementation — 2026-10-16

The latest fully qualified implementation checkpoint before this handoff update is:

`2a8032ca22200184fa294f544b95f9843feb5b99`

Foundation quality run:

`38899348184`

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

The preceding qualified boundary for the visible volatile-RAM workflow remains:

- `a5ea66476561390e6f70775449c28c013fbdf7ce`, Foundation `38895119391` — visible Tuner RAM status/control workflow wired end to end.

The current qualified source-presentation slice adds:

- source-aware Tuner Authority presentation derived from the native table projection rather than hardcoded offline labels;
- explicit presentation for verified live-controller source, imported tune source, offline fixture, and unavailable TuneSession;
- direct `TunerScreen` use of `ActiveTunerTable` rather than the legacy `OfflineTunerFixture` compatibility name;
- fail-closed Kotlin ABI validation for the Rust-defined projection `originCode` + source-flag combinations;
- rejection of unknown source origins, unknown flag bits, and contradictory origin/flag combinations before the UI can label a source;
- focused JVM tests for source presentation and ABI validation.

A failed intermediate run (`38898814313`) is historical evidence only: production Kotlin compiled, but the newly added tests initially used `kotlin.test` while this project uses JUnit. The test imports were corrected; the final exact SHA above is the qualified authority.

This handoff update creates a newer `current-work` SHA. It must pass Foundation before `main` advances again.

---

# Current architecture boundary

EpicHub is a native Kotlin/Jetpack Compose + Rust application. Kotlin owns Android/UI/platform integration. Rust owns semantic tuning state, ECU definition/profile authority, write geometry, reconciliation rules, retained RAM-source provenance, RAM synchronization classification, and native engine responsibilities.

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
verified live page acquisition
    ↓
exact retained TuneSourcePageImage + RAM provenance
    +
atomic semantic TuneSession install
    ↓
native tune catalog + source-aware table projection ABI
    ↓
ActiveTunerTable / TunerScreen
    ↓
engine-owned TuneDraft / 2D + 3D editor
    ↓
local Apply / Undo / Redo
    ↓
Rust-owned current draft revision
    ↓
explicit Tuner Write draft to RAM action
    ↓
high-level live connection owner
    ↓
source-relative write intent
    ↓
definition-owned byte patches
    ↓
opaque guarded native RAM-write batch
    ↓
shared EngineAuthoritySerialization gate
    ↓
JIT native currentness authorization
    ↓
USB precondition read → exact verify
    ↓
write → firmware ACK verify
    ↓
mandatory readback → exact verify
    ↓
exact verified transaction prefix, including zero-prefix interruption
    ↓
native retained-source reconciliation + provenance update
    ↓
atomic native RAM synchronization-state snapshot
    ↓
Tuner RAM status/control panel
```

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

The visible RAM workflow changes only volatile ECU RAM. It does not Burn/persist the tune.

---

# Tune source presentation authority

The Tuner must not infer source authority from USB VID/PID, family labels, table names, or connection-looking UI state.

The active table projection ABI carries the Rust-owned TuneSession origin:

- origin `1` + `OFFLINE | FIXTURE` = offline fixture;
- origin `2` + `OFFLINE` = imported file;
- origin `3` + no source flags = verified live-controller TuneSession.

Kotlin validates those exact combinations before exposing `TunerTableProjection`. Unknown origins, unknown source bits, or contradictory combinations fail closed.

`TunerAuthorityPresentation` then maps only the validated projection into user-facing wording. It does not create authority.

Current presentation semantics:

- live controller: verified live-controller source, but local Apply still changes only the semantic draft;
- imported tune: local/offline source with no live RAM authority implied;
- fixture: test/local source only;
- unavailable projection: no active TuneSession claim.

The table subtitle uses the same validated source presentation.

`OfflineTunerFixture` still exists only as a compatibility alias for older callers/tests. New Tuner UI code should use `ActiveTunerTable` or a future catalog/table-selection owner, not add new dependencies on the compatibility name.

---

# Generic ECU / authority invariants

Do not regress these:

- exact full runtime signature is profile authority;
- USB VID/PID, product strings, family/board labels, endpoints, and signature prefixes are not profile/write authority;
- generic ECU support remains definition/profile/protocol driven rather than hardcoded per ECU path;
- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision/history/Undo/Redo;
- 2D and 3D are peers over the same semantic state;
- previews are non-mutating until local Apply;
- exact retained pages remain raw byte authority beside the semantic session;
- write geometry comes from the exact selected definition/bundle, never Android/UI offsets;
- Android cannot construct arbitrary page/offset writes;
- stale profile generation, draft revision, or retained source fails closed;
- source presentation must be derived from validated native semantic authority, not transport heuristics.

---

# RAM provenance and synchronization semantics

Rust classifies retained evidence into five explicit app-facing states:

1. `FreshLiveControllerConfirmation`
2. `CurrentDraftConfirmedInRam`
3. `LocalDraftAheadOfConfirmedRam`
4. `PartiallyVerifiedInterruptedWrite`
5. `RamUnconfirmedAfterInterruptedWrite`

The state crosses JNI as one versioned fixed-size native snapshot. Kotlin validates that snapshot and does not derive a generic `isSynced` flag from unrelated values.

`NativeEngine.readRamSynchronizationState()` is the app-facing read boundary.

Important rules:

- fresh verified live acquisition establishes RAM confirmation for the acquired baseline;
- a completely readback-verified batch can confirm the exact draft revision represented by that batch;
- partial success advances only the exact verified prefix/ranges;
- an interrupted zero-prefix write still invalidates unsupported whole-draft confirmation;
- ACK alone never proves final RAM truth;
- mandatory readback remains the confirmation authority;
- Burn/persistence is not implied by any RAM state.

The Tuner RAM panel is deliberately gated by both an active connected USB session and `LiveTuneSourceLifecycleState.Live` after a fresh load. Disconnect/reconnect therefore cannot re-expose retained RAM provenance as current live state until the controller has been freshly exact-authorized and loaded again.

---

# Qualified user-facing Write-to-RAM workflow

Ownership path:

```text
Tuner panel
  → EpicHubApp state/action
  → AndroidUsbLiveConnectionSessionOwner
  → retained connected LiveTuneConnectionOwner + USB channel
  → existing qualified native guarded write executor
  → verified-prefix reconciliation
  → NativeEngine.readRamSynchronizationState()
  → Tuner panel
```

The UI never receives raw page numbers, offsets, write request bytes, or definition geometry.

Control behavior:

- fresh/current confirmed RAM state: action disabled and presented as RAM current;
- local draft ahead of confirmed RAM: `Write draft to RAM`;
- partial or zero-prefix interrupted write: `Retry write to RAM`;
- no verified live RAM authority: write action unavailable.

Visible wording explicitly says the operation affects **volatile ECU RAM only** and that Burn/persistence is separate and not performed.

---

# Authorization → USB-transfer serialization

`EngineAuthoritySerialization` closes the mutation race between native currentness authorization and physical USB I/O.

The real guarded executor holds the shared fair reentrant gate continuously across:

```text
JIT native authorization
→ physical USB pre-read/write/readback
→ verified-prefix native reconciliation
```

Current authority-mutating paths use the same gate, including Apply, Undo/Redo, source/session installation, profile imports/close, and covered engine/source replacement paths.

Do not bypass these app-level gated mutation wrappers with direct bridge mutations.

---

# Repository/process state

- Only `main` and `current-work` should exist as branch refs.
- Open historical PRs were cleaned up; Git history and Actions remain the audit trail.
- Foundation is push-triggered on `current-work`; query Actions REST directly for authoritative run state rather than relying on `fetch_commit_workflow_runs`.
- Advance `main` only after the exact `current-work` head has a complete green Foundation run.

---

# Immediate next work

The stale offline-only Tuner authority presentation is complete. Do **not** rebuild it.

The next concrete Tuner limitation is catalog/table selection:

- `ActiveTunerTable.activeEntry()` currently chooses the first native catalog entry;
- that first entry is deterministic because the native catalog is sorted by semantic table ID;
- this preserves the old single-table screen, but it is not yet a real multi-table tuning workflow.

Proceed with the smallest catalog-driven multi-table selection slice before Burn work:

1. inspect `TunerTableCatalog`, catalog ABI, `ActiveTunerTable`, and current Tuner state ownership;
2. add explicit semantic table selection based only on native catalog entries — never hardcoded table IDs/names or ECU-family branching;
3. preserve one engine-owned TuneSession and shared draft/history across table changes;
4. ensure changing the selected table cancels/rejects stale previews/selections rather than accidentally applying them to another table;
5. keep 2D and 3D as peer views of the selected semantic table;
6. design the selector to remain compact and progressive rather than turning the Tuner into a large always-visible table list;
7. retain `OfflineTunerFixture` only as compatibility where still required; do not use it for new UI work;
8. add focused tests for table-selection authority and stale-selection/preview behavior.

Do not begin Burn/persistence until this catalog-driven Tuner flow is coherent. Burn remains a later, separate explicit capability with its own definition-owned authority, verification, safety UX, and failure semantics.

Do not make Burn a side effect of Write to RAM and do not add a combined Apply/Write/Burn action.

---

# Development / qualification rule

- Make implementation, process, and documentation changes on `current-work`.
- Every meaningful `current-work` checkpoint must pass Foundation on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- Do not append new work directly to an older qualified SHA.
- Git history, closed PRs, and workflow runs are the archive; do not keep historical feature branches merely as documentation.
