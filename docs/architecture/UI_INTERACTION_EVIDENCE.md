# UI Interaction / Responsive / Performance Evidence

## Status

This document records accepted interaction/responsive/performance evidence gathered during Phase 1 and explains how it applies to the current Compose architecture.

It is **evidence**, not the current ground-layout specification. Current shell/navigation/layout authority is `UI_FOUNDATION_LAYOUT.md`, with `TUNING_TOOLS_FOUNDATION.md` defining the native guided-tool surface.

Visual authority and implementation evidence remain deliberately separated.

### Visual / interaction authority

The accepted EpicDash-JZ tablet/phone reference family remains strong evidence for the intended Tuner language, especially:

- `PJawZK/EpicDash-JZ/docs/UI_SPEC.md`;
- `PJawZK/EpicDash-JZ/docs/TUNER_UI_SIDEQUEST.md`.

`PJawZK/Epic-DashTune-UXUI` is interaction/responsive/performance evidence only. Its WebView/JavaScript/HTML/SVG implementation and visual theme/layout structure are not migrated as EpicHub runtime architecture.

User-authored HTML/CSS prototypes created specifically for EpicHub may be used as rapid visual/layout references. Once explicitly accepted, such a prototype/screenshot family may become visual/layout authority for the corresponding screen, but production implementation is translated into Compose over typed Kotlin/Rust state. See D-023.

D-022 requires clean reimplementation; D-023 keeps production runtime presentation in Kotlin/Jetpack Compose; D-024 keeps historical tuning-plugin capabilities as native Tuning Tools rather than a plugin/WebView subsystem.

---

# Current high-level presentation contract

The current first-class EpicHub functional destinations are:

```text
Dashboard | Tuner | Tuning Tools | Logger | Analyzer | Diagnostics
```

`Settings` is a global application surface rather than a peer work destination.

Compact navigation does not have to display six tiny permanent buttons. `UI_FOUNDATION_LAYOUT.md` owns the responsive navigation arrangement; every destination must remain deliberately reachable without creating unusable touch targets.

The older EpicDash-JZ shell:

```text
Dash | Tuner | Logging | Diagnostics
```

is historical interaction evidence only.

Top-level navigation remains separate from Dashboard page swipe/order.

Tuner hierarchy remains profile-driven:

```text
System → Category → Feature
```

Normal manual Tuner content modes remain:

```text
Parameters | Tables | Curves
```

Tuning Tools is a separate guided-workflow destination. Analyzer remains the generic data-exploration workspace.

---

# One responsive component/data model

Historical EpicDash-JZ/UXUI evidence supports the current rule:

> Use one semantic/component model and reflow it by available geometry. Do not create separate phone/tablet application implementations.

Transferable behavior:

- tablet landscape may keep persistent navigation/secondary regions;
- tablet portrait and phone layouts replace persistent rails with compact selectors/navigation;
- secondary panels move below/aside the primary workspace instead of shrinking it until unusable;
- phone layouts expose essential content and suppress redundant controls;
- horizontal scroll is preferable to compressing telemetry/navigation beyond legibility;
- orientation/resize recomputes presentation geometry without changing domain authority.

Responsive policy is driven by measured available geometry/window classes/content constraints, not hardcoded device model names.

---

# Responsive state is presentation state

Orientation/window-size changes may alter:

- rail versus compact navigation presentation;
- panel placement;
- visible secondary cards;
- local zoom/viewport bounds;
- toolbar wrapping;
- telemetry density.

They must not implicitly alter:

- selected semantic tune target;
- local semantic draft values;
- verified TuneSnapshot;
- stored table orientation/data order;
- ECU session/profile identity;
- analysis-session identity;
- write/Burn authority.

Keep semantic selection stable across geometry changes wherever practical; recompute presentation only.

---

# Active-surface rendering only

UXUI and EpicDash-JZ independently support the same optimization: inactive destinations should not consume high-rate rendering work merely because runtime values changed.

Compose consequence:

