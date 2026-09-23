# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual branch/PR head, then follow the exact next action.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or other historical repositories when EpicHub documentation establishes a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- active branch: `phase1/architecture-inventory`
- draft PR: **#1 — Phase 1: architecture and responsibility inventory**
- `main` still contains the clean-slate bootstrap
- no production Android/Rust scaffold exists yet

Always verify the current PR/head SHA when resuming; this file deliberately does not embed a SHA that becomes stale with its own commit.

---

# Current phase

**Phase 1 is at the explicit acceptance gate after a dedicated documentation-consistency audit.**

Review checklist:

```text
45 / 46 satisfied
```

Only unchecked item:

> user accepts Phase 1 result before production scaffold begins.

Primary authority/read order:

1. `docs/architecture/README.md`
2. `docs/architecture/DECISIONS.md`
3. `docs/architecture/PHASE1_GATE_ASSESSMENT.md`
4. `docs/architecture/REVIEW_CHECKLIST.md`
5. `docs/architecture/GAP_ANALYSIS.md`
6. domain contract/evidence docs referenced by the above

Do not start production scaffolding until explicit acceptance.

---

# Critical implementation methodology

Phase 1 performed responsibility archaeology, **not** line-by-line certification of all old code.

D-022 / `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` is mandatory throughout implementation:

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

Historical code is evidence for requirements, protocol behavior, useful techniques and known failures—not a structural template.

---

# Locked architecture summary

Durable decisions are D-001 through D-022 in `DECISIONS.md`.

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
- legacy bridge owner is `Esp32BridgeGateway` with message-specific codecs; GATT completion is not downstream confirmation.
- future direct ESP32 outputs, if added, use a separate versioned capability/gateway and firmware-owned physical failsafes.
- warnings/diagnostics are observational; any warning-driven automation is a separate AutomationRule/ActionPolicy path.
- Rust owns semantic tune draft/history; Kotlin owns Tuner selection/text-entry/gesture/presentation state.
- runtime/logging architecture is format-neutral.
- implementation order: **live path → MLG interoperability → MDF4 evaluation/implementation → scalable Analyzer**.
- `MeasurementWriter` / `MeasurementStore` are common format-neutral contracts.
- analyzers share one Rust evidence/analysis platform with domain profiles; recommendation never grants ECU mutation authority.
- qualifying immutable logs/sessions can be reused by multiple compatible analyses.

---

# Android build baseline

Authority: `ANDROID_BUILD_BASELINE.md` / D-021.

```text
applicationId / namespace = com.epicefi.epichub
minSdk                  = 26
targetSdk               = 36
compileSdk              = 37
production native ABIs  = armeabi-v7a + arm64-v8a
```

First physical scaffold records `Build.SUPPORTED_ABIS` on SM-A137F and SM-T500.

---

# Consistency audit completed

The pre-acceptance audit found and reconciled actual drift, including:

- generic accessory gateway/codec wording vs legacy bridge/direct-output split;
- warning rules appearing able to execute actions;
- generic logger/data/thread docs hardcoded to MDF despite MLG-first architecture;
- legacy ESP32 path incorrectly implying relay/GPIO authority;
- absolute per-sample JNI prohibition conflicting with the intended low-rate exception;
- ambiguous Kotlin/Rust semantic tune-draft ownership;
- settings ownership still marked unresolved;
- MDF-specific measurement-file ownership;
- provisional namespace wording after D-021 locked it;
- missing common `MeasurementWriter` and later analysis responsibilities in topology;
- stale D-021-only decision-log references after D-022;
- tests conflating legacy watchdog/liveness with future direct-output failsafe behavior.

The reconciled authority and final checklist are in `PHASE1_GATE_ASSESSMENT.md` and `REVIEW_CHECKLIST.md`.

---

# Remaining work is classified, not hidden

Use `GAP_ANALYSIS.md`. Main categories:

## Early Phase 2 measured PoCs

- USB runtime ownership comparison:
  - Kotlin I/O/decode reference;
  - Kotlin I/O → coarse Rust batches/decode;
  - Android-opened native resource → Rust sustained I/O/decode.
- packed RuntimeSnapshot/JNI lifecycle/performance;
- both production ARM ABIs on SM-A137F + SM-T500;
- Compose frame/jank under representative load.

## Feature-entry prerequisites

Examples:

- ECU protocol + generated-INI golden corpora;
- project/backup/journal formats before tuner persistence;
- genuine MLG compatibility corpus;
- MDF independent interoperability/compression benchmarks;
- AnalysisSession/domain-profile/ExperimentProposal schemas;
- diagnostic event/metric/export schemas.

Every inherited feature also passes D-022 when it enters implementation.

## Deliberately deferred

Examples:

- rich table-edit feature scope;
- dashboard customization scope;
- direct ESP32 GPIO/relay capability;
- cross-profile table regridding;
- later analyzer rollout order;
- specialized GPU renderer unless Compose measurements require it.

---

# Phase 2 entry plan after acceptance

First slice is deliberately new, small and non-mutating:

```text
Android bootstrap
→ Compose host
→ dual-ABI Rust shared library
→ NativeEngine create/close/recreate
→ versioned packed RuntimeSnapshot
→ minimal Rust ChannelStore
→ synthetic test channels
→ Compose snapshot display
→ bounded native event drain
→ JNI/snapshot diagnostics
```

Exclude from the first slice:

- ECU mutation;
- Burn;
- MDF writer;
- full Tuner migration;
- plugin ports.

After foundation qualification:

```text
format-neutral live ECU path
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

---

# Exact next action

If the user accepts Phase 1:

1. mark the final checklist item;
2. record Phase 1 acceptance;
3. merge/clean draft PR #1 as directed;
4. establish `main` as the accepted architecture baseline;
5. begin only the non-mutating Phase 2 foundation slice;
6. apply D-022 at every inherited subsystem implementation-entry gate.

If the user does not accept Phase 1, revise the specific concern only. Do not restart broad archaeology without a concrete evidence gap.