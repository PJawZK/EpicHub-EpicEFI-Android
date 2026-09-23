# EpicEFI – EpicHub

EpicHub is a new Android application for EpicEFI, designed as a clean-slate successor to the earlier EpicDash/EpicDash-JZ work.

The application is designed around a Kotlin/Jetpack Compose Android layer and a Rust engine for high-volume ECU, measurement, logging, analysis, and tuning work.

Existing EpicDash, EpicDash-JZ, EpicEFI firmware, Tune Viewer, plugin and related repositories are **reference/evidence sources, not implementation templates**. EpicHub does not bulk-port historical subsystems. Before an inherited capability is implemented, its relevant old behavior, callers/state/lifecycle/failure paths/tests/bugs are reviewed piece-by-piece, required behavior is separated from junk/bugs/obsolete behavior, and the clean replacement is independently designed and qualified. See `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` and D-022.

## Current phase

Phase 1 is at its **documentation-consistency / explicit-acceptance gate**.

The responsibility architecture, ownership, safety boundaries and target topology have been established, but production Android/Rust scaffolding remains blocked until:

1. the final consistency audit confirms the architecture documents agree; and
2. the user explicitly accepts Phase 1.

Current authority/index:

- `docs/architecture/README.md`
- `docs/architecture/DECISIONS.md`
- `docs/architecture/PHASE1_GATE_ASSESSMENT.md`
- `docs/architecture/REVIEW_CHECKLIST.md`
- `docs/architecture/GAP_ANALYSIS.md`

## Development handoff

The single current chat/project handoff lives at `docs/HANDOFF.md`. Git history is the archive; numbered or dated handoff files are not used.