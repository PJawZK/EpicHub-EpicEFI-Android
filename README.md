# EpicEFI – EpicHub

EpicHub is the native Android tuning/dashboard/logging application for EpicEFI, designed as a clean-slate successor to the earlier EpicDash/EpicDash-JZ work.

The production application is built around **Kotlin/Jetpack Compose + Rust**. Kotlin owns Android-facing application/UI/platform work; Rust owns the native engine, semantic tuning authority, ECU definition/profile logic, logging/analysis foundations, and write/reconciliation safety rules. Runtime state and Kotlin↔Rust communication remain typed/packed rather than using a JSON/WebView/browser runtime architecture. See D-023 and `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`.

Existing EpicDash, EpicDash-JZ, EpicEFI firmware, Tune Viewer, historical TunerStudio plugins and related repositories are **reference/evidence sources, not implementation templates**. EpicHub does not bulk-port historical subsystems. Retained behavior is researched piece-by-piece, accidental/obsolete behavior is separated from required behavior, and clean replacements are independently designed and qualified. See `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` and D-022.

Historical AE/PID/TCharge/Injection-Angle plugins are not recreated as a runtime plugin system. Their retained capabilities become native **Tuning Tools** on shared Rust analysis infrastructure with Compose workflows, while all ECU mutation remains behind the single semantic tuning authority. See D-024 and `docs/architecture/TUNING_TOOLS_FOUNDATION.md`.

## Current phase

**Phase 1 was accepted and closed on 2026-09-23.**

Phase 2 has progressed well beyond the original UI/diagnostics foundation. The current qualified baseline includes:

- native Compose application/workspace shell;
- Rust-owned semantic `TuneSession` / `TuneDraft` editing authority;
- shared 2D/3D editing, selection, interpolation, smoothing, Brush/Pull, history and Undo/Redo;
- generic definition-driven multi-ECU profile/protocol compilation and exact full-signature runtime authority;
- Android profile-package selection and explicit USB candidate/permission/connection ownership;
- definition-driven verified live page acquisition and atomic semantic tune installation;
- retained exact `TuneSourcePageImage` authority beside the semantic tune;
- source-relative guarded RAM-write planning and native opaque write batches;
- constrained Android USB execution with pre-read exact verification, firmware ACK verification, and mandatory exact readback;
- qualified pure verified-prefix retained-source reconciliation for partial-success recovery.

The critical invariant remains:

```text
TuneDraft Apply != ECU RAM write != Burn
```

The next implementation boundary is engine-owned integration of verified post-write retained-source reconciliation, followed later by explicit synchronization state, stronger write serialization, user-facing RAM-write controls, and separate Burn/persist support.

## Branch model

The repository was consolidated on 2026-10-16:

- `main` is the latest fully qualified baseline.
- `current-work` is the only active development branch.

Historical PRs, commits, workflow runs and Git history remain the audit trail; historical feature branches are not part of the ongoing development model.

The current qualified baseline on `main` is:

`8f7bd82dfaf967d7576b37482af5f9a4b3699fc7`

qualified by Foundation run `38858098167`.

## Current authority/index

- `docs/architecture/README.md`
- `docs/architecture/DECISIONS.md`
- `docs/architecture/GAP_ANALYSIS.md`
- `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`
- `docs/HANDOFF.md`

Phase-1 closure records remain under `docs/architecture/PHASE1_*` and `REVIEW_CHECKLIST.md`; they are historical closure evidence rather than current work instructions.

## Development handoff

The single current continuation handoff is `docs/HANDOFF.md`.

Git history, closed PRs and qualification runs are the archive; dated/numbered handoff files are not used for current continuation state.
