# EpicEFI Table Interaction Lab — Complete Feature Inventory (2D + 3D Core)

Authority: `EpicEFI_Table_Interaction_Lab_Portable_Developer_Reference_FINAL.html`

Named functions in authority file: **78**

Project rule: **2D and 3D are both CORE table functionality.**

The 3D surface, projected-cell hit testing, selected/hover contexts, crosshair, local labels, floating patch, spotlight, flattened plane, magnitude ruler, and floating exact-value cards are all considered part of the core tuning-table experience.

## CORE — 64 functions

### HISTORY / UNDO-REDO - BEGIN

- `captureTableState()`
- `snap()`
- `restore()`

### SHARED DISPLAY VALUE ACCESS - BEGIN

- `displayValue()`

### 2D TABLE RENDERER

- `color()`
- `cell2()`
- `contour()`
- `draw2()`

### WEIGHTED BRUSH EDIT - BEGIN

- `apply()`

### 3D TABLE + INSPECTION

- `prepare3DTransform()`
- `projectInto()`
- `project()`
- `ensureSurfaceQuads()`
- `drawSurface()`
- `drawCrosshair()`
- `drawLocalLabels()`
- `drawNeighborhood()`
- `drawPatch()`
- `patch()`
- `drawFlattenedPlane()`
- `drawMagnitude()`
- `ruler()`
- `draw3()`
- `nearest3()`
- `setFloat()`
- `floating()`

### SELECTION MODEL - BEGIN

- `selKey()`
- `selectionCells()`
- `setSingleSelection()`
- `toggleSelection()`
- `rectSelection()`
- `moveSelection()`
- `selectAllCells()`
- `collapseSelection()`

### POINTER GESTURE ACTIONS - BEGIN

- `setHoverCell()`
- `selectPointerCell()`
- `beginCellEdit()`
- `dragCellEdit()`
- `clearPointerDrag()`

### TABLE ACTIONS - BEGIN

- `resetTable()`
- `smoothWholeTable()`
- `moveHistory()`
- `undo()`
- `redo()`

### SELECTED-CELL OPERATIONS - BEGIN

- `mutateSelected()`
- `askNumber()`
- `setSelectedExact()`
- `interpolateSelected()`
- `interpolateHorizontal()`
- `interpolateVertical()`
- `weightedSmoothAt()`
- `smoothSelected()`
- `addSelected()`
- `addStrengthToSelected()`
- `interpolateSelectedHorizontal()`
- `interpolateSelectedVertical()`
- `smoothCurrentSelection()`

### DIRECT NUMERIC ENTRY TRANSACTION - BEGIN

- `positionNumHud()`
- `updateNumEntryPreview()`
- `startNumEntry()`
- `appendNumEntry()`
- `backspaceNumEntry()`
- `cancelNumEntry()`
- `commitNumEntry()`

## ADAPTER — 13 functions

### 3D POINTER / MOUSE - BEGIN

- `update()`
- `drawAll()`
- `switchPage()`

### STANDARD UI CONTROLS - BEGIN

- `setTool()`
- `bindNumber()`
- `bindToggle()`
- `sync()`

### KEYBOARD HELPER FUNCTIONS - BEGIN

- `kbSwitchPage()`
- `setView()`
- `kbCycle2DView()`
- `toggleLayer()`
- `kbUndo()`
- `kbRedo()`

## DEMO — 1 functions

### GENERATED TABLE DATA

- `base()`
