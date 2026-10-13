# Tuner numeric Set / Add / Scale operations

Status: implementation slice on `phase2/tuner-numeric-ops`.

This slice extends the qualified rectangular selection path with typed numeric `Set`, `Add`, and `Scale` operations while preserving Rust ownership of tune semantics.

## Scope

The selected rectangle remains an anchor/focus pair owned as presentation interaction state by Kotlin. Numeric entry adds two presentation values:

- selected operation: `Set`, `Add`, or `Scale`;
- raw operand text entered by the user.

Kotlin does not parse the operand into a tuning value, quantize it, clamp it, or calculate resulting cell values.

## Semantic transaction path

```text
anchor + focus + operation + raw operand text
                    |
                    v
JNI string boundary
                    |
                    v
Rust rectangle_numeric_command
                    |
                    +-- trim + parse f64
                    +-- reject malformed/non-finite operand
                    +-- expand inclusive rectangle in row-major order
                    +-- construct TableEditOperation::{Set, Add, Scale}
                    |
                    v
TuneDraft::preview
                    |
                    +-- validate every selected cell
                    +-- enforce protection
                    +-- compute operation result per cell
                    +-- quantize to table quantum
                    +-- enforce min/max representability
                    |
                    v
full Rust-generated preview TableProjection
                    |
                    +-- 2D preview
                    +-- 3D preview
                    |
                    v
explicit Apply(expected revision)
                    |
                    v
same operation reconstructed in Rust
                    |
                    v
TuneDraft::apply
                    |
                    v
one revision advance + one undo/redo history entry
```

## Operation semantics

`Set x` proposes the same semantic target value for every selected cell. The target is normalized to the table quantum by Rust.

`Add x` adds the operand independently to each selected cell's committed value, then Rust normalizes and validates every result.

`Scale x` multiplies each selected cell independently by the factor, then Rust normalizes and validates every result. The UI labels this field `Scale factor` so the operation is not mistaken for a percentage adjustment.

No operation silently clamps to table bounds. If any resulting value is outside the legal domain, the whole semantic preview is rejected.

## Parsing authority

Raw text crosses JNI as a Java/Kotlin `String`. Rust trims and parses it. This intentionally avoids making Compose or Kotlin's locale/parser behavior authoritative for tuning semantics.

Current accepted lexical form follows Rust `f64` parsing and therefore uses `.` as the decimal separator. This is surfaced explicitly in the temporary offline fixture UI. Locale-aware numeric-entry policy can be designed later without changing the semantic command boundary.

Malformed input, empty/whitespace-only input, and non-finite values are rejected before a `TableEditCommand` is created.

## Preview and stale revision behavior

Preview remains non-mutating and carries the committed `draft_revision` in the returned `TableProjection`.

The pending UI preview stores:

- exact rectangle;
- exact operation identity;
- exact raw operand text;
- expected base revision;
- Rust-generated preview projection.

Apply resubmits that same semantic intent and expected revision. If committed draft state changed after preview, Rust rejects Apply as stale instead of silently applying against newer data.

## Atomicity

The selected rectangle remains one transaction. The entire preview/apply is rejected when any selected cell is:

- protected;
- out of bounds;
- driven outside table bounds by the operation;
- otherwise not representable by the table descriptor.

There is no partial subset application.

## 2D / 3D ownership

Both views continue to consume the same committed projection, selection, and Rust-generated preview projection. Numeric operations therefore require no operation-specific 3D math.

3D remains inspection-only and does not originate edits in this slice.

## Explicit exclusions

This slice does not add:

- ECU reads;
- live RAM writes;
- Burn/nonvolatile persistence;
- controller reconciliation;
- project persistence;
- percentage-specific Add/Scale UI;
- interpolation;
- smoothing;
- Brush/Pull;
- Re-Bin;
- keyboard command routing;
- direct 3D editing.

The visible authority state remains `OFFLINE FIXTURE · NOT ECU APPLIED`.

## Qualification expectations

The slice must retain the full Foundation quality gate and specifically prove:

1. numeric operand text is parsed in Rust;
2. malformed/non-finite operands are rejected;
3. Set/Add/Scale reuse the existing `TableEditOperation` semantic engine;
4. results are quantized by Rust using the table descriptor;
5. an out-of-range result rejects the complete rectangle atomically;
6. protected-cell rejection remains atomic;
7. preview remains non-mutating;
8. Apply is revision-guarded;
9. one accepted rectangle operation creates one history entry;
10. 2D and 3D render the same Rust-generated preview state;
11. no ECU mutation API is introduced.

## Next slice

After qualification, the next useful operation family is deterministic interpolation over the same selected rectangle: horizontal, vertical, then 2D interpolation as distinct semantic commands. Those should preserve preview-first behavior and one-command/one-history-entry atomicity.
