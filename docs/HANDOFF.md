# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the current `main` head and active Phase 2 branch/PR, then follow the exact next action.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or other historical repositories when EpicHub documentation establishes a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- Phase 1 PR #1: merged
- Phase 1 accepted/closed: **2026-09-23**
- Phase 2 foundation PR #2: **merged**
- foundation squash-merge commit: `e2c80f8f45272ec3ff1db740d5c553e9a0d0da32`
- qualified foundation content tree: `e918a7568ce46f263a06aed4d4c410cc547bca14`
- qualified foundation push run: **Foundation quality #36 / 35826507558 — PASS**
- active design branch: `phase2/ui-diagnostics-foundation`
- active draft PR: **#3 — Phase 2: establish UI and diagnostics foundations**

Always verify the actual branch/PR head when resuming. The foundation merge SHA above is a milestone, not a claim that it remains the latest `main` commit after later documentation work.

---

# Accepted Phase 2 foundation

The following clean-slate foundation is accepted on `main`:

- `com.epicefi.epichub` Android application namespace;
- Kotlin + Jetpack Compose application host;
- pinned Gradle/JDK/Android SDK/NDK/Kotlin/Compose/Rust/cargo-ndk toolchain;
- production native ABIs `armeabi-v7a` + `arm64-v8a`;
- coarse JNI `NativeEngine` lifecycle;
- fixed/versioned 96-byte little-endian RuntimeSnapshot ABI v1;
- reusable direct ByteBuffer snapshot transfer;
- engine-owned minimal Rust ChannelStore;
- reserved synthetic test ChannelIds;
- low-rate synthetic Rust → RuntimeSnapshot → Kotlin → Compose path;
- Rust panic containment at JNI boundary;
- serialized snapshot-read/close lifecycle;
- committed `Cargo.lock` and `--locked` Rust checks/builds;
- CI APK verification for both production native libraries.

No real ECU transport, ECU mutation, Burn, BLE/accessory migration, logger/MLG/MDF implementation, full Tuner or analyzer/autotune functionality is part of the accepted foundation.

---

# Qualification evidence

Automated foundation qualification passed rustfmt, Clippy with warnings-as-errors, Rust unit tests, Kotlin/JVM RuntimeSnapshot ABI tests, Android assembly, both production ARM Rust builds and APK native-library inspection.

Physical qualification of the exact qualified APK passed on both reference devices:

- Samsung **SM-T500** tablet;
- Samsung **SM-A137F** phone.

Observed on both: installation succeeds, app remains running, RuntimeSnapshot sequence advances, synthetic channel values remain valid/active and no native/lifecycle crash was observed during the reported checks.

---

# Locked implementation methodology

D-022 / `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` remains mandatory for every inherited subsystem:

```text
semantic behavior slice
→ trace old implementation/callers/state/lifecycle/errors/tests/bug history
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ design clean permanent owner/contract
→ implement from scratch
→ qualify against protocol truth/proven behavior/focused fixtures
→ regression-test old failure modes
→ benchmark hot paths
→ accept the narrow slice
```

Never bulk-copy an EpicDash-JZ subsystem and clean it later.

D-023 / `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md` is also mandatory:

- production application logic remains Kotlin/Compose + Rust;
- no JavaScript/WebView/DOM runtime architecture;
- no JSON/CBOR document message bus inside the application;
- Kotlin↔Rust runtime communication uses semantic JNI calls, versioned packed buffers and typed bounded batches;
- serialization exists only at explicit persistence/export/interoperability boundaries;
- canonical diagnostic capture export is versioned `capture.cbor` + `report.txt`, optionally packaged with explicitly selected attachments;
- JSON is optional external/developer tooling only when deliberately justified, never runtime/source truth.

CBOR is **not** a universal EpicHub storage format. Other domains retain their deliberately selected external/persistence formats.

---

# Current Phase 2 design slice

Draft PR #3 establishes the UI skeleton and bug-capture architecture **before** real ECU/USB acquisition.

Current design authority:

- `docs/architecture/UI_FOUNDATION_LAYOUT.md`
- `docs/architecture/DIAGNOSTICS_CAPTURE_FOUNDATION.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`

Primary application destinations:

```text
Dashboard | Tuner | Logger | Analyzer | Diagnostics
```

Settings is globally accessible rather than a sixth high-frequency destination.

The diagnostic foundation specifies:

- always-on bounded typed diagnostic flight recorder;
- stable events/metrics/lifecycle owners/correlation IDs;
- global recent-problem capture;
- reproduction capture with user markers;
- previous-exit/crash context where Android permits;
- immutable DiagnosticCapture sessions;
- canonical versioned CBOR + human text export;
- privacy-bounded routine bundles with explicit optional attachments;
- observational behavior only under D-017.

---

# Documentation consistency status

The repository-wide consistency pass for this design slice is complete.

It reconciled current-state and cross-domain documentation so that:

- Phase 1 files are historical closure records, not active gate instructions;
- the already-qualified Kotlin/Rust foundation is not described as unimplemented;
- `CODEBASE_TOPOLOGY.md` reflects the actual minimal source tree and deliberate future expansion;
- D-023 consistently prohibits JSON/WebView/browser/runtime-message-bus architecture;
- diagnostic export consistently uses `capture.cbor` + `report.txt` at the explicit export boundary;
- historical JSON/JavaScript/React/WebView references remain only where they accurately describe source evidence;
- the old four-destination EpicDash-JZ shell is historical evidence only; the current five-destination shell is authoritative;
- test strategy distinguishes already-proven foundation evidence from future UI/diagnostics/USB/tuning gates;
- Tune Viewer JSON export is historical evidence, not EpicHub's default report-format decision;
- current gap/protocol/state/threading/action/dependency/source/responsibility documents agree with the active Phase-2 state.

PR #3's description has also been reconciled to the same contracts.

---

# Exact next action

1. review/iterate and explicitly accept the PR #3 UI shell + diagnostics design contracts;
2. once accepted, implement the responsive application shell and placeholder surfaces for Dashboard, Tuner, Logger, Analyzer and Diagnostics using the existing synthetic RuntimeSnapshot foundation;
3. implement the typed/bounded diagnostic event, metric, lifecycle and capture path without JSON/CBOR runtime communication;
4. implement canonical diagnostic export only at the explicit persistence/export boundary;
5. CI- and physically qualify UI reflow plus capture/export on SM-A137F + SM-T500;
6. only then begin the D-022 source-understanding gate for the read-only live ECU acquisition path.

ECU writes, Burn, BLE/accessory migration, production logging formats and analyzer/autotune algorithms remain outside the current slice.