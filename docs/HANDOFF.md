# EpicHub Current Handoff

## ACTIVE CONTINUATION — 2026-10-28 | Gate B JNI instrumentation foundation (unqualified)

**Latest Gate B code checkpoint:** `5c696528e86ba8ea2471547eb1bc19d01fefb2a2` on `current-work`; prior checkpoint `6b09a0963394effb52515c81805eec01b4347178` passed exact-head Foundation [40694147648](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40694147648). `main` remained `8310088f974d66596d7b0a85d19a700d13e4e0b0`. Latest added instrumentation cases and this handoff update need their own exact-head qualification; no Android device execution has occurred. The 2026-10-27 native EPCT catalog/coherence subgate below is qualified; Gate B is **not**.

- Added Android instrumentation runner and `androidTest` dependencies to `app/build.gradle.kts`.
- Extended loaded-JNI negative-authority tests to cover separate registry handles, closure isolation, unbound finish and stale completion after close. These are device tests **not yet executed**, beyond compile qualification.
- Added `NativeRuntimeJniInstrumentedTest.kt` invoking the *real packaged* `NativeProfileRegistryBridge` .so for create/alive/close and negative registry/epoch/catalog/prepare/bind authority checks. The test uses no physical ECU or USB connection.
- Foundation workflow now runs `gradle :app:assembleDebugAndroidTest --stacktrace` and publishes `epichub-gate-b-jni-androidtest` APK for device execution. **Building the test APK is not executing it.** The instrumented tests have **not yet been run on Android**, and exact-head CI for the latest checkpoint was not confirmed green at this documentation update. The earlier EPCT qualification is not evidence of Gate B completion.
- Next: verify build/CI at exact SHA; fix issues before progressing. Execute `gradle :app:connectedDebugAndroidTest` on an attached Android device or emulator with matching ABI; record evidence. Then add the actual loaded-JNI bind/catalog/prepare/complete cycle through the **retained fake USB owner**, reconnection, close, permission revoke, profile replacement and stale callbacks. Add multi-threaded sample/load/write/Burn-authority interleavings and verify context-aware USB detach observer production wiring and unregister. No second USB owner, no Burn UI, no live Monitor data publication; physical ECU last.

---

## ACTIVE CONTINUATION — 2026-10-27 | Native runtime catalog qualified, JNI instrumentation next

**Use this first.** Current implementation and exact-head CI take precedence over historical checkpoint paragraphs below (which retain earlier pending/WIP language for chronology). EpicHub remains **Connect · Monitor · Tune**; EpicScope owns the Analyzer. Keep multi-ECU support definition-driven, not board-hardcoded.

### Exact branch and qualification authority

- **Current qualified `main` and `current-work`:** `8310088f974d66596d7b0a85d19a700d13e4e0b0`, following successful exact-head [Foundation 40521470924](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40521470924) and verified non-force fast-forward (2 ahead, 0 behind). The earlier complete EPCT metadata implementation `685e2b0cca8b16b7c493c0a3eca414083f20d771` also passed [Foundation 40519893059](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40519893059) and was separately fast-forwarded to `main` (20 ahead, 0 behind).
- **This documentation change is not independently qualified until its own complete exact-head Foundation run succeeds.** Do not assume subsequent commits are qualified by these earlier runs. No force push: require current-work at exact green SHA with main an ancestor, fast-forward main, re-read both refs.
- All Task 1 checkpoints prior to metadata remain qualified: registry-owned ECU candidate; JNI candidate bind/revoke; bounded whole-frame CRC/status runtime acquisition through retained USB owner; monotonic clock; explicit sampling gate (50 ms min interval, 250 ms Fresh max age, 100 ms read timeout); USB detach/permission/profile replacement and RAM Write/Burn authority fencing. The **Burn UI is disabled** and no additional USB owner exists.

### Completed metadata subgate — implemented and offline-CI-qualified

