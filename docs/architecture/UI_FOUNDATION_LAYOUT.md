# EpicHub UI Foundation Layout

## Purpose

This document turns the Phase 1 interaction evidence into a concrete **ground layout contract** for the major EpicHub functions before real ECU data and feature-specific implementation are attached.

It defines screen responsibilities, persistent shell regions, responsive behavior and shared interaction surfaces. It does **not** lock final colors, iconography, typography tuning or decorative styling.

The goal is to make later subsystem work land in a stable UI structure instead of each feature inventing its own navigation/layout.

---

# 1. Primary application destinations

The first-class product destinations are:

```text
Dashboard
Tuner
Logger
Analyzer
Diagnostics
```

`Settings` is a global application surface rather than a peer high-frequency work destination.

Accessory/ESP32 functionality is contextual:

- user controls may appear as Dashboard widgets/actions;
- connection/device configuration lives under Settings → Accessories;
- diagnostic state appears under Diagnostics;
- a future dedicated Accessories destination may be added only if the actual control surface becomes large enough to justify one.

This keeps the primary navigation focused on the five activities the user returns to continuously.

---

# 2. Global shell

Every normal screen uses the same conceptual shell:

```text
┌──────────────────────────────────────────────────────────────┐
│ Compact system/status header                                │
│ ECU/source • logging • warnings • diagnostic capture • gear │
├───────────┬──────────────────────────────────────────────────┤
│ primary   │                                                  │
│ nav       │              active destination                  │
│           │                                                  │
│           │                                                  │
└───────────┴──────────────────────────────────────────────────┘
```

On compact layouts the primary navigation moves to the bottom:

```text
┌──────────────────────────────────────────────┐
│ compact status/header                       │
├──────────────────────────────────────────────┤
│                                              │
│              active destination              │
│                                              │
├──────────────────────────────────────────────┤
│ Dash | Tuner | Log | Analyze | Diagnostics   │
└──────────────────────────────────────────────┘
```

## Global status header

The persistent header may contain compact indicators for:

- live/offline/source state;
- ECU/profile identity when available;
- active logging state;
- current critical/warning incident count;
- active diagnostic capture state;
- Settings action.

The header is a **projection** of domain truth. It never owns reconnect/logging/tuning state.

A global diagnostic-capture action remains reachable from the header even when the user is not on the Diagnostics page.

---

# 3. Responsive rules

EpicHub uses one semantic screen model and reflows by available geometry.

## Wide / tablet landscape

Preferred arrangement:

- persistent left navigation rail;
- compact global status header;
- main work area occupies the remaining viewport;
- screen-specific secondary inspector may remain visible on the right;
- complex workspaces may use two or three resizable regions where useful.

## Medium / tablet portrait / large phone landscape

Preferred arrangement:

- compact navigation rail or bottom navigation depending on measured usable width;
- screen-specific secondary regions move below the primary workspace or into a sheet;
- toolbars may wrap but the primary editor/plot remains dominant.

## Compact / phone portrait

Preferred arrangement:

- compact header;
- bottom navigation with the five primary destinations;
- one dominant content surface at a time;
- inspector/details use bottom sheets or dedicated subpages;
- avoid shrinking tables/plots merely to preserve desktop-style sidebars.

Orientation/window changes preserve semantic selection and domain state while recomputing layout only.

---

# 4. Shared UI surfaces

These should be common Compose components/patterns rather than reimplemented independently per destination.

## `SystemStatusStrip`

Displays compact authoritative connection/source/logging/warning/capture state.

## `ChannelPicker`

Search/filter/select semantic channels from the common ChannelStore/file source model.

Math/derived channels are visually identifiable but remain normal semantic ChannelIds to downstream screens.

## `ChannelDetailsSheet`

Available from Dashboard, Logger and Analyzer by long-press/right-click/explicit details action.

Expected information:

- semantic channel name/ID;
- source;
- current value and units;
- validity/freshness;
- sample/update rate;
- recent min/max/average where available;
- missing/stale/rejected counts where meaningful;
- logging state;
- math/derived expression identity when applicable;
- source/profile provenance.

The details surface consumes existing statistics/metadata; it does not become a second statistics engine.

## `PlotSurface`

Common plot interaction language for Logger/Analyzer/diagnostics metrics:

- pan/zoom;
- cursor;
- selection/range;
- markers/events;
- channel visibility;
- viewport-sized data requests.

## `InspectorPanel`

Common wide-screen secondary region; becomes a sheet/subpage on compact layouts.

