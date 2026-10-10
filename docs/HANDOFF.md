# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual branch/PR/CI state, then continue from the parallel **Pages product prototype + native Android performance/runtime** workflow.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ, historical plugins or old standalone HTML files when EpicHub repository contracts define a newer deliberate design.

---

# Repository state

- integration branch: `main`
- active branch: `phase2/ui-diagnostics-foundation`
- active draft PR: **#3 — Phase 2: establish UI and diagnostics foundations**
- Phase 1: accepted/closed
- first Phase-2 Kotlin/Rust foundation: merged/qualified
- last native/performance harness head before the Pages architecture work: `faff3a2d17f5838c4bd78a12bd72f27aec014855`
- Foundation quality #275 for that head: **PASS**
- latest substantive Pages/workflow head before this handoff update: `574e76c1d869c0b2c262f4a101dcb843fa92343e`
- at the time of this handoff update, Foundation quality #285 and Prototype quality #10 were running for that head; verify their final conclusions before relying on them.

The current handoff commit itself is expected to be newer than the substantive head above. Always verify the actual PR head.

---

# Locked architecture

Durable decisions now run through **D-026**.

Start with:

- `docs/architecture/README.md`
- `docs/architecture/DECISIONS.md`
- `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`
- `docs/architecture/CODEBASE_TOPOLOGY.md`
- `docs/architecture/DEVELOPMENT_WORKFLOW.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`

Key retained rules:

- D-022: inherited behavior is reviewed piece-by-piece and reimplemented cleanly;
- D-023: Android production runtime remains Kotlin/Jetpack Compose + Rust with typed/packed runtime boundaries;
- D-024: historical plugin capabilities become native Tuning Tools, not a runtime plugin system;
- D-025: four primary workspaces group six distinct functional/domain routes without ownership collapse;
- D-026: GitHub Pages is a first-class modular product/interaction lab, not a production runtime.

---

# New development model — accepted direction

EpicHub now deliberately uses three coordinated lanes:

```text
Pages product/UX lane
→ layout + interaction + workflow + behavior specification

Native Android performance/runtime lane
→ Compose/Kotlin/Rust/JNI/lifecycle/device qualification

Production translation lane
→ accepted Pages feature → explicit translation contract → native implementation → qualification
```

This is intended to prevent native performance work from blocking product development while also preventing browser prototypes from becoming production architecture by convenience.

Browser performance is not Android performance evidence.

---

# GitHub Pages prototype foundation

A new modular prototype now exists under:

`prototype/`

Current foundation includes:

```text
prototype/
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── README.md
├── src/
│   ├── main.ts
│   ├── app/
│   ├── model/
│   ├── ports/
│   ├── adapters/
│   │   ├── fixtures/
│   │   └── ts-shim/
│   ├── ui/
│   ├── features/
│   │   ├── dashboard/
│   │   ├── tuner/
│   │   ├── tuning-tools/
│   │   ├── logger/
│   │   ├── analyzer/
│   │   ├── diagnostics/
│   │   └── settings/
│   └── styles/
└── tests/
```

The current shell is intentionally minimal: four primary workspaces, distinct Tuner/Tuning Tools and Logger/Analyzer leaf routes, deterministic fixture runtime values and unmistakable `FIXTURE` source labeling.

The structure deliberately prevents a repeat of one giant HTML file/script/stylesheet. `index.html` is mount-only, `main.ts` is bootstrap-only, feature code stays feature-local, shared UI requires real shared semantics, and adapter/wire code cannot live inside pages.

A prototype file around 350–500 lines is a responsibility-review signal rather than a target.

---

# Pages CI / publication

New workflows:

- `.github/workflows/prototype-quality.yml`
  - TypeScript typecheck
  - Vitest contract tests
  - Vite production build
  - build artifact upload

- `.github/workflows/prototype-pages.yml`
  - manual GitHub Pages build/deploy
  - deliberately manual until repository Pages settings/deployment are confirmed

The prototype toolchain intentionally follows the lightweight EpicScope pattern (TypeScript + Vite + Vitest) while keeping EpicHub-specific ownership and features.

