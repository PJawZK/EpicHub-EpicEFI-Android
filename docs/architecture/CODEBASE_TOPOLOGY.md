# EpicHub Codebase Topology

## Status

This document describes the **current physical repository topology plus the rules for deliberate expansion**.

The accepted Phase-2 Kotlin/Rust foundation and the implemented PR #3 UI/diagnostics slice are real source code. Future responsibilities are described as destinations only; empty package/crate forests are not pre-created.

Mandatory durable rules:

- D-022 — inherited behavior is understood piece-by-piece and reimplemented cleanly;
- D-023 — production runtime is Kotlin/Compose + Rust, with typed/packed runtime boundaries and serialization only at explicit persistence/export/interoperability boundaries;
- D-024 — historical tuning-plugin capabilities become native EpicHub Tuning Tools, not a runtime plugin system;
- D-025 — four primary user workspaces may group distinct leaf/domain routes without merging their owners/dependencies.

---

# Repository shape

```text
EpicHub-EpicEFI-Android/
├── .github/workflows/
├── app/                         # Kotlin / Compose Android product
├── rust/engine/                 # current Rust native engine crate
├── docs/
│   ├── HANDOFF.md
│   └── architecture/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

A new package/module/crate appears only when an actual dependency, lifecycle, performance or test boundary justifies it.

---

# Current Kotlin physical topology

Permanent namespace:

```text
com.epicefi.epichub
```

Current source responsibilities include:

```text
app/src/main/kotlin/com/epicefi/epichub/
├── EpicHubApplication.kt        # process-scoped diagnostics + generation allocation
├── EpicHubActivity.kt           # Android Activity / lifecycle adaptation / Compose host
├── EpicHubApp.kt                # current app orchestration; synthetic RuntimeSnapshot polling
├── navigation/
│   └── AppDestination.kt        # PrimaryWorkspace + distinct leaf AppDestination mapping
├── diagnostics/
│   ├── DiagnosticCaptureArchive.kt
│   ├── DiagnosticCaptureCborEncoder.kt
│   ├── DiagnosticClock.kt
│   ├── DiagnosticHub.kt
│   ├── DiagnosticLifecycleRegistry.kt
│   ├── DiagnosticMetrics.kt
│   ├── DiagnosticModel.kt
│   ├── DiagnosticRing.kt
│   └── NativeDiagnosticIngestor.kt
├── engine/
│   ├── NativeDiagnosticBatch.kt
│   ├── NativeEngine.kt
│   └── RuntimeSnapshot.kt
├── platform/
│   └── diagnostics/             # Android diagnostic archive/share transport
└── ui/
    ├── EpicHubShell.kt
    ├── components/
    ├── dashboard/
    ├── tuner/
    ├── tools/                   # separate Tuning Tools presentation/workflow owner
    ├── logger/
    ├── analyzer/                # separate Analyzer presentation/workflow owner
    ├── diagnostics/
    └── settings/
```

The exact file list may grow as implementation is decomposed, but these current owners are real.

Important ownership notes:

- `EpicHubApplication` owns the process-lifetime diagnostic recorder, not feature-domain state;
- `EpicHubActivity` owns Android Activity lifecycle adaptation, not application/domain authority;
- `EpicHubApp` is temporary composition/orchestration glue and must continue decomposing rather than becoming a catch-all controller;
- `NativeEngine` is the Kotlin JNI façade, not the native semantic engine itself;
- UI packages own presentation/workflow only;
- visual workspace grouping never grants one UI package ownership over another domain.

---

# Current navigation topology

The current shell exposes four primary user workspaces:

```text
Dashboard | Tuner | Logger | Diagnostics
```

Distinct leaf/domain routes remain:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

Current mapping:

```text
PrimaryWorkspace.DASHBOARD
└─ AppDestination.DASHBOARD

PrimaryWorkspace.TUNER
├─ AppDestination.TUNER
└─ AppDestination.TUNING_TOOLS

