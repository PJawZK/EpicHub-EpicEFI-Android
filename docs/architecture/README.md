# EpicHub Architecture Documents

Phase 1 architecture/responsibility archaeology is accepted and closed. The first Phase-2 Kotlin/Rust foundation is also merged and physically qualified on the SM-A137F phone and SM-T500 tablet.

Current Phase-2 work now runs two deliberate implementation surfaces in parallel: the native Android product/runtime and a modular GitHub Pages product/interaction prototype. The Pages prototype accelerates layout/workflow development and supplies accepted behavior contracts for native translation; it never becomes the production runtime.

## Start here

- `DECISIONS.md` — durable architectural decisions through D-026.
- `DEVELOPMENT_WORKFLOW.md` — repository, Pages/native translation and chat/handoff workflow.
- `PAGES_PROTOTYPE_ARCHITECTURE.md` — modular GitHub Pages product/interaction lab, anti-monolith rules, translation contracts and future TS-Shim adapter boundary.
- `SOURCE_REIMPLEMENTATION_METHOD.md` — mandatory piece-by-piece historical-source review and clean rewrite method used throughout implementation.
- `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` — locked Kotlin/Compose + Rust production runtime and boundary-only serialization contract; also defines why the Pages TypeScript/browser surface remains development/tooling only.
- `ANDROID_BUILD_BASELINE.md` — locked package/API/ABI/device support contract and foundation qualification baseline.
- `UI_FOUNDATION_LAYOUT.md` — four primary workspaces, six distinct functional/domain routes, four presentation-authority classes and diagnostics/control separation.
- `TUNING_TOOLS_FOUNDATION.md` — native replacement for historical AE/PID/TCharge/Injection-Angle plugin packaging and the UI/runtime ownership contract for those tools.
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

Historical JSON/JavaScript/TypeScript/React/WebView references in evidence documents describe those reference systems. They do **not** override D-023 or select EpicHub production runtime languages/formats.

Current TypeScript/Vite code under `prototype/` is different: it is an explicitly accepted development/specification surface under D-026. It may use browser-native techniques and explicit external-tool adapters while remaining outside the Android production runtime.

Likewise, historical TunerStudio plugin/JAR packaging is evidence only. D-024 maps retained capabilities into native EpicHub Tuning Tools rather than a runtime plugin subsystem.

## Target architecture contracts

- `CODEBASE_TOPOLOGY.md` — current physical topology plus deliberate expansion rules, including native product and Pages prototype homes.
- `DEPENDENCY_RULES.md` — allowed dependency direction and prohibited ownership/serialization leaks across native and prototype surfaces.
- `STATE_OWNERSHIP.md` — sole mutable authority for important production state.
- `DATA_FLOW.md` — major live/tuning/accessory/logging/analyzer data paths.
- `THREADING_MODEL.md` — UI/acquisition/logging/analysis concurrency model.
- `KOTLIN_RUST_BOUNDARY.md` — coarse typed/packed JNI/FFI contract and ownership split.
- `ACTION_MODEL.md` — semantic Local/AccessoryBridge/AccessoryOutput/ECU actions and warning/automation separation.
- `SAFETY_BOUNDARIES.md` — production safety/authority boundaries.
- `NAMING_RULES.md` — permanent descriptive naming and experiment rules.
- `TEST_STRATEGY.md` — qualification layers, MLG/MDF interop, subsystem testing and D-022/D-023 regression methodology.
- `UI_FOUNDATION_LAYOUT.md` — concrete workspace/route/presentation contract.
- `PAGES_PROTOTYPE_ARCHITECTURE.md` — browser-prototype structure, feature boundaries, translation contract and TS-Shim interop seam.
- `TUNING_TOOLS_FOUNDATION.md` — native guided tuning-tool surface and shared Rust-analysis integration.
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md` — concrete diagnostic flight-recorder/capture/export foundation before real subsystem complexity.

## Authority interpretation

When documents differ in level of detail:

1. `DECISIONS.md` states durable architecture decisions.
2. `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` defines the D-023 production language/runtime/serialization boundary.
3. `PAGES_PROTOTYPE_ARCHITECTURE.md` defines the D-026 development/specification browser surface and may not override production runtime authority.
4. protocol/source truth is resolved by `PROTOCOL_AUTHORITIES.md` plus direct evidence documents.
5. `STATE_OWNERSHIP.md`, `DEPENDENCY_RULES.md`, `DATA_FLOW.md`, `KOTLIN_RUST_BOUNDARY.md`, `ACTION_MODEL.md` and `SAFETY_BOUNDARIES.md` define cross-domain production contracts.
6. Phase-2 implementation contracts such as `UI_FOUNDATION_LAYOUT.md`, `TUNING_TOOLS_FOUNDATION.md` and `DIAGNOSTICS_CAPTURE_FOUNDATION.md` refine the accepted architecture for implementation but may not override durable decisions.
7. `CODEBASE_TOPOLOGY.md` maps those contracts into current/planned homes; it must not override them.
8. evidence documents explain why a decision exists but do not silently supersede a later durable decision.
9. `GAP_ANALYSIS.md` identifies work still open; an open implementation/benchmark gap does not negate an already-locked ownership boundary.

Historical evidence never overrides D-022's clean reimplementation rule, D-023's typed Kotlin/Rust production runtime rule, D-024's native Tuning Tools rule, D-025's workspace/domain separation or D-026's explicit separation between Pages development code and Android production runtime.

## Current handoff

The single current continuation index is `../HANDOFF.md`.

Git history is the archive; do not create dated/numbered handoff files.
