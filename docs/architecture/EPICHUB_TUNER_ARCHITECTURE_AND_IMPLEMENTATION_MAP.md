# EpicHub Tuner Architecture and Implementation Map

## Purpose

This document closes the broad TunerStudio behavioral-reference phase by translating the useful evidence from TunerStudio, MegaLogViewer, EpicScope, the EpicEFI Table Interaction Lab, historical EpicHub/EpicDash work, and current EpicHub architecture into one **EpicHub-native Tuner direction**.

It is not a feature-copy checklist.

The governing rule is:

> **Reference software contributes evidence, not ownership. Preserve the problem that a mature behavior solved and the invariant that made it useful; redesign the implementation, workflow, and architecture for EpicHub, and improve it where stronger evidence or cleaner ownership permits.**

This is the same relationship EpicScope now has to MegaLogViewer: MLV supplied valuable compatibility knowledge, mature workflow evidence, and lessons about ECU-log analysis, but EpicScope deliberately introduced normalized source models, semantic roles, explicit evidence qualification, reusable analysis primitives, large-log query architecture, and specialized analyzers rather than reproducing MLV's historical structure.

The Tuner should follow the same path with TunerStudio.

This document is an implementation map and synthesis. Existing durable decisions in `DECISIONS.md` remain architecture authority unless explicitly amended through the normal approval process.

---

# 1. Evidence sources and what each is allowed to decide

## 1.1 EpicHub architecture decisions — final architecture authority

The durable EpicHub architecture remains authoritative for:

- Kotlin/Jetpack Compose + Rust ownership;
- coarse typed/packed JNI boundaries;
- one ECU session;
- one runtime ChannelStore;
- exact profile identity;
- semantic rather than raw-memory UI actions;
- tune-state/persistence authority;
- logging/measurement format neutrality;
- observation/analysis/recommendation/mutation authority separation;
- Tuning Tools as native product functionality rather than historical plugins;
- four primary workspaces with separate domain owners.

Historical evidence cannot silently override these decisions.

## 1.2 TunerStudio — mature tuning-workflow and failure-boundary evidence

TunerStudio is useful evidence for:

- project/profile authority expectations;
- connection/mismatch/offline workflows;
- table/curve/3D interaction expectations;
- selected position versus live operating point;
- keyboard-heavy editing;
- re-bin versus ordinary axis editing;
- local/RAM/nonvolatile state separation;
- difference reporting and restore history;
- project/firmware migration expectations;
- dashboard/runtime-channel behavior;
- logging workflow behavior;
- bounded caches/history and other mature performance practices.

TunerStudio does **not** own:

- EpicHub class/module structure;
- Java/Swing architecture;
- page/dialog lifecycle;
- exact shortcut choices;
- exact file/persistence representation;
- direct coupling between UI navigation and Burn;
- plugin packaging;
- internal expression/cache architecture;
- Android presentation.

## 1.3 MegaLogViewer — compatibility and historical analysis evidence

MLV remains useful for:

- ecosystem log behavior and terminology;
- field aliases/compatibility evidence;
- mature log-navigation and analysis expectations;
- VE-analysis/autotune behavior worth studying;
- MLG interoperability evidence.

MLV field maps and normalized names are **not** EpicHub/EpicScope semantic naming authority. Compatibility aliases can be accepted without preserving old display vocabulary.

## 1.4 EpicScope — project-native improved analysis reference

EpicScope is stronger project-native evidence for Logger/Analyzer behavior because it has already applied the same clean-reimplementation principle to MLV-era workflows.

Useful EpicScope concepts include:

```text
source-specific data
      ↓
normalized source models
      ↓
semantic roles/capabilities
      ↓
generic analysis primitives
      ↓
event / tune / compare services
      ↓
specialized analyzers
      ↓
presentation
```

EpicScope also establishes important product behavior:

- semantic channel roles survive source identity changes;
- missing data remains explicit rather than fabricated;
- analyzers expose evidence and provenance before conclusions;
- event/condition qualification is reusable;
- tune context enriches analysis without replacing recorded-data authority;
- large logs are queried/normalized rather than expanded into uncontrolled UI object graphs;
- source-format quirks remain behind parser/normalized-model boundaries;
- comparison is a shared service rather than analyzer-specific reinvention.

EpicScope's Web/TypeScript/DOM/OPFS implementation is **not** Android production architecture. The portable parts are its semantic contracts and proven product behavior.

