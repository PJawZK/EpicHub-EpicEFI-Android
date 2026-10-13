# EpicEFI Table Interaction Reference Package — Corrected Core Definition

## Canonical authority

`EpicEFI_Table_Interaction_Lab_Portable_Developer_Reference_FINAL.html`

## Project rule

**2D and 3D are both CORE functionality.**

The 3D table and its inspection layers must not be removed from the core implementation package.

## Use these artifacts

- `EpicEFI_Table_Interaction_Lab_CORE_COMPLETE_2D_3D.html`
  - exact executable JS behavior from the canonical full reference;
  - 2D + 3D both enabled;
  - full manual editing and inspection workflow.

- `EpicEFI_Table_Interaction_Lab_COMPLETE_FEATURE_INVENTORY_2D_3D_CORE.md`
  - all functions inventoried;
  - 2D and 3D reclassified as CORE.

- `EpicEFI_Table_Interaction_Lab_PORTABILITY_APPENDIX_2D_3D_CORE.md`
  - complete portability guidance using the corrected core definition.

- `EpicHub_TABLE_INTERACTION_INTEGRATION_MAP_2D_3D_CORE.md`
  - EpicHub mapping with `TableGrid` and `Table3dView` as peer core components.

## Deprecated derivatives

Do not use these as implementation authority:

- `EpicEFI_Table_Interaction_Lab_CORE_ONLY.html`
- `EpicEFI_Table_Interaction_Lab_CORE_2D_COMPLETE.html`

They intentionally or effectively omit the 3D runtime experience and therefore do not represent the project's actual core requirements.

## Authority order

```text
Canonical FULL reference
        ↓
Complete 2D + 3D core reference
        ↓
Complete feature inventory / portability appendix
        ↓
EpicHub integration mapping
```
