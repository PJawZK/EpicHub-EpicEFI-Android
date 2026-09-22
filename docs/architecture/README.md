# EpicHub Architecture Documents

Phase 1 builds the repository-backed architecture before production Android/Rust implementation begins.

## Start here

- `PHASE1_SCOPE.md` — Phase 1 purpose, exclusions and completion gate.
- `DECISIONS.md` — durable architectural decisions.
- `DEVELOPMENT_WORKFLOW.md` — repository and chat/handoff workflow.

## Evidence and responsibility mapping

- `SOURCE_INVENTORY.md` — reference repositories and what each is authoritative/useful for.
- `RESPONSIBILITY_INVENTORY.md` — normalized behaviours/responsibilities independent of historical files.
- `RESPONSIBILITY_DESTINATIONS.md` — where retained/redesigned responsibilities belong in EpicHub.
- `PROTOCOL_AUTHORITIES.md` — source of truth for important wire/profile/file-format contracts.
- `GAP_ANALYSIS.md` — new EpicHub responsibilities and unresolved technical decisions.

## Target architecture contracts

- `CODEBASE_TOPOLOGY.md` — provisional finished Kotlin/Rust/file topology and file responsibilities.
- `DEPENDENCY_RULES.md` — allowed dependency direction and prohibited ownership leaks.
- `STATE_OWNERSHIP.md` — sole mutable authority for important state.
- `DATA_FLOW.md` — major live/tuning/accessory/logging/analyzer data paths.
- `THREADING_MODEL.md` — UI/acquisition/logging/analysis concurrency model.
- `KOTLIN_RUST_BOUNDARY.md` — coarse JNI/FFI contract and ownership split.
- `ACTION_MODEL.md` — semantic Local/Accessory/ECU actions.
- `SAFETY_BOUNDARIES.md` — production safety/authority boundaries.
- `NAMING_RULES.md` — permanent descriptive naming and experiment rules.
- `TEST_STRATEGY.md` — intended qualification layers and subsystem testing.

## Current handoff

The single current continuation index is `../HANDOFF.md`.

Git history is the archive; do not create dated/numbered handoff files.
