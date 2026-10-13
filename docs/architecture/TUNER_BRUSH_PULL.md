# Tuner Brush / Pull semantic editing

Status: offline semantic implementation slice.

Authority boundary remains:

```text
TuneDraft edit
    != ECU RAM write
    != Burn / nonvolatile persistence
```

The visible Tuner state remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Prior design reconciliation

The Table Interaction Lab already established weighted Brush/Pull editing, adjustable radius / strength / falloff, pinned cells that resist deformation, one authoritative 2D/3D table state, and Rust ownership of batched Brush/Pull edits.

The Lab did not establish a production-authoritative numeric falloff formula. Its browser implementation remains prototype evidence, not production architecture. This slice therefore preserves the behavior and makes the production transform semantics explicit rather than copying the prototype mechanics.

Brush/Pull weights are **editing spatial falloff**. They are not ECU runtime lookup/interpolation contribution weights.

## Production semantic transform

Both operations consume the committed `TuneDraft` table projection and produce a non-mutating `TablePreview`.

Inputs:

```text
anchor cell
focus cell
mode
signed value displacement
radius in grid cells
strength percent
falloff exponent
```

All numeric text from the fixture UI crosses JNI as text and is parsed/validated in Rust.

## Brush

Brush is a swept additive stroke from the selection anchor to the selection focus.

For every table cell, Rust computes the Euclidean distance in **table index space** to the anchor→focus line segment. Cells outside the requested inclusive radius are unaffected.

This deliberately uses grid topology rather than RPM/load engineering-unit distance. Brush radius is a visual/editing footprint, not calibration-axis interpolation.

A collapsed selection (anchor == focus) becomes one brush dab.

## Pull

Pull is a rubber-sheet local deformation centered on the selection focus cell.

The requested signed displacement is strongest at the focus and falls away radially in table index space. The selection anchor does not change Pull geometry; it remains part of the shared selection model and can later support pointer gesture provenance without creating another tune mutation path.

A protected focus cell cannot be grabbed and rejects Pull.

## Falloff

For a cell within radius `r` at distance `d`, the base influence is:

```text
base = 1 - d / (r + 1)
weight = base ^ falloffExponent
```

The radius is inclusive. The `+1` keeps the outermost included ring non-zero; for radius 4 and exponent 1, a cell exactly four grid cells away receives 20% influence.

Special cases:

- radius `0`: only the exact stroke/center receives weight 1;
- falloff exponent `0`: hard brush, all cells inside the radius receive weight 1;
- exponent `1`: linear falloff;
- exponent `2`: softer/narrower edge response.

Current bounds:

- radius: 0–32 cells;
- strength: >0–100%;
- falloff exponent: 0–8;
- displacement: finite signed value.

## Value calculation

Each affected editable cell is derived independently from the same committed pre-transform surface:

```text
target = current + displacement × strength × spatialWeight
```

The target is then passed through the existing `TuneDraft::preview(Set(target))` path, so Brush/Pull inherits the same table bounds, representable quantum, normalization, and canonical-value validation as every other edit.

No cell is computed from a previously transformed neighbor.

## Protection / pins

Brush/Pull follows the prior Table Interaction Lab pin semantics rather than the rectangle-transform semantics:

- protected/pinned cells inside the influence field remain unchanged;
- Brush continues around them;
- Pull continues around protected neighbors;
- a protected Pull focus rejects the operation because the grabbed center itself is fixed.

Any invalid editable target (for example a result outside the table value domain) rejects the **whole preview**. No partial mutation occurs.

## Preview and history

```text
selection + parameters
        ↓
Rust Brush/Pull transform
        ↓
aggregate TablePreview
        ↓
shared packed TableProjection
        ↓
2D + 3D preview
        ↓
explicit Apply(expectedRevision)
        ↓
one draft revision + one history entry
```

Preview does not mutate the draft. Apply is stale-revision guarded. Undo/Redo remains owned by `TuneDraft`.

The Compose layer compares committed and preview projections only to determine which cells should be highlighted. It does not calculate edit values or falloff.

## Current interaction adapter

This slice reuses the existing shared selection model:

- Brush uses anchor→focus as the stroke segment;
- Pull uses focus as the deformation center;
- the user enters displacement, radius, strength and falloff explicitly;
- both 2D and 3D render the same Rust-generated prospective surface;
- 3D remains inspection-only and does not originate edits yet.

Direct pointer stroke capture and direct 3D hit-test editing can later emit the same Brush/Pull semantic parameters. They must not create a second mutation implementation.

## Regression requirements

The implementation is expected to prove at least:

1. Brush sweeps across the anchor→focus segment.
2. Pull centers on focus.
3. radius and falloff use grid/index space even with non-uniform calibration axes.
4. outer-radius influence is deterministic and non-zero.
5. strength scales displacement before quantization.
6. hard falloff works with exponent 0.
7. pinned cells resist Brush/Pull mutation.
8. protected Pull focus is rejected.
9. any invalid editable target rejects the whole preview without draft mutation.
10. preview leaves the draft revision unchanged.
11. Apply creates one revision/history entry.
12. Undo reverses the complete Brush/Pull transaction.
13. 2D and 3D display identical preview values.
14. preview highlighting follows actual changed cells, including cells outside the rectangle selection.
15. Brush/Pull weights remain distinct from future ECU live-lookup contribution weights.

## Deliberately deferred

This slice does not add:

- continuous pointer/touch stroke sampling;
- gesture coalescing across multiple motion events;
- 3D direct editing;
- custom falloff curves;
- multiple Brush operation families such as local-average/gradient painting;
- ECU RAM write or Burn;
- controller reconciliation;
- project persistence.
