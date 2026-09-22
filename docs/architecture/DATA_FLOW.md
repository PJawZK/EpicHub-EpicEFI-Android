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
        ├──► Logger ─► measurement writer ─► MDF4
        │
        ├──► Expressions / warnings
        │
        └──► Analyzer/live queries where useful
```

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
        └──► exact complete signature comparison
                    │
                    ▼
              imported generated INI
                    │
                    ▼
            accepted compiled profile
                    │
             ┌──────┴──────┐
             ▼             ▼
       runtime schema   tune schema
```

A board-name match alone never enters the authority path.

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

## ESP32 accessory command flow

```text
Compose / physical input / approved rule
        │
        ▼
semantic ActionRequest
        │
        ▼
Kotlin ActionRouter
        │
        ▼
Esp32AccessoryGateway
        │
        ▼
Esp32MessageCodec
        │
        ▼
BleWriteScheduler / GATT
        │
        ▼
ESP32 firmware
        │
        ▼
relay/GPIO/CAN action under firmware failsafe authority
```

Rust is not in this command path unless the semantic event is separately published for logging/analysis.

## ESP32 state/variable return flow

```text
ESP32
  │ BLE notification/state response
  ▼
Esp32GattClient
  ▼
Esp32MessageCodec
  ▼
Esp32AccessoryGateway
  │
  ├──► AccessoryStateStore ─► Compose
  │
  └──► batched AccessoryChannelPublisher
                 │
                 ▼
          Rust ChannelStore
                 │
        ┌────────┼────────┐
        ▼        ▼        ▼
      MDF4    Analyzer  warnings/math
```

Requested state and confirmed/reported state remain distinguishable.

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
        └──► Esp32GpsEncoder ─► accessory BLE ─► ESP32/CAN (when enabled)
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
        └──► MeasurementStore writer abstraction
                         │
                         ▼
                       MDF4
                         │
                    optional sidecar
                    index/recovery state
```

The MDF writer is a file-format implementation, not the logging-session owner.

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
MeasurementStore / MDF reader + index
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

Whole-file raw sample arrays should not cross into Kotlin for ordinary graph display.

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
