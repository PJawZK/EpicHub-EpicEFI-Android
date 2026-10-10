# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual branch / PR / CI / Pages state, then continue from the current **Pages product-prototype + native Android performance/runtime** workflow.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Active development branch: `phase2/ui-diagnostics-foundation`

Active draft PR: **#3 — Phase 2: establish UI, diagnostics and product-prototype foundations**

Do not reconstruct current architecture from EpicDash-JZ, historical plugins, old standalone HTML files or old chat assumptions when the repository contracts define a newer deliberate design.

The user's latest explicit product direction is:

> **Focus on the base first.** Use the good EpicScope top-bar / structural layout as a reference together with the accepted EpicHub work, and refine the shared EpicHub shell before expanding feature-specific UI depth.

---

# Repository state at handoff

Immediately before this handoff update:

- repository visibility: **public**;
- integration branch: `main`;
- active branch: `phase2/ui-diagnostics-foundation`;
- active draft PR: **#3**;
- branch head: `3d8d8e1c4ff4e3310981d1bc1a03b8e1a3a79915` — `Index dashboard model and TS import contract`;
- previous commits of current product-prototype slice:
  - `8aab59fd9e3ab37f0cb4c4dc2c0b1112cc9602e3` — EpicScope-inspired EpicHub base shell;
  - `58561dc257e137b680a9b94c713145552b1e2d97` — shell/menu typing fix;
  - `e40cc689a574db1be1058f0b7af26b6e80b46fa4` — declarative Dashboard model and renderer;
  - `3d8d8e1c4ff4e3310981d1bc1a03b8e1a3a79915` — architecture index update for Dashboard / TS import contract;
- Prototype quality #18 for `3d8d8e...`: **PASS**;
- Foundation quality #293 for `3d8d8e...`: was still **in progress** at the time this handoff was prepared; verify the final conclusion before relying on it;
- GitHub Pages deployment is configured and working;
- live prototype URL: `https://pjawzk.github.io/EpicHub-EpicEFI-Android/`;
- Pages now auto-deploys on relevant prototype changes from both `phase2/ui-diagnostics-foundation` and `main`, with manual dispatch retained.

The handoff commit itself is expected to be newer than `3d8d8e...`. Always verify the actual PR head and CI conclusions first.

---

# Locked architecture

Durable decisions currently run through **D-026**.

Start with:

- `docs/architecture/README.md`
- `docs/architecture/DECISIONS.md`
- `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`
- `docs/architecture/CODEBASE_TOPOLOGY.md`
- `docs/architecture/DEVELOPMENT_WORKFLOW.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`
- `docs/architecture/DASHBOARD_MODEL_AND_TS_IMPORT.md`

Key retained rules:

- **D-022** — inherited behavior is reviewed piece-by-piece and reimplemented cleanly;
- **D-023** — Android production runtime remains Kotlin / Jetpack Compose + Rust with typed / packed runtime boundaries;
- **D-024** — historical plugin capabilities become native Tuning Tools, not a runtime plugin system;
- **D-025** — four primary workspaces group six distinct functional/domain routes without ownership collapse;
- **D-026** — GitHub Pages is a first-class modular product / interaction lab, not the production Android runtime.

Do not let the browser prototype override production state ownership, ECU authority, mutation safety, JNI/runtime contracts or native performance evidence.

---

# Current development model

EpicHub deliberately uses three coordinated lanes:

```text
Pages product / UX lane
→ layout + interaction + workflow + behavior specification

Native Android performance / runtime lane
→ Compose + Kotlin + Rust + JNI + lifecycle + device qualification

Production translation lane
→ accepted Pages feature
→ explicit translation contract
→ native implementation
→ physical qualification
```

Browser performance is not Android performance evidence.

Pages is an **executable product specification**, not a second production app and not a WebView plan.

---

# Public GitHub Pages prototype

The repository is now public specifically so the modular prototype can use GitHub Pages directly.

