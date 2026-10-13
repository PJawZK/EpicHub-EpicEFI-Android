# TunerStudio project migration, compatibility, and firmware-upgrade behavioral reference

## Scope

This slice investigates how TunerStudio preserves project meaning across firmware/profile changes and what it deliberately does *not* preserve automatically.

Focus areas:

- project-local ECU-definition authority;
- firmware-update workflow and tune restoration;
- project archive / rollback boundaries;
- sensor calibration handling;
- compatibility warnings and release-note dependence;
- preservation of project-local customizations;
- interaction with dashboards, logs, restore history and project settings;
- migration provenance and rollback requirements for EpicHub.

The goal is to extract behavioral contracts. It is not to reproduce the firmware loader or INI implementation.

---

## 1. Firmware flashing and project migration are related but distinct

**Observed behavior**

The bundled firmware-loader help recommends performing an update while the normal project is open because this assists with restoring tune settings and updating the project's ECU Definition after the firmware is flashed.

If no project is open, the loader can still finish the firmware update, but the user must later load the MSQ or open the normal project and resolve the next Difference Report.

**Mature lesson**

Flashing the controller and migrating the tuning project are different operations with different failure/rollback boundaries.

**EpicHub conclusion — ★**

Keep these domains separate:

```text
FirmwareInstall/Flash
        !=
Project/Profile Migration
        !=
Tune Restore
        !=
ECU live apply / persistence
```

EpicHub may orchestrate them in one guided workflow, but each step should remain independently observable, cancellable where safe, and recoverable.

---

## 2. The project owns its active profile/definition

**Observed behavior**

The bundled file-structure help states that projects do not directly reference the installation's global ECU-definition files. Instead, `projectCfg/mainController.ini` is a project-local copy of the selected ECU definition and is the definition used when the project opens.

TunerStudio rewrites that project-local definition only when a new ECU Definition is explicitly selected.

**Mature lesson**

An application upgrade or global definition-library change should not silently reinterpret an existing project.

**EpicHub conclusion — ✓ ★**

This reinforces EH-TS-002 / EH-TS-003:

- store profile identity/version/provenance with the project;
- reopen under the project's known semantic schema;
- require an explicit migration when changing profile semantics;
- never silently replace the project's interpretation merely because a newer global profile exists.

---

## 3. Project-local overrides are intentionally preserved across definition changes

**Observed behavior**

`projectCfg/custom.ini` is documented as a project-local override/include file specifically useful when changing ECU-definition files, including after a firmware upgrade, because it is not overwritten with `mainController.ini`.

**Mature lesson**

Not every project-specific behavior belongs inside the firmware/profile definition.

**EpicHub conclusion — ★**

Persist project-owned overrides separately from the resolved firmware profile.

Potential EpicHub categories include:

- channel aliases / user calculations;
- project-specific UI metadata;
- dashboard bindings;
- user units/presentation overrides;
- local warning presentation;
- compatibility mappings approved by the user.

Migration must state which overrides remain valid, require remapping, or are incompatible.

---

## 4. Pre-upgrade project archive is broader than the tune

**Observed behavior**

The firmware-loader archive help says a project backup can contain:

- dashboards;
- project settings;
- tune files;
- current ECU Definition (INI);
- optionally data logs.

It explicitly states that the firmware currently installed in the ECU is **not** saved in that project backup.

The bundled MS2 example also contains a dedicated `PreFirmwareUpgrade_<timestamp>.msq` in addition to ordinary restore points.

**Mature lesson**

Rollback requires both project state and access to the prior firmware image/version; project backup alone is not a complete controller image.

**EpicHub conclusion — ✗ ★**

Define a versioned project archive / migration snapshot that captures semantic application state, while recording firmware provenance separately:

```text
ProjectArchive
  project metadata
  resolved profile identity/version
  working tune + named tunes
  restore/checkpoint history policy or selected checkpoints
  dashboards
  log profiles
  project calculations/overrides
  sensor/calibration metadata where available
  migration records
  references to recorded logs (or optional inclusion)
  previous firmware identity/hash/source reference
```

Do not imply the firmware binary is archived unless it actually is.

---

## 5. Tune restoration after firmware update is compatibility-aware, not guaranteed lossless

**Observed behavior**

After flashing, the firmware-loader workflow recommends updating the project ECU Definition and restoring the project tune.

The help explicitly warns that when the new firmware has changed the available settings, warnings may appear and related settings should be reviewed. It also tells users to consult the firmware release notes for incompatibilities between previous and new firmware.

**Mature lesson**

A successful file load is not proof of semantic equivalence after a firmware change.

**EpicHub conclusion — ✗ ★**

Firmware/profile migration needs an explicit migration report rather than a generic successful-load result.