- `rust/engine/src/runtime_definition_catalog.rs` now retains exact compiler-derived `RuntimeValueType` along with `NamedRuntimeChannel`'s stable ID, INI name and units, from the **same resolved ECU definition** used for runtime decoding.
- `ProfileRegistryStore::runtime_catalog_for_epoch` and `runtime_catalog_from_registry` read a native registry-owned, immutable catalog copy under the existing mutex. Only the bound opaque runtime epoch can authorize it; close, rebind, profile mutation, wrong handle or disconnect reject stale metadata. No UI-provided type or name can be substituted.
- `rust/engine/src/jni_runtime_catalog.rs` exports bounded direct-buffer **EPCT v1** (magic, version, epoch, profile generation, definition revision, count, total length, then exact ID/type/bit-range/name/units entries). Supports scalar signed/unsigned/float and typed bitfields; fails closed on malformed names, duplicate names/IDs, invalid units, per-entry bounds or oversized whole catalogs (max 1,024 channels, 96 UTF-8 bytes/name, 64 bytes/units, 128 KiB total). No port, polling or sample publication.
- `RuntimeChannelCatalog.kt` validates source magic, version, byte order, UTF-8, count, lengths, IDs, numeric type and valid bit span, reserved fields, full consumption, and expected epoch. `NativeRegistryRuntimeCandidateBinding`, `LiveTuneConnectionOwner`, and the **same retained** `AndroidUsbLiveConnectionSessionOwner` expose read-only metadata only for current registry/profile/USB authority. Unsupported runtime metadata cannot be mistaken for connected monitoring.
- `LiveRuntimeChannelCatalog.canAnnotate(view)` is a **nonpublishing** gate: only Fresh `real-ecu-v1` samples with exact epoch, profile generation, definition revision and the full channel-ID set may be annotated. Stale/Invalid, synthetic or mismatched batches remain disallowed. There is still **no Monitor/Tuner adapter** using this method.
- Rust and Android JVM tests cover real compiler-derived unit/type/ID coherence, wrong registry/epoch, reconnect/disconnect, UTF-8 corruption, duplicate ID/name, invalid type or bit span, malformed length/overflow, stale profile and forged catalog, valid scalar/bitfield decoding, and Fresh-vs-Stale/source/definition/channel matching. Whole Foundation (strict Rust format/Clippy/tests, JVM tests, debug/performance/macrobenchmark and packaging) passed for both implementation and the final coherence addition.

### Open Task 1 dependencies (in order; do not skip)

**B — Real JNI and connection-lifecycle proof.** The Kotlin JVM tests mock native catalog and runtime read operations; Rust and Kotlin sides are independently qualified. Next exercise the **actually loaded native .so** in Android instrumentation or an equivalent JNI harness *with the retained fake USB transport*, proving bind/catalog/prepare/complete/close/reconnect, stale callback and permission revoke. Add multi-threaded sample/load/RAM-write/Burn-authority interleaving stress and confirm that the production creation path uses the context-aware USB-detach observer, with correct unregistering at close. No physical ECU yet.

**C — Explicit read-only Monitor/Tuner projection.** Build a versioned, session-fenced *real-data-only* source backed by Fresh/Stale/Invalid/Unknown sampling plus metadata, channel units/types/IDs and age, using `canAnnotate` or stronger coherence proofs. Never mix the legacy synthetic four-channel Dashboard ABI with real ECU metadata or fabricate unsupported channels. Test phone/tablet lifecycle, permission loss, profile reload, and stale/invalid presentation. Burn UI remains disabled.

**D — Controlled live sampling driver.** Only after B/C, add opt-in lifecycle-owned backpressure and a bounded schedule, single USB owner, serialized tune operations, immediate stop on detach/background/profile switch and no stale publication.

**E — Compatibility corpus and hardware last.** Validate generated Mega144H7/uaEFI and other INI definitions, document accepted/unsupported expressions and command variants, and keep physical ECU profiling at the end. Metadata limits are strict first-pass restrictions, **not claims of universal TunerStudio INI support**.

