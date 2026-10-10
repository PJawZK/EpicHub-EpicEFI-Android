# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the actual `phase2/performance-foundation` head, PR #4, and latest Foundation quality run before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The current direction is **native EpicHub product development**, not Pages/prototype-shell work and not continued optimization of provisional placeholder UI.

---

# Repository state

- integration branch: `main`
- active branch: `phase2/performance-foundation`
- active draft PR: **#4 — Phase 2: continue EpicHub native performance qualification**
- PR #4 base: `main` at `768b51ce9468406d860b214c134d1d524be4940a`
- PR #4 is open, draft and mergeable as of 2026-10-10
- the old PR #3 / Pages-prototype history is superseded for active development
- the active branch was rebuilt from the last native pre-Pages state and contains no accepted Pages runtime architecture

The PR #4 body is stale in one important respect: it still describes `ReusableContentHost` as retained for A/B testing and says successful physical qualification is pending. Both are obsolete. `ReusableContentHost` was removed after deterministic physical crashes and the native performance foundation has now completed multiple successful physical qualification rounds.

---

# Locked architecture

## D-022 — source reimplementation method

Inherited behavior is reviewed slice-by-slice and cleanly reimplemented. Do not bulk-port EpicDash-JZ or historical plugin subsystems.

## D-023 — runtime language and serialization

- production runtime: Kotlin / Jetpack Compose + Rust;
- no WebView/JavaScript/DOM production runtime architecture;
- Kotlin↔Rust uses semantic JNI operations and bounded/versioned packed results;
- JSON/CBOR are not universal runtime buses;
- serialization exists only at explicit persistence/export/interoperability boundaries.

## D-024 — native Tuning Tools

AE / PID / TCharge / Injection Angle become native Tuning Tools using shared Rust analysis infrastructure and the single semantic tuning authority. They are not runtime plugins.

## D-025 — navigation/domain structure

Primary workspaces:

```text
Dashboard | Tuner | Logger | Diagnostics
```

Leaf/domain routes:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

Grouping is presentation-only:

```text
Tuner workspace  → Tuner | Tuning Tools
Logger workspace → Logger | Analyzer
```

Do not make Analyzer depend on Logger UI/ViewModel/state and do not make Tuning Tools depend on Tuner UI/ViewModel/state.

---

# Native UI/runtime state

The production shell is Kotlin/Compose and conditionally composes only the active route.

Important rule: **do not reintroduce `ReusableContentHost` or retained inactive route compositions as a performance shortcut.** Physical testing showed deterministic `IllegalArgumentException: measure is called on a deactivated node` crashes when switching Dashboard → Tuner. Removing retained-route composition eliminated the crash.

Current shell behavior:

- compact phone: content + bottom primary navigation;
- wide layout threshold: `maxWidth >= 840.dp`;
- wide/tablet: left navigation rail + content;
- Tuner workspace contains Tuner/Tuning Tools route tabs;
- Logger workspace contains Logger/Analyzer route tabs;
- last meaningful Tuner/Logger leaf route is remembered;
- Diagnostics and capture foundation remain active;
- RuntimeSnapshot loop and Rust/native-engine foundation remain active.

Current Tuner/Tools/Logger/Analyzer visuals are deliberately provisional structural UI. Do not spend more time micro-optimizing these placeholder screens before replacing them with real product UI.

---

# Physical performance qualification — CLOSED FOUNDATION PHASE

The performance investigation is now sufficiently mature to stop blocking UI/function development.

Authoritative final baseline:

`docs/PERFORMANCE_BASELINE_2026-10-10.md`

Benchmark methodology:

`docs/PERFORMANCE_BENCHMARKING.md`

## Physical device

Samsung SM-A137F, API 34:

- 1080×2408
- 450 dpi
- approximately 384×856 dp
- portrait
- 60 Hz
- Android app ABI list: `armeabi-v7a,armeabi`
- EpicHub selected ABI: `armeabi-v7a`
- CPU clocks not locked
- sustained-performance mode off

This qualifies the **compact phone branch only**. It does not qualify the wide/tablet shell. SM-T500 landscape should be tested later when real tablet UI exists.

## Key physical-history conclusions

1. First physical benchmark exposed deterministic Compose crash:
   `IllegalArgumentException: measure is called on a deactivated node`.
2. Root cause was retained/deactivated route composition via `ReusableContentHost`.
3. Removing it eliminated the crash.
4. A later stale `UiObject2` harness failure was fixed by re-querying stale nodes.
5. Subsequent full physical suite passed without app crash or stale failure.
6. Selector-driven leaf benchmarks showed real-content route transitions substantially more expensive than trivial placeholder content.
7. A deeper audit found selector-tree traversal itself depended on screen complexity, so coordinate-driven leaf benchmarks were added.
8. Final coordinate-driven runs removed `findObjects()` and per-click `pidof` from the measured loop and preserved the same real-vs-placeholder gap, proving the important penalty is genuinely in the app/rendering path rather than mainly the benchmark driver.
9. No final coordinate run showed an EpicHub crash, stale-node failure, deactivated-node error, process death or app GC event aligned with the measured transition.