---

# 5. Dashboard ground layout

Primary purpose: fast live vehicle/ECU state at a glance.

## Wide

```text
┌──────────────────────────────────────────────────────────────┐
│ dashboard page selector | lock/edit | page actions          │
├───────────────────────────────────────────┬──────────────────┤
│                                           │ optional details │
│         dashboard gauge/widget grid       │ / selected item  │
│                                           │                  │
└───────────────────────────────────────────┴──────────────────┘
```

## Compact

```text
page selector / edit
────────────────────
scrollable gauge grid
────────────────────
(bottom navigation)
```

Rules:

- Dashboard page swipe/order is internal to Dashboard and never changes top-level navigation.
- Long-press on a gauge opens display/channel details/settings.
- Layout editing is an explicit mode so ordinary driving interaction cannot accidentally rearrange widgets.
- Accessory actions may be widgets, but they route through the semantic action model.
- Warning overlays remain bounded and do not permanently steal the dashboard workspace.

---

# 6. Tuner ground layout

Primary purpose: profile-driven semantic calibration browsing/editing with clear authority state.

Persistent conceptual hierarchy:

```text
System → Category → Feature
```

Content modes:

```text
Parameters | Tables | Curves
```

## Wide

```text
┌──────────────────────────────────────────────────────────────┐
│ profile/session authority strip                             │
├──────────────────────────────────────────────────────────────┤
│ System / Category / Feature hierarchy                       │
├───────────────────────────────────────┬──────────────────────┤
│                                       │ Live / Details /     │
│      primary parameter/table/curve    │ Changes / Context    │
│               editor                  │ secondary panel      │
│                                       │                      │
└───────────────────────────────────────┴──────────────────────┘
```

## Compact

- authority/profile strip remains visible but condensed;
- hierarchy becomes selectors/stacks rather than a persistent rail;
- primary editor uses full width;
- Live/Details/Changes becomes a bottom sheet/subpage;
- table/curve editing must not be compressed until touch targets become unusable.

Rules:

- scalar/bit/table/curve type comes from the compiled semantic profile; no fallback guessing;
- 3D table inspection is view-only;
- live trace/cursor overlays are presentation projections only;
- local semantic draft, live verified RAM, saved-project and Burn/uncertainty states remain distinguishable.

---

# 7. Logger ground layout

Primary purpose: start/stop reliable recording and expose recording health without turning Logger into the analysis application.

Logger has two primary task pages from the beginning:

```text
Live Logger
Trigger Logger
```

A recording/session list may open completed sessions directly in Analyzer.

## Live Logger — wide

```text
┌──────────────────────────────────────────────────────────────┐
│ Record/Stop | elapsed | file size | rate | drops | storage  │
├───────────────────────┬──────────────────────────────────────┤
│ channels / presets    │ live bounded preview / timeline     │
│ search + selected     │                                      │
├───────────────────────┴──────────────────────────────────────┤
│ session health / writer queue / warnings                    │
└──────────────────────────────────────────────────────────────┘
```

## Live Logger — compact

- large Record/Stop control;
- compact health row;
- preview plot;
- channel/preset selection in a sheet;
- detailed queue/storage information behind an expandable health surface.

## Trigger Logger

Dedicated page for high-value digital timing relationships, rather than hiding this in the generic logger.

Ground signal lanes:

```text
Crank
TDC/reference
CAM1
CAM2
Injection pulses
Ignition pulses
```

Additional lanes may be added only when real protocol/source evidence supports them.

Primary controls:

- Arm / Capture / Stop;
- timebase/zoom;
- trigger/event marker;
- source/session identity;
- export/open-in-analyzer.

Logger never silently changes the acquisition/channel model to fit a file format.

---

# 8. Analyzer ground layout

Primary purpose: explore live/imported measurement data, compare sessions, inspect channels and run analysis tasks.

Analyzer is distinct from Logger:

- Logger optimizes reliable acquisition/writing;
- Analyzer optimizes inspection/query/comparison/model work.

## Wide

```text
┌──────────────────────────────────────────────────────────────┐
│ source selector | compare | analysis task | time range      │
├───────────────────┬────────────────────────────┬─────────────┤
│ channel browser   │ main timeline/workspace    │ inspector   │
│ search/groups     │ stacked or free-form plots │ stats/task  │
│ math channels     │ markers/events             │ results     │
└───────────────────┴────────────────────────────┴─────────────┘
```

## Compact

