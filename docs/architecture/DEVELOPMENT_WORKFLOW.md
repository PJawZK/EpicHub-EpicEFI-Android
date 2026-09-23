# EpicHub Development Workflow

## Purpose

EpicHub is developed as a clean-slate product. Earlier EpicDash, EpicDash-JZ, EpicEFI firmware and related projects are evidence sources, not source-tree templates.

The repository must preserve a clear distinction between:

- product requirements and proven behaviour;
- protocol/file-format authority;
- implementation choices;
- experimental alternatives;
- accepted production structure.

## Repository workflow

`main` is the integration branch. Substantial work is performed on descriptive feature/task branches and reviewed before integration.

Phase 1 architecture work uses:

`phase1/architecture-inventory`

Generated binaries, signing material, secrets, personal paths, device identifiers and local build state must not be committed.

## Phase 1 rule

Phase 1 is architecture and repository archaeology only. Do not add production Android or Rust implementation until the Phase 1 architecture contracts establish the intended ownership, boundaries, naming and topology.

Reference repositories are inspected by domain. Findings are normalized into responsibilities rather than copied as historical files/classes.

Every discovered responsibility is classified as one of:

- `PRESERVE` — required behaviour should remain;
- `REDESIGN` — requirement remains but implementation should be replaced;
- `MERGE` — several historical mechanisms should become one responsibility;
- `SPLIT` — one historical implementation contains several responsibilities;
- `REFERENCE` — useful evidence but not a production requirement;
- `REMOVE` — historical behaviour/structure should not be carried forward.

Evidence confidence is recorded as:

- `PROVEN` — physically or otherwise explicitly qualified behaviour;
- `SUPPORTED` — strongly evidenced by source/tests/protocol documentation but not necessarily requalified physically;
- `PROPOSED` — new EpicHub architecture/design.

## Source authority

When historical/external sources disagree, use the following default authority order unless a responsibility-specific protocol document establishes a stronger source:

1. Firmware/protocol reality.
2. Physically verified EpicDash-JZ behaviour.
3. Exact generated INI/profile behaviour.
4. Original EpicDash protocol/function intent.
5. Other experimental, UI, tool and plugin repositories.

UI/UX repositories may be authoritative for explicitly accepted interaction or visual behaviour, but never for ECU protocol, tune-memory or firmware truth.

Within EpicHub itself, follow the authority interpretation in `docs/architecture/README.md`: durable decisions and cross-domain contracts override older/local evidence wording; topology maps those contracts but does not supersede them.

## Clean reimplementation rule

Phase 1 responsibility archaeology does **not** certify historical source files as production-quality code and does not authorize bulk source migration.

Before implementing any inherited subsystem or feature, follow `SOURCE_REIMPLEMENTATION_METHOD.md`:

```text
semantic behavior slice
→ trace historical implementation/callers/tests/failure history
→ classify required/safety/compatibility/useful/bug/obsolete/unknown behavior
→ define clean owner + API + state/thread/lifetime/failure contract
→ implement from scratch in EpicHub
→ qualify against protocol truth / proven behavior / focused goldens
→ benchmark hot paths
→ accept the narrow slice
→ move to the next slice
```

The prohibited migration pattern is:

```text
copy old subsystem
→ make it compile
→ rename/refactor it later
```

Historical bugs and awkward fixes are mined for the invariant they reveal, not for the old control flow that implemented them.

Old tests are reviewed as candidate behavioral specifications. Implementation-detail assertions and accidental/junk behavior are not transferred merely to make the new implementation match the old code.

A substantial implementation PR that replaces historical behavior should identify:

- historical evidence reviewed;
- required behavior and safety invariants;
- old bugs/technical debt deliberately excluded;
- new permanent owner/contract;
- test/golden evidence;
- performance/physical evidence where applicable;
- deliberate behavior differences from the old implementation.

This methodology applies to the entire project, including Android platform integration, ECU/profile/tuning, BLE/accessory, logging, MDF/MLG interoperability, analyzer/autotune, diagnostics and Compose UI.

## Architecture changes

Architecture documents are part of the implementation contract. A change that moves responsibility, state authority, dependency direction, transport ownership, safety policy or the Kotlin/Rust boundary must update the corresponding architecture documents in the same workstream.

New code should have an obvious permanent home before it is implemented. If it does not, resolve the ownership/topology question before adding production code.

### Cross-document consistency rule

A durable architecture change is not complete merely because `DECISIONS.md` or one domain document was updated.

Before accepting that workstream:

1. identify every document that restates the changed concept, including decision, ownership, dependency, data-flow, threading/FFI, action/safety, topology, test/gap, progress/handoff material where relevant;
2. update those documents in the same workstream, or clearly mark a document as historical evidence that does not override the current contract;
3. search the active branch/PR for superseded names, ownership statements, provisional values and old implementation-order phrases;
4. resolve contradictory wording instead of relying on readers to infer which statement is newer;
5. verify `REVIEW_CHECKLIST.md`, `GAP_ANALYSIS.md`, progress and handoff status remain truthful.

Examples of useful stale-term checks include old gateway/class names, old decision ranges, provisional values that have since been locked, file-format-specific wording inside generic contracts and previous state-owner names.

Evidence documents may intentionally describe historical behavior that differs from the target architecture, but they must label that behavior as evidence/history rather than a current production instruction.

## Experiments

Competing implementations may temporarily use explicit experimental names such as `UsbTransportT2` or `UsbTransportExperimental` when a permanent implementation already exists.

Experimental names must not survive acceptance. The selected implementation receives the permanent descriptive name before integration, and rejected variants are removed or retained only as deliberate test/reference material.

## Chat handoff workflow

There is exactly one current handoff file:

`docs/HANDOFF.md`

Git history is the handoff archive. Do not create numbered or dated handoff files.

When the user requests **"Create the EpicHub handoff"**:

1. inspect the actual repository state, including active branch, head SHA, PR state and relevant CI/qualification state;
2. incorporate only durable decisions, completed work, unresolved issues and the next concrete action from the current chat;
3. replace/update `docs/HANDOFF.md` so stale branch/PR/task information does not accumulate;
4. point to authoritative repository documents instead of duplicating them;
5. commit the handoff update;
6. return a minimal continuation instruction.

Preferred continuation instruction:

`Read PJawZK/EpicHub-EpicEFI-Android/docs/HANDOFF.md, verify the repository state, and continue from there.`

A new chat must read the handoff first, verify the branch/SHA/PR it names, then read the specifically referenced documents/source files before modifying the project.

## Phase 1 completion gate

Phase 1 is complete only when the repository can answer, with documented ownership:

- what every meaningful inherited responsibility is;
- whether it is preserved, redesigned, merged, split, reference-only or removed;
- what major new EpicHub responsibilities have no historical owner;
- where each retained/new responsibility will live;
- which language/runtime owns each subsystem;
- which source is authoritative for each important protocol/schema;
- who owns each mutable state;
- what crosses the Kotlin/Rust boundary;
- what the dependency direction is;
- what tests qualify each major subsystem;
- whether the current architecture documents agree after a cross-document consistency pass.

Only after this gate and explicit user acceptance should production Android/Rust scaffolding begin.