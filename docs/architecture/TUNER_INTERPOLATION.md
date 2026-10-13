# Tuner semantic interpolation

Status: implementation slice on `phase2/tuner-interpolation`.

This slice adds deterministic horizontal, vertical, and two-dimensional interpolation over the qualified rectangular selection / preview / Apply / history path.

## Design rule

Interpolation is a semantic tune transform, not a presentation shortcut.

Kotlin owns only:

- the selected anchor/focus rectangle;
- the interpolation mode requested by the user;
- display of the Rust-generated preview.

Rust owns:

- selection normalization;
- interpolation geometry;
- use of the X/Y bin coordinates;
- per-cell target generation;
- value quantization;
- bounds and representability checks;
- protected-cell enforcement;
- preview generation;
- stale-revision rejection;
- atomic commit and history.

## Why interpolation uses axis coordinates

EpicHub interpolation deliberately uses the actual table axis values rather than treating every grid interval as equally spaced.

For a non-uniform RPM axis such as:

```text
1000, 1500, 3000
```

the middle bin is 25% of the distance between 1000 and 3000, not 50% merely because it is the middle grid column.

That rule applies independently to X and Y and makes interpolation describe the calibration domain instead of the screen geometry.

## Modes

### Horizontal

For every selected row, the first and last selected columns are the endpoints. Every selected cell in that row is linearly interpolated according to its X-axis coordinate.

A selection spanning only one column is rejected because it does not define two horizontal endpoints.

### Vertical

For every selected column, the first and last selected rows are the endpoints. Every selected cell in that column is linearly interpolated according to its Y-axis coordinate.

A selection spanning only one row is rejected because it does not define two vertical endpoints.

### Two-dimensional

The four corners of the selected rectangle define a bilinear surface. Each selected cell is evaluated from its X/Y axis position relative to those four corners.

A two-dimensional interpolation requires at least two distinct rows and two distinct columns.

## Semantic path

```text
anchor + focus + interpolation mode
                 |
                 v
Rust TableRectangle
                 |
                 v
preview_interpolation
                 |
                 +-- normalize rectangle bounds
                 +-- validate interpolation geometry
                 +-- derive target from real X/Y axis coordinates
                 +-- reuse TuneDraft::preview(Set(target)) per selected cell
                 |      +-- protection
                 |      +-- bounds
                 |      +-- quantum normalization
                 |      +-- representability
                 |
                 v
aggregate TablePreview at one base revision
                 |
                 v
full preview TableProjection
          /                 \
        2D                   3D
                 |
                 v
explicit Apply(expected revision)
                 |
                 v
TuneDraft::commit_preview
                 |
                 v
one revision advance + one undo/redo history entry
```

The per-cell semantic preview calls are only used to reuse the existing authoritative value-domain rules. They do not mutate the draft. The aggregate preview is what is committed.

## Atomicity and protection

Every cell in the selected rectangle is validated, including boundary cells that serve as interpolation endpoints or corners.

If any selected cell is protected, out of bounds, or otherwise invalid, the complete interpolation preview is rejected. EpicHub does not silently interpolate only the editable subset.

The resulting interpolation is one history transaction regardless of how many cells receive different values.

## No-op behavior

If interpolation would not change any selected cell after profile-domain quantization, the operation is rejected as a no-op rather than creating an empty history entry and artificial draft revision.

## Preview authority

Preview retains the committed draft revision. Interpolation does not advance revision until explicit Apply.

The pending preview records:

- rectangle;
- interpolation mode;
- base draft revision;
- complete Rust-generated preview projection.

Apply regenerates the same semantic interpolation against the expected revision. If the draft changed after preview, Apply is rejected as stale.

## 2D / 3D relationship

Both views continue to consume exactly the same:

- committed table projection;
- rectangular selection;
- Rust-generated preview projection.

The 3D surface therefore visualizes the prospective interpolated shape without implementing its own interpolation math.

3D remains inspection-only under the current durable decision and does not originate interpolation commands.

## Explicit exclusions

This slice does not add:

- ECU reads;
- live RAM writes;
- Burn/nonvolatile persistence;
- controller reconciliation;
- project persistence;
- smoothing;
- Brush/Pull;
- Re-Bin;
- keyboard command routing;
- direct 3D editing.

The visible authority state remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Qualification expectations

The slice must retain the full Foundation quality gate and specifically prove:

1. horizontal interpolation uses real X-axis spacing;
2. vertical interpolation uses real Y-axis spacing;
3. 2D interpolation uses bilinear X/Y geometry;
4. non-uniform axes do not collapse to equal-index interpolation;
5. profile quantum normalization still applies to derived values;
6. preview remains non-mutating;
7. an accepted interpolation advances the draft once;
8. the complete transform is one undo/redo history entry;
9. any protected selected cell rejects the full transform;
10. invalid interpolation geometry is rejected;
11. no-op interpolation does not create a history entry;
12. 2D and 3D consume the same preview projection;
13. no ECU mutation path is introduced.

## Next slice

After qualification, the next useful transform is deterministic smoothing with a previewable parameter contract. It should reuse the same Rust `TablePreview` / explicit Apply / one-history-entry model rather than mutating cells incrementally from UI gestures.