PrimaryWorkspace.LOGGER
├─ AppDestination.LOGGER
└─ AppDestination.ANALYZER

PrimaryWorkspace.DIAGNOSTICS
└─ AppDestination.DIAGNOSTICS
```

The primary navigation renders only `PrimaryWorkspace` values. Workspace-local structural navigation selects leaf `AppDestination` values. The app remembers the most recent Tuner-workspace and Logger-workspace leaf route so returning to a workspace can restore the user's prior context.

Diagnostics record the leaf route, so `analyzer` does not become indistinguishable from `logger`, and `tuning-tools` does not become indistinguishable from `tuner` merely because they share primary navigation.

Settings is global.

Dashboard and Logger visual detail remains deliberately provisional while the user develops separate HTML visual prototypes for tablet/phone landscape/portrait. Those prototypes may later become explicit presentation authority without changing this route/owner topology.

---

# Current Rust physical topology

```text
rust/engine/
├── Cargo.toml
├── Cargo.lock
└── src/
    ├── lib.rs
    ├── engine.rs
    ├── jni_api.rs
    ├── channel_store.rs
    ├── runtime_snapshot.rs
    └── diagnostics.rs
```

Current responsibilities:

- `engine.rs` — native engine lifetime/composition for the current foundation;
- `jni_api.rs` — coarse JNI boundary with panic containment;
- `channel_store.rs` — engine-owned typed runtime channel authority;
- `runtime_snapshot.rs` — fixed/versioned RuntimeSnapshot ABI v1 publication;
- `diagnostics.rs` — bounded native diagnostic ring + fixed/versioned diagnostic batch ABI;
- `lib.rs` — crate entry/module declaration only.

The current single Rust crate is not permission to grow a giant engine. Future ECU/profile/tuning/logging/analysis responsibilities split into modules/crates when real dependency/test/build boundaries justify it.

---

# Current Kotlin ↔ Rust boundaries

Runtime snapshot:

```text
Rust ChannelStore
→ fixed/versioned RuntimeSnapshot packed buffer
→ reusable direct ByteBuffer
→ Kotlin typed decode
→ Compose projection
```

Diagnostics:

```text
Rust bounded diagnostic ring
→ fixed/versioned bounded diagnostic batch
→ one coarse JNI drain
→ Kotlin typed decode/ingest
→ process-scoped DiagnosticHub
→ optional Compose Diagnostics projection
```

Prohibited structures:

- one JNI getter/callback per sample/channel/event;
- JSON/CBOR runtime message bus;
- WebView/JavaScript/DOM production presentation;
- duplicate Kotlin high-rate ChannelStore;
- Rust depending on Android UI framework types;
- dynamic JAR/JavaScript plugin loading for Tuning Tools;
- Analyzer calls routed through Logger merely because they share a workspace;
- Tuning Tools calls routed through Tuner presentation merely because they share a workspace.

CBOR currently exists only at the explicit diagnostic capture export boundary.

---

# Diagnostics topology

Implemented current ownership:

```text
Kotlin process scope
DiagnosticHub
├─ bounded event ring
├─ metric summaries
├─ lifecycle/generation registry
├─ capture state
└─ finalized immutable captures

Rust NativeEngine
└─ bounded native event ring
   └─ bounded packed JNI drain

Compose Diagnostics
└─ Overview / Events / Metrics / Sessions / Captures

