# Mega144H7 resolved tune-page source

## Purpose

This slice establishes the first definition-authentic EpicHub source path from raw EpicEFI tune-page bytes into the existing semantic `TuneSnapshot` / `TuneSession` model.

It does **not** add ECU mutation, Burn, transport ownership, or TunerStudio runtime dependencies.

The architectural boundary is:

```text
resolved EpicEFI profile
        +
raw tune pages
        ↓
profile decoder
        ↓
semantic TuneSnapshot
        ↓
TuneSession / TuneDraft
```

The decoder is source-neutral. A raw page capture may come from an imported artifact or, later, a verified live controller source. The decoder does not infer provenance from the bytes.

## Qualified profile target

Initial profile identity:

- profile name: `353`
- signature: `rusEFI master.2026.08.26.MEGA144H7.2273317132`
- profile fingerprint: `1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa`
- little-endian tune storage
- page 1: 55,764 bytes
- page 2: 5,000 bytes
- total: 60,764 bytes

`profile_generation` is derived from the stable firmware/profile build identity (`2273317132`) rather than a particular tune capture. Tune capture generation and draft revision remain separate concepts.

## First semantic table set

Stable EpicHub semantic IDs are deliberately independent of raw firmware offsets:

| Semantic ID | Table | Definition source |
| --- | --- | --- |
| 1001 | VE Table | `veTable`, `veRpmBins`, `veLoadBins` |
| 1002 | Ignition Table | `ignitionTable`, `ignitionRpmBins`, `ignitionLoadBins` |
| 1003 | Target Lambda / AFR | `lambdaTable`, `lambdaRpmBins`, `lambdaLoadBins` |
| 1004 | MAP Estimate | `mapEstimateTable`, `mapEstimateRpmBins`, `mapEstimateTpsBins` |

Offsets, primitive types and scaling are profile implementation details inside the resolver; callers use the semantic IDs.

## Conditional profile state

The decoder accepts explicit resolved modes:

- hundredth VE presentation;
- hundredth ignition presentation;
- lambda targets versus AFR targets.

It does not evaluate arbitrary INI condition syntax at runtime and does not guess conditional state.

For VE and ignition the encoded storage quantum remains `0.01` regardless of whether the profile presents one or two decimals. Display precision is therefore not used as storage resolution.

For target mixture, lambda and AFR are materially different encoded domains and are resolved explicitly.

## Representable legal bounds

EpicHub keeps a strict invariant that every value admitted by `TableDescriptor` must be representable on the table's encoded quantum grid.

The lambda INI domain is declared as `0.6..1.5`, while the U8 scale is `0.006802721088435374`. Those endpoints are not themselves exact encoded values. The resolver therefore derives the first and last representable values inside the declared domain rather than weakening `TuneDraft` validation.

This preserves the distinction between:

- declared/profile UI bounds; and
- legal encoded values.

## Editing semantics

Resolved controller/imported tables enter the same existing semantic edit engine:

- one immutable `TuneSnapshot` baseline;
- one independent `TuneDraft`;
- semantic table IDs;
- quantized Set/Add/Scale/Increment/Decrement;
- interpolation;
- smoothing;
- Brush/Pull;
- preview-before-Apply;
- stale-revision rejection;
- one semantic Undo/Redo history.

For this initial profile slice, cells in these four tables are marked editable because the source definitions identify ordinary tune arrays and no cell-level protection metadata exists for them. This is not a statement about controller-write authority.

`TuneDraft Apply != ECU RAM write != Burn` remains unchanged.

## Page validation

Both page sizes are validated before any semantic snapshot is created, even though the initial four tables reside on page 1. This prevents a partial or mismatched profile capture from being accepted as a valid resolved profile generation.

All offset arithmetic is checked and short buffers are rejected instead of partially decoding.

## Explicit non-goals

This slice does not yet:

- replace the app startup fallback with live ECU page bytes;
- introduce a source-provider/session replacement transaction;
- expose a semantic table catalog through JNI/Kotlin;
- parse MSQ XML;
- evaluate all TunerStudio/INI conditional expressions;
- cover all roughly 100 profile tables or 176 curves;
- write controller RAM;
- Burn controller nonvolatile memory.

The next source slice should make `EngineState` accept a verified resolved `TuneSnapshot` transactionally and expose its semantic table catalog. Once a real controller/import source supplies the raw pages and resolved profile modes, the synthetic startup fallback can be removed from the normal Tuner path without changing `TuneDraft` editing semantics.
