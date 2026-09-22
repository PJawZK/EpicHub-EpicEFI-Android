# EpicHub Protocol Authorities

## Purpose

EpicHub must not let duplicated historical implementations become competing protocol truth. This document identifies the intended authority for each important protocol/schema area and records current evidence.

This file is refined throughout Phase 1.

## ECU USB transport candidate

Primary authority:

`PJawZK/epicefi_fw_jzfork/firmware/hw_layer/ports/stm32/serial_over_usb/usbcfg.cpp`

Current evidence:

- USB device class/subclass/protocol: `0xEF / 0x02 / 0x01`;
- VID `0x0483`;
- PID `0x5740`;
- CDC communications interface class `0x02`;
- CDC data interface class `0x0A`;
- bulk data endpoints;
- optional USB MSD shifts CDC control/data interface numbers.

Authority rule:

`0483:5740` plus expected CDC shape identifies a shared STM32 EpicEFI/rusEFI transport candidate only. It is not a Mega144H7 identity and grants no runtime decoding/tune-write/Burn authority.

## Android CDC topology and port setup

Primary authority for descriptor facts is the Android-observed USB topology plus matching firmware descriptor source. EpicDash-JZ is qualified behavioural evidence for host setup.

Rules:

- resolve CDC control/data relationships and bulk endpoints from descriptors, not hardcoded interface numbers;
- interface claiming/release and `SET_LINE_CODING`/`SET_CONTROL_LINE_STATE` are Android CDC concerns, not ECU protocol concerns;
- proven DTR/DTR+RTS compatibility attempts may inform the Android setup policy;
- sustained runtime bulk-I/O ownership remains a PoC decision, but upper protocol/session semantics must be unchanged by it.

## ECU live firmware identity

Primary authority:

- `firmware/console/binary/signature.cpp`;
- generated firmware signature headers/build output;
- `firmware/console/binary/tunerstudio.cpp` query handling.

`getTsSignature()` returns generated `TS_SIGNATURE`.

Authority rule:

The complete live TunerStudio signature is firmware identity/compatibility authority. Parsed board/build components are diagnostic only.

## Generated INI / profile authority

Primary authority:

The exact generated TunerStudio INI corresponding to the connected firmware signature.

Rules:

- exact complete live signature equality with imported generated-INI signature is required before runtime/tune interpretation;
- generated INI owns runtime layout, tune schema, page geometry, storage metadata, bounds, scaling and Burn definitions;
- board names do not hardcode runtime/tune geometry;
- compiled representations are derived acceleration only and remain exact-source-fingerprint-bound.

Current EpicDash-JZ evidence establishes a supported subset rather than a universal arbitrary TunerStudio parser. Current accepted profiles require, among other validated facts:

- `messageEnvelopeFormat = msEnvelope_1.0`;
- little-endian primitive interpretation;
- a non-empty query command;
- an output command beginning with `O`;
- valid output block/channel definitions;
- calibration page identifiers/sizes/read/Burn commands taken from the exact INI.

Supported-subset validation is separate from syntax parsing. Unsupported constructs must fail explicitly rather than compile silently incomplete behaviour.

## TunerStudio request framing

Primary authority:

- `firmware/console/binary/tunerstudio.cpp`, especially `tsProcessOne()`;
- `firmware/console/binary/tunerstudio_io.h`.

CRC request frame:

```text
BE u16 length(command+data)
command + request data
BE u32 CRC32(command+data)
```

CRC excludes the length field. Plain `S/Q` query commands are also accepted in the current firmware detection/startup path.

Future Rust rule: request framing has its own `encode_request`-style API.

## TunerStudio response framing/status

Primary authority:

`firmware/console/binary/tunerstudio_io.cpp` / `tunerstudio_io.h`.

CRC response:

```text
BE u16 length(status+data)
status
response data
BE u32 CRC32(status+data)
```

Rules:

- body length includes the one-byte response status;
- CRC excludes the length field;
- status is protocol metadata, not arbitrary payload;
- large payloads may stream incrementally while retaining this logical frame.

Future Rust rule: response framing has its own `decode_response -> status + payload` API. Do not use one symmetric `envelope()` abstraction for request and response.

