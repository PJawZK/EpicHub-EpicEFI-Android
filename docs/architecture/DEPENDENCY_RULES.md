# EpicHub Dependency Rules

## Purpose

The topology only works if dependency direction prevents domains from silently absorbing each other's responsibilities.

These are Phase 1 rules. Exact crate/package visibility is finalized during the Phase 2 scaffold while preserving these boundaries.

## Core direction

```text
Compose UI
  │
  ├─► semantic actions ─► Kotlin platform / bridge / accessory owners
  │
  └─► NativeEngine façade ─► JNI/FFI ─► Rust Engine
                                      │
                         ┌────────────┼────────────┐
                         ▼            ▼            ▼
                       ECU/Profile  Channels      Tuning
                                      │             │
                           ┌──────────┴─────────┐   ├─ project authority
                           ▼                    ▼   └─ operation journal
                        Logging             Analysis
                           │                    │
                           ▼                    ├─► Plotting
                    MeasurementWriter          │
                    /              \           ▼
               MLG/interop          MDF   MeasurementStore
                                           /           \
                                      interop readers   MDF reader
```

Dependencies point toward lower-level domain contracts, never toward presentation consumers. MLG/MDF are format implementations at the measurement boundary; neither becomes ChannelStore/runtime authority.

## Kotlin UI rules

Compose may depend on:

- UI models/state;
- semantic `ActionRouter` contracts;
- Kotlin `NativeEngine` façade;
- presentation helpers.

Compose must not depend directly on:

- ECU framing/CRC;
- raw tune pages/offsets/encoded bytes;
- measurement-file block/record structures;
- BLE characteristic objects for semantic actions;
- CAN IDs/button masks/raw GPIO as user-facing identity;
- historical WebView/JS bridge contracts.

A UI draft is not verified ECU or persisted project authority.

## Kotlin platform rules

Android platform packages may use Android framework APIs and expose narrow resource abstractions upward.

- `platform/usb` does not parse tunes or decide profile compatibility.
- `platform/bluetooth` does not interpret downstream CAN/action success.
- `platform/location` does not own dashboard widgets or generic ESP32 variable-set semantics.
- `platform/storage` does not implement measurement/tune/profile serialization or decide whether a project generation is valid.

Android SAF/document code moves validated/imported/exported bytes and URI permissions; Rust project/tuning/measurement code owns format/schema and identity validation.

## Legacy bridge rules

Legacy ESP32 bridge code may depend on:

- semantic action/bridge request contracts;
- Bluetooth transport/schema;
- location values only through an explicit GPS→variable-set encoder;
- coarse channel ingestion into Rust.

It must not depend on:

- ECU tune-memory internals;
- measurement-format internals;
- Dashboard-specific state.

The generic variable-set codec must not import location; GPS is only one producer.

There is one connection-wide GATT operation scheduler. Button, variable-request and variable-set producers do not each own independent GATT write pipelines.

GATT completion is transport evidence only and cannot be promoted into downstream ECU/CAN confirmation.

## Future direct ESP32 output rules

Direct relay/GPIO control is not part of legacy bridge compatibility.

If added, versioned direct-output code may depend on the BLE transport but remains semantically separate from legacy button forwarding. Android layouts never receive raw GPIO authority.

Physical output mapping/defaults/interlocks/watchdog/disconnect fail-safe remain ESP32 firmware-owned.

## Native boundary rules

Normal Kotlin production code calls Rust only through `nativeengine`.

FFI is coarse and semantic. Prohibited hot-path patterns include:

- one JNI getter per channel;
- one JNI call per high-rate ECU sample;
- callbacks per high-rate sample/channel;
- huge object graphs where packed/shared buffers fit;
- exposing internal Rust pointers/layout without versioned ABI.

Low-rate discrete Android-source events may cross individually when batching would add complexity without material benefit, but this is an explicit low-rate exception rather than a pattern for ECU/runtime streaming.

## Rust engine rules

`epichub-engine` composes lifetimes/transactions but delegates protocol/file-format/profile/tuning/analysis to their owners. It must not become a replacement giant `Manager`.

