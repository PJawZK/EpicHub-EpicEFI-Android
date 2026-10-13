# Tuner continuous pointer Brush routing

Status: offline semantic interaction slice stacked on the qualified central Tuner command router.

Authority remains:

```text
pointer / touch motion
        ↓
cell-level TunerCommand intent
        ↓
TunerCommandRouter
        ↓
coalesced Brush path
        ↓
Rust semantic Brush transform
        ↓
TablePreview
        ↓
explicit Apply
        ↓
one TuneDraft revision / history entry
```

The visible authority state remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Why this is not raw pointer painting

Raw Android/Compose motion events are presentation/input data. They are not tune edits.

The 2D pointer adapter converts motion into logical table-cell transitions. The router then:

- drops repeated samples in the same cell;
- collapses forward collinear runs to their endpoints;
- preserves real turns;
- bounds the semantic path to 256 points;
- updates the shared anchor/focus selection only as interaction state;
- emits a new semantic preview only when the coalesced path changes.

This keeps JNI and Rust preview demand tied to meaningful cell transitions rather than display/touch sampling frequency.

## Pointer interaction

Brush draw is an explicit 2D mode. With it enabled, dragging over a data cell begins a Brush stroke. Pointer capture remains with the start cell while the adapter maps the relative pointer position into logical row/column coordinates across neighboring cells.

The displayed Y axis is reversed, so pointer motion upward increases the logical row index and motion downward decreases it.

On drag end, the active gesture is closed but the Rust-generated preview remains pending. The user must still press Apply. Drag cancellation clears the active stroke and preview.

A simple tap is intentionally not repurposed as Brush painting in this slice; ordinary cell selection remains available when Brush draw is disabled.

## Semantic polyline

Rust accepts the coalesced path as a bounded list of `CellCoord` values.

For each editable table cell, Rust measures the minimum Euclidean distance in table-index space to the complete polyline. The existing radius/falloff equation then determines spatial influence.

Each affected cell receives at most one requested displacement for the complete stroke:

```text
target = committedValue + delta × strength × spatialWeight
```

This makes the result independent of pointer sampling density. Retracing or receiving many intermediate points does not repeatedly compound the same cell simply because the OS delivered more motion events.

The path is canonicalized again in Rust (duplicate removal and forward-collinear collapse) so correctness does not depend on the Kotlin router behaving perfectly.

## Shared existing semantics

The polyline Brush reuses the production Brush semantics from `TUNER_BRUSH_PULL.md`:

- radius and falloff are editing weights in grid/index space;
- they are not ECU lookup/interpolation weights;
- protected/pinned cells remain fixed;
- all targets derive from the same committed pre-transform surface;
- normal TuneDraft bounds, quantum and canonical-value validation apply;
- any invalid editable target rejects the complete preview;
- preview does not advance draft revision;
- Apply is stale-revision guarded;
- Apply creates one central history entry;
- Undo/Redo remains TuneDraft-owned.

## JNI boundary

The Kotlin fixture packs the bounded cell path into one `IntArray` as row/column pairs and makes one JNI call per semantic preview update. There is no per-cell JNI mutation path.

The JNI adapter rejects:

- empty paths;
- odd-length row/column arrays;
- more than 256 path points;
- negative coordinates;
- malformed numeric Brush parameters.

Rust performs table-bound validation after JNI conversion.

## UI behavior

2D and 3D continue to consume the same Rust preview projection. The selected rectangle reflects first/last gesture points, while preview highlighting reflects the actual cells changed by the complete curved path.

3D remains inspection-only and does not originate Brush gestures in this slice.

## Regression expectations

1. beginning a stroke creates a one-cell Brush preview intent;
2. duplicate cell samples do not create new semantic previews;
3. straight runs collapse to endpoints;
4. direction changes remain in the path;
5. dense and sparse samples describing the same polyline produce identical Rust previews;
6. curved/polyline Brush affects the path turns rather than only first→last straight-line geometry;
7. pointer sampling frequency does not amplify cell displacement;
8. invalid/out-of-range path cells are rejected;
9. preview remains non-mutating;
10. one stroke Apply creates one history transaction;
11. pointer cancellation removes the pending gesture preview;
12. drag end retains the preview for explicit user approval;
13. protected cells remain fixed;
14. both 2D and 3D display the same prospective values.

## Deliberately deferred

This slice does not add:

- pressure-sensitive Brush strength;
- velocity-sensitive Brush behavior;
- automatic Apply on pointer release;
- direct 3D Brush painting;
- stylus-specific affordances;
- custom sampled falloff curves;
- ECU RAM write, Burn, controller reconciliation or project persistence.
