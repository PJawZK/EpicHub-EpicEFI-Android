# EpicHub Dependency Rules

## Purpose

The codebase topology is useful only if dependency direction prevents domains from silently absorbing each other's responsibilities.

These are Phase 1 architectural rules. Exact crate/package visibility is finalized before implementation.

## Core direction

```text
Compose UI
  │
  ├─► semantic actions ─► Kotlin platform/accessory owners
  │
  └─► NativeEngine façade ─► JNI/FFI ─► Rust Engine
                                      │
                         ┌────────────┼────────────┐
                         ▼            ▼            ▼
                       ECU/Profile  Channels      Tuning
                                      │
                           ┌──────────┼──────────┐
                           ▼          ▼          ▼
                         Logging     MDF      Analysis
                                                │
                                                ▼
                                             Plotting
```

Dependencies should point toward lower-level domain contracts, never toward presentation consumers.

## Kotlin UI rules

Compose UI may depend on:

- UI models/state;
- `ActionRouter` and semantic action contracts;
- the Kotlin `NativeEngine` façade;
- Android presentation helpers/components.

Compose UI must not depend directly on:

- ECU framing/CRC;
- raw tune pages/offsets/encoded bytes;
- MDF block structures;
- Android BLE characteristic objects for normal accessory actions;
- raw GPIO mappings;
- historical WebView/JavaScript bridge contracts.

## Kotlin platform rules

Android platform packages may use Android framework APIs and expose narrow domain-neutral/resource abstractions upward.

`platform/usb` must not parse tune values or own profile compatibility.

`platform/bluetooth` must not decide accessory semantic meaning.

`platform/location` must not own dashboard widgets or GPS-to-ESP32 wire encoding.

`platform/storage` must not implement MDF/tune serialization.

## Accessory rules

`accessory` may depend on:

- semantic action contracts;
- Bluetooth transport client/schema;
- location data for explicit GPS forwarding;
- a coarse channel publisher into Rust.

It must not depend on:

- ECU tune-memory internals;
- MDF implementation details;
- Dashboard-specific state.

The ESP32 firmware remains physical output authority; Android accessory code must not expose raw GPIO mutation to layouts/scripts.

## Native boundary rules

Normal Kotlin production code calls Rust only through `nativeengine`.

The FFI surface must be coarse and semantic. Prohibited design patterns include:

- one JNI getter per channel;
- one JNI call per ECU sample;
- Rust→Kotlin callbacks per sample/channel;
- passing large object graphs through JNI when packed/shared buffers are suitable;
- exposing Rust internal pointers/struct layout without a versioned ABI contract.

## Rust engine rules

`epichub-engine` composes subsystems but should delegate protocol/file-format/analysis implementation to owning crates/modules.

It may coordinate lifetimes and transactions; it should not become a new giant `Manager`.

## Channels rules

`epichub-channels` is domain-neutral measurement/runtime state.

It must not import:

- Compose/Android;
- tuner UI concepts;
- MDF block structures;
- ESP32 GATT types.

ECU/GPS/accessory sources adapt observations into channel/sample contracts.

## ECU rules

ECU protocol code may depend on transport/profile/channel primitives required to interpret firmware data.

It must not depend on:

- Dashboard/Tuner Compose packages;
- MDF writer internals;
- accessory BLE/GATT;
- arbitrary app settings.

## Profile rules

Profile parsing/model code owns interpretation of the exact generated profile/INI.

Other domains consume typed compiled schemas rather than reparsing the INI independently.

There must not be a second profile parser hidden in the tuner or dashboard.

## Tuning rules

Tuning may depend on:

- accepted profile tune schema;
- sole ECU session/transport operation interface;
- immutable TuneSnapshot identity.

It must not accept raw page/offset/byte payloads from UI/actions/scripts as production edit requests.

Burn remains a tuning transaction, not a UI or generic transport function.

## Logging/MDF rules

`logging` orchestrates recording sessions and writes sample/event batches through a measurement writer abstraction.

`mdf` implements MDF format mechanics.

Therefore:

- `logging` should not duplicate MDF block code;
- `mdf` should not know UI/dashboard/tune semantics;
- the dashboard must not be in the write path.

## Analysis/plotting rules

`analysis` operates on measurement/channel sources and produces semantic results.

`plotting` turns relevant time-series results into viewport-sized trace packets.

Neither depends on Compose. Compose renders prepared results and owns user interaction/cursors/viewport requests.

## Diagnostics rules

Diagnostics may observe/report other systems but should not become their decision authority.

Instrumentation must not change queue/transport/tuning decisions merely to make metrics easier to collect.

## Interop rules

MSL/MLG/CSV adapters translate external formats into/out of EpicHub's common measurement/channel contracts.

External file formats must not become the runtime data model used throughout the app.

## Enforcement direction

Before Phase 2, these rules should be translated into practical enforcement where worthwhile:

- Rust crate dependency graph;
- Kotlin package/module visibility;
- lint/architecture tests for prohibited imports;
- FFI ABI tests;
- code-review checklist.
