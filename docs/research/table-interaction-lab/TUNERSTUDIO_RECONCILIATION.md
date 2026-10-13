# Table Interaction Lab ↔ TunerStudio reconciliation

## Purpose

This document reconciles the pre-existing EpicEFI Table Interaction Lab work with the TunerStudio behavioral/stability research.

The Table Interaction Lab is project-native design/prototype evidence. TunerStudio is an external mature behavioral reference. Neither should overwrite the other blindly.

The goal is to identify:

- behavior EpicHub had already designed or prototyped before the TunerStudio research;
- TunerStudio behavior that independently validates those choices;
- TunerStudio behavior that extends the current Table Interaction Lab scope;
- prototype/demo mechanics that must not become production architecture by accident.

## Authority and scope rule

The corrected Table Interaction package explicitly establishes that **2D and 3D are both CORE EpicHub tuning functionality**.

The executable `EpicEFI_Table_Interaction_Lab_CORE_COMPLETE_2D_3D.html` is useful implementation/prototype evidence, but older comments inside that HTML still use the earlier `OPTIONAL` classification for renderer sections. Those labels are superseded by:

1. `EpicEFI_Table_Interaction_Lab_REFERENCE_PACKAGE_2D_3D_CORE_README.md`;
2. `EpicEFI_Table_Interaction_Lab_COMPLETE_FEATURE_INVENTORY_2D_3D_CORE.md`;
3. `EpicEFI_Table_Interaction_Lab_PORTABILITY_APPENDIX_2D_3D_CORE.md`;
4. `EpicHub_TABLE_INTERACTION_INTEGRATION_MAP_2D_3D_CORE.md`.

Therefore production EpicHub must not treat 3D as a deferred visual extra merely because the executable prototype retains stale classification comments.

---

# 1. Already established by the Table Interaction Lab

## 1.1 One authoritative table/draft, multiple representations

The Lab defines one authoritative table/draft state consumed by both 2D and 3D.

Its EpicHub integration map places the authoritative draft in Rust, exposes a shared `TableUiModel`, then feeds peer `TableGrid` and `Table3dView` presentations.

**TunerStudio comparison:** independently validates the same mature user expectation: 2D and 3D are views over the same underlying X/Y/Z tune data.

**Decision: KEEP.**

This is already an EpicHub design decision. TunerStudio adds confidence; it does not originate the requirement.

---

## 1.2 2D and 3D use the same semantic edit path

The Lab requires 2D and 3D gestures to emit the same semantic `TableEditCommand` types into the same NativeEngine/Rust `TuneDraft` mutation path.

**TunerStudio comparison:** consistent with the mature expectation that changing representation does not create a second editable tune state.

**Decision: KEEP.**

No separate 3D tune model, 3D mutation service, or second undo authority should exist.

---

## 1.3 Shared selection state

The Lab already separates and centralizes:

- active/focused cell;
- selected cells;
- selection anchor;
- move;
- extend;
- toggle;
- rectangle selection;
- select-all;
- collapse.

Both 2D and 3D consume this same selection state.

**TunerStudio comparison:** validates selection as a durable editing concept independent of presentation.

**Production note:** TunerStudio additionally demonstrates that user selection and **live ECU lookup position** are distinct states. The Lab's `active cell` terminology must therefore not be overloaded to mean live controller operating position.

**Decision: KEEP + REFINE terminology.**

Production model should distinguish at least:

```text
FocusedCell / Selection
LiveLookupPosition
LiveLookupWeights
RecentLookupTrail
```

---

## 1.4 Preview-before-commit

The executable Lab keeps transient numeric entry in `previewValue` and exposes it through a shared display-value accessor so both renderers show the preview without mutating committed calibration values.

The integration map carries that into EpicHub as a shared preview feeding both `TableGrid` and `Table3dView`.

**TunerStudio comparison:** reinforces the value of reversible, low-friction editing and clear checkpoint semantics.

**Decision: KEEP and generalize.**

Numeric entry is one preview type. Production EpicHub should use the same semantic preview concept for potentially destructive transforms such as smoothing, interpolation, paste, re-bin, and analyzer proposals where appropriate.

