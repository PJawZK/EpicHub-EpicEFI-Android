# TunerStudio connection, project/INI authority, and session behavior

## Scope

This is the first deep TunerStudio behavioral-reference slice for EpicHub.

It covers:

- transport/device discovery;
- ECU signature identity;
- project-local ECU-definition authority;
- project/firmware mismatch handling;
- local-vs-controller tune reconciliation;
- offline operation;
- failure and recovery clues;
- relevant persistence behavior;
- implications for EpicHub's future live ECU session.

It deliberately does not yet attempt to specify write/Burn transaction semantics in full. Those are the next related slice.

---

# 1. User-visible behavior / promise

## 1.1 A detected port is not yet trusted ECU identity

**Observed behavior**

The packaged global settings define device-search query commands and baud-rate candidates. Bundled ECU definitions separately define the firmware query command and expected serial signature.

Representative package evidence:

- `TunerStudio.properties`
  - device search query candidates;
  - device search baud-rate candidates;
- `config/ecuDef/*.ini`
  - `queryCommand`;
  - `signature`;
  - protocol-envelope/runtime/page geometry.

This demonstrates two different concepts:

1. find something that responds on a transport;
2. establish the exact firmware/profile semantics needed to communicate safely.

**EpicHub conclusion — ★**

Maintain an explicit distinction between:

- transport candidate identity;
- verified ECU/firmware/profile identity.

For EpicHub, USB VID/PID or a successfully opened CDC endpoint must never be treated as sufficient semantic authority for runtime-channel interpretation or future tuning writes.

This aligns with the existing EpicHub architectural direction that `0483:5740` is a transport candidate identity rather than exact firmware/profile authority.

---

## 1.2 The project owns the working ECU definition

**Observed behavior**

Bundled help states that projects do not reference the installation's ECU-definition file directly. When a project is created, the selected ECU definition is copied into the project as:

`projectCfg/mainController.ini`

The project-local copy is then the definition used when the project opens.

The supplied example project confirms this structure and contains its own large `mainController.ini` beside `project.properties` and tune state.

Bundled help also says application updates do not replace files in the project tree, specifically preserving the project INI across TunerStudio updates.

**User expectation extracted from this behavior**

Opening an old tuning project should not silently reinterpret the tune through whatever ECU definition happens to ship with the newest application build.

**EpicHub conclusion — ★**

EpicHub should preserve the *semantic profile used by a tune/project* as durable project/session authority or preserve enough immutable profile identity/version information to reproduce it exactly.

Do not make a global "latest definition wins" lookup the sole source of truth for existing projects.

The exact persistence format does not need to mimic TunerStudio's copied INI file.

---

## 1.3 Definition lookup has a layered fallback model

**Observed behavior**

Bundled help documents the effective hierarchy:

1. project-local main definition;
2. project-local customization/override content;
3. project-local include resources;
4. installation include resources where no project override exists.

The installation `config/ecuDef` directory is used as a library for project creation and mismatch resolution, not as the live definition directly referenced by an established project.

**Limitation / quirk**

This is flexible but creates a historically layered configuration system in which behavior can depend on multiple text fragments and override rules.

**EpicHub conclusion — Improve**

Keep the mature capability—stable project-local semantic authority plus controlled overrides—but prefer a typed, resolved profile model with explicit provenance over reproducing a many-file textual override chain in runtime logic.

A useful EpicHub profile should be able to answer:

- what firmware identity was matched;
- which definition/profile version supplied each semantic item;
- whether user/project overrides altered it;
- whether the resolved profile is still compatible with the currently connected ECU.

---

# 2. Inputs and dependencies

## 2.1 Definition-driven communication geometry

**Observed behavior**

Representative ECU definitions declaratively describe communication details including:

- firmware query command and expected signature;
- protocol-envelope mode;
- endianness;
- page count and sizes;
- page identifiers;
- page-read commands;
- single/chunk write commands;
- per-page Burn commands;
- block-read timeout;
- write-block behavior;
- page-activation/inter-write delays;
- CRC/check commands;
- runtime-output block size;
- runtime-output read command;
- optional/scattered runtime-read behavior.

**User-visible promise behind the implementation**

Different ECU firmware generations can expose different protocol/memory geometry without requiring a separate hard-coded desktop application for each one.

