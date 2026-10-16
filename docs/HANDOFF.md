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

# Qualified state entering this handoff update — 2026-10-16

The previous fully qualified baseline on both `main` and `current-work` was:

`65a588d2b48dfaba460ccacfbfdb75ce2209c4ff`

Foundation quality run:

`38869610375`

That baseline already contained:

- generic definition/profile/runtime-signature authority;
- verified live tune-page acquisition and exact retained `TuneSourcePageImage`;
- one Rust-owned semantic `TuneSession` / `TuneDraft`;
- 2D + 3D peer editing over the same semantic state;
- source-relative RAM-write intent;
- definition-owned byte-patch planning;
- opaque guarded native write batches;
- constrained Android USB execution with precondition read, exact verification, write, ACK verification, and mandatory readback;
- pure, engine-owned, opaque-store, JNI, and Android verified-prefix reconciliation;
- explicit retained-RAM provenance / synchronization evidence without a generic `isSynced` boolean.

The authorization → physical USB transfer race was then closed on `current-work` at exact code SHA:

`1281db10dffa4d4bbd619dbf5d2028a75a552bff`

Foundation quality run:

`38871873302`

That run passed the complete Foundation gate:

- exact source SHA verification;
- Rust format;
- Rust Clippy with warnings denied;
- all Rust tests;
- Android JVM tests, including the new serialization/concurrency proofs;
- Android debug build;
- Android performance build;
- Android Macrobenchmark harness build;
- APK signature verification;
- production ARM native-library verification;
- physical benchmark-kit preparation;
- qualified artifact publication.

Therefore `1281db10dffa4d4bbd619dbf5d2028a75a552bff` is an immutable qualified code checkpoint.

This handoff-file update itself creates a newer `current-work` SHA. That newer SHA must also pass Foundation before `main` advances.

---

# Current architecture boundary

EpicHub is a native Kotlin/Jetpack Compose + Rust application. Kotlin owns Android/UI/platform integration. Rust owns semantic tuning state, ECU definition/profile authority, write geometry, reconciliation rules, retained RAM-source provenance, and native engine responsibilities.

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
exact verified transaction prefix
    ↓
native retained-source reconciliation + provenance update
    ↓
shared engine-authority serialization gate released
```

Keep these representations distinct:

1. exact retained source-page bytes from the last proven load/readback;
2. retained RAM provenance / verification evidence;
3. engine-owned semantic `TuneSession` and current `TuneDraft`;
4. planned source-relative ECU RAM mutations;
5. opaque guarded write transactions and their verified contiguous prefix;
6. persisted/Burned ECU state.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

A successful transaction proves only its exact readback-verified bytes. A fully verified batch can confirm the exact draft revision represented by that batch. Neither implies Burn/persistence.

---

# Retained RAM provenance semantics

The retained exact source image carries evidence describing what is actually known about ECU RAM rather than inferring synchronization from the current draft.

Current rules:

- verified live acquisition establishes the retained controller image as confirmed at draft revision `0`;
- a completely readback-verified guarded batch confirms that batch's exact draft revision;
- a partially verified batch advances only the exact verified transaction/range prefix;
- an incomplete reconciliation clears whole-draft confirmation while retaining the exact verified ranges that are still proven;
- current draft revision and confirmed RAM revision remain distinct;
- the last reconciliation records draft revision, verified transaction count, total transaction count, and completeness;
- provenance participates in retained-source authority/currentness, so changed certainty can stale an older opaque batch even if raw bytes did not change;
- byte reconciliation and provenance replacement remain atomic and fail closed;
- no generic whole-session `isSynced` boolean exists;
- no RAM provenance state implies nonvolatile persistence.

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

That gap is now closed for the current single-engine app architecture by `EngineAuthoritySerialization`, a process-local fair reentrant authority gate.

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

Read-only tune/profile operations are not serialized unnecessarily. USB disconnect/transport lifetime remains a transport concern and is represented as transport failure rather than being conflated with tune/profile/source authority mutation.

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
- stale profile generation, draft revision, or retained source fails closed;
- disconnect/reconnect invalidates unsupported live synchronization claims;
- Burn remains separate from RAM write.

---

# Immediate next work

Before exposing a user-facing RAM-write action, inspect and deliberately resolve one provenance edge case:

- when physical execution is interrupted with a verified transaction prefix of `0`, the current `UsbVerifiedGuardedWriteBatchExecutor` returns `Interrupted(0, ...)` without invoking native reconciliation;
- because the provenance model treats an incomplete write attempt as meaningful uncertainty even when zero transactions were verified, confirm whether this path must explicitly record incomplete-zero-prefix provenance before any UI can present RAM state.

Do not silently reinterpret this as success or whole-session synchronization.

After that edge case is resolved and qualified, proceed toward the smallest explicit user-facing **Write to RAM** workflow. That workflow should:

1. require a live, exactly selected controller/profile/source authority;
2. operate on the current exact draft revision;
3. plan through native source-relative/definition-owned authority only;
4. show clearly that the action writes volatile ECU RAM, not persistent storage;
5. execute through the already-qualified guarded + serialized USB path;
6. surface exact success / partial verification / failure state from native provenance rather than a guessed boolean;
7. keep Burn/persist as a later, separate capability and user action.

No Burn/persistence behavior should be added as part of the RAM-write UI slice.

---

# Development / qualification rule

- Make implementation, process, and documentation changes on `current-work`.
- Every meaningful `current-work` checkpoint must pass Foundation on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- Do not append new work directly to an older qualified SHA.
- Git history, closed PRs, and workflow runs are the archive; do not keep historical feature branches merely as documentation.