## 1.5 EpicEFI Table Interaction Lab — project-native Tuner interaction evidence

The corrected Table Interaction Lab establishes:

- 2D and 3D are both CORE Tuner representations;
- one Rust-owned semantic table/draft feeds both;
- selection/preview/protection/history are shared;
- both representations use the same semantic edit commands;
- 3D includes rich inspection behavior rather than being decorative;
- UI stops at semantic edit intent and does not own ECU transport/Burn.

TunerStudio independently validates several of these ideas and contributes additional mature behaviors such as live lookup overlays, Follow mode, re-bin semantics, clipboard interoperability, and persistence boundaries.

---

# 2. Product principle: preserve needs, improve solutions

Research findings are not implementation tasks merely because another application has the feature.

For every retained behavior, EpicHub should ask:

1. **What user problem does this solve?**
2. **What safety/correctness invariant made it valuable?**
3. **Does EpicHub already solve that problem more cleanly?**
4. **Can EpicScope/Table Lab/current architecture improve the solution?**
5. **Is the historical mechanism merely legacy coupling?**
6. **What independent tests prove the retained invariant?**

The desired transformation is:

```text
mature reference behavior
        ↓
problem + invariant + failure lessons
        ↓
EpicHub semantic owner
        ↓
clean contract
        ↓
improved native workflow
        ↓
independent qualification
```

not:

```text
TunerStudio / MLV feature
        ↓
matching EpicHub feature/class/screen
```

---

# 3. Target Tuner domain architecture

The Tuner should converge on the following ownership model.

```text
Exact profile source / generated INI
                │
                ▼
       ResolvedProfileGeneration
       ├─ ChannelDescriptors
       ├─ DerivedChannelGraph
       ├─ TuneObjectDescriptors
       │   ├─ parameters
       │   ├─ tables
       │   └─ curves
       ├─ Memory/Persistence capabilities
       ├─ Logger/calibration descriptors
       └─ Compatibility/Migration metadata
                │
                ▼
   Project baseline / TuneSnapshot
                │
                ▼
          Rust TuneDraft
       ├─ semantic values
       ├─ previews
       ├─ editability/protection
       ├─ Table/Curve edit commands
       ├─ semantic history
       ├─ checkpoints
       └─ TuneDiff
                │
          ┌─────┴─────┐
          ▼           ▼
      TuneUiModel   TableUiModel
                      │
                ┌─────┴─────┐
                ▼           ▼
            TableGrid    Table3dView
               2D          3D
```

High-rate runtime state remains separate:

```text
ECU / live source
      ↓
ChannelStore
      ↓
LiveLookupOverlay
├─ operating coordinate
├─ contributing cells/weights where valid
├─ recent bounded trail
└─ validity/session generation
```

The crucial invariant is:

```text
TuneDraft / table calibration state
            !=
LiveLookupOverlay / runtime telemetry state
```

Moving through RPM/load at high rate must not mutate, clone, serialize, or rebuild the underlying tune object.

---

# 4. Tune authority ladder

EpicHub should expose a deliberate authority ladder instead of allowing features to blur together.

```text
1. Observation
   recorded/live evidence only

2. Analysis
   derived measurements, qualification, comparison, models

3. Recommendation / Proposal
   semantic suggested change with evidence/provenance

4. Local TuneDraft mutation
   user-approved local semantic edit

5. ECU live/RAM apply
   explicit guarded write + readback/verification

6. ECU nonvolatile persistence
   explicit Burn/persist transaction + verification
```

No stage implicitly promotes itself to the next.

Examples:

- EpicScope-style analysis may identify a likely VE-region correction but does not change the tune.
- A Tuning Tool may create a `TuneProposal` or `ExperimentProposal` but does not own the write path.
- Accepting a proposal may update `TuneDraft` without touching the ECU.
- Applying `TuneDraft` to RAM does not imply Burn.
- Successful Burn does not rewrite historical analysis provenance.

This extends D-019 into the manual Tuner as well as Tuning Tools.

---

# 5. Core semantic objects

The exact final Rust type names may evolve, but these responsibilities need explicit owners.

## 5.1 `ResolvedProfileGeneration`

Immutable, exact-profile-bound compiled representation containing:

- profile/source identity and fingerprint;
- signature/compatibility metadata;
- channels and derived-channel dependencies;
- tune objects and storage representation;
- units/scaling/precision/ranges;
- editability/conditions;
- page/region/write/Burn capabilities;
- table/curve axes and live-position bindings;
- calibration descriptors;
- compatibility/migration metadata where deliberately supported.

Raw INI parsing never occurs in hot UI paths.

## 5.2 `TuneSnapshot`

An immutable complete tune authority tied to exact profile identity and provenance.

Possible origins include:

- verified ECU readback;
- persisted project baseline;
- named imported tune after validation;
- immutable history/checkpoint generation.

The origin must remain explicit.

## 5.3 `TuneDraft`

The single authoritative local semantic edit state.

It owns:

- proposed scalar/bit/table/curve changes;
- quantized semantic values;
- edit restrictions;
- bulk operations;
- semantic history;
- preview/commit boundaries where appropriate;
- baseline identity.

It is not ECU RAM state and not automatically persisted project authority.

## 5.4 `TuneEditCommand`

Input-independent semantic commands should cover, as applicable:

- Set / Increment / Decrement;
- Add / Subtract / Scale;
- selection operations;
- horizontal/vertical/2D interpolation;
- smoothing;
- table re-bin;
- clipboard paste;
- brush/pull where retained;
- proposal application;
- undo/redo as history operations.

Pointer, touch, keyboard, rotary controls, accessibility, 2D, 3D, and tests all invoke the same semantic commands.

## 5.5 `TuneDiff`

Read-only semantic comparison result between two compatible authorities/revisions.

It should be reusable for:

- working draft versus baseline;
- working draft versus ECU readback;
- saved tune versus project baseline;
- checkpoint versus checkpoint;
- pre/post analyzer proposal;
- pre/post migration when compatibility mapping exists.

Diff generation never writes the ECU.

## 5.6 `TuneProposal`

A proposed semantic change originating from analysis/Tuning Tools.

It should carry at least:

- source analysis/session identity;
- exact profile/tune baseline identity;
- affected semantic targets;
- proposed values/transforms;
- evidence/provenance/confidence;
- rejection/qualification information;
- stale-baseline detection material.

Applying it to `TuneDraft` is explicit and becomes an ordinary semantic edit/history transaction.

## 5.7 `ExperimentProposal`

A higher-authority proposal for a bounded live candidate test.

It still does not write directly. The sole tuning authority resolves/quantizes/validates it and owns any RAM apply/readback/restore sequence.

---

# 6. Table and curve architecture

## 6.1 One semantic object, multiple representations

The accepted direction is:

```text
Rust TuneDraft / table state
          │
      TableUiModel
       /       \
  TableGrid   Table3dView
     2D          3D
```

Both views share:

- committed values;
- draft revision;
- selected cells;
- focused cell;
- selection anchor;
- protection/editability;
- semantic preview;
- semantic history result.

Neither receives a private editable copy.

## 6.2 3D is core, not a decorative later mode

The richer Table Interaction Lab behavior remains the stronger EpicHub design target. TunerStudio is evidence that 3D is genuinely useful, not a ceiling on what EpicHub should provide.

3D presentation can own:

- camera yaw/pitch/zoom;
- projection/surface rendering;
- hit testing;
- hover;
- crosshair and local inspection overlays;
- exact-value cards;
- local flattened/2D context;
- other accepted inspection layers.

Those presentation values never dirty the tune.

## 6.3 Selection, hover, and live position are different

Production terminology/state should distinguish:

```text
EditorSelection
FocusedCell
PointerHover
LiveLookupCoordinate
LiveLookupContributors[]
RecentLookupTrail
FollowMode
```

A moving engine operating point must not silently move the user's selection unless Follow mode is explicitly enabled.

## 6.4 Direct axis edit is not re-bin

Directly changing axis breakpoints and resampling the table surface are different semantic operations.

`RebinTable` should:

- validate monotonic axes;
- define/respect an interpolation policy;
- preview axis and surface changes;
- show an exact diff;
- commit as one history transaction.

## 6.5 Preview should be generalized

The Table Lab's transient numeric `previewValue` should become a broader semantic preview concept for operations whose effect deserves inspection before commit:

- numeric entry where useful;
- interpolation;
- smoothing;
- paste;
- re-bin;
- analyzer/Tuning Tool proposals.

Preview state is not committed `TuneDraft` state and never causes ECU writes by itself.

---

# 7. EpicScope-informed analysis integration

