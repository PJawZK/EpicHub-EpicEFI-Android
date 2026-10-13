# Tuner smoothing

This slice adds deterministic table smoothing to the offline semantic Tuner fixture. It extends the same authority model used by numeric edits and interpolation: Rust owns the transform, preview is non-mutating, and Apply commits the preview as one revision/history entry.

## Scope

Implemented:

- rectangular target selection;
- fixed 3×3 weighted kernel;
- user-selectable smoothing strength from >0 through 100 percent;
- Rust parsing, validation, blending, quantization, protection checks, preview, stale-revision rejection, commit, undo, and redo;
- typed JNI/Kotlin bridge;
- one shared prospective projection rendered by both 2D and 3D views.

Not implemented here:

- repeated passes;
- variable kernel radius;
- axis smoothing;
- Brush/Pull;
- Re-Bin;
- direct 3D editing;
- ECU RAM writes or Burn.

## Kernel

The smoothing kernel is the separable 3×3 weight matrix derived from `[1 2 1] × [1 2 1]`:

```text
1 2 1
2 4 2
1 2 1
```

For each selected target cell, Rust evaluates the weighted neighborhood on the committed table surface. Missing samples beyond the physical table boundary are omitted and the remaining weights are renormalized.

The target is then blended by strength:

```text
smoothed = weighted_sum / available_weight_sum
raw_target = original + (smoothed - original) * strength
```

`strength` is the user percentage divided by 100. The resulting target is passed through the existing `TuneDraft::preview(Set(...))` path, so normal table bounds, quantum normalization, representability, and protection rules remain authoritative.

## Selection boundary semantics

The selected rectangle defines which cells may change, not which cells may contribute samples.

A target cell at a selection edge may therefore read neighboring committed values one cell outside the selected rectangle. This avoids introducing an artificial smoothing discontinuity merely because the user selected a sub-region.

A protected cell outside the selection may contribute as a read-only source sample. If a protected cell itself is selected as a smoothing target, the entire transform is rejected atomically.

## Determinism and source surface

Every target is derived from the same committed pre-transform projection. Smoothing does not update one cell and then feed that changed result into the next cell in row-major order.

This makes the transform independent of iteration order and ensures preview and Apply describe one semantic operation.

## Preview and history

```text
committed TuneDraft
      +
rectangle + strength
      ↓
Rust smoothing transform
      ↓
per-cell TuneDraft preview(Set(target))
      ↓
one aggregate TablePreview
      ↓
2D / 3D prospective projection
      ↓
explicit Apply
      ↓
one revision + one history entry
```

A no-op transform is rejected instead of creating an empty history entry. Apply requires the same draft revision used to generate the preview.

## Authority boundary

Kotlin owns:

- selection interaction;
- strength text entry;
- preview presentation;
- explicit Apply action.

Rust owns:

- numeric parsing and range validation;
- kernel and edge behavior;
- immutable-source evaluation;
- blending;
- table-value normalization;
- protection and bounds checks;
- preview generation;
- stale-revision rejection;
- mutation and history.

The visible state remains:

`OFFLINE FIXTURE · NOT ECU APPLIED`

No ECU read, controller reconciliation, live RAM write, Burn, or project persistence is added by this slice.
