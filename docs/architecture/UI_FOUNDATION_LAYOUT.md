# EpicHub UI Foundation Layout

## Purpose

This document defines the ground UI/information-architecture contract for EpicHub before the full ECU/logger/tuner/analyzer feature set is attached.

It separates three things that must not be conflated:

1. **primary user workspaces** — the small set of top-level places the user navigates between;
2. **functional/domain routes** — distinct responsibilities that remain separately owned in code;
3. **presentation recipes** — how the same semantic state/components are arranged for tablet/phone and landscape/portrait.

User-authored HTML/CSS/light-JavaScript prototypes are valid rapid design tools. Once explicitly accepted, a prototype/screenshot family may become the visual/layout authority for its presentation class. Production EpicHub still reimplements the accepted design natively in Compose; the browser/DOM/JavaScript implementation is not embedded into the app runtime.

Authority: D-016, D-017, D-023, D-024 and D-025.

---

# 1. Information architecture

## Four primary user workspaces

EpicHub presents four primary workspaces:

```text
Dashboard
Tuner
Logger
Diagnostics
```

`Settings` is global and does not consume a primary workspace slot.

## Six distinct functional/domain routes

The underlying functional routes remain:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

The presentation grouping is:

```text
EpicHub
├─ Dashboard
├─ Tuner
│  ├─ Tuner
│  │  ├─ Parameters
│  │  ├─ Tables
│  │  └─ Curves
│  └─ Tuning Tools
├─ Logger
│  ├─ Logger
│  │  ├─ Live Logger
│  │  └─ Trigger Logger
│  └─ Analyzer
│     ├─ Graph workspace
│     ├─ Scatter
│     ├─ Histogram / Table
│     └─ Math Channels
└─ Diagnostics

Settings = global
```

This is a **navigation grouping only**.

### Non-negotiable ownership invariant

Visual nesting must never become dependency nesting:

```text
WRONG
Analyzer → Logger UI/state → NativeEngine
Tuning Tools → Tuner UI/state → NativeEngine

RIGHT
Analyzer UI → Analyzer owner/shared measurement contracts
Logger UI   → Logger owner/shared measurement contracts
Tools UI    → Analysis/tool owner/shared channel/profile contracts
Tuner UI    → Tuning owner
```

Analyzer remains Analyzer-owned even though it appears inside the Logger workspace. Tuning Tools remains a separate analysis/tool responsibility even though it appears inside the Tuner workspace.

Primary-workspace selection and leaf-route selection are separate presentation state. Switching away and back should preserve the last meaningful leaf route when practical.

---

# 2. Global shell

Every normal screen uses the same conceptual shell:

```text
┌──────────────────────────────────────────────────────────────┐
│ EpicHub / active workspace context                           │
│ source • logging • warnings • problem capture • settings    │
├──────────────────────────────────────────────────────────────┤
│ workspace-local header / route controls when needed         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                    active work surface                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

The primary workspace selector exposes only:

```text
Dashboard | Tuner | Logger | Diagnostics
```

A workspace-local selector exposes its leaf routes where needed.

Examples:

```text
Tuner workspace:   Tuner | Tuning Tools
Logger workspace:  Logger | Analyzer
```

The accepted HTML references may refine the exact visual mechanism. It may be a dropdown, contextual header control, tab/segment, page selector or another deliberate pattern. The semantics above remain constant.

## Global status/header projection

The global shell may project compact state for:

- live/offline/source status;
- ECU/profile identity when available;
- logging/recording state;
- active warning/critical incident count;
- active problem-capture state;
- Settings access.

The header projects domain truth; it does not become transport, logger or tuning authority.

---

# 3. Four presentation authorities

EpicHub does not assume that one breakpoint-derived layout is sufficient for the final product.

The design process may establish four explicit visual authorities for each major screen/workspace:

```text
TABLET_LANDSCAPE
TABLET_PORTRAIT
PHONE_LANDSCAPE
PHONE_PORTRAIT
```

For Dashboard and Logger in particular, the user may maintain separate HTML prototypes for each class.

These are **presentation recipes over one semantic model**, not four applications and not four copies of domain logic.

```text
same route/state/actions/data
            │
            ▼
   presentation selection
            │
   ┌────────┼────────┬────────────┐
   ▼        ▼        ▼            ▼
