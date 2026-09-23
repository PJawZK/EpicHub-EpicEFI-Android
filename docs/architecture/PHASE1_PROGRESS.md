# Phase 1 Progress

## Status

Phase 1 architecture archaeology and pre-implementation design are complete enough for explicit acceptance.

Production Android/Rust scaffolding has **not** started.

Current review gate: **45 / 46 checklist items satisfied**. The sole remaining item is explicit user acceptance before production scaffolding begins.

See:

- `PHASE1_GATE_ASSESSMENT.md`
- `REVIEW_CHECKLIST.md`
- `GAP_ANALYSIS.md`
- `ANDROID_BUILD_BASELINE.md`
- `../HANDOFF.md`

---

# Architecture foundation completed

The Phase 1 branch now establishes:

- clean-slate development/handoff workflow;
- durable decision log through D-021;
- permanent naming rules;
- source/provenance inventory;
- responsibility inventory and destinations;
- target Kotlin/Rust file/package/crate topology;
- dependency, state-ownership, data-flow, threading, FFI, action and safety contracts;
- testing/qualification strategy;
- current gap classification by implementation phase rather than historical archaeology status.

The initial Android product/build baseline is locked to:

```text
applicationId / namespace = com.epicefi.epichub
minSdk                  = 26
targetSdk               = 36
compileSdk              = 37
production ARM ABIs     = armeabi-v7a + arm64-v8a
```

The dual-ABI decision deliberately preserves the existing SM-A137F phone as a physical target while supporting the arm64 SM-T500 tablet.

---

# Completed evidence/deep-dive domains

## ECU USB / protocol / generated INI

Completed responsibility decomposition covers Android USB/CDC setup, one ECU session, asymmetric request/response framing, sync/probe policy, exact signature/profile authority, runtime polling/decoding, generated-INI compilation/conditions and verified Read/Write/Burn transactions.

Current firmware authority was directly checked in `ECU_PROTOCOL_DIRECT_VERIFICATION.md`.

Remaining items are implementation/golden/physical evidence, not ownership discovery.

## ESP32 legacy bridge / Android BLE

`ESP32_ACCESSORY_EVIDENCE.md` establishes the original device as a BLE↔CAN bridge with local inputs rather than an inherited relay/GPIO controller.

Target architecture uses one connection-wide GATT operation scheduler and does not treat GATT completion as downstream action confirmation.

Any future direct-output controller is a separately versioned capability with firmware-owned failsafes.

## Tuning persistence / recovery

`TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md` separates:

- exact profile + verified TuneSnapshot project authority;
- rebuildable derived caches;
- portable project backup;
- durable pre-transmission uncertainty journal;
- unverified UI draft state.

No cache/backup/journal/draft becomes a second tune authority.

## Dashboard / Tuner interaction / responsive / performance

`UI_INTERACTION_EVIDENCE.md` preserves the accepted EpicDash-JZ visual/layout references while using Epic-DashTune-UXUI only for proven interaction/responsive/performance lessons.

Compose owns presentation/gesture/layout/camera state; Rust owns semantic runtime/tuning truth.

## Logging / playback / MLG / MDF

`LOGGING_INTEROP_EVIDENCE.md` locks the order:

```text
format-neutral live acquisition/channel path
→ MLG writer + optimization
→ MDF4 evaluation/implementation on identical workload
→ scalable Analyzer
```

Mobile log size is the first format decision metric, followed by I/O/CPU/thermal/recovery cost.

## Diagnostics

`DIAGNOSTICS_EVIDENCE.md` and `DIAGNOSTICS_CORE_EVIDENCE.md` establish stable semantic channel/warning/test-vector lessons while keeping diagnostics observational rather than control authority.

## Tune Viewer

`TUNE_VIEWER_EVIDENCE.md` establishes exact-profile interpretation/comparison rules, explicit semantic tuner types and conservative cross-profile behavior.

Changed table axes are non-comparable by default unless a separately specified transform is introduced.

The viewer is evidence for identity/comparison behavior, not a second parser authority or 3D-renderer source.

## AE / PID / TCharge / Injection Angle

`ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md` and `ANALYSIS_RESPONSIBILITY_MAP.md` normalize the historical plugins into one future Rust analysis platform with domain profiles.

Permanent rules include:

- observation, derived analysis, recommendation and ECU mutation are separate authority levels;
- evidence is source/profile/relevant-calibration/version bound;
- missing/stale evidence is not zero;
- interpolation/model guidance cannot fabricate direct evidence;
- independent event/visit blocks matter more than raw sample count;
- valid evidence can still be non-comparable or unidentified;
- existing logs may be reused across compatible analyses;
- Guided Capture is optional where existing evidence is sufficient;
- all temporary candidate Apply/Restore work must use the one semantic tuning authority;
- analyzers never own raw ECU writes or Burn.

---

# Current open work classification

`GAP_ANALYSIS.md` is now the authoritative unresolved-work list.

## Early Phase 2 measured PoCs

- Kotlin USB bulk I/O vs Android-opened native resource → Rust sustained I/O;
- packed RuntimeSnapshot/JNI lifecycle and performance;
- dual-ABI physical qualification on SM-A137F and SM-T500;
- Compose rendering/jank under representative runtime load.

## Feature-entry prerequisites

Examples:

- ECU protocol/genuine generated-INI golden corpora;
- project/backup/journal concrete schemas before tuning persistence ships;
- MLG executable interoperability corpus before production MLG acceptance;
- MDF4 independent interoperability and compression benchmarks before selecting the native baseline;
- concrete AnalysisSession/domain-profile/ExperimentProposal schemas before Analyzer/autotune feature rollout;
- structured diagnostic event/metric/export schemas.

## Deliberately deferred product decisions

Examples:

- rich table-edit operation scope;
- dashboard customization/presets;
- future direct ESP32 GPIO/relay capability;
- cross-profile table regridding;
- later analyzer profile rollout order;
- specialized GPU renderer unless Compose measurements prove a need.

These deferred choices do not block the clean foundation.

---

# Recommended Phase 2 entry slice

After explicit Phase 1 acceptance, begin only the small non-mutating foundation:

```text
Android application/bootstrap
→ Compose host
→ Rust shared library for both production ARM ABIs
→ NativeEngine create/close/recreate
→ versioned packed RuntimeSnapshot ABI
→ minimal Rust ChannelStore
→ synthetic/native test-channel publication
→ Compose display of snapshot
→ bounded native event drain
→ JNI/snapshot lifecycle diagnostics
```

Initial slice explicitly excludes:

- ECU mutation;
- Burn;
- MDF writer;
- full Tuner migration;
- independent plugin ports.

After the foundation is proven, continue with the already locked measurement path: live ECU acquisition first, then MLG, then MDF4 evaluation, then large-scale Analyzer work.

Tuning mutation remains later and uses the physically accepted EpicDash-JZ behavior as the safety oracle until equivalent or deliberately improved EpicHub behavior is qualified.

---

# Exact current next action

Do **not** perform more broad repository archaeology by default.

Present the Phase 1 gate result for explicit user acceptance. If accepted:

1. mark the final `REVIEW_CHECKLIST.md` item;
2. record Phase 1 acceptance;
3. merge/clean PR #1 as deliberately directed;
4. begin the non-mutating Phase 2 foundation slice above.