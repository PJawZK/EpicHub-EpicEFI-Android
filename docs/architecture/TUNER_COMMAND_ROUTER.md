# Tuner command / input router

Status: offline interaction foundation.

This slice centralizes **input intent**, selection/navigation semantics, shortcut discovery, preview dispatch, Apply/Cancel, and Undo/Redo routing without moving tune mathematics out of Rust.

The authority boundary remains:

```text
input device / UI control
        ↓
TunerCommand
        ↓
TunerCommandRouter
        ├─ selection/navigation state
        └─ semantic effect request
                ↓
        existing Rust-backed fixture operation
                ↓
            TablePreview
                ↓
        explicit Apply(expectedRevision)
                ↓
       Rust TuneDraft + central history
```

and still:

```text
TuneDraft edit
    != ECU RAM write
    != Burn / nonvolatile persistence
```

The visible Tuner authority remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Why this layer exists

Before this slice, touch selection, the Extend-selection toggle, preview buttons, Apply, Undo, and Redo each entered local Composable functions directly. That worked for one UI, but would force keyboard, rotary, accessibility, direct 3D editing, and future pointer gestures to duplicate behavior.

The command router makes those inputs adapters rather than edit owners.

## Command model

Current commands cover:

- select a cell;
- move selection up/down/left/right;
- transient selection extension (`Shift+Arrow`);
- persistent Extend-selection mode;
- clear selection;
- preview Increment/Decrement;
- preview Set/Add/Scale;
- preview H/V/2D interpolation;
- preview smoothing;
- preview Brush/Pull;
- Apply preview;
- Cancel preview;
- Undo;
- Redo.

A command can update only interaction state, emit an effect for the existing semantic edit path, or do both.

## Selection semantics

The shared selection remains an anchor/focus rectangle:

```text
anchor ─────────────┐
  │                 │
  │    selection    │
  │                 │
  └──────────── focus
```

Rules:

1. ordinary cell selection collapses anchor and focus to that cell;
2. persistent Extend mode keeps the existing anchor and moves focus;
3. `Shift+Arrow` extends transiently even when persistent Extend is off;
4. ordinary arrow movement collapses an existing rectangle to the moved focus cell;
5. with no selection, the first movement command creates a deterministic row-0/col-0 selection;
6. navigation clamps at table bounds;
7. because the 2D table renders Y rows high-to-low, visual Up increases the semantic row index and Down decreases it;
8. every selection/navigation change cancels any pending preview so stale visual intent cannot survive a changed target selection.

These rules are pure Kotlin state reduction and do not inspect or mutate table values.

## Command effects

Selection state is intentionally separated from semantic edit execution.

For example:

```text
PreviewStep(INCREMENT)
       ↓
router emits PreviewStep effect
       ↓
Compose adapter supplies current selection
       ↓
OfflineTunerFixture.previewRectangleStep(...)
       ↓
Rust TuneDraft preview
```

The same effect path is used whether the command originated from a button or keyboard shortcut.

Numeric values, interpolation, smoothing, Brush/Pull geometry, table constraints, quantization, stale revision rejection, Apply mutation, and edit history remain below this layer.

## Keyboard adapter

Keyboard events are translated to the same commands only while the table surface owns focus.

Current bindings:

```text
Arrow keys          Move selection
Shift + Arrow       Extend selection from anchor
+                   Preview Increment
-                   Preview Decrement
Enter               Apply current preview
Esc                 Cancel current preview
Ctrl + Z            Undo
Ctrl + Y            Redo
Ctrl + Shift + Z    Redo
```

The table requests focus after cell interaction. Text-entry controls live outside that focused table surface, so their normal arrow/text-edit/undo behavior is not intercepted by Tuner navigation.

The Android key adapter contains no tuning semantics. It maps platform key codes to a platform-neutral `TunerKeyStroke`, which is resolved by `TunerShortcutRegistry`.

## Command registry

`TunerShortcutRegistry` is the first shared discoverability source for command IDs, labels, and shortcut presentation.

It is intended to become the common source for:

- keyboard help;
- accessibility actions;
- rotary/controller binding configuration;
- command palette/help overlays;
- automated input-path tests.

The registry does not execute tune edits.

## Input adapters

The architecture now expects inputs to converge like this:

```text
Touch / mouse
Keyboard
Rotary encoder
Accessibility action
Direct 3D hit test
Brush pointer gesture
Automated UI test
        │
        └────────→ TunerCommand
                      ↓
               TunerCommandRouter
```

A new input device should normally require an adapter only. It must not gain a parallel implementation of selection, smoothing, interpolation, Brush/Pull, Apply, or history.

## 2D / 3D relationship

The router owns one shared selection state for both views. Switching between 2D and 3D therefore does not create a second selection model.

`TUNER_3D_EDITING_CONTRACT.md` now satisfies the D-016 requirement for a separately accepted 3D semantic edit contract. Direct 3D interaction is therefore allowed to become an input adapter, but not an edit owner.

The accepted direction is:

```text
3D camera / mesh / hit-test state
             ↓
      semantic row/column
             ↓
        TunerCommand
             ↓
     TunerCommandRouter
             ↓
 existing Rust semantic edit path
```

3D hit-testing may emit `SelectCell`, movement/selection commands, Brush stroke commands, or the same semantic preview commands already used by 2D/keyboard controls. It must not calculate calibration values, own a separate preview, or mutate table vertices directly.

Camera orbit/zoom remains presentation state and emits no tuning command. An explicit interaction mode must distinguish camera manipulation from calibration-edit intent before direct 3D editing is implemented.

## Regression requirements

This slice is expected to prove at least:

1. default cell selection collapses anchor/focus;
2. persistent Extend preserves anchor;
3. Shift+Arrow preserves anchor without persistent Extend;
4. ordinary movement collapses a rectangle;
5. Up/Down follow the displayed Y orientation;
6. movement clamps at table bounds;
7. no-selection movement creates deterministic initial selection;
8. invalid cell selection is not consumed;
9. selection changes cancel pending preview;
10. edit commands produce effects without mutating selection state;
11. +/− map to the same Increment/Decrement preview commands used by buttons;
12. Enter maps to Apply preview;
13. Esc maps to Cancel preview;
14. Ctrl+Z maps to Undo;
15. Ctrl+Y and Ctrl+Shift+Z map to Redo;
16. unrelated Ctrl/Alt combinations are not consumed;
17. registry command IDs remain unique;
18. button paths and keyboard paths converge before semantic edit execution;
19. text-entry fields remain outside table keyboard interception;
20. Rust remains the only authority for table-value transformation and history mutation.

The additional regression requirements for direct 3D editing are defined in `TUNER_3D_EDITING_CONTRACT.md`, including hit-test correctness, camera/edit separation, 2D/3D command parity and representation-neutral history.

## Deliberately deferred

This slice does not yet add:

- configurable/remappable shortcuts;
- long-press or key-repeat coalescing policy;
- direct rotary encoder hardware input;
- accessibility action publication;
- command palette UI;
- direct 3D hit-test implementation;
- direct 3D Brush/Pull gestures;
- ECU RAM write or Burn;
- project persistence.

Continuous 2D pointer Brush strokes are implemented by the later pointer-Brush slice. Direct 3D editing is now architecturally accepted but remains implementation work; it must extend this router rather than bypassing it.