# EpicHub UI Foundation Layout

## Purpose

This document turns the Phase 1 interaction evidence into a concrete **ground layout contract** for the major EpicHub functions before real ECU data and feature-specific implementation are attached.

It defines screen responsibilities, persistent shell regions, responsive behavior and shared interaction surfaces. It does **not** lock final colors, iconography, typography tuning or decorative styling.

The goal is to make later subsystem work land in a stable UI structure instead of each feature inventing its own navigation/layout.

User-authored HTML/CSS prototypes may be used as rapid visual/layout interaction references while the design is still evolving. Once an HTML prototype or screenshot family is explicitly accepted, it may become the visual/layout authority for the corresponding screen. Production EpicHub still reimplements that design in Compose; the HTML/WebView/browser implementation is not embedded as application runtime architecture. See D-023 and `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`.

---

# 1. Primary application destinations

The first-class product destinations are:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

`Settings` is a global application surface rather than a peer high-frequency work destination.

`Tuning Tools` is the native home for the former AE/PID/TCharge/Injection-Angle plugin capabilities. It is a first-class functional destination from the ground UI stage, even if some individual tools are initially placeholders or explicitly marked unavailable/not-qualified. See D-024 and `TUNING_TOOLS_FOUNDATION.md`.

Accessory/ESP32 functionality is contextual:

- user controls may appear as Dashboard widgets/actions;
- connection/device configuration lives under Settings → Accessories;
- diagnostic state appears under Diagnostics;
- a future dedicated Accessories destination may be added only if the actual control surface becomes large enough to justify one.

The information architecture therefore contains six functional destinations plus global Settings. **Navigation presentation is responsive rather than forcing six tiny permanent phone buttons.** Wide layouts may expose all six directly; compact layouts may group lower-frequency destinations behind an explicit `More`/secondary navigation surface or place Tuning Tools next to/under Tuner, provided every destination remains one deliberate action away and its state is preserved.

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

On compact layouts the shell keeps a bottom/compact navigation surface, but it must not compress six destinations into unusable tap targets merely to mimic tablet navigation.

Conceptually:

```text
┌──────────────────────────────────────────────┐
│ compact status/header                       │
├──────────────────────────────────────────────┤
│                                              │
│              active destination              │
│                                              │
├──────────────────────────────────────────────┤
│ compact primary navigation + explicit More  │
└──────────────────────────────────────────────┘
```

The exact compact grouping is visual-design work and should be informed by the accepted HTML prototypes and physical phone testing.

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

- persistent left navigation rail exposing the functional destinations directly;
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
- bounded primary navigation with an explicit route to all six functional destinations;
- one dominant content surface at a time;
- inspector/details use bottom sheets or dedicated subpages;
- avoid shrinking tables/plots merely to preserve desktop-style sidebars;
- do not use six tiny equally weighted bottom-navigation items if that damages touch/label clarity.

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

Available from Dashboard, Logger, Analyzer and Tuning Tools by long-press/right-click/explicit details action.

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

