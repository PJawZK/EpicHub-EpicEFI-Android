# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual `main` and PR #3 heads, then continue from the current physical-qualification / accepted-prototype gate.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or historical plugin repositories when EpicHub documentation defines a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- Phase 1: accepted/closed; PR #1 merged
- first Phase-2 Kotlin/Rust foundation: PR #2 merged
- active branch: `phase2/ui-diagnostics-foundation`
- active draft PR: **#3 — Phase 2: establish UI and diagnostics foundations**
- exact software-qualified application-changing head: `37dab2133fd01dd59fdd8a487756577aca1d6528`
- qualification: **Foundation quality #169 / run 35970595763 — PASS**
- qualified artifact: `epichub-foundation-debug`, artifact ID `10795463807`
- qualified APK SHA-256: `bb7ed1b5ec96982f627fcea2105f31997f5e260366908434928d6bb38e28f9b1`
- later handoff/documentation-only PR head at the time of this handoff: `63b6396ae37d41c9a4ecb570b827e0e476bab62b`
- Foundation quality #170 / run 35971964845 for that documentation-only head was still in progress when this handoff was written.

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

The navigation grouping must never become dependency nesting. Specifically, do not introduce:

```text
Analyzer → Logger UI/ViewModel/state → analysis/runtime
Tuning Tools → Tuner UI/ViewModel/state → analysis/runtime
```

Instead retain parallel ownership:

```text
Logger UI       → logging / measurement contracts
Analyzer UI     → analysis / measurement contracts
Tuner UI        → semantic tuning authority
Tuning Tools UI → shared analysis/tool contracts
                   └─ ExperimentProposal → semantic tuning authority
```

---

# PR #3 implementation state

PR #3 contains the first production UI/diagnostics foundation.

## Four-workspace UI shell

Implemented:

```text
Dashboard
Tuner
├─ Tuner
└─ Tuning Tools

Logger
├─ Logger
└─ Analyzer

Diagnostics
```

- four primary workspace buttons;
- six distinct leaf/domain routes underneath;
- nested route tabs inside the Tuner and Logger workspaces;
- last-leaf restoration per grouped workspace;
- actual leaf route retained for diagnostics;
- navigation contract tests that assert grouping without ownership collapse;
- global Settings;
- synthetic RuntimeSnapshot remains active through the shell;
- Dashboard and Logger remain intentionally visually provisional.

The current simple responsive scaffold is temporary. Long-term accepted presentation authority may be supplied as four explicit prototype families:

```text
Tablet Landscape
Tablet Portrait
Phone Landscape
Phone Portrait
```

These are four presentation recipes over one semantic/runtime architecture, not four applications or duplicated domain logic.

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

Meaning of Problem capture:

```text
something strange happens anywhere in EpicHub
→ user taps Problem capture without leaving that workspace
→ bounded diagnostic flight recorder preserves recent pre-trigger evidence
→ a short post-trigger window is retained
→ immutable diagnostic capture is finalized
→ capture can later be reviewed/exported
```

Reproduction mode is:

```text
Start reproduction
→ perform the failing steps
→ add marker(s) if useful
→ stop
→ review/export
```

This is diagnostic evidence capture, not ECU/logger data recording.

Diagnostics is evidence/observation, not connection or warning-policy authority:

```text
Diagnostics            → evidence, incidents, history, captures
Connection panel        → live connect/disconnect/reconnect actions routed to transport/session owner
Settings → Connections  → persistent connection/auto-reconnect policy
Diagnostics → Warnings  → active/history/acknowledgement
Settings → Warnings     → warning rule/threshold/debounce/cooldown configuration
```

Manual connection controls may be visible near Diagnostics or global connection state, but they must remain semantic actions routed to the real transport/session owner. Diagnostics itself must not become the connection owner.

Not yet claimed complete:

- previous-process/crash-context recovery;
- optional attachment selection/provenance/hashes;
- richer archive integrity policy;
- physical overhead/usability qualification.

---

# Tablet Landscape HTML prototype review

The current reviewed external prototype is:

`EpicHub-1.12.1_TabletLandscape.html`

It was supplied in chat as a design prototype, not added to the repository. If a future chat needs to inspect the exact file contents again, the user may need to re-upload it unless it has since been committed separately.

The prototype is explicitly allowed to use HTML/CSS/light JavaScript and prototype JSON because those are design/prototyping mechanisms only. None of that selects the production runtime, JNI, persistence or state architecture.

## Overall assessment

The prototype is a credible **Tablet Landscape visual/layout authority candidate**. Its Dashboard, Tuner, Tuning Tools internals, Logger/Analyzer workspace and Trigger Logger are substantially aligned with EpicHub's architectural contracts.

The important conclusion is that the HTML's simpler navigation hierarchy is now deliberately reflected in D-025 and production navigation: four primary workspaces, six distinct functional owners/routes underneath.

## Tuner and Tuning Tools

Strong alignment:

- Tuner remains manual/profile-driven semantic calibration editing;
- Tuning Tools remain guided domain workflows;
- AE/PID/TCharge/Injection Phasing workflows are modeled as evidence/analysis tools rather than independent plugin runtimes;
- temporary experiment proposals hand off to the semantic Tuning authority;
- tool logic does not own Burn;
- baseline/requested/effective/restore/keep-in-RAM concepts fit the tuning safety model.

Do not convert visual nesting into a Tuning-Tools-through-Tuner-UI dependency.

## Logger and Analyzer

