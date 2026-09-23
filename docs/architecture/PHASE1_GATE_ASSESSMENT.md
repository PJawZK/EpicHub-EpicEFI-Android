# Phase 1 Gate Assessment

## Status

Phase 1 cross-repository architecture archaeology is complete enough to stop broad source review.

A dedicated cross-document consistency audit was then performed before acceptance. It found and reconciled several real contradictions/stale statements, especially around format-neutral logging versus MDF-specific wording, legacy ESP32 bridge versus future direct outputs, warning observation versus automation authority, JNI batching exceptions, semantic tune-draft ownership, settings ownership, locked namespace/build state and D-022 source-reimplementation methodology.

After those reconciliations, the architecture/source/test gate is **ready for explicit user acceptance** before production scaffolding begins.

This does **not** mean every future feature/format/algorithm has been implemented or physically qualified. It means remaining unknowns are explicit implementation, fixture, benchmark, compatibility or product-scope tasks with known owners/evidence plans rather than hidden architecture responsibilities.

It also does **not** mean every historical source file has been certified as correct or reusable. Phase 1 mapped responsibilities and high-value evidence. D-022 / `SOURCE_REIMPLEMENTATION_METHOD.md` requires deeper piece-by-piece review whenever each subsystem enters implementation, followed by a clean rewrite rather than bulk source migration.

---

# Completion-gate assessment

`PHASE1_SCOPE.md` defines ten closure conditions.

## 1. No major required capability remains hidden inside old broad classes

**SATISFIED.**

EpicDash-JZ, original EpicDash/ESP32 behavior, generated profile/tuning authority, persistence/recovery, UI interaction/performance, logging, diagnostics, Tune Viewer and AE/PID/TCharge/Injection Angle plugin responsibilities have been decomposed by domain.

Plugin-derived analysis/autotune responsibilities are explicitly mapped in `ANALYSIS_RESPONSIBILITY_MAP.md`.

This is a responsibility-completeness statement, not a claim that every old source file is production quality. Implementation-stage source review remains mandatory under D-022.

## 2. ECU / ESP32 / file-format protocol authorities are explicit

**SATISFIED at architecture-authority level.**

- ECU USB/signature/framing/read/write/Burn authority is documented in `PROTOCOL_AUTHORITIES.md` and directly corroborated against current firmware in `ECU_PROTOCOL_DIRECT_VERIFICATION.md`.
- legacy ESP32 bridge protocol/failsafe limits are documented in `ESP32_ACCESSORY_EVIDENCE.md`.
- external measurement formats are isolated behind `MeasurementStore`/`MeasurementWriter`/interop boundaries; MLG/MDF exact executable compatibility remains fixture/interoperability work, not ownership discovery.
- exact live signature equality is the connected-firmware compatibility gate; the exact selected generated-profile source/fingerprint remains identity for compiled schemas, caches, project/tuning state.

## 3. Kotlin/Rust boundary has no expected high-rate per-value chatter

**SATISFIED.**

`KOTLIN_RUST_BOUNDARY.md` requires coarse semantic operations, bounded/versioned packed snapshots, viewport-sized analysis results and bounded event drains.

High-rate ECU/runtime per-channel/per-sample JNI is prohibited. Low-rate discrete Android-source events may cross individually when simpler and measured acceptable; bursts/higher-rate sources are batched. That exception is explicit and cannot become the ECU streaming model.

## 4. Mutable state ownership is unambiguous

**SATISFIED.**

`STATE_OWNERSHIP.md` defines sole owners for Android platform resources, BLE/GATT, ECU session, profile, ChannelStore, live tune/TuneSnapshot, semantic tune draft, project authority, operation journal, logging, measurement-file state, analyzer heavy state, Compose interaction state, settings and diagnostics.

In particular:

- Rust owns semantic tune draft/history;
- Kotlin Tuner state owns selection/text-entry/gesture/presentation only;
- Kotlin `SettingsStore` owns application/user preferences only;
- selected measurement readers/writers own format state but never ChannelStore/logger truth.

`ANALYSIS_RESPONSIBILITY_MAP.md` adds immutable completed `AnalysisSession` ownership and staging/replacement rules.

## 5. Dependency direction prevents presentation/transport/file-format ownership leaks

**SATISFIED.**

`DEPENDENCY_RULES.md`, `SAFETY_BOUNDARIES.md` and D-019/D-020 prevent:

- raw ECU memory authority in UI/analyzers;
- a second INI/profile parser authority;
- dashboard-owned telemetry;
- plugin-specific ECU writers;
- warning/diagnostic definitions becoming hidden control policy;
- MLG/MDF/external formats becoming the runtime model.

