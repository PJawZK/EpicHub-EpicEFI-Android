# EpicHub Table Interaction Integration Map — 2D + 3D Core

Authority:
`EpicEFI_Table_Interaction_Lab_Portable_Developer_Reference_FINAL.html`

## Core rule

EpicHub must treat both the **2D table** and **3D table** as first-class Tuner functionality.

The 3D table is not a later visual extra.

```text
Rust TuneDraft / semantic table state
                 │
            TableUiModel
             /       \
        TableGrid   Table3dView
           2D          3D
```

Both must show the same values, selection, preview state, protection/editability, and draft revision.

## Compose ownership

### `TableGrid.kt`

Owns:

- Numbers mode;
- Heat mode;
- Contour mode;
- Hybrid mode;
- selection overlay;
- hover overlay;
- pinned/protected markers;
- 2D pointer hit testing.

### `Table3dView.kt`

Owns:

- 3D projection;
- surface mesh;
- height representation;
- yaw / pitch / zoom;
- projected-cell hit testing;
- selected overlay;
- hover overlay;
- pinned/protected markers;
- crosshair traces;
- local labels;
- local 2D patch;
- spotlight;
- flattened inspection plane;
- vertical magnitude ruler;
- selected exact-value card;
- hover exact-value card.

### `TableSelection.kt`

Shared by both 2D and 3D:

- active cell;
- selected cells;
- selection anchor;
- move/extend/toggle/rectangle/select-all/collapse.

### Numeric preview

The same transient preview must be visible in both representations before commit.

```text
Compose previewValue
      │
      ├─ TableGrid
      └─ Table3dView
```

## Rust ownership

Rust tuning domain owns:

- authoritative draft values;
- legal bounds/scaling/precision;
- editability/protection;
- Set/Add/Scale operations;
- H/V interpolation;
- smoothing;
- batched Brush/Pull edits;
- authoritative tune edit history.

## Editing from 3D

3D direct editing should emit the same semantic `TableEditCommand` types as 2D.

Do not create a second 3D-specific tune mutation path.

```text
2D gesture ─┐
            ├─ TableEditCommand ─→ NativeEngine ─→ Rust TuneDraft
3D gesture ─┘
```

## 3D migration order

The 3D work should not be deferred until after the entire Tuner is otherwise complete.

Recommended sequence:

1. establish shared `TableUiModel`;
2. implement 2D read-only rendering;
3. implement 3D read-only rendering from the same model;
4. prove both show identical table state;
5. add shared selection;
6. add numeric preview to both;
7. add semantic editing;
8. add 3D selection/hit testing;
9. add 3D inspection layers;
10. enable 3D direct editing through the same commands.

## Regression requirements

A production table implementation is incomplete unless tests verify:

- 2D and 3D values are identical;
- numeric preview appears identically in both;
- selected cell is identical in both;
- multi-selection is identical in both;
- undo/redo updates both;
- interpolation/smoothing results appear in both;
- Brush/Pull edits update both;
- pinned/protected state is visible in both;
- 3D hover remains transient;
- 3D Selected remains persistent;
- all 3D detail layers remain functional.

## ECU boundary

Neither 2D nor 3D owns ECU transport.

Both stop at semantic edit intent / TuneDraft.

Explicit guarded RAM write and Burn remain separate operations below the tuning domain.
