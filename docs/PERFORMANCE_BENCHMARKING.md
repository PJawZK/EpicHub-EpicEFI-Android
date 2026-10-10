# EpicHub deterministic performance benchmarking

## Purpose

EpicHub performance work must distinguish between:

- steady-state app cost;
- primary-workspace transition cost;
- Tuner and Logger leaf-route transition cost;
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

This remains the combined end-to-end navigation regression signal. It deliberately mixes primary-workspace and leaf-route changes and must not be used alone to choose an optimization target.

`EpicHubNavigationBenchmark.warmPrimaryWorkspaceNavigation`

- warms all destinations outside the measured block;
- starts from Dashboard;
- repeatedly switches only Dashboard/Tuner/Logger/Diagnostics;
- records `FrameTimingMetric` with full compilation.

This isolates primary shell/content replacement from Tuner and Logger leaf-route changes.

### Leaf-route performance authority

The authoritative leaf-route performance methods are:

- `warmTunerLeafNavigationCoordinates`
- `warmTunerLeafNavigationPlaceholderCoordinates`
- `warmLoggerLeafNavigationCoordinates`
- `warmLoggerLeafNavigationPlaceholderCoordinates`

For each iteration these methods:

- warm destinations and enter the desired workspace outside the measured block;
- resolve the centers of both route tabs from the accessibility tree during setup;
- verify the EpicHub process is alive before measurement begins;
- enter the measured block with only direct `UiDevice.click(x, y)` injections and the fixed settle delay;
- do not call `findObjects()` or `pidof` between measured transitions.

This avoids a measured accessibility-tree traversal whose cost changes with destination-content complexity. These coordinate-driven methods are therefore the performance authority for the current leaf-navigation investigation.

The older selector-driven methods remain available:

- `warmTunerLeafNavigation`
- `warmTunerLeafNavigationPlaceholder`
- `warmLoggerLeafNavigation`
- `warmLoggerLeafNavigationPlaceholder`

They continue to exercise stable accessibility selectors and crash/harness robustness, but they are **not** authoritative for leaf rendering performance because each measured click searches the Compose semantics tree before injecting the tap.

Placeholder variants run the same destination state, shell, route tabs and navigation-control path as their corresponding real leaf benchmark, while a benchmark-only intent extra substitutes only the leaf content body with a trivial placeholder. They are diagnostic A/B probes, not candidate product behavior. Compare each placeholder method only with its matching real-content coordinate method on the same device/build. Do not accept placeholder rendering itself as a performance optimization.

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

## Trace attribution

The performance build currently adds narrow Android trace sections around:

- `EpicHub.selectDestination:<destination>` for synchronous destination-state/diagnostics mutation;
- `EpicHub.ShellContent:<destination>` for the route-content composable invocation.

`EpicHub.ShellContent:*` marks composable invocation only. It does not by itself measure all subsequent Compose/Android measurement, placement, drawing or GPU submission. Use it only as a correlation marker inside Perfetto.

These markers exist to correlate app-owned work with Perfetto during the current navigation investigation. They are intentionally narrow; broad permanent per-frame tracing is still avoided.

## Running on the physical qualification device

The qualified physical benchmark kit contains the exact CI-built EpicHub performance APK, Macrobenchmark APK and both Windows PowerShell and Linux Bash runners. The Macrobenchmark APK is an instrumentation/test APK; do not attempt to install it by tapping it in a file manager. The runner installs it with ADB's test-package flag.

Connect exactly one physical Android device through ADB, keep it awake and unlocked, and do not interact with it while the benchmark runs.

The physical runner records the APK hashes plus comparison-critical device context in `benchmark-run-manifest.txt`, including:

- device model, API level and build fingerprint;
- device ABI list and the ABI Android selected for EpicHub;
- display size, density, approximate dp dimensions and orientation;
- refresh rate when Android exposes it through `dumpsys display`;
- whether the runner requested CPU locking or sustained-performance mode;
- the Macrobenchmark-reported CPU-lock and sustained-performance state when available in benchmark JSON.

This metadata is required because phone/compact and tablet/wide layouts are different code paths. In particular, results from a compact portrait device must not be treated as qualification of the wide tablet shell.

