# EpicHub Codebase Topology

## Status

This document describes the **current physical repository topology plus the rules for its deliberate expansion**.

Phase 1 originally defined responsibility destinations before code existed. The first Phase-2 foundation is now real and qualified, so the actual source tree is no longer allowed to be described as if production scaffolding were still pending.

Detailed behaviour-to-destination mapping remains in `RESPONSIBILITY_DESTINATIONS.md`. This file defines where code exists now, which future domain homes are expected, and when a new package/crate/file is justified.

D-022 and D-023 are mandatory:

- inherited functionality is reviewed piece-by-piece and reimplemented cleanly;
- production runtime code remains Kotlin/Jetpack Compose + Rust;
- runtime/JNI state is typed/packed;
- serialization is restricted to explicit persistence/export/interoperability boundaries.

---

# Repository shape

Current top level:

```text
EpicHub-EpicEFI-Android/
├── .github/workflows/
├── app/                         # Kotlin / Compose Android product
├── rust/engine/                 # current minimal Rust native engine crate
├── docs/
│   ├── HANDOFF.md
│   └── architecture/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

The repository does **not** create empty package/crate forests merely because the long-term architecture has many domains. A new package/module/crate appears when a real implementation slice creates a dependency, lifecycle, performance or test boundary that benefits from it.

---

# Current Kotlin foundation

Permanent application namespace:

```text
com.epicefi.epichub
```

Current accepted physical source tree:

```text
app/src/main/kotlin/com/epicefi/epichub/
├── EpicHubActivity.kt
├── EpicHubApp.kt
└── engine/
    ├── NativeEngine.kt
    └── RuntimeSnapshot.kt
```

These files are deliberately small foundation owners:

- `EpicHubActivity.kt` — Android Activity / Compose host only;
- `EpicHubApp.kt` — current application/synthetic foundation surface; it is not a permanent multi-domain controller;
- `engine/NativeEngine.kt` — Kotlin JNI/native-engine façade and lifecycle;
- `engine/RuntimeSnapshot.kt` — Kotlin-side typed decoder/representation for the versioned packed RuntimeSnapshot ABI.

As UI/diagnostics work is implemented, `EpicHubApp.kt` must decompose rather than grow into a catch-all.

---

# Expected Kotlin expansion

The target responsibility homes remain conceptually:

```text
com/epicefi/epichub/
├── app/                 # process/application-scoped Android concerns if a split is justified
├── navigation/          # AppRoute/NavHost and route-only state
├── platform/
│   ├── usb/             # discovery, permission, descriptor/open/close Android ownership
│   ├── bluetooth/       # Android BLE/GATT ownership
│   ├── location/        # Android location acquisition
│   ├── storage/         # SAF/document URI transport only
│   ├── lifecycle/       # Android lifecycle adaptation
│   └── diagnostics/     # Android/platform diagnostic emitters
├── engine/              # Kotlin NativeEngine façade / ABI adapters
├── actions/             # semantic app action routing/policy
├── accessory/           # legacy ESP32 bridge / future distinct direct-output gateway
├── settings/            # typed application/user preferences
└── ui/
    ├── dashboard/
    ├── tuner/
    ├── logger/
    ├── analyzer/
    ├── diagnostics/
    ├── settings/
    └── components/
```

This is a **responsibility topology, not an instruction to pre-create every directory**.

The active UI foundation contract is `UI_FOUNDATION_LAYOUT.md`:

- primary destinations: Dashboard, Tuner, Logger, Analyzer, Diagnostics;
- Settings is a global application surface;
- one responsive semantic screen model reflows across phone/tablet/orientation;
- global diagnostics capture remains reachable from the common shell.

Compose never becomes ECU/profile/log-file authority.

---

# Current Rust foundation

Current accepted physical Rust tree:

```text
rust/engine/
├── Cargo.toml
├── Cargo.lock
└── src/
    ├── lib.rs
    ├── engine.rs
    ├── jni_api.rs
    ├── channel_store.rs
    └── runtime_snapshot.rs
```

Current responsibilities:

- `engine.rs` — native engine lifetime/composition for the small foundation;
- `jni_api.rs` — coarse JNI boundary with panic containment;
- `channel_store.rs` — minimal engine-owned typed runtime channel authority;
- `runtime_snapshot.rs` — fixed/versioned RuntimeSnapshot ABI publication;
- `lib.rs` — crate entry only.

The current `rust/engine` crate is intentionally small. It is **not** evidence that all future ECU/profile/tuning/logging/analysis responsibilities belong in one giant crate/file.

---

# Expected Rust domain expansion

As substantive features arrive, the following permanent domains must obtain clear homes, either as modules inside the existing crate or as separate crates when dependency/test/build boundaries justify the split:

```text
engine/lifecycle
ffi / ABI
channels
ECU transport/session/protocol
profile / generated-INI compilation
tuning / TuneSnapshot / semantic draft / write / Burn / recovery
measurement contracts
logging
MLG/interop
MDF4
analysis
expressions/math
plot preparation
diagnostics
project persistence / backup
```

A possible later multi-crate workspace may use descriptive domains such as:

```text
epichub-engine
epichub-channels
epichub-ecu
epichub-profile
epichub-tuning
epichub-measurement
epichub-logging
epichub-analysis
epichub-diagnostics
epichub-interop
```

but **crate names/count are not architecture authority**. Responsibility ownership and dependency direction are.

Do not split a crate simply to make the tree resemble a Phase-1 sketch.

---

# Kotlin ↔ Rust placement rule

Kotlin owns Android framework integration and presentation.

Rust owns or is intended to own sustained/high-volume semantic engines and native data work.

Normal crossing pattern:

```text
Compose / Kotlin semantic request
→ NativeEngine façade
→ coarse JNI operation
→ Rust owner

