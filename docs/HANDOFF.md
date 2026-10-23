# EpicHub Current Handoff

## Qualified firmware-fixture runtime bitfield support — 2026-10-23

Foundation [39924037372](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39924037372) passed at **exact SHA `2b5d9385b4e82eae8985d4b30475d26166e0fa1f`**, promoted to `main`. Native `runtime_channel_decode.rs` now decodes unsigned 8/16/32-bit inclusive `bits` ranges with explicit range bounds and byte order. `runtime_definition_catalog.rs` compiles `bits, U08/U16/U32, offset, [first:last]` entries alongside supported numeric scalars. The fixture-derived tests check `RPMValue` and `miniOch` from `PJawZK/epicefi_fw_jzfork` `firmware/tools/ts_ini_testdata/mini.ini`, plus unsupported `miniFormula = { 100 + 1 }` and invalid bit ranges. Compatibility is verified **for the extracted fixture declaration syntax**, not for complete real generated ECU definitions.

Firmware source audit found: `firmware/console/binary/output_channels.txt` is generator source, not a complete generated TunerStudio INI. `firmware/tunerstudio/tunerstudio.template_zh.ini` has `[OutputChannels]`, `ochGetCommand = "O%2o%2c"`, `ochBlockSize = @@TS_TOTAL_OUTPUT_SIZE@@`, `@@OUTPUTS_SECTION_FROM_FILE@@`, and many calculated `{ ... }` channels. The available `mini.ini` is a synthetic firmware validator fixture, **not** a generated Mega144H7 runtime catalog. Do not claim general EpicEFI output compatibility, expression support, or actual USB runtime acquisition.

Next narrow work: obtain/generate representative fully resolved Mega144H7 and uaEFI INIs with exact firmware/signature provenance; inventory every output-channel form, metadata and command; add explicit unsupported capability reports; bind channel catalog generation to the selected live ECU session; verify runtime command/envelope/range semantics before starting continuous polling; then batched native channel publication and Dashboard/Tuner consumers. Preserve read-only runtime behavior and keep Burn UI disabled.

This handoff documentation commit requires its own exact-head Foundation qualification before promotion.

---

## Qualified generic runtime scalar definition compiler — 2026-10-23

Foundation [39921602400](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39921602400) passed at exact commit `2dc7abe7a106b1a6df6e86acd489f3a94775c9e5`, promoted to `main`. The native `runtime_definition_catalog.rs` now compiles strictly supported fixed-offset numeric `scalar` declarations from *resolved* `[OutputChannels]` definitions into the previously qualified `CompiledRuntimeChannelCatalog`. Channel name, units and deterministic name-derived ID are retained. Parsing bounds, duplicate names, unsupported expression/type rejection, byte ordering and sample decode tests qualified.

**Limitations / next boundary:** This is a strict subset compiler, not compatibility with all real/generated EpicEFI INIs. Real definitions may include bits, expressions, arrays, extra metadata, macros, alternate command/block layouts and runtime conditionals not yet accepted. A representative generated INI corpus must establish actual syntax and compatibility before live activation. No `ochGetCommand` request is issued by this compiler, no continuous USB output stream is connected, no JNI runtime batches are wired to the native channel store, and the Dashboard remains synthetic. Do not claim hardware qualification or complete definition support. Next: fixture-backed generated INI coverage with explicitly classified unsupported declarations, command/envelope acquisition contract, then guarded live-session runtime publisher and bounded batched snapshots.

This documentation-only commit must qualify at its own exact Foundation SHA before promoting `main` further.

---

## Qualified read-only nonvolatile projection — 2026-10-23

The native page-scoped Burn evidence projection is qualified at `84b77de1788bd932875bc116115af7844361f498` by Foundation [39914326976](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39914326976), and this exact SHA was promoted to `main`.

Implemented: a versioned 48-byte `NVP1` native snapshot, read-only JNI `fillNonvolatilePageState`, Kotlin `NonvolatilePageState` decoder, and `NativeEngine.readNonvolatilePageState(page, verifiedSignature)`. States are **Unknown**, **PostBurnCrcVerified** (evidence from the existing ordered guarded Burn ACK + post-Burn CRC contract) and **StaleEvidence**. Projection is read under the engine registry lock and compares signature fingerprint, page CRC, profile generation, current revision and live-source origin. Unknown carries no CRC evidence. Kotlin/native malformed-state tests qualified. No Burn UI was added; the Android UI does not yet consume this API.

