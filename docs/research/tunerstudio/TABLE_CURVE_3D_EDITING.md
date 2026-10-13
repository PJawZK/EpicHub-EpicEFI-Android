# TunerStudio table, curve, and 3D editing behavioral reference

## Scope

This slice investigates TunerStudio as a mature reference for interactive tune editing, especially:

- table/curve definition and capability discovery;
- 2D vs 3D editing semantics;
- selection and active/live-cell tracking;
- keyboard/mouse workflows;
- multi-cell transforms and interpolation;
- re-binning;
- clipboard/import/export behavior;
- undo/redo, Apply/Cancel, and original-value recovery;
- persistence of editor view preferences;
- interaction with live ECU write/Burn state.

The goal is to extract behavioral contracts for EpicHub, not to reproduce Swing widgets or proprietary implementation structure.

---

# 1. User-visible behavior / promise

## 1.1 2D and 3D are views of the same table

**Observed behavior**

Bundled 3D-editor help explicitly describes the 3D editor as displaying the same X/Y/Z data as the standard table editor, with depth added for Z values.

The user therefore edits one tune object through different representations rather than maintaining separate 2D and 3D copies.

**EpicHub conclusion — ★**

EpicHub should have one semantic table model and multiple projections:

- numeric/table view;
- 3D surface view;
- future analyzer/heat-map overlays;
- compact/mobile presentation where appropriate.

Selection, edits, undo history, dirty state, live-cell position, and controller synchronization must remain shared across views.

Do not make a 3D screen maintain an independent editable copy of the tune table.

---

## 1.2 Tables and curves are definition-driven capabilities

**Observed behavior**

Across the 105 bundled ECU definitions inspected:

- 102 contain `tableEditor` declarations;
- 103 contain `curveEditor` declarations;
- 100 contain explicit `[TableEditor]` sections;
- 103 contain explicit `[CurveEditor]` sections.

Representative table definitions bind semantic editor objects to:

- X-bin storage;
- Y-bin storage;
- Z-value storage;
- runtime variables used for active-position tracking;
- human direction labels such as `RICHER/LEANER`, `ADVANCE/RETARD`, `UP/DOWN`;
- default 3D orientation;
- help/topic metadata.

**Mature lesson**

The editor is not hard-coded only for VE/spark tables. The ECU definition describes what data forms a table/curve and how runtime state maps into it.

**EpicHub conclusion — ✓ ★**

This aligns with EpicHub's intended resolved-profile/semantic-tuning authority. Preserve the principle, but compile it into typed Rust metadata rather than exposing raw INI syntax to UI code.

The profile should resolve table/curve descriptors such as:

- stable semantic tune-object ID;
- X/Y/Z data references and shape;
- units/precision/ranges;
- writable/read-only capability;
- live lookup channels for X/Y position;
- directional labels;
- persistence region(s);
- interpolation/re-bin capability constraints.

---

# 2. Selection, live position, and visual state

## 2.1 Selected cell and active/live cell are different concepts

**Observed behavior**

The 3D editor distinguishes:

- **selected point** — where keyboard/mouse editing currently targets;
- **active point** — where the running engine/controller is currently operating.

Default presentation uses different colors. The user can jump selection to the active point or enable Follow Mode so selection continually follows the active point.

The standard table also supports a runtime history trail showing recent movement through the map.

**EpicHub conclusion — ✗ ★**

Model these independently:

```text
EditorSelection
LiveLookupPosition
LiveLookupWeights
RecentLookupTrail
```

Do not overload one `selectedCell` field with live telemetry state.

This is especially important when the user deliberately edits a different region while the engine runs elsewhere.

---

## 2.2 Live lookup is weighted, not merely a single nearest cell

**Observed behavior**

The 3D editor has an optional `Increment All Active Cells` mode. When connected and in Follow Mode, edits can affect all currently active/interpolated cells whose contribution exceeds an adjustable active-weight threshold. The help explicitly notes that this can affect up to four table vertices.

**Mature lesson**

The runtime lookup position has richer meaning than "current cell." For an interpolated table, neighboring vertices can all contribute to the current result.

**EpicHub conclusion — ★**

Where the firmware/profile can expose or deterministically reproduce lookup weighting, EpicHub should support a semantic live-overlay model that can show:

- active coordinate;
- contributing cells;
- contribution/weight where meaningful.

Do not make weighted live-editing an initial default. It is powerful and potentially risky; treat it as an explicit expert editing mode with clear affected-cell preview.

---

## 2.3 Edited cells remain visually identifiable

**Observed behavior**

Bundled 3D help states that cells edited since entering the editing session are highlighted separately from the selected and active points.

The table widget also exposes a `Back to original value` action.

**EpicHub conclusion — ★**

Maintain origin-aware edit metadata so UI can distinguish:

- unchanged;
- locally modified;
- selected;
- live-active;
- applied-to-ECU-live;
- persisted-to-ECU;
- reverted.

A cell color alone must not be the authority; the semantic edit/history model owns these states.

---

# 3. Keyboard and mouse workflow

## 3.1 Keyboard editing is a primary workflow

**Observed behavior**

TunerStudio documents keyboard operation prominently rather than as an incidental shortcut layer.

3D navigation includes:

- arrow keys to move selection;
- multiple equivalent increment/decrement keys;
- modifier-assisted larger increments;
- jump-to-active;
- Follow Mode toggle;
- view-orientation shortcuts.

2D editing exposes hotkeys/context actions for:

- increment/decrement;
- increase/decrease by explicit amount;
- set equal;
- scale/multiply;
- interpolate;
- horizontal interpolation;
- vertical interpolation;
- smoothing;
- fill-up-and-right;
- copy/paste.

**Mature lesson**

Fast repetitive tune work benefits heavily from low-latency keyboard operation. Mouse-only editing is materially worse for desktop/physical-keyboard use.

**EpicHub conclusion — ✗ ★**

EpicHub must define a semantic editor-command layer independent of input source:

```text
MoveSelection
ExtendSelection
Increment
Decrement
Add
Subtract
Scale
SetValue
Interpolate2D
InterpolateHorizontal
InterpolateVertical
Smooth
Copy
Paste
Undo
Redo
JumpToLive
ToggleFollow
```

Touch, keyboard, rotary/physical controls, accessibility actions, and future desktop input should invoke the same commands.

Do not hard-wire keyboard handlers directly to table storage mutations.

---

## 3.2 Increment size is user-adjustable

**Observed behavior / implementation evidence**

Both 2D and 3D editors expose configurable normal/modified increment sizes. The 3D settings persist normal increment and CTRL increment values.

**EpicHub conclusion — ★**

Increment semantics should respect tune-object precision and profile-defined step size while permitting user acceleration/multipliers.

A generic `+1 raw unit` operation is insufficient because different tune objects have different physical units, storage scaling, and useful adjustment granularity.

---

# 4. Multi-cell editing operations

## 4.1 Transformations apply to the current selection

**Observed behavior**

The standard table supports operations over selected cells including:

- set to a value;
- increment/decrement;
- add/subtract explicit amount;
- scale/multiply;
- interpolation;
- smoothing;
- fill operations;
- copy/paste.

**EpicHub conclusion — ✗ ★**

Treat multi-cell edits as one semantic edit transaction rather than N unrelated per-cell edits.

Benefits:

- atomic undo/redo;
- one preview/diff;
- bounded ECU transport batching;
- one diagnostic/history event;
- clear validation/clamping report;
- dramatically better large-table performance.

---

## 4.2 Horizontal, vertical, and 2D interpolation are distinct operations

**Observed behavior / implementation evidence**

The table UI exposes separate commands for:

- interpolate horizontal;
- interpolate vertical;
- interpolate over a selected 2D area using its corners.

**EpicHub conclusion — ★**

Do not expose a vague single `Interpolate` action if the selection geometry allows materially different results.

The editor command should encode the interpolation axis/mode explicitly and show the affected region before application when the result is not obvious.

---

## 4.3 Smoothing is parameterized

**Observed behavior / implementation evidence**

Smoothing has a configurable factor between 0 and 1, with higher values producing more smoothing. The help encourages viewing the effect in 3D.

**EpicHub conclusion — ★**

Smoothing should be a previewable deterministic transformation with:

- explicit factor;
- exact affected selection;
- one undo unit;
- before/after numeric diff;
- optional 3D preview.

Do not mutate repeatedly as a slider moves unless preview and commit are deliberately separated.

---

# 5. Re-binning and axis editing

