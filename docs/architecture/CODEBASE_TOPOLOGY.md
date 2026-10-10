# EpicHub Codebase Topology

## Status

This document describes the **current physical repository topology plus the rules for deliberate expansion**.

EpicHub now has two deliberately different implementation surfaces in one repository:

1. the native Android product (`app/` + `rust/engine/`);
2. the GitHub Pages product/interaction prototype (`prototype/`).

The Pages surface is development/specification code under D-026. It does not become a second production runtime and does not weaken D-023.

Mandatory durable rules:

- D-022 — inherited behavior is understood piece-by-piece and reimplemented cleanly;
- D-023 — production runtime is Kotlin/Compose + Rust, with typed/packed runtime boundaries and serialization only at explicit persistence/export/interoperability boundaries;
- D-024 — historical tuning-plugin capabilities become native EpicHub Tuning Tools, not a runtime plugin system;
- D-025 — four primary user workspaces may group distinct leaf/domain routes without merging their owners/dependencies;
- D-026 — GitHub Pages is a first-class modular product/interaction lab whose accepted behavior is translated into the native product.

---

# Repository shape

```text
EpicHub-EpicEFI-Android/
├── .github/workflows/
│   ├── foundation-quality.yml
│   ├── prototype-quality.yml
│   └── prototype-pages.yml
├── app/                         # Kotlin / Compose Android product
├── rust/engine/                 # Rust native engine
├── prototype/                   # TypeScript/Vite GitHub Pages development surface
├── benchmark/                   # external Android Macrobenchmark harness
├── scripts/
├── docs/
│   ├── HANDOFF.md
│   └── architecture/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

A new package/module/crate/directory appears only when an actual dependency, lifecycle, performance, test or feature-ownership boundary justifies it.

---

# Native Android topology

Permanent namespace:

```text
com.epicefi.epichub
```

Current Kotlin responsibilities include:

```text
app/src/main/kotlin/com/epicefi/epichub/
├── EpicHubApplication.kt        # process-scoped diagnostics + generation allocation
├── EpicHubActivity.kt           # Android Activity / lifecycle adaptation / Compose host
├── EpicHubApp.kt                # current app orchestration; must continue decomposing
├── navigation/                  # workspace + leaf-route mapping
├── diagnostics/                 # typed recorder / metrics / capture foundation
├── engine/                      # Kotlin JNI façade + packed runtime models
├── platform/diagnostics/        # Android archive/share transport
└── ui/
    ├── EpicHubShell.kt
    ├── components/
    ├── dashboard/
    ├── tuner/
    ├── tools/                   # independent Tuning Tools presentation/workflow owner
    ├── logger/
    ├── analyzer/                # independent Analyzer presentation/workflow owner
    ├── diagnostics/
    └── settings/
```

Ownership notes:

- `EpicHubApplication` owns process-lifetime diagnostic recording, not feature-domain state;
- `EpicHubActivity` owns Android lifecycle adaptation, not application/domain authority;
- `EpicHubApp` is temporary composition/orchestration glue and must not become a catch-all controller;
- `NativeEngine` is the Kotlin JNI façade, not the native semantic engine itself;
- UI packages own presentation/workflow only;
- visual workspace grouping never grants one UI package ownership over another domain.

---

# Current navigation topology

Four primary user workspaces:

```text
Dashboard | Tuner | Logger | Diagnostics
```

Distinct leaf/domain routes:

```text
Dashboard
Tuner
Tuning Tools
Logger
Analyzer
Diagnostics
```

Mapping:

```text
Dashboard workspace
└─ Dashboard

Tuner workspace
├─ Tuner
└─ Tuning Tools

Logger workspace
├─ Logger
└─ Analyzer

Diagnostics workspace
└─ Diagnostics
```

Settings is global. Workspace-local presentation must not collapse sibling domain ownership.

---

# Current Rust topology

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
- `runtime_snapshot.rs` — fixed/versioned RuntimeSnapshot ABI publication;
- `diagnostics.rs` — bounded native diagnostic ring + fixed/versioned diagnostic batch ABI;
- `lib.rs` — crate entry/module declaration only.

The current single Rust crate is not permission to grow a giant engine. Future ECU/profile/tuning/logging/analysis responsibilities split into modules/crates when real dependency/test/build boundaries justify it.

---

# Kotlin ↔ Rust boundary

Runtime snapshot:

```text
Rust ChannelStore
→ fixed/versioned packed RuntimeSnapshot
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

Prohibited production structures include per-sample JNI calls/callbacks, JSON/CBOR runtime message buses, WebView/browser presentation, duplicate high-rate Kotlin channel authority and dynamic plugin loading for Tuning Tools.

---

# Pages prototype physical topology

