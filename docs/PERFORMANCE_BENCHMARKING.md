# EpicHub deterministic performance benchmarking

## Purpose

EpicHub performance work must distinguish between:

- steady-state app cost;
- workspace/leaf-route transition cost;
- Diagnostics-tab transition cost;
- cold first-use/startup cost;
- benchmark/profiler overhead.

The `:benchmark` module is a separate `com.android.test` Macrobenchmark module. It drives the real non-debuggable `performance` APK from a separate instrumentation process using UI Automator. No benchmark loop executes inside EpicHub.

The performance target remains the existing R8/resource-shrunk `performance` build. That build is profileable by shell so Android Macrobenchmark/Perfetto can collect release-like traces without enabling app debugging.

## Current benchmark scenarios

`EpicHubNavigationBenchmark.warmWorkspaceNavigation`

- launches EpicHub;
- warms all workspaces and leaf routes outside the measured block;
- returns to Dashboard;
- repeatedly switches Dashboard/Tuner/Tuning Tools/Logger/Analyzer/Diagnostics;
- records `FrameTimingMetric` with full compilation to minimize JIT noise.

This is the primary signal for destination composition/reuse and shell-navigation work.

`EpicHubNavigationBenchmark.warmDiagnosticsTabs`

- warms the application and all Diagnostics tabs outside the measured block;
- measures repeated switching through Overview, Performance, Events, Metrics, Sessions and Captures;
- records `FrameTimingMetric` independently from primary workspace navigation.

This isolates Diagnostics presentation cost from shell navigation.

`EpicHubNavigationBenchmark.coldFirstVisitJourney`

- starts from a cold app process;
- uses `CompilationMode.None()`;
- measures startup plus first visits through Tuner, Tuning Tools, Logger, Analyzer, Diagnostics and Performance;
- records both `StartupTimingMetric` and `FrameTimingMetric`.

This intentionally represents a pessimistic fresh-install / first-use path and must not be compared directly with the fully compiled warm-navigation scenarios.

## Running on the physical qualification device

Connect exactly one physical Android device through ADB and run from PowerShell:

```powershell
./scripts/run-performance-benchmark.ps1
```

Run one benchmark method only:

```powershell
./scripts/run-performance-benchmark.ps1 -Method warmWorkspaceNavigation
./scripts/run-performance-benchmark.ps1 -Method warmDiagnosticsTabs
./scripts/run-performance-benchmark.ps1 -Method coldFirstVisitJourney
```

The script refuses emulator execution by default. `-AllowEmulator` exists only for debugging the harness; emulator numbers are not valid performance qualification evidence.

Android Gradle/Macrobenchmark pulls benchmark JSON and Perfetto trace artifacts into the benchmark module build outputs. The script prints the newest result files after a successful run.

## Qualification policy

Cloud CI compiles/packages the benchmark harness but does not use emulator performance numbers as acceptance evidence.

Physical-device performance results are initially **report-only**. Do not fail CI on a numerical threshold until enough repeated SM-A137F runs establish normal variance.

When comparing implementations:

1. Compare the same benchmark method, device, orientation and build type.
2. Prefer median/P95/P99 frame timing/overrun over one worst outlier.
3. Keep warm and cold scenarios separate.
4. If only one scenario regresses, optimize that subsystem rather than changing unrelated UI/runtime code.
5. A performance improvement is not accepted merely because an intended UI feature was removed. Feature-removal builds may be used as diagnostic probes to estimate feature cost.

## Measurement overhead

Macrobenchmark driving occurs outside EpicHub. The target app still pays normal Android profiling/trace collection overhead while a trace is recorded, so absolute values can move slightly compared with an untraced manual run.

Use the deterministic benchmark for A/B comparison. Continue using EpicHub's own lightweight capture metrics as an independent in-app view of CPU, allocation, GC, JNI and transition attribution.

For deeper investigation, inspect the Perfetto trace from the failing scenario. Add custom EpicHub trace sections only when the system trace identifies a specific ambiguous hot path; do not permanently add broad per-frame tracing just to obtain more labels.

## Later additions

Once deterministic results are stable:

- establish device-specific variance bands;
- add Logger scrolling/live-data scenarios;
- add Analyzer pan/zoom/query scenarios;
- add Tuner table/curve edit scenarios;
- add Dashboard live-gauge scenarios;
- compare `CompilationMode.None`, partial/Baseline Profile and full compilation;
- generate and validate a Baseline Profile from representative EpicHub journeys;
- add report generation that compares a candidate run with the accepted benchmark baseline.
