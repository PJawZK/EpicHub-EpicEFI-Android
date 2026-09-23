# EpicHub Current Handoff

## Continuation instruction

Read this file completely first. Then verify the current branch/head/PR state in `PJawZK/EpicHub-EpicEFI-Android` and follow the exact next action below.

Do not reconstruct current architecture from EpicDash-JZ or other old repositories when this repository establishes a newer deliberate contract.

---

# Repository state

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Permanent integration branch: `main`

Active branch: `phase1/architecture-inventory`

Open draft PR: **#1 — Phase 1: architecture and responsibility inventory**

Branch head immediately before this handoff update:

`aab3227837e7c619b6d38221f9d1356d0ae3cfd3`

This handoff update itself advances the branch. Always verify the actual current head before making changes.

`main` still contains the clean-slate bootstrap; Phase 1 architecture documentation remains on PR #1 until explicitly accepted/merged.

No production Android/Rust scaffold exists yet.

---

# Current phase

**Phase 1 is at its explicit acceptance gate.**

Architecture/source/test review result:

```text
45 / 46 checklist items satisfied
```

The only unchecked item is:

> user accepts Phase 1 result before production scaffold begins.

Authority:

- `docs/architecture/PHASE1_GATE_ASSESSMENT.md`
- `docs/architecture/REVIEW_CHECKLIST.md`
- `docs/architecture/PHASE1_PROGRESS.md`
- `docs/architecture/GAP_ANALYSIS.md`

Do not start production scaffolding until the user explicitly accepts Phase 1.

---

# Durable architecture

Read `docs/architecture/DECISIONS.md` for D-001 through D-021.

Important locked decisions:

- EpicHub is a new clean-slate Android application; no EpicDash-JZ install/update continuity is required.
- Kotlin + Jetpack Compose owns Android-facing application/UI/platform behavior.
- Rust owns sustained high-volume channel/logging/analysis work and eventually semantic tuning after qualification.
- Kotlin↔Rust traffic is coarse semantic operations and bounded/versioned snapshots/results, never per-channel/per-sample JNI ping-pong.
- one ECU protocol/session owner;
- one domain-neutral Rust ChannelStore;
- exact generated profile/INI is structural authority;
- presentation never receives raw tune page/offset/byte authority;
- complete verified TuneSnapshot is tuning truth;
- RAM Write uses semantic resolution + ACK + exact readback + full expected-snapshot verification;
- Burn remains separate and explicit;
- uncertainty fails closed with no blind replay;
- Android BLE/GATT remains Kotlin-owned with one connection-wide operation scheduler;
- original ESP32 is a BLE↔CAN bridge, not an inherited direct relay controller;
- any future direct output capability is separately versioned and firmware owns physical failsafes;
- runtime acquisition is format-neutral;
- implementation order is live channel path → MLG → MDF4 evaluation → scalable Analyzer;
- mobile log size is the first file-format decision metric;
- diagnostics observe but never become hidden control authority;
- full semantic tune comparison requires exact authoritative profile identity;
- historical AE/PID/TCharge/Injection Angle plugins become domain profiles on one shared analysis platform, not independent ports;
- observations, analysis, recommendations and ECU mutation are separate authority levels;
- a qualifying recorded session may be reused across compatible analyses instead of forcing new Guided Capture.

---

# Android build baseline

Authority: `docs/architecture/ANDROID_BUILD_BASELINE.md` and D-021.

Initial contract:

```text
applicationId / namespace = com.epicefi.epichub
minSdk                  = 26
targetSdk               = 36
compileSdk              = 37
production native ABIs  = armeabi-v7a + arm64-v8a
```

Dual ARM support is required initially because the existing SM-A137F phone may expose a 32-bit Android userspace while the SM-T500 tablet supports arm64.

First physical scaffold must record `Build.SUPPORTED_ABIS` from both devices rather than relying on SoC capability assumptions.

---

# Completed Phase 1 evidence domains

Detailed evidence is indexed by `docs/architecture/README.md`.

Completed reviews include:

