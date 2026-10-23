# EpicHub Current Handoff

## Qualified native bound runtime candidate — 2026-10-23

**Latest qualified production SHA:** `146418ddb9506c3b551ee53efbd34d4a4df21b9c`; exact-head [Foundation 39939012838](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39939012838) **success**. Verified `main` was an ancestor (8 commits, 0 behind) of `current-work`; fast-forward of `main` succeeded to the qualified SHA.

Added `rust/engine/src/runtime_bound_live_session.rs` and its `lib.rs` export. This is a **native-only, offline-testable candidate runtime authority layer**, not yet the retained Android USB owner's authorized live telemetry. A single `RuntimeBoundLiveSession` owns the `RuntimeAcquisitionSession`, `LiveChannelStore`, `CompiledRuntimeChannelCatalog`, `RuntimeOutputReadPlan` and descriptive channel metadata together. Its binding checks nonblank endpoint, exact observed/runtime profile signature, positive profile generation, present definition fingerprint and matching `[TunerStudio]` signature in the same resolved definition as the `[OutputChannels]` compiler and `ochGetCommand` / envelope. Unsupported definitions and read commands fail closed. Rebinding revokes prior Fresh authority, including failed rebinds. Native session epochs now use a process-wide checked monotonic atomic counter so destroying and recreating an owner cannot accidentally reuse an old request token (ABA). Bound lease fields must match for begin/complete/latest. Full-frame CRC/status/length/decode failures invalidate earlier Fresh readings, timeout/age marks them Stale, and disconnect prevents publication. Regression tests cover exact frames, signature/definition mismatch, corrupted CRC, non-finite values, retries, stale tokens, forged bindings, timeout/disconnect, and cross-owner epoch reuse. This stage makes **no JNI calls or physical ECU requests**, adds no USB polling and does not alter the synthetic Dashboard or Burn UI.

**Still-open authority boundary, do not skip:** The new Rust `bind()` presently accepts a profile object and resolved definition from an *internal* caller; it is **not** proof that those arguments came from the active native registry/USB session. The selected bundle must be retrieved through `profile_registry_store::with_selected_compiled_bundle()` (or a similarly exact registry-owned and generation-checked handle) so the catalog, command plan, profile fingerprint and definition source are tied to the same selected identity. The current `definition_revision` is an in-process FNV source-coherence marker, **not** a cryptographic digest or verified firmware provenance. The Android `AndroidUsbLiveConnectionSessionOwner` and `EngineAuthoritySerialization` must gate native bind/request/completion/read and synchronously revoke on disconnect, owner replacement, USB failure, permission loss, profile replacement and timeouts. Test race/interleaving and stale-callback invalidation with fake transport. No second USB owner; no unguarded JNI or synthetic/live mixing. This is a **qualified native substep**, not completed Task 2 runtime authority, and not live product telemetry.

**Next step:** Integrate native bundle/registry selection into the bound session behind the existing serialized connection authority; then add a guarded batched JNI/Kotlin read model and offline fake-port tests. Generated real ECU INI compatibility and physical Mega144H7 validation are still outstanding. Hardware work stays last. Do not work on an EpicHub Analyzer; EpicScope owns it.

This documentation-only handoff checkpoint must pass a separate exact-head complete Foundation run before promoting its SHA to `main`. The implementation SHA above is already qualified/promoted independently.

---

## Qualified session-fenced live channel store — 2026-10-23

**Qualified production SHA:** `0918702a5378cda977118d8d06f0ac9a92d79fe8`; exact-head Foundation [39934286409](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39934286409) **success**; promoted to `main`.

Added `rust/engine/src/live_channel_store.rs` as a bounded, real-data-only batch store separate from the pre-existing synthetic `ChannelStore` and ABI-v1 4-slot synthetic `RuntimeSnapshot`. `LiveChannelStore` accepts only nonzero session IDs, Fresh full-batch `RuntimePublishedSnapshot` inputs from the earlier acquisition state machine, nonzero unique channel IDs, finite values, at most `MAX_RUNTIME_CHANNELS`, strictly increasing sequence numbers and nonregressing monotonic millisecond timestamps. Snapshot publication replaces the entire accepted batch atomically; rejected requests leave the prior batch unmodified. It tracks current batch session, sequence, receive time and per-channel validity. `expire`, `mark_stale`, `mark_invalid` and `disconnect` remove Fresh authority without hiding prior numbers. Post-disconnect publication is rejected. Tests verify session/sequence/time rejection, timeout and disconnect invalidation, duplicate channel and non-finite value rejection. Rust + Android Foundation passed. No Android JNI, USB polling, synthetic display source change or physical ECU operations were made.