---

## 1.5 Central semantic history

The Lab prototypes table history and explicitly maps authoritative tune edit history to Rust.

The prototype uses complete table snapshots, which are acceptable for the 32×32 standalone reference but are not automatically the production storage strategy.

**TunerStudio comparison:** strongly validates undo/redo as tuning-domain state rather than individual widget state.

**Decision: KEEP semantic ownership, IMPROVE implementation.**

Production history should retain semantic transaction metadata and choose snapshot/delta/reversible-operation storage based on correctness and performance rather than copying the JavaScript snapshot implementation.

---

## 1.6 Input adapters call shared operations

The Lab explicitly requires keyboard, pointer, UI controls, and future input methods to call existing domain operations rather than reproducing calibration logic.

**TunerStudio comparison:** mature keyboard-heavy tuning workflows validate this strongly.

**Decision: KEEP.**

Compose pointer/touch, hardware keyboard, rotary/physical controls, accessibility actions, and automated tests should all route into the same semantic edit commands.

---

## 1.7 Protected/pinned cells

The Lab contains edit-protection state and ensures selected transforms and brush operations skip pinned cells while both 2D and 3D display that protection.

**TunerStudio comparison:** adds no reason to remove this and supports the broader requirement for table-level editability/protection metadata.

**Decision: KEEP.**

Production protection authority belongs to the tuning domain/profile/draft, not only to a UI marker.

---

## 1.8 Rich 3D inspection is core

The corrected Lab definition requires 3D surface rendering, projected-cell hit testing, transient Hover, persistent Selected context, crosshair traces, local labels, local 2D patch, spotlight, flattened inspection plane, magnitude ruler, and exact-value cards.

**TunerStudio comparison:** validates 3D as a practical tuning/inspection representation and adds mature behaviors such as live active-point tracking, history trail, Follow Mode, and optional weighted active-cell editing.

**Decision: KEEP the Lab's richer 3D inspection model.**

TunerStudio is behavioral evidence here, not a design ceiling. EpicHub's existing 3D inspection concept is intentionally richer in several areas.

---

# 2. TunerStudio findings that extend the Lab

## 2.1 User selection vs live ECU lookup

The Lab distinguishes selection from pointer hover but does not yet fully define the controller's continuously changing lookup state.

TunerStudio demonstrates separate selected and active/live positions, optional Follow Mode, runtime history trail, and weighted contributing cells for interpolated lookup.

**Add to EpicHub model:**

```text
EditorSelection
PointerHover
LiveLookupCoordinate
LiveLookupContributors[]
RecentLookupTrail
FollowMode
```

Live telemetry must never silently move user selection unless Follow Mode is explicitly active.

---

## 2.2 Re-bin as a distinct semantic transformation

The Lab supports normal cell operations and interpolation but the supplied corrected package does not define a complete axis re-bin workflow.

TunerStudio distinguishes:

- editing axis breakpoints while leaving Z values unchanged;
- explicit Re-Bin that changes axes and resamples/interpolates the Z surface.

**Add to EpicHub:** a first-class `RebinTable` semantic transaction with validation, preview, one history entry, and exact axis/surface diff.

---

## 2.3 Clipboard and tune-object import/export

The Lab reference is self-contained and does not establish production clipboard/file interchange behavior.

TunerStudio provides mature spreadsheet-compatible table copy/paste and object-level import/export separate from whole-tune handling.

**Add to EpicHub:**

- tabular plain-text clipboard interchange;
- complete parse/validation before mutation;
- atomic paste transaction;
- tune-object-level import/export with semantic compatibility checks.

---

## 2.4 Edit checkpoint vs ECU application/persistence

The Lab correctly stops 2D and 3D at semantic edit intent / `TuneDraft`; RAM write and Burn remain separate lower-layer operations.

TunerStudio reinforces this and adds mature Apply/Cancel behavior, RAM-vs-Flash distinction, and Burn ordering.

**Decision: KEEP the Lab boundary and strengthen it.**

Production EpicHub must distinguish:

```text
local draft mutation
local edit checkpoint
apply to ECU live/RAM
persist ECU nonvolatile
local durable project checkpoint
```

UI navigation is not the owner of any of these persistence transitions.

---

## 2.5 Live weighted-cell inspection/editing

The Lab has weighted brush editing, but that weighting is a **user editing brush kernel**, not ECU runtime lookup contribution.

TunerStudio's weighted-active-cell behavior is a different concept: it reflects the controller's interpolated table lookup contribution.

These must remain separate models.

**Add to EpicHub:** live lookup contributors and weights where firmware/profile semantics make them knowable.

**Do not conflate:**

- brush radius/falloff weights;
- ECU interpolation/lookup weights.

---

# 3. Prototype mechanics that must not become production architecture

The executable Lab is valuable behavior evidence but deliberately contains prototype choices.

Do not directly promote these into production contracts:

- browser DOM ownership;
- Canvas implementation details;
- generated demonstration axes/data;
- JavaScript object/global state organization;
- full-table snapshots solely because the prototype uses them;
- CPU-side 3D quad sorting solely because the prototype uses it;
- browser-specific prompt/dialog handling;
- exact key bindings as immutable product policy;
- stale internal `OPTIONAL` renderer comments.

The portable concepts are the semantic behaviors and invariants, not the browser implementation.

---

# 4. Unified EpicHub table architecture after reconciliation

```text
ResolvedTuneProfile
      │
      ▼
Rust TuneDraft / semantic table state
      │
      ├─ legal values / scaling / precision
      ├─ editability / protection
      ├─ semantic TableEditCommand execution
      ├─ authoritative edit history
      ├─ previewable bulk transformations
      └─ controller mutation planning (separate guarded layer)
      │
      ▼
TableUiModel
      │
      ├───────────────┐
      ▼               ▼
TableGrid          Table3dView
2D                 3D
      │               │
      └──── shared selection / preview / draft revision ────┘

Runtime ChannelStore
      │
      ▼
LiveLookupOverlay
      ├─ coordinate
      ├─ contributors / weights where valid
      ├─ trail
      └─ validity/staleness
```

Input sources:

```text
Touch / Pointer / Keyboard / Rotary / Accessibility / Tests
                         │
                         ▼
                  TableEditCommand
                         │
                         ▼
                    Rust TuneDraft
```

ECU boundary:

```text
TuneDraft edit
   != live ECU apply
   != Burn/persist
```

---

# 5. Regression consequences

The pre-existing Lab regression requirements remain mandatory:

- 2D and 3D show identical committed values;
- both show identical transient numeric preview;
- selection remains identical between views;
- undo/redo changes both views together;
- interpolation/smoothing updates both;
- brush/pull edits update both;
- protected/pinned state is represented consistently;
- 3D hover remains transient;
- 3D Selected remains persistent;
- required 3D inspection layers remain functional.

Add TunerStudio-derived regression coverage for:

1. user selection remains fixed while live ECU lookup moves;
2. Follow Mode explicitly couples selection to live lookup and disengages predictably;
3. stale/disconnected telemetry invalidates live overlay without corrupting selection;
4. ECU lookup weights and brush-edit weights cannot be confused;
5. re-bin changes axes and surface as one reversible transaction;
6. axis-only edit leaves Z unchanged;
7. malformed clipboard input performs zero partial mutation;
8. valid large paste is one history transaction;
9. local edit checkpoint does not imply ECU RAM write;
10. ECU RAM write does not imply Burn/nonvolatile persistence;
11. switching 2D↔3D during preview/history/live overlay cannot fork tune state;
12. direct 3D editing produces the same semantic history/diff as equivalent 2D editing.

---

# 6. Status interpretation

TunerStudio research comparison markers describe the **current production EpicHub implementation**, not merely whether an idea existed in a prototype.

Therefore a capability may simultaneously be:

- **already designed/prototyped in the Table Interaction Lab**; and
- **still missing from production EpicHub runtime**.

Future research documents should state both dimensions explicitly where relevant to avoid incorrectly classifying mature prior EpicHub work as a new TunerStudio-derived idea.