## Protocol synchronization and probe policy

Firmware authority: `TsChannelBase::in_sync` and `tsProcessOne()`.

Application evidence: EpicDash-JZ plain/framed `S/Q`, framed signature synchronization and bounded read-only `O` probes.

Rules:

- synchronization/probe strategy belongs to session policy above the pure codec;
- profileless recognition may read identity, then closes/stops;
- profileless mode does not run output preflight, TuneSnapshot reads or mutation operations.

## Supported ECU command semantics

Primary authority:

- firmware `tunerstudio.cpp`;
- `tunerstudio_commands.cpp`;
- exact generated INI for page/command geometry.

Audited path:

- `S/Q` — live signature;
- `O` — output bytes by offset/count;
- `R` — calibration page bytes;
- `C` — calibration chunk write;
- `B` — page Burn request.

`O`, `R`, `C` and `B` validate their ranges/pages in firmware. `C` observes firmware lock/preset rules and board overrides. `B` returns a distinct Burn acknowledgement.

## INI semantic structure and condition authority

Distinct responsibilities established by current source:

1. syntax/source parsing;
2. supported-subset validation;
3. runtime-channel schema compilation;
4. tune storage schema compilation;
5. table/curve/menu/dialog semantic schema compilation;
6. primitive engineering/raw representation;
7. condition grammar/evaluation against a verified TuneSnapshot;
8. fail-closed condition-driven UI/write policy.

`TuningIniDefinition` shows structural surfaces can be compiled without inventing tune values. `IniConditionEvaluator` shows conditions can constrain write authority, not just visibility.

Compose may display condition state but does not independently reinterpret it or authorize writes.

## Tune mutation safety contract

Behaviour authority: physically accepted EpicDash-JZ workflow plus current INI/firmware reality.

Required RAM mutation sequence:

1. current session/generation/signature/profile/tune identity;
2. semantic target + active condition authority;
3. complete baseline reread/verification;
4. bounded `C` write;
5. exact acknowledgement;
6. exact `R` range readback;
7. complete expected TuneSnapshot reread/comparison;
8. verified RAM state or fail-closed uncertainty.

Normal Read/Write/Burn owns one exclusive ECU-operation slot and deliberately pauses ordinary output polling. Burn remains separate/explicit with flash-state progression and complete post-Burn tune verification.

## Legacy ESP32 BLE bridge authority

Source evidence:

- `AKCore/EpicEFIVirtualDash/Firmware/src/main.cpp` for original ESP32 wire/CAN behaviour;
- original Android `BleManager.kt` for app-side format intent;
- EpicDash-JZ evolved `BleManager.kt` for qualified/reworked Android lifecycle/reconnect/queue behaviour.

Authority caution:

The upstream repository currently declares no GitHub repository license. Protocol/behaviour may be reimplemented cleanly where compatibility is required; source transplantation is not assumed permissible.

### Device/service identity

Observed legacy server:

- advertised name `ESP32 Dashboard`;
- service `4fafc201-1fb5-459e-8fcc-c5c9c331914b`;
- characteristics `...a8`, `...a9`, `...aa`, `...ab`.

The service/characteristic UUID set is stronger protocol identity than a generic device-name match. Discovery policy may use name as a hint but should validate service identity before declaring compatibility.

### Button characteristic `...a8`

Primary/current Android wire form:

- 16-bit little-endian button mask.

Original firmware also accepts one byte as a legacy single-button ID.

CAN forwarding:

- ID `0x711`;
- payload `[0x5A, 0x00, 27, maskHi, maskLo]`.

The firmware forwards only when the mask changes.

Important limitation: BLE disconnect does not automatically clear `lastButtonMask` or send a zero/release CAN frame. Release/disconnect behaviour is therefore not a proven failsafe contract and must be explicitly compatibility-tested before changing it.

### Variable request characteristic `...aa`

Android→ESP32:

- one or more 4-byte big-endian variable hashes;
- original firmware accepts up to 16 per batch;
- requests are issued sequentially over CAN `0x700 + ECU_ID` (currently `0x701`);
- per-variable timeout is 100 ms.

