# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual branch/PR head, then follow the exact next action.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or other historical repositories when EpicHub documentation establishes a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- Phase 1 PR: **#1 — Phase 1: architecture and responsibility inventory — merged**
- Phase 1 merge commit: `d98c5f8a6e3dcad0b479323e12d0e845d6d3fefb`
- Phase 1 review result: **46 / 46 accepted**
- Phase 1 accepted/closed: **2026-09-23**
- production Android/Rust implementation had not started before acceptance
- next development branch: `phase2/foundation-bootstrap`

Always verify the current branch/PR head when resuming. The Phase 1 merge SHA above is a historical accepted-baseline marker, not a claim that it remains the current `main` head after later documentation or implementation commits.

---

# Current phase

**Phase 2 — foundation bootstrap.**

Phase 1 architecture is now the accepted baseline. Do not reopen broad source archaeology without a concrete evidence gap. Historical-source review now happens narrowly at each inherited subsystem's implementation-entry gate under D-022.

Primary authority/read order:

1. `docs/architecture/README.md`
2. `docs/architecture/DECISIONS.md`
3. `docs/architecture/PHASE1_GATE_ASSESSMENT.md`
4. `docs/architecture/GAP_ANALYSIS.md`
5. `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md`
6. `docs/architecture/ANDROID_BUILD_BASELINE.md`
7. domain contract/evidence docs when their subsystem enters implementation

---

# Mandatory implementation methodology

D-022 / `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` remains mandatory:

```text
semantic behavior slice
→ trace relevant old implementation/callers/state/lifecycle/errors/tests/bug history
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ design a clean permanent owner/contract
→ implement from scratch
→ qualify against protocol truth/proven behavior/focused fixtures
→ regression-test old failure modes
→ benchmark hot paths
→ accept the narrow slice
```

Never bulk-copy an EpicDash-JZ/plugin subsystem and clean it later.

---

# Locked architecture summary

Durable Phase 1 decisions are D-001 through D-022 in `DECISIONS.md`.

Key contracts:

- Kotlin/Compose owns Android-facing UI/platform integration.
- Rust owns ChannelStore, high-volume measurement/logging/analysis and eventually qualified semantic tuning.
- high-rate Kotlin↔Rust traffic uses bounded/coarse snapshots/batches; low-rate discrete Android-source events may cross individually when justified.
- one authoritative ECU protocol/session owner.
- exact live TunerStudio signature is the compatibility gate; exact selected profile source/fingerprint owns compiled/project/tuning identity.
- one Rust ChannelStore.
- complete verified TuneSnapshot is live tune truth.
- semantic RAM Write = baseline identity → plan → bounded `C` → ACK → exact `R` readback → complete expected TuneSnapshot verification.
- Burn is separate/explicit; uncertainty fails closed; no blind replay.
- Android BLE/GATT stays Kotlin-owned with one connection-wide scheduler.
- existing ESP32 is a legacy BLE↔CAN bridge, not a direct relay controller.
- future direct ESP32 outputs, if added, are a separate versioned capability with firmware-owned physical failsafes.
- warnings/diagnostics are observational; any warning-driven automation is a separate AutomationRule/ActionPolicy path.
- Rust owns semantic tune draft/history; Kotlin owns Tuner selection/text-entry/gesture/presentation state.
- runtime/logging architecture is format-neutral.
- implementation order after foundation: **live path → MLG interoperability → MDF4 evaluation/implementation → scalable Analyzer**.
- `MeasurementWriter` / `MeasurementStore` are common format-neutral contracts.
- analyzers share one Rust evidence/analysis platform with domain profiles; recommendation never grants ECU mutation authority.

---

# Android support baseline

Authority: `ANDROID_BUILD_BASELINE.md` / D-021.

```text
applicationId / namespace = com.epicefi.epichub
minSdk                  = 26
targetSdk               = 36
compileSdk              = 37
production native ABIs  = armeabi-v7a + arm64-v8a
```

Exact AGP/Kotlin/Compose/Gradle/NDK/Rust patch versions are selected and reproducibly pinned as part of the Phase 2 scaffold, then qualified as a complete toolchain rather than treated as architecture.

First physical qualification records `Build.SUPPORTED_ABIS` on SM-A137F and SM-T500 and proves JNI/native loading on both production ABI paths.

---

# Phase 2 foundation scope

The first production slice is deliberately new, small and non-mutating:

```text
Android application/bootstrap
→ Compose host
→ dual-ABI Rust shared library
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
- MDF/MLG production writer implementation;
- full Tuner migration;
- BLE/accessory migration;
- plugin ports;
- analyzer/autotune feature work.

The purpose is to qualify the clean Kotlin↔Rust application foundation before attaching inherited/high-risk domains.

---

# First Phase 2 acceptance targets

The foundation is not accepted merely because it compiles.

At minimum prove:

- reproducible clean build from pinned toolchain;
- application launches through the Compose host;
- Rust library loads on both production ARM ABIs;
- `NativeEngine` create/close/recreate is leak/crash safe across repeated lifecycle cycles;
- packed RuntimeSnapshot schema is versioned and bounds-checked;
- synthetic channels flow Rust → bounded snapshot → Kotlin → Compose without per-channel JNI calls;
- inactive UI does not create unnecessary high-rate work;
- bounded native events cannot grow memory without limit;
- process/activity recreation does not leave stale native handles authoritative;
- basic frame/jank, CPU, memory and JNI cadence are measurable from the first slice.

No ECU hardware mutation is needed to qualify this foundation.

---

# Work carried forward from Gap Analysis

Early measured work includes:

- USB runtime ownership comparison:
  - Kotlin I/O/decode reference;
  - Kotlin I/O → coarse Rust batches/decode;
  - Android-opened native resource → Rust sustained I/O/decode;
- packed RuntimeSnapshot/JNI lifecycle/performance;
- both production ARM ABIs on SM-A137F + SM-T500;
- Compose frame/jank under representative load.

Feature-entry prerequisites remain where they belong: genuine ECU/INI/MLG/MDF corpora, project/journal schemas, analyzer schemas, diagnostics schemas and other domain-specific qualification evidence are added when those features enter implementation.

---

# Exact next action

1. create `phase2/foundation-bootstrap` from the cleaned accepted `main` baseline;
2. pin the first complete compatible Android/Rust build toolchain;
3. scaffold only the Android/Compose host + dual-ABI Rust library + minimal `NativeEngine` lifecycle;
4. get that clean scaffold building before adding RuntimeSnapshot or ChannelStore complexity;
5. add RuntimeSnapshot/ChannelStore/synthetic-channel flow incrementally with tests and diagnostics;
6. keep ECU mutation, Burn, logging formats, Tuner migration and plugin/analyzer work out of this first slice;
7. apply D-022 if any inherited behavior is introduced.