Do **not** treat post-Burn CRC evidence as verified flash contents after a power cycle. The public read method accepts a signature provided by its caller: only use it in UI once the active verified connection/session owner supplies and gates the current signature, and connection teardown invalidates/prohibits stale displays. Independent physical ECU tests and explicit reconnect/disconnect authority are still outstanding. The snapshot's session epoch describes the current engine session and is not an externally validated ECU identity. The immediate next safety work is binding this read-only projection to the retained Android live connection owner, checking source teardown and interleavings, then proceeding to generic continuous telemetry, live gauges and full tuning controls. EpicScope remains the Analyzer.

The documentation-only commit adding this note must pass Foundation at its own exact SHA before promotion.

---

## Qualified Burn source-epoch safety checkpoint — 2026-10-23

Foundation run [39912522960](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39912522960) succeeded at commit `c7445fe4e5410a64fda704ec10023597edf86304`, and `main` was fast-forwarded to that exact SHA. The guarded native Burn plan now captures the engine tune-session epoch, rejects equal-byte source reinstallation before Burn or after Burn ACK, and requires the epoch again before recording page persistence evidence. Native regressions passed. No Burn UI or hardware testing was introduced.

The immediately prior qualified checkpoint `24c0d6a` introduced separate native page-scoped nonvolatile Burn evidence, cleared upon source replacement and invalidated on later edits. Read-only production projection, explicit connection/session teardown authority, concurrency/interleaving audit, independent physical persistence checks, and final user Burn controls remain open. Prioritize these narrow safety boundaries before expanding the agreed generic ECU connection, live Dashboard and full tuning capabilities. EpicScope owns Analyzer functionality.

This documentation update requires its own exact-head Foundation qualification.

---

## Product focus update — 2026-10-23

The user confirmed that **EpicHub's product focus is generic ECU connections, live gauges/Dashboard, and complete ECU tuning capabilities**. EpicScope is the mature Analyzer product; do not build a competing EpicHub Analyzer. Keep optional future integration open but do not make EpicScope an implementation prerequisite. Supporting logging/interoperability remains secondary to the core ECU experience.

The source-linked feature inventory, identified gaps, and dependency-ordered roadmap are in [`docs/architecture/CONNECTION_DASHBOARD_TUNING_ROADMAP_2026-10-23.md`](architecture/CONNECTION_DASHBOARD_TUNING_ROADMAP_2026-10-23.md). This product-direction update **does not supersede the immediate native guarded-Burn proof-state correctness gate below**. First qualify that gate, then prioritize sustained definition-driven live runtime acquisition and gauges in parallel with full non-table tuning breadth. Treat code presence, automated CI qualification, physical hardware qualification, and user-facing completeness as separate statuses.

This is a documentation-only update; Foundation must qualify its exact resulting `current-work` SHA before `main` advances.

---


## Continuation instruction

Read this file first, then verify the exact `main`, `current-work`, and Foundation state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Branch model:

- `main` = latest fully qualified baseline.
- `current-work` = the only active development branch and must remain based on / ahead of `main`.
- Historical commits, closed PRs, and workflow runs are the audit trail; do not recreate long-lived phase/feature branches.
- Every meaningful `current-work` checkpoint must pass the complete Foundation workflow on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- `fetch_commit_workflow_runs` is not authoritative here because Foundation is push-triggered; query the Actions workflow/runs directly.

---

# Latest qualified implementation — 2026-10-17

Latest qualified implementation checkpoint:

`9c5e332b737085e93ab84531199909b9d5b5c9a6`

Foundation quality run:

`38982149150`

That exact SHA passed the full Foundation gate:

- exact source SHA verification;
- Rust format;
- Rust Clippy with warnings denied;
- all Rust tests;
- Android JVM tests;
- Android debug build;
- Android performance build;
- Android Macrobenchmark harness build;
- APK signature verification;
- production ARM native-library verification;
- physical benchmark-kit preparation;
- all qualified artifact publication.