### Variable data characteristic `...a9`

Expected CAN response ID: `0x720 + ECU_ID` (`0x721`).

Entry format:

```text
hash:  int32 BE
value: float32 BE
```

ESP32 returns one BLE notification containing repeated 8-byte entries. Original firmware can send a partial batch after timeouts and rate-limits BLE notifications to 10 ms minimum spacing.

Legacy implementation details that are **not** elevated to permanent protocol guarantees include dropping a newly received batch while one is active, advancing on any matching CAN response ID without explicitly checking expected hash, and absence of application-level delivery acknowledgement.

### Generic variable-set characteristic `...ab`

Historical names call this `GPS_DATA`, but the reviewed firmware treats it as generic variable-set transport:

```text
[hash int32 BE][4-byte raw value]
```

One or more entries are copied directly to CAN `0x780 + ECU_ID` (`0x781`). Producers include GPS variables and virtual ADC values. Therefore EpicHub internal naming must be **variable-set**, with GPS as one producer, not the protocol's identity.

### Local ESP32 inputs

Reviewed original firmware additionally publishes:

- GPIO 5 ADC, sampled nominally at 100 Hz, scaled 0–1023 and change-thresholded;
- GPIO 1 active-low digital input, sampled nominally at 50 Hz with 50 ms debounce.

Both feed the CAN variable-set concept. The reviewed digital-input hash is marked TODO in source and is not permanent protocol authority without independent ECU/profile evidence.

### Legacy bridge liveness/reconnect

Firmware has a 5-second ESP task watchdog and restarts BLE advertising about 100 ms after disconnect. This protects firmware liveness; it is not a physical-output state failsafe.

The reviewed legacy firmware does **not** expose protocol version, capabilities, application-level command acknowledgement, direct output state or hardware-fault status.

## EpicDash-JZ Android BLE lifecycle evidence

The evolved Android manager preserves UUID/wire compatibility while adding:

- guarded scan sessions;
- 10-second windows;
- 5/10/20/30-second reconnect backoff;
- scan-mode adaptation;
- connection timeout;
- stale/superseded GATT callback rejection;
- deterministic stale-GATT cleanup;
- required-characteristic validation;
- notification setup, high connection priority and MTU request;
- manual/automatic reconnect semantics;
- alternate-transport suspension;
- queue diagnostics and malformed-batch accounting.

These behaviours are reference evidence for Kotlin platform BLE design.

The evolved implementation still owns three independent characteristic write FIFOs. EpicHub does not preserve that ownership model: one connection-wide GATT operation scheduler serializes operations, while semantic message class/priority remains explicit above it.

## Legacy bridge vs future direct accessory outputs

This distinction is now authoritative for Phase 1:

### Legacy bridge compatibility

Potentially preserve/reimplement:

- UUIDs;
- button mask forwarding;
- variable request/response;
- generic variable-set forwarding;
- local input bridge behaviour where product-relevant;
- proven Android reconnect/lifecycle behaviour.

### Future direct relay/GPIO outputs

Not present as a reviewed legacy capability. If EpicHub adds them, use a deliberately versioned extension defining:

- capabilities/version handshake;
- semantic output IDs;
- command acknowledgement;
- confirmed state/fault telemetry;
- boot/default state;
- hardware interlocks;
- watchdog/disconnect fail-safe behaviour.

Android GATT completion alone must never be treated as confirmed physical state.

## Location/GPS mapping

Authority:

- Android location APIs for acquisition semantics;
- tested EpicDash-JZ mapping behaviour where compatibility is required;
- generic ESP32 variable-set/CAN format for forwarded values.

Acquisition, channel mapping, GPS packing and variable-set transport remain separate responsibilities.

## MDF4

Primary authority:

ASAM MDF specification plus independent interoperability validation.

Implementation rule:

MDF4 remains behind EpicHub's measurement-store/file-format abstraction. Rust crate behaviour must be cross-validated against independent tooling/corpus before becoming accepted authority.

## MSL / MLG / CSV interoperability

Authority depends on the exact external format/tool ecosystem. These remain interoperability concerns, not EpicHub's internal channel/data authority.