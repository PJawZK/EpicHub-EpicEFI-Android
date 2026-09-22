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

When sources disagree, use the following default authority order unless a responsibility-specific protocol document establishes a stronger source:

1. Firmware/protocol reality.
2. Physically verified EpicDash-JZ behaviour.
3. Exact generated INI/profile behaviour.
4. Original EpicDash protocol/function intent.
5. Other experimental, UI, tool and plugin repositories.

UI/UX repositories may be authoritative for explicitly accepted interaction or visual behaviour, but never for ECU protocol, tune-memory or firmware truth.

## Architecture changes

Architecture documents are part of the implementation contract. A change that moves responsibility, state authority, dependency direction, transport ownership, safety policy or the Kotlin/Rust boundary must update the corresponding architecture document in the same workstream.

New code should have an obvious permanent home before it is implemented. If it does not, resolve the ownership/topology question before adding the production code.

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
- what tests qualify each major subsystem.

Only after this gate should production Android/Rust scaffolding begin.
