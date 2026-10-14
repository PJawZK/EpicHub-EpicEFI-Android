# ECU calibration compiler stage

This stage extends the qualified ECU-definition foundation into normalized calibration storage and editor semantics without adding live ECU write authority.

## Source authority

The compiler consumes already-resolved ECU-definition text. It does not guess conditional branches.

Any unresolved preprocessor directive such as `#if`, `#else`, `#endif`, `#include`, or another `#...` source directive fails closed. Conditional/include resolution is a separate compiler stage that must run before calibration or editor compilation is allowed to become tuning authority.

This is important for generated rusEFI/TunerStudio definitions where the same constant can be emitted differently under feature flags such as hundredth-VE or lambda-target modes.

## Normalized calibration primitives

Stage 1 normalizes `scalar` and `array` constants into `CalibrationPrimitive`:

- stable source name;
- storage type (`U08`, `S08`, `U16`, `S16`, `U32`, `S32`, `F32`);
- byte offset;
- scalar or array dimensions;
- units/expression text;
- scale;
- translate;
- optional minimum/maximum;
- optional display precision.

The parser splits comma-separated fields only at the top level. Commas inside quoted strings, braces or function expressions therefore remain part of the source expression.

Bitfields are deliberately not compiled yet. They remain outside this slice until enum/bit semantics and source-condition handling are explicit.

## Editor descriptors

`[TableEditor]` and `[CurveEditor]` are normalized separately from physical storage.

A table descriptor retains:

- table ID;
- 3D map ID;
- display title;
- optional source page field;
- X primitive reference and optional runtime channel;
- Y primitive reference and optional runtime channel;
- Z primitive reference.

A curve descriptor retains:

- curve ID;
- display title;
- X primitive reference and optional runtime channel;
- Y primitive reference.

Descriptor compilation validates references against the normalized primitive catalog. Table X/Y bindings must be one-dimensional arrays and the Z array dimensions must match the axis lengths. Curve X/Y arrays must be one-dimensional and have equal lengths.

This gives EpicHub a definition-driven path from source definitions to editor semantics without putting board-specific byte offsets into Android UI code.

## Current authority boundary

This stage still does **not** create a complete `ResolvedEcuProfile` and does not grant:

- live source acquisition;
- raw-page decoding into a `TuneSnapshot`;
- ECU RAM writes;
- Burn;
- arbitrary full-INI interpretation.

Before a compiled definition can replace the current Mega144H7 decoder, remaining stages must cover at least:

1. preprocessor/include/condition resolution;
2. page ownership for constants and command geometry;
3. bitfields/enums where required by tuning semantics;
4. deterministic semantic-ID assignment;
5. raw-byte decode/encode from normalized primitive metadata;
6. complete table/curve catalog selection and validation;
7. exact profile/signature/fingerprint authority checks.

The current Mega144H7 adapter remains the reference decoder until the compiled path can reproduce its proven `TuneSnapshot` semantics exactly.