Strong alignment:

- Trigger Logger has distinct timing-lane responsibility;
- regular Logger workspace contains graph/timeline/recording functions;
- Analyzer-style graph/scatter/histogram/table/math-channel tools fit naturally inside the Logger primary workspace from the user's perspective;
- the underlying Logger and Analyzer owners must remain parallel and share lower-level measurement/channel contracts rather than pass through each other.

Do not create a giant Logger workspace owner/ViewModel that absorbs Analyzer domain ownership merely because the UI groups them.

## Dashboard

Strong alignment:

- Drive/Edit distinction;
- screen lock;
- gauge selection/configuration/duplicate/lock/fullscreen;
- right-side detail rail;
- user pages;
- ESP32 bridge I/O widgets as contextual Dashboard widgets rather than a separate application domain.

Production ESP32 actions must still route through semantic bridge/action authority rather than embedding BLE UUID/GPIO details in widget UI code.

## Diagnostics prototype corrections before it becomes authority

The HTML currently mixes some evidence UI with operational/configuration controls. Keep the visual ideas, but separate the responsibilities meaningfully:

```text
Diagnostics
→ evidence, metrics, events, lifecycle/session history, captures, active warning incidents

Connection panel
→ scan/connect/disconnect/reconnect semantic actions

Settings → Connections
→ persistent connection and auto-reconnect policy

Diagnostics → Warnings
→ current/history/acknowledgement

Settings → Warnings & Alerts
→ threshold/rule/debounce/cooldown/enable policy
```

Also add or represent the global **Problem capture** concept in the HTML authority so the accepted reference does not omit a capability already present in the production shell.

## Fixture/demo truth

Any prototype/demo display that says things such as:

- exact match;
- verified;
- authorized;
- current;
- live;
- connected;

must make it unmistakable when the value is fixture/demo/synthetic truth rather than real ECU evidence.

Do not let a polished prototype teach future implementation that simulated authority states may be presented as real authority states.

## Presentation authority model

Once accepted, prefer four explicit reference files or equivalent reference artifacts:

```text
Tablet Landscape
Tablet Portrait
Phone Landscape
Phone Portrait
```

Do not treat one HTML file as a universal responsive production specification. Each accepted prototype is a presentation recipe. Production Compose still uses one shared semantic/runtime model and shared component responsibilities underneath.

Cross-orientation media-query fallbacks inside a prototype are acceptable during iteration, but should be ignored or removed when a file is frozen as one specific presentation authority.

## Touch ergonomics

The visual density of the tablet prototype is desirable. Do not mechanically inflate it into generic Material-sized UI. Preserve compact visual controls where appropriate, but separate visual geometry from touch hit-target geometry when necessary for in-car use.

---

# Current gate

The exact application-changing head `37dab2133fd01dd59fdd8a487756577aca1d6528` passed Foundation quality #169.

For physical testing, use the exact #169 qualified APK with SHA-256:

`bb7ed1b5ec96982f627fcea2105f31997f5e260366908434928d6bb38e28f9b1`

Check at minimum on SM-A137F and SM-T500:

1. only four primary workspace choices are shown;
2. Tuner ↔ Tuning Tools local navigation works;
3. leaving and returning to Tuner restores the prior Tuner/Tuning Tools leaf;
4. Logger ↔ Analyzer local navigation works;
5. leaving and returning to Logger restores the prior Logger/Analyzer leaf;
6. actual leaf route remains visible in diagnostics evidence;
7. phone/tablet shell has no clipping or unusable targets;
8. synthetic RuntimeSnapshot continues advancing;
9. Diagnostics Overview / Events / Metrics / Sessions / Captures populate;
10. Problem capture / reproduction / marker / stop work;
11. recent-problem capture auto-finishes after its post-trigger window;
12. export/share produces a diagnostic ZIP;
13. rotate/recreate Activity during capture and verify evidence/capture survives while NativeEngine is recreated cleanly;
14. no crash, duplicate active native engine, or obvious responsiveness regression.

If physical issues appear, use the diagnostics system itself to capture them and correct PR #3 before acceptance.

After both devices pass, decide PR #3 acceptance/merge. Accepted HTML presentation authorities may intentionally be integrated before real ECU/USB work if they are mature enough.

---

# Immediate next design work

The current HTML review produced these accepted directions:

1. **Four-workspace navigation is the intended route.** It is safe because grouping remains presentation-only and does not create extra runtime/JNI barriers.
2. **Problem capture** is the global diagnostic flight-recorder action described above; it should be represented in the prototype without being confused with Logger recording.
3. **Diagnostics, live connection actions, persistent connection settings and warning-rule settings must be visibly separated by responsibility.**
4. Demo/fixture authority states must be unmistakably labeled as demo/synthetic.
5. Prototype JSON/JavaScript remains prototype machinery only.
6. Tablet Landscape / Tablet Portrait / Phone Landscape / Phone Portrait should become explicit presentation authorities rather than one universal responsive reference.

When the user supplies newer Dashboard/Logger HTML revisions, compare them against these contracts before translating to Compose.

---

# Deferred work

Not part of the current PR #3 acceptance gate unless deliberately brought forward after prototype acceptance:

- real ECU USB transport/session;
- ECU writes/Burn;
- production logging formats;
- deep Analyzer implementation;
- AE/PID/TCharge/Injection-Angle algorithms;
- BLE/accessory migration;
- final Dashboard/Logger Compose visuals before the user's prototypes are accepted.
