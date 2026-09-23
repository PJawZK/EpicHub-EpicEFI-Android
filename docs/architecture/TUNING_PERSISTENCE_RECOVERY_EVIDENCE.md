# Tuning Persistence / Recovery Evidence

## Status

Phase 1 source archaeology against the qualified/evolved EpicDash-JZ tuning storage and recovery paths at source baseline `45de2752811edbd66b01441c0cff8311d3e62647`.

Primary reviewed sources:

- `TunerPermanentProjectStore.kt`;
- `TunerRecoveryBundleStore.kt`;
- `TunerRecoveryBridge.kt`;
- `T3RamRecoveryFileStore.kt`;
- `T3RamScalarTransaction.kt`;
- `T6PersistentBurnRecoveryFileStore.kt`;
- `T6PersistentBurn.kt`.

The goal is not to preserve these class names or JSON formats. It is to preserve the proven authority and durability boundaries while removing WebView-era caches and pilot-specific workflow machinery.

---

# Primary finding: four different kinds of persisted state

The old implementation contains several persisted/cached things that must not be collapsed into one generic `project state` object.

EpicHub should model four distinct categories:

```text
1. Project authority
   ├─ exact generated INI/profile source
   └─ last complete verified TuneSnapshot

2. Derived acceleration
   ├─ compiled parsed profile
   ├─ semantic schema/projection caches
   └─ analyzer/index caches later

3. User recovery / backup
   └─ portable envelope containing project authority only

4. Operation uncertainty journal
   └─ tiny durable marker written before a command may change ECU state
```

Semantic tune drafts/history are a separate Rust tuning-domain state category and are not verified project/live-ECU authority. Kotlin editor selection, text-entry, gesture and other presentation state are separate again. Neither category is automatically part of recovery/project authority merely because it exists locally.

---

# Project authority

`TunerPermanentProjectStore` explicitly states and implements:

- exact imported `mainController.ini` is the sole structural/schema authority;
- the last **complete validated native TuneSnapshot** is tune-value authority;
- no semantic-value sidecar is an alternate tune authority;
- no WebView cache is authority;
- a compiled INI profile is only a derived startup accelerator;
- compiled profile acceptance requires an exact source SHA-256 match to the authoritative INI.

The store actively deletes legacy semantic `values.json` / `project.json` files to prevent old alternate-authority state from surviving.

## EpicHub invariant

The clean app keeps the same authority model:

```text
exact source profile
+ last complete verified TuneSnapshot
= persisted tuning project authority
```

Everything else is rebuildable or optional.

---

# Exact identity binding

A saved TuneSnapshot is accepted for a profile only when at least:

- snapshot ECU signature equals profile signature;
- snapshot profile fingerprint equals exact compiled profile fingerprint;
- snapshot structure/serialization is valid.

A compiled profile accelerator is accepted only when:

- its schema version is supported;
- source SHA-256 equals the current authoritative INI;
- imported profile name/identity matches the current project context.

## EpicHub consequence

Every derived tuning cache must carry an exact source/profile identity and be disposable on mismatch. No derived cache may silently become project authority because it is faster to load.

---

# Atomic authority updates

The old store uses Android `AtomicFile` for authoritative source/snapshot commits and verifies critical writes by rereading them.

Importing/replacing an authoritative INI also preserves the previous generation and rolls back if:

- the exact written INI does not reread byte-for-byte;
- accompanying profile metadata cannot be committed.

Recovery import similarly stages the old INI/snapshot/profile-name state and restores it if the new authority generation cannot be fully committed and reverified.

## EpicHub consequence

Project authority updates are **generation commits**, not independent best-effort file writes.

A clean implementation should provide a transaction-like project persistence operation:

```text
validate candidate authority generation
→ write durable temp/new files
→ fsync as required
→ atomic replace/commit
→ reread + decode + identity verify
→ expose new project generation
```

If commit cannot be proven, previous project authority remains valid.