The browser product/interaction lab is deliberately modular:

```text
prototype/
├── index.html                     # document/mount only
├── package.json
├── tsconfig.json
├── vite.config.ts
├── README.md
├── src/
│   ├── main.ts                    # bootstrap only
│   ├── app/
│   │   ├── create-app.ts          # composition root
│   │   ├── dependencies.ts        # adapter composition
│   │   ├── routes.ts              # workspace/leaf contract
│   │   ├── navigation-state.ts    # presentation navigation state
│   │   ├── shell.ts               # shell structure
│   │   └── feature-registry.ts    # route → feature renderer
│   ├── model/                     # transport-neutral prototype semantic types
│   ├── ports/                     # typed feature capabilities
│   ├── adapters/
│   │   ├── fixtures/              # deterministic/demo sources
│   │   └── ts-shim/               # future external tooling adapter
│   ├── ui/                        # genuinely shared presentation primitives
│   ├── features/
│   │   ├── dashboard/
│   │   ├── tuner/
│   │   ├── tuning-tools/
│   │   ├── logger/
│   │   ├── analyzer/
│   │   ├── diagnostics/
│   │   └── settings/
│   └── styles/                    # global tokens/base/shell only
└── tests/
```

Current prototype implementation is intentionally small. The directory drawing is a growth rule, not an instruction to create empty files.

## Prototype dependency direction

```text
app composition root
├─ features
├─ adapters
└─ shared ui

features → model + ports + shared ui
adapters → model + ports
ports    → model
model    → no DOM, no transport, no feature UI
```

Feature-local state/actions/view code stays inside that feature until a genuinely shared contract exists.

Forbidden browser shortcuts:

```text
feature → fixture implementation
feature → TS-Shim wire schema
feature → another feature's mutable state as its data gateway
adapter → page DOM
model → socket/network transport
one global all-feature state/controller file
```

Analyzer and Logger remain siblings over shared lower-level concepts. Tuning Tools and Tuner remain siblings over their proper lower-level concepts.

---

# TS-Shim topology

A future TS-Shim connection is an external interoperability adapter, not a new domain owner:

```text
Pages feature
→ typed prototype port
→ prototype/adapters/ts-shim
→ external transport / wire schema
→ TS-Shim
```

The adapter owns endpoint/connection/reconnect/wire parsing/serialization/capability translation. Feature code receives semantic typed state.

Initial integration should remain read-only until command/mutation identity, safety and verification are separately accepted. Raw ECU page/offset/byte authority must not leak into the browser feature layer.

If the shim requires JSON/WebSocket messages, that is an explicit development/tooling interoperability boundary under D-023, not the Android runtime representation.

---

# Pages → native translation topology

Accepted Pages code is not mechanically ported. Instead:

```text
accepted Pages feature
→ translation contract
   ├─ visual/interaction authority
   ├─ inputs/outputs/actions/states
   ├─ error/stale/loading behavior
   ├─ production owner(s)
   └─ prototype-only shortcuts to discard
→ Compose/Kotlin/Rust implementation
→ native tests + physical qualification
```

This lets the browser prototype be an executable product specification without becoming a second production architecture.

---

# Diagnostics topology

Implemented native ownership remains:

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
└─ evidence/presentation

Android platform/diagnostics
└─ capture.cbor + report.txt archive / FileProvider share
```

The Pages Diagnostics feature may prototype this interaction/presentation, but fixture or TS-Shim data never becomes native DiagnosticHub authority.

---

# Future native destinations

Create only as implementation requires:

```text
platform/usb
platform/bluetooth
platform/location
platform/storage
platform/lifecycle
actions
accessory
settings
```

Expected future Rust domains include ECU session/protocol, profile compilation, semantic tuning, logging/measurement formats, shared analysis, plotting preparation, expressions and project persistence.

---

# File and naming rules

One file has one primary reason to change.

Avoid permanent catch-alls such as:

```text
Utils
Helpers
Common
DataManager
SystemManager
EpicHubManager
app-state.ts containing every feature
one giant index.html
one giant styles.css
```

Native production files around 400–600 lines remain a responsibility-review signal.

Prototype files around **350–500 lines** are also a responsibility-review signal because web prototypes are especially prone to accumulating shell, feature, state and adapter code into one convenient file.

Line count is not a hard split threshold. The real question is whether the file has more than one independent reason to change.

Use permanent semantic names, never development-history labels such as T3/T4/T6/W4/V2/New.

## Summary

> Keep the native product and Pages prototype deliberately separate in runtime authority but aligned in semantic ownership. Use the browser surface to develop product behavior rapidly, keep both codebases modular by feature/responsibility, and let external tools such as TS-Shim enter only through explicit adapters.
