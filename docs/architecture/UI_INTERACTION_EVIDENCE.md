# UI Interaction / Responsive / Performance Evidence

## Status

Phase 1 source archaeology for the clean-slate Compose presentation architecture.

Visual authority and implementation evidence are deliberately separated.

### Visual/layout authority

The accepted EpicDash-JZ tablet/phone reference family and current presentation contracts remain authoritative for the intended EpicHub Tuner visual/layout language.

Primary current contracts reviewed:

- `PJawZK/EpicDash-JZ/docs/UI_SPEC.md`;
- `PJawZK/EpicDash-JZ/docs/TUNER_UI_SIDEQUEST.md`.

### Interaction/performance reference only

`PJawZK/Epic-DashTune-UXUI` is reviewed for proven features, interaction rules, responsive behavior and performance techniques.

Its visual theme/layout language is **not** imported into EpicHub simply because a behavior was proven there.

GitHub currently reports no declared repository license for that reference repository, so code transplantation is not assumed. Concepts/behavior can be reimplemented cleanly.

---

# Locked high-level presentation contract

Top-level application destinations remain conceptually:

```text
Dash | Tuner | Logging | Diagnostics
                   + Settings
```

Top-level navigation is separate from Dashboard page swipe/order.

Tuner hierarchy is derived from the exact accepted INI/profile:

```text
System → Category → Feature
```

Normal Tuner content modes:

```text
Parameters | Tables | Curves
```

The application must not invent a firmware-specific navigation taxonomy when the current INI supplies the hierarchy.

Condition-hidden/disabled/unsupported items remain governed by profile condition authority below the UI layer.

---

# One responsive component/data model

The current EpicDash-JZ contract and UXUI responsive work independently support the same architectural rule:

> Use one semantic/component model and reflow it by available geometry. Do not create separate phone/tablet application implementations.

Reference behavior:

- tablet landscape may keep persistent navigation/secondary regions;
- tablet portrait and phone layouts replace persistent rails with compact selectors;
- secondary panels move below/aside the main calibration surface instead of shrinking the editor until unusable;
- phone layouts expose essential content and suppress redundant panel-hide/focus controls;
- horizontal scroll is preferable to compressing compact telemetry/navigation beyond legibility;
- orientation/resize recomputes a presentation profile without changing tune/runtime authority.

## Compose consequence

Responsive policy should be based on measured available width/height/window size classes and content constraints, not hardcoded device model names.

A provisional presentation model may expose something like:

```text
TunerLayoutProfile
├─ systemNavigation: PersistentRail | CompactSelector
├─ hierarchyArrangement: Row | Wrapped | Stacked
├─ editorPriority: Dominant
├─ secondaryPanelPlacement: Side | Below | HiddenByUser
├─ telemetryLayout: Full | Compact | HorizontalScroll
└─ compactTouchMode: Boolean
```

The exact type is Phase 2 work; the responsibility boundary is Phase 1 authority.

---

# Responsive state is presentation state, not domain state

Orientation/window-size changes may alter:

- rail versus selector presentation;
- panel placement;
- visible secondary cards;
- local zoom/viewport bounds;
- toolbar wrapping;
- telemetry density.

They must **not** implicitly alter:

- selected semantic tune target;
- local draft values;
- verified TuneSnapshot;
- table stored orientation/data order;
- ECU session/profile identity;
- write/Burn authority.

Where possible, keep the semantic selection stable across rotation and recompute only its presentation geometry.

---

# Active-surface rendering only

UXUI's integration `render-coordinator.js` forwards runtime snapshots only to the active page. The current EpicDash-JZ UI spec independently states to render only active content where practical.

## Compose consequence

Do not drive all top-level destinations with high-rate recomposition simply because one native runtime snapshot changes.

Use:

- one engine snapshot source;
- screen/view-model projections selecting only required channels/fields;
- lifecycle-aware collection;
- active-route subscription/render policy;
- stable immutable UI models;
- change detection before expensive custom drawing.

Inactive pages may preserve interaction state without consuming high-rate render work.

---

# Snapshot acceptance / stale-state lesson

UXUI's isolated state store accepts snapshots only when source/session/revision facts are valid, rejects retired/stale revisions and prevents an existing session from changing source identity silently.

This mirrors the stronger native/session rules already adopted in EpicHub.

## Compose consequence