- EpicDash-JZ broad responsibility decomposition;
- direct current EpicEFI/rusEFI USB/signature/framing/read/write/Burn verification;
- original EpicDash/ESP32 BLE↔CAN bridge;
- evolved EpicDash-JZ Android BLE behavior;
- generated INI/profile/condition authority;
- tune Read/Write/Burn safety;
- project persistence/backup/uncertainty recovery;
- Dashboard/Tuner UI interaction/responsive/performance behavior;
- MSL playback, MLG-first logging and MDF4 transition architecture;
- diagnostics/performance/lifecycle behavior;
- Diagnostics-Core shared semantic channel/warning/test-vector lessons;
- EpicEFI Tune Viewer exact-profile comparison/definition behavior;
- AE/PID/TCharge/Injection Angle analysis/autotune evidence.

Important deep-dive docs:

- `ECU_PROTOCOL_DIRECT_VERIFICATION.md`
- `ESP32_ACCESSORY_EVIDENCE.md`
- `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md`
- `UI_INTERACTION_EVIDENCE.md`
- `LOGGING_INTEROP_EVIDENCE.md`
- `DIAGNOSTICS_EVIDENCE.md`
- `DIAGNOSTICS_CORE_EVIDENCE.md`
- `TUNE_VIEWER_EVIDENCE.md`
- `ANALYZER_AUTOTUNE_PLUGIN_EVIDENCE.md`
- `ANALYSIS_RESPONSIBILITY_MAP.md`

---

# Remaining open work is classified, not hidden

Read `GAP_ANALYSIS.md` before assuming an open item blocks Phase 2.

## Early Phase 2 measured PoCs

- Kotlin USB bulk I/O versus Android-opened native resource → Rust sustained I/O;
- versioned packed RuntimeSnapshot/JNI lifecycle/performance;
- dual-ABI physical qualification on SM-A137F and SM-T500;
- Compose frame/jank behavior under representative engine/logging load.

## Feature-entry prerequisites

Examples:

- ECU protocol/generated-INI golden corpora;
- concrete project/backup/journal formats before tuning persistence ships;
- MLG compatibility corpus before production writer acceptance;
- MDF independent interoperability/version/compression benchmarks;
- concrete AnalysisSession/domain-profile/ExperimentProposal schemas before Analyzer/autotune rollout;
- diagnostic event/metric/export schemas.

## Deliberately deferred product decisions

Examples:

- rich table-edit operation scope;
- dashboard customization/presets;
- direct ESP32 GPIO/relay capability;
- cross-profile table regridding;
- later analyzer profile rollout priority;
- specialized GPU rendering unless Compose proves insufficient.

These do not block the clean foundation.

---

# Do not regress

- Do not copy historical large classes wholesale.
- Do not reintroduce WebView/JSON as the runtime hot path.
- Do not create `T3`/`B2`/`New`-style permanent names.
- Do not create a second generated-INI/parser/condition authority.
- Do not create alternate ECU Write/Burn paths.
- Do not treat USB VID/PID or board name as profile authority.
- Do not make Dashboard/UI the telemetry authority.
- Do not route ordinary ESP32 GATT through Rust just for centralization.
- Do not treat GATT completion as downstream CAN/hardware confirmation.
- Do not make diagnostics/warning definitions hidden subsystem control policy.
- Do not let interpolated/model analyzer guidance count as direct observed evidence.
- Do not require Guided Capture when an existing immutable session satisfies the same analysis contract.
- Do not create additional handoff files.

---

# Phase 2 entry plan after explicit acceptance

First implementation slice is deliberately small and non-mutating:

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
→ JNI/snapshot lifecycle diagnostics
```

First slice excludes:

- ECU writes;
- Burn;
- MDF writer;
- full Tuner migration;
- plugin ports.

After foundation qualification, continue with:

```text
format-neutral live ECU path
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

Tuning mutation remains later and uses physically accepted EpicDash-JZ behavior as its oracle until EpicHub parity/improvement is qualified.

---

# Exact next action

If the user explicitly accepts Phase 1:

1. mark the final item in `REVIEW_CHECKLIST.md`;
2. record Phase 1 acceptance in repository docs;
3. merge/clean draft PR #1 as directed;
4. establish `main` as the accepted architecture baseline;
5. begin Phase 2 with only the non-mutating foundation slice above.

If the user does **not** accept Phase 1 yet, discuss/revise the specific concern only. Do not restart broad source archaeology without a concrete evidence gap.