### Resume protocol

Read this section, then compare actual `main`/`current-work` and latest exact-head Foundation run. Preserve any newer code or human changes over historical text. Continue **Gate B**, qualify it fully, and only then advance to C. At each qualified checkpoint update this first section and exact CI references; keep historical entries below unchanged.


---

## Qualified USB device / permission / profile lifecycle fencing — 2026-10-27

**Exact implementation SHA:** `38bb44559ad944ab4deb4f1276c2a14d4251cc22`. Full [Foundation 40512006983](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40512006983) **success** (Rust format/lint/tests, Android JVM tests, debug/performance/macrobenchmark builds and packaging). Verified `current-work` 12 commits ahead and zero behind `main`, and fast-forwarded `main` without force. The documentation-only checkpoint subsequently passed **Foundation 40513443318** and was promoted to `main` at `ac9a02a4`; the active catalog branch above is separate and unqualified.

### New authority boundary

- `AndroidUsbLiveConnectionSessionOwner` now supports a read-only `AndroidUsbEndpointAccess` validity gate. Its existing `UsbManager` constructor verifies the exact selected endpoint is still present **and permitted**, without opening another connection; unavailable devices/grants revoke the candidate and retained channel before tune load, runtime sampling, and RAM Write/Burn entrypoints. Access-query exceptions (including `SecurityException`) fail closed. Read-side freshness and connection/candidate status cannot claim a lost endpoint is live.
- All retained tune-load and guarded RAM Write/Burn paths now check the **currently selected profile-owner object**, not merely the previous owner's `isAlive`, before and after the operation. A profile switch, endpoint removal or permission loss during an operation revokes the retained channel and causes `NotConnected`, not a falsely trusted completion. This does **not** enable Burn UI.
- Initial connection preserves the existing opener's semantic difference between `PermissionRequired` and `DeviceUnavailable`; it rejects any offered-but-no-longer-permitted channel before sending an identity query, and rechecks device access after the native runtime candidate bind. Runtime completion rechecks the same retained channel/owner/epoch and endpoint access after the transport callback.
- Added `AndroidUsbDetachObserver`: a lifecycle-owned runtime Android `ACTION_USB_DEVICE_DETACHED` receiver that passes the exact `usb:deviceName:deviceId` to the **same** owner; only that endpoint is revoked. A new context-aware session constructor opts into this receiver and `close()` unregisters it. The existing `UsbManager` constructor continues using dynamic access checks. **No new USB owner or permission-request path**.
- Added offline JVM cases for unrelated vs exact detach, grant loss before connection and during profile candidate bind, permission disappearance during native runtime callback, Android permission-query exceptions, profile-owner replacement before guarded RAM Write, grant disappearance inside write callback, and distinct PermissionRequired vs DeviceUnavailable outcomes. Existing retained USB/fake-port and native foundation tests continue passing.

### Remaining Task 1 gates (do not mark complete)

This is a **lifecycle subgate**, not fully enabled Monitor/Tuner telemetry. The context-aware detach constructor has been compiled and its core callback logic tested offline but is **not yet proven in an Android instrumentation/system broadcast test**, nor verified that all production creation paths instantiate it. There is not yet a real loaded JNI + fake USB end-to-end instrumentation test, concurrent multi-threaded load/write/runtime stress test, or a manifest-backed autonomous sampling driver. Android grant changes without a detach broadcast are reconciled synchronously on the next owned operation or read-side snapshot; no background grant-monitoring worker was introduced.

**Next:** Expose `RuntimeBoundLiveSession::channel_info` through a native registry-handle + opaque-epoch read-only, bounded, versioned metadata projection. The compiler already owns `NamedRuntimeChannel { name, units, id }` from the **same resolved ECU definition** as the decoder, but these names/units are currently **not carried across JNI to Android Monitor/Tuner**. Preserve exact epoch/generation/source revision and fail closed on stale registry, malformed names/units, duplicate IDs or oversized catalogs; add type metadata only from the definition-authorized native decoder, never heuristic UI mappings. Then complete combined JNI/device instrumentation and carefully gated, opt-in Monitor/Tuner source publication, with live/synthetic isolation. Generate and qualify real Mega144H7/uaEFI INI support/unsupported corpus before physical hardware tests (hardware last). EpicScope owns Analyzer; EpicHub remains Connect · Monitor · Tune.