## 5.1 Changing axis bins can intentionally leave Z unchanged

**Observed behavior**

Click-editing an X/Y bin changes that axis value without automatically changing Z values.

This changes the physical meaning/location represented by existing Z cells.

**EpicHub conclusion — ★**

Axis editing and re-binning must be separate semantic operations. Never silently interpolate Z when the user requested only an axis-value edit.

---

## 5.2 Re-bin preserves the tune approximately by interpolating Z

**Observed behavior**

TunerStudio provides a dedicated Re-Bin workflow. The user enters new X/Y bins and explicitly chooses `Apply New X & Y Values, Interpolate Z`.

The documented intent is to retain the old tune shape as closely as possible while moving breakpoints.

The workflow validates at least:

- numeric input;
- X bins ordered low-to-high left-to-right;
- Y bins ordered low-to-high bottom-to-top.

Undo/redo can reverse the resulting change.

**EpicHub conclusion — ✗ ★**

Re-bin must be implemented as an explicit bulk tune transformation:

```text
old axes + old surface
   -> validate proposed axes
   -> resample/interpolate old surface onto new axes
   -> preview numeric/surface delta
   -> commit as one history transaction
```

It should never be implemented as a sequence of independent axis edits followed by opportunistic cell fixes.

**Regression importance: very high.** Re-bin can alter every value in a table while visually appearing to be an axis-layout change.

---

# 6. Undo, Redo, Apply, Cancel, and recovery

## 6.1 Undo/redo is treated as tuning-workflow state, not only widget state

**Implementation evidence**

The generic tuning-dialog controls describe Undo as applying to changes made from **any tuning window**, with Alt+Z / Alt+Y shortcuts for Undo/Redo.

Multiple editor components contain explicit error handling for failed undo/redo operations.

**EpicHub conclusion — ✗ ★**

EpicHub should have one semantic tune-edit history authority, not an independent undo stack in every Compose table widget.

A history entry should identify:

- tune/profile/session generation;
- affected tune object(s);
- before state or reversible operation;
- after state;
- source command;
- local/live/persistence effects.

Undoing a local-only edit is different from undoing an edit already sent live to the ECU. The history layer must not hide that distinction.

---

## 6.2 Cancel reverts to open/last-Apply state

**Implementation evidence from user-visible control text**

The generic tuning dialog describes Cancel as closing the view and reverting settings to what they were when the view was opened **or when Apply was last pressed**.

Apply does not close the dialog; it saves the current changes and, when connected, includes writing them to Flash/Burn for the relevant controller region.

OK/Close similarly establish persistence/close behavior.

**Mature invariant**

Users need a clear checkpoint boundary within a long editing session.

**Limitation / quirk**

The checkpoint is tied to Swing dialog lifecycle and, in the default workflow, controller Burn semantics.

**EpicHub conclusion — Improve**

Preserve the concept of an **edit-session baseline/checkpoint**, but separate:

- local edit commit/checkpoint;
- apply-to-live ECU;
- persist/Burn to ECU;
- close/navigate UI.

Compose navigation must not become the owner of controller persistence.

A future EpicHub table workflow can therefore support explicit states such as:

```text
baseline
 -> local working edits
 -> apply live (optional/guarded)
 -> persist ECU (optional/guarded/capability-driven)
 -> checkpoint local history
```

Cancel/revert must state exactly which of these domains it can still reverse.

---

## 6.3 `Back to original value` is a useful local recovery primitive

**Implementation evidence**

The table context menu exposes a `Back to original value` command for selected cells.

**EpicHub conclusion — ★**

Offer selection-level revert to the current edit-session baseline or chosen comparison baseline.

The UI must name the baseline explicitly when ambiguity exists, e.g.:

- revert to value at edit-session start;
- revert to last local checkpoint;
- revert to current ECU value;
- revert to comparison tune.

Do not use an undefined generic `Original` once EpicHub supports multiple tune authorities.

---

# 7. Clipboard, import/export, and validation

## 7.1 Table clipboard data is tabular and interoperable

**Observed behavior**

Bundled help documents normal Ctrl+C / Ctrl+V and explicitly notes interoperability with Excel.

**Implementation evidence**

The table component validates clipboard content and distinguishes errors such as:

- empty clipboard;
- invalid table copy;
- non-numeric table data;
- non-numeric individual data.