## Final coordinate baseline — 2026-10-10

Qualified source build used for the final physical round:

`a20fd4b52a33a2646381c9fd15d881f1e91d1ea8`

CI for that build:

Foundation quality #330 — PASS

APK hashes:

```text
app       7e8d660b83ea8ed8be1fa50a2fba87064ee8912e9aa4ffd9d8c247d44b29c89d
benchmark aadfbdde3fea65efe602ece75925a788ebd14a153cec121543e82a64b63303a1
```

### Tuner ↔ Tuning Tools

Real content:

```text
frame count median 30
CPU P50 22.58 ms
CPU P95 43.31 ms
CPU P99 46.25 ms
```

Placeholder:

```text
frame count 14 every iteration
CPU P50 21.94 ms
CPU P95 24.88 ms
CPU P99 26.30 ms
```

### Logger ↔ Analyzer

Real content:

```text
frame count median 29
CPU P50 23.40 ms
CPU P95 44.80 ms
CPU P99 46.59 ms
```

Placeholder:

```text
frame count 14 every iteration
CPU P50 22.92 ms
CPU P95 26.20 ms
CPU P99 27.47 ms
```

Interpretation:

- real provisional screen trees clearly add substantial rendering work and a high-cost frame population;
- trivial placeholders still sit around ~22–23 ms median on this low-end unlocked-clock device, so some shared shell/navigation/rendering cost also exists;
- this is now a **baseline**, not a reason to optimize temporary cards/screens;
- re-profile once real Tuner tables, Logger live data, Analyzer plots and Dashboard gauges exist.

---

# Benchmark authority

Leaf-route performance authority:

```text
warmTunerLeafNavigationCoordinates
warmTunerLeafNavigationPlaceholderCoordinates
warmLoggerLeafNavigationCoordinates
warmLoggerLeafNavigationPlaceholderCoordinates
```

These resolve route-tab coordinates during setup and use direct coordinate clicks in the measured block.

Older selector-driven leaf methods remain useful for accessibility/harness regression but are not leaf-performance authority because repeated semantics-tree lookup perturbs the workload.

The combined workspace, primary-workspace, Diagnostics-tab and cold-first-visit methods remain available as regression/diagnostic tools.

Narrow Perfetto markers currently exist around:

```text
EpicHub.selectDestination:<destination>
EpicHub.ShellContent:<destination>
```

`ShellContent` marks composable invocation only; it does not by itself measure all later layout/draw/GPU work.

---

# Accessibility benchmark state

`StaticSelectionChoice` now uses one authoritative semantics node for automation targets. The measured non-selected Tuning Tools/Analyzer targets no longer exhibit the previous Samsung non-clickable warning flood.

Remaining non-clickable warnings are mostly selected/no-op route/workspace targets and Diagnostics text during warm-up. This is not currently blocking product development.

---

# Diagnostics / capture foundation

Retained capabilities include:

- bounded process-scoped DiagnosticHub;
- bounded Kotlin and Rust diagnostic rings;
- coarse Rust→Kotlin diagnostic drain;
- typed events/payloads;
- metrics/percentiles;
- lifecycle tracing;
- recent-problem and reproduction captures;
- markers;
- immutable captures;
- ZIP export with `capture.cbor` + `report.txt`;
- Diagnostics UI tabs;
- global Problem capture controls.

Diagnostics is evidence/observation, not connection ownership or warning-rule ownership.

---

# Next development priority

**Stop the placeholder-performance investigation and return to product development.**

Recommended order:

1. establish/translate the proper EpicHub navigation/shell presentation authority;
2. build the real Tuner UI;
3. implement functional tuning/table editing on the native semantic tuning authority;
4. build real Logger behavior/UI;
5. build Analyzer plotting/query behavior/UI;
6. build/refine Dashboard gauges and interaction;
7. refine Diagnostics presentation around actual product behavior;
8. measure the SM-T500 landscape/wide branch when real tablet UI is present;
9. re-run targeted performance benchmarks against real production scenarios instead of structural placeholders.

Performance infrastructure should remain in place as a regression/profiling tool, not become the main project.

---

# Deferred / later work

- real ECU USB transport/session work where not already implemented;
- ECU writes/Burn behind the semantic tuning authority;
- production logger file formats;
- deep Analyzer implementation;
- native AE/PID/TCharge/Injection-Angle algorithms;
- BLE/accessory migration;
- Baseline Profile generation/qualification once representative journeys exist;
- hard numerical performance thresholds only after device variance is understood;
- physical wide/tablet qualification on SM-T500 after real UI exists.
