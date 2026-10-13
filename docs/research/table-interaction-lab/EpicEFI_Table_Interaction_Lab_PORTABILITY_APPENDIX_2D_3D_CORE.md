# EpicEFI Table Interaction Lab — Complete Portability Appendix

Authority:
`EpicEFI_Table_Interaction_Lab_Portable_Developer_Reference_FINAL.html`

## Core-definition correction

For this project, **3D is core functionality**, not an optional visualization.

A complete port must preserve both:

```text
2D table
3D table
```

on the same authoritative table/draft state.

## CORE feature families

### Table state / history

- `captureTableState()`
- `snap()`
- `restore()`
- `displayValue()`
- `moveHistory()`
- `undo()`
- `redo()`

### Selection

- `selKey()`
- `selectionCells()`
- `setSingleSelection()`
- `toggleSelection()`
- `rectSelection()`
- `moveSelection()`
- `selectAllCells()`
- `collapseSelection()`

### Manual editing

- `apply()`
- `mutateSelected()`
- `setSelectedExact()`
- `addSelected()`
- `addStrengthToSelected()`
- `interpolateSelected()`
- `interpolateHorizontal()`
- `interpolateVertical()`
- `weightedSmoothAt()`
- `smoothSelected()`
- `smoothWholeTable()`

### Numeric-entry transaction

- `positionNumHud()`
- `updateNumEntryPreview()`
- `startNumEntry()`
- `appendNumEntry()`
- `backspaceNumEntry()`
- `cancelNumEntry()`
- `commitNumEntry()`

### Pointer semantics

- `setHoverCell()`
- `selectPointerCell()`
- `beginCellEdit()`
- `dragCellEdit()`
- `clearPointerDrag()`

### 2D table renderer

- `color()`
- `cell2()`
- `contour()`
- `draw2()`

Required 2D modes:

- Numbers
- Heat
- Contour
- Hybrid

### 3D table renderer — CORE

- `prepare3DTransform()`
- `projectInto()`
- `project()`
- `ensureSurfaceQuads()`
- `drawSurface()`
- `draw3()`
- `nearest3()`

Required 3D behaviors:

- same values as 2D;
- height representation;
- yaw / pitch / zoom;
- projected-cell hit testing;
- selected-cell overlay;
- hover overlay;
- pinned-cell overlay.

### 3D inspection layers — CORE

- `drawCrosshair()`
- `drawLocalLabels()`
- `drawNeighborhood()`
- `drawPatch()`
- `patch()`
- `drawFlattenedPlane()`
- `drawMagnitude()`
- `ruler()`
- `setFloat()`
- `floating()`

Required inspection behavior:

- transient Hover context;
- persistent Selected context;
- Selected owns overlap where appropriate;
- exact local values;
- local 2D patch;
- crosshair traces;
- spotlight neighborhood;
- flattened inspection plane;
- vertical magnitude ruler;
- floating exact-value cards.

## Shared-state invariant

The fundamental rule is:

```text
one authoritative table/draft
          │
      displayValue()
       /       \
     2D         3D
```

3D must never maintain a separate value array or separate tune authority.

## What remains non-core

The following may still be replaced or removed during integration:

- demo-generated VE data;
- HTML/CSS styling;
- keyboard diagnostic panel;
- shortcut-help overlay;
- browser DOM plumbing;
- demo axis generation.

These are implementation/demo conveniences, not tuning functionality.

## Porting rule

A production port is incomplete if the 2D editor works but the 3D table workflow is missing.

Both representations should consume the same selection, preview, edit, history, and tune-draft semantics.