tablet-L  tablet-P phone-L     phone-P
```

Production layout selection should use available window geometry/orientation rather than hardcoding device model names. The four authority classes are design targets; unusual future devices may map to the closest suitable presentation class.

Orientation/window changes must preserve semantic work state. Reflow must not reset current page, active channel, selected tool, analysis source, cursor, pending safe presentation state, or other domain-owned state merely because geometry changed.

The current simple wide/compact breakpoint in the scaffold is temporary implementation scaffolding, not the final visual contract.

---

# 4. Shared UI concepts

Common responsibilities should have common semantic/presentation contracts even when their geometry differs by workspace or presentation class.

## Channel selection/details

Dashboard, Logger, Analyzer and Tuning Tools should consume the same semantic channel identity/metadata model.

A channel-details surface may show:

- semantic channel name/ID;
- source;
- current value/unit;
- validity/freshness;
- sample/update rate;
- min/max/average where available;
- rejection/missing/stale counts where meaningful;
- logging state;
- math/derived expression identity;
- source/profile provenance.

Math/derived channels remain normal stable ChannelIds to downstream systems, while the UI marks them visibly (for example `*`).

## Plot interaction language

Logger, Analyzer, Tuning Tools and diagnostic metrics should reuse compatible interaction concepts:

- pan/zoom;
- cursor;
- range/selection;
- markers/events;
- channel visibility;
- bounded viewport/result requests.

Reuse does not require one giant screen/widget implementation.

## Detail/inspector placement

Wide/tablet-landscape layouts may keep details permanently visible on the right. Narrower/portrait layouts may move the same responsibility to a sheet/subpage.

This is presentation variation, not a new owner.

---

# 5. Dashboard workspace

Primary purpose: fast live vehicle/ECU state at a glance plus explicitly entered layout editing.

Required semantic behaviors:

- named Dashboard pages;
- explicit Drive versus Edit state;
- layout lock/protection;
- gauge/widget selection/configuration;
- move/resize only from deliberate edit interaction;
- long-press/details behavior;
- bounded warning presentation;
- accessory controls represented as semantic actions, never raw BLE/GPIO authority;
- optional deep-link/open-in-Logger behavior using semantic channel identity.

The accepted Dashboard HTML reference may supersede generic geometry in this document.

Dashboard layout geometry is presentation state. Runtime channel truth is not stored in widget definitions.

---

# 6. Tuner workspace

The Tuner primary workspace contains two separate functional routes:

```text
Tuner
Tuning Tools
```

## Tuner route

Primary purpose: profile-driven semantic calibration browsing/editing with explicit authority state.

Conceptual hierarchy:

```text
System → Category → Feature
```

Content modes:

```text
Parameters | Tables | Curves
```

Required authority distinctions remain visible:

```text
profile identity
verified TuneSnapshot
local pending semantic edits
verified dirty RAM
saved/Burn state
uncertainty state
```

Rules:

- scalar/bit/table/curve type comes from the accepted compiled semantic profile;
- no UI fallback guessing changes a parameter into the wrong editor type;
- 3D table view is inspection-only unless a separately qualified edit contract is created;
- RAM Write and Burn remain distinct explicit transactions;
- presentation never receives raw page/offset/byte authority.

## Tuning Tools route

Primary purpose: guided tuning-assistance workflows migrated from the historical plugin projects without recreating a plugin runtime.

Initial families:

```text
Acceleration Enrichment / transient fuel
PID / controller analysis
TCharge
Injection phasing
```

Tool states must remain honest, for example:

```text
Planned
Evidence review
Read-only analysis
Guided capture
Measurement qualified
Recommendation qualified
Bounded experiment qualified
Unavailable for current source/profile
```

The visual placement inside Tuner does **not** mean Tools calls through Tuner UI state.

Conceptual production path:

```text
Tuning Tools Compose workflow
→ shared typed channel/source contract
→ Rust analysis/domain profile
→ immutable result/recommendation/ExperimentProposal
→ optional proposal handed to sole tuning authority
```

Tools never own raw ECU Write/Burn.

See `TUNING_TOOLS_FOUNDATION.md`.

---

# 7. Logger workspace

The Logger primary workspace contains two separate functional routes:

```text
Logger
Analyzer
```

They may share a tightly integrated visual workspace because the user commonly moves directly between recording/playback/inspection tasks, but their domain ownership remains separate.

## Logger route

Primary purpose: reliable recording-session control and recording health.

Primary pages:

```text
Live Logger
Trigger Logger
```

### Live Logger responsibilities

- Record/Stop;
- elapsed/session/file/storage summary;
- selected channel/preset management;
- acquisition/write rate;
- drops/stalls/queue/storage health;
- bounded live preview;
- user marker/event action;
- completed-session handoff/open in Analyzer.

The file format never becomes runtime/channel authority.

### Trigger Logger

Dedicated digital timing capture with the initial lanes:

```text
Crank
TDC/reference
CAM1
CAM2
Injection pulses
Ignition pulses
```

Controls may include Arm/Capture/Stop, timebase/zoom, trigger/reference selection, markers, source/session identity and open/export actions.

## Analyzer route

Primary purpose: generic exploration of live/imported measurements.

Expected capabilities include:

- graph workspaces;
- stacked/grid/free-form presentation;
- persistent timeline/cursor;
- scatter plots;
- histogram/binned-table analysis;
- source/session comparison;
- A/B cursors and delta/statistics;
- saved ranges/markers;
- channel details/statistics;
- math/derived channels in the full channel list with visible derived marker;
- deterministic rejection of stale async query results.

Analyzer reads shared measurement/channel sources directly. It does not need Logger UI/state as a data gateway.

The accepted Logger/Analyzer HTML reference may define their integrated tablet-landscape composition while the code remains split by responsibility.

---

# 8. Diagnostics workspace

Primary purpose: **evidence, health, history and problem capture**.

Diagnostics remains observational under D-017.

Core evidence surfaces may include:

```text
Overview
Events
Metrics
Sessions / generations
Problem Captures
Warnings / incidents
```

The exact visual grouping may follow the accepted HTML prototype rather than these names being mandatory tabs.

## Evidence belongs here

Examples:

- subsystem health;
- transport/session generations;
- disconnect/reconnect history;
- protocol/CRC/timeout evidence;
- logger queue/drop evidence;
- analysis job state/rejections;
- lifecycle owner history;
- performance metrics;
- warning incidents/history;
- problem capture history/export.

## Operational controls do not become Diagnostics authority

Connection actions may be visible near diagnostic evidence for convenience, but ownership must remain explicit.

Preferred separation:

```text
Diagnostics
→ observe what happened / what is happening