Warning evaluation is observational. Any future automatic action based on warning state travels through a separately configured AutomationRule/ActionPolicy path.

D-022 additionally prevents historical class/control-flow structure from becoming de facto architecture merely because it already exists.

## 6. Topology has a home for Phase 2 starting components

**SATISFIED.**

`CODEBASE_TOPOLOGY.md` now aligns with the locked architecture, including:

- locked `com.epicefi.epichub` namespace;
- one `Esp32BridgeGateway` with message-specific legacy codecs;
- separate optional `Esp32DirectOutputGateway` for a future output capability;
- Kotlin `SettingsStore`;
- Rust semantic `tune_draft` ownership;
- common `MeasurementStore` and `MeasurementWriter` contracts;
- MLG-first interoperability and later MDF4 implementation;
- shared analysis session/evidence/profile responsibilities.

Exact crate/file splits remain refinable during implementation; responsibility boundaries are authority.

## 7. Remaining decisions are deliberate PoCs/fixtures/benchmarks, not unknown ownership

**SATISFIED.**

Remaining tasks are classified as Phase 2 measured PoCs, feature-entry prerequisites or later/deferred product choices.

The pre-scaffold product/build locks are resolved by D-021 / `ANDROID_BUILD_BASELINE.md`:

```text
applicationId / namespace = com.epicefi.epichub
minSdk                  = 26
targetSdk               = 36
compileSdk              = 37
production ARM ABIs     = armeabi-v7a + arm64-v8a
```

D-022 additionally locks how historical implementations are reviewed/reimplemented when their feature enters production work.

## 8. High-risk subsystem tests/qualification paths are defined

**SATISFIED.**

`TEST_STRATEGY.md` covers:

- Kotlin policies/codecs;
- Rust parser/codec/tuning/channel tests;
- ECU/profile/ESP32 golden vectors;
- tuning fault injection and uncertainty;
- MLG ecosystem interoperability before production MLG acceptance;
- independent MDF interoperability later;
- JNI ABI/lifecycle;
- USB sustained-I/O comparison;
- legacy BLE/ESP32 bridge behavior separately from any future direct-output physical failsafe tests;
- Compose performance;
- logging soak/recovery;
- analyzer replay/scaling/cancellation;
- staged bench/vehicle mutation qualification.

D-022 adds a source-understanding gate before implementation acceptance: old tests are candidate specifications only after their real requirement is identified, and known old bug behavior receives explicit negative/regression tests instead of blind parity.

## 9. Repository architecture documents agree

**SATISFIED after the dedicated consistency audit.**

The audit specifically reconciled these previous mismatches:

1. generic `Esp32AccessoryGateway` / `Esp32MessageCodec` wording versus the concrete legacy `Esp32BridgeGateway` + message-specific codecs and separate direct-output gateway;
2. warning rules appearing able to execute actions versus D-017 observational diagnostics/warnings;
3. generic logger/thread/data-flow docs being MDF-specific despite D-014 MLG-first, format-neutral architecture;
4. legacy ESP32 flow implying relay/GPIO authority despite firmware evidence showing a BLE↔CAN bridge;
5. absolute “never per sample” JNI language versus the intended low-rate external-source exception;
6. ambiguous Kotlin/Rust semantic tune-draft ownership;
7. settings ownership marked “to be locked” despite the gate claiming state ownership was settled;
8. MDF-specific measurement-file state where MLG and other readers/writers must share the generic contract;
9. stale provisional namespace wording after D-021 locked `com.epicefi.epichub`;
10. topology missing an explicit `MeasurementWriter` and the later-discovered shared analysis responsibilities;
11. stale decision-log references stopping at D-021 after D-022 was added;
12. legacy/direct-output test wording that could have treated the legacy task watchdog as a physical-output failsafe.

Current authority documents consistently state:

- Kotlin/Compose + Rust split;
- one ECU session;
- one ChannelStore;
- exact profile source/identity authority;
- exact verified TuneSnapshot/write/Burn safety;
- one connection-wide BLE scheduler;
- format-neutral live measurement path;
- MLG first, MDF4 later after comparison/interop proof;
- diagnostics/warnings observational only;
- exact-profile tune comparison;
- shared analysis engine/domain profiles;
- initial Android package/API/ABI baseline;
- historical implementations mined piece-by-piece and cleanly reimplemented rather than bulk-ported.

## 10. User explicitly accepts Phase 1 before production scaffold

**PENDING USER DECISION.**

No production scaffold should be created until this final gate is explicitly accepted.

---

# Remaining work: allowed to stay open after Phase 1

These items have known owners and proof plans. Keeping them open does not make Phase 1 architecture incomplete.

## Early Phase 2 measured PoCs