### Linux / Zorin OS

Install the small host-side requirements:

```bash
sudo apt install adb zip
```

From the extracted benchmark kit:

```bash
chmod +x run-performance-benchmark.sh
./run-performance-benchmark.sh
```

For the current final leaf-navigation qualification, run only:

```bash
./run-performance-benchmark.sh --method warmTunerLeafNavigationCoordinates
./run-performance-benchmark.sh --method warmTunerLeafNavigationPlaceholderCoordinates
./run-performance-benchmark.sh --method warmLoggerLeafNavigationCoordinates
./run-performance-benchmark.sh --method warmLoggerLeafNavigationPlaceholderCoordinates
```

Other available methods include:

```bash
./run-performance-benchmark.sh --method warmPrimaryWorkspaceNavigation
./run-performance-benchmark.sh --method warmWorkspaceNavigation
./run-performance-benchmark.sh --method warmDiagnosticsTabs
./run-performance-benchmark.sh --method coldFirstVisitJourney
```

### Windows PowerShell

From the extracted benchmark kit:

```powershell
./run-performance-benchmark.ps1
```

For the current final leaf-navigation qualification:

```powershell
./run-performance-benchmark.ps1 -Method warmTunerLeafNavigationCoordinates
./run-performance-benchmark.ps1 -Method warmTunerLeafNavigationPlaceholderCoordinates
./run-performance-benchmark.ps1 -Method warmLoggerLeafNavigationCoordinates
./run-performance-benchmark.ps1 -Method warmLoggerLeafNavigationPlaceholderCoordinates
```

Both prebuilt runners install/reinstall the exact qualified APK pair, invoke instrumentation directly, pull benchmark JSON and Perfetto trace evidence, record device/build/APK hashes, preserve post-run diagnostic state and create one `EpicHub-Benchmark-<timestamp>.zip` for analysis.

The runners refuse emulator execution by default. Emulator numbers are useful only for harness debugging, not performance qualification evidence.

When running from a repository checkout instead of a prebuilt CI kit, the PowerShell runner can also use Gradle `connectedPerformanceAndroidTest`; the qualified physical-kit path is preferred for reproducible device comparison because it avoids rebuilding the target locally.

## Qualification policy

Cloud CI compiles/packages the benchmark harness but does not use emulator performance numbers as acceptance evidence.

Physical-device performance results are initially **report-only**. Do not fail CI on a numerical threshold until enough repeated SM-A137F runs establish normal variance.

When comparing implementations:

1. Compare the same benchmark method, device, orientation, effective layout width/branch, selected app ABI and build type.
2. Prefer median/P95/P99 frame timing/overrun over one worst outlier, but also inspect frame counts and per-iteration distributions when the compared runs produce different numbers of rendered frames.
3. Keep warm and cold scenarios separate.
4. Use the isolated primary-workspace and coordinate-driven leaf-route benchmarks to choose the subsystem to optimize; keep the combined workspace journey as an end-to-end regression check.
5. Use selector-driven leaf methods for harness/accessibility regression, not as performance authority.
6. Compare placeholder A/B methods only against their corresponding real-content coordinate method; use them for attribution, never as an accepted feature-removal optimization.
7. If only one scenario regresses, optimize that subsystem rather than changing unrelated UI/runtime code.
8. A performance improvement is not accepted merely because an intended UI feature was removed. Feature-removal builds may be used as diagnostic probes to estimate feature cost.
9. Physical phone/compact qualification does not replace tablet-landscape qualification when the production layout takes a different responsive branch.
10. On devices where clocks cannot be locked, treat small differences as noise and use repeated, large same-device A/B effects rather than hard absolute acceptance thresholds.

## Measurement overhead

Macrobenchmark driving occurs outside EpicHub. The target app still pays normal Android profiling/trace collection overhead while a trace is recorded, so absolute values can move slightly compared with an untraced manual run.

Accessibility-driven UI Automator work can also perturb Compose when selectors are searched repeatedly against a rich semantics tree. This is why the coordinate-driven leaf variants resolve semantics only in setup and avoid selector walks inside the measured block.

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
