# TunerStudio tune write, Burn, and persistence safeguards

## Scope

This slice follows the connection/project-authority research and focuses on the mature distinction between:

- local working values;
- values written to ECU volatile/RAM state;
- values persisted to ECU nonvolatile/Flash state;
- page-specific persistence capabilities;
- controller/local reconciliation safeguards.

The goal is to extract the safety invariants EpicHub should keep while rejecting incidental UI coupling or firmware-specific legacy behavior.

---

# 1. User-visible behavior / promise

## 1.1 A live ECU write is not necessarily persistent

**Observed behavior**

Bundled help explicitly distinguishes writing values to controller RAM from using **Burn** to persist those values to Flash/nonvolatile storage.

The documented promise is:

- RAM-written values can affect current controller behavior immediately;
- without Burn, those changes can be lost on controller power cycle;
- Burn makes supported changes survive power cycle.

This distinction appears consistently in VE Analyze and WUE Analyze help as well as user-facing Burn terminology.

**EpicHub conclusion — ✗ ★**

EpicHub must model at least three independent authorities/states:

1. **local working tune**;
2. **ECU live/volatile state**;
3. **ECU nonvolatile persisted state**.

Do not use a single `dirty` or `saved` flag to represent all of them.

A tuning value can therefore legitimately be:

- changed locally but not sent;
- sent/live on ECU but not persisted;
- persisted on ECU but not yet checkpointed/named locally;
- synchronized in all authorities.

---

## 1.2 Burn capability is definition/page specific

**Observed behavior**

ECU definitions declare Burn commands as part of page geometry rather than relying on one universal application command.

Package inventory for `config/ecuDef/*.ini` in the supplied 3.6.00.06 Beta distribution:

- 105 ECU definitions inspected;
- all 105 declare a `burnCommand` definition;
- 57 contain at least one empty Burn-command entry for a page;
- all 105 declare page read/write geometry;
- 92 include CRC/check-command support;
- 99 include timeout-related communication declarations.

A representative MS3 definition contains a RAM-only page with no Burn command.

**Mature lesson**

Persistence is not a property of the application globally. It is a capability of the connected firmware/profile and sometimes of individual tune regions/pages.

**EpicHub conclusion — ★**

The resolved ECU profile must expose persistence capability semantically:

- writable live/volatile;
- persistable/nonvolatile;
- RAM-only/non-persistable;
- requires power cycle after persistence where applicable;
- verification/checksum capability where available.

The UI should derive available actions from these capabilities rather than merely enabling a generic Burn button whenever online.

---

# 2. Inputs and dependencies

## 2.1 Write semantics come from the ECU definition

**Observed behavior**

Representative ECU definitions declare separate operations for:

- single-value writes;
- block/chunk writes;
- page reads;
- Burn commands;
- page activation;
- inter-write delay;
- read timeout;
- CRC/check commands.

**EpicHub conclusion — ★**

EpicHub's future tuning authority must convert semantic edits into profile-derived transport operations. Android UI code should never independently decide offsets, page changes, write grouping, Burn commands, or timing delays.

---

## 2.2 Some firmware supports explicit verification primitives

**Observed behavior**

Many definitions contain page/table CRC/check commands.

**Inference**

These primitives are intended to avoid unnecessary full reads and/or verify memory/configuration state, but the exact TunerStudio decision policy has not yet been completely traced.

**EpicHub conclusion — △ ★**

Where firmware provides checksums/CRCs, EpicHub should evaluate using them for:

- tune equality detection;
- post-write verification;
- reconnect reconciliation;
- minimizing unnecessary large reads.

Do not assume CRC presence means identical semantics across all ECU families.

---

# 3. Edge cases and safeguards

## 3.1 Auto-Burn on dialog close

**Observed behavior**

Global TunerStudio properties ship with `autoBurnOnCloseDialog=true` and describe the behavior as sending a Burn when a tuning dialog closes so changes are persisted to the controller.

The property comments recommend disabling this only for advanced purposes such as firmware development.

**Mature invariant extracted**

A user leaving an editing context should not unknowingly leave important controller changes only in volatile state.

**Limitation / quirk**