---

## Qualified connection-owned, freshness-aware sampling gate — 2026-10-27

**Implementation SHA:** `2a0c140579c8f25e81d14f2c9809183b6143d9d9`. Exact-head complete [Foundation CI 40466270449](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40466270449) **success** (Rust format/lint/tests; Android JVM tests; debug, performance and macrobenchmark builds; APK/package checks). Verified `current-work` 7 ahead, 0 behind `main` and fast-forwarded `main` without force. This documentation-only commit must itself pass complete exact-head Foundation before promotion.

### Completed and qualified

- Added `app/src/main/kotlin/com/epicefi/epichub/engine/LiveRuntimeSamplingGate.kt`, a **pure internal, thread-free state machine** with validated bounded policy (default minimum interval **50 ms**, maximum live age **250 ms**, and read timeout **100 ms**). It provides explicit `Unknown`, `Fresh`, `Stale` and `Invalid` projections with an unambiguous `real-ecu-v1` source marker. No timer, coroutine, second USB owner, autonomous poller, or Dashboard source switch was added.
- Read-side `snapshot(nowMs)` computes age without USB activity; values are only available as **live** when the frame is Fresh and belongs to the active epoch. Expired/disconnected readings can be retained *only as non-live history*, with explicit freshness status. New binding clears history to avoid sharing old values across ECU/profile sessions. Clock regression, duplicate/out-of-order sequence, incorrect epoch, profile generation or definition revision changes, malformed/duplicate/non-finite/oversized samples, and a failed transfer invalidate Fresh authority.
- `AndroidUsbLiveConnectionSessionOwner` is still the **sole retained USB owner**, now exposing internal `sampleRuntimeIfDue()` and no-IO `sampledRuntimeSnapshot()`. A due tick retains `EngineAuthoritySerialization` across the already-qualified one-shot read. Results are accepted only after native full-frame CRC/status/definition/epoch validation **and** sampling policy checks. Failure revokes native candidate and closes the retained USB port; catalog/profile owner replacement is reconciled before both an explicit sample and read-side snapshot. `readRuntimeOnce` now performs an additional post-read check against the same retained channel/owner/epoch so re-entrant disconnect callbacks cannot turn a late result Fresh.
- Added JVM tests for Unknown→Fresh→Stale, exact interval bounds, age expiry and resumed sampling, cross-epoch clearing, sample-number immutability, invalid clocks and malformed channels, failure revocation, changing selected profile, and re-entrant disconnect during an in-flight fake read. The Rust/JNI/USB one-shot framework is unchanged.

### Explicitly *not* complete

This closes the **controlled sample admission and read-side freshness subgate**, **not all of Task 1**. A connected ECU or a candidate runtime epoch does not automatically enable monitoring. These methods are internal; no background cadence is driven, no Android Monitor/Dashboard/Tuner consumer uses these snapshots yet, and no live/synthetic blending was introduced. The legacy four-channel synthetic Dashboard ABI remains isolated; Burn UI stays disabled. No on-device real-JNI fake-USB instrumentation or physical ECU read has yet proved the Kotlin↔JNI↔native↔USB stack end to end. Generated production Mega144H7/uaEFI INI channels and firmware provenance are not yet qualified. Physical/USB permission change notification and proactive channel disposal outside an explicit owner method need further work.