- one authoritative engine snapshot source;
- screen/ViewModel projections select only required values;
- lifecycle-aware collection;
- active-route update/render policy;
- stable immutable UI models;
- change detection before expensive custom drawing.

The accepted foundation now provides RuntimeSnapshot ABI v1 over a reusable direct buffer. That proves the typed snapshot pattern on SM-A137F and SM-T500; it does not justify sample-rate recomposition or one JNI getter per channel.

---

# Snapshot/stale-state lesson

Historical UXUI work rejected retired/stale revisions and prevented an existing session from silently changing source identity.

Current EpicHub consequence:

- Rust owns ChannelStore/source/session validity;
- Kotlin receives immutable typed/versioned display projections;
- no JavaScript-style or JSON-backed telemetry authority exists in Kotlin;
- presentation may retain last-displayed values for transition/diagnostic purposes, but live/connected/fresh indicators follow authoritative source/session state;
- old live values after disconnect are not current truth.

---

# Dashboard rendering performance

Historical WebView/SVG optimization showed that static artwork should be built once and runtime changes should update only the smallest dynamic layer.

Its recorded synthetic benchmark was specific to that historical renderer and is **not** a Compose forecast.

Transferable rule:

```text
static geometry → construct/cache once
runtime numeric state → update only dynamic layer
unchanged visual result → skip state/draw work
```

Compose gauges/plots should cache static paths/text where safe, avoid rebuilding static geometry for one changed value, quantize only presentation coordinates when visually harmless, and be measured on target hardware.

---

# Dashboard presentation settings

Useful candidates include:

- long-press gauge display settings;
- per-gauge presentation min/max;
- local decimal precision override;
- globally consistent unit/format preferences;
- no-signal state retaining known configured units;
- suppression of accidental text-selection/context-menu behavior.

These are presentation settings only and never alter ChannelStore/log truth.

---

# Table semantic state versus presentation state

Rust/profile/tuning owns or validates:

- table identity/dimensions;
- X/Y/Z semantic arrays;
- verified baseline/source values;
- local semantic draft/history;
- resolved semantic cell indices;
- edit-operation math/constraints where retained;
- comparison against authoritative baseline;
- write-plan compilation and ECU verification.

Compose owns:

- viewport zoom/pan/scroll;
- visible rows/columns;
- sticky headers;
- touch gesture recognition;
- focused cell/gesture phase;
- local display orientation;
- heat-map rendering;
- panel/focus/fullscreen presentation;
- 3D camera state;
- modal/pointer capture.

Reversing X or Y changes display order only. It never rearranges stored tune arrays or semantic cell identity.

---

# Table selection and editing

Historical evidence supports a semantic selection model independent of gestures:

```text
TableSelection
├─ cells / ranges
├─ row bands
├─ column bands
└─ normalized resolved cell indices
```

Gesture phase is separate and must explicitly arbitrate range-drag versus pinch/zoom.

Candidate editing operations include direct cell edit, arithmetic/fill, smoothing/interpolation, copy/paste, undo/redo, baseline revert and CSV import/export. These remain product-scope candidates unless separately accepted.

Correct architecture:

```text
Compose gesture/toolbar
→ semantic TableEditCommand
→ Rust tuning model validates selection/operation
→ semantic draft changes
→ eventual verified write plan
```

No toolbar operation gets its own raw ECU write path.

Undo/redo is semantic draft history, not automatic ECU rollback.

---

# Baseline / draft / live state

EpicHub distinguishes saved project, live verified TuneSnapshot, local draft, verified RAM dirty state, Burn persistence state and uncertainty.

A generic `changed` marker is insufficient once these can diverge. Presentation must identify the states being compared.

---

# 3D and 2D table visualization

3D inspection remains view-only unless a separately accepted semantic edit contract is added.

Useful historical interactions include pitch/yaw/zoom/reset, fullscreen inspection, pointer capture and stable presets. Camera state is presentation-only.

2D live cursor/trace is a bounded, display-rate projection of runtime channels into table-axis space. It is not tune state, and one semantic owner maps channels to axes.

---

# Curves and parameters

Curves may show multiple compatible overlays but exactly one explicit editing owner. Incompatible axes are not presented as equivalent.

