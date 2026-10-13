# Tuner selection and preview slice

## Scope

This slice extends the qualified read-only table projection with the first offline interaction path. It deliberately remains narrower than the full Table Interaction Lab behavior.

Implemented here:

- one selected table cell shared across 2D and 3D presentation;
- Rust-owned semantic increment/decrement preview;
- explicit preview base revision;
- guarded Apply against the expected draft revision;
- Rust-owned Undo/Redo history;
- projection reload after a committed history mutation;
- 3D preview visualization without direct 3D mutation gestures.

Not implemented here:

- rectangular or arbitrary multi-cell selection;
- keyboard command routing;
- numeric entry / Set / Add / Scale;
- H/V/2D interpolation;
- smoothing, Brush or Pull;
- axis Re-Bin;
- live lookup overlays;
- ECU RAM write, Burn, controller reconciliation or project persistence.

## Authority model

The UI owns only interaction presentation state:

```text
selected cell
pending preview presentation
2D/3D view choice
```

Rust remains authoritative for tune semantics:

```text
Offline TuneDraft
    ↓
TableEditCommand { Increment | Decrement }
    ↓
TuneDraft::preview
    ↓
previewed semantic value + base revision
    ↓
Apply(expected revision)
    ↓
TuneDraft::apply
    ↓
new draft revision
    ↓
versioned TableProjection ABI
```

The Kotlin layer does not calculate the edited value itself. It asks Rust for the preview result and displays that result in both 2D and 3D.

## Preview versus commit

Selection and preview are intentionally non-mutating.

A preview captures the current `draftRevision`. Apply sends that revision back with the semantic step command. If the draft revision has changed first, Rust rejects the Apply rather than silently applying an edit against a different baseline.

This establishes the transaction invariant needed before expanding to multi-cell and higher-level commands.

## History

Undo and Redo execute against Rust `TuneDraft` history. Kotlin does not maintain a parallel edit stack. After a successful history operation, the UI reloads the packed table projection and verifies that its revision matches the revision returned by native code.

## 2D / 3D relationship

2D currently owns direct cell selection. The selected cell and pending preview are shared presentation state, so switching to 3D shows the same selected point and previewed height/value.

3D remains inspection-only under the current decision set. Direct 3D editing should only be added after the shared semantic command path is established for richer selection and input methods.

## Safety boundary

The visible Tuner continues to identify itself as:

`OFFLINE FIXTURE · NOT ECU APPLIED`

No controller transport, RAM write or Burn call is reachable from this slice.

## Next expansion

After qualification, the next useful interaction layer is a selection model that can represent contiguous ranges and emit one semantic multi-cell command transaction. That should precede interpolation, smoothing and gesture-driven tools so those features share one authoritative selection and history model rather than creating tool-specific state paths.