**Next Task 1 gates in order:** (1) close remaining profile selection, native-JNI runtime and device-detach/permission-loss lifecycle tests (including concurrent load/write/sample interleavings and on-device fake-USB test harness); (2) expose *definition-derived* runtime catalog labels/units/type and a session-fenced, versioned Monitor/Tuner projection from the already-bounded sample gate, preserving synthetic/live source isolation; (3) only then introduce an opt-in, lifecycle-owned sampling driver with backpressure, no concurrent USB operations and clear stop/reconnect behavior; (4) validate real generated Mega144H7/uaEFI definition subsets and document unsupported ECU capabilities; (5) physical ECU profiling remains last. EpicScope remains the Analyzer; do not duplicate it in EpicHub.

---

## Qualified bounded runtime read cycle — 2026-10-26

**Exact production SHAs:** core implementation `a0937a3068dc1ee31898e7f59dc5ecb6a23adccf` passed full [Foundation 40404160160](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40404160160); Android boot-relative clock correction `df27ff27b59bbc7e48d33c94df337e05f1f19dd3` passed full [Foundation 40405376658](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40405376658). Both exact SHA fast-forwards to `main` succeeded with `current-work` ahead and zero behind. This documentation-only update requires **its own exact-head full Foundation success** before promotion.

### Completed and offline-qualified

- The selected registry-owned native runtime candidate now resolves an opaque epoch to its *private* binding, with no caller-supplied profile, source definition or spoofable session identity. `ProfileRegistryStore::prepare_runtime_read_for_epoch` returns one read-only, whole-frame command and exact response bound; `finish_runtime_read_for_epoch` validates the same epoch, sequence, elapsed time and full response against Rust's existing `RuntimeAcquisitionSession`, CRC/status and decoder. Unsupported frames above Android's 64 KiB transaction limit cannot receive runtime read authority.
- `rust/engine/src/jni_runtime_read.rs` exposes **versioned, bounded JNI prepare/finish** entrypoints. Request metadata contains the exact expected response length and transport request. Completion emits a v1 `EPIC`-source-marked **real-data-only** batch with epoch, sequence, receive timestamp, profile generation, resolved-definition revision, validity and up to 1,024 channel ID/value/validity tuples; malformed buffer lengths, stale epochs/tokens and non-Fresh data are rejected. It never opens or owns a USB port.
- `NativeRuntimeReadCycle` does one explicit `transactExact` through the *retained* `UsbReadOnlyTransactionChannel`; partial underlying USB chunks are assembled within an absolute response bound. `AndroidUsbLiveConnectionSessionOwner.readRuntimeOnce` keeps the existing `EngineAuthoritySerialization` lock and selected USB owner across native prepare, transport, native completion and batch parse. On invalid CRC/status/length/timeout, transport fault or rejected native verification it closes the USB channel and revokes the runtime candidate; unsupported runtime definitions never initiate IO. Android timing uses `SystemClock.elapsedRealtime()` for a nonnegative boot-relative monotonic clock. There is **no polling loop**.
- Added pure Rust epoch/CRC/timeout/reconnect tests and Kotlin fake-native + fake-USB tests for fragmented responses, CRC failure, incomplete transfer, timeout, stale profile selection, invalid v1 batch and failure revocation. Existing JVM/debug/performance/macrobenchmark/API packaging and strict Rust CI all passed. The old synthetic four-channel ABI is unchanged; Burn UI remains disabled.

### Still-open Task 1 boundary

This **qualifies a one-shot internal runtime transaction contract**, NOT a generally enabled live Monitor. `RuntimeReadCycleResult.Fresh` is not routed to the Dashboard/Tuner. No continuous scheduling, sample-age-based read-side projection, channel catalog metadata (labels/units), real/synthetic source switching, live Dashboard layout or Android instrumentation verifying the actual loaded JNI bridge against a fake USB implementation has been qualified. JVM tests mock JNI behind `NativeRuntimeReadOps`; Rust and Kotlin are independently qualified, but a single on-device JNI crossing and multi-transaction race/interleaving test is still needed. Proactive closure on profile catalog replacement or USB device/permission events outside an explicit owner method is also still open; the next read/load reconciles dead ownership. Signed profile `definition_fingerprint` remains imported metadata rather than proof of running firmware provenance.

