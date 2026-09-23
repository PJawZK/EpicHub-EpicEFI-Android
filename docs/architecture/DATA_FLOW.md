# EpicHub Data Flow

## Purpose

This document defines the intended direction of important runtime data. It complements state/dependency ownership and prevents UI/file-format/transport layers from becoming accidental data authorities.

## Live ECU telemetry

Target flow:

```text
Android USB discovery/permission/open
        │
        ▼
selected ECU transport resource
        │
        ▼
Rust ECU session / protocol
        │
        ▼
runtime decode using exact profile
        │
        ▼
Rust ChannelStore
        │
        ├──► RuntimeSnapshot ─► FFI buffer ─► Compose Dashboard/Tuner
        │
        ├──► Logger ─► MeasurementWriter ─► selected recording format
        │
        ├──► Expressions / warnings
        │
        └──► Analyzer/live queries where useful
```

The recording format is intentionally not part of ChannelStore/runtime authority. The locked implementation sequence is live path first, then MLG interoperability, then MDF4 evaluation/implementation on the same workload.

The UI is not in the acquisition/logging path.

## ECU identity/profile authority flow

```text
safe USB transport candidate
        │
        ▼
live signature query
        │
        ├──► diagnostic firmware recognition
        │
        └──► exact complete signature compatibility gate
                    │
                    ▼
              imported generated INI
                    │
                    ├──► exact source fingerprint
                    │
                    ▼
            accepted compiled profile
                    │
             ┌──────┴──────┐
             ▼             ▼
       runtime schema   tune schema
```

Signature equality is a necessary live compatibility gate. The selected profile source/fingerprint remains the exact source identity for compiled schemas, caches, project state and tuning plans. A board-name match alone never enters the authority path.

## Tune read flow

```text
Compose Tuner
   │ semantic request
   ▼
NativeEngine
   ▼
Rust tuning snapshot reader
   ▼
exclusive ECU session
   ▼
profile-derived complete read plan
   ▼
complete TuneSnapshot
   ▼
verify/adopt immutable tune authority
   ▼
semantic TuneModel projection
   ▼
Compose Tuner
```

## Tune RAM write flow

```text
Compose semantic edit/draft
        │
        ▼
NativeEngine semantic request
        │
        ▼
target resolver + safety policy
        │
        ▼
validated WritePlan + expected complete snapshot
        │
        ▼
exclusive ECU transaction
        │
        ├─► baseline reread/fingerprint
        ├─► bounded C write(s)
        ├─► ACK
        ├─► exact edited-range readback
        └─► complete TuneSnapshot reread
                    │
                    ▼
           exact expected-image match?
             │ yes             │ no/ambiguous
             ▼                 ▼
 verified RAM state       uncertain/fail closed
```

No UI/raw-address bypass path exists.

## Burn flow

```text
explicit user Burn request
        │
        ▼
verified TuneSnapshot + dirty/flash state
        │
        ▼
profile-derived BurnPlan
        │
        ▼
exclusive BurnTransaction
        │
        ├─► flash-ready preflight
        ├─► page Burn request/ACK
        ├─► firmware progression/completion evidence
        └─► complete post-Burn TuneSnapshot
                    │
                    ▼
        verified unchanged RAM image
        + no remaining required unsaved state
                    │
                    ▼
             clear saved dirty state
```

Burn is never implicit after RAM editing.

## Legacy ESP32 bridge command flow

The reviewed legacy device is a BLE↔CAN bridge, not a direct relay/GPIO controller.

```text
Compose / physical input / approved automation request
        │
        ▼
semantic ActionRequest(target = AccessoryBridge)
        │
        ▼
Kotlin ActionRouter
        │
        ▼
Esp32BridgeGateway
        │
        ▼
message-specific legacy codec
        │
        ▼
BleWriteScheduler / GATT
        │
        ▼
ESP32 legacy bridge
        │
        ▼
CAN forwarding / legacy bridge behavior
```

Android GATT completion proves only transport completion. It does not prove that the downstream CAN consumer applied a semantic action. Rust is not in this command path unless the semantic event is separately published for logging/analysis.

## Future direct ESP32 output flow

Only if a versioned direct-output capability is deliberately added:

```text
semantic ActionRequest(target = AccessoryOutput)
        │
        ▼
Kotlin ActionRouter
        │
        ▼
Esp32DirectOutputGateway
        │
        ▼
versioned output codec/protocol
        │
        ▼
BleWriteScheduler / GATT
        │
        ▼
ESP32 direct-output firmware capability
        │
        ▼
physical relay/GPIO under firmware mapping/interlock/failsafe authority
```

This path must not be inferred from or merged into legacy bridge compatibility.

## ESP32 bridge state/variable return flow

```text
ESP32 legacy bridge
  │ BLE notification / variable response
  ▼
Esp32GattClient
  ▼
message-specific legacy decoder
  ▼
Esp32BridgeGateway
  │
  ├──► bridge request/transport state ─► Compose where useful
  │
  └──► batched AccessoryChannelPublisher
                 │
                 ▼
          Rust ChannelStore
                 │
        ┌────────┼────────┐
        ▼        ▼        ▼
     Logger   Analyzer  warnings/math
```

Legacy downstream state remains unknown unless independently observed. A future direct-output capability, if added, has its own explicitly acknowledged/reported state model.

## GPS flow

```text
Android Location API
        │
        ▼
AndroidLocationSource
        │
        ▼
validated LocationSample
        │
        ├──► batched runtime-channel publication ─► Rust ChannelStore
        │
        └──► GpsVariableEncoder ─► generic variable-set codec ─► BLE ─► ESP32/CAN (when enabled)
```

Dashboard rendering never owns GPS acquisition truth.

## Native logging flow

```text
Channel/sample observations
        │
        ▼
logging Recorder
        │
        ├──► LogSession status/metrics
        │
        └──► MeasurementWriter abstraction
                         │
             ┌───────────┴───────────┐
             ▼                       ▼
      MLG interoperability       MDF4 native candidate
       writer/reader path        after benchmark/interop proof
```

The logger owns recording-session orchestration. Format writers own only their format encoding/finalization/recovery mechanics. No writer format becomes runtime channel authority.

## Offline analyzer flow

```text
Compose viewport/query request
        │
        ▼
NativeEngine
        │
        ▼
Rust Analyzer
        │
        ▼
MeasurementStore / source-specific reader + optional index
        │
        ├─► required block/range reads only
        ├─► conversions/expressions
        ├─► analysis operation
        └─► resolution/downsampling
                    │
                    ▼
            bounded Plot/Result packet
                    │
                    ▼
               Compose render
```

MDF4 may provide the long-term native large-log reader/index path, but the analyzer contract is not MDF-specific. Whole-file raw sample arrays should not cross into Kotlin for ordinary graph display.

## Interoperability import flow

```text
MSL / MLG / CSV
        │
        ▼
format-specific interop reader
        │
        ▼
common channel/measurement representation
        │
        ├──► playback/dashboard
        └──► analyzer / optional conversion/export
```

External formats do not become the native ChannelStore type system.

## Diagnostic flow

Subsystems emit structured events/metrics to their owning diagnostic adapter/ring. Diagnostics UI reads bounded snapshots/events and may export a combined report.

Diagnostics observation must not become command/transport/tuning authority.