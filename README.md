# EpicEFI – EpicHub

EpicHub is a new Android application for EpicEFI, designed as a clean-slate successor to the earlier EpicDash/EpicDash-JZ work.

The production application is deliberately built around **Kotlin/Jetpack Compose + Rust**. Kotlin owns Android-facing application/UI/platform work; Rust owns the native engine and high-volume ECU/measurement/logging/analysis work. Runtime state and Kotlin↔Rust communication remain typed/packed rather than using a JSON/WebView/browser message architecture. See D-023 and `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`.

EpicHub now also contains a first-class **GitHub Pages product/interaction prototype** under `prototype/`. The Pages surface is used to develop layouts, workflows and feature behavior rapidly while native Android performance work continues independently. Accepted prototype behavior is translated into Compose/Kotlin/Rust; the browser implementation is never embedded as the production runtime. See D-026 and `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`.

The prototype is intentionally modular rather than a single large HTML file. Its TypeScript/Vite structure separates app composition, feature routes, shared UI, semantic models, capability ports, fixture adapters and future external-tool adapters. A future TS-Shim integration belongs behind the prototype's typed adapter/port boundary rather than leaking transport or wire-schema details into EpicHub feature code.

Existing EpicDash, EpicDash-JZ, EpicEFI firmware, Tune Viewer, historical TunerStudio plugins and related repositories are **reference/evidence sources, not implementation templates**. EpicHub does not bulk-port historical subsystems. Before an inherited capability is implemented, its relevant old behavior, callers/state/lifecycle/failure paths/tests/bugs are reviewed piece-by-piece, required behavior is separated from accidental/buggy/obsolete behavior, and the clean replacement is independently designed and qualified. See `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` and D-022.

Historical AE/PID/TCharge/Injection-Angle plugins are not recreated as a runtime plugin system. Their retained capabilities become native **Tuning Tools** on shared Rust analysis infrastructure with Compose workflows, while all ECU mutation remains behind the single semantic tuning authority. See D-024 and `docs/architecture/TUNING_TOOLS_FOUNDATION.md`.

## Development model

EpicHub development now has three coordinated lanes:

```text
GitHub Pages prototype
→ product layout / workflow / interaction specification

Native Android performance/runtime
→ Compose / Kotlin / Rust / JNI / device qualification

Production translation
→ accepted prototype behavior reimplemented against native EpicHub owners/contracts
```

Browser performance does not prove Android performance. Likewise, native Android optimization does not block product/UI iteration in Pages.

## Current phase

**Phase 1 was explicitly accepted and closed on 2026-09-23.**

The first Phase-2 Kotlin/Rust foundation is also complete and accepted on `main`: Compose host, dual-ABI Rust library, `NativeEngine`, versioned packed RuntimeSnapshot ABI and minimal Rust ChannelStore were CI-qualified and physically exercised on the SM-A137F phone and SM-T500 tablet.

Current Phase-2 work includes:

- native UI/diagnostics/performance qualification on the active PR #3 branch;
- the modular GitHub Pages prototype foundation under `prototype/`;
- explicit translation contracts from accepted Pages features into native production owners;
- a future external TS-Shim adapter boundary for the Pages prototype without changing Android production architecture.

Current design contracts live in:

- `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`
- `docs/architecture/UI_FOUNDATION_LAYOUT.md`
- `docs/architecture/TUNING_TOOLS_FOUNDATION.md`
- `docs/architecture/DIAGNOSTICS_CAPTURE_FOUNDATION.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`

The next inherited production subsystem remains subject to D-022 review before real ECU/USB integration. Prototype progress does not bypass that gate.

Current authority/index:

- `docs/architecture/README.md`
- `docs/architecture/DECISIONS.md`
- `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`
- `docs/architecture/GAP_ANALYSIS.md`
- `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`
- `docs/HANDOFF.md`

Phase-1 closure records remain under `docs/architecture/PHASE1_*` and `REVIEW_CHECKLIST.md`; they are historical closure evidence rather than current work instructions.

## Prototype development

From `prototype/`:

```bash
npm install
npm run dev
npm run typecheck
npm test
npm run build
```

`Prototype quality` validates TypeScript/tests/build on relevant PR changes. `Publish EpicHub prototype to Pages` is deliberately manual until the repository's Pages settings/deployment path are confirmed; it can later be promoted to automatic publication from `main`.

## Development handoff

The single current chat/project handoff lives at `docs/HANDOFF.md`. Git history is the archive; numbered or dated handoff files are not used.
