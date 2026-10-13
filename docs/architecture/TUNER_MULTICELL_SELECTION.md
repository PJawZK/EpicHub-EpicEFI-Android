# Tuner rectangular multi-cell selection

Status: implementation slice on `phase2/tuner-multicell-selection`.

This slice extends the qualified single-cell selection/preview foundation with rectangular multi-cell selection while preserving Rust ownership of tune semantics.

## Scope

The UI stores only two coordinates:

- anchor cell;
- focus cell.

Those coordinates describe an inclusive rectangle. Kotlin does not expand the rectangle into authoritative edit commands and does not calculate preview values.

Normal 2D tap starts a new one-cell anchor/focus selection. `Extend selection` keeps the current anchor and moves the focus corner on the next cell tap. The same rectangle is rendered in both 2D and 3D.

## Semantic transaction path

```text
anchor + focus + operation
          |
          v
Rust rectangle_step_command
          |
          +-- expands inclusive bounds in row-major order
          |
          v
TuneDraft::preview
          |
          +-- validates every selected cell
          +-- enforces protection, range, quantum and increment
          +-- produces one TablePreview at one base revision
          |
          v
preview TableProjection (same committed revision)
          |
          +-- 2D preview
          +-- 3D preview
          |
          v
explicit Apply(expected revision)
          |
          v
TuneDraft::apply
          |
          v
one mutation transaction + one history entry + one revision advance
```

## Atomicity

The rectangle is one semantic command. If any cell in the rectangle is protected, out of bounds or otherwise invalid, Rust rejects the complete preview. No subset is previewed or applied.

Undo therefore reverts the whole accepted rectangle operation as one history entry rather than creating per-cell history noise.

## Preview representation

The native layer returns a complete versioned `TableProjection` containing Rust-computed preview values. The projection retains the committed draft revision because preview does not mutate `TuneDraft`.

This is deliberate. Kotlin must not derive preview values from `value + increment`, because later operations such as interpolation, smoothing and Brush/Pull can generate different changes for each selected cell. A full semantic preview projection lets those operations use the same UI contract later.

## Protection versus selection

Protected cells remain selectable for inspection. Selection itself is not mutation authority.

If a selected rectangle includes protected cells, the UI shows that fact, but Rust is still responsible for rejecting edit preview/apply. This keeps protection enforcement below the presentation layer.

## 2D / 3D ownership

Both views consume:

- the same committed `TunerTableProjection`;
- the same anchor/focus selection;
- the same Rust-generated preview projection.

3D remains inspection-only under the current durable decision. It visualizes the selected rectangle and preview heights but does not originate edit commands.

## Explicit exclusions

This slice still does not add:

- ECU reads;
- RAM writes;
- Burn/nonvolatile persistence;
- controller reconciliation;
- project persistence;
- numeric Set/Add/Scale entry;
- H/V/2D interpolation;
- smoothing;
- Brush/Pull;
- Re-Bin;
- keyboard command routing;
- direct 3D editing.

The visible authority state remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Qualification expectations

The slice must retain the complete Foundation quality gate and specifically prove:

1. reversed anchor/focus coordinates normalize to the same rectangle;
2. rectangle expansion is deterministic row-major order;
3. preview is non-mutating;
4. an accepted rectangle edit advances the draft once;
5. the accepted rectangle becomes one undoable history entry;
6. any protected cell rejects the entire preview atomically;
7. Kotlin receives Rust-generated preview projections rather than calculating preview values;
8. 2D and 3D consume the same selection and preview state;
9. no ECU mutation API is introduced.

## Next slice

After qualification, the next useful step is to generalize the operation surface over this selected-cell set: typed numeric `Set`, `Add` and `Scale` commands plus direct numeric entry. Those operations should reuse the same Rust preview/apply/history path rather than introducing operation-specific UI authority.
