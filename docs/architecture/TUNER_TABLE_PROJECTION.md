# EpicHub Tuner Table Projection

## Purpose

This slice proves that one Rust semantic table state can cross the Kotlin/Rust boundary once and feed both 2D and 3D native Compose representations without creating a second tune model.

It remains explicitly offline and read-only.

## Data path

```text
Rust offline fixture / TuneDraft
        ↓
representation-neutral TableProjection
        ↓
versioned variable-size packed ABI
        ↓
Kotlin TunerTableProjection
        ├─ read-only 2D grid
        └─ read-only 3D mesh
```

The projection includes profile generation, draft revision, source flags, table identity/name, dimensions, semantic numeric-domain metadata, axes, values and editability.

## Boundary rules

- no per-cell JNI calls;
- no JSON/document serialization;
- payload is little-endian, versioned and self-describing through explicit section offsets;
- payload size is queried before allocating the direct buffer, so table dimensions are not fixed to 16×16 or 32×32;
- Kotlin validates ABI version, dimensions and every section range before decoding;
- 2D and 3D consume the same decoded projection instance;
- high-rate runtime telemetry is not part of this projection;
- live lookup markers/trails remain a later, separate ChannelStore overlay.

## Fixture boundary

The visible Tuner currently uses an explicit `OfflineTunerFixture` adapter so the first UI slice cannot be mistaken for verified ECU/profile authority.

The UI states:

`OFFLINE FIXTURE · NOT ECU APPLIED`

The adapter is read-only and exposes no tune mutation or hardware operation. An engine-bound read-only projection path is also covered below the UI and is the direction for later real profile/project integration.

## 2D / 3D status

The current 2D surface shows X/Y axes, numeric cell values and protected-cell state.

The current 3D surface is deliberately inspection-only and draws a mesh from the exact same table values/revision. This obeys current D-016 while allowing the later shared-command 3D editing decision to be reviewed separately.

This is not yet the full Table Interaction Lab visual/interaction feature set. Hover, rich selected-cell inspection, camera interaction, live lookup, trails and semantic editing remain later slices.

## Tests

Rust verifies:

- explicit offline fixture/profile identity;
- versioned variable-size ABI layout;
- offsets/alignment and representative values/editability;
- engine-generation read-only projection availability;
- invalid/closed engine handles cannot publish the projection.

Kotlin/JVM verifies:

- decoding of the self-describing variable table payload;
- offline/fixture flags;
- axes, values and editability;
- rejection of unknown ABI versions;
- rejection of out-of-bounds section offsets.

## Deliberately absent

- Compose edit commands;
- ECU read/write/Burn;
- profile parser;
- project persistence;
- selection/history UI;
- interpolation/smoothing/re-bin;
- live lookup overlay;
- analyzer proposals.

The next slice should attach shared selection/preview semantics to the existing 2D/3D projection while keeping ECU mutation absent, after this read-only projection path is qualified.
