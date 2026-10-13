# EpicHub Tuner 3D Editing Contract

Status: accepted architectural contract satisfying the D-016 condition for direct 3D editing.

## Purpose

D-016 deliberately kept the first 3D table surface inspection-only until EpicHub had a separately accepted semantic edit contract. That prerequisite now exists.

This contract allows 3D to become an **editable input surface** without becoming a second tune model, a second edit engine, or a second history authority.

The governing rule is:

> 2D and 3D may differ in presentation, hit-testing, camera state and gesture mapping, but they must converge on the same semantic selection, preview, edit commands, `TuneDraft`, validation and history.

This document does not grant ECU RAM-write or Burn authority.

## Relationship to D-016

D-016 remains unchanged:

- semantic tune/profile/runtime truth belongs below presentation;
- Compose owns camera, zoom/pan, gesture arbitration, layout and other presentation state;
- presentation transformations may not reorder or mutate authoritative tune data.

The phrase in D-016 that 3D remains view-only "unless a future separately accepted semantic edit contract is added" is now satisfied by this document.

The accepted change is therefore narrow:

```text
Before:
3D presentation → inspect shared table state

After:
3D presentation → inspect shared table state
                → resolve pointer/gesture to semantic cell intent
                → emit existing TunerCommand
```

Nothing below `TunerCommand` becomes 3D-specific.

## Authority model

```text
Rust TuneDraft / semantic table state
              │
         TableProjection
          /          \
         /            \
   2D TableGrid     3D TableView
      │                 │
      │          camera / projection
      │          hit-test / gesture
      │                 │
      └──────┬──────────┘
             ↓
        TunerCommand
             ↓
      TunerCommandRouter
             ↓
     semantic preview request
             ↓
       Rust transform
             ↓
        TablePreview
             ↓
   explicit Apply(expectedRevision)
             ↓
       Rust TuneDraft
             ↓
     one history transaction
```

### Rust remains authoritative for

- table identity and dimensions;
- legal numeric domain, quantum and canonical values;
- editable/protected state;
- selection-resolved value transforms once an edit is requested;
- Set/Add/Scale/Increment/Decrement;
- interpolation;
- smoothing;
- Brush/Pull geometry and value displacement;
- preview validity;
- stale-revision rejection;
- commit mutation;
- undo/redo history.

### Compose may own for 3D

- camera rotation, tilt, zoom and reset;
- projection matrices and display geometry;
- mesh drawing;
- visual Z exaggeration;
- perspective/orthographic presentation choice if added later;
- pointer-to-ray conversion;
- hit-testing the rendered mesh;
- hover/focus affordances;
- gesture arbitration between camera manipulation and edit gestures;
- mapping a successful hit to a semantic row/column coordinate;
- mapping platform input into `TunerCommand`.

A 3D hit test is presentation evidence only. It becomes tuning intent only after it resolves to a valid semantic cell coordinate and is routed through the shared command model.

## Shared selection

There is exactly one table selection model.

3D must not create a private selected vertex/cell state that can diverge from 2D. A successful 3D cell hit emits the same `SelectCell(row, col)` command used by other input adapters.

Consequences:

- selecting a cell in 3D updates the selection seen in 2D;
- selecting in 2D updates the highlighted cell/region in 3D;
- switching representations preserves selection;
- preview applicability is representation-neutral;
- selection changes continue to invalidate stale pending preview according to the command-router rules.

The 3D view may retain transient presentation-only hover information, but hover is not semantic selection.

## Direct edit behavior

3D direct editing must reuse existing semantic commands rather than inventing 3D-specific value mutation APIs.

Examples:

```text
3D hit + tap
    → SelectCell(row, col)

3D selected cell + keyboard +
    → PreviewStep(INCREMENT)

3D selected region + command action
    → PreviewNumeric / PreviewInterpolation / PreviewSmoothing

3D Brush gesture
    → BeginBrushStroke / ContinueBrushStroke / EndBrushStroke
    → existing bounded/coalesced Brush path

Apply
    → ApplyPreview(expectedRevision)
```

A future 3D pull/deform gesture may provide a presentation-derived focus cell or displacement intent, but the actual falloff, weighting, quantization, protection and table mutation remain Rust-owned.

## Camera/edit gesture arbitration

3D adds a real ambiguity that 2D does not: the same drag motion could rotate the camera or edit the calibration surface.

Therefore direct 3D editing must use an explicit interaction mode or equally unambiguous modifier/gesture contract. Accidental editing while orbiting the camera is not acceptable.