Connection controls panel
→ Connect / Disconnect / Reconnect / Scan
→ routed to actual transport/session owner

Settings → Connections
→ persistent connection policy/preferences, e.g. auto-reconnect

Diagnostics → Warnings / incidents
→ current/history/acknowledgement

Settings → Warnings & alerts
→ rule definitions, threshold, debounce, cooldown, enablement
```

A convenience button shown on a Diagnostics page is only a semantic action trigger; Diagnostics must not implement the connection state transition itself.

---

# 9. Global problem capture

Diagnostic problem capture is distinct from ECU measurement logging.

It must be reachable while the user remains on the screen where the problem occurred.

Recommended visible naming should avoid confusion with Logger recording, for example:

```text
Problem capture
```

Compact representations may use a bug/diagnostic icon or shorter `Problem` label.

Idle actions:

```text
Capture recent problem
Start reproduction capture
```

While active:

```text
Add marker
Stop & review
```

`Capture recent problem` freezes recent bounded diagnostic flight-recorder context and collects a short bounded post-trigger window before finalizing an immutable capture.

`Start reproduction capture` records bounded diagnostic evidence while the user deliberately reproduces an issue and places markers.

The diagnostic capture does not silently become a full ECU measurement log.

See `DIAGNOSTICS_CAPTURE_FOUNDATION.md`.

---

# 10. Settings

Settings is global and grouped by responsibility rather than by whichever screen happens to expose a control.

Initial groups:

```text
General
Units & formatting
Dashboard presentation
Tuner presentation
Tuning Tools
Logging & storage
Connections
Warnings & alerts
Accessories
Diagnostics & privacy
Appearance
About / build information
```

Important separation:

- `Connections` owns persistent connection preferences/policy, not current diagnostic history;
- `Warnings & alerts` owns rule configuration, not incident history;
- `Diagnostics & privacy` owns capture/export/privacy policy, not reconnect or tuning authority.

---

# 11. Fixture/demo truth

Prototype and early scaffold states must never visually imply live verified authority when no real source exists.

Fixture/demo surfaces should make states such as these unmistakable:

```text
DEMO
FIXTURE
SYNTHETIC
OFFLINE PROTOTYPE
NOT CONNECTED
NOT VERIFIED
```

A simulated TuneSnapshot, profile match, ECU state, warning, analysis result or RAM experiment must not be presented using the same unqualified wording as real verified production state.

The HTML prototype may simulate actions to demonstrate workflow, but the production translation must use actual typed owner state.

---

# 12. Prototype serialization/code boundary

HTML prototypes may freely use browser-local implementation techniques that make visual iteration faster, including JavaScript objects/local state and JSON import/export.

Those mechanisms are **prototype evidence only**.

They do not establish production choices for:

- Compose state transport;
- Kotlin↔Rust communication;
- Dashboard layout persistence;
- Analyzer/Logger runtime state;
- tune-comparison authority;
- diagnostic capture format;
- Tuning Tools manifests.

Production representation follows D-023 and the relevant domain contract.

---

# 13. Current scaffold mapping

The current Compose scaffold should represent:

```text
PrimaryWorkspace
- Dashboard
- Tuner
- Logger
- Diagnostics