**EpicHub conclusion — ★**

Communication semantics should be capability/profile driven wherever firmware legitimately varies.

Avoid scattering firmware-family conditionals through Android UI, transport, logger, and tuning code.

The Rust semantic engine is the natural EpicHub owner for a validated/resolved ECU profile and the communication operations derived from it.

---

## 2.2 Runtime channels are definition-owned semantics

**Observed behavior**

The same ECU definition that identifies the firmware also declares runtime-output geometry and channel decoding.

This couples verified firmware identity to the interpretation of runtime bytes.

**EpicHub conclusion — ✓ / ★**

EpicHub's existing direction toward generated-INI runtime-channel ownership is compatible with this mature expectation.

The improvement opportunity is to enforce this through typed profile authority in Rust rather than allowing arbitrary UI consumers to parse offsets/scales independently.

---

# 3. Edge cases

## 3.1 Firmware signature mismatch is a first-class state

**Observed behavior**

TunerStudio contains an explicit firmware-signature mismatch workflow. User-facing resources expose four possible actions:

- open a different project;
- update the project's ECU definition;
- connect anyway;
- work offline.

The warning states that the project's serial signature must match the controller-reported signature for correct communication and explicitly warns against using an incorrect ECU definition.

**Implementation evidence**

Limited class inspection confirms that this is implemented as a dedicated mismatch dialog/state, not merely as log text. It also shows that the availability of "Connect Anyway" is conditional rather than universally offered.

No proprietary implementation code is retained here.

**EpicHub conclusion — ✗ / ★**

EpicHub needs an explicit incompatible-profile session state before live tuning exists.

A useful state model should distinguish at least:

- transport opened, ECU not identified;
- ECU identified, compatible profile resolved;
- ECU identified, project profile mismatch;
- ECU identity recognized but compatible profile unavailable;
- user selected offline/project-only mode;
- connection failed/unavailable.

Do not collapse these into a generic error banner.

**Deliberate improvement**

EpicHub should be stricter than legacy desktop behavior around unsafe overrides. A generic "connect anyway" should not grant tuning/write authority if channel/memory geometry cannot be proven safe.

If an expert override exists at all, its resulting capabilities should be explicitly restricted.

---

## 3.2 Known firmware but wrong project

**Observed behavior**

TunerStudio can offer to update the project's ECU definition when the connected firmware is recognized by the installed definition library. User-facing text describes this as a common path after updating firmware on the same car/ECU, while warning that it is not appropriate for switching to a different ECU family.

**EpicHub conclusion — ★**

Treat firmware-update migration and "wrong vehicle/project" as different workflows even if both initially look like an identity mismatch.

EpicHub should require enough identity/context to determine whether it is offering:

- profile upgrade within the same project/ECU lineage;
- project switch;
- project clone/migration;
- unsupported ECU handling.

---

## 3.3 Recognized controller with no available definition

**Observed behavior**

User-facing resources distinguish a controller/firmware being found from TunerStudio having an ECU definition capable of supporting it. The application can direct the user to browse to a firmware-supplied definition and, where available, obtain a matching definition separately.

**EpicHub conclusion — ★**

"ECU responded" and "EpicHub can interpret this ECU" must remain separate statuses.

Diagnostics should retain the raw identity result even when profile resolution fails so support can diagnose unsupported/new firmware without requiring a successful session.

---

## 3.4 A problematic port can be suppressed from future scans

**Observed behavior**

TunerStudio exposes a UI message stating that a COM port may be disabled from detect scans because previous attempts to access it caused issues. The user can explicitly reactivate it.

**Implementation evidence**

Limited class inspection confirms a persisted/list-managed disabled-port mechanism and an explicit re-enable action.

**Mature lesson**

Blindly probing every enumerated transport forever can itself create reliability problems with some drivers/devices.

**EpicHub conclusion — △ / ★**

Android USB discovery differs from desktop COM-port scanning, so the exact mechanism should not be copied.

However, EpicHub should remember repeatedly failing/problematic device candidates where repeated automatic opening causes churn, permission loops, driver errors, or reconnect storms.

Any suppression must be inspectable and reversible by the user.

---

# 4. Persistence

## 4.1 Current tune is continuously checkpointed

**Observed behavior**