## Channel rules

`epichub-channels` is domain-neutral runtime/measurement state.

It must not import:

- Compose/Android;
- tuner UI concepts;
- measurement-format block/record structures;
- ESP32 GATT types.

ECU/GPS/bridge sources adapt observations into sample/channel contracts.

## ECU rules

ECU protocol code may depend on transport/profile/channel primitives needed for firmware communication.

It must not depend on:

- Dashboard/Tuner Compose packages;
- measurement writer/reader format internals;
- BLE/GATT accessory code;
- arbitrary UI settings.

Request framing, response framing/status, synchronization policy and typed command operations remain distinct responsibilities.

## Profile rules

Profile code owns exact generated-INI interpretation.

Other domains consume typed compiled schemas; there is no second parser in Tuner/Dashboard.

Profile condition compatibility/write authority remains independently tested from the general analyzer expression language.

Derived compiled profile/schema caches depend on exact source identity and are disposable.

## Tuning rules

Tuning may depend on:

- accepted profile tune/condition schema;
- sole ECU session operation interface;
- immutable TuneSnapshot identity;
- app-private persistence primitive/path;
- project backup byte I/O adapter where necessary.

It must not accept raw page/offset/bytes from UI/actions/scripts as production edit requests.

Burn remains a tuning transaction, not UI/generic transport behavior.

### Project persistence rule

Rust tuning/project code owns the meaning and integrity of:

```text
exact profile source + complete verified TuneSnapshot
```

Kotlin storage code may provide path/URI/file transport but never decides that a project/profile/snapshot pair is authoritative.

Compiled schemas, semantic projections and startup caches are derived acceleration only.

### Portable backup rule

Portable recovery/backup is an encoding of project authority, not a third authority.

Import flow is:

```text
Android document bytes
→ Rust backup/schema validation
→ exact profile parse + snapshot identity validation
→ transactional local project-generation commit
```

Export reverses this without exposing tune mutation authority.

### Operation journal rule

The tuning operation journal depends on tuning transaction identity and durability primitives, but is independent from portable project backup.

It must be durable before possible hardware mutation, and its presence/corruption blocks further mutation until resynchronization. It never authorizes automatic replay.

No saved project cache may automatically clear an uncertainty journal.

## Logging / measurement-format rules

`logging` orchestrates recording and writes sample/event batches through a `MeasurementWriter` abstraction.

Format implementations own their own mechanics:

- MLG reader/writer lives in interoperability code for the first ecosystem-compatible writer path;
- MDF implements MDF block/metadata/compression/index/recovery mechanics when the native MDF4 path is introduced.

Therefore:

- logging does not duplicate MLG records or MDF blocks;
- format codecs know no Compose/dashboard/tune semantics;
- Dashboard is not in the writer path;
- selecting MLG or MDF does not change ChannelStore/runtime ownership.

## Analysis / plotting rules

`analysis` operates on `MeasurementStore`/channel sources and produces semantic results.

`plotting` produces viewport-sized trace packets.

Neither depends on Compose or on one mandatory file format. Compose owns interaction/cursors/viewport requests only.

## Diagnostics rules

Diagnostics observe/report but do not become subsystem decision authority.

Instrumentation must not change queue/transport/tuning decisions merely for convenience.

Warning evaluation emits warning state/incidents only. If a future automation reacts to warning state, that action is owned by a separate explicit AutomationRule/ActionPolicy path rather than by diagnostics/warning evaluation itself.

## Interop rules

MSL/MLG/CSV adapters translate external formats into/out of EpicHub common measurement/channel contracts. External formats do not become the internal runtime model.

## Enforcement direction

During the Phase 2 foundation and as each subsystem is introduced, translate important rules into practical enforcement where useful:

- Rust crate dependency graph;
- Kotlin visibility/package rules;
- architecture tests for prohibited imports;
- FFI ABI tests;
- persistence/operation-journal fault-injection tests;
- review checklist.

Architecture ownership is locked in Phase 1; the executable enforcement is added alongside the code it can actually validate.