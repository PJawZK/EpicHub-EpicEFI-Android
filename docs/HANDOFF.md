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

`a5ea66476561390e6f70775449c28c013fbdf7ce`

Foundation quality run:

`38895119391`

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

The preceding qualified boundaries that matter to this slice are:

- `0837b8840791507f53ae69e9b829dd6b3118cbf6`, Foundation `38883369329` — high-level current-draft RAM-write orchestration through the retained authorized USB channel;
- `1c0d58bdc8fb628f1e229a138aa84076f8601848`, Foundation `38893329201` — app-facing `RamWriteControlState` and exact outcome mapping;
- `a5ea66476561390e6f70775449c28c013fbdf7ce`, Foundation `38895119391` — visible Tuner RAM status/control workflow wired end to end.

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
- stale profile generation, draft revision, or retained source fails closed.

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

The Tuner RAM panel is deliberately gated by both:

- an active connected USB session; and
- `LiveTuneSourceLifecycleState.Live` after a fresh load.

Therefore disconnect/reconnect cannot re-expose retained RAM provenance as current live state until the controller has been freshly exact-authorized and loaded again.

---

# Qualified user-facing Write-to-RAM workflow

The first explicit volatile RAM workflow is now present in the Tuner workspace.

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

The control behavior is intentionally contextual:

- fresh/current confirmed RAM state: action disabled and presented as RAM current;
- local draft ahead of confirmed RAM: `Write draft to RAM`;
- partial or zero-prefix interrupted write: `Retry write to RAM`;
- no verified live RAM authority: write action unavailable;
- while a write is active, settings/profile/USB control actions that could compete with it are blocked at the app workflow boundary.

The result presentation preserves native/transport truth:

- complete reconciliation reports exact verified transaction count;
- interrupted writes report the reconciled verified prefix and original transport/authority failure;
- reconciliation rejection reports its exact native reason;
- unavailable post-write RAM state makes no synchronization claim.

Visible wording explicitly says the operation affects **volatile ECU RAM only** and that Burn/persistence is separate and not performed.

---

# Authorization → USB-transfer serialization

`EngineAuthoritySerialization` closes the former mutation race between native currentness authorization and physical USB I/O.

The real guarded executor holds the shared fair reentrant gate continuously across:

```text
JIT native authorization
→ physical USB pre-read/write/readback
→ verified-prefix native reconciliation
```

Current authority-mutating paths use the same gate, including Apply, Undo/Redo, source/session installation, profile imports/close, and the covered engine/source replacement paths.

Do not bypass these app-level gated mutation wrappers with direct bridge mutations.

---

# Repository/process state

- Only `main` and `current-work` should exist as branch refs.
- Open historical PRs were cleaned up; Git history and Actions remain the audit trail.
- Foundation is push-triggered on `current-work`; query Actions REST directly for authoritative run state rather than relying on `fetch_commit_workflow_runs`.
- Advance `main` only after the exact `current-work` head has a complete green Foundation run.

---

# Immediate next work

The volatile Write-to-RAM vertical slice is complete. Do **not** rebuild it.

Next, tighten the surrounding Tuner presentation before introducing Burn:

1. remove remaining stale offline-only wording in `TunerScreen` (for example the Authority card currently still describes the editor as an offline fixture even when the active session is live);
2. derive source/authority presentation from the existing native catalog/projection/live lifecycle rather than hardcoded labels;
3. keep the new RAM panel compact and context-sensitive so it guides the next meaningful action without adding visible complexity;
4. add focused UI/state tests where useful for the five RAM-state presentations and action enablement;
5. preserve local Apply vs RAM write separation in both wording and interaction;
6. only after this presentation/state cleanup should Burn/persistence be designed as a separate explicit capability with its own authority, verification, safety UX, and failure semantics.

Do not make Burn a side effect of Write to RAM and do not add a combined Apply/Write/Burn action.

---

# Development / qualification rule

- Make implementation, process, and documentation changes on `current-work`.
- Every meaningful `current-work` checkpoint must pass Foundation on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- Do not append new work directly to an older qualified SHA.
- Git history, closed PRs, and workflow runs are the archive; do not keep historical feature branches merely as documentation.