**EpicHub conclusion — ✗ ★**

Support predictable plain-text tabular copy/paste in addition to any richer internal format.

On paste:

1. parse completely;
2. validate shape and numeric content;
3. resolve unit/scaling rules;
4. show/record clamping or rejected cells;
5. commit atomically as one semantic edit.

Never partially mutate the table and then discover malformed clipboard content halfway through.

---

## 7.2 Table import/export is separate from whole-tune import/export

**Implementation evidence**

The table context menu has explicit `Export Table` and `Import Table` actions, distinct from complete MSQ/tune handling.

**EpicHub conclusion — ★**

Retain tune-object-level portability. A user should be able to move one table/curve without replacing unrelated tune settings.

Use typed tune-object identity and compatibility checks rather than relying only on shape equality.

---

# 8. 3D presentation and persistence

## 8.1 3D view parameters are persisted

**Implementation evidence**

The 3D table settings persist values including:

- yaw;
- roll;
- color fill/theme;
- antialiasing;
- even spacing;
- smart/dynamic selection movement;
- Follow Mode;
- active/selected colors;
- selected X/Y and active Z text visibility;
- increment sizes;
- Z-height scale;
- increment-all-active-cells setting and threshold.

**Mature lesson**

Frequent tuners expect the editor to reopen in a useful working orientation rather than reset on every visit.

**EpicHub conclusion — ★**

Persist **presentation preferences separately from tune data**.

A 3D camera angle, color theme, selection behavior, or history-trail length must never dirty the tune or enter ECU synchronization logic.

---

## 8.2 Even spacing is a visualization choice, not tune geometry

**Observed behavior**

3D can display axes using physical spacing between bin values or equal visual spacing by index. This is explicitly described as a viewing aid for highly concentrated axis regions.

**EpicHub conclusion — ★**

Keep geometry truth and visual projection separate. Equal-spacing mode must not alter axis values or interpolation semantics.

---

## 8.3 Smart selection adapts arrow direction to camera orientation

**Observed behavior**

TunerStudio optionally changes arrow-key selection semantics so "right" moves visually right even after the 3D table has been rotated.

**Decision — △**

This is mature but potentially surprising behavior. EpicHub should validate it with users before adopting it.

Safer default:

- 2D editor: arrows follow table indices;
- 3D editor: offer explicit `screen-relative` vs `axis-relative` navigation preference.

Do not silently switch semantics.

---

# 9. Read-only and analysis overlays

## 9.1 The table widget can represent read-only data

**Implementation evidence**

The table component has explicit read-only messaging stating that edits to a read-only table can be used for analysis but will not be saved with the tune file.

It also exposes analysis-specific cell lock/unlock state and tooltip metadata such as beginning value, hit count, hit weighting, and target AFR/Lambda where relevant.

**EpicHub conclusion — ✓ ★**

EpicHub already intends to separate Analyzer from Tuner ownership. Preserve that separation while allowing both domains to use a common table projection model.

Analyzer overlays/locks/statistics must never accidentally acquire tune-write authority merely because they render through a table component.

---

# 10. Performance / caching implications

## 10.1 UI gestures should produce semantic bulk operations

**Evidence-driven conclusion**

TunerStudio exposes many operations that can affect large selections in one action: paste, smooth, interpolate, scale, re-bin, fill.

**EpicHub conclusion — ★**

These operations should execute in Rust against a typed working tune and produce one compact change set. Compose should render the result rather than looping cell-by-cell through JNI.

This aligns with EpicHub's existing coarse-JNI architecture.

---

## 10.2 Live overlays should not force full-table recomputation

**Inference from mature editor behavior**

TunerStudio continuously updates active position/history while large table state remains stable.

**EpicHub conclusion — ★**

Architect separate update paths for:

- static/slow table values and axes;
- local edit deltas;
- high-rate live lookup marker/weights;
- recent trail.

A 200+ Hz runtime stream must not rebuild or copy an entire 16x16/large table into Compose on every sample.

---

# 11. Relevant implementation evidence

Implementation inspection remained narrow and behavior-oriented.

It established that:

- the core table widget exposes distinct commands for selection transforms, interpolation modes, smoothing, copy/paste, history trail, selected-cell lock/unlock, and return-to-original;
- clipboard input is validated before use;
- generic tuning controls expose Undo/Redo plus Apply/Cancel checkpoint semantics;
- 3D editor options are persisted independently from tune data;
- 3D live-edit mode can target weighted active vertices;
- table/curve capabilities are overwhelmingly ECU-definition-driven.

No proprietary source or algorithm is copied into EpicHub documentation.

---

# 12. Limitations / quirks

## 12.1 Help text contains some stale/incorrect shortcut wording

**Observed behavior**

The old 2D help page contains contradictory labels for a few increase/decrease shortcuts, while implementation-facing menu strings are clearer.

**Decision: reject help text as sole authority for exact bindings.**

EpicHub should have one command registry from which menus, keyboard help, accessibility labels, and shortcut documentation are generated.

---

## 12.2 Many synonymous increment keys are desktop legacy

TunerStudio accepts several equivalent characters for increment/decrement in 3D.

**Decision: do not copy blindly.**

EpicHub should support a compact, discoverable default keymap plus remapping where worthwhile.

---

## 12.3 Follow Mode can turn navigation into mutation targeting

When combined with `Increment All Active Cells`, Follow Mode can cause edits to track the running engine and potentially modify multiple weighted vertices.

**Decision: preserve only as deliberate expert functionality.**

Default EpicHub behavior should keep observation and mutation targeting visually and semantically distinct.

---

# 13. EpicHub mapping summary

| Finding | EpicHub status | Decision |
|---|---|---|
| One semantic table, multiple 2D/3D views | △ | ★ Make authoritative |
| Definition-driven table/curve descriptors | ✓ | Keep, typed in Rust |
| Selected vs live-active position | ✗ | ★ Separate state models |
| Weighted contributing live cells | ✗ | ★ Overlay; guarded expert live editing |
| Keyboard-first editor command model | ✗ | ★ Add semantic command layer |
| Multi-cell transforms | ✗ | ★ Atomic bulk transactions |
| H/V/2D interpolation distinction | ✗ | Keep explicitly |
| Parameterized smoothing | ✗ | ★ Preview + one undo unit |
| Direct axis edit vs Re-Bin | ✗ | ★ Distinct operations |
| Re-Bin with Z resampling | ✗ | ★ High-value capability |
| Global semantic undo/redo | ✗ | ★ Central tune history |
| Apply/Cancel checkpoint semantics | △ | Improve: decouple from UI/Burn |
| Selection-level revert | ✗ | ★ Add explicit baseline |
| Plain tabular clipboard interop | ✗ | ★ Add with atomic validation |
| Per-table import/export | ✗ | ★ Add typed compatibility checks |
| 3D presentation persistence | ✗ | ★ Keep outside tune state |
| Smart camera-relative navigation | △ | Validate before adopting |
| Analysis/read-only overlays | △ | Keep authority separation |
| High-rate live marker independent of table state | △ | ★ Performance contract |

---

# 14. Suggested regression tests

## Shared 2D/3D model

1. Edit a cell in 2D; switch to 3D; exact value and dirty metadata are preserved.
2. Edit in 3D; return to numeric table; same semantic edit/history entry is visible.
3. Change only 3D camera/color preferences; tune hash/dirtiness is unchanged.

## Selection and live state

4. Move user selection while engine live point changes; neither overwrites the other.
5. Enable Follow Mode; selection follows live position without creating tune edits.
6. Disable Follow Mode; selection remains where user leaves it while active marker continues updating.
7. Weighted active overlay highlights exactly the contributing cells/weights from a deterministic lookup fixture.

## Keyboard command model

8. Arrow navigation moves selection without mutating data.
9. Increment/decrement respects tune precision and configured step.
10. Large-step modifier performs one semantic transaction.
11. Input command produces identical result whether invoked by keyboard, touch command, or test API.

## Bulk operations

12. Set/add/subtract/scale over a rectangular selection affects only selected cells.
13. Horizontal interpolation produces expected values for deterministic fixture.
14. Vertical interpolation produces expected values.
15. 2D corner interpolation produces expected interior surface.
16. Smooth operation commits as one undo unit.
17. Fill operation affects exactly the documented geometric region.
18. Out-of-range results follow explicit clamp/reject policy and report it.

## Re-bin

