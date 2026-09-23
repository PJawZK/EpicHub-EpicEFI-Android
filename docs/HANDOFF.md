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

Always verify the actual branch/PR head when resuming. Milestone SHAs above are not claims about the latest documentation commit.

---

# Accepted Phase 2 foundation

Accepted on `main`:

- `com.epicefi.epichub` Android namespace;
- Kotlin + Jetpack Compose host;
- pinned Android/Gradle/Kotlin/Rust/NDK toolchain;
- production ABIs `armeabi-v7a` + `arm64-v8a`;
- coarse JNI `NativeEngine` lifecycle;
- fixed/versioned 96-byte little-endian RuntimeSnapshot ABI v1;
- reusable direct ByteBuffer snapshot transfer;
- engine-owned minimal Rust ChannelStore;
- reserved synthetic test ChannelIds;
- low-rate synthetic Rust → RuntimeSnapshot → Kotlin → Compose path;
- JNI panic containment and serialized read/close lifecycle;
- committed `Cargo.lock` / `--locked` builds;
- CI APK verification for both ARM libraries.

Physical execution of the exact qualified foundation APK passed on both Samsung SM-T500 and SM-A137F.

No real ECU transport, ECU mutation/Burn, BLE/accessory migration, production logger format, full Tuner, Analyzer or Tuning Tools implementation is part of that accepted foundation.

---

# Locked implementation methodology

D-022 / `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` is mandatory for every inherited subsystem:

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

Never bulk-copy an EpicDash-JZ subsystem or a TunerStudio plugin and clean it later.

D-023 / `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md` is mandatory:

- production application logic remains Kotlin/Compose + Rust;
- no JavaScript/WebView/DOM runtime architecture;
- no JSON/CBOR document message bus inside the application;
- Kotlin↔Rust communication uses semantic JNI calls, versioned packed buffers and typed bounded batches;
- serialization exists only at explicit persistence/export/interoperability boundaries;
- canonical diagnostic capture export is versioned `capture.cbor` + `report.txt` with explicit optional attachments;
- CBOR is not a universal EpicHub storage format.

HTML/CSS and lightweight JavaScript are explicitly allowed for **design prototypes outside the production runtime**. Accepted prototypes are translated into Compose over typed Kotlin/Rust state, not embedded in a WebView.

D-024 / `docs/architecture/TUNING_TOOLS_FOUNDATION.md` is mandatory for historical plugin-derived functionality:

- AE/PID/TCharge/Injection Angle become native EpicHub **Tuning Tools**;
- shared analysis/provenance/statistics/domain models live in Rust under D-020;
- Compose owns guided workflow/presentation;
- Analyzer remains generic measurement exploration;
- all ECU mutation stays behind the single semantic tuning authority under D-019;
- no dynamic JAR/JavaScript/plugin loader, plugin-local ECU connection or plugin-local ChannelStore is recreated.

---

# Current Phase 2 design slice

Draft PR #3 establishes the UI skeleton, native Tuning Tools home and bug-capture architecture **before** real ECU/USB acquisition.

Current design authority:

- `docs/architecture/UI_FOUNDATION_LAYOUT.md`
- `docs/architecture/TUNING_TOOLS_FOUNDATION.md`
- `docs/architecture/DIAGNOSTICS_CAPTURE_FOUNDATION.md`
- `docs/architecture/RUNTIME_LANGUAGE_AND_SERIALIZATION.md`

Functional application destinations:

```text
Dashboard | Tuner | Tuning Tools | Logger | Analyzer | Diagnostics
```

Settings is global. Wide layouts may expose all six directly. Compact navigation may group destinations to avoid unusably small phone controls while keeping every destination deliberately reachable.

Tuning Tools initially reserves visible homes for:

```text
Acceleration Enrichment
PID Controller
TCharge
Injection Angle
```

Entries may honestly be `Planned`, `Evidence review`, `Read-only analysis`, `Guided capture`, `Recommendation qualified`, `Bounded experiment qualified` or unavailable for the current source/profile.

The diagnostic foundation specifies:

- always-on bounded typed diagnostic flight recorder;
- events/metrics/lifecycle owners/correlation IDs;
- recent-problem and reproduction capture;
- previous-exit/crash context where available;
- immutable DiagnosticCapture sessions;
- canonical versioned CBOR + human text export at the export boundary;
- privacy-bounded routine bundles with explicit optional attachments;
- observational behavior only under D-017.

---

# Visual design workflow

The user is actively developing Dashboard and Logger visual/layout concepts in HTML for rapid iteration.

These prototypes are **not production code**. Once supplied and explicitly accepted, use them as visual/layout authority inputs for the corresponding Compose surfaces.

Do not implement a large amount of speculative Dashboard/Logger styling before those prototypes mature enough to review. The structural shell/navigation/state contracts may proceed, but visual detail should be translated from the accepted prototypes rather than independently reinvented.

Prototype relationship:

```text
HTML/CSS prototype
→ visual/layout review
→ explicit acceptance
→ Compose translation
→ physical phone/tablet comparison
```

---

# Documentation consistency status

The repository consistency pass has been reconciled through D-024:

- Phase 1 records are historical closure evidence;
- the Kotlin/Rust foundation is already qualified, not pending;
- current physical topology is documented accurately;
- D-023 prevents browser/JSON runtime drift while allowing design-only HTML prototypes;
- D-024 prevents historical plugin packaging from becoming a new plugin subsystem;
- current UI authority includes Tuning Tools as a first-class functional destination;
- Analyzer and Tuning Tools have separate responsibilities;
- diagnostics remain typed/observational and export CBOR only at the explicit boundary.

Historical references may still mention JavaScript/JSON/plugins when accurately describing source evidence; those references do not override current durable decisions.

---

# Exact next action

1. continue reviewing/iterating PR #3's structural UI, Tuning Tools and diagnostics contracts;
2. incorporate the user's Dashboard/Logger HTML prototypes as visual/layout authority when they are mature enough to provide;
3. after design acceptance, implement the responsive shell and real placeholder/fixture surfaces for Dashboard, Tuner, Tuning Tools, Logger, Analyzer and Diagnostics using the existing synthetic RuntimeSnapshot foundation;
4. implement typed/bounded diagnostics and capture without JSON/CBOR runtime communication;
5. translate accepted Dashboard/Logger prototypes into Compose and physically compare on SM-A137F + SM-T500;
6. qualify navigation/reflow/diagnostic capture and tool-shell behavior on both devices;
7. only then begin the D-022 source-understanding gate for read-only live ECU acquisition.

ECU writes, Burn, BLE/accessory migration, production logging formats and deep Tuning Tool algorithms remain outside the immediate shell/diagnostics implementation slice.