**Open integration boundary:** Bind the active, exact-selected ECU profile generation, signature, resolved definition fingerprint, `CompiledRuntimeChannelCatalog`, `RuntimeOutputReadPlan`, acquisition session and live batch store to the existing serialized `AndroidUsbLiveConnectionSessionOwner`. A caller-provided integer `session` alone is **not** proof of a live ECU identity. The Android owner must gate all calls and invalidate the native session/store synchronously on disconnect, rejected reconnect, owner close, timeout or USB failure; no live Dashboard reads should bypass these fences. Design JNI batched projections with explicit live source marker, catalog generation, validity, sample age and bounded channel count. Preserve the current synthetic ABI until a separately qualified versioned live ABI exists. Hardware-free fake-port tests should verify reconnect, incomplete frames, timeout, invalid CRC, stale callbacks and stale display prevention; generated real ECU INI compatibility is still outstanding. Physical ECU validation stays last.

This documentation-only checkpoint requires its own exact-head Foundation qualification.

---

## Current continuation authority — 2026-10-23

**Read this section first.** This section consolidates the October 23 continuation after the source audit and successive qualification checkpoints. The sections below are retained as **historical evidence**; older phrases such as "Latest qualified implementation — 2026-10-17", "next Burn proof-state gate" and "runtime compiler not implemented" are **not current instructions**. Verify branch heads, recent commits and Actions again before editing.

**Verified branch state before this handoff revision:** `main` = `0918702a5378cda977118d8d06f0ac9a92d79fe8`, the newly qualified native live-store milestone, Foundation [39934286409](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39934286409) **success**. Both branches matched that production SHA before the initial documentation-only commit `fad4490`. This handoff revision is on `current-work` **ahead of** `main`, and itself requires a successful complete Foundation run at the **revised exact SHA** before `main` advances. Verify branch heads and Actions rather than relying on these recorded hashes. Do not rewrite, bypass or silently cancel other concurrent qualified work.

### Product decision and priorities

**EpicHub = Connect · Monitor · Tune**: reliable definition-driven connections to different ECUs, live gauges/Dashboard, and complete ECU tuning (not only tables). No hardcoded Mega144H7-only product architecture. TunerStudio is a *behavioral and workflow reference*, not a source to copy. The accepted EpicEFI Table Interaction Lab is the existing table interaction reference; preserve its mature 2D/3D work. **EpicScope owns Analyzer / VE analysis**, and its implementation is much more mature; do not invest in a duplicate EpicHub Analyzer. Optional future integration through compatible channels, tune/source fingerprints and exports is welcome, but is **not** a blocking task.

Use [Connection, Dashboard and Full-Tuning Roadmap](architecture/CONNECTION_DASHBOARD_TUNING_ROADMAP_2026-10-23.md) for product breadth, but interpret its earlier Burn and runtime status through this newer handoff. Never confuse source-present, native-test-qualified, Android-CI-qualified, ECU-hardware-qualified, and product-complete.

### Completed and CI-qualified foundation since the earlier October handoff

| Milestone | Qualified commit / Foundation run | What that qualifies — and what it does not |
| --- | --- | --- |
| Native page-scoped nonvolatile Burn evidence | `24c0d6a` / [39909934322](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39909934322) | Burn-ACK-plus-post-Burn-CRC evidence, not independent flash power-cycle proof |
| Epoch-bound guarded Burn handles | `c7445fe` / [39912522960](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39912522960) | Equal-byte source reinstallation cannot authorize an old planned Burn |
| Read-only persistence state projection | `84b77de` / [39914326976](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39914326976) | Native/JNI/Kotlin Unknown, PostBurnCrcVerified and StaleEvidence; not connection-owned UI authority |
| Bounded definition-neutral typed runtime decoder | `95b4055` / [39918299554](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39918299554) | Offset/type/endian/scale validation; more than the synthetic four-channel ABI |
| Strict INI output scalar compiler | `2dc7abe` / [39921602400](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39921602400) | Fixed-offset numeric `[OutputChannels]` subset; not all generated definitions |
| INI bitfield support | `2b5d938` / [39924037372](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39924037372) | Unsigned U08/U16/U32 inclusive bit ranges and firmware mini-fixture examples; not calculated expressions |
| Read-only output request protocol plan | `61fd7d3` / [39927675749](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39927675749) | Exact `O%2o%2c` offset/count framing and `msEnvelope_1.0` CRC/status/length validation for examined firmware; no USB polling |
| Native session-fenced runtime acquisition skeleton | `dc51b71` / [39930630762](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39930630762) | One in-flight full-frame request, token/timeouts/fail-closed state and native tests; no live owner/JNI/consumer hookup |
| Handoff for native acquisition | `b2dabdd` / [39931896516](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39931896516) | Documentation-only, qualified and promoted |
| Bounded native live channel batch store | `0918702` / [39934286409](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39934286409) | Separate from synthetic `ChannelStore`; session/monotonic sequence/time, duplicate ID and non-finite rejection, freshness/expiry/disconnect tests. Native-only; not connected to USB/JNI/Dashboard. |

