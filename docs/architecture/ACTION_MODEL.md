# EpicHub Semantic Action Model

## Purpose

EpicHub must support user/app actions that target different domains without exposing transport or hardware/memory implementation details to dashboard layouts or Compose controls.

Primary target domains:

- `Local` — actions implemented inside the Android application;
- `Accessory` — semantic commands sent to the ESP32/accessory controller;
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

A dashboard button should bind to a semantic action such as:

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

## Accessory actions

Path:

```text
Compose/input
  → ActionRouter
  → Esp32AccessoryGateway
  → Esp32MessageCodec
  → BleWriteScheduler / Esp32GattClient
  → ESP32
```

Rust is not in the accessory command path merely to centralize architecture.

The ESP32 remains the final authority for:

- physical GPIO/relay mapping;
- electrical interlocks;
- disconnect/watchdog fallback;
- any hardware-level safety condition that must survive Android/app failure.

## Requested vs confirmed accessory state

EpicHub should distinguish at least:

- unavailable/offline;
- known OFF;
- ON requested/pending;
- confirmed ON where feedback exists;
- OFF requested/pending;
- confirmed OFF;
- command failed/no response;
- hardware fault where protocol supports it.

Sending a command is not sufficient evidence that the physical state changed.

Where the current ESP32 protocol lacks acknowledgement/state feedback, Phase 1 should record that limitation and decide whether a versioned protocol extension is required.

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

Source identity supports policy such as allowing a user-held accessory action while prohibiting the same action from unattended automation.

## Automation

Future rules may request approved actions, but arbitrary custom math/script execution must not gain unrestricted ECU or hardware output authority.

An action definition/policy explicitly states allowed automated sources.

Examples:

- warning rule may request an auxiliary fan action if explicitly allowed;
- fuel-pump/critical output may require a guarded user action;
- tune writes/Burn are never implicitly enabled simply because the rule engine can name an action.

## Logging actions/events

Semantic actions and confirmations should be representable as timestamped events in the measurement/logging system.

For accessory state, useful channels/events may include separate command and confirmed-state records so latency/failure can be analyzed later.

Example:

```text
12.001  UserAction(aux_fan, ON)
12.048  AccessoryState(aux_fan, CONFIRMED_ON)
```

The logging event representation must not become the action execution authority.

## Extensibility

Transport changes should not force layout/action-definition changes.

If a future accessory transport moves from BLE to another mechanism, the semantic action IDs and UI bindings should remain stable while only the gateway/transport implementation changes.