At minimum classify each setting/tune object as:

- directly compatible;
- renamed/mapped;
- converted/scaled;
- defaulted because newly introduced;
- removed/unsupported;
- conditionally inactive;
- normalized/clamped;
- ambiguous/manual review required.

The migration result should preserve the reason/provenance for every automatic transform.

---

## 6. Existing value validation participates in migration behavior

**Observed behavior**

TunerStudio validates MSQ values against the active ECU definition on load. Out-of-range values can be corrected to the nearest legal value and reported as warnings. The bundled help notes that this may then legitimately produce a Difference Report against the controller because the local value has already been normalized.

The help also notes that after a firmware change some warnings can refer to settings no longer used in the new configuration.

**EpicHub conclusion — ★**

Migration/import validation must retain provenance:

```text
source value
source profile/schema
migration rule
normalized value
reason
severity
whether setting is active in destination profile
```

Do not collapse `unsupported`, `inactive`, `clamped`, and `successfully converted` into one warning class.

---

## 7. Sensor calibrations have a distinct migration lifecycle

**Observed behavior**

One firmware-loader completion path explicitly reports `Sensor Calibrations Restored` separately from tune settings.

Representative ECU definitions also expose dedicated calibration-table write/lock behavior rather than treating all calibration data as ordinary tune constants.

**Mature lesson**

Calibration data can have different storage, locking, write and compatibility semantics from normal tune settings.

**EpicHub conclusion — ★**

Model calibration assets separately from ordinary TuneDraft objects when the firmware/profile requires it.

For each calibration family record:

- semantic calibration type;
- source/profile provenance;
- writable/lock requirements;
- destination compatibility;
- conversion requirements;
- whether restore succeeded independently of tune restore.

A firmware migration must never silently assume that restoring the normal tune also restored thermistor, AFR, MAF or other calibration tables.

---

## 8. Partial tune/table assets are useful migration tools

**Observed behavior**

The file-structure help documents:

- `.msq` as the full tune for a firmware;
- `.msqpart` as a subset/dialog tune that can load only the settings visible in that dialog;
- `.table` as X/Y/Z-only table data that is intentionally not tied to one table size and can interpolate when imported into a differently sized table.

It explicitly describes `.table` as useful for importing a table into a new firmware.

**Mature lesson**

Cross-version migration is often safer when users can migrate compatible semantic objects instead of forcing the entire source tune into the destination schema.

**EpicHub conclusion — ★**

Retain object-level migration/import paths:

- full-project/profile migration;
- full tune migration;
- subsystem/setting-group migration;
- individual table/curve migration;
- calibration-specific migration.

Each path gets its own compatibility checks and preview/diff.

---

## 9. Non-tune project state must survive firmware migration deliberately

**Observed behavior**

Project archives include dashboards and project settings, and normal project structure keeps dashboards, DataLogs, project configuration and restore points outside the global application installation.

This proves mature expectation that a firmware update should not inherently erase the user's project workspace.

**Inference**

The static package does not prove exactly how every dashboard/log-profile binding is migrated when channels are renamed or removed.

**EpicHub conclusion — ★**

Treat firmware migration as a set of independently versioned state migrations:

```text
Tune schema migration
Channel schema migration
Dashboard binding migration
Log-profile migration
Calculated-channel migration
Warning/config migration
History/checkpoint migration
Calibration migration
```

A tune can migrate successfully while one dashboard widget becomes unresolved. Those outcomes should be reported separately rather than making the entire project unusable.

---

## 10. Rollback must preserve the old semantic world

**Observed behavior**

The firmware-loader backup help explicitly frames the archive as protection in case the user rolls back to the previous firmware. It states that the old firmware itself must separately be available and reloaded before loading the backed-up tune/project state.

**EpicHub conclusion — ✗ ★**

Before migration, create an immutable pre-migration snapshot containing enough information to reopen the project under the previous profile semantics.

Rollback should be defined as:

1. restore/verify prior firmware where applicable;
2. restore prior profile identity/version;
3. restore prior project semantic state;
4. restore/calibrate compatible controller-specific calibration assets;
5. re-establish ECU identity and tune synchronization;
6. only then resume normal mutation authority.

Do not overwrite or mutate the only copy of the old project schema during migration.

---

## 11. Proposed EpicHub migration transaction

```text
Preflight
  identify current ECU/profile/project
  verify destination firmware/profile
  capture immutable pre-migration checkpoint/archive
  validate rollback prerequisites

Firmware step (optional/external)
  flash/update controller
  re-identify controller

Resolve destination profile
  load destination semantic schema
  do NOT yet mutate source project state

Migration planning
  map settings/tables/calibrations/channels
  classify compatible/converted/defaulted/removed/ambiguous
  generate preview + diagnostics

User review
  resolve manual mappings / rejected changes

Commit project migration
  atomically create new project/profile generation
  preserve source generation and provenance

Controller reconciliation
  compare migrated working draft with ECU
  explicitly apply live / persist only after review

Post-migration validation
  validate dashboards/log profiles/calculations
  surface unresolved bindings
  retain rollback path
```