Tying persistence directly to *dialog close* is a desktop-UI lifecycle decision, not a fundamental ECU-safety principle.

**EpicHub conclusion — Improve**

Do **not** copy "screen closes → Burn" literally.

EpicHub should instead expose explicit semantic write state, for example:

- local pending edits;
- applied live;
- persistence pending;
- persisted/verified;
- persistence unavailable;
- persistence failed.

Then navigation can warn or safely complete an explicit transaction according to policy without making UI component lifecycle the persistence owner.

---

## 3.2 Auto-Burn before writes move to another page

**Observed behavior**

Global settings ship with `autoBurnOnPageChange=true` and document that when writing moves to a new page, the previously modified page is Burned first. The stated purpose is to prevent writing a new page until prior writes are persisted.

ECU definitions can also expose a firmware-level `autoBurnOnPageChange` capability/override.

**Mature invariant extracted**

Crossing a controller memory/persistence boundary while an earlier region is still volatile can create ambiguous or unsafe partial state.

**EpicHub conclusion — ★**

EpicHub should model **dirty ECU regions/transactions explicitly** and serialize transitions that require persistence or verification.

The rule should be expressed semantically, e.g.:

> Do not begin an operation whose correctness depends on region/page B while region/page A is in a required-but-unpersisted transition state.

This is stronger and more portable than literally reproducing "Burn on page change."

---

## 3.3 Some pages cannot be Burned

**Observed behavior**

More than half of the supplied ECU definitions contain at least one empty Burn-command entry.

**EpicHub conclusion — ★**

A generic "Burn all dirty pages" implementation must not assume every writable region supports nonvolatile persistence.

Regression coverage must include:

- writable + Burn-capable region;
- writable RAM-only region;
- mixed transaction touching both;
- user navigation away with volatile-only state active.

---

## 3.4 Controller/local state can differ even after successful connection

**Observed behavior**

TunerStudio supports a "Perform Difference Report On Connect" preference and a workflow that reads controller settings, compares them with TunerStudio's local settings, then requires a choice when differences exist.

**Mature invariant**

A compatible firmware signature is not proof that the local and controller tunes are synchronized.

**EpicHub conclusion — ★**

Identity compatibility, tune equality, live-write state, and persisted state must be separate axes in the session model.

---

## 3.5 Replacing local state with controller state is protected

**Observed behavior**

TunerStudio's difference-report workflow offers saving a restore point before replacing local TunerStudio settings with controller settings. Bundled help says automatic restore points are created for this reconciliation path.

**EpicHub conclusion — ★**

Any destructive authority switch must be transactional and recoverable.

At minimum:

1. capture immutable pre-transition local tune;
2. capture controller identity/profile/session generation;
3. obtain full comparison or explicitly record comparison failure;
4. record user resolution choice;
5. atomically commit selected state;
6. retain diff/history metadata.

---

# 4. Persistence

## 4.1 CurrentTune checkpoints the local working state

**Observed behavior**

Bundled help states that when automatic current-tune save is enabled, `CurrentTune.msq` is saved after page Burn and on project close, then loaded on project open.

**Mature lesson**

Controller persistence and local persistence are both important and should advance together when appropriate, but they remain distinct domains.

**EpicHub conclusion — ★**

After a successful ECU persistence transaction, EpicHub should normally checkpoint the corresponding local working tune/history atomically, while still retaining independent metadata for controller-vs-local synchronization.

Do not make local persistence depend solely on graceful Activity/application shutdown.

---

## 4.2 Restore points capture meaningful tuning milestones

**Observed behavior**

TunerStudio creates automatic restore points on selected meaningful events and can skip creating one when settings have not changed.

User-facing resources describe configurable restore-point behavior including tune save/open/load-related events and disk-space limits.

**EpicHub conclusion — ★**

EpicHub history should be semantic/event driven rather than timer-only autosave spam.

Candidate checkpoint triggers:

- before destructive ECU/local reconciliation;
- before firmware/profile migration;
- after successful major persistence transaction;
- explicit user checkpoint;
- before loading another tune into a connected ECU;
- before applying analyzer/autotune bulk corrections.

Deduplicate checkpoints when tune state is unchanged.

---

# 5. Failure / fallback behavior

