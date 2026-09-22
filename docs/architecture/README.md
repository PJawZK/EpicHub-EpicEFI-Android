# EpicHub Architecture Documents

Phase 1 builds the repository-backed architecture before production Android/Rust implementation begins.

## Start here

- `PHASE1_SCOPE.md` — Phase 1 purpose, exclusions and completion gate.
- `PHASE1_PROGRESS.md` — current completed/current source-review tranches.
- `PHASE1_GATE_ASSESSMENT.md` — current closure assessment and allowed post-Phase-1 open work.
- `REVIEW_CHECKLIST.md` — explicit Phase 1 closure checklist; final user-acceptance item remains the scaffold gate.
- `DECISIONS.md` — durable architectural decisions.
- `DEVELOPMENT_WORKFLOW.md` — repository and chat/handoff workflow.
- `ANDROID_BUILD_BASELINE.md` — initial package/API/ABI/device support contract.

## Evidence and responsibility mapping

- `SOURCE_INVENTORY.md` — reference repositories, provenance handling and what each is authoritative/useful for.
- `RESPONSIBILITY_INVENTORY.md` — normalized behaviours/responsibilities independent of historical files.
- `RESPONSIBILITY_DESTINATIONS.md` — where retained/redesigned responsibilities belong in EpicHub.
- `ANALYSIS_RESPONSIBILITY_MAP.md` — permanent owners/boundaries for plugin-derived analysis/autotune responsibilities.
- `PROTOCOL_AUTHORITIES.md` — source of truth for important wire/profile/file-format contracts.
- `GAP_ANALYSIS.md` — current unresolved implementation/fixture/benchmark/product work, classified by when it becomes required.

Deep-dive evidence:

- `ECU_PROTOCOL_DIRECT_VERIFICATION.md` — targeted direct verification against the current EpicEFI/rusEFI firmware fork.
- `ESP32_ACCESSORY_EVIDENCE.md` — original/evolved BLE↔CAN bridge and accessory boundaries.
- `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md` — project authority, recovery, journals and derived caches.
- `UI_INTERACTION_EVIDENCE.md` — Dashboard/Tuner interaction, responsive behavior and rendering lessons.
- `LOGGING_INTEROP_EVIDENCE.md` — live logging, MSL/MLG/MDF interoperability and analyzer-facing storage lessons.
- `DIAGNOSTICS_EVIDENCE.md` — structured diagnostics, performance and lifecycle tracing evidence.
- `DIAGNOSTICS_CORE_EVIDENCE.md` — shared semantic-channel/warning-definition/test-vector reference review.
- `TUNE_VIEWER_EVIDENCE.md` — tune/profile parsing, exact-identity comparison, definition-diff and visualization lessons.
- `ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md` — AE/PID/TCharge/Injection Angle evidence, analysis profiles and bounded experiment lessons.

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