**Latest qualifying transition:** The native `live_channel_store.rs` and its `lib.rs` export at `0918702` initially had CI pending during this audit, then Foundation [39934286409](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39934286409) completed successfully, and `main` advanced to that exact commit. This establishes an **isolated native bounded batch store**, not end-user live telemetry. `ChannelStore` still represents synthetic Dashboard data; no JNI publication or Android connection ownership is implied by the green run.

### Firmware and definition evidence

Source audit was pinned to `PJawZK/epicefi_fw_jzfork` (`master` during this work): `firmware/console/binary/tunerstudio.cpp`, `tunerstudio_commands.cpp`, `output_channels.txt`, `firmware/tunerstudio/tunerstudio.template_zh.ini`, `readme.md` and `firmware/tools/ts_ini_testdata/mini.ini`. The examined EpicEFI CRC envelope uses a definition-declared `O%2o%2c` command, 16-bit offset/count and exactly bounded output bytes. The firmware mini-INI is a **test fixture**, not a fully generated Mega144H7 INI. No fully generated, firmware-SHA/signature-pinned Mega144H7 and uaEFI INI corpus was available as tracked files during the audit. Generate the real outputs using the firmware's documented `ConfigDefinition.jar` plus board-specific source; record provenance and classify all forms including scalars, bits, calculations, arrays, conditionals and command metadata. The exact command/envelope assumptions are **per verified ECU definition**, not universal for every ECU.

### Next work in dependency order — close each gate before the next

1. **Preserve and use the now-qualified native live batch store.** The exact-head run `39934286409` passed and `main` advanced to `0918702`. Reconfirm present heads and the handoff-only CI before further production edits; keep synthetic and live stores separate. Do not restart this already completed implementation gate.
2. **Close runtime authority binding.** Bind verified active connection identity, signature, firmware/profile generation, definition digest, compiled channel catalog, output-read plan and unique connection/session epoch atomically. Enforce disconnect, permission revocation, firmware/profile switch, stale token and retry semantics. Do not allow a caller-chosen numeric token alone to stand in for device identity.
3. **Finish bounded acquisition/publishing contract.** One full-block read is already modeled; introduce rigorously sequenced chunk assembly only if output size requires it. All chunks must match range, response CRC/status, token, epoch and definition. Publish whole-frame batches with timestamps, validity and generation; keep history visibly non-live after timeout or disconnect. No leaking synthetic samples into live channels.
4. **Expose a guarded batched JNI/Kotlin read model and integrate under the existing `AndroidUsbLiveConnectionSessionOwner` and `EngineAuthoritySerialization`.** No second USB owner, no unauthorized concurrent stream with tune load, RAM Write or Burn, and no unbounded allocations or per-channel JNI calls. Exercise transport fake/offline replay and interleaving tests before hardware.
5. **Make Dashboard and Tuner consume one real channel source.** Replace synthetic data *only after* valid live session selection; add stable names/units, unknown/stale/fault display, actual gauges, configurable persisted layouts and phone/tablet behavior. Finish the definition-driven scalar/enum/bitfield/curve/dialog tuning controls and their guards as separate acceptance gates; keep the mature table editor.
6. **Physical qualification remains last.** Test real ECU identity, repeated polling, unplug/replug, performance and safety on the user's available Mega144H7 and Android devices once the theoretical/source and host-test gates are finished. Independently check nonvolatile persistence across power cycles before Burn UI. No firmware modification or ECU hardware work was done in this conversation.

**Open safety boundary:** Burn UI is still intentionally disabled. Native post-Burn CRC proof is not physical flash durability; read-only persistence projection cannot be shown as current ECU authority until tied to the verified retained connection and tested across reconnects. Never conflate Write RAM, Burn, and project-save operations.

### Continuation protocol

Review this summary, the detailed historical sections below, the cited roadmap, and current GitHub heads/actions. Continue in **small, exact-head CI-verified commits** on `current-work`; fast-forward `main` only after the entire Foundation workflow passes at that exact head. Avoid restarting completed work or pursuing a competing Analyzer. Do not call a synthetic Dashboard, transport-free runtime decoder, fixture-only definition compiler or unexposed Burn flow user-complete.

---

## Qualified native runtime acquisition session skeleton — 2026-10-23

**Qualified production commit:** `dc51b71730440a0fb20940d6b7f09f0805a98dd6` on `main`, exact-head Foundation [39930630762](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39930630762) **success**. `current-work` matched before promotion.

