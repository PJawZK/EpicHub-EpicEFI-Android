# EpicEFI / EpicHub Table Interaction Lab research package

## Purpose

This directory preserves prior EpicHub table-interaction work that predates the TunerStudio behavioral-reference investigation.

It must be considered when evaluating TunerStudio so mature external behavior is not incorrectly described as a new EpicHub idea when the project had already designed or prototyped it.

## Corrected project rule

**2D and 3D are both CORE Tuner functionality.**

The 3D table and its inspection workflow are not optional polish and must not be deferred as a separate future visualization subsystem.

## Preserved artifacts

- `EpicEFI_Table_Interaction_Lab_REFERENCE_PACKAGE_2D_3D_CORE_README.md`
  - corrected package authority and deprecation rules;
- `EpicEFI_Table_Interaction_Lab_COMPLETE_FEATURE_INVENTORY_2D_3D_CORE.md`
  - functional inventory and CORE/adapter/demo classification;
- `EpicEFI_Table_Interaction_Lab_PORTABILITY_APPENDIX_2D_3D_CORE.md`
  - portable feature-family guidance;
- `EpicHub_TABLE_INTERACTION_INTEGRATION_MAP_2D_3D_CORE.md`
  - intended Kotlin/Compose + Rust production ownership;
- `EXECUTABLE_REFERENCE_MANIFEST.md`
  - identity/checksum and behavioral notes for the uploaded executable HTML reference;
- `TUNERSTUDIO_RECONCILIATION.md`
  - crosswalk between this pre-existing EpicHub work and the mature TunerStudio evidence.

## Authority discipline

The original canonical authority named by these artifacts is:

`EpicEFI_Table_Interaction_Lab_Portable_Developer_Reference_FINAL.html`

The supplied executable derivative is:

`EpicEFI_Table_Interaction_Lab_CORE_COMPLETE_2D_3D.html`

The corrected package documents supersede stale internal comments in the executable derivative that still classify renderer sections as optional.

## Production rule

Do not bulk-port the HTML/JavaScript implementation into EpicHub.

Use it as behavior/prototype evidence under D-022:

```text
understand behavior and invariants
→ classify portable domain semantics vs adapter/demo mechanics
→ map to existing EpicHub ownership
→ implement independently in Kotlin/Compose + Rust
→ regression-test both 2D and 3D against the same semantic draft
```

The production semantic authority remains the native EpicHub tuning domain, not JavaScript or browser state.