**Next dependency order:** (1) strengthen selected-owner/native JNI integration and deterministic replay across reconnect, permission loss, profile replacement, stale callback and concurrent tune operations; (2) add a bounded, serialized sampling policy and read-side Fresh/Stale/Invalid/Unknown batches with timestamp/age and definition channel names/units; (3) connect actual live batches to a versioned Monitor/Tuner source separately from synthetic content and test tablet/phone behavior; (4) qualify generated real Mega144H7/uaEFI ECU definitions, including unsupported expressions and command variants; (5) physical ECU testing last. This is **not the end of Task 1**. Do not create a competing USB owner, enable Burn UI or build a duplicate EpicHub Analyzer: EpicScope owns Analyzer.

---

## Qualified Android-owned runtime candidate bridge — 2026-10-23

**Exact qualified implementation SHA:** `87fb1e35e876ba7bb75fe3f7bf0031a334253cc2`. Full [Foundation 39945006875](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39945006875) **success** (Rust formatting, Clippy, tests; Android JVM tests, debug/performance/macrobenchmark build and packaging gates). Verified `current-work` was 10 commits ahead and 0 behind `main`; fast-forwarded `main` to this SHA. This handoff-only commit must separately qualify at its exact SHA before promoting.

### New verified boundary

Added `rust/engine/src/jni_runtime_candidate.rs` and global mutex-serialized registry bind/revoke wrappers. Native JNI resolves the selected *registered compiled bundle* using signature bytes and expected generation, returning a positive, process-unique candidate epoch or a negative rejection code; revocation invalidates the native candidate. JNI performs **no USB IO**, accepts no arbitrary runtime channel definitions, and publishes no real telemetry. Added Kotlin `RuntimeCandidateBindingOperation` and `NativeRegistryRuntimeCandidateBinding` to dispatch this JNI only from `LiveTuneConnectionOwner` after an exact definition-query/profile selection. The retained `AndroidUsbLiveConnectionSessionOwner` binds the candidate following successful identity selection, retains an internal typed `Bound(epoch)` vs `Unavailable(reason)` candidate status and revokes on disconnect, reconnect or owner closure. An unsupported runtime definition does **not** reject an otherwise authorized tune connection. Connection, load, RAM Write, Burn entrypoints and disconnect now hold `EngineAuthoritySerialization` across the existing single USB owner. Tests with a fake USB port/candidate binder check success, unsupported runtime candidate, reconnect rejection cleanup, profile owner closure and stale display authority.

### Crucial limitations — do not mistake this for Task 1 completion

- This is the **candidate-bind/revoke JNI subgate**, not the complete acquisition/publication boundary. There is no JNI `begin/complete/latest` read model, no USB `transactExact` runtime polling, no versioned live batch projection, no Dashboard/Tuner live channel consumption, and no ECU hardware testing.
- `RuntimeCandidateBindingResult.Bound` is **not live data**; it means only the active selected definition has supported runtime protocol and channels in the current limited subset. Existing four-channel synthetic ABI remains unchanged; Burn UI stays disabled.
- Offline Android tests inject a fake binding operation, and Rust tests independently exercise registry-bound native runtime. A combined JNI+fake-port runtime transaction/interleaving proof is **still required**. No generated real Mega144H7/uaEFI output INI corpus is yet qualified.
- Closing a selected profile registry synchronously revokes its native candidate; a stale Android USB channel is closed when the retained connection owner next reconciles its dead authority (for example, next load). Proactive physical channel cleanup on catalog replacement/USB detach and handling native bridge exceptions or transport IO failure remain separate ownership hardening gates.

