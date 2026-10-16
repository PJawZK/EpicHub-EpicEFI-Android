# EpicHub Architecture Documents

Phase 1 architecture/responsibility archaeology is accepted and closed. Phase 2 now has a qualified end-to-end vertical slice spanning native semantic tuning, generic ECU definitions/profile authority, explicit Android USB connection/live acquisition, retained exact source-page authority, guarded RAM-write execution, and pure verified-prefix retained-source reconciliation.

The repository was consolidated on 2026-10-16 so `main` is the latest qualified baseline and `current-work` is the single active development branch. Historical PRs/commits/workflow runs remain the audit trail; historical feature branches are not the ongoing working model.

## Start here

- `DECISIONS.md` — durable architectural decisions through D-025.
- `DEVELOPMENT_WORKFLOW.md` — repository and chat/handoff workflow.
- `SOURCE_REIMPLEMENTATION_METHOD.md` — mandatory piece-by-piece historical-source review and clean rewrite method used throughout implementation.
- `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` — locked Kotlin/Compose + Rust runtime and boundary-only serialization contract; HTML/CSS/JavaScript may be used for design prototypes without becoming production runtime.
- `ANDROID_BUILD_BASELINE.md` — package/API/ABI/device support contract and Foundation qualification baseline.
- `UI_FOUNDATION_LAYOUT.md` — four primary workspaces, leaf/domain routes, presentation-authority classes and diagnostics/control separation.
- `TUNING_TOOLS_FOUNDATION.md` — native replacement for historical plugin packaging and shared Rust-analysis integration.
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md` — typed diagnostics, bounded flight recorder and problem-capture foundation.
- `TUNER_3D_EDITING_CONTRACT.md` — accepted semantic contract for direct 3D editing over the same Rust-owned tune state as 2D.
- `TUNER_TABLE_PROJECTION.md` — representation-neutral Rust table projection shared by native 2D and 3D views.
- `TUNER_COMMAND_ROUTER.md` — shared table command/selection/input convergence layer.
- `TUNER_POINTER_BRUSH.md` — continuous 2D pointer Brush routing/coalescing contract.
- `../HANDOFF.md` — single current development continuation index and exact qualified boundary.

Phase-1 closure/history:

- `PHASE1_SCOPE.md`
- `PHASE1_PROGRESS.md`
- `PHASE1_GATE_ASSESSMENT.md`
- `REVIEW_CHECKLIST.md`

These are closure/history records, not current Phase-2 work instructions.

## Current qualified architecture boundary

The current baseline has already implemented and qualified the following chain:

```text
profile package / ECU definition
    ↓
compiled profile + protocol bundle
    ↓
explicit USB candidate / permission / connection
    ↓
exact runtime signature authority
    ↓
verified live page acquisition
    ↓
retained exact TuneSourcePageImage
    +
atomic semantic TuneSession install
    ↓
2D / 3D engine-owned TuneDraft editing
    ↓
source-relative write intent
    ↓
guarded native RAM-write batch
    ↓
constrained Android USB execution
    ↓
pre-read exact verification
    ↓
write + ACK verification
    ↓
mandatory exact readback
    ↓
pure verified-prefix retained-source reconciliation
```

The immediate next architectural integration is engine-owned replacement of only the retained source-page image after an exact readback-verified transaction prefix. It must preserve `TuneSession`, `TuneDraft`, history and revision, make no whole-session synchronization claim, and remain separate from Burn/persistence.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

## Evidence and responsibility mapping

- `SOURCE_INVENTORY.md` — reference repositories, provenance handling and what each is authoritative/useful for.
- `RESPONSIBILITY_INVENTORY.md` — normalized behaviours/responsibilities independent of historical files.
- `RESPONSIBILITY_DESTINATIONS.md` — where retained/redesigned responsibilities belong in EpicHub.
- `ANALYSIS_RESPONSIBILITY_MAP.md` — permanent owners/boundaries for historical plugin-derived analysis/autotune responsibilities.
- `PROTOCOL_AUTHORITIES.md` — source of truth for important wire/profile/file-format contracts.
- `GAP_ANALYSIS.md` — unresolved implementation/fixture/benchmark/product work, classified by when it becomes required.

Deep-dive evidence includes:

- `ECU_PROTOCOL_DIRECT_VERIFICATION.md`
- `ESP32_ACCESSORY_EVIDENCE.md`
- `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md`
- `UI_INTERACTION_EVIDENCE.md`
- `LOGGING_INTEROP_EVIDENCE.md`
- `DIAGNOSTICS_EVIDENCE.md`
- `DIAGNOSTICS_CORE_EVIDENCE.md`
- `TUNE_VIEWER_EVIDENCE.md`
- `ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md`

Historical JSON/JavaScript/TypeScript/React/WebView references describe reference systems and do not override D-023. Historical TunerStudio plugin/JAR packaging is evidence only; D-024 maps retained capabilities into native EpicHub Tuning Tools.

## Target architecture contracts

- `CODEBASE_TOPOLOGY.md`
- `DEPENDENCY_RULES.md`
- `STATE_OWNERSHIP.md`
- `DATA_FLOW.md`
- `THREADING_MODEL.md`
- `KOTLIN_RUST_BOUNDARY.md`
- `ACTION_MODEL.md`
- `SAFETY_BOUNDARIES.md`
- `NAMING_RULES.md`
- `TEST_STRATEGY.md`
- `UI_FOUNDATION_LAYOUT.md`
- `TUNING_TOOLS_FOUNDATION.md`
- `DIAGNOSTICS_CAPTURE_FOUNDATION.md`
- `TUNER_TABLE_PROJECTION.md`
- `TUNER_COMMAND_ROUTER.md`
- `TUNER_POINTER_BRUSH.md`
- `TUNER_3D_EDITING_CONTRACT.md`

## Authority interpretation

When documents differ in detail:

1. `DECISIONS.md` states durable architecture decisions.
2. `RUNTIME_LANGUAGE_AND_SERIALIZATION.md` defines the D-023 runtime/serialization boundary.
3. protocol/source truth comes from `PROTOCOL_AUTHORITIES.md` plus direct evidence documents.
4. ownership and cross-domain contracts come from `STATE_OWNERSHIP.md`, `DEPENDENCY_RULES.md`, `DATA_FLOW.md`, `KOTLIN_RUST_BOUNDARY.md`, `ACTION_MODEL.md` and `SAFETY_BOUNDARIES.md`.
5. implementation contracts refine those durable decisions without silently overriding them.
6. `CODEBASE_TOPOLOGY.md` maps those contracts into physical code homes.
7. evidence documents explain why decisions exist but do not supersede later durable decisions.
8. `GAP_ANALYSIS.md` identifies open work; an open implementation gap does not negate an already-locked ownership boundary.
9. `../HANDOFF.md` defines the exact current continuation/qualification boundary and takes precedence over stale phase-status prose.

Historical evidence never overrides D-022's clean reimplementation rule, D-023's typed Kotlin/Rust runtime rule, D-024's native Tuning Tools rule, or D-025's workspace/domain separation.
