# EpicHub Architecture Documents

Phase 1 architecture/responsibility archaeology is accepted and closed. Phase 2 now implements the clean application incrementally against the locked decisions and the mandatory D-022 source-reimplementation methodology.

## Start here

- `DECISIONS.md` — durable architectural decisions through D-022.
- `DEVELOPMENT_WORKFLOW.md` — repository and chat/handoff workflow.
- `SOURCE_REIMPLEMENTATION_METHOD.md` — mandatory piece-by-piece historical-source review and clean rewrite method used throughout implementation.
- `ANDROID_BUILD_BASELINE.md` — locked initial package/API/ABI/device support contract.
- `UI_FOUNDATION_LAYOUT.md` — Phase-2 ground layout/navigation/responsive contract for Dashboard, Tuner, Logger, Analyzer, Diagnostics and Settings.
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md` — Phase-2 structured diagnostics, flight-recorder and bug-capture design to establish before complex subsystem integration.

Phase-1 closure/history:

- `PHASE1_SCOPE.md`
- `PHASE1_PROGRESS.md`
- `PHASE1_GATE_ASSESSMENT.md`
- `REVIEW_CHECKLIST.md`

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

- `CODEBASE_TOPOLOGY.md` — target responsibility topology and planned Kotlin/Rust/file homes; exact physical module split may evolve without changing ownership.
- `DEPENDENCY_RULES.md` — allowed dependency direction and prohibited ownership leaks.
- `STATE_OWNERSHIP.md` — sole mutable authority for important state.
- `DATA_FLOW.md` — major live/tuning/accessory/logging/analyzer data paths.
- `THREADING_MODEL.md` — UI/acquisition/logging/analysis concurrency model.
- `KOTLIN_RUST_BOUNDARY.md` — coarse JNI/FFI contract and ownership split.
- `ACTION_MODEL.md` — semantic Local/AccessoryBridge/AccessoryOutput/ECU actions and warning/automation separation.
- `SAFETY_BOUNDARIES.md` — production safety/authority boundaries.
- `NAMING_RULES.md` — permanent descriptive naming and experiment rules.
- `TEST_STRATEGY.md` — qualification layers, MLG/MDF interop, subsystem testing and D-022 regression methodology.
- `UI_FOUNDATION_LAYOUT.md` — concrete screen-region/responsive foundation before real feature depth.
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md` — concrete diagnostics/capture/export foundation before real subsystem complexity.

## Authority interpretation

When documents differ in level of detail:

1. `DECISIONS.md` states durable architecture decisions.
2. protocol/source truth is resolved by `PROTOCOL_AUTHORITIES.md` plus direct evidence documents.
3. `STATE_OWNERSHIP.md`, `DEPENDENCY_RULES.md`, `DATA_FLOW.md`, `KOTLIN_RUST_BOUNDARY.md`, `ACTION_MODEL.md` and `SAFETY_BOUNDARIES.md` define cross-domain contracts.
4. Phase-2 implementation contracts such as `UI_FOUNDATION_LAYOUT.md` and `DIAGNOSTICS_CAPTURE_FOUNDATION.md` refine the accepted architecture for implementation but may not override durable decisions.
5. `CODEBASE_TOPOLOGY.md` maps those contracts into planned homes; it must not override them.
6. evidence documents explain why a decision exists but do not silently supersede a later durable decision.
7. `GAP_ANALYSIS.md` identifies work still open; an open implementation/benchmark gap does not negate an already-locked ownership boundary.

Historical evidence never overrides D-022's clean reimplementation rule merely because old code exists.

## Current handoff

The single current continuation index is `../HANDOFF.md`.

Git history is the archive; do not create dated/numbered handoff files.