Current publication workflow:

`.github/workflows/prototype-pages.yml`

Triggers:

- relevant prototype changes on `phase2/ui-diagnostics-foundation`;
- relevant prototype changes on `main`;
- manual `workflow_dispatch`.

The workflow performs:

```text
checkout
→ Node setup
→ install dependencies
→ typecheck / tests / Vite build
→ Pages artifact
→ deploy
```

Live prototype:

`https://pjawzk.github.io/EpicHub-EpicEFI-Android/`

The first Pages deployment blocker was the `github-pages` environment branch policy; that was corrected and deployment subsequently passed.

---

# Modular prototype structure — anti-monolith authority

The browser prototype is under:

`prototype/`

Current deliberate structure:

```text
prototype/
├── index.html                 # mount/document only
├── package.json
├── tsconfig.json
├── vite.config.ts
├── src/
│   ├── main.ts                # bootstrap only
│   ├── app/                   # composition / routing / shell
│   ├── model/                 # transport-neutral prototype models
│   ├── ports/                 # typed feature capabilities
│   ├── adapters/
│   │   ├── fixtures/          # deterministic demo/runtime sources
│   │   └── ts-shim/           # future external TS-Shim adapter
│   ├── ui/                    # genuinely shared UI primitives
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

Important anti-monolith rules:

- `index.html` never becomes the application;
- `main.ts` remains bootstrap-only;
- shell / routing / dependency composition stays in `app/`;
- feature-specific rendering, state, actions and CSS stay feature-local;
- `ui/` is only for things with real cross-feature semantics;
- transport / wire / TS-Shim code cannot live inside feature pages;
- do not create generic dumping grounds such as one giant `utils.ts`, `helpers.ts`, `AppManager` or all-feature global state object;
- roughly **350–500 lines** in one prototype file is a responsibility-review signal, not a target.

This is intentionally similar in development ergonomics to EpicScope without copying EpicScope domain ownership into EpicHub.

---

# Current shared shell / base layout

The current browser shell deliberately takes the **good EpicScope top-bar density and structural approach** as a visual/interaction reference while preserving EpicHub's own workspace model.

Current accepted / implemented base direction:

- compact **54 px** top bar;
- full-window application shell;
- thin low-cost bottom status bar;
- feature content owns the work area between top and bottom bars;
- no permanent row of primary workspace tabs;
- EpicHub brand / workspace control opens the primary workspace switcher;
- current functional route is shown beside EpicHub;
- Tuner/Tuning Tools and Logger/Analyzer local navigation lives in the same top row;
- center area contains source authority/status and currently labels fixture data unmistakably as **FIXTURE**;
- right-side shell actions currently contain:
  - Dashboard Edit — only visible on Dashboard;
  - global Screen Lock;
  - reserved / disabled Problem Capture placement;
  - Settings;
- no clock, Rate/Age clutter, global Read ECU/Burn controls or page-specific command clutter has been added to the shared shell;
- current Screen Lock and Dashboard Edit controls establish shell state / presentation state only; do not invent deeper behavior until their actual feature semantics are accepted.

Current shell code is intentionally split rather than centralized:

```text
prototype/src/ui/
├── top-bar.ts
├── workspace-menu.ts
├── route-nav.ts
├── source-status.ts
├── settings-popover.ts
└── status-bar.ts