---

# TS-Shim groundwork

The Pages architecture deliberately prepares for a future TS-Shim interface without coupling features to it.

Required conceptual path:

```text
EpicHub Pages feature
→ typed prototype port
→ prototype/adapters/ts-shim
→ external transport/wire protocol
→ TS-Shim
```

The adapter owns endpoint configuration, reconnect behavior, wire schema/version, serialization and capability translation.

Features must not know socket URLs, JSON keys, request opcodes or shim-specific connection state.

If the shim uses JSON/WebSocket/HTTP, that is an explicit external tooling interoperability boundary under D-023, not a production Android runtime choice.

Initial shim work should be read-only until mutation identity, safety and verification are separately accepted. The browser prototype must never gain raw page/offset/byte authority by accident.

Before implementing this adapter, inspect the actual TS-Shim contract rather than guessing it.

---

# Native Android performance state

The native app/runtime implementation has not been changed by the new Pages scaffold/architecture work.

The previous performance sequence established that the dominant remaining UI issue is transition-related rather than JNI/runtime cost. The most recent native experiment retains inactive destination compositions using `ReusableContentHost` for physical A/B testing.

Macrobenchmark infrastructure is now external to the target app and uses the SM-A137F physical device. Earlier harness issues exposed and fixed:

- unsigned Macrobenchmark APK publication;
- missing APK signature verification in CI;
- AndroidX warm `startActivityAndWait()` framestats failure on the SM-A137F;
- UI Automator accessibility lookup assumptions;
- loss of useful diagnostic evidence when instrumentation failed.

The #275 kit contains the hardened runner. A successful deterministic physical benchmark result for the current retention experiment is still required before selecting the next native optimization direction.

See `docs/PERFORMANCE_BENCHMARKING.md` for the physical benchmark workflow.

---

# Product information architecture

D-025 remains unchanged:

```text
Primary workspaces
Dashboard | Tuner | Logger | Diagnostics

Distinct routes
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics

Tuner workspace  → Tuner | Tuning Tools
Logger workspace → Logger | Analyzer
Settings          → global
```

Visual nesting is presentation-only:

```text
Analyzer !→ Logger UI/state as gateway
Tuning Tools !→ Tuner UI/state as gateway
```

Pages and Android must both preserve this separation.

---

# Pages → native translation rule

Do not mechanically port a completed TypeScript feature.

For each accepted substantial Pages feature, record:

```text
visual/interaction authority
inputs / outputs
actions
state transitions
empty/loading/error/stale behavior
identity/provenance requirements
production owner(s)
Compose responsibility
Kotlin platform/workflow responsibility
Rust/domain responsibility
browser-only shortcuts to discard
native qualification required
```

Then implement natively against the production contracts and physically compare/qualify.

---

# Immediate next work

1. Verify current **Prototype quality** and **Foundation quality** results after the Pages foundation commits.
2. Fix any TypeScript/Vite/CI issue before expanding the prototype.
3. Confirm/enable GitHub Pages using the repository's GitHub Actions deployment path, then run `Publish EpicHub prototype to Pages` manually for the first publication.
4. Use the Pages shell as the new product-development base rather than returning to giant standalone HTML files.
5. Grow features as vertical modular slices, beginning with shell/navigation/source-state details and then Dashboard/Tuner/Tuning Tools/Logger/Analyzer/Diagnostics/Settings as needed.
6. Continue the native SM-A137F Macrobenchmark path independently; do not simplify accepted product functionality merely to hide native performance cost.
7. Review the real TS-Shim interface before creating its adapter implementation.

---

# Deferred / guarded work

Still guarded by production contracts rather than by what the prototype can visually demonstrate:

- real Android ECU USB session/protocol;
- ECU mutation/Burn;
- production logging format implementation;
- deep native Analyzer engine;
- qualified tuning-tool algorithms;
- BLE/accessory migration;
- TS-Shim mutation/control capability.

The Pages prototype may design those workflows earlier, but it does not grant production authority or bypass D-022/D-019 safety/identity gates.
