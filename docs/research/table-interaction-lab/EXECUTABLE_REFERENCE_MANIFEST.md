# EpicEFI Table Interaction Lab executable reference manifest

## Source artifact

`EpicEFI_Table_Interaction_Lab_CORE_COMPLETE_2D_3D.html`

Uploaded research artifact characteristics:

- self-contained HTML/JavaScript tuning-table interaction reference;
- 2D and 3D table views enabled;
- shared authoritative calibration-value state;
- selection, hover, preview, history, protected/pinned cells, Brush/Pull/Pin/Select tools;
- 2D Numbers / Heat / Contour / Hybrid representations;
- 3D surface projection, selection/hit testing and inspection layers;
- direct numeric-entry preview/commit transaction;
- context-aware keyboard dispatcher calling shared domain operations;
- no ECU transport ownership;
- demo-generated table/axis data intended to be replaced by production tune/profile data.

SHA-256 of the uploaded executable reference:

```text
387cd6646533448de14941d4b7b374fb80b234fdd44581ce11333b3ac916c550
```

## Important authority correction

The executable artifact retains historical comments that label some renderer sections `OPTIONAL`.

Those comments are stale with respect to the corrected project scope. The authoritative corrected package documents establish that **both 2D and 3D are CORE functionality**, including the 3D inspection workflow.

Use this authority order:

```text
Corrected reference-package README
        ↓
Complete feature inventory / portability appendix
        ↓
EpicHub integration map
        ↓
Executable HTML as behavioral/prototype evidence
```

The executable implementation is not production architecture. Its reusable value is the demonstrated interaction behavior and separation of domain operations from adapters/renderers.

## Key executable invariants preserved by the research docs

- committed calibration values and transient numeric preview are separate;
- both 2D and 3D read through shared displayed-value semantics;
- selected cells are separate from pointer hover/focus;
- one history snapshot is taken per committed gesture/operation rather than per rendered frame;
- selected-cell operations skip protected/pinned cells;
- 2D and 3D pointer adapters delegate editing semantics to shared operations;
- input adapters invoke domain operations rather than implementing parallel tuning algorithms;
- hidden views are not redrawn in the reference implementation;
- reusable 3D projection/surface metadata is cached rather than recreated for every point on every frame.

These invariants are represented more directly for EpicHub in `EpicHub_TABLE_INTERACTION_INTEGRATION_MAP_2D_3D_CORE.md` and `TUNERSTUDIO_RECONCILIATION.md`.
