# EpicHub Semantic Action Model

## Purpose

EpicHub must support user/app actions that target different domains without exposing transport or hardware/memory implementation details to dashboard layouts or Compose controls.

Primary target domains:

- `Local` — actions implemented inside the Android application;
- `Accessory` — semantic commands handled by an accessory/bridge domain;
- `Ecu` — guarded semantic ECU/tuning/runtime actions handled by the engine.

## Core model

Conceptual request:

```text
ActionRequest
├── actionId
├── target domain
├── requested semantic value/state
├── source
├── timestamp/correlation ID
└── optional guard context
```

Conceptual result/state:

```text
ActionResult
├── accepted / rejected
├── pending / completed / failed
├── reason/status
└── correlation ID
```

The exact Kotlin types are defined in Phase 2 after the inventory is complete.

## UI rule

A dashboard control binds to a semantic action such as:

`buttonbox_aux_1`

or, for future direct accessory hardware where deliberately supported:

`aux_fan_override`

not:

- BLE characteristic UUID;
- button mask bit;
- ESP32 GPIO number;
- ECU page/offset/raw payload.

The target owner resolves implementation details.

## Local actions

Examples:

- change dashboard page;
- acknowledge warning;
- open analyzer;
- start/stop a local UI mode;
- request logging start/stop through the logging domain.

The router dispatches these directly to the appropriate Kotlin/app or NativeEngine API. They do not traverse unrelated transports.

## Accessory/bridge actions

The reviewed original ESP32 firmware is primarily a BLE↔CAN bridge with local input sources. It does **not** provide a reviewed BLE-controlled relay/GPIO output authority.

Legacy bridge path:

```text
Compose/input
  → ActionRouter
  → Esp32AccessoryGateway
  → Esp32MessageCodec
  → connection-wide BleWriteScheduler
  → Esp32GattClient
  → ESP32 bridge
  → CAN consumer / ECU
```

Rust is not in this command path merely to centralize architecture.

Legacy examples include:

- sending the 16-bit button-box mask to the ESP32 for CAN forwarding;
- requesting CAN-backed ECU variables through the bridge;
- sending generic variable-set values such as GPS or virtual ADC data.

## Legacy bridge state limitation

The current button protocol has no application-level acknowledgement/state message. Android GATT accepting a write is not evidence that the downstream CAN consumer applied the semantic action.

For a legacy bridge action EpicHub can represent states such as:

- unavailable/offline;
- request queued;
- handed to GATT;
- transport write completed/failed where Android reports it;
- downstream state unknown unless a separate authoritative feedback channel exists.

It must not label a button/CAN state as physically `confirmed` merely because a BLE write completed.

The original firmware also does not send an automatic zero/release button frame on BLE disconnect. Disconnect/release semantics therefore require explicit compatibility testing before EpicHub changes them.

## Future direct accessory outputs

If EpicHub later adds ESP32-controlled relays/GPIO outputs, that is a **new versioned accessory capability**, not behaviour inherited from the original bridge.

A direct-output extension must define:

- capability/version negotiation;
- semantic output IDs independent of GPIO numbers;
- application-level command acknowledgements;
- confirmed hardware state/fault feedback where needed;
- electrical/interlock ownership;
- disconnect/watchdog fallback;
- boot/default state;
- behavior across Android/app/BLE failure.

For such direct physical outputs, the ESP32 firmware—not Android—is the final hardware authority for mapping, interlocks and fail-safe state.

## Requested vs confirmed state

EpicHub distinguishes confirmation according to the capability actually available.

For a versioned accessory output with explicit feedback:

- unavailable/offline;
- known OFF;
- ON requested/pending;
- confirmed ON;
- OFF requested/pending;
- confirmed OFF;
- command failed/no response;
- hardware fault.

For the legacy button bridge, downstream confirmation is generally unavailable unless an independent returned ECU/channel value provides it. The UI/state model must retain that uncertainty rather than inventing confidence.

## Generic variable-set action/data path

The legacy characteristic historically named `GPS_DATA` actually carries generic 8-byte `hash + raw32/float32` variable-set entries. EpicHub therefore separates:

- `Esp32VariableSetCodec` — wire encoding;
- `GpsVariableEncoder` — GPS-specific producer;
- other producers such as virtual inputs where deliberately retained.

A transport name must not force unrelated producers into a GPS domain.

## ECU actions

Normal UI actions targeting tune mutation travel:

```text
Compose
  → ActionRouter / TunerViewModel
  → NativeEngine semantic request
  → Rust tuning safety/target resolution
  → sole ECU transaction/session owner
```

UI cannot construct or submit raw calibration addresses/bytes as a production action.

ECU actions with materially different risk levels may require different guards; one generic action router must not weaken the tuning safety contract.

## Action sources

Every guarded action can identify its origin, for example:

- direct user touch;
- physical key/rotary control;
- approved dashboard widget;
- approved automation/rule;
- internal recovery/maintenance operation.

Source identity supports policy such as allowing a user-held action while prohibiting the same action from unattended automation.

## Automation

Future rules may request approved actions, but arbitrary custom math/script execution must not gain unrestricted ECU or physical-output authority.

An action definition/policy explicitly states allowed automated sources.

Examples:

- a warning rule may request a versioned auxiliary-output action if explicitly allowed and the accessory capability supports it;
- fuel-pump/critical outputs may require guarded user/hardware authority;
- tune writes/Burn are never implicitly enabled simply because the rule engine can name an action.

## Logging actions/events

Semantic actions and any genuine confirmations should be representable as timestamped events in the measurement/logging system.

For a protocol with explicit confirmation:

```text
12.001  UserAction(aux_fan, ON)
12.048  AccessoryState(aux_fan, CONFIRMED_ON)
```

For the legacy button bridge the event may instead be:

```text
12.001  UserAction(buttonbox_aux_1, PRESSED)
12.006  BridgeTransport(buttonbox_mask, WRITE_COMPLETED)
```

with downstream state remaining unknown unless observed independently.

The logging event representation never becomes the action execution authority.

## Extensibility

Transport changes should not force layout/action-definition changes.

If a future accessory transport moves from BLE to another mechanism, semantic action IDs and UI bindings remain stable while only the gateway/transport implementation changes.