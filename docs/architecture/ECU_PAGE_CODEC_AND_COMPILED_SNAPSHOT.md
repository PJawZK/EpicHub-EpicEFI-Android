# ECU page codec and compiled semantic snapshot

## Purpose

This stage turns resolved TunerStudio definition data into bounded calibration byte access and semantic table snapshots without introducing board-specific offset routing.

The intended authority chain is:

```text
resolved definition text
  -> compiled profile identity / page geometry / protocol
  -> compiled paged calibration primitives
  -> compiled editor descriptors
  -> stable semantic table bindings
  -> raw page bytes
  -> TuneSnapshot
  -> TuneSession / TuneDraft
```

`TuneDraft Apply` is still not ECU RAM write and is still not Burn.

## Page ownership

TunerStudio definition pages are explicitly declared inside `[Constants]` with `page = N` and are 1-based.

EpicHub preserves that source page number in `PagedCalibrationPrimitive` and converts to zero-based collection indexing only at the raw byte-buffer boundary.

Offsets remain page-relative.

A calibration primitive before any active page assignment fails closed.

## Raw storage codec

The codec currently supports the normalized storage types already compiled by the calibration stage:

- `U08`
- `S08`
- `U16`
- `S16`
- `U32`
- `S32`
- `F32`

Endianness is not inferred from the ECU name. It comes from the compiled definition.

Engineering decode is:

```text
engineering = raw * scale + translate
```

Encoding is the inverse transformation.

The byte access layer performs checked element-count multiplication, checked offset addition, page presence checks, and exact slice bounds checks. It never saturates an out-of-range write.

For integer storage, encode also requires the engineering value to map exactly to an integer raw value within tolerance. This prevents a hidden second quantization policy below the semantic tuning layer.

Declared minimum/maximum bounds remain authoritative and are checked before encoding.

## Semantic table assembly

A table is assembled from the compiled `[TableEditor]` descriptor:

- `xBins` -> compiled primitive
- `yBins` -> compiled primitive
- `zBins` -> compiled primitive
- editor title -> semantic table display name
- editor page, when present -> cross-check against all three primitive page assignments

Rows are the Y-axis length and columns are the X-axis length. The Z primitive must match the compiled editor shape contract.

Editable table storage must currently be a discrete integer type. `F32` Z storage fails closed because EpicHub does not yet have evidence for a universal definition-derived edit quantum for arbitrary floating-point table storage. F32 remains supported by the raw codec and may be used by scalar/axis data.

For integer Z storage, semantic quantum is `abs(scale)` and quantum origin is `translate`. Descriptor minimum/maximum are intersected with the physical raw storage range and snapped inward to representable values on that lattice.

## Semantic IDs

TunerStudio editor identifiers are not used directly as EpicHub numeric semantic IDs.

The generic assembler accepts `SemanticTableBinding` entries that bind a stable EpicHub semantic ID to a compiled editor table ID.

This is deliberately a profile-normalization boundary, not a board switch. The next profile compiler stage can own these bindings as normalized profile metadata.

For Mega144H7, the current compatibility bindings preserve the already-qualified IDs:

- `1001` -> `veTableTbl`
- `1002` -> `ignitionTableTbl`
- `1003` -> resolved Lambda/AFR target editor
- `1004` -> `mapEstimateTableTbl`

The raw layout for those tables is no longer specified by those IDs.

## Mega144H7 parity requirement

Before retiring the hand-coded Mega decoder, the compiled path must produce an equal `TuneSnapshot` from the same raw pages.

The parity test covers:

- profile generation;
- source origin;
- semantic table IDs;
- names;
- rows/columns;
- minimum/maximum;
- quantum and quantum origin;
- display precision;
- X/Y axes;
- table values;
- editable masks.

This proves definition-driven table interpretation before the compatibility decoder is removed.

## Failure policy

The stage fails closed for at least:

- unresolved definition directives;
- primitive before page assignment;
- invalid/zero page number;
- unavailable page;
- byte range outside page;
- element-count mismatch;
- invalid scale/translate;
- non-finite engineering value;
- value outside declared bounds;
- engineering value not representable by integer raw storage;
- unknown editor table;
- unknown primitive;
- editor/primitive page mismatch;
- invalid table shape;
- missing table bounds/precision;
- non-discrete editable Z storage;
- invalid final `TuneSnapshot`.

No error path may silently select another page, clamp a value, fabricate a quantum, or reinterpret an ECU based on board name/USB identity.

## Still out of scope

This stage does not yet:

- probe transports;
- identify a controller;
- choose a profile from USB VID/PID;
- write ECU RAM;
- Burn;
- support bitfields/strings as semantic table data;
- provide arbitrary macro-expression evaluation;
- define floating-point table edit quantum;
- remove the Mega compatibility resolver before parity and integration are fully qualified.