`main` and `current-work` were both at that SHA before this handoff-only documentation update.

Important recent qualified checkpoints:

- `5b585b6096c862d704d90fec61c7e6bdd8edb10e`, Foundation `38949529443` — firmware-backed Burn envelope codec/verifier. Burn request is definition-owned; EpicEFI Burn success status is `4`, not normal command success `0`; Burn ACK is not persistence proof.
- `d894c50b683e814723ca58faa13a30e64265e7e5`, Foundation `38954935032` — opaque native guarded Burn plan/store + JNI + Android owner. Android receives only opaque-handle-owned staged requests and cannot construct arbitrary Burn geometry.
- `c8c4a8bf52b0734aa09875b2eb48fc434b9bed96`, Foundation `38981181683` — serialized physical USB Burn executor. Exact sequence is Burn request/ACK verification, then full-page post-Burn CRC request/verification, all inside `EngineAuthoritySerialization`.
- `9c5e332b737085e93ab84531199909b9d5b5c9a6`, Foundation `38982149150` — high-level live Burn routing through exact live identity and the retained authorized USB session, with no Burn UI exposure.

Earlier qualified history contains the complete volatile RAM-write chain, explicit RAM provenance/synchronization state, zero-prefix interruption invalidation, serialized JIT authority → USB transfer → reconciliation, and the visible Write-to-RAM workflow. Do not rebuild those boundaries.

---

# Core invariants

Keep these distinctions explicit:

`TuneDraft Apply != ECU RAM write != Burn / nonvolatile persistence`

Additional authority invariants:

- Rust remains semantic tuning and ECU definition/profile authority.
- Exact full runtime signature selects the exact compiled definition/profile/protocol bundle.
- Definition-owned semantic IDs and page protocol metadata own geometry; Android/UI must not construct raw page/offset commands.
- One engine-owned `TuneSession` remains the semantic editing authority.
- The retained exact source page image remains the byte authority for guarded writes and Burn eligibility.
- RAM writes remain guarded by retained-before precondition read, exact verification, write ACK verification, post-write readback, exact verification, and verified-prefix reconciliation.
- A Burn is eligible only for a verified live-controller source whose RAM provenance confirms the current draft revision.
- Burn is definition-page scoped. Multi-page ECUs may have distinct `B%2i` commands and may have pages with no Burn capability.
- Burn request framing and response verification remain native/definition-owned.
- EpicEFI Burn success ACK status is `4`; ordinary status `0` must not be accepted as Burn success.
- Burn ACK alone never proves persistence.
- Current persistence proof contract is: verified Burn-specific ACK followed by a matching definition-owned full-page post-Burn CRC response for the exact guarded source page.
- All physical Burn transfer and native verification run under `EngineAuthoritySerialization`, preventing tune/profile/source authority mutation across the operation.
- Burn is not exposed in Compose/UI yet.

---

# Current Burn architecture

The qualified path now reaches the retained physical USB channel:

```text
verified live TuneSession + exact retained source image
    ↓
RAM provenance confirms current draft revision
    ↓
plan_selected_engine_guarded_page_burn(...)
    ↓
opaque GuardedBurn plan handle
    ↓
NativeGuardedBurn (Android opaque owner)
    ↓
EngineAuthoritySerialization::exclusive
    ↓
stage definition-owned framed Burn request
    ↓
retained authorized UsbReadOnlyTransactionChannel
    ↓
verify Burn-specific ACK (status 4)
    ↓
stage definition-owned full-page CRC request
    ↓
same retained authorized USB channel
    ↓
verify CRC matches exact guarded source page
    ↓
UsbGuardedBurnResult.PersistedAndVerified
```

The high-level routing is deliberately below UI:

- `NativeLiveTuneBurnOperation` creates/owns the opaque guarded Burn plan and invokes the serialized executor.
- `LiveTuneConnectionOwner.burnPageToNonvolatile(...)` resolves the exact live runtime identity from lifecycle state; connected-but-not-loaded state cannot Burn.
- `AndroidUsbLiveConnectionSessionOwner.burnPageToNonvolatile(...)` supplies only the already-retained authorized USB channel.
- A disconnected, closed, stale, non-live, RAM-unconfirmed, profile-stale, source-changed, protocol-failed, CRC-failed, or transport-failed path remains explicit and fail-closed.

