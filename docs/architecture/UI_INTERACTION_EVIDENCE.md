# UI Interaction / Responsive / Performance Evidence

## Status

This document records the accepted interaction/responsive/performance evidence gathered during Phase 1 and explains how it applies to the current Compose architecture.

It is **evidence**, not the current ground-layout specification. The active EpicHub shell/navigation/layout contract is `UI_FOUNDATION_LAYOUT.md`.

Visual authority and implementation evidence remain deliberately separated.

### Visual / interaction authority

The accepted EpicDash-JZ tablet/phone reference family remains strong visual/interaction evidence for the intended Tuner language, especially:

- `PJawZK/EpicDash-JZ/docs/UI_SPEC.md`;
- `PJawZK/EpicDash-JZ/docs/TUNER_UI_SIDEQUEST.md`.

`PJawZK/Epic-DashTune-UXUI` is an interaction/responsive/performance reference only. Its WebView/JavaScript/HTML/SVG implementation and its visual theme/layout structure are not migrated as EpicHub architecture.

D-022 requires clean reimplementation; D-023 keeps production runtime presentation in Kotlin/Jetpack Compose with typed/packed Rust integration.

---

# Current high-level presentation contract

The current first-class EpicHub destinations are:

```text
Dashboard | Tuner | Logger | Analyzer | Diagnostics
```

`Settings` is a global application surface rather than a sixth high-frequency work destination.

The older EpicDash-JZ four-destination shell:

```text
Dash | Tuner | Logging | Diagnostics
```

is historical interaction evidence only and no longer overrides `UI_FOUNDATION_LAYOUT.md`.

Top-level navigation remains separate from Dashboard page swipe/order.

Tuner hierarchy is derived from the exact accepted INI/profile:

```text
System → Category → Feature
```

Normal Tuner content modes remain:

```text
Parameters | Tables | Curves
```

The UI does not invent a firmware-specific taxonomy when the authoritative profile supplies it. Condition-hidden/disabled/unsupported items remain governed by profile condition authority below presentation.

---

# One responsive component/data model

Historical EpicDash-JZ/UXUI evidence supports the current rule:

> Use one semantic/component model and reflow it by available geometry. Do not create separate phone/tablet application implementations.

Transferable behavior:

- tablet landscape may keep persistent navigation/secondary regions;
- tablet portrait and phone layouts replace persistent rails with compact selectors/bottom navigation;
- secondary panels move below/aside the primary workspace instead of shrinking it until unusable;
- phone layouts expose essential content and suppress redundant controls;
- horizontal scroll is preferable to compressing telemetry/navigation beyond legibility;
- orientation/resize recomputes presentation geometry without changing domain authority.

Responsive policy is driven by measured available geometry/window classes/content constraints, not hardcoded device model names.

A Tuner layout model may eventually expose concepts such as:

```text
TunerLayoutProfile
├─ systemNavigation: PersistentRail | CompactSelector
├─ hierarchyArrangement: Row | Wrapped | Stacked
├─ editorPriority: Dominant
├─ secondaryPanelPlacement: Side | Below | HiddenByUser
├─ telemetryLayout: Full | Compact | HorizontalScroll
└─ compactTouchMode: Boolean
```

The exact Kotlin type is an implementation detail; the ownership boundary is already locked.

---

# Responsive state is presentation state

Orientation/window-size changes may alter:

- rail versus selector/bottom-nav presentation;
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
- write/Burn authority.

Keep semantic selection stable across geometry changes wherever practical; recompute presentation only.

---

# Active-surface rendering only

UXUI proved an important optimization by forwarding runtime snapshots only to the active page. EpicDash-JZ independently reached the same requirement.

Compose consequence:

- one authoritative engine snapshot source;
- screen/ViewModel projections select only required values;
- lifecycle-aware collection;
- active-route update/render policy;
- stable immutable UI models;
- change detection before expensive custom drawing.

Inactive destinations may preserve interaction state without consuming high-rate rendering work.

The accepted foundation now provides a versioned packed RuntimeSnapshot ABI v1 over a reusable direct buffer. That foundation proves the typed snapshot pattern on SM-A137F and SM-T500; it does not justify sample-rate recomposition or one JNI getter per channel.

---

# Snapshot acceptance / stale-state lesson

Historical UXUI state work rejected retired/stale revisions and prevented an existing session from silently changing source identity.

Current EpicHub consequence:

- Rust owns ChannelStore/source/session validity;
- Kotlin receives immutable typed/versioned display projections;
- no JavaScript-style or JSON-backed telemetry authority exists in Kotlin;
- presentation may retain last-displayed values for transition/diagnostic purposes, but live/connected/fresh indicators follow authoritative source/session state;
- old live values after disconnect are not current truth.

---

# Dashboard rendering performance

## Static versus dynamic geometry

UXUI r20 used hundreds of individually updated SVG segments/filters/gradients/transitions. r21 constructed static artwork once and changed only the pointer transform when the quantized visual angle actually changed.

Its recorded synthetic benchmark for 12,000 updates reported:

- r20 median: 175 ms;
- r21 median: 17 ms;
- reported speedup: ~10.29×;
- reported update-work reduction: ~90.3%.

Those figures are specific to the historical WebView/SVG implementation and are **not** Compose performance forecasts.

Transferable rule:

```text
static geometry → construct/cache once
runtime numeric state → update only dynamic layer
unchanged visual result → skip state/draw work
```

Compose gauges/plots should:

- separate static ticks/zones/labels from dynamic value/pointer/validity layers;
- cache expensive paths/text geometry where safe;
- avoid rebuilding static path lists because one value changed;
- quantize only presentation coordinates where visually harmless, never underlying ChannelStore/log truth;
- avoid decorative paint/compositing cost without useful information;
- benchmark on reference hardware instead of assuming Compose removes render cost.

---

# Dashboard presentation settings

Useful feature candidates from UXUI include:

- long-press gauge display settings;
- per-gauge presentation min/max;
- local decimal precision override;
- globally consistent units/format preferences;
- no-signal state retaining known configured units;
- suppressing accidental text-selection/context-menu behavior on instrument surfaces.

These are presentation settings only and never alter decoded channel truth or recorded samples.

---

# Table semantic state versus presentation state

Rust/profile/tuning owns or validates:

- table identity/dimensions;
- X/Y/Z semantic arrays;
- verified baseline/source values;
- local semantic draft/history;
- resolved semantic cell indices;
- edit-operation math/constraints where product scope includes it;
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

### Orientation invariant

Reversing X or Y in presentation changes **display order only**. It never rearranges stored tune arrays or changes semantic cell identity.

---

# Table selection model

Historical evidence includes both explicit modes and gesture-first selection:

- Single;
- Multi;
- corner-defined Range;
- row/column/all/clear;
- tap select;
- long-press + drag rectangular range;
- double-tap edit/focus;
- pointer/mouse drag where available.

EpicHub keeps the semantic selection model stable while presentation gestures may evolve.

Conceptually:

```text
TableSelection
├─ cells / ranges
├─ row bands
├─ column bands
└─ normalized resolved cell indices
```

Gesture phase is separate:

```text
TableGestureState
├─ Idle
├─ PressPending
├─ RangeDrag
├─ PinchZoom
└─ EditingCell
```

Multi-touch recognizers require explicit arbitration/rollback. A second touch beginning pinch zoom must not leave a half-applied range selection.

---

# Table editing operations

UXUI demonstrated candidates including:

- direct cell edit;
- selection-value entry;
- row/column/table selection;
- Add/Subtract/Multiply/Divide/Fill/Flatten;
- 3×3 smoothing;
- row/column interpolation;
- bilinear rectangle interpolation;
- copy/paste;
- undo/redo;
- baseline revert;
- CSV import/export.

These remain feature candidates, not automatically accepted product scope.

Correct architecture:

```text
Compose gesture/toolbar
→ semantic TableEditCommand
→ Rust tuning model validates selection/operation
→ semantic draft changes
→ eventual verified write plan
```

No toolbar operation gets its own raw ECU write path.

Operations that mathematically require a contiguous range reject non-contiguous selection with an explicit reason.

---

# Undo / redo

Undo/redo is semantic **draft edit history**, not ECU rollback.

Undoing a local edit does not imply compensating ECU bytes. Hardware changes occur only through another explicit verified tuning transaction.

---

# Baseline / draft / live state in tables

EpicHub distinguishes:

- saved project TuneSnapshot;
- live verified TuneSnapshot;
- local semantic draft;
- verified RAM dirty state;
- Burn dirty/persistence state;
- uncertainty journal/state.

A generic `changed` marker becomes ambiguous once offline draft and live RAM can diverge. UI comparison must identify the two states being compared.

Useful conceptual distinctions include:

- `DraftChangedFromProject`;
- `DraftChangedFromLiveBaseline`;
- `VerifiedRamChangedFromSavedProject`;
- `BurnDirtyPage`;
- `UncertainAfterMutation`.

Exact wording/colors remain UI design work.

---

# 3D table visualization

Current locked rule:

- 3D inspection is view-only unless a separately accepted edit contract is added.

Useful historical interactions:

- small preview → fullscreen inspection;
- pitch/yaw/zoom/reset;
- touch/drag/pinch camera control;
- stable view presets;
- modal pointer capture so underlying controls do not fire;
- no need to duplicate a useful 2D live trace in 3D.

Compose consequence:

- 3D camera state is Kotlin presentation state;
- renderer consumes immutable axes/table values;
- it never becomes a second tune model/edit path;
- start with the simplest renderer that meets target-device responsiveness; specialized GPU work requires measurement evidence.

---

# 2D live trace

Transferable principles from the bounded historical trace:

- live cursor/trace projects current runtime channels into semantic table-axis space;
- history is bounded;
- display cadence is throttled to visual usefulness rather than ECU sample rate;
- trace history is not tune state;
- one semantic owner maps runtime channels to table coordinates so multiple renderers do not independently reinterpret mapping.

Whether EpicHub keeps a trail, cursor only or both remains product scope.

---

# Curves