19. Reject non-numeric axis input without changing tune.
20. Reject non-monotonic X axis without partial mutation.
21. Reject non-monotonic Y axis without partial mutation.
22. Direct axis edit changes only requested axis value when that mode is chosen.
23. Re-bin resamples Z from the old physical coordinate system onto the new axes.
24. Entire re-bin is one undoable transaction.
25. Re-bin preview/diff exactly matches committed result.
26. Re-bin touching multiple ECU regions produces one semantic edit plan but profile-valid transport operations.

## Undo/redo and baselines

27. Multi-cell transform Undo restores all affected cells atomically.
28. Redo restores the exact post-operation state.
29. New edit after Undo follows a documented branch/history policy.
30. Selection-level revert restores the chosen explicit baseline, not an ambiguous global value.
31. Cancel of an un-applied local edit session returns to session baseline.
32. Apply establishes a new local baseline without requiring UI close.
33. If edits were already sent live to ECU, local Undo/Cancel never falsely claims the controller was reverted unless an ECU reversal transaction succeeds.

## Clipboard/import

34. Copy emits stable tabular text suitable for spreadsheet paste.
35. Valid spreadsheet-shaped numeric paste maps predictably into selected origin/shape.
36. Empty clipboard causes no mutation.
37. Non-numeric clipboard causes no partial mutation.
38. Shape mismatch follows explicit reject/clip/expand policy; no accidental wrapping.
39. Paste exceeding tune range is validated atomically with explicit clamp/reject report.
40. Per-table import rejects incompatible semantic object/profile even if dimensions happen to match.

## ECU live/persistence integration

41. Local table edit remains local until selected policy applies it live.
42. Bulk operation is coalesced into bounded profile-valid controller writes.
43. Disconnect during bulk apply cancels the old session generation and preserves local working edit.
44. Live-applied but unburned table remains visibly distinct from persisted state.
45. Undo of live-applied edit creates an explicit reverse ECU mutation; it is not UI-only history magic.

## Performance

46. High-rate runtime lookup updates do not copy/rebuild complete table data per sample.
47. 16x16 and substantially larger fixture tables remain responsive during live marker updates.
48. Paste/interpolate/re-bin cross JNI as coarse semantic operations rather than one call per cell.
49. Switching 2D/3D views does not duplicate authoritative table storage.
50. Long edit history remains bounded/persisted according to explicit policy without degrading live rendering.

---

# 15. Keep / improve / reject decisions

## Keep

- one tune table shared by 2D/3D views;
- separate selected and live-active positions;
- keyboard-efficient editing;
- explicit multi-cell transformations;
- horizontal/vertical/2D interpolation;
- parameterized smoothing;
- explicit re-bin with Z interpolation;
- undo/redo and recoverable edit baselines;
- table-level clipboard/import/export interoperability;
- persistent editor presentation preferences;
- history/live path visualization.

## Improve

- central semantic edit/history authority rather than widget-local mutation;
- explicit local/live/persisted state rather than dialog-driven Apply/Burn coupling;
- preview/diff for destructive bulk transforms;
- generated shortcut/help documentation from one command registry;
- typed tune-object compatibility instead of shape-only import assumptions;
- weighted live-cell editing as a guarded expert action with preview.

## Reject / do not copy blindly

- Swing dialog lifecycle as tuning transaction authority;
- exact legacy shortcut collection;
- ambiguous `Original` baseline wording;
- camera-relative smart navigation without explicit preference;
- any assumption that a 3D renderer owns separate tune data;
- per-cell JNI/controller calls for bulk table operations.

---

# 16. Remaining questions for later runtime validation

The package provides strong static/user-visible evidence, but the following should remain explicitly open until exercised with a real/simulated controller or deeper targeted tracing:

1. exact timing of live ECU writes during direct table cell editing;
2. whether every table operation writes immediately or can remain locally staged under specific dialog/configuration modes;
3. exact coalescing/debounce behavior for rapid key-repeat edits;
4. exact scope/retention limit of the cross-window undo history;
5. how Undo behaves after successful Burn on every supported firmware family;
6. exact controller-write behavior of Re-Bin and large paste operations;
7. whether 3D Follow Mode + weighted editing has additional safety interlocks in registered editions;
8. exact behavior when a clipboard selection is larger/smaller than the selected table region.

These uncertainties should not block adopting the stronger implementation-independent conclusions above.