EpicScope demonstrates the stronger direction for analysis compared with treating MLV/TunerStudio analyzers as isolated utilities.

## 7.1 Reuse semantic contracts, not Web implementation

EpicHub should adapt the proven concepts:

- normalized measurement sources;
- semantic channel roles;
- explicit validity/staleness;
- source/profile/tune provenance;
- reusable qualification filters;
- events/segments/visits;
- grouped/streaming statistics;
- coverage/evidence strength;
- comparability;
- blocked validation where needed;
- tune-aware operating-point mapping;
- generic compare services;
- bounded plotting/query results;
- specialized domain analyzers over generic primitives.

It should **not** embed or reproduce:

- browser DOM state;
- TypeScript object layout;
- OPFS implementation details;
- Web-specific sidecar representations;
- source-format-specific UI paths.

## 7.2 Analyzer versus Tuning Tools versus Tuner

```text
Analyzer
= generic exploration / evidence / comparison

Tuning Tools
= guided domain workflow with prerequisites,
  qualification, identifiability and recommendation policy

Tuner
= manual semantic calibration browsing/editing
  + sole user-facing path into mutation authority
```

They share lower-level contracts but not UI/ViewModel ownership.

## 7.3 Analysis should feed the Tuner directly through semantic proposals

A same-application workflow should eventually look like:

```text
MeasurementStore / ChannelStore
        ↓
EpicScope-informed analysis primitives
        ↓
Analyzer / Tuning Tool
        ↓
Evidence + TuneProposal
        ↓
Tuner preview / diff
        ↓
user accepts selected changes
        ↓
TuneDraft transaction
```

No export-to-CSV/MSQ and manual re-import should be required merely to move a proposal between two EpicHub domains.

External export remains useful for interoperability, not internal domain communication.

## 7.4 Evidence remains attached to proposals

A proposal should preserve enough evidence to answer:

- Which source/log/session produced this?
- Which tune/profile baseline was active?
- Which filters/modes excluded samples?
- Which operating regions/events contributed?
- How strong/repeatable was the evidence?
- What was observed versus interpolated/modelled?
- What changed after the proposal was generated?

This is where EpicHub can materially improve on opaque autotune workflows.

---

# 8. MLV / EpicScope lessons for table analysis

A VE/fueling analysis should not become `AFR error → blindly change VE`.

EpicScope's matured Fueling work already establishes the better pattern:

- separate steady-state calibration evidence from transient-response evidence;
- remove DFCO/hard fuel-cut samples from ordinary VE evidence;
- distinguish AE/decel behavior from steady calibration;
- treat missing context conservatively;
- qualify operating stability using a meaningful window rather than fragile sample-to-sample jitter gates;
- keep low-evidence visited bins visible instead of pretending they were never visited;
- expose evidence strength;
- retain correction-layer context such as trims rather than automatically attributing every mixture error to VE;
- preserve actual source evidence behind every tuning conclusion.

Therefore a future EpicHub VE tool should use MLV/TunerStudio autotune behavior as reference evidence, while its product model should be closer to:

```text
qualified observations
      ↓
operating-point evidence
      ↓
confidence / repeatability / exclusions
      ↓
proposed table delta
      ↓
2D/3D TuneDiff preview
      ↓
explicit user acceptance
```

This is a deliberate improvement rather than a clone.

---

# 9. Keep / improve / reject cross-reference

