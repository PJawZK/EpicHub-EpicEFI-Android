# Tuner 3D camera and gesture arbitration

Status: runtime interaction slice stacked on the qualified 3D hit-selection foundation.

The governing contract remains:

```text
3D camera / rendering state
        != tune state
        != edit history
        != ECU authority
```

The visible Tuner authority remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Purpose

This slice adds useful 3D inspection movement without allowing camera gestures to become ambiguous edit gestures.

The interaction modes remain explicit:

- **Inspect** — drag orbits the camera, pinch zooms, no tune command is emitted.
- **Select/Edit** — taps perform deterministic projected-cell hit testing and route `SelectCell(row, col)` through the shared command router; camera gestures are disabled.

The system does not infer intent from drag distance or timing. Mode arbitration happens before gesture interpretation.

## Camera state

`Tuner3dCameraState` contains presentation-only:

- yaw;
- pitch;
- zoom.

Camera values are bounded and normalized. Invalid/non-finite gesture input is ignored or repaired to safe presentation defaults.

Current bounds:

- yaw: −80° to +80°;
- pitch: 10° to 80°;
- zoom: 0.65× to 2.5×.

Camera state is currently owned by the 3D Compose surface. It is intentionally absent from:

- `TuneDraft`;
- table projections over JNI;
- tune history;
- project persistence;
- ECU RAM/Burn operations.

A later presentation-persistence slice may preserve preferred camera state without changing tune semantics.

## Projection model

The table is projected from semantic coordinates:

```text
column → grid X
row    → grid depth
value  → normalized Z
```

Yaw rotates the grid in the X/depth plane. Pitch combines depth and value height into the screen vertical axis. The resulting raw projection is fitted into the viewport and then multiplied by zoom.

The camera changes only screen-space presentation. It never changes:

- row/column identity;
- table values;
- editability/protection;
- selection semantics;
- preview semantics.

## Shared rendering and hit geometry

`Tuner3dGeometry.projectCells(...)` accepts the camera state and remains the single projection path used by both rendering and hit testing.

Therefore:

```text
camera state
     ↓
projected semantic vertices
     ├─ Canvas rendering
     └─ bounded hit testing
```

Select/Edit hit testing uses exactly the transformed geometry the user sees. Orbiting or zooming cannot create a second hit-test coordinate system.

## Gesture arbitration

### Inspect mode

Pointer transform gestures are interpreted only as camera input:

- one-finger drag → yaw/pitch orbit;
- pinch → zoom;
- gesture rotation is currently ignored;
- no `TunerCommand` is emitted;
- no JNI call is made;
- no preview/history operation is created.

### Select/Edit mode

Camera transformation gestures are not active.

A tap may resolve to a real projected table vertex. A successful hit emits only semantic `(row, col)` intent to the existing parent callback, which routes through `TunerCommand.SelectCell` and `TunerCommandRouter`.

Visual between-cell locations remain non-authoritative.

## Preview behavior

The 3D surface continues to render the pending Rust-generated preview projection when one exists. Camera movement therefore inspects the prospective surface rather than a separate stale mesh.

Camera orbit/zoom does not cancel, modify, apply or regenerate a tune preview.

## Reset

Inspect mode exposes `Reset 3D view`, restoring the default camera state. This is a presentation reset only.

## Regression requirements

The implementation must prove:

1. camera gestures are bounded;
2. invalid gesture values do not corrupt camera state;
3. invalid stored camera values normalize safely;
4. orbit changes projected screen geometry without changing semantic row/column identity;
5. zoom changes screen spacing without changing table membership;
6. transformed hit testing still resolves the correct semantic cell;
7. extreme valid camera states keep exactly one projected vertex per table cell;
8. Inspect gestures do not route tune commands;
9. Select/Edit continues to use the existing hit → `SelectCell` path;
10. drawing and hit testing use the same camera-aware geometry.

## Deliberately deferred

This slice does not add:

- camera persistence across workspaces/restarts;
- pan/translation separate from orbit;
- perspective projection;
- gesture rotation;
- direct 3D Brush/Pull gestures;
- surface/triangle edit targets;
- ECU RAM write or Burn;
- project persistence.
