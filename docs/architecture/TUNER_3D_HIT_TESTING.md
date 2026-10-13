# Tuner 3D hit testing and selection

Status: implementation contract for the first runtime slice under the accepted D-026 3D editing contract.

## Scope

This slice adds only deterministic 3D cell hit testing and selection routing.

It does **not** add direct 3D value deformation, Brush/Pull gestures, camera orbit, ECU mutation, RAM write, Burn, or project persistence.

## Interaction modes

The 3D surface has two explicit presentation modes:

- `Inspect`: pointer/touch hits do not emit tuning commands.
- `Select/Edit`: a hit may resolve to one real table cell and emit `TunerCommand.SelectCell(row, col)`.

The mode is Compose-owned presentation/interaction state under D-016. It is not tune state.

## Geometry authority

The existing 3D view is a projected mesh. Rendering and hit testing must use the same deterministic projection helper so visual points and selectable points cannot drift apart.

The projection helper receives:

- table rows/columns;
- the exact displayed value array (committed or preview surface);
- canvas width/height.

It produces one projected point for every authoritative table cell.

No projected triangle, edge, or interpolated surface point becomes a semantic tune target.

## Hit contract

`hitTest(...)` evaluates distance from the pointer/touch position to projected **cell vertices only**.

Rules:

1. only real `(row, col)` cells are candidates;
2. the nearest candidate within the configured hit radius wins;
3. a candidate outside the radius is not selectable;
4. equal-distance ties resolve deterministically by row then column;
5. empty/invalid geometry returns no hit;
6. hit testing never reads/modifies tune values beyond the read-only displayed projection used for geometry;
7. the result is only `TunerCellPosition` and carries no numeric value authority.

## Command routing

```text
3D tap in Select/Edit mode
        ↓
Tuner3dGeometry.hitTest(...)
        ↓
TunerCellPosition?
        ↓
TunerCommand.SelectCell(row, col)
        ↓
TunerCommandRouter
        ↓
shared 2D/3D selection state
```

The exact same router semantics therefore govern:

- persistent Extend selection;
- pending-preview cancellation on selection changes;
- bounds validation;
- later keyboard/semantic edit commands.

The 3D adapter does not call `OfflineTunerFixture`, JNI, or Rust directly.

## Preview interaction

Hit testing uses the currently displayed surface (preview projection when present, committed projection otherwise) so hit locations match the mesh the user can see.

Selection remains semantic row/column state. Changing selection still cancels a pending preview through the existing router contract.

## Regression requirements

The slice must prove at least:

1. projection produces exactly `rows × cols` cell vertices;
2. a vertex hit resolves to its exact row/column;
3. a nearby hit inside the radius resolves to the nearest vertex;
4. a hit outside the radius resolves to no cell;
5. equal-distance ties are deterministic;
6. non-uniform value heights do not alter row/column identity;
7. 3D selection dispatch uses `TunerCommand.SelectCell` rather than a parallel selection implementation;
8. Inspect mode emits no selection command;
9. Select/Edit selection is reflected by the already-shared 2D/3D selection state;
10. no ECU or tune mutation path is added.
