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
- exact software-qualified implementation head before this handoff-only update: `37dab2133fd01dd59fdd8a487756577aca1d6528`
- qualification: **Foundation quality #169 / run 35970595763 — PASS**
- qualified artifact: `epichub-foundation-debug`, artifact ID `10795463807`

Always verify the current PR head when resuming. A documentation-only handoff commit may be newer than the qualified application tree.

---

# Locked methodology and runtime rules

D-022 / `SOURCE_REIMPLEMENTATION_METHOD.md` is mandatory for inherited functionality:

```text
understand one historical behavior slice deeply
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ define clean owner/contract
→ implement from scratch
→ qualify against protocol truth/proven behavior/tests/bench evidence
```

Never bulk-port an EpicDash-JZ subsystem or historical TunerStudio plugin and clean it later.

D-023 / `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`:

- production runtime is Kotlin/Jetpack Compose + Rust;
- no JavaScript/WebView/DOM production runtime architecture;
- no JSON/CBOR runtime message bus;
- Kotlin↔Rust uses semantic JNI operations and bounded/versioned packed buffers/results;
- serialization exists only at explicit persistence/export/interoperability boundaries;
- diagnostic capture export uses versioned `capture.cbor` + `report.txt`; CBOR is not a universal EpicHub format;
- HTML/CSS/light JavaScript are allowed for design prototypes only and are translated into Compose when accepted.

D-024 / `TUNING_TOOLS_FOUNDATION.md`:

- AE/PID/TCharge/Injection Angle/Phasing become native **Tuning Tools**;
- no dynamic plugin/JAR/JavaScript runtime is recreated;
- Compose owns guided workflow/presentation;
- Rust owns shared analysis/provenance/statistics/domain models;
- all ECU mutation remains behind the single semantic tuning authority.

D-025 / `UI_FOUNDATION_LAYOUT.md`:

- EpicHub exposes **four primary user workspaces**:

```text
Dashboard | Tuner | Logger | Diagnostics
```

- six distinct functional/domain leaf routes remain:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

- mapping:

```text
Tuner workspace  → Tuner | Tuning Tools
Logger workspace → Logger | Analyzer
```

- visual nesting is presentation-only;
- Analyzer must not depend on Logger UI/ViewModel/state;
- Tuning Tools must not depend on Tuner UI/ViewModel/state;
- sibling routes call their proper lower-level owners/contracts directly;
- the last meaningful Tuner/Logger leaf route is preserved when switching workspaces;
- diagnostics record the actual leaf route, not just the containing workspace.

---

# PR #3 implementation state

PR #3 contains the first production UI/diagnostics foundation.

## UI shell

Implemented:

- four primary workspace navigation items;
- distinct leaf routes for Tuner/Tuning Tools and Logger/Analyzer;
- nested route tabs inside the Tuner and Logger workspaces;
- last-leaf restoration per grouped workspace;
- navigation contract tests that assert grouping without ownership collapse;
- global Settings;
- synthetic RuntimeSnapshot remains active through the shell;
- Dashboard and Logger remain intentionally visually provisional.

The current simple width breakpoint is only a scaffold. Long-term accepted presentation authority may be supplied as four explicit prototype families:

```text
Tablet Landscape
Tablet Portrait
Phone Landscape
Phone Portrait
```

These are presentation recipes over one semantic/runtime architecture, not separate applications or duplicated domain logic.

The user is developing Dashboard and Logger HTML prototypes. Do **not** independently over-design those final Compose surfaces. When supplied and explicitly accepted:

```text
HTML/CSS prototype
→ visual/layout review
→ accepted presentation authority
→ native Compose translation
→ physical comparison
```

Prototype JSON/JavaScript is prototype machinery only and does not select EpicHub runtime/persistence mechanisms.

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
- capture completion survives Activity recreation because the recorder is process-scoped;
- immutable captures;
- `capture.cbor` + `report.txt` default ZIP archive;
- secure FileProvider share path;
- Diagnostics UI: Overview / Events / Metrics / Sessions / Captures;
- full diagnostics Compose projection updates continuously only while Diagnostics is visible; recording/native drain continue independently.

Global diagnostic capture is labelled **Problem capture** so it is not confused with Logger recording.

Diagnostics is evidence/observation, not connection or warning-policy authority:

```text
Diagnostics            → evidence, incidents, history, captures
Connection panel        → live connect/disconnect/reconnect actions routed to transport/session owner
Settings → Connections  → persistent connection/auto-reconnect policy
Diagnostics → Warnings  → active/history/acknowledgement
Settings → Warnings     → warning rule/threshold/debounce/cooldown configuration
```

Not yet claimed complete:

- previous-process/crash-context recovery;
- optional attachment selection/provenance/hashes;
- richer archive integrity policy;
- physical overhead/usability qualification.

---

# Current gate

The exact application head `37dab2133fd01dd59fdd8a487756577aca1d6528` passed Foundation quality #169.

Next action is **physical qualification of that exact APK** on:

- Samsung SM-A137F phone;
- Samsung SM-T500 tablet.

Check at minimum:

1. install/launch;
2. only four primary workspace choices are shown;
3. Tuner ↔ Tuning Tools leaf routing works and is restored after leaving/returning;
4. Logger ↔ Analyzer leaf routing works and is restored after leaving/returning;
5. phone/tablet shell has no clipping or unusable targets;
6. synthetic RuntimeSnapshot continues advancing;
7. Diagnostics Overview / Events / Metrics / Sessions / Captures populate;
8. Problem capture → reproduction → marker → stop;
9. recent-problem capture auto-finishes after its post-trigger window;
10. export/share produces a diagnostic ZIP;
11. rotate/recreate Activity during capture and verify evidence/capture survives while NativeEngine is recreated cleanly;
12. no crash, duplicate active native engine, or obvious responsiveness regression.

If physical issues appear, use the diagnostics system itself to capture them and correct PR #3 before acceptance.

After both devices pass, decide PR #3 acceptance/merge. Real ECU/USB integration remains after this foundation unless the accepted HTML UI references are intentionally integrated first.

---

# Deferred work

Not part of the current PR #3 acceptance gate:

- real ECU USB transport/session;
- ECU writes/Burn;
- production logging formats;
- deep Analyzer implementation;
- AE/PID/TCharge/Injection-Angle algorithms;
- BLE/accessory migration;
- final Dashboard/Logger visual implementation before the user's prototypes are accepted.