The exact implementation may use Rust app-private file I/O after Kotlin supplies the app-private project directory. Android SAF remains for user-selected external import/export, not normal internal tuning authority.

---

# Derived acceleration / projection caches

`TunerPermanentProjectStore` contains substantial process and compiled-profile caching because the historical INI parse and giant WebView workspace serialization were expensive.

Important semantics worth preserving:

- cache identity is source/profile/snapshot fingerprint-bound;
- stale async work checks authority identity again before committing;
- cache candidates are generation/candidate-ID bound so an old callback cannot install a newer/incorrect derived state;
- caches can be dropped without losing tuning authority.

Historical mechanisms not worth preserving literally:

- 13M+ character JSON workspace serialization;
- WebView injection-gated compiled-profile persistence;
- giant bootstrap JSON caches.

Those are consequences of the old presentation architecture.

## EpicHub consequence

Use typed native schemas/models and small UI projections. Derived caches still follow exact identity binding and stale-work cancellation, but there is no JSON/WebView bootstrap authority.

---

# Offline saved-project behavior

Historical offline bootstrap decodes the saved TuneSnapshot against the exact INI/profile, then forcibly removes write authority before exposing the workspace.

Key semantic rule:

```text
saved project != live ECU session
```

A saved project may display/edit a local draft as product policy permits, but cannot imply that values are live or that writes/Burn are available.

## EpicHub consequence

Source/session state is explicit:

- `SavedProject`
- `LiveVerifiedSession`
- later perhaps `ImportedLog`, `Demo`, etc.

Only a live exact-profile verified ECU session can acquire hardware mutation authority.

---

# User recovery / backup bundle

`TunerRecoveryBundleStore` deliberately contains exactly the two project authorities:

- INI source;
- optional complete TuneSnapshot.

It does **not** own:

- ECU transport;
- semantic writes;
- read-back;
- Burn.

The external bundle is mirrored to a user-recoverable destination and can be selected later for restore.

On import it:

1. caps total input size;
2. checks bundle schema version;
3. validates non-empty/size-bounded INI source;
4. reparses the INI;
5. decodes the optional TuneSnapshot;
6. requires snapshot signature/profile fingerprint to match that INI;
7. transactionally commits INI + snapshot + profile identity;
8. rolls back to previous project generation if commit/verification fails.

## EpicHub consequence

Portable backup is a separate feature from internal project persistence.

Suggested final concepts:

- Rust `ProjectArchive`/`ProjectBackupCodec` owns schema/integrity/identity;
- Kotlin `AndroidDocumentAccess` owns user picker/URI permission and external file transfer;
- restoring a backup creates a new local project-authority generation only after full validation;
- imported backup never grants ECU write/Burn authority by itself.

Do not continuously mirror huge JSON if a smaller versioned archive/container can preserve the same two authorities more efficiently.

---

# Operation uncertainty journal

The RAM-write and persistent-Burn recovery files are fundamentally different from the project backup.

## Durable-before-transmission rule

`T3RamRecoveryFileStore` uses `AtomicFile` plus explicit `FileDescriptor.sync()` because the marker must be durable **before** the transport is allowed to cross the point where a `C` frame may have started transmitting.

The transaction then rereads the marker and requires exact equality before permitting transmission.

`T6PersistentBurnRecoveryFileStore` applies the same principle before a `B` frame and intentionally survives USB generation changes/power cycles during the historical persistence proof workflow.

## Marker meaning

A marker does not say the command definitely succeeded. It says:

> the application reached a state where hardware may have changed, and final state has not yet been proven/cleared.

This is exactly the information needed after process death, crash, USB loss or reboot.

## Fail-closed startup rule

Historical RAM workflow distinguishes:

- marker absent;
- marker present;
- marker corrupt.

Present or corrupt marker blocks a new write and requires recovery/resynchronization. It does not permit blind replay.

## Identity content

