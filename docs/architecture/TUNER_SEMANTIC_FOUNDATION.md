# EpicHub Tuner Semantic Foundation

## Purpose

This document defines the first production implementation slice of the EpicHub Tuner.

The slice is deliberately **offline and non-mutating**. It establishes semantic tune-edit authority and history without introducing ECU RAM writes, Burn, reconnect behavior, live readback, profile parsing, or Compose table rendering.

It implements the foundation required by the broader Tuner architecture while keeping the safety boundary narrow enough to qualify independently.

## Implemented scope

The first Rust tuning-domain implementation provides:

- immutable `TuneSnapshot` baseline data;
- explicit snapshot origin (`OfflineFixture` / `ImportedFile`);
- exact profile-generation binding;
- `TableDescriptor` with shape, range and precision metadata;
- `TableSnapshot` axis/value/editability state;
- Rust-owned `TuneDraft` copied from but independent of the immutable baseline;
- representation-neutral `TableProjection` for future 2D and 3D consumers;
- semantic `TableEditCommand` operations:
  - Set;
  - Add;
  - Scale;
  - Increment;
  - Decrement;
- atomic multi-cell validation and mutation;
- precision-aware quantization;
- protected/non-editable cells;
- non-mutating preview;
- stale-preview rejection through draft revision binding;
- semantic undo/redo history;
- monotonic draft revision updates.

## Explicitly out of scope

This slice does **not** implement:

- generated INI/profile parsing;
- live ECU connection/session authority;
- ECU tune read;
- ECU RAM write;
- Burn/nonvolatile persistence;
- operation uncertainty journal;
- project persistence;
- tune compare/diff;
- interpolation, smoothing, brush/pull or re-bin;
- clipboard/import/export;
- table/curve JNI projection;
- Compose `TableGrid`;
- Compose `Table3dView`;
- live lookup overlay;
- analyzer/tuning-tool proposals.

None of those behaviors should be inferred from the existence of the semantic draft.

## Authority model

```text
Offline/imported immutable TuneSnapshot
              │
              ▼
          Rust TuneDraft
              │
      semantic edit commands
              │
              ▼
 representation-neutral TableProjection
              │
       ┌──────┴──────┐
       ▼             ▼
 future 2D       future 3D
```

The key invariant is:

```text
TuneDraft mutation
    != ECU RAM write
    != Burn
    != project persistence
```

No method in this slice can communicate with hardware.

## Atomic edit rule

A multi-cell command is validated completely before any cell is changed.

If any target is:

- outside the table;
- protected;
- non-finite;
- outside the descriptor range;
- duplicated in the command;

then the command performs **zero partial mutation** and creates no history entry.

This rule is intentionally established before adding large paste, interpolation, smoothing, re-bin or analyzer-proposal operations.

## Preview rule

Preview is derived from one exact draft revision and does not alter committed values.

A preview can only commit when:

- its base revision still matches the current draft revision;
- every target cell still contains the expected pre-preview value;
- all affected cells remain editable and valid.

Otherwise it fails with `StalePreview` or another typed validation error.

This becomes the foundation for later numeric entry, bulk-transform preview and analyzer proposal review.

## History rule

One semantic multi-cell command produces one history transaction.

Undo/redo operates on semantic before/after cell deltas and advances the draft revision. It does not imply ECU or persistence state changes.

Future table operations such as interpolation, smoothing and re-bin should follow the same transaction boundary rather than creating one history item per affected cell.

## 2D / 3D rule

The Rust domain publishes one representation-neutral table projection containing:

- descriptor;
- X axis;
- Y axis;
- values;
- editability;
- draft revision.

Future 2D and 3D presentations consume the same projection and submit the same semantic edit-command family.

This slice does not yet change D-016's current production restriction on direct 3D editing. That durable decision is reviewed separately when the Compose 3D editing path is introduced.

## Current regression coverage

Rust tests currently verify:

1. editing a draft cannot mutate the immutable baseline;
2. multi-cell operations fail atomically when one cell is protected;
3. one multi-cell edit produces one undo/redo transaction;
4. range failure performs zero partial mutation;
5. increment/decrement follows descriptor precision;
6. preview does not mutate committed values;
7. stale preview cannot commit after another edit changes draft revision;
8. table projection remains representation-neutral and revisioned for future 2D/3D consumers.

Foundation CI is expected to run `cargo fmt`, `cargo clippy -D warnings`, Rust tests, Android JVM tests and Android builds before this slice is accepted.

## Next implementation slice after acceptance

After this foundation passes CI/review, the next narrow slice should add the **typed Kotlin/Rust Tuner projection boundary** and a fixture-driven read-only Compose table surface.

Recommended order:

1. fixed/versioned or otherwise explicitly typed bounded table-projection boundary;
2. offline fixture selection owned by the tuning domain;
3. `TableUiModel` in Kotlin as a projection only;
4. read-only 2D `TableGrid`;
5. read-only 3D `Table3dView` from the same projection;
6. prove identical values/revision in both representations;
7. only then attach selection/preview/edit commands to Compose.

ECU mutation remains out of scope until the offline semantic path and UI projection are independently qualified.