Bundled help says "Automatically Load and Save Current Tune" is enabled by default. `CurrentTune.msq` is updated after page Burn and on project close, then loaded again when the project opens.

The example project contains `CurrentTune.msq`.

**Mature lesson**

The user's working state should survive normal application/project lifecycle without requiring them to remember an explicit "Save As" workflow after every tuning change.

**EpicHub conclusion — ✗ / ★**

EpicHub will need a durable working-tune model distinct from controller persistence.

Future design must explicitly separate:

- local working tune;
- last synchronized controller RAM state;
- controller nonvolatile/Burn state;
- named/exported tune files;
- restore/history checkpoints.

Avoid representing all five as one ambiguous "saved" boolean.

---

## 4.2 Restore points protect destructive reconciliation

**Observed behavior**

The example project contains a `restorePoints/` history spanning multiple dates.

Bundled help documents automatic restore points:

- when closing a project if settings changed;
- around difference-report reconciliation;
- specifically before replacing local settings with controller settings.

User-facing strings also describe saving a restore point before accepting controller values.

**User-visible promise**

Resolving a controller-vs-local disagreement should not destroy the previous local tune state.

**EpicHub conclusion — ✗ / ★**

Before any future "Use ECU" / "replace local state" action, EpicHub should create an atomic recoverable checkpoint or equivalent immutable history entry.

This is more important than reproducing TunerStudio's exact restore-point folder format.

---

## 4.3 Project settings themselves are backed up

**Observed behavior**

The supplied example project contains both:

- `project.properties` and `project.properties.bkup`;
- persisted channel values plus a `.bak` copy.

**Inference**

These files suggest defensive handling of project/persisted-state corruption or interrupted replacement, though this slice has not yet proven exactly when each backup is created/restored.

**EpicHub conclusion — △**

EpicHub persistence should use atomic replacement/journaling or database transaction semantics such that interrupted writes do not strand the project in a half-written state.

Do not copy suffix-based backup behavior unless it provides a useful export/debug affordance on top of stronger atomic storage.

---

# 5. Failure / fallback behavior

## 5.1 Offline mode is a supported workflow, not only an error condition

**Observed behavior**

TunerStudio exposes "Work offline" as a deliberate action during firmware mismatch and displays an explicit offline state.

Its project-local ECU definition and CurrentTune model allow meaningful editing without an attached ECU.

**EpicHub conclusion — ✗ / ★**

EpicHub should support a first-class offline project/tune session.

Capabilities should be explicit:

- project/tune browsing and editing: available;
- live runtime data: unavailable/stale;
- ECU reads: unavailable;
- ECU writes/Burn: unavailable;
- offline analysis/generation features: available when inputs exist.

Do not emulate "disconnected" by feeding zero-valued live channels through normal online code paths.

---

## 5.2 Operations can fail terminally when the controller disappears

**Observed behavior**

For controller-dependent SD-card operations, implementation evidence shows explicit online checks and terminal errors if the controller goes offline. A bounded wait path also terminates after a timeout instead of waiting indefinitely.

**Mature lesson**

Higher-level operations need cancellation/failure semantics tied to session generation/connection state; they cannot assume the transport remains valid for the whole workflow.

**EpicHub conclusion — ★**

Every future multi-step ECU operation should execute against a specific session generation and abort deterministically if that session becomes stale/disconnected.

A reconnect must not silently allow an old in-flight operation to continue against a new ECU/session instance.

---

## 5.3 Communication history can be retained for post-failure diagnosis

**Observed behavior**

Global settings document a bounded history of recent controller interactions that may be retained in memory and dumped when a communication exception occurs, even when full communication debug logging is disabled.

The shipped default has the count set to zero, so the capability is present but not active by default in this package.

**EpicHub conclusion — ✓ / ★**

EpicHub's bounded DiagnosticHub/Rust diagnostic-ring architecture is already stronger conceptually.

Future ECU transport diagnostics should add bounded structured communication events sufficient to reconstruct the lead-up to a failure without enabling unbounded packet logging.

Sensitive or high-volume raw payload capture should remain opt-in/limited.

---

# 6. Local tune vs controller reconciliation

## 6.1 Difference reporting is a normal connection concern

**Observed behavior**

TunerStudio has a preference named "Perform Difference Report On Connect".

User-facing resources describe:

- reading controller settings;
- generating a difference report;
- detecting differences between the settings currently in TunerStudio and settings found in the controller;
- requiring the user to select which settings to use.

**Implementation evidence**

Limited inspection confirms this is an actual configurable connection workflow rather than documentation-only terminology.

**EpicHub conclusion — ✗ / ★**

A future EpicHub online transition must not assume that the local working tune and controller tune are identical merely because the ECU identity/profile matches.

The session handshake should establish tune-sync state separately from identity state.

Suggested semantic states:

- identity compatible, tune equality not checked;
- tune equal;
- tune differs;
- comparison incomplete/failed;
- user chose local as authority;
- user chose ECU as authority;
- merge/manual resolution required.

---

## 6.2 Reconciliation is guarded by restore history

**Observed behavior**

TunerStudio explicitly offers a restore point before replacing local TunerStudio settings with controller settings.

**EpicHub conclusion — ★**

Controller/local reconciliation should be modeled as a transactional state transition:

1. capture immutable pre-transition local state;
2. verify session generation and compatible profile;
3. read/compare controller state;
4. obtain explicit resolution choice when needed;
5. commit the selected authority atomically;
6. retain a human-readable diff/history record.

This is preferable to a destructive "read ECU" button that simply overwrites memory.

---

# 7. Performance / caching

## 7.1 Search probes are bounded/configured rather than unconstrained

**Observed behavior**

Device detection uses configured query-command and baud-rate candidates. Implementation evidence shows the scan iterating configured search interfaces/settings rather than using an unbounded generic probe strategy.

**EpicHub conclusion — △**

Android USB candidate discovery should be more deterministic because hardware descriptors/endpoints are available before protocol probing.

Still, protocol-level identification should use a bounded handshake with explicit deadlines and cancellation.

---

## 7.2 Definition data avoids rediscovering protocol geometry at runtime

**Observed behavior**

Protocol, runtime-channel, memory-page, and timing characteristics are mostly declared in the ECU definition.

**Mature lesson**

Resolve/validate communication geometry once per profile/session rather than repeatedly rediscovering or reparsing it on each read/edit/render path.

**EpicHub conclusion — ★**

Compile/resolve generated INI/profile input into a validated immutable runtime representation owned by Rust. UI/runtime consumers should work with semantic IDs/operations rather than repeatedly parsing textual configuration.

---

# 8. Relevant implementation evidence

Implementation inspection in this slice was intentionally narrow.

It was used only to confirm that:

- signature mismatch is represented by a dedicated workflow with multiple actions;
- "Connect Anyway" can be conditionally unavailable;
- device scanning iterates defined search interfaces/settings and has cancellation/delay behavior;
- problematic COM ports have an explicit disable/reactivate path;
- controller-dependent SD operations check connection state and terminate on offline/timeout;
- the difference-report-on-connect option exists in executable behavior.

No implementation code or proprietary architecture is reproduced here.

---

# 9. Limitations / quirks to avoid copying blindly

## 9.1 Generic "Connect Anyway"

**Reject as a default EpicHub behavior.**

The mature lesson is that expert users sometimes need diagnostic escape hatches. The unsafe part is granting normal semantic access when firmware/profile compatibility is unproven.

EpicHub should prefer a restricted diagnostic session over a fully empowered incompatible session.

## 9.2 File-fragment override complexity

**Improve.**

Project-local semantic stability is valuable. Runtime dependence on loosely layered text fragments is not inherently desirable.

Resolve to a typed, validated profile with provenance.

## 9.3 Desktop COM-port heuristics

**Do not copy literally.**

Android USB enumeration/permissions have different failure modes. Preserve the concept of reversible suppression/backoff for repeatedly problematic candidates, not the COM-port UI itself.

## 9.4 UI event as persistence policy

TunerStudio historically ties some safety behavior to events such as closing dialogs/page transitions. That behavior is investigated in the write/Burn slice.

EpicHub should derive persistence from explicit semantic transaction state, not from incidental UI lifecycle where possible.

---

# 10. EpicHub mapping summary