Parameter controls consume typed profile metadata/state; they do not parse raw INI strings or calculate page/offset/bytes in Compose.

Focus/text-entry state remains Kotlin presentation state; committed semantic values become Rust tuning-draft operations.

---

# Panel, modal and haptic behavior

Secondary panels may fold/reflow without reconstructing semantic state. Compact layouts may omit redundant fold controls.

Permanent interaction rule:

> A modal or fullscreen editor/inspection surface owns its pointer stream for the duration of the gesture.

Haptics are presentation feedback only and never imply hardware write/Burn success before verified domain results.

---

# Tuning Tools evidence consequence

Historical AE/PID/TCharge/Injection-Angle plugin UIs are workflow evidence, not mini-app templates.

Current consequence:

- Tuning Tools is visible in the ground shell under D-024;
- Compose owns tool-selection/guided-workflow presentation;
- Rust owns shared analysis primitives and domain profiles/models;
- Analyzer supplies/reuses generic plots/statistics/query infrastructure;
- tool results may become semantic recommendations/proposals but never independent write/Burn authority;
- no dynamic plugin/JAR/WebView architecture is retained.

Detailed authority lives in `TUNING_TOOLS_FOUNDATION.md`.

---

# HTML prototype consequence

HTML/CSS prototypes are useful because visual iteration is much cheaper than rebuilding the full Android application for every design change.

When a Dashboard, Logger or other EpicHub prototype is explicitly accepted:

```text
prototype visual/interaction intent
→ documented/accepted authority
→ Compose implementation
→ physical phone/tablet comparison
```

The prototype may supply spacing, hierarchy, component appearance, responsive intent and interaction examples. It does not supply production domain ownership, persistence, transport, safety logic or browser runtime authority.

---

# Lifecycle / rotation preservation

Preserve across resize/rotation where meaningful:

- selected primary route;
- selected Tuner hierarchy/target;
- selected Tuning Tool and compatible in-progress presentation state;
- semantic table selection;
- local draft;
- active curve identity;
- user panel/focus/display preferences.

Recompute from geometry:

- rail vs compact navigation;
- parameter columns;
- panel placement;
- visible table cells;
- toolbar wrapping;
- plot/table transforms;
- 3D viewport.

Do not serialize/reconstruct complete workspaces merely because Android rotated.

---

# Performance ownership

Rust/background work includes large semantic transforms, shared channel/analysis math, downsampling and persisted-data/schema work.

Compose/UI-thread work includes pointer arbitration, layout, small projections, prepared canvas/path rendering and feedback/animation.

Avoid:

- giant JSON workspace construction;
- JSON/browser message buses;
- rebuilding unchanged semantic models on every snapshot;
- recalculating static artwork per sample;
- updating inactive pages at runtime cadence;
- one native call/subscription per widget/channel when a coarse projection is enough.

---

# Current evidence conclusions

1. **Rust owns tuning/runtime semantic truth; Compose owns interaction/presentation state.**
2. **Responsive design is one semantic/component model with geometry-driven reflow.**
3. **Current navigation is defined by `UI_FOUNDATION_LAYOUT.md`; old shells are evidence only.**
4. **Tuning Tools is native EpicHub functionality under D-024, not a plugin runtime.**
5. **Accepted HTML prototypes may become visual/layout authority but are translated to Compose.**
6. **Semantic selections/edit commands are separate from gesture state.**
7. **X/Y reversal, zoom, pan, panels, camera state and heat maps are presentation only.**
8. **3D remains view-only unless separately accepted for editing.**
9. **Only active surfaces consume high-rate display updates.**
10. **Static geometry is cached; runtime rendering changes the smallest useful dynamic layer.**
11. **Modal pointer streams cannot leak to underlying actions.**
12. **Haptics/animation never imply hardware success before verified results.**
13. **Offline/project/live/draft/Burn/uncertain states remain explicitly distinguishable.**
14. **Runtime UI data remains typed/packed Kotlin↔Rust state under D-023, not JSON/JavaScript state.**