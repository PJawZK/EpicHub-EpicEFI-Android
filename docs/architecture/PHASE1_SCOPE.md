# Phase 1 — Architecture and Responsibility Inventory

## Goal

Establish enough repository-backed architectural truth that EpicHub production code can begin without inheriting accidental EpicDash/EpicDash-JZ structure or inventing duplicate authorities.

## In scope

- cross-repository source/provenance inventory;
- behavior/responsibility inventory by domain;
- protocol/schema authority identification;
- classification of inherited behavior as Preserve/Redesign/Merge/Split/Reference/Remove;
- target Kotlin package/file topology;
- target Rust crate/module/file topology;
- responsibility-to-destination mapping;
- gap analysis for new EpicHub capabilities;
- dependency direction;
- state ownership;
- data flow;
- threading/concurrency model;
- Kotlin/Rust FFI boundary;
- semantic action/accessory model;
- safety boundaries;
- test/qualification strategy;
- repository/chat handoff workflow.

## Out of scope

Until the completion gate is accepted, Phase 1 does not add:

- Android Studio production application scaffold;
- Compose production screens;
- Cargo/Rust production workspace;
- JNI/native engine implementation;
- MDF4 implementation;
- new ECU transport implementation;
- new ESP32 firmware/protocol implementation;
- production tune-write/Burn implementation.

Small analysis scripts/tools used only to inspect/reference source are allowed if needed and clearly remain tooling, not product code.

## Primary reference sources

Initial priority:

1. `PJawZK/EpicDash-JZ` — qualified/evolved behavior and safety evidence;
2. `AKCore/EpicEFIVirtualDash` — original EpicDash/ESP32 function and wire-protocol intent;
3. `PJawZK/epicefi_fw_jzfork` — firmware/protocol reality and generated profile behavior.

Then review relevant:

- `PJawZK/Epic-DashTune-UXUI`;
- `PJawZK/PJawZK-EpicEFI-Tune-Viewer`;
- diagnostics repository;
- AE/PID/TCharge/Injection Angle tuning plugins;
- external Android/JNI/MDF specifications when required.

## Required completion condition

Before Phase 2, every meaningful known responsibility should be answerable in terms of:

- purpose;
- authority/evidence source;
- required behavior/invariants;
- evidence level;
- Preserve/Redesign/Merge/Split/Reference/Remove classification;
- future owner/language;
- planned package/crate/file destination;
- mutable state owner, if any;
- thread/lifecycle expectations;
- safety implications;
- intended test/qualification layer.

Major future features with no historical implementation must appear explicitly in Gap Analysis and Codebase Topology.

## Phase 1 review gate

Phase 1 is ready to close only when:

1. no known major capability is still hidden inside an old broad class without a responsibility destination;
2. ECU/ESP32/file-format protocol authorities are explicit enough to prevent competing implementations;
3. the Kotlin/Rust boundary has no expected high-frequency per-value crossing;
4. runtime/tune/accessory/logging mutable-state ownership is unambiguous;
5. dependency direction prevents UI/transport/file-format layers from becoming domain authority;
6. the topology contains the expected permanent home for every Phase 2 starting component;
7. unresolved decisions are deliberate measured PoCs/choices, not accidental unknowns;
8. test strategy exists for each high-risk subsystem;
9. architecture documents agree with one another;
10. the user explicitly accepts the Phase 1 architecture before production scaffold begins.