| Finding | EpicHub status | Decision |
|---|---|---|
| Transport candidate separate from firmware/profile authority | ✓ direction already documented | Keep and enforce |
| Project-stable semantic definition/profile | △ architecture needs concrete persistence contract | ★ Add durable profile identity/resolution |
| Definition-driven runtime/protocol capability discovery | ✓ direction partially established | ★ Resolve into typed Rust authority |
| Explicit firmware/profile mismatch state | ✗ | ★ Add session states and restricted mismatch handling |
| Offline project/tune mode | ✗ | ★ First-class capability model |
| Tune equality checked separately from firmware compatibility | ✗ | ★ Add tune-sync state |
| Restore before destructive local/ECU reconciliation | ✗ | ★ Mandatory transaction/history invariant |
| Bounded communication diagnostics | ✓ diagnostic foundation exists | ★ Feed structured transport/session events into it |
| Reversible backoff for problematic device candidates | △ | Validate against Android USB failure modes |
| Generation-safe cancellation of multi-step ECU operations | △ planned lifecycle principles exist | ★ Make explicit contract |

---

# 11. Suggested regression tests

## Identity and profile resolution

1. **Known device, matching profile**
   - transport opens;
   - identity query succeeds;
   - compatible profile resolves;
   - runtime acquisition may start only after resolution.

2. **Known USB VID/PID, unknown firmware signature**
   - transport candidate is visible diagnostically;
   - session does not become semantically online;
   - no runtime decoding/write authority is granted.

3. **Project profile differs from connected firmware**
   - mismatch state is explicit;
   - no automatic destructive migration;
   - user can choose project switch, supported profile migration, or offline mode;
   - unsafe normal write authority remains blocked.

4. **Recognized firmware with missing local profile**
   - identity is retained;
   - profile-resolution failure is distinguishable from transport failure.

5. **Malformed/internally inconsistent profile**
   - validation fails before runtime acquisition;
   - no partial channel map becomes active.

## Connection generation / recovery

6. **USB disconnect during identity handshake**
   - handshake cancels;
   - no stale profile/session becomes active.

7. **USB disconnect during runtime acquisition**
   - live channel validity changes deterministically;
   - values are not silently presented as fresh zeros;
   - diagnostics record disconnect cause/generation.

8. **Reconnect same physical ECU**
   - new session generation created;
   - identity/profile revalidated;
   - stale operations from previous generation cannot resume.

9. **Reconnect different ECU on same USB candidate identity**
   - prior semantic identity is not reused;
   - firmware/profile mismatch is surfaced before runtime interpretation.

10. **Repeated failing candidate**
    - reconnect/backoff remains bounded;
    - user can explicitly retry/reset suppression;
    - no reconnect storm.

## Project/tune reconciliation

11. **Local tune equals ECU tune**
    - session reaches synchronized state without destructive prompts.

12. **Local tune differs from ECU tune**
    - mismatch is explicit;
    - neither side is overwritten before resolution.

13. **User accepts ECU state**
    - immutable local checkpoint exists first;
    - replacement is atomic;
    - history/diff references both states.

14. **Failure while reading ECU for comparison**
    - local working tune remains intact;
    - no half-applied controller image becomes authoritative.

15. **App/process death during project persistence**
    - last committed tune/profile/project state remains readable;
    - no mixed-generation project metadata.

## Offline behavior

16. **Open project without ECU**
    - editing/project navigation works according to offline capability contract;
    - live-only operations are unavailable with explicit reason.

17. **Transition online → offline → online**
    - offline edits remain local;
    - reconnect triggers identity and tune-difference validation before ECU mutation.

---

# 12. Open questions for runtime validation

The package gives strong evidence for the behaviors above, but the following should still be exercised in a controlled TunerStudio runtime session before calling the connection slice fully closed:

- exact automatic reconnect timing/backoff after a live serial disconnect;
- whether dashboard channels freeze, invalidate, blank, or otherwise visually indicate stale data during transport loss;
- whether signature is re-queried on every reconnect or under which circumstances cached identity is reused;
- exact ordering of signature validation versus initial controller tune reads;
- when project-property `.bkup` and persisted-channel `.bak` files are created/restored;
- whether a difference report on connect is skipped/changed after known-clean shutdown or cached equality;
- precise behavior when "Connect Anyway" is used on an allowed firmware family;
- handling of application sleep/suspend and serial-device disappearance/reappearance.

These questions become the next runtime-observation portion of this same subsystem before moving fully into write/Burn semantics.