- source/task toolbar;
- main plot workspace full width;
- channel browser and inspector as sheets/subpages;
- free-form plot windows become an explicitly managed workspace rather than desktop windows squeezed into phone width.

Ground interaction requirements:

- stacked and free-form graph arrangements;
- shared timeline/cursor where applicable;
- comparison mode visibly states the compared sources;
- channel details/statistics open from long-press/right-click/details action;
- user math channels appear in the full channel list with a visible derived marker such as `*` while retaining stable semantic identity;
- source loading/analysis operations reject stale async results by request generation.

Future domain analyses (AE/PID/TCharge/injection phasing) plug into the Analyzer task/result region rather than becoming separate mini-apps.

---

# 9. Diagnostics ground layout

Primary purpose: make subsystem health, performance evidence and bug capture easy to inspect without diagnostics becoming system-control authority.

Primary pages:

```text
Overview | Events | Metrics | Sessions | Captures
```

## Overview

Subsystem health cards:

- App/UI;
- Native engine / JNI;
- ECU/USB;
- BLE/accessory;
- Logger/storage;
- Tuning authority;
- Analyzer;
- lifecycle/process health.

Each card shows concise health + latest relevant incident and links to filtered detail.

## Events

Filterable structured event stream:

- severity;
- domain;
- event code;
- time;
- correlation/session;
- expandable typed fields.

## Metrics

Registered metrics with explicit units and p50/p95/p99/max/current where meaningful.

## Sessions

Lifecycle/session/generation ownership view for:

- Activity/service;
- native engine;
- ECU session;
- USB connection attempts;
- BLE GATT generations;
- logger sessions;
- analyzer queries/captures.

## Captures

Saved problem-capture sessions:

- timestamp;
- build/device;
- user note;
- marker count;
- included domains;
- export/share/delete;
- optional linked measurement/log artifact references.

A persistent `Capture problem` action remains available from the global status header.

---

# 10. Settings ground layout

Settings is grouped by responsibility rather than historical implementation class.

Initial groups:

```text
General
Units & formatting
Dashboard presentation
Tuner presentation
Logging & storage
Connections
Accessories
Diagnostics & privacy
Appearance
About / build information
```

Settings may open as a dedicated route/screen but does not occupy one of the five high-frequency primary navigation slots.

Connection settings configure policy/preferences; live connect/reconnect state remains owned by the actual transport/session domain.

---

# 11. Global problem-capture affordance

Bug capture must be reachable while the user is inside the failing feature.

Recommended behavior:

- compact bug/record icon in `SystemStatusStrip`;
- tap when idle → quick actions:
  - `Capture recent problem`;
  - `Start reproduction capture`;
- tap while capture active →
  - `Add marker`;
  - `Stop & review`.

This avoids the common failure mode where navigating to Diagnostics destroys or changes the state the user wanted to capture.

The detailed capture architecture is defined in `DIAGNOSTICS_CAPTURE_FOUNDATION.md`.

---

# 12. Initial implementation order

Before real ECU transport enters the clean application:

```text
1. build the stable responsive app shell/navigation
2. create placeholder/fixture surfaces for all five primary destinations
3. implement Diagnostics event/metric/capture plumbing
4. route the already-qualified synthetic RuntimeSnapshot into Dashboard + Diagnostics
5. verify layout/state/lifecycle on phone and tablet
6. only then attach the first real read-only ECU transport slice
```

Placeholder screens must use the real navigation/state architecture, not temporary Activities that are thrown away later.

---

# Acceptance criteria for the ground UI

The ground UI is ready for real subsystem integration when:

- all five primary destinations exist in one responsive shell;
- Settings is globally reachable;
- navigation survives rotation/recreation without duplicated engine ownership;
- Dashboard can consume a bounded RuntimeSnapshot projection;
- Tuner has stable hierarchy/editor/secondary-panel geometry even with fixture content;
- Logger has separate Live and Trigger pages;
- Analyzer has source/channel/workspace/inspector regions and compact equivalents;
- Diagnostics has Overview/Events/Metrics/Sessions/Captures pages;
- global problem capture is reachable from every normal destination;
- no screen introduces raw ECU/page/GATT/file-format authority;
- phone and tablet layouts are both physically usable before feature depth is added.

## Summary

The UI foundation is intentionally **structural before decorative**:

> establish stable destinations, work regions, responsive behavior and shared interaction surfaces first; then attach each real subsystem through its clean semantic contracts without repeatedly redesigning the application shell.