Rust current/runtime state
→ bounded/versioned packed snapshot or result buffer
→ Kotlin typed decode/projection
→ Compose
```

Prohibited target structures:

- one JNI getter per channel;
- JSON message bus across JNI;
- WebView/JavaScript/DOM runtime presentation;
- Kotlin-owned duplicate high-rate ChannelStore;
- Rust code directly depending on Android UI framework types.

See `KOTLIN_RUST_BOUNDARY.md` and `RUNTIME_LANGUAGE_AND_SERIALIZATION.md`.

---

# Diagnostics topology

The current diagnostics design adds responsibility, not a new authority layer.

Expected homes when implemented:

```text
Kotlin
- platform diagnostic emitters
- Compose diagnostics/capture UI
- Android document/share transport

Rust
- engine event ring
- metric accumulators
- engine lifecycle/session/correlation events
- canonical diagnostic-capture schema/encoding owner where appropriate
```

Crossing is a coarse bounded diagnostic drain, not one JNI callback per event.

Canonical diagnostic archive representation is the D-023 boundary format defined in `DIAGNOSTICS_CAPTURE_FOUNDATION.md`: versioned CBOR machine data plus generated human `report.txt` in the export archive. That encoding does not become runtime state.

---

# ECU/profile/tuning topology rule

When the read-only live ECU slice begins, Android USB and native ECU responsibilities remain split deliberately:

```text
Kotlin platform/usb
→ discover / permission / descriptor / open / close
→ selected transport resource
→ Rust ECU session/protocol
→ exact signature/profile gate
→ runtime decode
→ ChannelStore
```

The USB implementation may benchmark multiple sustained-I/O ownership options, but no option creates a second ECU session or a Dashboard-specific transport.

Future tune mutation lives below semantic tuning authority:

```text
Compose semantic edit intent
→ Rust tune draft / target resolution
→ verified write plan
→ exclusive ECU transaction
→ ACK + exact range readback + complete expected TuneSnapshot verification
```

Burn remains a separate explicit transaction.

---

# Logging / measurement / analyzer topology rule

The live runtime model stays format-neutral:

```text
source
→ decode/timestamp
→ ChannelStore / sample batches
→ Logger
→ MeasurementWriter
    ├─ MLG interoperability writer first
    └─ MDF4 later after measurement/interop evaluation
```

Analyzer uses common channel/measurement contracts rather than one file-format-specific object model.

File codecs never become runtime ChannelStore authority.

---

# Accessory topology rule

Legacy ESP32 BLE↔CAN compatibility and any future direct GPIO/relay capability remain separate.

Expected Kotlin semantic owners:

```text
platform/bluetooth
→ one GATT client / one connection-wide operation scheduler

accessory/Esp32BridgeGateway
→ legacy button / variable request / variable set semantics

accessory/<future direct-output gateway>
→ only if a versioned direct-output protocol is deliberately added
```

Raw UUIDs/button masks/GPIO identifiers do not become UI semantic action IDs.

---

# File and naming rules

One production file has one primary reason to change.

Avoid permanent catch-alls such as:

- `Utils`;
- `Helpers`;
- `Common` as a dumping ground;
- `DataManager`;
- `SystemManager`;
- `EpicHubManager`;
- giant Activity/ViewModel/engine files that accumulate unrelated domains.

A file approaching roughly 400–600 lines triggers a responsibility review; it is not an automatic split threshold.

Use permanent semantic names, never development-history labels such as T3/T4/T6/W4/V2/New.

---

# Current implementation order

The current active work is:

```text
accepted Kotlin/Rust synthetic foundation
→ UI shell/placeholder surfaces
→ typed diagnostics/flight-recorder/capture foundation
→ physical UI/diagnostic qualification on SM-A137F + SM-T500
→ read-only live ECU acquisition D-022 gate + implementation
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

Tuning mutation, BLE/accessory migration and other high-risk inherited subsystems enter later through their own D-022 review/qualification gates.

## Summary

The topology rule is intentionally conservative:

> preserve clear ownership and dependency direction, keep the real tree as small as the current implementation permits, and split into additional packages/crates only when an actual responsibility boundary exists.

The current minimal physical tree is authoritative for what exists today; `RESPONSIBILITY_DESTINATIONS.md` is authoritative for where later responsibilities belong.