| Mature reference behavior | EpicHub treatment | Reason |
|---|---|---|
| Project-local profile authority | **KEEP / strengthen** | Prevent silent reinterpretation; bind immutable compiled profile generation to project. |
| Signature/profile mismatch workflow | **KEEP / improve** | Preserve explicit incompatibility states; offer restricted diagnostics rather than unrestricted “connect anyway.” |
| Offline tune editing | **KEEP** | Useful independent project workflow; revalidate before ECU mutation. |
| TunerStudio dialog/page-close Burn | **REJECT mechanism** | Preserve persistence safety invariant but navigation does not own Burn. |
| RAM vs nonvolatile distinction | **KEEP / formalize** | Explicit tune-state authority ladder. |
| 2D and 3D over same table | **KEEP** | Independently validated and already project-native. |
| 3D as limited legacy view | **IMPROVE** | Retain richer Table Lab inspection/edit model. |
| Separate selected/live cell | **KEEP / formalize** | Prevent runtime motion from corrupting editing state. |
| Weighted active-cell lookup | **KEEP where profile semantics support it** | Useful live evidence; separate from brush weights. |
| Re-bin distinct from axis edit | **KEEP** | Prevent silent surface reinterpretation. |
| Spreadsheet copy/paste | **KEEP / improve atomic validation** | Valuable interoperability without making clipboard structure tune authority. |
| TunerStudio local undo/redo | **KEEP need / redesign owner** | Central Rust semantic history across all Tuner views. |
| Restore points | **KEEP / improve** | Typed immutable checkpoints with provenance, deduplication, retention policy. |
| Difference reports | **KEEP / generalize** | One semantic TuneDiff service, observational only. |
| MLV FieldMaps aliases | **REFERENCE only** | Compatibility evidence, not semantic/display naming authority. |
| MLV graph/histogram workflows | **KEEP user need / use EpicScope refinement** | EpicScope has stronger normalized/semantic/evidence architecture. |
| Per-widget formulas | **REJECT** | Shared compiled derived-channel graph in ChannelStore. |
| Hidden dashboard unsubscribe | **KEEP idea / generalize** | Whole-app demand graph across Dashboard/Logger/Analyzer/Tuner overlays. |
| One sample rate for everything | **REJECT** | Acquisition/derivation/logging/UI need independent budgets. |
| Log file format as logger architecture | **REJECT** | MeasurementWriter/Store abstraction; MLG/MDF are adapters. |
| Huge log in UI memory | **REJECT** | Bounded query/downsample below JNI/UI. |
| Analyzer direct ECU writes | **REJECT** | Recommendation/proposal only; tuning authority owns all mutation. |
| Firmware upgrade equals tune migration | **REJECT** | Separate flashing, profile migration, tune migration, calibration migration, reconciliation. |
| Mutate only project generation during migration | **REJECT** | Create new atomic generation, preserve rollback source. |
| Java/Swing/plugin architecture | **REJECT** | Historical shell has no production authority. |

---

# 10. Demand, performance, and cache model

The Tuner must fit the already-qualified EpicHub performance direction.

## 10.1 Independent workload rates

Separate budgets for:

- ECU acquisition;
- ChannelStore publication;
- derived graph evaluation;
- logger retention;
- analyzer processing;
- live lookup overlay publication;
- Compose rendering.

A 250 Hz ECU stream does not imply 250 Hz whole-table or whole-screen recomposition.

## 10.2 Demand graph

Conceptually:

```text
consumer demand
      ↓
required semantic channels
      ↓
derived dependency closure
      ↓
acquisition plan permitted by profile/firmware
```

An invisible table may release lookup-overlay presentation demand while Logger or warnings keep RPM/MAP acquisition active.

## 10.3 Revision-based Tuner publication

Prefer:

```text
initial full Tune/Table snapshot
then
revision + changed ranges/cells + selection/preview revision
```

rather than transferring the full table for every edit.

Live runtime overlays have their own revision path.

## 10.4 Cache lifetime is semantic

Examples:

- profile metadata → profile generation;
- TuneDiff → authority/profile revisions;
- table geometry → tune-object revision + view parameters;
- live lookup → ECU session/sample generation;
- analyzer query → source identity + query/window + channel set;
- derived sample → source sample generation;
- diagnostics/history → bounded feature-specific policy.

A cache miss is safer than silently using state from the wrong tune/profile/session generation.

---

# 11. First implementation-ready Tuner slice

The first production Tuner implementation should be deliberately **offline and non-mutating toward the ECU**.

This exercises the hardest semantic ownership rules without mixing in USB/write/Burn/reconnect failure modes immediately.

## Slice A — Offline semantic table/curve foundation

### Inputs

Use curated, exact-profile-bound fixtures/goldens rather than live ECU mutation.

At minimum:

- one representative compiled/fixture `ResolvedProfileGeneration` or narrow equivalent;
- one validated baseline `TuneSnapshot` fixture;
- table descriptor(s), including X/Y/Z, units, precision, bounds;
- at least one curve descriptor so table-specific assumptions do not become the universal edit model.

### Rust/domain scope

Implement the smallest real semantic owners needed for:

- immutable baseline tune identity;
- `TuneDraft` derived from baseline;
- semantic tune-object identity;
- table/curve descriptors;
- value bounds/scaling/precision handling;
- protection/editability;
- `TableEditCommand` / generic edit command entry point;
- Set/Add/Scale/Increment/Decrement;
- one semantic transaction per multi-cell action;
- authoritative semantic undo/redo;
- bounded/revisioned projection for UI.