Do not maintain an independent JavaScript-style telemetry authority in Kotlin. Rust owns ChannelStore/session validity; Kotlin receives immutable versioned display snapshots.

Presentation state may retain the last displayed values for transition/diagnostics, but connected/live indicators must follow authoritative source/session/freshness state. Old live values after disconnect are not current truth.

---

# Dashboard rendering performance

## Static versus dynamic geometry

UXUI r20 used hundreds of individually updated SVG zone segments, filters, gradients and transitions. r21 changed the design so static artwork was constructed once and each runtime update changed at most one pointer transform per gauge when the quantized visual angle actually changed.

The recorded synthetic benchmark for 12,000 updates reported:

- r20 median: 175 ms;
- r21 median: 17 ms;
- reported speedup: ~10.29×;
- reported update-work reduction: ~90.3%.

This benchmark is specific to that WebView/SVG implementation and must **not** be treated as a Compose performance forecast.

The transferable principle is:

```text
static visual geometry → construct/cache once
runtime numeric state → update only dynamic layer
unchanged visual result → skip draw/state work
```

## Compose consequence

For custom gauges/plots:

- separate static ticks/zones/labels from dynamic pointer/value/validity layers;
- cache expensive path/text geometry where Compose APIs allow it safely;
- avoid rebuilding path lists because one numeric value changed;
- quantize only presentation coordinates when visually harmless; never quantize/log underlying channel truth;
- avoid decorative effects that create measurable paint/compositing cost without useful information;
- benchmark on low/mid target hardware rather than assuming Compose removes rendering cost.

---

# Dashboard presentation settings

Useful interaction concepts from UXUI that may be carried forward if product scope retains them:

- long-press a gauge to open local display settings;
- per-gauge presentation min/max;
- optional local decimal precision overriding a global presentation preference;
- globally consistent unit/format settings;
- no-signal state still shows known configured units;
- suppress accidental text selection/context-menu style interactions on an instrument surface.

These are presentation settings only. They never alter the raw/decoded channel truth in ChannelStore or logs.

---

# Table semantic state versus presentation state

The current locked Tuner contract and UXUI table work provide a strong split.

## Rust/tuning semantic ownership

Rust owns or validates:

- table identity and dimensions;
- X/Y/Z semantic arrays;
- current verified baseline/source values;
- local semantic draft values and edit history;
- selection translated into exact semantic cell indices for edit commands;
- Add/Subtract/Multiply/Divide/Fill/Flatten semantics if those become product features;
- smoothing/interpolation semantics and validity constraints;
- undoable semantic edit commands where persistence/correctness benefits from native ownership;
- comparison against authoritative baseline;
- write-plan compilation and ECU verification.

## Compose presentation/interaction ownership

Compose should own:

- viewport zoom/pan/scroll;
- visible row/column range;
- sticky header presentation;
- touch gesture recognition;
- currently focused cell/selection gesture phase;
- local display orientation (reverse X/Y display order);
- heat-map rendering;
- folded/focus/fullscreen panel presentation;
- 3D camera/pitch/yaw/zoom;
- modal/pointer capture.

### Orientation invariant

Reversing X or Y in presentation changes **display order only**. It must not rearrange the stored tune arrays or change semantic cell identity.

---

# Table selection model

Useful interaction evidence exists in two different forms:

UXUI earlier tuning work exposed explicit touch modes:

- Single;
- Multi;
- Range via two opposite corners;
- row/column/all/clear.

Later UXUI table interaction also proved a touch-first gesture language:

- tap → select;
- long-press + drag → rectangular range;
- double-tap → edit/focus;
- mouse drag retained where available.

The current EpicDash-JZ contract requires row/column/range/all/clear semantic-array selection, not one exact gesture implementation.

## EpicHub decision direction

Keep the **semantic selection model** stable and allow presentation gestures/modes to evolve around it.

Suggested semantic shape:

```text
TableSelection
├─ cells / ranges
├─ row bands
├─ column bands
└─ normalized resolved cell indices
```

Gesture state should be separate:

```text
TableGestureState
├─ Idle
├─ PressPending
├─ RangeDrag
├─ PinchZoom
└─ EditingCell
```

### Multi-touch conflict lesson

UXUI r28 explicitly separates long-press selection from pinch zoom: a second touch cancels/restores any pending range selection before zoom begins.

That is a useful general rule for Compose pointer handling: simultaneous gesture recognizers must have explicit arbitration/rollback instead of both mutating selection/viewport state.

---

