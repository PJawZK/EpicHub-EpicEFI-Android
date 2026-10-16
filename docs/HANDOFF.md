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

# Qualified state — 2026-10-16

The latest fully qualified implementation checkpoint is:

`d35c76a448679b081ca13215246acf9c4e202dc4`

Foundation quality run:

`38880159652`

That checkpoint completed the explicit app-facing RAM synchronization-state boundary. It contains:

- generic definition/profile/runtime-signature authority;
- verified live tune-page acquisition and exact retained `TuneSourcePageImage`;
- one Rust-owned semantic `TuneSession` / `TuneDraft`;
- 2D + 3D peer editing over the same semantic state;
- source-relative RAM-write intent;
- definition-owned byte-patch planning;
- opaque guarded native write batches;
- constrained Android USB execution with precondition read, exact verification, write, ACK verification, and mandatory readback;
- pure, engine-owned, opaque-store, JNI, and Android verified-prefix reconciliation;
- process-local engine-authority serialization across just-in-time authorization, physical USB transfer, and reconciliation;
- zero-verified-prefix interruption reconciliation, so even an interrupted first transaction records incomplete RAM provenance instead of silently preserving an older whole-draft confirmation;
- Rust-owned semantic RAM synchronization classification;
- one atomic versioned native RAM-state snapshot exposed to Android;
- fail-closed Kotlin decoding of the native state/evidence without a generic `isSynced` boolean.

The handoff content describing that checkpoint was qualified on exact repository SHA:

`9411e5c86fced27fefb9ea8e4fb5162f73c8b136`

Foundation quality run:

`38880935456`

That run passed the complete Foundation gate:

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

Therefore `9411e5c86fced27fefb9ea8e4fb5162f73c8b136` is the latest immutable qualified repository checkpoint entering the next work slice.

This small handoff metadata update creates a newer `current-work` SHA and must itself pass Foundation before `main` advances to it.

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
source-relative write intent
    ↓
definition-owned byte patches
    ↓
opaque guarded native RAM-write batch
    ↓
shared engine-authority serialization gate acquired
    ↓
just-in-time native batch/currentness authorization
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
shared engine-authority serialization gate released
    ↓
atomic native RAM synchronization-state snapshot for Android
```

Keep these representations distinct:

1. exact retained source-page bytes from the last proven load/readback;
2. retained RAM provenance / verification evidence;
3. engine-owned semantic `TuneSession` and current `TuneDraft`;
4. planned source-relative ECU RAM mutations;
5. opaque guarded write transactions and their verified contiguous prefix;
6. app-facing semantic RAM synchronization state derived by Rust from the retained evidence;
7. persisted/Burned ECU state.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

A successful transaction proves only its exact readback-verified bytes. A fully verified batch can confirm the exact draft revision represented by that batch. Neither implies Burn/persistence.

---

# Retained RAM provenance and synchronization semantics

The retained exact source image carries evidence describing what is actually known about ECU RAM rather than inferring synchronization from the current draft.

Current rules:

- verified live acquisition establishes the retained controller image as confirmed at draft revision `0`;
- a completely readback-verified guarded batch confirms that batch's exact draft revision;
- a partially verified batch advances only the exact verified transaction/range prefix;
- an incomplete reconciliation clears whole-draft confirmation while retaining the exact verified ranges that are still proven;
- a zero-verified-prefix interruption also performs native reconciliation, clears whole-draft confirmation, and records an incomplete reconciliation with zero verified transactions;
- current draft revision and confirmed RAM revision remain distinct;
- the last reconciliation records draft revision, verified transaction count, total transaction count, and completeness;
- provenance participates in retained-source authority/currentness, so changed certainty can stale an older opaque batch even if raw bytes did not change;
- byte reconciliation and provenance replacement remain atomic and fail closed;
- no generic whole-session `isSynced` boolean exists;
- no RAM provenance state implies nonvolatile persistence.

Rust classifies that evidence into five explicit app-facing states:

1. `FreshLiveControllerConfirmation` — a freshly acquired live-controller image confirms the current draft/baseline and no write reconciliation has occurred;
2. `CurrentDraftConfirmedInRam` — a complete readback-verified write reconciliation confirms the exact current draft revision in volatile RAM;
3. `LocalDraftAheadOfConfirmedRam` — RAM has an exact confirmed revision, but the local draft has advanced beyond it;
4. `PartiallyVerifiedInterruptedWrite` — an interrupted batch has a non-zero verified transaction prefix, but no whole-draft RAM confirmation remains;
5. `RamUnconfirmedAfterInterruptedWrite` — an interrupted batch has zero verified transactions, so the previous whole-draft RAM confirmation is invalidated and current RAM cannot be claimed as confirmed from retained evidence.

The state crosses JNI as one versioned, fixed-size native snapshot rather than several independently read fields. The snapshot keeps the semantic state and its evidence atomic:

- profile generation;
- acquisition source;
- current draft revision;
- optional confirmed RAM draft revision;
- verified write-range count;
- optional last reconciliation draft revision;
- verified transaction count;
- total transaction count;
- reconciliation completeness.

Kotlin decodes and validates this snapshot and fails closed on contradictory or unsupported evidence. Kotlin does not derive synchronization by comparing unrelated getters and does not own the classification rules.

`NativeEngine.readRamSynchronizationState()` is the app-facing read boundary. It returns no state when the active source has no applicable live RAM provenance.

---

# Authorization → USB-transfer serialization

The previously known race was:

```text
native currentness/JIT authorization
    ↓
