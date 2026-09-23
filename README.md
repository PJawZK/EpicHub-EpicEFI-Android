# EpicEFI – EpicHub

EpicHub is a new Android application for EpicEFI, designed as a clean-slate successor to the earlier EpicDash/EpicDash-JZ work.

The production application is deliberately built around **Kotlin/Jetpack Compose + Rust**. Kotlin owns Android-facing application/UI/platform work; Rust owns the native engine and high-volume ECU/measurement/logging/analysis work. Runtime state and Kotlin↔Rust communication remain typed/packed rather than using a JSON/WebView/browser message architecture. See D-023 and `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`.

Existing EpicDash, EpicDash-JZ, EpicEFI firmware, Tune Viewer, plugin and related repositories are **reference/evidence sources, not implementation templates**. EpicHub does not bulk-port historical subsystems. Before an inherited capability is implemented, its relevant old behavior, callers/state/lifecycle/failure paths/tests/bugs are reviewed piece-by-piece, required behavior is separated from accidental/buggy/obsolete behavior, and the clean replacement is independently designed and qualified. See `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` and D-022.

## Current phase

**Phase 1 was explicitly accepted and closed on 2026-09-23.**

The first Phase-2 Kotlin/Rust foundation is also complete and accepted on `main`: Compose host, dual-ABI Rust library, `NativeEngine`, versioned packed RuntimeSnapshot ABI and minimal Rust ChannelStore were CI-qualified and physically exercised on the SM-A137F phone and SM-T500 tablet.

Current design work is intentionally establishing the **application UI skeleton and diagnostic/bug-capture foundation before real ECU/USB integration**. Active design contracts live in:

- `docs/architecture/UI_FOUNDATION_LAYOUT.md`
- `docs/architecture/DIAGNOSTICS_CAPTURE_FOUNDATION.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`

After those contracts are accepted and implemented/qualified against the existing synthetic foundation, the next inherited subsystem is the read-only live ECU acquisition path under D-022.

Current authority/index:

- `docs/architecture/README.md`
- `docs/architecture/DECISIONS.md`
- `docs/architecture/GAP_ANALYSIS.md`
- `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`
- `docs/HANDOFF.md`

Phase-1 closure records remain under `docs/architecture/PHASE1_*` and `REVIEW_CHECKLIST.md`; they are historical closure evidence rather than current work instructions.

## Development handoff

The single current chat/project handoff lives at `docs/HANDOFF.md`. Git history is the archive; numbered or dated handoff files are not used.