Current interaction contract:

- Single: one active editable curve;
- Multi: multiple compatible visible curves with exactly one editing owner;
- active owner comes from explicit curve-manager state, not ambiguous line hit-testing;
- incompatible axes are not overlaid as if scales were equal;
- point/range edits use the same semantic array-cell authority.

Rust/profile/tuning owns identities/axis/value semantics/compatibility/current-draft values/edit commands.

Compose owns viewport, visible overlays, active styling and cursor/selection gesture state.

---

# Parameters

Current contract:

- selected INI dialog renders directly in Tuner content;
- multi-column fields where width permits, one-column flow when narrow;
- disabled controls communicate why unavailable;
- editing remains semantic.

Compose field components consume typed metadata/state. They never parse raw INI strings or calculate physical page/offset/bytes.

Focus/text-entry state stays in Kotlin; committed semantic values become Rust tuning-draft operations.

---

# Panel folding / focus / fullscreen

Useful behavior:

- secondary panels may fold independently on wide layouts;
- folding causes deterministic workspace reflow;
- focus mode prioritizes the editor while keeping essential navigation reachable;
- fullscreen dedicates the viewport to the primary editor;
- compact layouts may omit redundant fold controls and simply stack required content.

Panel visibility is local presentation state and does not rebuild semantic tuner state.

---

# Pointer / modal ownership

Permanent interaction rule:

> A modal or fullscreen editor/inspection surface owns its pointer stream for the duration of the gesture.

Closing/tapping/dragging a modal must not trigger semantic actions beneath it.

High-consequence actions can have additional guards, but ordinary table/curve navigation does not inherit awkward safety gestures merely because the historical WebView had pointer leakage.

---

# Haptics

Useful evidence:

- haptics can improve cell/range selection feedback;
- disabled controls do not emit success-like feedback;
- device/system haptic preferences remain authoritative.

Haptics are presentation feedback only and never imply ECU write/Burn success before native verification.

---

# Changed-item / favorites / presets concepts

Potential reusable product patterns:

- `Changes` list deep-linking semantic items changed from an explicitly named baseline;
- Quick Tuning/favorites as navigation shortcuts, not alternate definitions;
- logging channel presets as user configuration;
- intuitive tap-load / long-press-edit behavior where retained.

Use stable semantic IDs, never visual row indices or old DOM element IDs.

---

# Lifecycle / rotation state preservation

Preserve across ordinary resize/rotation where meaningful:

- selected primary route;
- selected System/Category/Feature;
- selected table/curve/parameter target;
- semantic table selection;
- local draft;
- active curve identity;
- user panel/focus/display preferences.

Recompute from geometry:

- rail vs bottom/compact navigation;
- parameter column count;
- secondary-panel placement;
- visible table cells;
- toolbar wrapping;
- plot/table pixel transforms;
- 3D viewport.

Do not serialize/reconstruct the complete tuning workspace merely because Android rotated.

---

# Performance ownership

## Rust / background computation

Appropriate examples:

- large semantic tune transforms;
- large table-operation math where profiling justifies it;
- shared live channel→axis mapping;
- analysis/downsampling;
- persisted data/schema work.

## Compose/UI thread

Appropriate examples:

- pointer gesture arbitration;
- layout;
- small presentation projections;
- canvas/path rendering from prepared values;
- animations/haptics.

## Avoid

- giant JSON workspace construction;
- JSON/browser message buses;
- rebuilding unchanged semantic models on every snapshot;
- recalculating static artwork per sample;
- updating inactive pages at runtime cadence;
- one native call/Flow per widget/channel when a coarse snapshot projection is enough.

D-023 makes the first two explicit architecture exclusions, not merely performance preferences.

---

# Current evidence conclusions

The evidence is sufficient to keep these permanent rules:

1. **Rust owns tuning/runtime semantic truth; Compose owns interaction/presentation state.**
2. **Responsive design is one semantic/component model with geometry-driven reflow.**
3. **Current primary navigation is defined by `UI_FOUNDATION_LAYOUT.md`; older shells are historical evidence only.**
4. **Visual/Tuner behavior may be informed by accepted EpicDash-JZ references without migrating their WebView implementation.**
5. **Semantic table/curve selections and edit commands are separate from touch gesture state.**
6. **X/Y reversal, zoom, pan, panel folds, camera state and heat maps are presentation only.**
7. **3D remains view-only unless a future accepted edit contract says otherwise.**
8. **Only active presentation surfaces consume high-rate display updates.**
9. **Static geometry is cached; runtime rendering changes the smallest useful dynamic layer.**
10. **Modal pointer streams cannot leak to underlying actions.**
11. **Haptics/animation never imply hardware success before verified domain results.**
12. **Offline/project/live/draft/Burn/uncertain states remain explicitly distinguishable.**
13. **Undo/redo acts on local semantic edits, not implicit ECU rollback.**
14. **Runtime UI data remains typed/packed Kotlin↔Rust state under D-023, not JSON/JavaScript state.**

Remaining questions are product-level interaction scope and measured Compose performance/ergonomics, not ownership ambiguity.