Android platform/diagnostics
└─ capture.cbor + report.txt archive / FileProvider share
```

The expensive full diagnostics UI projection is refreshed continuously only while the Diagnostics route is visible. Recording and native drain continue independently.

Activity and NativeEngine generations are owners inside the process-scoped recorder. Destroyed-owner history is bounded; active owners are not evicted.

Diagnostics remains observational. Future live connection controls route to the transport/session owner; persistent connection policy belongs under Settings → Connections. Warning incidents/history remain observable; rule configuration belongs under Settings → Warnings & alerts.

---

# Expected future Kotlin destinations

Create only as implementation requires:

```text
platform/usb          Android USB discovery/permission/open/close
platform/bluetooth    Android BLE/GATT
platform/location     Android location
platform/storage      SAF/document URI transport
platform/lifecycle    shared Android lifecycle adapters when justified
actions               semantic application action routing/policy
accessory              ESP32 bridge / future distinct direct-output gateway
settings               typed application/user preferences
```

Do not move ECU/profile/log-file authority into these Android packages.

---

# Expected future Rust domains

As substantive features arrive:

```text
ECU transport/session/protocol
profile / generated-INI compilation
tuning / TuneSnapshot / semantic draft / write / Burn / recovery
measurement contracts
logging
MLG interoperability
MDF4
shared analysis primitives
Tuning Tools domain profiles/models
expressions/math
plot preparation
project persistence / backup
```

Possible future crate names are descriptive implementation choices, not architecture authority. Responsibility ownership and dependency direction are authoritative.

---

# Tuning Tools topology

Visual placement:

```text
Primary Tuner workspace
└─ Tuning Tools leaf route
```

Runtime/ownership path:

```text
Compose ui/tools
→ typed guided workflow/capture request
→ coarse NativeEngine analysis operation
→ Rust shared analysis platform
→ Rust domain profile/model
→ typed result / recommendation / experiment proposal
→ Compose tool workspace
```

AE/PID/TCharge/Injection Angle do not each receive their own plugin runtime, ECU transport, ChannelStore, parser stack or Burn path.

Analyzer remains generic data exploration. Tuning Tools owns guided domain workflows. Tuner owns manual semantic calibration editing. All mutation remains behind the single Rust tuning authority.

---

# Logger / Analyzer topology

Visual placement:

```text
Primary Logger workspace
├─ Logger leaf route
└─ Analyzer leaf route
```

Ownership remains parallel over shared lower-level contracts:

```text
ChannelStore / MeasurementStore
├─ Logger owner → recording/session/writer health
└─ Analyzer owner → query/plot/statistics/compare
```

Analyzer does not use Logger UI/state as a data gateway. Logger may hand a completed immutable session/source identity to Analyzer without transferring ownership.

---

# UI prototype rule

HTML/CSS/light JavaScript may be used outside production to prototype screens rapidly.

```text
prototype
→ explicit visual/layout acceptance
→ native Compose translation
→ physical comparison
```

The planned visual-authority family may contain separate references for:

```text
Tablet Landscape
Tablet Portrait
Phone Landscape
Phone Portrait
```

These are presentation recipes over one semantic architecture, not four production implementations.

No WebView package or browser runtime is created merely because a prototype was convenient.

---

# Next topology expansion

After PR #3 physical qualification and acceptance, the next inherited subsystem is read-only ECU acquisition. D-022 review happens before production code.

Conceptual boundary remains:

```text
Kotlin platform/usb
→ discovery / permission / descriptor / open / close
→ selected transport resource
→ Rust ECU session/protocol
→ exact signature/profile gate
→ runtime decode
→ ChannelStore
```

Sustained USB I/O ownership remains benchmark-driven; no candidate creates a second ECU session or Dashboard-specific transport.

Logging remains format-neutral:

```text
source
→ decode/timestamp
→ ChannelStore / sample batches
→ Logger
→ MeasurementWriter
    ├─ MLG first
    └─ MDF4 after evaluation
```

---

# File and naming rules

One production file has one primary reason to change.

Avoid permanent catch-alls such as `Utils`, `Helpers`, `Common` dumping grounds, `DataManager`, `SystemManager`, `EpicHubManager`, or giant Activity/App/engine files accumulating unrelated domains.

A file around 400–600 lines is a responsibility-review signal, not an automatic split threshold.

Use permanent semantic names, never development-history labels such as T3/T4/T6/W4/V2/New.

## Summary

> Keep the real tree as small as the current implementation permits, make process/platform/presentation/native ownership explicit, preserve coarse typed Kotlin↔Rust boundaries, and let four simple user workspaces group distinct domain routes without turning visual hierarchy into runtime dependency hierarchy.
