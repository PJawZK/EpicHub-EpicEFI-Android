# EpicHub Architecture Documents

Phase 1 architecture/responsibility archaeology is accepted and closed. The first Phase-2 Kotlin/Rust foundation is also merged and physically qualified on the SM-A137F phone and SM-T500 tablet.

Current Phase-2 work establishes the application shell, native Tuning Tools, four-workspace navigation model and diagnostic/problem-capture foundation **before** real ECU/USB integration.

## Start here

- `DECISIONS.md` — durable architectural decisions through D-025.
- `DEVELOPMENT_WORKFLOW.md` — repository and chat/handoff workflow.
- `SOURCE_REIMPLEMENTATION_METHOD.md` — mandatory piece-by-piece historical-source review and clean rewrite method used throughout implementation.
- `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` — locked Kotlin/Compose + Rust runtime and boundary-only serialization contract; also clarifies that HTML/CSS/JavaScript may be used for design prototypes without becoming production runtime.
- `ANDROID_BUILD_BASELINE.md` — locked package/API/ABI/device support contract and foundation qualification baseline.
- `UI_FOUNDATION_LAYOUT.md` — four primary workspaces, six distinct functional/domain routes, four presentation-authority classes and diagnostics/control separation.
- `TUNING_TOOLS_FOUNDATION.md` — native replacement for historical AE/PID/TCharge/Injection-Angle plugin packaging and the UI/runtime ownership contract for those tools.
- `EPICHUB_TUNER_ARCHITECTURE_AND_IMPLEMENTATION_MAP.md` — research-closeout synthesis mapping TunerStudio, MegaLogViewer, EpicScope and the Table Interaction Lab into an EpicHub-native Tuner architecture and implementation sequence. Reference products contribute evidence, not implementation authority.
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md` — typed diagnostics, bounded flight recorder and problem-capture foundation.
- `../HANDOFF.md` — single current development continuation index.

Phase-1 closure/history:

- `PHASE1_SCOPE.md`
- `PHASE1_PROGRESS.md`
- `PHASE1_GATE_ASSESSMENT.md`
- `REVIEW_CHECKLIST.md`

These are closure/history records, not current Phase-2 work instructions.

## Evidence and responsibility mapping

- `SOURCE_INVENTORY.md` — reference repositories, provenance handling and what each is authoritative/useful for.
- `RESPONSIBILITY_INVENTORY.md` — normalized behaviours/responsibilities independent of historical files.
- `RESPONSIBILITY_DESTINATIONS.md` — where retained/redesigned responsibilities belong in EpicHub.
- `ANALYSIS_RESPONSIBILITY_MAP.md` — permanent owners/boundaries for historical plugin-derived analysis/autotune responsibilities.
- `EPICHUB_TUNER_ARCHITECTURE_AND_IMPLEMENTATION_MAP.md` — cross-reference and implementation map for mature tuning/log-analysis evidence versus current EpicHub ownership.
- `PROTOCOL_AUTHORITIES.md` — source of truth for important wire/profile/file-format contracts.
- `GAP_ANALYSIS.md` — current unresolved implementation/fixture/benchmark/product work, classified by when it becomes required.

Deep-dive evidence:

- `ECU_PROTOCOL_DIRECT_VERIFICATION.md`
- `ESP32_ACCESSORY_EVIDENCE.md`
- `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md`
- `UI_INTERACTION_EVIDENCE.md`
- `LOGGING_INTEROP_EVIDENCE.md`
- `DIAGNOSTICS_EVIDENCE.md`
- `DIAGNOSTICS_CORE_EVIDENCE.md`
- `TUNE_VIEWER_EVIDENCE.md`
- `ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md`

Historical JSON/JavaScript/TypeScript/React/WebView references in evidence documents describe those reference systems. They do **not** override D-023 or select EpicHub runtime languages/formats.

Likewise, historical TunerStudio plugin/JAR packaging is evidence only. D-024 maps retained capabilities into native EpicHub Tuning Tools rather than a runtime plugin subsystem.

The broad TunerStudio/MLV/EpicScope closeout follows the same rule: useful mature behavior is converted into EpicHub responsibilities, invariants and improvement opportunities rather than cloned screens/classes/workflows.

## Target architecture contracts

- `CODEBASE_TOPOLOGY.md` — current physical topology plus deliberate expansion rules, including the four-workspace/leaf-route split.
- `DEPENDENCY_RULES.md` — allowed dependency direction and prohibited ownership/serialization leaks.
- `STATE_OWNERSHIP.md` — sole mutable authority for important state.
- `DATA_FLOW.md` — major live/tuning/accessory/logging/analyzer data paths.
- `THREADING_MODEL.md` — UI/acquisition/logging/analysis concurrency model.
- `KOTLIN_RUST_BOUNDARY.md` — coarse typed/packed JNI/FFI contract and ownership split.
- `ACTION_MODEL.md` — semantic Local/AccessoryBridge/AccessoryOutput/ECU actions and warning/automation separation.
- `SAFETY_BOUNDARIES.md` — production safety/authority boundaries.
- `NAMING_RULES.md` — permanent descriptive naming and experiment rules.
- `TEST_STRATEGY.md` — qualification layers, MLG/MDF interop, subsystem testing and D-022/D-023 regression methodology.
- `UI_FOUNDATION_LAYOUT.md` — concrete workspace/route/presentation contract before real feature depth.
- `TUNING_TOOLS_FOUNDATION.md` — native guided tuning-tool surface and shared Rust-analysis integration.
- `EPICHUB_TUNER_ARCHITECTURE_AND_IMPLEMENTATION_MAP.md` — proposed Tuner convergence and first implementation-ready offline semantic table/curve slice; it identifies, but does not self-authorize, the D-016 follow-up required before production 3D direct editing.
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md` — concrete diagnostic flight-recorder/capture/export foundation before real subsystem complexity.

## Authority interpretation

When documents differ in level of detail:

1. `DECISIONS.md` states durable architecture decisions.
2. `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` defines the D-023 language/runtime/serialization boundary.
3. protocol/source truth is resolved by `PROTOCOL_AUTHORITIES.md` plus direct evidence documents.
4. `STATE_OWNERSHIP.md`, `DEPENDENCY_RULES.md`, `DATA_FLOW.md`, `KOTLIN_RUST_BOUNDARY.md`, `ACTION_MODEL.md` and `SAFETY_BOUNDARIES.md` define cross-domain contracts.
5. Phase-2 implementation contracts such as `UI_FOUNDATION_LAYOUT.md`, `TUNING_TOOLS_FOUNDATION.md`, `EPICHUB_TUNER_ARCHITECTURE_AND_IMPLEMENTATION_MAP.md` and `DIAGNOSTICS_CAPTURE_FOUNDATION.md` refine the accepted architecture for implementation but may not override durable decisions.
6. `CODEBASE_TOPOLOGY.md` maps those contracts into current/planned homes; it must not override them.
7. evidence documents explain why a decision exists but do not silently supersede a later durable decision.
8. `GAP_ANALYSIS.md` identifies work still open; an open implementation/benchmark gap does not negate an already-locked ownership boundary.

Historical evidence never overrides D-022's clean reimplementation rule, D-023's typed Kotlin/Rust runtime rule, D-024's native Tuning Tools rule or D-025's workspace/domain separation merely because the old implementation used another language, serialization mechanism, plugin mechanism or navigation structure.

## Current handoff

The single current continuation index is `../HANDOFF.md`.

Git history is the archive; do not create dated/numbered handoff files.