Common plot interaction language for Logger/Analyzer/Tuning Tools/diagnostics metrics:

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
(compact navigation)
```

Rules:

- Dashboard page swipe/order is internal to Dashboard and never changes top-level navigation.
- Long-press on a gauge opens display/channel details/settings.
- Layout editing is an explicit mode so ordinary driving interaction cannot accidentally rearrange widgets.
- Accessory actions may be widgets, but they route through the semantic action model.
- Warning overlays remain bounded and do not permanently steal the dashboard workspace.
- Accepted user-authored HTML Dashboard prototypes may refine/supersede this generic geometry once explicitly approved; Compose reproduces the approved result without embedding the HTML runtime.

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
- local semantic draft, live verified RAM, saved-project and Burn/uncertainty states remain distinguishable;
- manual Tuner editing and guided Tuning Tools workflows remain separate UI responsibilities even when they target the same semantic calibration values.

---

# 7. Tuning Tools ground layout

Primary purpose: guided tuning-assistance workflows built from the former AE/PID/TCharge/Injection-Angle plugin knowledge without preserving a plugin runtime.

Initial visible tool families:

```text
Acceleration Enrichment
PID Controller
TCharge
Injection Angle
```

Individual tools may initially be placeholders, but their state must be honest:

```text
Planned
Evidence review
Read-only analysis
Guided capture
Recommendation qualified
Bounded experiment qualified
Unavailable for current source/profile
```

## Wide

```text
┌──────────────────────────────────────────────────────────────┐
│ Tuning Tools | source/session | profile | tool status        │
├──────────────────┬──────────────────────────────┬─────────────┤
│ tool browser     │ active tool workspace        │ evidence /  │
│ categories       │ guidance / plots / results   │ details /   │
│ maturity badges  │ capture / recommendation     │ provenance  │
└──────────────────┴──────────────────────────────┴─────────────┘
```

## Compact

- tool selector/cards first;
- active tool workspace occupies full width;
- evidence/details/provenance move to a bottom sheet/subpage;
- guided capture controls remain reachable without crowding result/plot surfaces.

Rules:

- Tuning Tools consumes the shared ChannelStore/MeasurementStore/analysis platform; it does not create a plugin-local channel or log model.
- Tool algorithms/domain profiles are native Rust responsibilities; Compose owns guided workflow/presentation.
- Analyzer remains the generic data-exploration workspace. A tool may deep-link to Analyzer or reuse common plots/statistics without becoming Analyzer itself.
- A tool may produce a semantic recommendation or experiment proposal but never owns raw ECU write/Burn authority.
- No dynamic JAR/JavaScript/plugin loader is introduced merely to preserve historical packaging.

The detailed contract is `TUNING_TOOLS_FOUNDATION.md`.

---

# 8. Logger ground layout

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

Accepted user-authored HTML Logger prototypes may refine/supersede this generic geometry once explicitly approved; Compose reproduces the approved result without making HTML/JavaScript the runtime implementation.

---

# 9. Analyzer ground layout

Primary purpose: explore live/imported measurement data, compare sessions, inspect channels and run generic analysis/query tasks.

Analyzer is distinct from Logger and Tuning Tools:

- Logger optimizes reliable acquisition/writing;
- Analyzer optimizes generic inspection/query/comparison/model-support work;
- Tuning Tools packages domain-specific tuning workflows using the same underlying sources/analysis primitives.

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
- source loading/analysis operations reject stale async results by request generation;
- domain-specific AE/PID/TCharge/Injection-Angle guided workflows belong in Tuning Tools, while generic plots/statistics/query primitives remain reusable here.

---

# 10. Diagnostics ground layout

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
- Tuning Tools;
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
- analyzer queries/captures;
- Tuning Tools capture/analysis jobs.

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

# 11. Settings ground layout

Settings is grouped by responsibility rather than historical implementation class.

Initial groups:

```text
General
Units & formatting
Dashboard presentation
Tuner presentation
Tuning Tools
Logging & storage
Connections
Accessories
Diagnostics & privacy
Appearance
About / build information
```

Settings may open as a dedicated route/screen but does not need to occupy one of the high-frequency primary navigation slots.

Connection settings configure policy/preferences; live connect/reconnect state remains owned by the actual transport/session domain.

---

# 12. Global problem-capture affordance

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

# 13. Initial implementation order

Before real ECU transport enters the clean application:

```text
1. build the stable responsive app shell/navigation
2. create real placeholder/fixture surfaces for all six functional destinations
3. include Tuning Tools cards/workspace structure from the beginning
4. implement Diagnostics event/metric/capture plumbing
5. route the already-qualified synthetic RuntimeSnapshot into Dashboard + Diagnostics + relevant tool fixtures
6. refine Dashboard/Logger styling from accepted HTML prototypes as they mature
7. verify layout/state/lifecycle on phone and tablet
8. only then attach the first real read-only ECU transport slice
```

Placeholder screens must use the real navigation/state architecture, not temporary Activities that are thrown away later.

---

# Acceptance criteria for the ground UI

The ground UI is ready for real subsystem integration when:

- Dashboard, Tuner, Tuning Tools, Logger, Analyzer and Diagnostics all exist in one responsive shell;
- Settings is globally reachable;
- compact navigation keeps all destinations reachable without undersized/ambiguous controls;
- navigation survives rotation/recreation without duplicated engine ownership;
- Dashboard can consume a bounded RuntimeSnapshot projection;
- Tuner has stable hierarchy/editor/secondary-panel geometry even with fixture content;
- Tuning Tools has a stable tool-selection/workspace/inspector pattern and visible AE/PID/TCharge/Injection-Angle entries with honest maturity states;
- Logger has separate Live and Trigger pages;
- Analyzer has source/channel/workspace/inspector regions and compact equivalents;
- Diagnostics has Overview/Events/Metrics/Sessions/Captures pages;
- global problem capture is reachable from every normal destination;
- no screen introduces raw ECU/page/GATT/file-format authority;
- no plugin/WebView runtime is introduced for Tuning Tools or HTML-derived visual prototypes;
- phone and tablet layouts are both physically usable before feature depth is added.

## Summary

The UI foundation is intentionally **structural before decorative**, while accepted HTML prototypes can later refine the visual/layout authority:

> establish stable destinations, work regions, responsive behavior and shared interaction surfaces first; make native Tuning Tools visible from the start; then translate accepted visual prototypes into Compose and attach each real subsystem through its clean semantic contracts without repeatedly redesigning the application shell.