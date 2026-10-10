# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the actual branch / PR / CI / Pages state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Active development branch: `phase2/ui-diagnostics-foundation`

Active draft PR: **#3 — Phase 2: establish UI, diagnostics and product-prototype foundations**

## Current product direction — IMPORTANT

The first EpicScope-inspired EpicHub visual shell is **rejected**.

Do **not** continue refining the current 54 px top bar, bottom status bar, current workspace-menu composition, current source-status placement, or current Lock/Edit/Problem/Settings grouping as if those were accepted product direction.

The latest explicit instruction is to **scrap that visual foundation and review the foundation properly before another shell implementation**.

Read:

- `docs/architecture/UI_FOUNDATION_LAYOUT.md`
- `docs/architecture/UI_FOUNDATION_REVIEW.md`
- `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`
- `docs/architecture/DECISIONS.md`

The new review document is the immediate authority for how to treat the rejected shell.

---

# What remains valid

The rejection is visual/product-shell scope, not a rollback of the core architecture.

Retain:

- D-022 clean reimplementation method;
- D-023 Kotlin / Jetpack Compose + Rust production runtime;
- D-024 native Tuning Tools rather than a plugin runtime;
- D-025 four primary workspaces / six distinct domain routes;
- D-026 modular GitHub Pages product/interaction lab;
- modular `prototype/` anti-monolith structure;
- typed prototype ports/adapters and TS-Shim isolation;
- declarative Dashboard document/model and TS `.dash` conversion direction;
- production safety/state ownership boundaries;
- native Android performance work as an independent lane.

Information architecture remains:

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

Visual grouping does not change domain ownership.

---

# Rejected shell implementation

The following current browser files are implementation evidence only and are **not visual authority**:

```text
prototype/src/ui/top-bar.ts
prototype/src/ui/workspace-menu.ts
prototype/src/ui/route-nav.ts
prototype/src/ui/source-status.ts
prototype/src/ui/status-bar.ts
prototype/src/ui/settings-popover.ts
prototype/src/styles/top-bar.css
prototype/src/styles/overlays.css
prototype/src/styles/status-bar.css
prototype/src/styles/shell.css
```

Do not translate this shell into Compose.

Do not incrementally polish it.

The current shell may remain temporarily in the branch while the replacement foundation is designed, solely so there is a runnable comparison surface and the Dashboard groundwork remains reachable.

---

# Why the shell was rejected

The earlier work skipped a necessary foundation stage and treated an implementation experiment as accepted design.

Main problems now recorded in `UI_FOUNDATION_REVIEW.md`:

- one compact row was chosen before global/workspace/route responsibilities were resolved;
- desktop EpicScope density was copied too literally for an Android-first, touch/in-car product;
- global and feature-local actions were mixed;
- disabled/future Problem Capture consumed permanent chrome;
- source/provenance was reduced to badge placement rather than a complete semantic hierarchy;
- bottom status bar had no proven unique responsibility;
- route identity was duplicated;
- responsive behavior was mostly subtraction/hiding instead of four deliberate presentation recipes;
- Lock/Edit states were visually exposed before their behavior contracts existed;
- the shell was judged without representative Dashboard/Tuner/Logger/Diagnostics surfaces inside it.

EpicScope remains useful as **principle evidence**, not a dimensional/template authority.

Useful EpicScope lessons:

- strong hierarchy;
- maximize real work-surface area;
- contextual controls near the work they affect;
- product/logo switcher can be useful;
- avoid permanent placeholders for capabilities that do not exist;
- do not duplicate context across global and local chrome.

Do not inherit its desktop dimensions, tiny typography, hover assumptions, Logger-specific toolbar composition or domain ownership automatically.

---

# Required next foundation sequence

Do this **before another shared-shell implementation**:

1. Classify every candidate shell item as:
   - permanent global identity/navigation;
   - global runtime/status truth;
   - workspace-local navigation;
   - route-local action;
   - transient/contextual action;
   - overlay/sheet/settings action;
   - invisible until the capability actually exists.
2. Define four intentional presentation classes:
   - Tablet Landscape;
   - Tablet Portrait;
   - Phone Landscape;
   - Phone Portrait.
3. Define touch/readability constraints before styling:
   - touch target policy;
   - text hierarchy;
   - in-car glance priority;
   - icon-only rules;
   - safe-area/system-bar behavior;
   - landscape/portrait collapse rules.
4. Exercise the shell with minimal representative surfaces for:
   - Dashboard;
   - Tuner;
   - Logger/Analyzer;
   - Diagnostics;
   - Settings;
   - fixture/disconnected/live/error states.
5. Only after those structural decisions are sound, establish visual tokens and implement the replacement shell.
6. Require explicit user acceptance of the replacement base before expanding feature depth.

---

# Dashboard groundwork

Preserve the declarative Dashboard work already present:

- EpicHub-owned `DashboardDocument`;
- source-space geometry;
- native widget vocabulary;
- Dashboard Edit as local document editing, not ECU mutation;
- `.dash` compatibility as conversion, not emulation;
- no foreign JavaScript/action authority;
- unresolved/approximate import mappings must be reported;
- TS-Shim is a runtime adapter, not a layout engine.

Do not let the current surrounding rejected shell become Dashboard presentation authority.

Before implementing the real `.dash` parser, inspect representative project `.dash` files and fixture-test the conversion.

---

# Pages / CI

GitHub Pages is configured at:

`https://pjawzk.github.io/EpicHub-EpicEFI-Android/`

Prototype publication workflow:

`.github/workflows/prototype-pages.yml`

It deploys relevant prototype changes from both `phase2/ui-diagnostics-foundation` and `main`.

The rejected responsive refinement commit `97e1c3eec0686e97ba66d73b8398b5eaaf967814` was explicitly reverted by `38fcb24871cd062aa8312d7c832967e6c0d710b0`.

A foundation-review document was then added. Always verify the actual current PR head and CI results rather than relying on these historical SHAs.

---

# Native Android lane

Pages work does not settle Android performance.

Keep the existing physical SM-A137F Macrobenchmark / Perfetto investigation separate. The remaining native hypothesis concerns transition/destination composition lifetime rather than Rust/JNI steady-state cost alone.

Do not simplify accepted product functionality merely to make current Compose benchmarks look better.

See `docs/PERFORMANCE_BENCHMARKING.md`.

---

# Guarded/deferred work

Do not let the visual prototype bypass production contracts for:

- real ECU USB/protocol;
- ECU mutation/Burn;
- production logging format;
- deep native Analyzer engine;
- qualified tuning-tool algorithms;
- BLE/accessory migration;
- TS-Shim mutation/control;
- real `.dash` parser before representative-source qualification.

---

# Immediate next work

The next continuation should **not code another top bar immediately**.

First produce the foundation responsibility map and four-presentation shell contract described above, using current EpicScope as principle evidence and the retained EpicHub architecture as authority. Only then build a replacement shared shell from scratch.