prototype/src/styles/
├── tokens.css
├── base.css
├── shell.css
├── top-bar.css
├── overlays.css
├── status-bar.css
└── feature-base.css
```

`prototype/src/app/shell.ts` should remain composition/orchestration, not absorb all header, navigation, settings and styling behavior again.

## Immediate product priority

Even though Dashboard groundwork now exists, the user's current instruction is to **finish reviewing/refining the shared base first**.

Next UI discussion should therefore stay on:

- top-bar proportions and density;
- what belongs left / center / right;
- workspace-menu behavior;
- local-route treatment;
- source-status presentation;
- Settings / Lock / Edit / Problem placement;
- bottom status bar;
- responsive tablet / phone collapse behavior;
- consistency with the useful parts of EpicScope's shell.

Do **not** immediately expand Dashboard/Tuner/Logger/Diagnostics feature depth unless the base is accepted or the user explicitly changes direction.

---

# Product information architecture — still locked under D-025

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

Both Pages and Android must preserve this separation.

---

# Dashboard groundwork already present — preserve, but do not let it bypass the base review

After the shell base pass, the branch also added a declarative Dashboard foundation.

Authority:

`docs/architecture/DASHBOARD_MODEL_AND_TS_IMPORT.md`

Current Dashboard direction:

- Dashboard is an EpicHub-owned declarative data model;
- no TunerStudio runtime embedding;
- no TunerStudio JavaScript execution;
- no WebView as the production Dashboard runtime;
- source-canvas width / height and exact x/y/width/height geometry are preserved;
- complete canvas scales while retaining relative position and size;
- initial widget vocabulary:
  - radial gauge;
  - digital value;
  - linear bar;
  - indicator;
  - static label;
- Dashboard Edit is local document editing, never ECU tuning mutation / Burn authority;
- TunerStudio `.dash` compatibility is **conversion, not emulation**;
- imported foreign actions / expressions do not gain controller authority automatically;
- unsupported or approximate import mappings must be reported explicitly;
- channel identity is separate from layout geometry;
- TS-Shim is a future runtime-data / command adapter, not the Dashboard layout engine.

The branch contains an initial `DashboardDocument`, default dashboard fixture and renderer. Treat these as groundwork already established, not as a reason to skip the user's current base-shell review.

Before implementing a real `.dash` parser, inspect actual project `.dash` files and qualify against representative fixtures rather than guessing the schema.

---

# TS-Shim groundwork

The prototype architecture intentionally prepares for a future TS-Shim interface:

```text
EpicHub Pages feature
→ typed prototype port
→ prototype/adapters/ts-shim
→ external transport / wire protocol
→ TS-Shim
```

Adapter owns:

- endpoint configuration;
- reconnect behavior;
- wire schema/version;
- serialization;
- capability translation.

Feature code must not know socket URLs, JSON keys, request opcodes or shim-specific connection state.

If TS-Shim uses JSON / WebSocket / HTTP, that is an external interoperability boundary allowed by D-023, not a production Android runtime choice.

Initial TS-Shim work should be read-only until mutation identity, safety and verification are separately accepted. Browser code must never gain raw ECU page/offset/byte authority by convenience.

The same modular Pages groundwork can eventually become the basis of the TS-Shim-facing web interface, but do not collapse the TS-Shim adapter into feature logic.

---

# Native Android performance state — parallel lane

Pages work does **not** replace or settle native Android performance work.

The earlier native optimization series established:

- Rust/JNI/runtime cost is comparatively small;
- steady-state rendering is much healthier than route changes;
- dominant remaining jank is transition-related;
- selector/navigation-control A/B experiments changed CPU/allocation/jank tradeoffs but did not fully explain the root cause;
- a lightweight selector recovered some low-CPU feel but transition jank remained high;
- the next native hypothesis became destination composition lifetime / subtree reconstruction rather than merely selector visuals;
- the current native experiment retains inactive destination compositions using `ReusableContentHost` for physical A/B testing.

Macrobenchmark / Perfetto infrastructure now runs outside the target app against the SM-A137F physical device.

Important harness history:

- benchmark APK publication was initially unsigned — fixed;
- CI now verifies APK signatures;
- the first physical deterministic run reached instrumentation but all three scenarios failed because:
  - AndroidX warm `startActivityAndWait()` could not confirm a rendered frame on the SM-A137F (`lastFrameNs=null`);
  - cold journey UI Automator could not find the expected `Tuner workspace` node;
- the harness was then hardened to:
  - avoid that warm framestats completion dependency;
  - use more robust visible-label fallbacks;
  - preserve failure evidence including gfxinfo, UI hierarchy, activity/window state, logcat and produced traces;
- **Foundation quality #275** passed for native/performance harness head `faff3a2d17f5838c4bd78a12bd72f27aec014855`;
- a successful deterministic physical run of that hardened path has not yet been recorded in this chat because work shifted to Pages/product structure.

Keep native performance investigation separate. Do not simplify accepted product functionality merely to hide current Compose cost.

See:

`docs/PERFORMANCE_BENCHMARKING.md`

---

# Pages → native translation rule

Do not mechanically port completed TypeScript features into Android.

For each substantial accepted Pages feature, record a translation contract covering:

```text
visual / interaction authority
inputs / outputs
actions
important state transitions
empty / loading / error / stale behavior
identity / provenance requirements
production owner(s)
Compose responsibility
Kotlin platform / workflow responsibility
Rust / domain responsibility
browser-only shortcuts to discard
native qualification required
```

Then implement natively against production contracts and physically qualify.

---

# Latest reviewed / historical visual evidence

The latest previously reviewed standalone EpicHub tablet UI prototype was:

`EpicHub-1.12.1_TabletLandscape.html`

The user can re-upload it if exact contents are required.

Do not treat it as current architecture authority. It is visual/interaction evidence only.

For the current **base shell**, also inspect the current EpicScope structure/top bar as a reference because the user explicitly wants the good EpicScope base qualities carried into EpicHub.

---

# Immediate next work for the continuation chat

1. Read this handoff and the authority docs listed above.
2. Verify current PR #3 head and the final conclusion of any CI run triggered by this handoff commit.
3. Open/check the live Pages prototype at `https://pjawzk.github.io/EpicHub-EpicEFI-Android/`.
4. **Stay on the shared base shell first.** Review it against the current EpicScope top-bar / structure and the accepted EpicHub shell decisions.
5. Iterate only the common shell/base until its proportions, grouping, navigation, status and responsive behavior are accepted.
6. Preserve the declarative Dashboard groundwork already present, but do not let it pull work away from the base-shell review prematurely.
7. Keep Prototype quality green and let Pages auto-deploy relevant prototype changes.
8. Continue the native SM-A137F deterministic Macrobenchmark path independently when returning to native performance work.
9. Review the real TS-Shim external contract before implementing its adapter.

