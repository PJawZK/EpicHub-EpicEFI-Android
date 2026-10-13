# TunerStudio tune compare, difference reporting, and restore-history behavioral reference

## Scope

This slice investigates TunerStudio as a mature reference for:

- comparison between local/offline tune state and controller state;
- comparison between the currently loaded tune and another saved tune;
- historical restore-point comparison;
- authority choices after a difference report;
- restore-point creation triggers, deduplication, retention and loading;
- firmware/profile migration interaction;
- validation/clamping and how it can itself create differences;
- failure/recovery implications for EpicHub.

The purpose is to extract behavioral contracts. It does not reproduce proprietary implementation code.

---

# 1. User-visible comparison model

## 1.1 Difference reporting is not limited to connection-time mismatch

**Observed behavior**

Bundled help describes two major comparison workflows:

1. **Compare on connect** — compare the offline/currently loaded tune with the connected controller and present differences before choosing which state to keep.
2. **Compare loaded tune to a saved tune** — compare the currently loaded tune with another MSQ offline and show the differing settings side by side.

Restore points can also be opened in a visual difference report against current state.

**Mature lesson**

Comparison is a reusable domain capability, not a special-case connection dialog.

**EpicHub conclusion — ★**

Build one semantic tune-comparison service that can compare any two compatible tune snapshots/authorities:

- working `TuneDraft`;
- current ECU readback snapshot;
- last-known ECU-synchronized state;
- named saved tune;
- restore/history checkpoint;
- pre-firmware-upgrade checkpoint;
- analyzer proposal/bulk-edit preview where useful.

The UI may present different workflows, but they should consume the same typed diff model.

---

## 1.2 Difference reporting and authority switching are separate concepts

**Observed behavior / implementation evidence**

The connection difference workflow exposes choices including `Use Controller Settings`, `Send Local Settings`, `Exit - No Changes`, and an offline/exit path. User-facing strings explicitly describe leaving the report without changing the currently loaded tune or controller.

**Mature lesson**

A diff does not itself mutate either side. Comparison first establishes evidence; a later explicit action selects an authority or exits unchanged.

**EpicHub conclusion — ★**

Use a two-stage model:

```text
Compare(A, B) -> TuneDiff
Resolve(TuneDiff, explicit action) -> optional TuneMutationPlan
```

Opening or inspecting a diff must never implicitly write the ECU, replace the working tune, or Burn.

---

# 2. Comparison granularity and semantic identity

## 2.1 Saved tunes are semantic configuration documents

**Observed behavior**

MSQ files are XML documents containing firmware signature/version metadata and named `constant` / `pcVariable` entries. The representative MS3 project tune records a firmware signature and named configuration values rather than existing only as an opaque byte dump.

**Inference strongly supported by the package**

TunerStudio's user-facing difference reports operate at setting/table level rather than exposing raw memory offsets as the primary comparison UX.

**EpicHub conclusion — ★**

EpicHub comparisons should operate on resolved semantic tune-object/setting IDs. Raw page/offset differences may exist as diagnostic evidence below the profile boundary but should not be the normal diff authority.

A diff entry should retain, where applicable:

- stable setting/tune-object ID;
- human label/path;
- semantic type;
- units/precision;
- left/right values;
- delta/percentage where meaningful;
- source profile/version for both sides;
- comparability/migration status;
- write/persistence capability only when a later action is planned.

---

## 2.2 Table/curve comparison should remain object-aware

**Observed behavior**

Bundled help describes visual side-by-side comparison of settings and highlights differences. Restore points are also eligible for full visual difference reports.

**EpicHub conclusion — ★**

Tables/curves should not be flattened into hundreds of unrelated scalar rows only. The semantic diff should support:

- object-level changed/unchanged summary;
- axis changes separated from Z/value changes;
- changed-cell mask;
- absolute/relative deltas;
- 2D heat/difference presentation;
- optional 3D delta surface using the existing Table Interaction Lab presentation path;
- drill-down to individual values.

The same underlying diff must serve list/summary, 2D and 3D presentations.

---

# 3. Validation can create differences

## 3.1 Loading a tune may normalize invalid values

**Observed behavior**

Bundled MSQ validation help states that when a loaded tune contains values outside limits defined by the firmware INI, TunerStudio can warn and clamp them to the nearest valid value. The help explicitly notes that this can then produce a difference report when the controller still contains the prior invalid values.

It distinguishes warnings from more critical errors.

**Mature lesson**

`file successfully parsed` is not equivalent to `semantic state remained byte/value identical to the source file`.

**EpicHub conclusion — ★**

Tune import/load/migration must produce a validation report independent from the later tune diff.

For each changed-on-load value record:

- original source value;
- normalized/migrated value;
- reason;
- severity;
- profile rule that caused the change.

A later ECU comparison must be able to explain that a difference came from import normalization rather than an unexplained edit.

Never silently clamp and then present the result as though the user manually changed it.

---

# 4. Restore points as semantic history

## 4.1 Restore points are full tune snapshots