Initial implementation should prefer explicit modes such as:

```text
Inspect / Orbit
Select / Edit
Brush
```

Exact labels may evolve, but the invariant is locked: **camera manipulation and calibration mutation intent must be distinguishable before a semantic edit command is emitted.**

Camera motion never changes selection merely because the pointer crosses mesh cells.

## Hit-testing requirements

A 3D hit-test adapter must:

1. operate on the currently rendered `TableProjection` revision;
2. resolve the visual mesh triangle/vertex/cell back to semantic row/column coordinates;
3. reject misses and out-of-range coordinates rather than clamping an arbitrary hit into the table;
4. account for display orientation without reordering source table storage;
5. avoid using Z exaggeration as tune-value authority;
6. remain deterministic for a fixed projection, camera and pointer coordinate;
7. emit no mutation by itself.

If the view interpolates a continuous surface between table vertices for rendering, that interpolation is presentation geometry. It must not silently imply a new editable semantic sample between cells.

## Preview and Apply

3D consumes the same shared prospective table projection as 2D.

It may visualize changed cells, displaced surface regions or preview deltas differently, but it does not calculate an independent preview.

The lifecycle remains:

```text
semantic command
    ↓
Rust preview from committed TuneDraft
    ↓
shared prospective TableProjection
    ↓
2D and 3D render same prospective values
    ↓
explicit Apply(expectedRevision)
    ↓
one TuneDraft revision/history transaction
```

Changing camera state must never invalidate a preview. Changing semantic selection/target or committed draft revision may invalidate it according to existing rules.

## Live lookup overlay

Future live ECU lookup position/contributors/trail remain a separate overlay sourced from `ChannelStore` / runtime state.

3D may render that overlay spatially, but live lookup does not become selection and does not grant edit authority.

The following remain distinct:

- selected cell/region;
- live active point;
- interpolation contributors/weights;
- edited-since-baseline markers;
- pending preview changes;
- camera focus/hover.

## Performance boundary

3D editing must not introduce per-vertex or per-frame JNI traffic.

Expected split:

- projection/mesh data changes when semantic table revision or preview revision changes;
- camera transforms stay entirely in Compose/rendering state;
- hit testing stays presentation-side for the currently decoded projection;
- semantic commands cross the existing coarse edit boundary;
- Brush paths are bounded/coalesced before the Rust preview request;
- high-rate live lookup is a separate bounded overlay path.

A camera orbit at display refresh rate must therefore require zero tune-state JNI mutation calls.

## Regression requirements

Before direct 3D editing is considered qualified, tests must prove at least:

1. a known 3D mesh hit resolves to the correct semantic row/column;
2. table display orientation does not swap or reverse authoritative storage;
3. a miss emits no `SelectCell` or edit command;
4. 3D selection and 2D selection are the same router state;
5. a 3D-originated Increment/Decrement produces the same Rust preview as the equivalent 2D/keyboard command;
6. a 3D-originated multi-cell operation remains one semantic preview and one history transaction on Apply;
7. protected cells behave identically regardless of input representation;
8. camera orbit/zoom emits no tune mutation command;
9. camera state changes do not invalidate a semantic preview;
10. selection/semantic-target changes do invalidate stale preview where existing router rules require it;
11. preview remains non-mutating until explicit Apply;
12. Undo/Redo are representation-neutral;
13. 3D rendering consumes the same committed/prospective values and draft revision as 2D;
14. no 3D-specific tune-history store exists;
15. no per-frame/per-vertex JNI edit traffic is introduced.

## Initial implementation slice

The first implementation after this contract should stay deliberately small:

1. add deterministic 3D cell hit testing against the existing mesh;
2. add an explicit 3D `Select/Edit` interaction mode separate from camera orbit;
3. route a hit to `SelectCell(row, col)`;
4. render shared selection in 3D;
5. allow the existing keyboard/command operations to act on that 3D-selected cell/region;
6. prove parity with the equivalent 2D commands;
7. only then add direct 3D Brush/Pull gestures.

This gives 3D real editing capability without coupling the first hit-test slice to a complex free-form deformation gesture.

## Deliberately not granted by this contract

- ECU RAM writes;
- Burn/nonvolatile persistence;
- a second tune model;
- a second selection model;
- a 3D-specific undo stack;
- direct mutation from mesh vertices;
- arbitrary between-cell tune values created by visual interpolation;
- camera-driven edits;
- per-frame JNI mutation traffic;
- live lookup becoming edit authority.

Those boundaries remain governed by the existing tuning, persistence and ECU-authority decisions.