---

# Deferred / guarded work

Still guarded by production contracts rather than by what the prototype can visually demonstrate:

- real Android ECU USB session/protocol;
- ECU mutation / Burn;
- production logging format implementation;
- deep native Analyzer engine;
- qualified tuning-tool algorithms;
- BLE/accessory migration;
- TS-Shim mutation/control capability;
- real TunerStudio `.dash` parser until representative source files are inspected and fixture-tested.

The Pages prototype may design workflows earlier, but it does not grant production authority or bypass D-022 / D-019 safety, identity or mutation gates.

---

# Ready-to-paste continuation prompt

Use this in a new chat:

```text
Continue EpicHub development from the repository handoff.

Repository: PJawZK/EpicHub-EpicEFI-Android
Active branch: phase2/ui-diagnostics-foundation

Read docs/HANDOFF.md first, then verify current PR #3 head, CI state and the live GitHub Pages prototype before changing anything.

Treat the current repository architecture documents as authority. Do not reconstruct EpicHub from old EpicDash-JZ/plugin code or old standalone HTML unless the handoff explicitly calls for historical/visual evidence.

Current product priority: focus on the shared EpicHub base first. Use the good EpicScope top-bar/structural layout as a reference together with the already-implemented EpicHub shell. Refine the common top bar, workspace/menu behavior, local route navigation, source status, right-side actions, bottom status bar and responsive structure before expanding feature-specific UI depth.

Preserve the declarative Dashboard/TS-import groundwork already in the branch, but do not let it displace the base-shell review.
```