---

## 12. Keep / improve / reject

### Keep

- project-local profile authority;
- explicit definition/profile update after firmware change;
- pre-upgrade backup;
- restore tune only after destination profile exists;
- separate calibration restoration;
- release-note/incompatibility awareness;
- project-local overrides surviving profile replacement;
- object/subsystem-level import tools.

### Improve for EpicHub

- typed migration rules instead of filename/INI-centric behavior;
- machine-readable migration report;
- provenance for every automatic transform;
- schema-aware migration of dashboards/log profiles/calculated channels;
- atomic source/destination project generations;
- explicit rollback readiness check;
- compatibility status per subsystem rather than one success/warning result.

### Reject as architecture

- assuming an MSQ load equals successful firmware migration;
- silently replacing project profile semantics;
- treating project backup as firmware backup;
- treating all calibration data as normal tune constants;
- destroying the source project generation during migration;
- allowing unresolved channel/UI bindings to silently rebind to unrelated channels.

---

## 13. Suggested regression tests

1. Global application/profile update does not reinterpret an existing project until explicit migration.
2. Project-local profile survives application reinstall/update.
3. Project-local override survives profile replacement when still valid.
4. Incompatible override is surfaced, not silently ignored/rebound.
5. Pre-migration archive contains tune, profile metadata, dashboards and project settings.
6. Archive metadata clearly states whether firmware binary is or is not included.
7. Migration cannot start without a durable pre-migration checkpoint when rollback policy requires one.
8. Destination profile must resolve before source tune is transformed.
9. Directly compatible scalar migrates unchanged.
10. Renamed setting migrates through explicit mapping and records provenance.
11. Rescaled setting converts deterministically and records source/destination units.
12. Removed setting is preserved in migration report and not written to destination.
13. New destination setting receives explicit default provenance.
14. Conditional/inactive setting is distinguished from missing/removed setting.
15. Out-of-range source value is normalized with original value retained in report.
16. Ambiguous setting blocks or requires manual review according to policy.
17. Table object migration validates axes/shape before mutation.
18. Different-sized compatible table migration uses an explicit previewable resampling operation.
19. Tune-object-only migration does not alter unrelated tune state.
20. Calibration restore success/failure is independent from ordinary tune restore status.
21. Locked calibration region cannot be reported restored unless unlock/write/relock lifecycle succeeds.
22. Dashboard with all channel IDs preserved migrates unchanged.
23. Dashboard with renamed channel uses an explicit migration mapping.
24. Dashboard with removed channel retains an unresolved widget instead of silent rebinding.
25. Log profile with missing channel is marked partially incompatible.
26. Calculated channel with renamed dependency is migrated or explicitly unresolved.
27. Restore/history checkpoints retain the profile generation under which they were created.
28. Comparing pre- and post-migration checkpoints uses schema-aware mapping.
29. Migration commit is atomic across tune/profile/project metadata.
30. Process death before migration commit leaves the source project intact.
31. Process death after migration commit but before ECU write leaves a valid migrated project with explicit ECU-sync pending state.
32. ECU disconnect after firmware flash does not cause source project mutation.
33. Reconnect to unexpected firmware/profile aborts controller reconciliation.
34. Rollback restores prior profile semantic interpretation before loading prior tune state.
35. Rollback does not assume prior firmware exists merely because a project archive exists.
36. User can retain both pre- and post-migration project generations for comparison.
37. Migration warnings are grouped by tune/calibration/dashboard/logging/project subsystem.
38. Successful tune migration does not hide unresolved dashboard/log-profile compatibility issues.
39. Project migration performed offline remains blocked from ECU mutation until identity is revalidated.
40. Migration diagnostics contain source profile, destination profile, rule/version and user decisions.

---

## 14. Open questions requiring dynamic or firmware-specific validation

Static evidence does not establish:

- exact algorithm TunerStudio uses to map renamed/changed constants between firmware versions;
- whether sensor-calibration restoration is supported for every ECU family or only selected firmware loaders;
- exact archive file format and import conflict policy in this package;
- exact dashboard/log-profile channel remapping behavior after ECU-definition change;
- whether restore-point history is copied into every archived project or governed by archive options/version;
- exact firmware-loader rollback automation beyond the documented backup/restore guidance;
- exact handling of custom.ini entries whose referenced symbols disappear or change type.

These remain implementation/runtime validation questions and are not claimed as established TunerStudio behavior.