Focused JVM tests prove:

- Burn stage must succeed before CRC stage executes;
- rejected Burn ACK stops before CRC;
- CRC mismatch is not reported as persistence success;
- transport failure remains stage-specific;
- invalid timeout fails before native/USB work;
- one serializer scope covers both physical stages and both native verification calls;
- live routing forwards exact live identity, internal definition page, and caller/retained channel;
- connected-but-not-loaded, closed, and disconnected states cannot reach Burn authority.

---

# Immediate next correctness boundary

Before recording persistent/nonvolatile provenance or exposing any Burn control in the UI, tighten the opaque native Burn handle into a native proof-state machine.

Required native sequence:

```text
Planned
  ↓ verified Burn-specific ACK
BurnAckVerified
  ↓ verified matching post-Burn full-page CRC
PostBurnCrcVerified
```

Why this is required:

- the Kotlin physical executor already executes Burn then CRC in the correct order;
- however the opaque native handle currently exposes Burn and CRC verification as independent calls;
- native persistence evidence must not be recordable from a CRC response alone;
- native authority should enforce the proof sequence even if a caller misuses the JNI wrapper.

Recommended next slice:

1. Add execution/proof state to the opaque guarded Burn plan store, not Kotlin bookkeeping.
2. Reject CRC verification before Burn ACK has been successfully verified.
3. Prefer making the handle one-shot after full proof: no second Burn/CRC staging from a completed plan.
4. Preserve the existing lock-order rule: retain plan/state safely, release the plan-store mutex before entering profile/engine authority, then transition state only after successful native verification.
5. Add dedicated native error/status mapping for invalid Burn proof-stage ordering rather than collapsing it into transport failure.
6. Test out-of-order CRC, duplicate/repeated stages, stale authority during each transition, close/race behavior, and normal `Planned → BurnAckVerified → PostBurnCrcVerified` flow.

Only after that state machine is qualified should persistent provenance be recorded.

---

# Persistence provenance after proof-state qualification

There is currently no native nonvolatile persistence provenance model. Do not treat the existing `RamSourceProvenance` as proof of flash/nonvolatile state.

The next layer after the proof-state machine should record native evidence tied to at least:

- exact profile generation;
- exact draft revision;
- definition page number;
- exact page CRC32;
- proof method (`Burn ACK + post-Burn page CRC`);
- session/source authority that produced the evidence.

Important lifecycle semantics:

- fresh controller page acquisition proves current RAM bytes, not nonvolatile persistence; persistence evidence should begin unknown unless separately verified;
- local draft edits should make a previously verified persisted revision visibly older, not erase history;
- RAM writes should not automatically claim persistence;
- a new source/session/profile install must invalidate persistence evidence belonging to the previous authority;
- re-Burning one page should update that page's persistence evidence without claiming unrelated pages are persisted;
- only exact post-Burn CRC verification after the required Burn ACK may advance persistence evidence.

A separate app-facing persistence snapshot/state can then be designed, analogous to RAM synchronization state but page-aware and explicitly nonvolatile.

---

# UI boundary — still intentionally not started

Do not add a Burn button yet.

Before user-facing Burn:

1. qualify native Burn proof-state ordering;
2. qualify nonvolatile persistence provenance and an app-facing read-only state projection;
3. decide how the selected semantic table maps to definition page(s) without presenting raw page numbers as normal UX;
4. define confirmation and failure presentation that clearly distinguishes:
   - local Apply;
   - volatile RAM current;
   - nonvolatile page persistence verified;
   - unknown/stale persistence;
5. keep multi-page/partial Burn truthful — never claim the whole tune is persisted when only one page has proof.

---

# Repository discipline

At the start of the next session:

1. inspect `main` and `current-work` heads;
2. inspect the latest Foundation run on `current-work`;
3. if this handoff-only SHA is fully green, promote that exact SHA to `main` and verify both refs;
4. if it is not green, fix only the exact failing gate;
5. continue with the native guarded-Burn proof-state machine above.

Do not recreate old phase branches, old RAM-write work, or the already-qualified Burn planner/envelope/opaque-wrapper/USB-routing layers.


---

