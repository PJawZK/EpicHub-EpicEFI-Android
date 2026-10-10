# EpicHub Shared Shell Foundation Contract

Status: **foundation proposal for review — not visual authority yet**

This document establishes the responsibility model and presentation-class rules that must exist before the rejected shell is replaced.

Authority context:

- `UI_FOUNDATION_LAYOUT.md`
- `UI_FOUNDATION_REVIEW.md`
- D-023 through D-026

The goal is not to design one universal toolbar. The goal is to define one semantic application shell that can have deliberate Tablet Landscape, Tablet Portrait, Phone Landscape and Phone Portrait presentations.

---

# 1. Core principle

The work surface is primary.

Shared chrome exists only when it supplies one of these permanent responsibilities:

1. orientation — where am I in EpicHub?
2. source truth — what data/controller context am I looking at?
3. globally relevant state — is something important happening outside the current route?
4. global navigation — how do I move between primary workspaces/settings?
5. emergency/high-value global action — only when a real accepted capability requires it.

Everything else belongs closer to the feature that owns it.

The shell must not become a collection point for controls simply because every route can technically reach it.

---

# 2. Responsibility classes

Every visible control/status element must belong to exactly one primary presentation responsibility.

## A. Permanent global identity/navigation

Purpose: establish product identity and move between primary workspaces.

Semantic contents:

- EpicHub product identity;
- active primary workspace;
- primary workspace switching:
  - Dashboard
  - Tuner
  - Logger
  - Diagnostics;
- global Settings entry.

Rules:

- only the four D-025 primary workspaces appear here;
- Tuning Tools and Analyzer do not become top-level global destinations;
- exact mechanism may differ by presentation class;
- global navigation must not repeat the active leaf route when local navigation already communicates it.

## B. Global runtime/source truth

Purpose: make authority/provenance obvious without entering a feature.

Semantic candidates:

- source class: Fixture / Offline / ECU / TS-Shim / playback/imported source;
- connection/session state where applicable;
- controller/profile/source identity where available;
- stale/invalid/unverified state;
- active recording state when recording is globally consequential;
- active warning/critical incident summary when one exists;
- active diagnostic capture state when one exists.

Rules:

- source provenance is mandatory and must survive every presentation class;
- `FIXTURE`, `DEMO`, `OFFLINE`, `NOT VERIFIED` or equivalent must never disappear due to responsive collapse;
- healthy/default state should be quiet;
- exceptional states may become more prominent;
- shell projection never owns the underlying domain state.

## C. Workspace-local navigation

Purpose: move between distinct routes grouped under one primary workspace.

Current requirements:

```text
Tuner workspace  → Tuner | Tuning Tools
Logger workspace → Logger | Analyzer
```

Dashboard and Diagnostics currently have no equivalent required leaf-route switch.

Rules:

- local route switching is not global navigation;
- it may use a segment, tabs, contextual selector, compact menu or another presentation-class-appropriate control;
- it should appear near workspace context rather than competing with global controls;
- it must not duplicate the active route as a second label unless the duplication adds real orientation.

## D. Route-local actions

These stay with the route unless a separate accepted reason promotes them.

Examples:

### Dashboard

- Edit / Done;
- page selection;
- widget/layout actions;
- layout lock if layout-specific.

### Tuner

- current tune/profile context;
- local edit/apply/write/burn actions;
- table/curve/parameter tools.

### Logger

- Record/Stop;
- channel preset;
- marker;
- trigger logger controls.

### Analyzer

- graph workspace;
- range/cursor/analysis tools;
- scatter/histogram/table actions.

### Diagnostics

- evidence filters;
- capture history/export actions;
- subsystem-specific views.

Rules:

- these actions do not enter global chrome merely because they are important within one route;
- ECU Write/Burn never becomes a shared shell action.

## E. Transient/contextual actions

Purpose: actions that matter only in a temporary state.

Examples:

- exit active edit mode;
- stop an active reproduction capture;
- resolve/acknowledge an active incident;
- active selection actions;
- temporary reconnect/retry surfaces.

Rules:

- appear because state requires them;
- disappear when the state ends;
- avoid permanently reserving space for rare states.

## F. Global overlay/sheet actions

Examples:

- Settings;
- workspace switcher where that presentation class uses an overlay;
- source/session details;
- warning/incident center;
- connection details;
- help/about if later justified.

Rules:

- overlays are entry points into real owned surfaces;
- placeholder previews do not earn permanent controls.

## G. Invisible until capability exists

Do not show disabled permanent controls merely to reserve future space.