return request bytes to Android
    ↓
[authority could mutate here]
    ↓
physical USB transaction
```

That gap is closed for the current single-engine app architecture by `EngineAuthoritySerialization`, a process-local fair reentrant authority gate.

The real guarded write executor holds the gate continuously across:

```text
JIT native authorization
→ physical USB pre-read/write/readback
→ verified-prefix native reconciliation
```

Current authority-mutating paths use the same gate:

- TuneSession Apply operations;
- Undo / Redo;
- live-controller source/session installation;
- imported Mega144H7 source/session installation;
- compiled-profile import;
- definition-bundle import;
- profile-registry close;
- NativeEngine source replacement / close paths covered by the current wrappers.

The gate is deliberately reentrant so authority helpers can compose without introducing lock-order inversions. JVM tests prove that a competing authority mutation cannot enter while a guarded transfer owns the gate and that transport + reconciliation occur inside one serializer scope.

Read-only tune/profile/RAM-state operations are not serialized unnecessarily. Rust engine/source authority locks still produce an atomic RAM-state snapshot. USB disconnect/transport lifetime remains a transport concern and is represented as transport failure rather than being conflated with tune/profile/source authority mutation.

Do not bypass the gated app-level mutation wrappers with direct bridge mutations in future code. If the architecture ever expands beyond the current single app-level `NativeEngine`, revisit the serialization ownership model rather than assuming this process-global gate scales unchanged.

---

# Proven invariants — do not regress

- exact full runtime signature is profile authority;
- USB VID/PID, product strings, family/board labels, endpoints, and signature prefixes are never profile/write authority;
- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision/history/Undo/Redo;
- 2D and 3D are peers over the same semantic state;
- previews are non-mutating until local Apply;
- exact retained pages remain raw byte authority beside the semantic session;
- failed source/profile replacement preserves the previous valid state;
- write geometry comes from the exact selected definition/bundle, never Android/UI offsets;
- Android cannot construct arbitrary page/offset writes;
- guarded writes require pre-read exact verification, ACK verification, and mandatory readback verification;
- every physical guarded write executes under the same serialization authority as active tune/profile/source mutations;
- partial write success is represented only as an exact verified prefix;
- zero-prefix interruptions still update native provenance and invalidate unsupported whole-draft RAM confirmation;
- stale profile generation, draft revision, or retained source fails closed;
- app-facing RAM synchronization state is Rust-classified from one atomic evidence snapshot rather than guessed in UI code;
- disconnect/reconnect invalidates unsupported live synchronization claims;
- Burn remains separate from RAM write.

---

# Immediate next work

Proceed toward the smallest explicit user-facing **Write to RAM** workflow. Do not add Burn/persistence in this slice.

The current integration inspection identifies `EpicHubApp` + `AndroidUsbLiveConnectionSessionOwner` / `LiveTuneConnectionOwner` as the existing live connection/profile/source ownership path. Reuse that path rather than creating a second connection or tune authority.

Before changing UI, trace the exact `AndroidUsbLiveConnectionSessionOwner` API and the Tuner shell state so the write workflow has one owner that already possesses or can safely expose:

- the active `NativeEngine`;
- the active profile registry / exact runtime signature authority;
- the retained live-controller source;
- the connected USB transaction channel;
- the current exact draft revision.

The Write-to-RAM workflow should then:

1. be available only when a live, exactly selected controller/profile/source authority exists;
2. target the current exact draft revision;
3. create the opaque guarded batch through native source-relative / definition-owned authority only;
4. never accept raw page, offset, byte geometry, or write request construction from UI code;
5. communicate clearly that the action changes **volatile ECU RAM** and is not persistent storage;
6. execute only through the already-qualified serialized guarded USB executor;
7. read `NativeEngine.readRamSynchronizationState()` after execution and surface exact native-owned outcomes such as current-draft confirmed, partial verification, or RAM unconfirmed after interruption;
8. preserve the original transport/authority/reconciliation failure details for diagnostics rather than collapsing everything into a generic failure;
9. avoid claiming synchronization merely because the transport returned success;
10. keep Burn/persist as a later, separate capability and explicit user action.

Prefer a small workflow/state owner that can later feed Compose over putting write orchestration directly inside a composable. Reuse the existing live connection/profile/session ownership instead of creating a second parallel connection or tune authority.

No Burn/persistence behavior should be added as part of the RAM-write UI slice.

---

# Development / qualification rule

- Make implementation, process, and documentation changes on `current-work`.
- Every meaningful `current-work` checkpoint must pass Foundation on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- Do not append new work directly to an older qualified SHA.
- Git history, closed PRs, and workflow runs are the archive; do not keep historical feature branches merely as documentation.