Leaf AppDestination / domain route
- Dashboard
- Tuner
- Tuning Tools
- Logger
- Analyzer
- Diagnostics
```

The scaffold may use visually plain tabs/segments for nested routes until the accepted HTML reference supplies final workspace-local navigation styling.

The active leaf route remains visible to diagnostics/restoration even though the global primary navigation highlights only its parent workspace.

---

# 14. Acceptance criteria

The ground UI/navigation architecture is ready when:

- only four primary workspaces are exposed globally;
- Tuning Tools remains a separate leaf/domain route inside Tuner;
- Analyzer remains a separate leaf/domain route inside Logger;
- no nested route depends on its parent UI/domain merely because of visual grouping;
- switching primary workspaces can preserve the last meaningful nested route;
- Settings remains global;
- problem capture is reachable without leaving the failing workspace;
- Diagnostics remains observational;
- connection control, connection policy, warning incidents and warning-rule policy have clearly separate owners/surfaces;
- fixture/demo authority is unmistakable;
- tablet-landscape, tablet-portrait, phone-landscape and phone-portrait may each have accepted visual authority without duplicating semantic/domain logic;
- accepted HTML prototypes translate to Compose rather than entering the runtime as WebViews/JavaScript;
- phone/tablet physical testing confirms usable touch/navigation/state restoration.

## Summary

EpicHub now deliberately favors a simpler user-facing hierarchy without weakening domain separation:

> **four primary workspaces, six distinct functional/domain routes, four presentation authorities, one semantic/runtime architecture.**