**Observed behavior**

The Restore Points UI states that each restore point contains all controller settings for that point in time. It offers both `Compare to Current` and `Load Restore Point`.

Bundled example projects contain timestamped `.msq` restore points spanning multiple years, confirming that restore history is project-persistent rather than session-only.

**EpicHub conclusion — ★**

A durable history checkpoint must be independently:

- inspectable;
- comparable;
- loadable/recoverable;
- attributable to a semantic event;
- compatible/migratable according to profile rules.

History is therefore more than an undo stack.

---

## 4.2 Restore points are event driven

**Observed behavior / implementation evidence**

The package exposes independent preferences for creating restore points on:

- project close;
- ECU connect when state differs from the last restore point;
- tune open/load-to-controller;
- tune save / Ctrl+S.

It also exposes an option to skip a restore point when nothing has changed since the last one.

Help additionally confirms automatic restore creation before accepting controller settings from a connection difference report.

**Mature lesson**

Useful history is tied to meaningful state transitions, with change deduplication.

**EpicHub conclusion — ★**

Use semantic checkpoint triggers rather than periodic snapshot spam. Candidate events include:

- before ECU/local authority replacement;
- before firmware/profile migration;
- before loading another tune into a connected ECU;
- before analyzer/autotune bulk apply;
- after successful major persistence transaction;
- explicit user checkpoint;
- named tune save/import where policy requires it.

Do not create another checkpoint if the semantic tune hash is unchanged unless explicitly requested.

---

## 4.3 Restore history has a retention budget

**Implementation evidence**

TunerStudio preferences expose `Maximum Megabytes Disk Space` for restore points.

**EpicHub conclusion — ★**

History retention must be bounded and explicit. Prefer policy based on both semantic importance and storage budget, for example:

- pin user-named/manual checkpoints;
- preserve pre-migration/pre-authority-switch checkpoints preferentially;
- deduplicate identical tune content;
- prune low-value automatic checkpoints by age/count/space;
- never delete the only recoverable pre-destructive-transition checkpoint merely because a new routine autosave appeared.

Retention metadata belongs to project/history state, not tune calibration.

---

# 5. Loading a restore point is destructive and guarded

**Observed behavior / implementation evidence**

The Restore Points UI warns that all current settings will be overwritten by the selected restore point and asks for confirmation before loading it. A controller target must be selected for the restore workflow.

**EpicHub conclusion — ★**

`LoadCheckpoint` must be a guarded semantic operation, not a file-open shortcut.

Before committing:

1. resolve checkpoint/profile compatibility;
2. compare checkpoint to current working state;
3. create a pre-restore checkpoint when state differs;
4. build a mutation plan;
5. clearly distinguish local replacement from ECU live apply and ECU persistence;
6. execute only the explicitly approved domains.

Restoring local state must not automatically imply ECU Burn.

---

# 6. Firmware upgrade and migration

## 6.1 Firmware upgrade creates a distinct preservation boundary

**Observed behavior**

The example MS2 project contains a `PreFirmwareUpgrade_<timestamp>.msq` alongside ordinary restore history.

The firmware-loader help recommends updating the project ECU definition and restoring project tune settings after firmware load. It explicitly warns that settings available in the new firmware may differ from the previous firmware and advises reviewing warnings/settings after restore.

**Mature lesson**

Firmware/profile migration is not ordinary same-schema restore.

**EpicHub conclusion — ★**

Treat migration as a typed transformation:

```text
OldProfile TuneSnapshot
   -> migration/compatibility mapping
   -> validation report
   -> NewProfile TuneDraft
   -> explicit review/diff
   -> optional ECU application
```

Always retain the immutable pre-migration snapshot and migration provenance.

Do not compare incompatible profiles by raw position or silently drop settings.

---

## 6.2 Missing or changed settings need explicit classification

**Observed behavior**

Firmware-loader help states that changes in available settings between firmware versions can produce warnings after restore.

**EpicHub conclusion — ★**

Cross-profile diff/migration entries should classify at least:

- directly comparable;
- renamed/mapped;
- converted/scaled;
- added with default;
- removed/not representable;
- conditionally inactive;
- ambiguous/manual review required.

This classification is more important than merely counting unequal values.

---

# 7. Authority labels for EpicHub

TunerStudio's mature workflows show that ambiguous words such as `current`, `saved`, and `original` are unsafe once several authorities coexist.

**EpicHub conclusion — ★**

Use explicit source labels in comparison/history APIs and UI, e.g.:

```text
Working Draft
ECU Live Readback
ECU Last Verified Persisted Snapshot
Named Tune: <name>
Checkpoint: <timestamp/event>
Pre-Migration Snapshot
Analyzer Proposal
```

Each source should carry profile/version/provenance and, for ECU-derived state, session generation.

---

# 8. Performance and storage

## 8.1 Comparison should be incremental/object-aware

**Inference / EpicHub design conclusion**

Large modern tunes can contain many scalars and large tables. Reconstructing every visual panel merely to answer `what changed?` is unnecessary.