**Next step in Task 1 (do not skip):** Implement an explicitly connection-owned, bounded runtime read cycle: expose the registry-owned request plan and token through versioned batched JNI; issue exactly one `transactExact` on the retained USB channel; complete with the same token and bound epoch; return copy-bounded real sample batches with source/generation/validity/time/freshness; reject CRC/status/length/timeouts, stale callbacks, disconnect/permission loss, profile replacements and concurrent tune operations. Add deterministic fake-port tests for full success and every failure/interleaving class, then exact-head Foundation qualification. No other USB owner, unguarded poller or synthetic/live blending. Physical ECU work stays last; EpicScope continues owning the Analyzer.

---

## Qualified registry-owned runtime candidate — 2026-10-23

**Exact production implementation SHA:** `2c3467d510e8ea396eface7974a9f51af5b94dd9` on `main`, qualified by complete [Foundation 39941582220](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/39941582220) **success**. Both Android and Rust build/test/lint/signature/packaging gates passed. The fast-forward from prior `main` succeeded (3 commits ahead, 0 behind). The documentation update carrying this paragraph is a **separate CI checkpoint** and is not qualified until its own exact SHA succeeds.

### What changed

`rust/engine/src/profile_registry_store.rs` now retains one `RuntimeBoundLiveSession` under each native registry handle. `bind_runtime_candidate` selects the controller through the existing exact signature authority using the actual registry-owned compiled profile and its resolved bundle (including expanded includes), checks the caller's expected profile generation, and builds the runtime catalog/read command/store from that **same selected source**. Unsupported runtime channel expressions and profile-only imports without a compiled bundle fail closed. A rejected rebind removes prior Fresh authority; registry mutations revoke runtime authority; a closed registry handle cannot access old samples. Host-only begin/complete/latest/disconnect operations enforce the registry handle and bound lease, and return bounded copied batches; they perform **no transport IO**. Added offline tests for correct command/frame decoding, generation mismatch, bad signature, absent bundle, unsupported expression, cross-registry lease rejection, registry mutation, close/handle reuse, age-based stale state and late-token invalidation.

**Precise qualification level:** This closes a **native registry ownership subgate**, not the complete runtime authority gate. `bind_runtime_candidate` is still an internal, transport-free API; the observed signature response and endpoint identifier are its inputs, NOT independently verified current Android USB identity. No JNI-facing live publication, no connection-owned stream, no Android fake-port runtime acquisition, no Dashboard live source, no hardware proof and no Burn UI were added. A stored profile fingerprint is imported definition metadata, not verified firmware authenticity; the existing source revision is a non-cryptographic in-process coherence marker, not a secure content digest.

### Immediate continuation boundary — do not skip

1. Under the **retained** `AndroidUsbLiveConnectionSessionOwner` and `EngineAuthoritySerialization`, create a guarded, versioned native JNI bridge that selects this registry-owned runtime candidate only after exact ECU identity/profile match. Resolve the native registry handle from the currently selected `LiveTuneConnectionOwner`; do not pass arbitrary profile bytes, raw definitions, or independently chosen session numbers from the UI. Ensure a failed bind cannot leave a transport or stale native lease active.
2. Revoke native bound sessions synchronously on disconnect, reconnect, owner close, permission/device loss, transport IO failure, profile registry replacement and owner selection changes; hold a single USB channel owner across runtime read, tune load, RAM Write and disabled Burn. No concurrent unguarded poller.
3. Add bounded single-frame offline USB acquisition and coarse batched JNI/Kotlin projections, with explicit source marker, generation, validity and sample age. Test incomplete/invalid CRC/status frames, timeouts, late callbacks, reconnects, interleaved operations and stale Dashboard prevention. Preserve the older synthetic ABI and source isolation until live integration is independently qualified.
4. Finish reproducible generated Mega144H7/uaEFI INI provenance and unsupported-feature classification before general live channel claims. Physical ECU profiling remains last. EpicScope, not EpicHub, owns the Analyzer.

Continue with small `current-work` commits, exact-head complete Foundation qualification, and fast-forward `main` only when green. Avoid reopening the already qualified registry implementation or promoting partial CI evidence.

---

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