Horizontal/vertical interpolation can enter either this slice or its immediate follow-up depending on implementation size, but the command architecture must allow it without redesign.

### Compose scope

Implement:

- actual Tuner table selection from fixture/profile data;
- 2D read-only rendering from `TableUiModel`;
- 3D read-only rendering from exactly the same model;
- shared selection;
- shared numeric preview;
- edit submission through semantic commands;
- undo/redo projection;
- explicit OFFLINE/FIXTURE/NOT ECU APPLIED authority presentation.

Do not add a second Kotlin tune copy merely to make UI implementation easier.

### Explicitly out of scope

- ECU RAM writes;
- Burn;
- live tune readback;
- automatic analyzer/autotune proposal application;
- firmware migration;
- production project backup codec;
- specialized Tuning Tools;
- full clipboard/re-bin/smoothing breadth unless the slice remains small enough after the core is stable.

## Acceptance gates

The slice is not complete unless:

1. 2D and 3D show the same authoritative values;
2. 2D and 3D share the same selection and preview;
3. equivalent 2D/3D edits produce the same semantic command/history result;
4. no UI code receives raw page/offset/byte write authority;
5. every multi-cell edit is one semantic transaction;
6. undo/redo is tuning-domain history, not per-widget history;
7. presentation/camera state cannot dirty the tune;
8. profile/tune identity is visible in diagnostics/test projections;
9. invalid/out-of-range edits fail atomically;
10. JNI remains coarse—no one-call-per-cell design;
11. switching views cannot fork state;
12. no ECU mutation API is invoked by this slice;
13. fixture/offline state cannot visually masquerade as verified live ECU authority.

---

# 12. Implementation sequence after the first slice

## Phase A — semantic offline Tuner core

Build the first slice above.

## Phase B — mature table/curve editing

Add deliberately:

- H/V/2D interpolation;
- smoothing with preview;
- spreadsheet clipboard;
- object-level import/export;
- re-bin;
- richer semantic history/checkpoints;
- protection/pinning behavior;
- full 3D inspection/direct-edit contract.

## Phase C — EpicScope-informed analysis/proposal bridge

Introduce shared/adapted analysis contracts and prove:

```text
recorded evidence
→ qualification
→ proposal
→ TuneDiff preview
→ explicit TuneDraft acceptance
```

Start with read-only proposal generation. Do not grant ECU mutation authority here.

## Phase D — live lookup overlay

Connect ChannelStore runtime channels to Tuner tables:

- live RPM/load position;
- profile-supported contributors/weights;
- validity/staleness;
- bounded trail;
- Follow mode.

Keep this high-rate path separate from TuneDraft revisions.

## Phase E — live tune readback and semantic comparison

Add:

- verified complete ECU `TuneSnapshot` read;
- local/ECU identity labels;
- TuneDiff;
- reconnect/session-generation handling;
- explicit reconciliation choices.

Still no automatic write merely because differences exist.

## Phase F — guarded RAM application

Add the mutation coordinator:

```text
semantic draft diff
→ validated write plan
→ session/baseline preflight
→ batched writes
→ exact readback
→ complete verification
→ verified RAM state or uncertainty
```

## Phase G — explicit Burn/nonvolatile persistence

Only after RAM-write qualification:

- per-region persistence capability;
- explicit Burn plan;
- post-Burn verification;
- uncertain-persistence recovery.

## Phase H — durable project/history/migration depth

Add:

- project store;
- restore/checkpoints;
- retention/deduplication;
- portable backup;
- migration plans/reports;
- atomic profile/project generations;
- calibration migration;
- rollback.

These should build on the already-proven semantic owners rather than forcing them into existence from persistence format requirements.

---

# 13. Research-backlog interpretation

EH-TS-001…102 are **evidence traceability identifiers**, not a 102-item feature queue.

During implementation closeout, every finding should receive one or more of these statuses:

- `PROVEN_NEED` — mature evidence proves the user/safety problem exists;
- `ALREADY_COVERED` — current EpicHub architecture already satisfies the principle;
- `EPICHUB_IMPROVEMENT` — keep the need but deliberately implement a stronger solution;
- `IMPLEMENTATION_CANDIDATE` — appropriate for a planned product slice;
- `TARGETED_VALIDATION` — only a narrow runtime experiment is still needed;
- `DEFER` — useful but not needed for current implementation sequence;
- `REJECT_LEGACY_MECHANISM` — historical mechanism should not be reproduced.