# Table editing operations

UXUI demonstrated a richer local table editor including:

- direct cell edit;
- exact selection-value entry;
- row/column/table selection;
- Add/Subtract/Multiply/Divide/Fill/Flatten;
- 3×3 smoothing;
- row/column linear interpolation;
- bilinear rectangle interpolation;
- copy/paste;
- undo/redo;
- baseline revert;
- CSV text import/export.

These are **feature candidates**, not automatically locked EpicHub scope.

However they reveal the right architecture:

```text
Compose gesture/toolbar
→ semantic TableEditCommand
→ tuning model validates selection/operation
→ draft changes
→ same verified write plan used for eventual ECU RAM write
```

Do not implement each toolbar operation as an independent raw ECU write path.

Non-contiguous selections should be rejected for operations that mathematically require a contiguous row/column/rectangle, with an explicit user-facing reason.

---

# Undo / redo

Undo/redo is user-edit history, not ECU rollback.

It should operate over semantic draft edit commands/snapshots. Undoing a local edit does not imply sending compensating ECU bytes unless the user explicitly performs another verified RAM write.

This distinction should remain visible once live RAM writes and local drafts can diverge.

---

# Baseline / draft / live state in tables

UXUI comparison deliberately compared bundled baseline against local draft rather than pretending the baseline was live ECU memory.

EpicHub has stronger authority categories:

- saved project TuneSnapshot;
- live verified TuneSnapshot;
- local draft;
- write/Burn dirty state;
- uncertainty journal.

Table highlighting must name/encode **which two states are being compared**. A generic `changed` marker is ambiguous once offline draft and live RAM both exist.

Recommended conceptual distinctions:

- `DraftChangedFromProject`
- `DraftChangedFromLiveBaseline`
- `VerifiedRamChangedFromSavedProject`
- `BurnDirtyPage`
- `UncertainAfterMutation`

Exact UI wording/colors remain visual/product design work.

---

# 3D table visualization

Locked EpicDash-JZ contract:

- 3D inspection is view-only unless separately accepted for editing.

UXUI provides useful interaction evidence:

- small preview can open a fullscreen inspection surface;
- pitch/yaw/zoom/reset;
- touch/drag/pinch camera interactions;
- stable view presets such as Front/Back/Left/Right/Top/Bottom;
- pointer events inside the modal must be fully captured so underlying navigation/actions do not fire;
- no live trace needed in 3D if 2D already owns the useful live trace.

## Compose consequence

Treat 3D camera state as presentation-only Kotlin state. The surface consumes immutable table values/axes. It must not become an alternate tune model or edit path.

Start with the simplest renderer that can maintain responsive rotation on target devices; benchmark before choosing a custom GPU stack.

---

# 2D live trace

UXUI used a bounded 2D trace (24 positions) at a throttled rate and intentionally avoided duplicating it in 3D.

Transferable principles:

- live cursor/trace is a presentation projection of current runtime channels into table-axis space;
- bound history length;
- throttle to visually useful cadence, not ECU sample rate;
- do not make trace history part of tune state;
- one owner computes/matches the semantic table coordinates; multiple renderers should not independently reinterpret channel-to-axis mapping.

Whether EpicHub keeps a trail, only a live cursor or both is product/UI scope.

---

# Curves

Locked current contract:

- Single: one active editable curve;
- Multi: multiple compatible visible curves, exactly one editing owner;
- active owner comes from explicit curve-manager state, not ambiguous line hit-testing;
- incompatible axes are not overlaid as if scales were equal;
- point/range edits use the same semantic array-cell authority.

## State split

Rust/profile/tuning:

- curve identities;
- axis/value semantics;
- compatibility;
- current/draft values;
- semantic edit commands.

Kotlin/Compose:

- viewport;
- visible overlays;
- active display styling;
- cursor/selection gesture state.

---

# Parameters

Locked contract:

- selected INI dialog renders directly in Tuner content area;
- compact multi-column fields where width permits, one-column flow when narrow;
- disabled controls communicate why unavailable;
- editing remains semantic.

## Compose consequence

Parameter field components consume typed field metadata/state. They never parse raw INI strings or calculate physical page/offset/byte writes.

Focus/text-entry/validation presentation state stays in Kotlin; a committed semantic requested value becomes a Rust tuning-draft operation.

---

# Panel folding / focus / fullscreen

UXUI showed useful interaction behavior for complex tool pages:

- side/bottom panels can fold independently on large layouts;
- folding triggers an explicit workspace reflow/redraw;
- focus mode prioritizes the editor while keeping essential navigation reachable;
- fullscreen dedicates the viewport to the primary editor;
- phone layouts may omit redundant fold controls and simply stack/show all required content.

## Compose consequence

Panel visibility is local presentation preference/state. Layout reflow should derive deterministically from it and current window geometry; panel visibility must not force reconstruction of the semantic tuner model.

---

# Pointer / modal ownership

UXUI found real bugs where fullscreen 3D pointer/tap handling could leak through to controls/navigation beneath it.

Permanent interaction rule:

> A modal or fullscreen editor/inspection surface owns its pointer stream for the duration of the gesture.

Compose implementation must ensure closing/tapping/dragging a modal cannot trigger an underlying semantic action.

High-consequence actions deserve additional guard behavior, but ordinary table/curve navigation should not require safety gestures merely because the old WebView had pointer leakage.

---

# Haptics

Useful evidence:

- haptics for cell/range selection can improve touch feedback;
- disabled controls should not emit success-like feedback;
- device/system haptic preferences remain authoritative.

In EpicHub, haptics are presentation feedback only. They never signal ECU write/Burn success before native verification returns that result.

---

# Changed-item / favorites / presets concepts

Potential reusable interaction patterns:

- `Changes` list showing semantic items changed from an explicitly named baseline and deep-linking to them;
- Quick Tuning/favorites as user navigation shortcuts, not alternate tune definitions;
- logging channel presets as presentation/user configuration;
- tap loads, long-press edits a preset where intuitive.

These should use stable semantic IDs, not visual row indices or old WebView DOM element IDs.

---

# Lifecycle / rotation state preservation

The historical Tuner rotation work and responsive contract show that orientation transitions must preserve meaningful state while recalculating geometry.

Suggested state classes:

## Preserve across ordinary rotation/window resize

- selected top-level route;
- selected System/Category/Feature;
- selected table/curve/parameter target;
- semantic table selection where practical;
- local draft values;
- active curve identity;
- user panel/focus preference where still meaningful;
- user display orientation/zoom preferences when sensible.

## Recompute from new geometry

- rail vs compact selectors;
- number of parameter columns;
- secondary-panel placement;
- visible table cells;
- toolbar wrapping;
- plot/table pixel transforms;
- 3D render viewport.

Do not serialize/reconstruct the complete tuning workspace merely because Android rotated.

---

# Performance ownership

The new Compose architecture should keep expensive work in the correct place:

## Rust / background computation

- large semantic tune model transforms;
- table operation math over large selections if profiling justifies it;
- live channel→table-axis mapping if shared with logging/analysis;
- analysis/downsampling;
- persisted data/schema work.

## Compose/UI thread

- pointer gesture arbitration;
- layout;
- small presentation projections;
- canvas/path rendering from prepared values;
- animations/haptics.

## Avoid

- giant JSON workspace construction;
- rebuilding unchanged semantic models on every snapshot;
- recalculating full static gauge/table artwork per sample;
- updating inactive pages at runtime cadence;
- giving every widget/channel its own native call/Flow subscription when a coarse snapshot projection is enough.

---

# Phase 1 responsibility conclusions

The current evidence is sufficient to lock these ownership boundaries:

1. **Rust owns tuning/runtime semantic truth; Compose owns interaction/presentation state.**
2. **Responsive design is one component/data model with geometry-driven reflow.**
3. **Visual authority comes from accepted EpicHub/EpicDash-JZ references, not UXUI styling.**
4. **Semantic table/curve selections and edit commands are separate from touch gesture state.**
5. **X/Y reversal, zoom, pan, panel folds, camera state and heat maps are presentation only.**
6. **3D remains view-only unless a future accepted edit contract says otherwise.**
7. **Only active presentation surfaces consume high-rate display snapshots.**
8. **Static geometry is cached; runtime rendering mutates the smallest useful dynamic layer.**
9. **Modal pointer streams cannot leak to underlying actions.**
10. **Haptics/animation never imply hardware success before verified domain results.**
11. **Offline/project/live/draft/Burn/uncertain states remain explicitly distinguishable in UI.**
12. **Undo/redo acts on local semantic edits, not implicit ECU rollback.**

Remaining questions are product-level interaction scope and physical Compose performance/ergonomics, not basic ownership ambiguity.