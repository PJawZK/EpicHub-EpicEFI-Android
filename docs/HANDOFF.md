# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual `main` and PR #3 heads, then continue from the current physical-qualification gate.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or historical plugin repositories when EpicHub documentation defines a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- Phase 1: accepted/closed; PR #1 merged
- first Phase-2 Kotlin/Rust foundation: PR #2 merged
- active branch: `phase2/ui-diagnostics-foundation`
- active draft PR: **#3 — Phase 2: establish UI and diagnostics foundations**
- latest fully software-qualified implementation commit before documentation-only reconciliation: `be8b3fe09c1fda1bf59d0916518e61343fe0e43e`
- qualification: **Foundation quality #153 / run 35905023246 — PASS**
- qualified artifact: `epichub-foundation-debug`, artifact ID `10770833239`

Always verify the current PR head when resuming. Later documentation-only commits may move the branch without changing the qualified application code.

---

# Locked methodology and language rules

D-022 / `SOURCE_REIMPLEMENTATION_METHOD.md` is mandatory for inherited functionality:

```text
understand one historical behavior slice deeply
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ define clean owner/contract
→ implement from scratch
→ qualify against protocol truth/proven behavior/tests/bench evidence
```

Never bulk-port an EpicDash-JZ subsystem or a TunerStudio plugin and clean it later.

D-023 / `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`:

- production runtime is Kotlin/Jetpack Compose + Rust;
- no JavaScript/WebView/DOM runtime architecture;
- no JSON/CBOR runtime message bus;
- Kotlin↔Rust uses semantic JNI operations and bounded/versioned packed buffers/results;
- serialization exists only at explicit persistence/export/interoperability boundaries;
- diagnostic capture export uses versioned `capture.cbor` + `report.txt`; CBOR is not a universal EpicHub format;
- HTML/CSS/light JavaScript are allowed for design prototypes only and are translated into Compose when accepted.

D-024 / `TUNING_TOOLS_FOUNDATION.md`:

- AE/PID/TCharge/Injection Angle become native **Tuning Tools**;
- no dynamic plugin/JAR/JavaScript runtime is recreated;
- Compose owns guided workflow/presentation;
- Rust owns shared analysis/provenance/statistics/domain models;
- all ECU mutation remains behind the single semantic tuning authority.

---

# PR #3 implementation state

PR #3 is no longer documentation-only. It now contains the first production UI/diagnostics foundation.

## UI shell

Implemented Compose destinations:

```text
Dashboard | Tuner | Tuning Tools | Logger | Analyzer | Diagnostics
```

Settings is global.

Implemented shell behavior:

- wide/tablet navigation rail;
- compact/phone bottom navigation;
- compact-safe two-row status/capture header;
- shared synthetic RuntimeSnapshot remains active through the shell;
- real structural screens exist for all destinations;
- Dashboard and Logger remain intentionally visually provisional.

The user is developing Dashboard and Logger HTML prototypes. Do **not** independently over-design those two Compose screens. When supplied and explicitly accepted:

```text
HTML/CSS prototype
→ visual/layout review
→ accepted authority
→ Compose translation
→ phone/tablet comparison
```

## Diagnostics

Implemented:

- process-scoped bounded `DiagnosticHub`;
- bounded Kotlin event ring with explicit dropped-event accounting;
- bounded Rust engine diagnostic ring;
- versioned fixed 656-byte Rust→Kotlin diagnostic batch ABI v1;
- one coarse JNI diagnostic drain; no per-event callback bridge;
- typed event codes/payloads;
- bounded metric summaries including percentiles;
- Activity and NativeEngine generation/lifecycle tracing;
- bounded retained destroyed-owner history;
- invariant/late-owner evidence;
- recent-problem capture with pre-trigger history and post-trigger completion;
- reproduction capture with markers;
- recent capture completion survives Activity recreation because the recorder is process scoped;
- immutable captures;
- `capture.cbor` + `report.txt` default ZIP archive;
- secure FileProvider share path;
- Diagnostics UI: Overview / Events / Metrics / Sessions / Captures;
- full diagnostics Compose projection updates continuously only while Diagnostics is visible; recording/native drain continue independently.

Automated coverage includes Rust diagnostic layout/drain/overflow, Kotlin decoder validation, Kotlin ring/drop accounting, percentile vectors, lifecycle retention, capture semantics/deadline behavior, CBOR header/default archive contents, existing RuntimeSnapshot regression tests, dual-ABI build and APK inspection.

Not yet claimed complete:

- previous-process/crash-context recovery;
- optional attachment selection/provenance/hashes;
- richer archive integrity policy;
- physical overhead/usability qualification.

---

# Current gate

The current next action is **physical qualification of PR #3 on the same exact APK on both reference devices**:

- Samsung SM-A137F phone;
- Samsung SM-T500 tablet.

Check at minimum:

1. install/launch;
2. all six destinations reachable;
3. phone compact header/navigation has no clipping or unusable targets;
4. tablet rail/reflow behaves sensibly;
5. synthetic RuntimeSnapshot continues advancing;
6. Diagnostics Overview / Events / Metrics / Sessions / Captures populate;
7. reproduction capture → marker → stop;
8. recent-problem capture auto-finishes after its post-trigger window;
9. export/share produces a diagnostic ZIP;
10. rotate/recreate Activity during capture and verify evidence/capture survives while the NativeEngine is recreated cleanly;
11. no crash, duplicate active native engine, or obvious responsiveness regression.

If physical issues appear, use the diagnostics system itself to capture them and correct PR #3 before acceptance.

After both devices pass, review/accept and merge PR #3. Only then begin the D-022 source-understanding gate for read-only live ECU acquisition, unless the user chooses to integrate the accepted Dashboard/Logger prototypes first.

---

# Deferred work

Not part of the current PR #3 acceptance gate:

- real ECU USB transport/session;
- ECU writes/Burn;
- production logging formats;
- deep Analyzer implementation;
- AE/PID/TCharge/Injection-Angle algorithms;
- BLE/accessory migration;
- final Dashboard/Logger visual implementation before the user’s prototypes are accepted.
