# Generic ECU profile architecture

## Purpose

EpicHub must accept multiple ECU firmware/profile generations without turning board names into application control flow.

The first real profile implementation, Mega144H7 profile `353`, proved definition-authentic decoding from raw tune pages into the existing semantic `TuneSnapshot` / `TuneSession` model. That implementation is now treated as the first adapter to a generic ECU-profile contract, not as the product-wide ECU model.

This document is the D-022 source-understanding/design gate for that generalization.

---

## Evidence classification

### Required behavior

Mature TunerStudio-style tooling separates these concerns:

```text
transport candidate
    -> runtime firmware identity
    -> exact ECU definition/profile match
    -> protocol/schema interpretation
    -> tune/runtime acquisition
    -> tuning authority
```

The transport medium is not the ECU identity. A USB VID/PID, serial port, TCP endpoint, UART or CAN channel only establishes a candidate path to a controller.

The live firmware signature is the strongest runtime identity available in the current EpicEFI/rusEFI protocol family. The generated ECU definition contains the matching expected signature plus protocol and data-layout information.

The definition/profile owns interpretation details such as:

- query/signature command;
- message envelope/framing;
- endianness;
- runtime/output geometry;
- calibration page identifiers and sizes;
- page read/write commands;
- scalars, arrays, tables and curves;
- conditional presentation/encoding modes;
- capability metadata.

EpicHub must therefore be able to recognize a controller independently of whether a matching tune profile is installed, while refusing tune interpretation/mutation authority until the live identity and resolved profile authority match exactly.

### Safety behavior

The following must remain fail-closed:

- unknown runtime signature;
- known controller family with no matching installed profile;
- imported profile whose exact expected signature differs from the live signature;
- malformed or incomplete profile definition;
- raw tune pages whose count/size/geometry do not satisfy the selected profile;
- any profile resolution failure.

None of those states may silently fall back to a nearby board, parsed board name, USB product text, VID/PID, profile generation prefix or other plausibility heuristic.

### Compatibility behavior worth preserving

TunerStudio's useful product behavior is definition-driven extensibility: adding another ECU definition does not require adding another board-specific tuning application.

EpicHub should preserve that property while using an EpicHub-owned normalized profile model rather than making INI text the permanent runtime architecture.

TunerStudio INI remains an important import/compiler source because EpicEFI/rusEFI already generates authoritative definitions from firmware configuration.

### Behavior deliberately not copied

EpicHub will not recreate:

- arbitrary board-name switch statements;
- USB identity as ECU authority;
- permissive signature-prefix matching;
- hidden automatic substitution of a "close enough" definition;
- ad-hoc INI string lookup throughout production tuning code;
- a second calibration-math implementation inside profile adapters;
- source switching as an Undo-able table edit.

---

## Identity model

Three separate concepts are required.

### 1. Transport candidate identity

Examples:

- USB VID/PID/interface candidate;
- serial device;
- TCP endpoint;
- UART bridge;
- CAN-backed TunerStudio channel.

This answers only:

> Could there be a compatible controller reachable here?

It never grants profile or tuning authority.

### 2. Runtime controller identity

A read-only interrogation produces controller identity, principally the complete live TunerStudio signature for the current EpicEFI/rusEFI family.

Descriptive fields such as parsed firmware family or board name may be exposed to Diagnostics/UI, but they are not compatibility authority.

### 3. Resolved ECU profile identity

A resolved profile owns:

- exact expected signature;
- stable profile identifier;
- profile generation/build identity;
- definition fingerprint where available;
- source/page geometry;
- protocol/data-layout semantics;
- table/curve/scalar descriptors;
- conditional resolved modes/capabilities.

Only an exact live-signature/profile-signature match may authorize interpretation of live controller bytes.

Offline/imported sources do not require a live ECU, but their provenance and selected profile identity must remain explicit.

---

## Runtime architecture

```text
Transport
   |
   v
RuntimeControllerIdentity
   |
   v
ProfileRegistry / ProfileProvider
   |
   v
ResolvedEcuProfile
   |
   +---- exact identity/protocol/schema authority
   |
   v
Source acquisition
   |
   v
TuneSnapshot
   |
   v
TuneSession
   |
   v
TuneDraft / semantic editor
```

The existing semantic tuning architecture remains unchanged below `TuneSnapshot`.

`TuneSession`, `TuneDraft`, table projection, 2D/3D editing, preview/Apply, history and the semantic table catalog must not care which ECU board produced the snapshot.

---

## Profile contract

The first native contract is intentionally small.

A resolved profile must expose:

- `ProfileIdentity`;
- exact signature match semantics;
- expected raw page geometry;
- a resolver that converts validated source pages plus explicit provenance into a `TuneSnapshot`.

The resolver may carry profile-specific already-resolved modes internally. Callers must not switch on board names to select calibration math.

Mega144H7 becomes the first concrete implementation of this contract.

A generic registry/matcher must be testable with multiple profile implementations without adding any board-name branch to the matching/install path.

---

## Definition compiler direction

The long-term input path should be:

```text
TunerStudio ECU definition / other supported definition source
        |
        v
EpicHub definition compiler
        |
        +-- parse and validate
        +-- resolve includes/conditions/settings
        +-- normalize primitive types/scaling/bounds
        +-- normalize protocol/runtime/page geometry
        +-- build semantic table/curve/scalar descriptors
        +-- calculate stable fingerprint
        |
        v
CompiledEcuProfile
```

Production tuning should consume `CompiledEcuProfile` / `ResolvedEcuProfile`, not scatter INI parsing through UI, transport or tuning code.

This also leaves the door open to future profile sources that are not TunerStudio INI while preserving the same internal contract.

---

## Source installation rule

Source acquisition remains distinct from profile resolution and TuneSession installation.

The generic transaction is:

```text
selected ResolvedEcuProfile
        +
raw source pages
        +
explicit source provenance
        |
        v
complete profile decode/validation
        |
        v
TuneSnapshot candidate
        |
        v
atomic TuneSession replacement
```

The existing atomicity invariant remains mandatory:

- resolve completely before obtaining session mutation authority;
- on any failure, preserve the old session, baseline, draft revision and Undo/Redo history exactly;
- successful source replacement starts a new session at revision `0` with fresh history.

Still explicitly:

```text
profile match != source install != TuneDraft Apply != ECU RAM write != Burn
```

---

## First implementation acceptance criteria

This architecture slice is accepted when:

1. a generic Rust profile identity/resolution contract exists;
2. exact-signature matching is implemented independently of board names;
3. the current Mega144H7 decoder implements that generic contract without changing its proven decoding semantics;
4. generic atomic source installation accepts a profile implementation rather than calling a Mega144H7 decoder directly;
5. a second test profile proves registry/matching/install plumbing has no Mega144H7/board-name branch;
6. unknown and near-match signatures fail closed;
7. all existing Mega144H7 resolver/source-install regression tests still pass;
8. no ECU write, Burn or live-transport authority is added in this slice.

A second real ECU definition parser/decoder is deliberately not required for this first contract slice. The next profile-provider/compiler slice should use at least two real definitions as fixtures, with Mega144H7 plus a materially different generated definition such as uaEFI, and prove both can be compiled without application board-name branching.

---

## Non-goals

This slice does not add:

- live ECU transport;
- automatic ECU detection UI;
- dynamic INI parsing on Android;
- full ECU-definition compiler;
- MSQ parsing;
- live tune-page acquisition;
- controller RAM writes;
- readback/reconciliation;
- Burn/persistence;
- broad support claims for ECU profiles that have not been compiled and qualified.