Current examples:

- Problem Capture before its workflow is implemented;
- warning center before meaningful warning state exists;
- connection actions before a real source adapter exposes them;
- export/compare controls before those workflows exist.

Architecture can reserve ownership without visible chrome.

---

# 3. Current candidate placement decisions

These are foundation-level decisions, not final pixel layout.

| Item | Responsibility | Foundation treatment |
| --- | --- | --- |
| EpicHub identity | Global identity | Permanent |
| Workspace switch | Global navigation | Permanent, presentation varies |
| Settings | Global overlay | Permanent entry, low visual priority |
| Source/provenance | Global source truth | Permanent semantic presence |
| ECU/profile identity | Global source truth | Show when meaningful/available |
| Connection state | Global source truth | Quiet when healthy, prominent when exceptional |
| Warning summary | Global state | Conditional, only when warnings exist |
| Recording state | Global state | Conditional; route-local control remains Logger-owned |
| Problem capture | Global capability | No permanent placeholder until implemented |
| Dashboard Edit | Route-local | Dashboard only, not shared global action cluster |
| Screen Lock | Unresolved | Do not expose until behavior contract exists |
| Tuner / Tuning Tools switch | Workspace-local | Tuner workspace only |
| Logger / Analyzer switch | Workspace-local | Logger workspace only |
| Write/Burn | Route-local tuning authority | Never global shell |
| Record/Stop | Logger route-local | May project active recording state globally |
| Bottom status bar | No proven responsibility | Omit by default |

---

# 4. Presentation classes

The following are structural recipes, not final styling.

## Tablet Landscape

Primary qualities:

- largest persistent work surface;
- enough horizontal room to keep global identity, source truth and utilities visible;
- workspace-local navigation can be visible without crowding the global layer;
- dense feature tools remain inside the feature surface.

Proposed structure:

```text
┌──────────────────────────────────────────────────────────────┐
│ Global app bar: EpicHub/workspace | source truth | utilities │
├──────────────────────────────────────────────────────────────┤
│ Optional workspace context row: leaf routes / local context │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                     active work surface                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

The second row exists only when the workspace/route needs it. It is not mandatory empty chrome.

No permanent bottom status bar by default.

## Tablet Portrait

Primary qualities:

- preserve touch target size rather than shrinking desktop controls;
- allow local navigation to occupy its own row when necessary;
- global source truth stays visible in compact form;
- nonessential metadata moves into source/details overlays.

Proposed structure:

```text
┌──────────────────────────────────────────────┐
│ Global app bar                               │
├──────────────────────────────────────────────┤
│ Optional workspace/local route row           │
├──────────────────────────────────────────────┤
│                                              │
│              active work surface             │
│                                              │
└──────────────────────────────────────────────┘
```

A right-side tablet-landscape inspector may become a sheet/panel in portrait without changing ownership.

## Phone Landscape

Primary qualities:

- protect vertical work area aggressively;
- no desktop-style multi-row chrome by default;
- maintain source provenance and global orientation;
- local route switching may move into a compact contextual selector if a second row would harm the work surface.

Proposed structure:

```text
┌────────────────────────────────────────────────────────┐
│ Compact global bar + current workspace/source + utility│
├────────────────────────────────────────────────────────┤
│                  active work surface                   │
└────────────────────────────────────────────────────────┘
```

Workspace-local navigation can be:

- compact segment if it fits without shrinking targets;
- workspace-title dropdown/context menu;
- route switch invoked from the workspace control.

Do not solve phone landscape by making controls tiny.

## Phone Portrait

Primary qualities:

- thumb-friendly global navigation;
- source provenance visible without consuming excessive top-bar width;
- clear local route identity;
- sheets/subpages preferred over squeezing desktop inspectors horizontally.

Two candidate global-navigation patterns are allowed for product testing:

### Candidate P1 — bottom primary navigation

```text
┌─────────────────────────────┐
│ Context/source app bar      │
├─────────────────────────────┤
│ optional local route row    │
├─────────────────────────────┤
│                             │
│ active work surface         │
│                             │
├─────────────────────────────┤
│ Dash | Tuner | Log | Diag   │
└─────────────────────────────┘
```

Advantages:

- four primary workspaces fit naturally;
- strong thumb reach;
- active workspace is always obvious.

Cost:

- permanent bottom height.

### Candidate P2 — top workspace switcher

```text
┌─────────────────────────────┐
│ EpicHub/workspace | source  │
├─────────────────────────────┤
│ optional local route row    │
├─────────────────────────────┤
│                             │
│ active work surface         │
│                             │
└─────────────────────────────┘
```

Advantages:

- maximum content height;
- visually closer to tablet/landscape structure.

Cost:

- workspace switching is less immediate.

Phone Portrait should explicitly compare P1 and P2 rather than assuming the desktop/tablet switcher is automatically correct.

---

# 5. Interaction and readability policy

These rules are required before styling acceptance.

## Touch targets

Pages prototype target sizes should model Android intent:

- normal actionable target: **48 px minimum** in the prototype unless a larger invisible hit area is deliberately provided;
- dense secondary controls: **44 px minimum hit area** only where justified;
- do not accept 30–34 px desktop buttons as Android shell authority.

Visual icons may be smaller than the hit area.

## Typography

Meaningful persistent shell text should not rely on 9–10 px desktop labels.

Foundation target hierarchy:

- primary workspace/current context: approximately 15–17 px visual text in prototype;
- normal actionable labels: approximately 13–15 px;
- secondary status/detail: approximately 12–13 px;
- metadata below 12 px only when genuinely nonessential and still readable.

Final Android dp/sp mapping is a production translation concern, but the Pages prototype must not normalize illegibly tiny UI.

## In-car glance priority

At a glance, the user should be able to identify:

1. current workspace/route context;
2. whether data is real/live vs fixture/offline/unverified;
3. active exceptional state: recording, critical warning, capture, disconnected/stale;
4. the dominant local action for the active task.

Decorative detail must not compete with those four.

## Icon-only controls

Allowed when:

- icon meaning is conventional or repeatedly learned;
- hit target remains large enough;
- accessible label exists;
- Pages can provide tooltip/title for desktop testing;
- state is visually distinguishable.

Do not turn unfamiliar product concepts into mystery icons solely to save width.

## Hover/focus

- no required behavior may depend on hover;
- keyboard/focus behavior should work in Pages;
- keyboard support must not dictate Android visual geometry.

## Safe areas

Replacement CSS should account for browser/device safe areas using `env(safe-area-inset-*)` where relevant so prototype geometry does not assume rectangular unobstructed phone screens.

---

# 6. Source/provenance hierarchy

Source state must be designed semantically before being styled.

Minimum semantic states:

```text
Fixture / Demo
Offline / No source
Connecting
Live / verified source
Live / identity uncertain
Stale
Invalid / faulted
Playback / imported measurement
TS-Shim external source
```

The shell should normally present:

- compact source class/state;
- source/controller identity when available and useful;
- exceptional connection/validity state.

Expanded source details may expose:

- endpoint/transport;
- controller/profile identity;
- session/generation;
- age/freshness;
- channel/source counts;
- verification state.

Do not make Rate/Age permanent shell text unless later evidence proves they deserve that priority.

---

# 7. State projection vs action ownership

A recurring rule:

> The shell may project globally relevant state without owning the action that changes it.

Examples:

```text
Logger owns Record/Stop
→ shell may show “REC” while recording

Diagnostics owns capture workflow
→ shell may show active capture state once capability exists

Warning engine owns incidents
→ shell may show critical count/state

Transport/session owner owns connection
→ shell may show disconnected/reconnecting state
```

This prevents global chrome from becoming a command bus.

---

# 8. Representative-shell qualification matrix

Before accepting replacement chrome, exercise these combinations:

| Workspace/route | Must prove |
| --- | --- |
| Dashboard / Drive | low-noise glance view, no edit clutter |
| Dashboard / Edit | local edit controls appear without polluting every route |
| Tuner | workspace-local route switch + dense calibration actions fit naturally |
| Tuning Tools | nested route remains independently owned and discoverable |
| Logger / active recording | recording control local, recording state globally obvious |
| Analyzer | maximum graph area, no redundant route/global chrome |
| Diagnostics | evidence-first layout, incidents/source state clear |
| Settings | global access without pretending settings is a primary workspace |
| Fixture | unmistakable non-live authority in all four presentation classes |
| Disconnected/live/stale | state changes remain understandable without moving core navigation |

---

# 9. Replacement-shell implementation gate

Do not write the replacement shared shell until the design pass produces:

- one responsibility map matching this document;
- structural wireframes for all four presentation classes;
- explicit choice/testing plan for Phone Portrait P1 vs P2;
- representative route fixtures;
- touch/readability constraints applied before visual polish.

Once implemented, the first acceptance pass should judge **structure and hierarchy**, not feature completeness.

Only after user acceptance should the visual foundation be recorded as authority and translated toward Compose.
