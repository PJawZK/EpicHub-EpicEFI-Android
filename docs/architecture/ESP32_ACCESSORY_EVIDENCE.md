# ESP32 Accessory / BLE Bridge Evidence

## Status

Phase 1 source archaeology against:

- `AKCore/EpicEFIVirtualDash` original ESP32-S3 firmware and Android BLE manager;
- `PJawZK/EpicDash-JZ` evolved Android `BleManager.kt` at source baseline `45de2752811edbd66b01441c0cff8311d3e62647`.

This document records behaviour/protocol evidence. The upstream `AKCore/EpicEFIVirtualDash` repository currently has no declared GitHub repository license, so its source is not assumed to be transplantable. EpicHub may reimplement observed protocol/behaviour cleanly where compatibility is required.

## Core finding: bridge, not output controller

The original ESP32 firmware is primarily a BLE↔CAN bridge with a small set of local input sources.

Observed local hardware use in `Firmware/src/main.cpp`:

- GPIO 9 controls CAN transceiver high-speed mode;
- GPIO 5 is one hardware ADC input;
- GPIO 1 is one digital/touch input;
- a WS2812 pin is defined but not used as an accessory output authority in the reviewed path;
- there is no reviewed BLE-controlled relay/GPIO output mapping comparable to the future semantic accessory-output concept.

Therefore Phase 1 must distinguish:

1. **legacy bridge compatibility** — button-mask forwarding, variable request/response, generic variable-set forwarding and local input publication;
2. **future physical accessory outputs** — a new product capability requiring an explicit versioned protocol/state/failsafe design if added.

The original firmware cannot be cited as an existing relay-output failsafe implementation because it does not provide that function in the reviewed path.

## BLE identity and service

Observed server identity:

- advertised device name: `ESP32 Dashboard`;
- service UUID: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`;
- requested BLE MTU: 517 bytes.

Characteristics:

| UUID suffix | Original name | Direction | Actual reviewed role |
|---|---|---|---|
| `...a8` | Button | Android → ESP32 | button-mask/legacy single-button forwarding to CAN |
| `...a9` | VarData | ESP32 → Android | batched hash+float variable notification |
| `...aa` | VarRequest | Android → ESP32 | batched variable-hash requests forwarded sequentially to CAN |
| `...ab` | GPS data | Android → ESP32 | **generic hash+4-byte value variable-set forwarding to CAN**, not GPS-only |

### Naming consequence

EpicHub should not call `...ab` the GPS protocol internally. Its wire role is generic variable-set forwarding. GPS and phone-side virtual ADC values are producers of that protocol.

Suggested permanent semantic split:

- `Esp32VariableSetCodec` — `[hash(4) + raw value(4)]` entry codec;
- `GpsVariableEncoder` — converts Android location into the specific GPS hashes/packed values;
- optional virtual-input encoder/source — produces ADC-style values where retained.

## Button path

BLE input accepted by original firmware:

- preferred/current Android form: 2-byte little-endian 16-bit button mask;
- firmware also accepts a one-byte button ID as a legacy convenience and converts it to one mask bit.

CAN output:

- CAN ID `0x711`;
- 5-byte payload:
  - `0x5A` header;
  - `0x00` reserved;
  - category `27` (`TS_HW_BUTTONBOX1_CATEGORY`);
  - button-mask high byte;
  - button-mask low byte.

The ESP32 only forwards a button frame when the received mask differs from `lastButtonMask`.

### Disconnect limitation

The reviewed `onDisconnect` callback only marks BLE disconnected. It does not:

- force `lastButtonMask` to zero;
- send a zero/release CAN frame;
- provide a remote command acknowledgement/state query.

Therefore a future compatibility bridge must define deliberate disconnect/release semantics instead of assuming the legacy implementation already provides them. Whether a zero frame is correct depends on the ECU/button-box consumer semantics and must be verified before changing compatibility behaviour.

## Variable request/response bridge

### Android → ESP32 request

Characteristic `...aa` accepts one or more 4-byte big-endian variable hashes.

Original firmware:

- supports up to 16 hashes per batch;
- does not queue a new batch while one is active;
- requests one hash at a time over CAN ID `0x701` (`0x700 + ECU_ID`, current ECU ID 1);
- starts a 100 ms timeout per pending variable.

### ECU → ESP32 response

Expected CAN ID: `0x721` (`0x720 + ECU_ID`).

Expected payload:

```text
hash:  int32 big-endian
value: float32 big-endian
```

Each received response is appended to the BLE batch buffer and advances the pending request index. The firmware does not explicitly correlate the returned hash to the currently requested hash before advancing.

### ESP32 → Android notification

Characteristic `...a9` notifies one batch of 8-byte entries:

```text
[hash BE 4][float BE 4] ...
```

Original firmware rate-limits notifications to at least 10 ms apart.

If a requested variable times out, the firmware advances to the next. When the final variable times out it sends whatever partial batch has been collected.

### Implementation risks not to preserve blindly

The legacy code is useful protocol evidence but contains implementation behaviour that should not automatically become EpicHub contract:

- new batches are dropped while one is pending rather than explicitly rejected/queued with correlation;
- CAN responses advance the request sequence without validating that returned hash matches the expected hash;
- BLE notification has no application-level delivery acknowledgement;
- timeout/partial-batch semantics are implicit rather than versioned/correlated.

EpicHub compatibility mode may reproduce only the wire behaviour required by existing firmware. A future extended protocol should add explicit request identity/status rather than encoding assumptions in the UI.

## Generic variable-set path

Characteristic `...ab` accepts one or more 8-byte entries:

```text
[hash int32 BE][value/raw32 BE]
```

The ESP32 copies each 8-byte entry directly to CAN ID `0x781` (`0x780 + ECU_ID`).

Reviewed producers include:

- GPS float variables;
- GPS packed `uint32` variables;
- phone virtual ADC values;
- ESP32 local ADC/digital input helper sends using the same CAN variable-set concept.

This confirms the path is generic variable-set transport, not a GPS-specific domain.

## ESP32 local inputs

### Hardware ADC1

Reviewed firmware:

- GPIO 5;
- 12-bit `analogRead`, scaled to 0–1023;
- nominal sample interval 10 ms (100 Hz);
- configured averaging sample count currently 1;
- sends only if changed by at least 5 units;
- publishes using the A1 variable hash through the CAN variable-set path.

### Digital input

Reviewed firmware:

- GPIO 1 with pull-up, touch/active-low semantics;
- nominal sampling every 20 ms (50 Hz);
- 50 ms debounce;
- packs current bits into a 16-bit value;
- current source only implements bit 0;
- sends value through the variable-set CAN path after stable change.

The source contains a TODO around the digital-input variable hash, so that exact hash must not be elevated to permanent protocol authority without independent ECU/profile evidence.

## ESP32 watchdog and BLE reconnect

Firmware initializes the ESP task watchdog at 5 seconds and feeds it from the main loop. This is a firmware-liveness protection against a wedged loop; it is not an output-state failsafe.

On BLE disconnect:

- `deviceConnected` becomes false;
- advertising restarts after 100 ms;
- pending batch state is reset when a new connection becomes active.

No protocol version/capability/health/command-ack characteristic is present in the reviewed original implementation.

## Evolved EpicDash-JZ Android BLE behaviour

The later `BleManager.kt` substantially improves Android connection lifecycle while retaining the original UUID/wire format.

Observed additions:

- one guarded scan session;
- 10 second scan windows;
- reconnect backoff 5/10/20/30 seconds;
- low-latency scan first, balanced scan after repeated misses;
- 12 second connection timeout;
- stale/superseded `BluetoothGatt` callback rejection;
- explicit stale-GATT close/cleanup;
- required characteristic validation before reporting online;
- notification setup, high connection priority and MTU request;
- manual reconnect and automatic-reconnect pause/resume semantics;
- ability to suspend BLE for an alternate transport without changing the user's reconnect preference;
- queue diagnostics and lifecycle instrumentation;
- malformed batched variable-data byte accounting.

These are strong behavioural inputs for EpicHub's Kotlin BLE platform layer.

## Important queue architecture correction

EpicDash-JZ still maintains three independent write FIFOs and three independent `isWriting...` flags:

- button;
- variable request;
- variable-set/GPS.

That prevents overlap **within** each characteristic queue but does not provide one explicit connection-wide operation owner across all characteristics.

EpicHub should instead own one connection-wide `BleWriteScheduler`/GATT operation queue, with message class/priority metadata if needed. This makes ordering/backpressure explicit and prevents the semantic domains from each believing they independently own the Android GATT write pipeline.

The scheduler remains a transport mechanism; it does not decide accessory semantics.

## Command acknowledgement and state

The legacy protocol has transport-level/GATT completion and variable notifications, but no reviewed application-level acknowledgement saying:

- button command X was applied;
- a physical output reached requested state;
- current hardware output state is Y;
- firmware protocol version/capability set is Z;
- a command failed due to a hardware interlock/fault.

Therefore `requested` versus `confirmed` state is **not available for legacy button forwarding** from this protocol alone.

EpicHub must not manufacture confirmation from `writeCharacteristic()` acceptance or Android's write callback.

## Phase 1 protocol decision boundary

### Preserve for legacy compatibility where required

- service/characteristic UUIDs;
- 16-bit LE BLE button mask and CAN button payload;
- big-endian hash/value entries;
- batched variable request/response concept;
- generic variable-set CAN forwarding;
- Android reconnect/lifecycle lessons from EpicDash-JZ.

### Redesign without breaking the above contract

- one global GATT operation scheduler;
- semantic names independent of old `GPS` characteristic naming;
- explicit correlation/status in app-side request models;
- clean separation of Android GATT lifecycle, wire codec, CAN-bridge semantics and app actions;
- disconnect/release behavior documented/tested rather than accidental.

### Versioned extension only if future hardware/actions require it

- protocol version/capability handshake;
- application-level command acknowledgements;
- explicit state telemetry;
- direct ESP32 relay/GPIO output commands;
- hardware interlock/fault reporting;
- physical output watchdog/disconnect failsafe policy.

Those output features are not inherited from the reviewed original bridge and must be designed/qualified as new functionality.