## 5.1 Burn failure must not be conflated with write failure

**Inference strongly supported by the RAM/Flash split**

A successful live write followed by failed/nonexistent Burn leaves a meaningful state: ECU behavior has changed now, but that state is not guaranteed after power cycle.

**EpicHub conclusion — ★**

Represent this state explicitly and visibly.

Example semantic status:

`LIVE_APPLIED_NONVOLATILE_PENDING`

The user should never see a generic "Saved" success message in this state.

---

## 5.2 Disconnect between live write and Burn

**Inference / required edge case**

The mature model makes this failure possible even if this package slice has not yet reproduced it dynamically.

**EpicHub conclusion — ★**

If disconnect occurs after RAM writes but before persistence:

- preserve local knowledge of intended values;
- mark controller persistence as unknown/unconfirmed;
- do not silently retry against a replacement session without revalidation;
- on reconnect, re-identify ECU/profile and compare controller state before deciding whether to resume/reapply.

---

## 5.3 Burn-capable page changes need serialization

**Observed behavior**

TunerStudio's auto-Burn-on-page-change policy demonstrates that operation ordering matters.

**EpicHub conclusion — ★**

The semantic tuning engine should own a serialized controller-mutation queue/transaction coordinator. Individual Compose screens must not fire independent writes/Burns that can interleave across pages or sessions.

---

# 6. Performance / caching

## 6.1 Block writes are a declared capability

**Observed behavior**

Definitions distinguish value writes and chunk/block writes, and can enable block-writing behavior.

**EpicHub conclusion — ★**

Bulk edits, paste/interpolation, analyzer corrections, and table operations should be coalesced into profile-valid block operations where appropriate instead of emitting one transport command per UI cell change.

This optimization belongs below the semantic edit API so user interaction semantics remain independent of protocol batching.

---

## 6.2 CRC/check support may avoid expensive full comparisons

**Observed behavior**

Most supplied ECU definitions contain checksum/CRC operations.

**EpicHub conclusion — △ ★**

Benchmark equality/reconciliation strategies:

- full region read;
- CRC-first then selective read;
- cached known-state comparison where safe.

Correctness and session generation validation take priority over reducing bytes.

---

# 7. Relevant implementation evidence

Implementation inspection remained deliberately narrow.

It confirms that:

- application/global configuration carries auto-Burn-on-close and auto-Burn-on-page-change preferences;
- ECU-definition parsing recognizes per-definition page/write/Burn/timing capabilities;
- the runtime has dedicated Burn UI/actions rather than treating Burn as synonymous with ordinary writes;
- difference-report-on-connect and current-tune autosave are real configurable behaviors.

No proprietary implementation source or algorithm is copied here.

---

# 8. Limitations / quirks

## 8.1 UI-lifecycle-driven Burn

**Decision: improve, not copy.**

Dialog close/page transition is a convenient mature desktop trigger but a poor permanent owner for safety semantics in a Compose application.

EpicHub should bind persistence to semantic transaction state.

## 8.2 Page as user-facing abstraction

**Decision: mostly hide.**

Firmware memory pages are important implementation/profile concepts but should not normally leak into EpicHub UX. Users care whether edits are live, pending, persisted, verified, or unsupported.

## 8.3 Automatic persistence can surprise expert users

**Decision: preserve explicit visibility/control.**

Automatic safe persistence may be a good default, but EpicHub should expose the resulting state clearly and provide deliberate expert workflows for RAM-only testing where firmware supports it.

Expert mode must not weaken identity/session safety checks.

---

# 9. EpicHub mapping summary

| Finding | EpicHub status | Decision |
|---|---|---|
| Local / ECU-RAM / ECU-nonvolatile are distinct states | ✗ | ★ Core tuning-state model |
| Per-region persistence capability | ✗ | ★ Typed profile capability |
| Burn ordering matters across dirty regions | ✗ | ★ Mutation coordinator |
| Auto-Burn on UI close | n/a | Improve: semantic transaction, not UI lifecycle |
| RAM-only writable regions exist | ✗ | ★ Explicit capability/state |
| Local-vs-controller diff on connect | ✗ | ★ Separate tune-sync state |
| Restore before destructive reconciliation | ✗ | ★ Mandatory recoverability invariant |
| Block write capability | △ | ★ Coalesce below semantic API |
| CRC/check operations | △ | Validate for reconciliation/verification |
| Current tune autosave | ✗ | ★ Durable working tune with atomic persistence |

