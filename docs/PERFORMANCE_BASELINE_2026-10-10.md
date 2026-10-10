# EpicHub accepted physical performance baseline — 2026-10-10

## Status

This document closes the current SM-A137F performance-foundation investigation. The result is an accepted **report-only baseline**, not a hard product-performance threshold.

Development should now return to real EpicHub UI and functional implementation. Revisit performance when real Dashboard/Tuner/Logger/Analyzer behavior exists, or if a clear regression appears.

## Qualified build

- Branch: `phase2/performance-foundation`
- Application/benchmark source head used for the final physical round: `a20fd4b52a33a2646381c9fd15d881f1e91d1ea8`
- CI: Foundation quality #330 — PASS
- App APK SHA-256: `7e8d660b83ea8ed8be1fa50a2fba87064ee8912e9aa4ffd9d8c247d44b29c89d`
- Benchmark APK SHA-256: `aadfbdde3fea65efe602ece75925a788ebd14a153cec121543e82a64b63303a1`

## Device context

- Device: Samsung SM-A137F
- Android API: 34
- Build fingerprint: `samsung/a13venseea/a13ve:14/UP1A.231005.007/A137FXXU5EXE3:user/release-keys`
- App ABI selected by Android: `armeabi-v7a`
- Device app ABI list: `armeabi-v7a,armeabi`
- Display: 1080×2408, 450 dpi, approximately 384×856 dp
- Orientation: portrait
- Refresh rate: 60 Hz
- Layout branch exercised: compact/phone (`maxWidth < 840.dp`)
- Macrobenchmark CPU lock: false
- Sustained-performance mode: false

These measurements do **not** qualify the wide/tablet shell. The SM-T500 landscape path should be measured later against real UI because it exercises a different responsive branch.

## Final authoritative leaf-route results

The coordinate-driven methods are authoritative for this phase because route-tab centers are resolved during setup and the measured block uses only direct coordinate clicks plus the fixed settle delay. No repeated accessibility-tree `findObjects()` or per-click `pidof` runs inside the measured loop.

### Tuner ↔ Tuning Tools — real content

Method: `warmTunerLeafNavigationCoordinates`

- frame count runs: 30, 30, 30, 30, 29
- frame count median: 30
- CPU frame P50: 22.58 ms
- CPU frame P90: 42.08 ms
- CPU frame P95: 43.31 ms
- CPU frame P99: 46.25 ms
- overrun P50: 21.64 ms
- overrun P95: 29.68 ms
- overrun P99: 33.02 ms
- 81.2% of sampled frames >16.67 ms CPU
- 41.6% >33.33 ms
- 0% >50 ms
- max sampled CPU frame: 48.74 ms

### Tuner ↔ Tuning Tools — trivial placeholder content

Method: `warmTunerLeafNavigationPlaceholderCoordinates`

- frame count: 14 in every iteration
- CPU frame P50: 21.94 ms
- CPU frame P90: 24.17 ms
- CPU frame P95: 24.88 ms
- CPU frame P99: 26.30 ms
- overrun P50: 7.38 ms
- overrun P95: 10.67 ms
- overrun P99: 12.07 ms
- 81.4% of sampled frames >16.67 ms CPU
- 0% >33.33 ms
- max sampled CPU frame: 26.57 ms

### Logger ↔ Analyzer — real content

Method: `warmLoggerLeafNavigationCoordinates`

- frame count runs: 29, 30, 29, 28, 30
- frame count median: 29
- CPU frame P50: 23.40 ms
- CPU frame P90: 42.97 ms
- CPU frame P95: 44.80 ms
- CPU frame P99: 46.59 ms
- overrun P50: 22.20 ms
- overrun P95: 32.97 ms
- overrun P99: 34.52 ms
- 74.7% of sampled frames >16.67 ms CPU
- 44.5% >33.33 ms
- 0% >50 ms
- max sampled CPU frame: 46.86 ms

### Logger ↔ Analyzer — trivial placeholder content

Method: `warmLoggerLeafNavigationPlaceholderCoordinates`

- frame count: 14 in every iteration
- CPU frame P50: 22.92 ms
- CPU frame P90: 25.50 ms
- CPU frame P95: 26.20 ms
- CPU frame P99: 27.47 ms
- overrun P50: 8.46 ms
- overrun P95: 12.07 ms
- overrun P99: 14.21 ms
- 91.4% of sampled frames >16.67 ms CPU
- 0% >33.33 ms
- max sampled CPU frame: 28.49 ms

## What the final experiment proves

1. The earlier selector-driven harness was not the primary cause of the real-content penalty. Removing accessibility-tree lookup from the measured block did **not** collapse the real-vs-placeholder difference.
2. Real Tuner/Tools and Logger/Analyzer content consistently produces roughly 29–30 captured frames per iteration versus exactly 14 for trivial placeholders.
3. Real content also retains a distinct high-cost frame population around the low-to-mid 40 ms range; placeholder content stays below 30 ms in this final round.
4. Therefore the current extra work is genuinely in the app/rendering path, not mainly UI Automator selector traversal.
5. The placeholder path still has a roughly 22–23 ms median frame on this low-end, unlocked-clock A13, so there is also shared shell/navigation/rendering cost. That is worth revisiting later, but not before real product UI exists.
6. No final coordinate run showed an EpicHub crash, `StaleObjectException`, deactivated-node error, process death, or app GC event aligned with the measured leaf transitions.
7. The original `ReusableContentHost` retained-route strategy remains rejected. It caused deterministic physical crashes and must not be reintroduced as a performance optimization.

## Interpretation policy

Do not optimize the present provisional cards/screens merely to reduce these numbers. Their content is intentionally temporary and will be replaced by real product UI.

Use this baseline to detect future regressions and to guide profiling once real functionality exists. The next meaningful performance work should target production scenarios such as:

- Tuner table/curve editing;
- Logger live-data recording and scrolling;
- Analyzer plotting/pan/zoom/query;
- Dashboard live gauges;
- SM-T500 landscape/wide-shell behavior;
- real ECU/session updates.

Small same-device differences should not be treated as architectural facts because CPU clocks are not locked on this device. Prefer large repeated A/B differences, distributions, frame counts, and real user-facing behavior.

## Phase closure

Performance-foundation work is considered sufficiently mature to stop blocking product development.

Next priority: proper EpicHub UI and functional implementation, while keeping the Macrobenchmark/Perfetto infrastructure as a regression and attribution tool.