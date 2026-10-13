# Direct 3D Brush / Pull gestures

Status: offline Tuner interaction implementation slice stacked on the qualified 3D camera contract.

Authority remains:

```text
3D pointer geometry
      ↓
semantic TunerCommand intent
      ↓
existing Rust Brush/Pull preview
      ↓
explicit Apply(expectedRevision)
      ↓
TuneDraft + central history
```

Direct 3D gestures do not calculate canonical table values, mutate `TuneDraft`, write ECU RAM or Burn.

## Gesture arbitration

Interaction mode is resolved before a gesture is interpreted.

### Inspect

- drag/pinch -> presentation-only camera;
- no edit commands.

### Select/Edit without Direct gesture

- tap -> nearest bounded projected vertex -> `SelectCell(row, col)`;
- no camera gesture.

### Select/Edit + Direct gesture + Brush

- drag start must hit a real projected table vertex;
- the hit emits the existing `BeginBrushStroke` command;
- later vertex hits emit the existing `ContinueBrushStroke` command;
- release emits `EndBrushStroke`;
- cancellation emits `CancelBrushStroke`;
- the shared router performs duplicate removal / straight-run coalescing;
- Rust evaluates the entire semantic path against one committed table surface;
- release leaves the prospective surface visible until explicit Apply or Cancel.

This is the same Brush semantic path already used by 2D. 3D does not have a separate Brush engine.

### Select/Edit + Direct gesture + Pull

- drag start must hit one real projected table vertex;
- that semantic cell becomes the shared selection / Pull center;
- vertical screen displacement is converted to whole descriptor `editIncrement` steps;
- one 18 dp gesture step requests one table edit increment;
- upward movement requests positive displacement; downward movement requests negative displacement;
- gesture displacement is bounded to +/-64 steps;
- repeated motion inside the same step does not request another preview;
- each changed step emits the existing `PreviewBrushPull(PULL_DEFORM, ...)` command;
- configured radius, strength and falloff are reused from the shared Brush/Pull controls;
- Rust remains responsible for bounds, protected focus handling, quantization, falloff, prospective values and rejection;
- release leaves the last prospective surface visible until explicit Apply or Cancel.

Direct 3D Pull deliberately does not use the typed `Value displacement` field as its gesture displacement. The gesture itself supplies signed displacement in semantic edit-increment units. The ordinary Preview Pull action continues to use the typed displacement field.

## Stable gesture geometry vs preview geometry

Rendering and gesture capture use different value surfaces while a direct edit gesture is active:

```text
committed projection + camera
          ↓
   gesture hit geometry

Rust prospective projection + same camera
          ↓
      rendered mesh
```

This separation is intentional.

If hit testing followed the prospective surface, every Brush/Pull preview could move projected vertices during the active gesture. In Compose it could also change pointer-input keys and restart the gesture detector. Direct gesture capture therefore stays anchored to the committed geometry until release, while the prospective result remains visible as feedback.

A later gesture begins from the then-current committed revision after Apply, or from the unchanged committed surface after Cancel.

## Shared controls

The existing Tuner state remains the only control source:

- `Direct gesture` toggle;
- Brush vs Pull mode;
- radius;
- strength;
- falloff;
- Brush typed displacement for Brush strokes / ordinary preview actions.

No 3D-only calibration settings are introduced.

The Direct gesture toggle means:

- 2D + Brush: continuous 2D Brush path;
- 2D + Pull: no direct Pull gesture yet; ordinary Preview Pull remains available;
- 3D Select/Edit + Brush: direct projected-vertex Brush path;
- 3D Select/Edit + Pull: direct vertical Pull deformation;
- 3D Inspect: camera owns gestures regardless of the toggle.

## Preview and history

Neither Brush nor Pull auto-applies on release.

```text
gesture
  ↓
Rust preview at committed revision N
  ↓
visible prospective surface
  ↓
Apply preview
  ↓
one accepted semantic transaction
  ↓
revision N+1 + one Undo entry
```

Stale-revision protection remains the same as other Tuner operations.

## Safety / protection

- protected Brush cells remain pinned according to the existing Rust Brush contract;
- a protected Pull center is rejected by Rust;
- out-of-range Pull results reject the complete prospective transform;
- invalid presentation inputs produce no Pull delta;
- gesture magnitude is bounded before preview requests;
- no screen coordinate is treated as a calibration value.

## Regression requirements

This slice must preserve or prove:

1. 3D Brush emits the same routed semantic Brush path used by 2D.
2. Pull movement is expressed in whole descriptor edit-increment steps.
3. sub-step Pull motion emits no displacement.
4. large Pull motion is bounded.
5. non-finite/invalid presentation inputs acquire no edit authority.
6. positive/negative vertical direction is deterministic.
7. active direct gestures use committed geometry even while the prospective mesh changes.
8. camera gestures remain disabled in Select/Edit.
9. edit gestures remain disabled in Inspect.
10. release does not apply the tune.
11. Apply remains one history transaction.
12. 2D and 3D still render the same Rust-generated prospective table.

## Deliberately deferred

This slice does not add:

- direct 2D Pull dragging;
- triangle/surface hit targets between real cells;
- freeform value-space sculpting;
- pressure-sensitive stylus strength;
- camera + editing in the same ambiguous gesture;
- camera persistence;
- ECU RAM write / Burn;
- controller reconciliation;
- project persistence.