### USB sustained-I/O ownership

Compare on the same ECU/profile/device workload:

```text
A. Kotlin USB I/O/decode reference
B. Kotlin bulk I/O → coarse Rust batches/decode
C. Android open/claim → proven native FD/handle → Rust sustained I/O/decode
```

Selection remains measurement-driven. Architecture above the transport adapter is unchanged.

Before a candidate becomes production code, its corresponding EpicDash-JZ/firmware transport behavior is reviewed piece-by-piece under D-022 so legacy lifecycle/retry/race behavior is understood rather than copied.

### Compose/runtime snapshot performance

Prove:

- packed ABI snapshot publication;
- frame/jank behavior;
- no sample-rate recomposition coupling;
- table/plot/3D interaction cost;
- inactive-page suppression.

### Dual-ABI JNI lifecycle

Physically prove engine create/close/recreate and exact `Build.SUPPORTED_ABIS` on SM-A137F and SM-T500 before assuming ABI behavior.

---

# Feature-entry prerequisites, not scaffold blockers

These must be completed before their corresponding feature is accepted, but the empty foundation does not need them all on day one.

Every inherited feature also requires the D-022 source-understanding/reimplementation gate before its production implementation is accepted.

## ECU/profile/tuning

- executable `S/Q/O/R/C/B` golden vectors;
- broader genuine generated-INI compatibility corpus;
- exact clean Rust parser/compiler parity;
- project container/backup/journal concrete schemas;
- fault-injection parity;
- bench read/write/Burn parity before vehicle mutation.

## Logging/measurement

- genuine MLG executable compatibility corpus before production MLG writer acceptance;
- writer power-loss/recovery qualification;
- Android recording retention/storage policy;
- MDF4 independent interoperability and version/compression benchmarks before native MDF4 baseline is accepted.

## Analyzer/autotune

- concrete versioned `AnalysisSession` schema;
- domain-profile trait/interface;
- replay golden corpus;
- semantic `ExperimentProposal` contract;
- per-profile maturity/physical qualification before recommendation/experiment capability is exposed.

## Diagnostics

- stable event/metric IDs and export schema;
- retention/persistence limits;
- warning-vector integration.

## Tune comparison

- exact persisted comparison identity tuple;
- same/cross-profile goldens;
- changed-axis regridding only if explicitly implemented;
- optional compiled-profile projection only if benchmarked useful.

---

# Deliberately deferred product decisions

These are not required to create the core application architecture:

- exact rich table arithmetic/fill/smoothing/interpolation product scope;
- dashboard customization/favorites preset scope;
- legacy ESP32 feature subset beyond needed compatibility;
- any future direct ESP32 GPIO/relay protocol;
- cross-profile transformed table comparison;
- later analyzer profile rollout order;
- MDF4 compression/version choice before measurement;
- specialized GPU renderer unless Compose measurements prove a need.

---

# Recommended first Phase 2 implementation slice after acceptance

Keep the first slice deliberately small and non-mutating:

```text
Android app/bootstrap
→ Compose host
→ Rust shared library for both ARM ABIs
→ NativeEngine create/close/recreate
→ versioned packed RuntimeSnapshot ABI
→ minimal Rust ChannelStore
→ synthetic/native test channel publication
→ Compose display of snapshot
→ bounded native event drain
→ diagnostics counters for JNI/snapshot lifecycle
```

Even in this small slice, inherited lifecycle/state/performance lessons are reviewed under `SOURCE_REIMPLEMENTATION_METHOD.md` before substantive inherited behavior is implemented. The scaffold itself is new; it is not a renamed EpicDash-JZ skeleton.

Acceptance for that first slice should include:

- CI builds both production ARM ABIs;
- ABI-specific artifact inspection;
- install/launch on SM-A137F and SM-T500;
- physical `SUPPORTED_ABIS` capture;
- repeated Activity recreation without duplicate engine ownership;
- snapshot version mismatch test;
- stable buffer publication/read test;
- no WebView/JSON runtime hot path;
- no ECU writer, no Burn, no MDF writer and no plugin feature port yet.

After this foundation is proven, proceed with:

```text
format-neutral live ECU path
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

Tuning mutation remains later and retains physically accepted EpicDash-JZ behavior as behavioral evidence/oracle while the implementation is independently redesigned and physically qualified.

---

# Gate result

Architecture/source/test checklist result after the consistency reconciliation:

```text
45 / 46 review items satisfied
```

The single intentionally unchecked item is:

> user accepts Phase 1 result before production scaffold begins.

No unresolved architecture-document contradiction currently blocks that decision. The deeper per-subsystem old-code review remains deliberately an implementation-entry gate so it occurs immediately before each clean replacement is designed and written.