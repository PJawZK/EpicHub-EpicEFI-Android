# EpicHub Development Workflow

## Purpose

EpicHub is developed as a clean-slate product. Earlier EpicDash, EpicDash-JZ, EpicEFI firmware and related projects are evidence sources, not source-tree templates.

The repository preserves a clear distinction between:

- product requirements and proven behaviour;
- protocol/file-format authority;
- implementation choices;
- experimental alternatives;
- accepted production structure.

## Repository workflow

`main` is the integration branch.

Substantial work uses descriptive feature/task branches and PRs. The first Phase-2 foundation is already merged; current work uses `phase2/ui-diagnostics-foundation` / draft PR #3.

Generated binaries, signing material, secrets, personal paths, device identifiers and local build state are not committed.

## Historical Phase-1 classification

Phase 1 is closed. Its responsibility classifications remain useful vocabulary:

- `PRESERVE`
- `REDESIGN`
- `MERGE`
- `SPLIT`
- `REFERENCE`
- `REMOVE`

Evidence confidence remains:

- `PROVEN`
- `SUPPORTED`
- `PROPOSED`

Those labels describe responsibility/evidence disposition; they do not replace D-022's finer implementation-entry classification of individual historical behavior.

## Source authority

When historical/external sources disagree, default authority is:

1. firmware/protocol reality;
2. physically verified EpicDash-JZ behaviour;
3. exact generated INI/profile behaviour;
4. original EpicDash protocol/function intent;
5. other experimental/UI/tool/plugin repositories.

UI/UX references may be authoritative for explicitly accepted interaction/visual behavior, never ECU/tune-memory/firmware truth.

Within EpicHub, follow `docs/architecture/README.md` authority ordering. Durable decisions and cross-domain contracts override older/local evidence wording.

## Clean reimplementation rule — D-022

Before implementing inherited behavior, follow `SOURCE_REIMPLEMENTATION_METHOD.md`:

```text
semantic behavior slice
→ trace old implementation/callers/state/lifecycle/errors/tests/bug history
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ define clean owner/API/state/thread/lifetime/failure contract
→ implement from scratch
→ qualify against protocol truth/required behavior/focused fixtures
→ regression-test historical failures
→ benchmark hot paths
→ accept narrow slice
```

Prohibited:

```text
copy old subsystem
→ make it compile
→ rename/refactor later
```

Substantial replacement PRs should record historical evidence reviewed, required invariants, old bugs/technical debt excluded, new owner/contract, tests/goldens, performance/physical evidence and deliberate differences.

## Runtime language / serialization rule — D-023

Production application logic remains Kotlin/Jetpack Compose + Rust.

Do not introduce JavaScript/WebView/DOM application runtime or serialized-document messaging as a convenience architecture.

Before adding JSON, CBOR, XML, protobuf, MessagePack or another generic serializer, identify the explicit persistence/export/external-interoperability boundary that requires it.

Runtime/JNI/domain communication remains typed/packed. Serializer dependencies stay isolated behind their boundary codecs.

Current canonical diagnostic export is versioned `capture.cbor` + `report.txt`; CBOR is not the live diagnostic transport.

## Architecture changes

Architecture documents are implementation contracts. A change in responsibility, authority, dependency direction, transport ownership, safety policy, runtime-language policy, serialization boundary or Kotlin/Rust boundary updates the corresponding documents in the same workstream.

New code must have an obvious permanent owner/home before implementation.

### Cross-document consistency rule

Before accepting any durable architecture change:

1. identify every document restating the changed concept;
2. update them together, or explicitly mark old text as historical evidence;
3. search the active branch/PR for superseded names, ownership statements, old phase status, provisional values, old implementation order and serialization/runtime leakage;
4. resolve contradictions rather than relying on chronology;
5. verify `GAP_ANALYSIS.md`, current handoff and active implementation contracts remain truthful.

Required stale-term checks now include, where relevant:

- old Phase-1 pending/acceptance wording;
- old foundation-as-future wording;
- old top-level navigation counts/destinations;
- `JSON`, `NDJSON`, `JSONObject`, `WebView`, `JavaScript`, `React`, `TypeScript` when used as current target instructions rather than historical evidence;
- JSON/CBOR document payloads over JNI;
- serializers imported into runtime/domain state packages;
- file-format-specific wording inside generic logging/analysis contracts;
- old state-owner/gateway names.

Historical evidence may legitimately contain those terms, but must not present them as current target architecture.

## Experiments

Competing implementations may temporarily use explicit experiment names when comparing real alternatives. Accepted code receives permanent descriptive names; rejected variants are removed or retained only as deliberate test/reference material.

## Testing/qualification discipline

Every accepted slice gets the cheapest strong proof available before adding more complexity:

- pure unit/golden tests;
- ABI/schema tests;
- fault-injection/lifecycle tests;
- CI build/lint/static checks;
- device/bench qualification where behavior depends on Android/hardware;
- performance measurement for hot paths.

Do not stack multiple unqualified subsystems and attempt to debug the combined result later.

The current UI/diagnostics slice must be qualified with the existing synthetic RuntimeSnapshot foundation before live ECU transport is attached.

## Chat handoff workflow

There is exactly one current handoff file:

`docs/HANDOFF.md`

Git history is the archive. Do not create numbered/dated handoff files.

When the user requests **"Create the EpicHub handoff"**:

1. inspect actual branch/head/PR/CI/qualification state;
2. incorporate only durable decisions, completed work, unresolved issues and next action;
3. replace/update `docs/HANDOFF.md` rather than append stale history;
4. point to repository authority docs instead of duplicating them;
5. commit the handoff update;
6. return a minimal continuation instruction.

Preferred continuation instruction:

`Read PJawZK/EpicHub-EpicEFI-Android/docs/HANDOFF.md, verify the repository state, and continue from there.`

## Current development gate

Phase 1 is closed and the first Phase-2 foundation is qualified.

Current gate:

```text
accept PR #3 UI + diagnostic contracts
→ implement responsive shell + typed diagnostics/capture on synthetic foundation
→ CI + SM-A137F + SM-T500 qualification
→ only then start D-022 review/implementation of read-only live ECU acquisition
```

ECU mutation/Burn/logger formats/BLE migration/analyzer-autotune remain outside the current slice.