EpicHub should precompute or lazily compute semantic hashes at suitable levels:

```text
TuneSnapshot
  -> tune-object hash
  -> table axis hash / value hash
  -> per-cell diff only when object differs
```

Do not use hashes as authority when exact comparison is required; use them as a fast equality/filter stage.

## 8.2 Content-addressed checkpoints can deduplicate storage

**EpicHub conclusion — ★**

Because automatic checkpoint triggers can occur frequently, deduplicate immutable tune payloads where practical while retaining separate event metadata for distinct semantic events.

A single tune content snapshot can therefore be referenced by multiple history events without losing provenance.

---

# 9. Keep / improve / reject summary

| TunerStudio behavior | EpicHub decision |
|---|---|
| Compare local/offline vs ECU on connect | **KEEP**, generalized into reusable semantic compare |
| Compare current tune vs saved tune offline | **KEEP** |
| Restore point vs current visual diff | **KEEP** |
| Diff automatically mutates state | **REJECT** — compare is observational |
| Explicit `Use Controller` / `Send Local` authority actions | **KEEP**, but express as typed mutation plans |
| Automatic restore before destructive reconciliation | **KEEP / strengthen** |
| Restore checkpoints on meaningful events | **KEEP** |
| Skip unchanged restore points | **KEEP** |
| Maximum restore storage budget | **KEEP / improve retention policy** |
| Load restore point overwrites current settings | **KEEP only as guarded explicit operation** |
| Firmware migration through old/new definitions | **KEEP invariant, improve with typed mapping/provenance** |
| Validation/clamping on tune load | **KEEP validation**, but make normalization provenance explicit |
| Raw firmware pages/offsets as normal comparison UX | **REJECT** |

---

# 10. Suggested regression tests

## Comparison authority

1. Working draft vs ECU readback can be compared without mutation.
2. Working draft vs named saved tune works offline.
3. Working draft vs historical checkpoint works offline.
4. Diff source labels cannot be swapped or become ambiguous.
5. Exiting a diff with no action changes neither source.
6. Selecting `Use ECU` creates a mutation plan but does not bypass guards.
7. Selecting `Use Local` revalidates ECU session/profile before any write.

## Scalar/table diff

8. Equal scalar values are omitted/marked unchanged deterministically.
9. Units/precision do not create false differences when semantic values are equal.
10. Table axis-only changes are distinguishable from Z-value changes.
11. Changed-cell mask is identical in list/2D/3D views.
12. Large table diff can render summary before all per-cell detail is materialized.
13. Conditional/inactive settings are classified rather than blindly treated as ordinary unequal values.

## Validation/import

14. Out-of-range imported value is reported with original and normalized values.
15. Normalization-generated ECU difference carries that provenance.
16. Fatal import error performs no partial working-tune mutation.
17. Warning-only normalization remains recoverable to the pre-load state.

## Restore/history

18. Project-close checkpoint occurs only when policy permits and tune changed.
19. Connect checkpoint is skipped when semantic tune hash matches last checkpoint.
20. Tune-save/open triggers obey their individual settings.
21. Pre-authority-switch checkpoint exists before local tune is replaced by ECU state.
22. `Compare to Current` on checkpoint is observational.
23. Load checkpoint requires explicit confirmation/plan.
24. Loading checkpoint can replace local draft without automatically Burning ECU.
25. Restore operation failure leaves both pre-restore checkpoint and clear partial-state metadata.
26. Manual/pinned checkpoint survives automatic-retention pruning.
27. Identical payload checkpoints can deduplicate content while retaining separate event records.

## Firmware/profile migration

28. Pre-firmware-upgrade snapshot is captured before migration.
29. Same-ID compatible setting maps cleanly.
30. Renamed setting uses explicit migration mapping.
31. Unit/scaling conversion records provenance.
32. Removed setting is reported as not representable, not silently discarded.
33. New setting with default is distinguished from a user-sourced value.
34. Ambiguous mapping blocks automatic ECU application.
35. Migration result can be compared against both old snapshot and current ECU-compatible draft.

## Session/failure

36. ECU diff read started on session generation A cannot resolve against generation B.
37. Disconnect during comparison produces an incomplete/invalid comparison, not equality.
38. Corrupt saved tune produces a load/compare error without changing working state.
39. Unsupported/password-protected tune failure performs no mutation.
40. Process death while writing history metadata cannot corrupt the authoritative working tune.

---

# 11. Remaining unknowns

Static package evidence does **not** prove the following precisely and they remain dynamic-validation items:

- exact visual grouping/order of every difference-report row;
- exact per-setting selection granularity, if any, during all difference-report variants;
- exact treatment of conditionally disabled settings in all firmware families;
- exact retention-pruning algorithm behind the disk-space preference;
- exact comparison caching strategy/performance thresholds;
- exact behavior when comparing MSQ files across substantially incompatible signatures outside the firmware-loader workflow.

These should not be attributed to TunerStudio without controlled execution or narrower implementation evidence.
