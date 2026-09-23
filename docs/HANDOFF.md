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

Always verify the actual branch/PR head when resuming. The foundation merge SHA above is a milestone, not a claim that it remains the latest `main` commit after documentation updates.

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

Automated qualification passed rustfmt, Clippy with warnings-as-errors, Rust unit tests, Kotlin/JVM RuntimeSnapshot ABI tests, Android assembly, both production ARM Rust builds and APK native-library inspection.

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

---

# Current Phase 2 design slice

Before real ECU/USB acquisition, establish the application skeleton and bug-capture tooling so later subsystems enter a stable UI and diagnostic environment.

Current design authority under draft PR #3:

- `docs/architecture/UI_FOUNDATION_LAYOUT.md`
- `docs/architecture/DIAGNOSTICS_CAPTURE_FOUNDATION.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`

Proposed primary application destinations:

```text
Dashboard | Tuner | Logger | Analyzer | Diagnostics
```

Settings remains globally accessible rather than a sixth high-frequency destination.

The diagnostic foundation provides:

- always-on bounded typed diagnostic flight recorder;
- stable events/metrics/lifecycle owners/correlation IDs;
- global recent-problem capture;
- guided reproduction capture with user markers;
- previous-exit/crash context where Android permits;
- immutable DiagnosticCapture sessions;
- versioned CBOR + human text export;
- routine privacy-bounded bundles with explicit optional attachments.

Diagnostics remain observational under D-017.

---

# Exact next action

1. complete the repository-wide documentation consistency pass for D-023 and the new UI/diagnostic design;
2. iterate/accept PR #3's screen structure and diagnostic capture workflow;
3. implement the responsive application shell and placeholder surfaces for Dashboard, Tuner, Logger, Analyzer and Diagnostics using the existing synthetic RuntimeSnapshot foundation;
4. implement the typed/bounded diagnostic event, metric, lifecycle and capture path without JSON/CBOR runtime communication;
5. physically qualify UI reflow and capture/export on SM-A137F + SM-T500;
6. only then begin the D-022 source-understanding gate for the read-only live ECU acquisition path.

ECU writes, Burn, BLE/accessory migration, production logging formats and analyzer/autotune algorithms remain out of this current slice.