Historical markers bind recovery evidence to facts such as:

- operation phase/type;
- action/preview identity;
- source session/generation;
- ECU signature;
- profile fingerprint;
- baseline tune fingerprint;
- expected candidate tune fingerprint;
- semantic target/storage-definition identity;
- original/proposed raw bytes in the scalar pilots;
- for Burn, page/Burn identity and firmware request counters/status-schema identity.

The exact scalar-oriented fields are historical proof machinery; the identity principle is permanent.

---

# Final EpicHub operation journal design direction

Normal EpicHub tuning supports batch semantic changes, so the final journal should not reproduce scalar-pilot fields literally.

Provisional permanent model:

```text
TuningOperationJournal
├─ schema/version
├─ operationId
├─ operationKind          # RamWrite | Burn
├─ phase
├─ created/updated time
├─ ecuSignature
├─ profileFingerprint
├─ sourceSession/generation
├─ baselineTuneFingerprint
├─ expectedTuneFingerprint
├─ writePlanFingerprint   # semantic/resolved batch identity
├─ affectedPages
├─ burn evidence identity # only when relevant
└─ recovery classification/status
```

Critical lifecycle:

```text
plan verified
→ durable journal written + fsynced + reread
→ point of possible transmission crossed
→ perform operation
→ exact readback / full snapshot / burn evidence
→ final state proven
→ clear journal durably
```

If the process dies at any point after the durable marker and before proven clear, next launch reports uncertainty and requires deliberate resynchronization.

No automatic command replay.

---

# Project authority versus operation journal

These must remain independent:

```text
Project authority answers:
“What complete tune/profile state was last verified and persisted?”

Operation journal answers:
“Could a later operation have changed ECU state without final proof?”
```

A clean saved TuneSnapshot does **not** cancel an uncleared operation journal automatically. Conversely, an operation journal does not replace the saved TuneSnapshot.

After uncertainty, the application must reconnect and perform the required fresh complete ECU read/status verification to re-establish authority before new mutations.

---

# Historical T3/T6 proof workflows that should not become normal product UX

The pilot/qualification code includes:

- paired RAM candidate write then baseline restore;
- persistent candidate Burn;
- power-cycle persistence proof;
- Burned baseline restoration and another power-cycle proof.

Those sequences were valuable qualification evidence. They are not the desired ordinary tuning workflow.

Permanent product semantics remain:

```text
Read ECU
→ edit locally
→ write RAM
→ exact verification
→ continue editing
→ explicit Save/Burn
→ Burn evidence + post-Burn verification
```

Recovery journals preserve uncertainty safety without forcing proof-lab restoration/power-cycle UX into everyday tuning.

---

# Ownership consequences

## Rust owns

- exact project authority model;
- profile/snapshot identity validation;
- internal app-private project serialization/integrity;
- derived profile/model cache identity;
- tuning operation journal schema/state machine;
- recovery classification;
- decision whether hardware state must be reread before further mutation.

## Kotlin owns

- Android app-private base-directory provisioning if needed;
- SAF/user document picker;
- URI permission lifecycle;
- copying/exporting/importing portable project archives;
- user-facing recovery/backup UI;
- no tune semantic/write/Burn authority.

---

# Testing consequences

Phase 2/qualification should include fault injection at every persistence boundary:

- source/profile commit interrupted before/after rename;
- snapshot serialization/write/reread mismatch;
- stale async cache candidate after project replacement;
- backup import malformed/oversized/schema mismatch;
- backup snapshot belongs to different profile;
- app/process death after journal fsync but before transmission;
- death after possible `C`/`B` transmission but before ACK;
- death after ACK but before readback/full verification;
- corrupt operation journal;
- journal-clear failure after successful proof;
- reconnect with different ECU/profile/generation;
- complete-read recovery that classifies ECU as baseline/candidate/other where meaningful.

No test should interpret recovery metadata alone as proof of ECU state.