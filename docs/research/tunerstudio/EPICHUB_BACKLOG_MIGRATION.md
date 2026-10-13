# EpicHub TunerStudio-research backlog extension — project migration / firmware upgrades

This extension continues the numbered review backlog after the compare/history extension. It remains a review backlog, not an implementation queue.

## Project migration / compatibility / firmware upgrades

### EH-TS-077 — Explicit firmware-flash vs project-migration separation

**Marker:** ★

Treat controller firmware installation, project/profile migration, tune restoration, and ECU live/persist operations as distinct stateful steps even when orchestrated in one guided workflow.

### EH-TS-078 — Immutable project-local profile generation

**Marker:** ✓ ★

Existing projects reopen against their recorded semantic profile generation. Application/profile-library updates must not silently reinterpret project state.

### EH-TS-079 — Project-owned override layer independent of firmware profile

**Marker:** ★

Persist project-specific aliases, calculations, presentation/config overrides and approved compatibility mappings separately from replaceable firmware/profile definitions. Migration must classify each override as retained, remapped or incompatible.

### EH-TS-080 — Versioned pre-migration project archive

**Marker:** ✗ ★

Before firmware/profile migration, capture a durable project archive containing project/profile provenance, working/named tune state, relevant checkpoints, dashboards, log profiles, calculations/overrides, calibration metadata, migration records and optional log references/inclusion.

### EH-TS-081 — Firmware provenance is not project-backup payload

**Marker:** ★

Record previous firmware identity/version/hash/source separately and state whether a firmware binary is actually archived. Never imply that a project backup alone can restore controller firmware.

### EH-TS-082 — Typed per-setting migration classification

**Marker:** ✗ ★

Classify destination handling for each semantic setting/object as directly compatible, renamed/mapped, converted/scaled, newly defaulted, removed/unsupported, conditionally inactive, normalized/clamped, or ambiguous/manual-review-required.

### EH-TS-083 — Migration transform provenance

**Marker:** ✗ ★

Every automatic migration transform retains source value/schema, destination value/schema, applied rule/version, reason and severity. Later diffs must be able to explain migration-generated changes.

### EH-TS-084 — Calibration migration as a separate domain

**Marker:** ✗ ★

Thermistor/AFR/MAF and other controller calibration assets may have independent storage, write, lock and compatibility semantics. Track and validate calibration restoration separately from ordinary TuneDraft migration.

### EH-TS-085 — Object/subsystem-level migration paths

**Marker:** ★

Support full-project, full-tune, subsystem/setting-group, individual table/curve and calibration-specific migration/import paths with separate compatibility checks and preview/diff.

### EH-TS-086 — Independent non-tune schema migrations

**Marker:** ✗ ★

Firmware/profile migration also evaluates dashboard channel bindings, log profiles, calculated-channel dependencies, warning/presentation config and history/checkpoint compatibility. One subsystem can succeed while another remains partially unresolved.

### EH-TS-087 — Preserve unresolved bindings explicitly

**Marker:** ✗ ★

If a migrated dashboard/log/calculated-channel reference cannot be mapped safely, retain an explicit unresolved binding. Never silently bind it to a different same-named/positionally similar channel without proven semantic compatibility.

### EH-TS-088 — Atomic source/destination project generations

**Marker:** ✗ ★

Build migration into a new project/profile generation and atomically commit it while preserving the source generation. Process death before commit leaves the source intact; process death after commit leaves a valid migrated project even if ECU reconciliation has not occurred.

### EH-TS-089 — Rollback readiness and ordered rollback workflow

**Marker:** ✗ ★

Before migration, validate rollback prerequisites. Rollback restores/verifies prior firmware where needed, prior semantic profile generation, prior project state and compatible calibration assets before re-establishing ECU synchronization and mutation authority.

### EH-TS-090 — Migration report as a first-class artifact

**Marker:** ✗ ★

Generate a durable machine-readable/user-readable migration report grouped by tune, calibration, dashboard, logging, calculations and project state. Include mappings, transformations, defaults, omissions, unresolved items and user decisions.

## Research source

See `PROJECT_MIGRATION_FIRMWARE_UPGRADES.md` for evidence, keep/improve/reject decisions, 40 regression scenarios and open validation questions.