New `rust/engine/src/runtime_acquisition_session.rs` is a **transport-free, read-only native state machine**, not yet live ECU telemetry. It composes the previously qualified `CompiledRuntimeChannelCatalog` and `RuntimeOutputReadPlan`; enforces a nonzero session token, catalog/request frame-length agreement and single-frame representability (<= 65,535 bytes); serializes one in-flight output request; issues tokenized request bytes for transport execution; validates matching session/sequence, elapsed monotonic timeout, CRC/status/exact response body via plan; decodes an entire frame before publishing a new snapshot; and marks existing samples stale/invalid on timeout, CRC/status/length failure or disconnect. Explicitly invalid samples are never promoted to Fresh. Five native regressions cover full successful frame, request collision/wrong token, timed-out late reply, bad CRC recovery, disconnect/session replacement and NaN invalidity. No USB, JNI, Dashboard or synthetic channel-store behavior changed.

**Hard limits / next work:** This stage only supports one atomic entire-block request, bounded to 16-bit count. Larger output blocks require sequenced chunk assembly with strictly matched offset/length and atomic whole-frame publish. Session token uniqueness across reconnects MUST be enforced by the retained Android `AndroidUsbLiveConnectionSessionOwner` or a native handle-generation authority; a caller-provided numeric token alone is not verified device identity. A completed native state-machine frame still must not be considered an authorized current ECU sample without binding exact profile signature + definition hash + catalog + command plan to the active transport owner. The current `ChannelStore` remains synthetic fixed channels and must not silently mix real/synthetic observations. Add bounded live batch store with timestamps, validity, generation and stale markers; shared consumer-facing ABI; cancellation/disconnect tests; thereafter integrate under the existing serialized USB connection owner and evaluate offline replay before any single-ECU hardware validation. Current Dashboard remains synthetic. Full generated Mega144H7/uaEFI INI compatibility and expression support are still outstanding.

This documentation-only checkpoint requires its own exact-head Foundation qualification before promotion.

---

## Qualified read-only output request contract and firmware-source audit — 2026-10-23

**Qualified production SHA:** `61fd7d371e646b586dc2a5085f98e7645d738b46`; Foundation [39927675749](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39927675749) **success**, fast-forwarded to `main`.

New native module `rust/engine/src/runtime_output_read_plan.rs` represents a **read-only protocol plan**, not a live transport operation. It accepts only exact resolved `ochGetCommand = "O%2o%2c"` plus `msEnvelope_1.0`, and bounds output offsets/counts against the compiled output block length and the underlying 16-bit request fields. The request body is `O` + 16-bit little-endian offset + 16-bit little-endian count in the existing ms-envelope CRC32 request codec. Responses must pass the existing envelope CRC and status checks and carry exactly the requested payload length. Tests cover request bytes, bounds, unsupported command/envelope, error statuses, CRC corruption, and short payloads. No USB polling or Dashboard changes were made.

**Firmware-source evidence (separate repository):** `PJawZK/epicefi_fw_jzfork`, default branch `master`, source paths:
- `firmware/tunerstudio/tunerstudio.template_zh.ini` [OutputChannels] declares `ochGetCommand = "O%2o%2c"`, `ochBlockSize = @@TS_TOTAL_OUTPUT_SIZE@@`, generated `@@OUTPUTS_SECTION_FROM_FILE@@` and multiple calculated `{ ... }` channels.
- `firmware/console/binary/tunerstudio.cpp` `TunerStudio::handleCrcCommand` reads offset/count via native 16-bit `memcpy` and dispatches `TS_OUTPUT_COMMAND`; this supports the fixed little-endian output request shape for the examined firmware build target. Do **not** generalize that assumption to other ECU protocols.
- `firmware/console/binary/tunerstudio_commands.cpp` `TunerStudio::cmdOutputChannels` checks offset + count against `TS_TOTAL_OUTPUT_SIZE`, writes `TS_RESPONSE_OK` plus exactly the requested output bytes through CRC-framed response.
- `firmware/console/binary/output_channels.txt` is generator input, **not** a final INI. `firmware/tools/ts_ini_testdata/mini.ini` is a synthetic validator fixture.
- `firmware/tunerstudio/readme.md` documents generating final `rusefi*.ini` with `ConfigDefinition.jar` from project and board inputs (`gen_config.bat` on Windows). Fully generated Mega144H7 and uaEFI INIs were **not present as tracked files** in the inspected fork; no release assets were available in that fork's GitHub release listing.

**Outstanding:** obtain reproducible *fully generated* Mega144H7 and uaEFI INI outputs with firmware SHA/signature, complete the compatibility matrix (scalar/bits/expressions/arrays/metadata/scattered commands), couple selected profile + compiled runtime catalog + command plan atomically, add bounded acquired frame assembly and failure/stale/transport cancellation semantics, then share live data with Dashboard and Tuner. Real ECU validation stays separate and last, respecting one-ECU hardware constraints. Treat `ochGetCommand` and ms-envelope compatibility as per-profile capabilities, not global defaults.

The documentation-only commit adding this checkpoint requires its own Foundation qualification.

---

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