---

# 10. Suggested regression tests

## Basic state separation

1. **Local edit only**
   - local working value changes;
   - ECU live and persisted state unchanged;
   - status clearly indicates pending local edit.

2. **Apply live without Burn**
   - ECU RAM/live value changes;
   - persisted state remains pending/old;
   - UI does not report fully saved/persisted.

3. **Apply + Burn success**
   - local, live ECU, and persisted ECU states converge;
   - local durable checkpoint advances only after confirmed transaction policy.

4. **RAM-only region**
   - live write allowed when profile permits;
   - Burn action unavailable/not attempted;
   - state communicates non-persistence.

## Ordering and batching

5. **Multiple edits same page/region**
   - writes can be coalesced according to profile capability;
   - final semantic values are correct.

6. **Dirty page A then edit page B**
   - required persistence/ordering invariant executes before unsafe crossing;
   - no interleaved mutations violate firmware constraints.

7. **Mixed Burn-capable + RAM-only regions**
   - coordinator handles each capability correctly;
   - no false global "Burn complete" state.

## Failure cases

8. **Disconnect after live write, before Burn**
   - local intent retained;
   - persistence marked unknown/pending;
   - old session operation cannot resume after reconnect.

9. **Burn timeout/failure**
   - live value may remain active;
   - persisted status remains failed/unverified;
   - retry requires same validated session or complete revalidation.

10. **Reconnect after uncertain Burn**
    - re-identify profile;
    - read/CRC-check affected region;
    - reconcile actual controller state before declaring success.

11. **Process death during local checkpoint after successful ECU Burn**
    - project storage recovers atomically;
    - controller state remains independently discoverable on next session.

## Reconciliation

12. **Controller and local tune differ on connect**
    - mutation remains blocked pending explicit policy/choice where required;
    - no side is overwritten automatically.

13. **Use controller state**
    - pre-transition local checkpoint exists;
    - new local state commits atomically.

14. **Use local state / send tune**
    - profile/session identity rechecked;
    - write/persistence plan is previewable/explicit;
    - failure leaves recoverable state.

## Performance

15. **Large table paste/interpolation**
    - semantic edit is correct;
    - transport write count is bounded/coalesced;
    - UI does not block on one command per cell.

16. **CRC-first tune comparison**
    - equality path avoids unnecessary full reads where firmware semantics prove this safe;
    - any ambiguous CRC/result falls back to authoritative read.

---

# 11. Keep / improve / reject decisions

## Keep

- explicit RAM-vs-nonvolatile distinction;
- profile-defined write/Burn capabilities;
- conservative ordering around persistence boundaries;
- local/controller diff before destructive synchronization;
- restore checkpoint before authority replacement;
- block-write/checksum capabilities where firmware supports them.

## Improve

- express write/Burn state semantically rather than via dialog/page lifecycle;
- make session-generation ownership explicit;
- make persistence/result state visible and machine-testable;
- use atomic local project/history persistence;
- centralize batching and controller mutation in Rust semantic authority.

## Reject

- UI screen/dialog lifecycle as the fundamental persistence owner;
- assuming every writable region is Burn-capable;
- treating successful transport writes as equivalent to durable tune save;
- unrestricted "connect anyway" leading directly into normal mutation authority.

---

# 12. Next research questions

Before write/Burn behavior is considered closed, runtime observation should still verify:

- visual indication of un-Burned changes in normal tuning dialogs;
- exact behavior when auto-Burn is disabled;
- whether TunerStudio retries failed Burn automatically or leaves explicit pending state;
- behavior if connection drops between write and Burn;
- how Burn progress/failure is surfaced;
- whether difference-report equality uses full read, CRC, cached state, or a combination for representative modern firmware;
- exact semantics of Apply/Cancel buttons in ordinary tuning dialogs versus immediate live writes;
- whether Undo/Redo after a live write sends inverse controller writes immediately or stages them locally.

Those runtime details overlap directly with the next table-editing investigation.