# Consolidated project chronology from the long EpicHub development conversation (2026-10)

This section preserves the development decisions and audit trail from the long conversation that led to the current Burn boundary. The qualified implementation and "Immediate next correctness boundary" sections above override older intermediate states below. Do not restart completed slices based on older chat messages.

## Repository consolidation and qualification policy

The repository formerly used dozens of stacked \`phase1/*\`, \`phase2/*\`, and research branches with draft PR checkpoints. The user approved consolidation after qualifying PR #81. The original guarded-source-reconciliation primitive initially failed rustfmt, then a concurrent test's assertion against a process-global guarded-batch count exposed nondeterministic test isolation. Both were corrected without expanding production write behavior. PR #81 reached exact green checkpoint \`8f7bd82dfaf967d7576b37482af5f9a4b3699fc7\` (Foundation \`38858098167\`).

\`main\` was advanced to the qualified stacked lineage while preserving commit history, \`current-work\` was established as the only active development line, Foundation triggering was updated for the new branch, and stale overview documentation was corrected. The historical PR queue was closed (zero open at cleanup) and the user physically removed historical remote branch refs from a local clone, verifying that only \`main\` and \`current-work\` remained and matched \`d44bd6b121301bf25ce1afd2c1858c29d7f1722e\`. Do not recreate the stacked-PR branch forest.

Repository rule: edit only \`current-work\`; require a completed green Foundation run for **the exact candidate SHA** (format, Clippy, Rust tests, Android JVM, builds, signatures/native libraries, artifacts); then advance \`main\` only to that qualified SHA. Intermediate failed/cancelled runs are not qualification. A handoff-only change also triggers qualification; avoid unnecessary self-referential documentation commits. Use the explicit branch-ref operation for promotion, not the file update endpoint (which formerly produced content-identical accidental commits).

## Completed RAM-write architecture, in development order

The Tuner is definition-driven rather than Mega144H7-hardcoded. The exact full runtime signature selects a coherent compiled definition/profile/protocol bundle; Rust owns semantic IDs, \`TuneSession\`, \`TuneDraft\`, page geometry, encoding, and write planning. Android is an authorized transport/UI executor, never a second source of raw write geometry.

- Guarded RAM writing developed through retained \`TuneSourcePageImage\`, source-relative semantic edit intents, an opaque native batch store, exact-before precondition reads, framed writes with firmware ACK, mandatory exact readback, and contiguous verified-prefix reconciliation.
- Engine-owned reconciliation atomically advances retained bytes without rewriting draft/history; the batch store clones the batch under its mutex and releases that mutex before entering engine/profile authority. The engine-owned slice qualified at \`97f7f2add2421e86d2ea3ef979e6af7b768e129c\`; opaque batch-store reconciliation at \`d44bd6b121301bf25ce1afd2c1858c29d7f1722e\`.
- Dedicated JNI reconciliation and Kotlin verified batch executor propagate the exact verified contiguous prefix, preserve transport errors, and fail closed on reconciliation errors. Qualified at \`0fa9e3867a62492563c42b180a33bfe81e8fade7\` (Foundation \`38866632546\`).
- Explicit native \`RamSourceProvenance\` separates verified source bytes, current draft revision, confirmed RAM revision, exact verified ranges and interrupted write evidence; partial verification is never presented as whole-draft synchronization. Qualified at \`65a588d2b48dfaba460ccacfbfdb75ce2209c4ff\` (Foundation \`38869610375\`).
- \`EngineAuthoritySerialization\` closes the authorization-to-physical-USB race across JIT native authority, pre-read/write/readback and native reconciliation, with competing tune/profile/source mutations sharing the same gate. Qualified with handoff at \`a96f3fccff328661466553b954c0c33cc46f8801\` (Foundation \`38873022777\`).
- Critical zero-prefix correction: even \`Interrupted(0, …)\` invokes native reconciliation with prefix zero; retained bytes remain unchanged while whole-draft RAM confirmation becomes unconfirmed. Qualified at \`2e024fa8c54ef3e730b99e8dbf84599093da9695\` (Foundation \`38875188598\`).
- Native RAM-state classification/JNI snapshot supplies atomic app-facing evidence (not a generic \`isSynced\` Boolean), distinguishing live confirmed, current-draft confirmed, local draft ahead, partially verified, and interrupted/unconfirmed states. The snapshot is versioned and Kotlin rejects inconsistent/malformed data.
- High-level current-draft write orchestration resolves identity/authority through the loaded live owner and retained authorized USB session; Compose never supplies a raw page or offset. A compact Tuner ECU RAM panel displays provenance and conditional volatile \`Write draft to RAM\` / retry / RAM-current states. No Burn is implied. The visible slice qualified at \`a5ea66476561390e6f70775449c28c013fbdf7ce\` (Foundation \`38895119391\`) with documentation at \`4c1c3794ee96d1e8899cad1569bb0ca9f7dba977\` (Foundation \`38896125204\`).

Local Apply, volatile RAM write and nonvolatile Burn remain three distinct actions. Disconnection, source replacement or stale profile must not resurrect live authorization; fresh live load is required. Offline/imported source must not be falsely presented as current ECU RAM. Subsequent qualified work cleaned up \`TunerScreen\` source-aware presentation, multi-table behavior, and session-lifecycle coherence; consult current code and the qualified Burn sections above rather than revisiting obsolete offline-only UI assumptions.

## Burn research, safeguards and completed boundaries

The user approved progressing from RAM-write completeness into independently guarded Burn/persistence, but explicitly **not** exposing an unsafe Burn button before proof and provenance are established. Firmware investigation confirmed:

- EpicEFI uses the definition-owned Burn command (\`B\`, \`0x42\` for the examined implementation; profile/page variants must remain definition-driven), carried in the MegaSquirt CRC-framed envelope.
- Burn acknowledgement has success status \`4\` (\`TS_RESPONSE_BURN_OK\`), not normal success \`0\`, and must have the expected empty payload. The firmware invokes \`requestBurn()\` before responding.
- Burn ACK is **not proof of persistent storage**. Require the exact definition-owned full-page post-Burn CRC read and comparison with the guarded retained source.
- Eligibility requires a verified live-controller source, exact selected generation/session/source identity and the current draft confirmed in ECU RAM; page-scoped capability, multi-page limits and stale authority fail closed.
- No arbitrary Burn geometry, firmware commands or response interpretation should become Android/UI authority.

Checkpoint \`e3fd57ad…\` qualified native guarded Burn eligibility; \`5b585b6096c862d704d90fec61c7e6bdd8edb10e\` (Foundation \`38949529443\`) qualified firmware-backed Burn request/ACK framing and verification. The guarded transport stage encountered only formatter failure in its first run; the later completed and qualified steps are **not** limited to that intermediate checkpoint: opaque Burn handle/store + JNI/Android owner qualified at \`d894c50b683e814723ca58faa13a30e64265e7e5\` (Foundation \`38954935032\`); serialized Burn then CRC USB execution qualified at \`c8c4a8bf52b0734aa09875b2eb48fc434b9bed96\` (Foundation \`38981181683\`); retained live-session routing qualified at \`9c5e332b737085e93ab84531199909b9d5b5c9a6\` (Foundation \`38982149150\`).

**Do not interpret prior interrupted chat responses or their last pending CI run as the current repository state.** The current implementation already reaches the retained physical Burn transport channel. The **next correctness slice** remains a native opaque Burn handle proof-state machine enforcing \`Planned → BurnAckVerified → PostBurnCrcVerified\` in the native store, including out-of-order/repeat rejection and stale/closed/race tests. After it qualifies, add distinct page-scoped nonvolatile persistence provenance and read-only projection. Only then design the user-facing Burn workflow; do not equate a successful physical Burn command, or an arbitrary CRC response, with persisted proof.

## Branch and CI state when this historical appendix was added (2026-10-23)

Before the appendix commit, \`main\` was \`9c5e332b737085e93ab84531199909b9d5b5c9a6\` (Foundation \`38982149150\`, success), while \`current-work\` was one handoff-only commit ahead at \`202c4e72f38ef21347c24d9b72774ba272f314a0\` (Foundation \`38982939249\`, success). This appendix itself is another documentation-only commit and requires its own exact-head Foundation qualification before promoting \`main\`. Do not describe the appendix SHA as qualified merely because its parent is green.