A single research item can be both `PROVEN_NEED` and `EPICHUB_IMPROVEMENT`.

Numbering must never be interpreted as priority.

The four existing EH-TS backlog files may be consolidated later as repository housekeeping, but implementation does not need to wait for that mechanical merge if this architecture map supplies the required ownership/status interpretation.

---

# 14. Known architecture decision requiring explicit follow-up

There is one important current decision mismatch that must not be hidden.

`D-016` currently says that three-dimensional table inspection remains view-only unless a separately accepted semantic edit contract is added.

The later corrected Table Interaction Lab establishes direct 3D editing as CORE behavior **through the same semantic edit commands as 2D**.

These are not actually incompatible at the ownership level—the Lab supplies precisely the type of semantic edit contract D-016 requires—but the durable decision wording has not yet been updated to accept it.

Therefore:

- do **not** silently implement production 3D editing in violation of D-016;
- do **not** downgrade 3D editing to optional merely because D-016 predates the corrected Lab;
- before enabling production 3D edit gestures, propose a focused decision update stating that 3D may emit the same qualified semantic `TuneEditCommand`s as 2D and receives no additional mutation authority.

This document proposes that clarification but does not self-authorize a new durable decision.

---

# 15. What should not be built next

The closeout evidence does **not** justify immediately building:

- the entire TunerStudio feature surface;
- a Swing-like dialog/menu hierarchy;
- a plugin loader;
- a second Analyzer inside Tuner;
- a second ChannelStore for Tuner;
- per-widget calculated-channel engines;
- a direct analyzer-to-ECU writer;
- a file-format-driven Logger/Tuner architecture;
- a giant all-at-once project migration system;
- a full ECU write/Burn path before local semantic editing is proven;
- a new broad performance campaign on placeholder UI;
- a TunerStudio-exact shortcut/visual reproduction.

The next engineering work should prove the **smallest real semantic Tuner core** first.

---

# 16. Regression and qualification strategy

The first Tuner implementation should reuse the project methodology rather than treat historical behavior as golden by default.

Use, in order:

1. exact profile/tune fixture truth;
2. project-native Table Interaction Lab invariants;
3. TunerStudio mature behavior where deliberately retained;
4. focused differential fixtures;
5. known historical failure modes turned into new invariant tests;
6. Android physical UI/performance qualification once the real surfaces exist.

Important initial regression classes:

- profile mismatch / wrong-generation rejection;
- 2D/3D state identity;
- selection versus live overlay separation;
- preview versus commit separation;
- atomic multi-cell operations;
- precision/range enforcement;
- semantic undo/redo;
- stale async revision rejection;
- presentation state never dirtying tune;
- no raw-address authority leakage;
- bounded JNI traffic;
- fixture/offline authority presentation.

As live integration arrives, add session loss, readback mismatch, uncertain write, Burn, reconnect, and migration failure tests incrementally rather than contaminating the first slice.

---

# 17. Final synthesis

The research phase has done its job if it prevents two opposite mistakes:

1. **Ignoring mature lessons** and rediscovering years of ECU-software failure modes from scratch.
2. **Treating mature software as the design specification** and carrying its historical limitations into a new application.

EpicHub should instead use the accumulated evidence to build a Tuner that is more coherent than TunerStudio and more integrated with analysis than the old TunerStudio/MLV split.

The intended product relationship is:

```text
TunerStudio
→ mature tuning interaction/safety/persistence evidence

MegaLogViewer
→ mature log-analysis/compatibility evidence

EpicScope
→ improved project-native Logger/Analyzer semantics

Table Interaction Lab
→ improved project-native 2D/3D Tuner interaction semantics

EpicHub architecture
→ final ownership, safety, runtime and Android implementation authority
```

The first concrete step is therefore **not “implement TunerStudio features.”**

It is:

> **Implement one offline, profile-bound, Rust-owned semantic TuneDraft feeding shared 2D and 3D Tuner views with semantic commands and history, with no ECU mutation yet.**

That creates the foundation on which live lookup, EpicScope-informed proposals, ECU comparison, guarded RAM writes, Burn, project history, and migration can be added without changing who owns the tune.