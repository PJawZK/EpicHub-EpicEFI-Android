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
- with USB MSD enabled, CDC control/data move to interfaces 1/2; without MSD they are interfaces 0/1.

Authority rule:

`0483:5740` plus expected CDC shape identifies a shared STM32 EpicEFI/rusEFI transport candidate only. It is not a Mega144H7 identity and grants no runtime decoding/tune-write/Burn authority.

Application evidence/reference:

- EpicDash-JZ `UsbDeviceSelectionPolicy`;
- `docs/audits/ECU_RECOGNITION_FIRMWARE_EVIDENCE.md`;
- USB selection/permission tests.

## Android CDC topology and port setup

Primary authority for device/interface facts is the USB descriptor exposed by Android and the matching firmware descriptor source. EpicDash-JZ `UsbEcuManager` is qualified behavioural evidence for the Android host-side setup sequence.

Current evidence:

- CDC control and data interface numbers cannot be hardcoded because optional mass storage changes them;
- the control/data relationship can be resolved from CDC Union or Call Management descriptors, then validated against actual bulk IN/OUT endpoints;
- the Android host path may claim control and data interfaces separately;
- `SET_LINE_CODING` and `SET_CONTROL_LINE_STATE` are Android/CDC setup operations, not ECU binary-protocol operations;
- the proven compatibility probe has exercised DTR and DTR+RTS variants.

Authority rule:

Android USB topology discovery, interface claiming and CDC control requests stay outside the ECU protocol codec. Future native runtime-I/O ownership may move below Kotlin only after the Android-opened-FD approach is physically proven; the protocol layer must not depend on one hardcoded interface number or endpoint address.

## ECU live firmware identity

Primary authority:

- `firmware/console/binary/signature.cpp`;
- generated firmware signature headers/build output;
- `firmware/console/binary/tunerstudio.cpp` query handling.

Current evidence:

`signature.cpp` includes the generated board-specific signature header and `getTsSignature()` returns `TS_SIGNATURE`.

Authority rule:

The complete live TunerStudio signature is the runtime firmware identity/compatibility token. Parsed board/build components may be displayed diagnostically but do not independently authorize output decoding or tuning.

## Generated INI / profile authority

Primary authority:

The exact generated TunerStudio INI corresponding to the connected firmware signature.

Authority rule:

- exact complete live signature equality with imported generated-INI signature is required before runtime/tune interpretation;
- generated INI owns runtime output layout, tune schema, page geometry, storage metadata, bounds, scaling and Burn definitions used by EpicHub;
- board names must not be used to hardcode runtime/tune geometry;
- compiled representations are derived acceleration only and remain fingerprint-bound to the exact source INI.

Current EpicDash-JZ parser evidence establishes a deliberately supported subset rather than a generic arbitrary TunerStudio parser. The current accepted path requires:

- `messageEnvelopeFormat = msEnvelope_1.0`;
- little-endian channel/tune primitive interpretation;
- a non-empty query command;
- an output command beginning with `O`;
- a valid `ochBlockSize` and parsed scalar/bit output channels;
- calibration page identifiers/sizes/read commands/Burn commands taken from the exact INI.

That supported-subset validation is a distinct responsibility from lexical parsing. Future EpicHub must reject unsupported constructs explicitly instead of silently compiling an incomplete profile.

Reference evidence:

- EpicDash-JZ `UsbTunerStudioProfile.kt`;
- `TuningIniDefinition.kt`;
- `IniConditionEvaluator.kt`;
- generated-INI compatibility tests;
- `reference/ecu/mainController-*.ini`.

## TunerStudio request framing

Primary authority:

- `firmware/console/binary/tunerstudio.cpp`, especially `tsProcessOne()`;
- `firmware/console/binary/tunerstudio_io.h` for packet constants and synchronization state.

The CRC request frame is asymmetric with the response frame and must be modeled separately:

```text
request
┌───────────────┬────────────────────────┬──────────────┐
│ BE u16 length │ command + request data │ BE u32 CRC32 │
└───────────────┴────────────────────────┴──────────────┘
```

Where:

- `length` is the byte count of `command + request data`;
- CRC32 covers `command + request data`, not the length field;
- a known command with valid size and CRC establishes the firmware channel's synchronized state.

Plain `S/Q` query commands are also accepted by current firmware for detection/startup.

Future Rust API rule:

Use a request-specific codec operation such as `encode_request(...)`. Do not expose a single symmetric `envelope()` abstraction that obscures the different response body/status semantics.

## TunerStudio response framing/status

Primary authority:

`firmware/console/binary/tunerstudio_io.cpp` / `tunerstudio_io.h`.

CRC response format is:

```text
response
┌──────────────────┬────────┬──────────────┬──────────────┐
│ BE u16 bodyLength│ status │ response data│ BE u32 CRC32 │
└──────────────────┴────────┴──────────────┴──────────────┘
```

Where:

- `bodyLength = 1 + responseDataLength`;
- the first body byte is the response/status code;
- CRC32 covers `status + response data`, not the length field;
- large payloads may be emitted incrementally but retain the same logical framing;
- protocol error codes are meaningful response status values, not payload bytes to be stripped heuristically.

Future Rust API rule:

Use response-specific operations such as `decode_response(...) -> { status, payload }`. Status interpretation belongs in typed protocol response handling.

## Protocol synchronization and probe policy

Firmware authority:

`TsChannelBase::in_sync` and `tsProcessOne()` in `tunerstudio_io.h` / `tunerstudio.cpp`.

Application behavioural evidence:

EpicDash-JZ deliberately runs a compatibility sequence that can try plain/framed `S/Q`, establishes CRC synchronization with a framed signature command, and verifies bounded read-only `O` requests before normal streaming.

Authority rule:

Synchronization/probe strategy is session policy above the pure frame codec. It must remain read-only until exact profile authority exists. A profileless recognized ECU stops after identity and closes; it does not run output preflight, TuneSnapshot reads or mutation operations.

## Supported command semantics

Primary authority:

- `firmware/console/binary/tunerstudio.cpp`;
- `firmware/console/binary/tunerstudio_commands.cpp`;
- exact generated INI for command/page geometry.

Current audited path:

- `S/Q` — query live signature;
- `O` — request live output bytes by offset/count;
- `R` — read calibration bytes for page/offset/count;
- `C` — write a calibration chunk for page/offset/count + bytes;
- `B` — request Burn for a page.

`O` validates requested range against firmware `TS_TOTAL_OUTPUT_SIZE`, updates current TunerStudio state, gathers live-data fragments and returns the requested bytes. `R` and `C` validate page/range against firmware working-page boundaries. `C` can be rejected by firmware lock/preset-refresh policy and applies board configuration overrides. `B` validates the page/configuration and returns a distinct Burn acknowledgement.

Authority rule:

EpicHub implements typed operations corresponding to these semantics. It must not infer page identifiers, output sizes or Burn page mapping from board names.

## INI semantic structure and condition authority

The exact generated INI remains source authority, while EpicHub's compiler/evaluator owns the supported interpretation.

Distinct responsibilities proven by the current implementation are:

1. lexical/section parsing and escaped-string/list handling;
2. supported-subset validation;
3. runtime-channel schema compilation;
4. calibration/tune storage schema compilation;
5. table/curve/menu/dialog semantic structure compilation;
6. primitive engineering/raw value codecs;
7. condition expression parsing/evaluation against a verified TuneSnapshot;
8. fail-closed condition-driven UI/write policy.

`TuningIniDefinition` demonstrates that structural tune surfaces can be compiled from INI without inventing values. `IniConditionEvaluator` demonstrates that conditions are not presentation decoration only: they also constrain whether a semantic target is currently writable. Unsupported/unresolved conditions fail closed for affected writes.

Authority rule:

Compose may render condition state, but it must not independently reinterpret conditions or authorize writes. Condition/write authority stays below the UI boundary in the profile/tuning engine.

## Tune mutation safety contract

Behaviour authority during Phase 1:

Physically accepted EpicDash-JZ tuning workflow plus exact current INI/firmware protocol reality.

Required preserved sequence for normal RAM mutation:

1. current live session/generation/signature/profile/tune identity;
2. semantic target resolution from exact current INI;
3. complete baseline verification;
4. bounded `C` write;
5. exact acknowledgement;
6. exact `R` range read-back;
7. complete expected TuneSnapshot reread/comparison;
8. verified RAM state or fail-closed uncertainty.

Normal tune Read/Write/Burn owns one exclusive ECU operation slot and temporarily pauses ordinary output polling. The user-visible stale-data policy must understand that deliberate exclusivity rather than presenting old values as live or raising a false transport failure.

Burn remains a separate explicit transaction with flash-state evidence and complete post-Burn tune verification.

Presentation must not expose arbitrary raw page/offset/bytes as a production mutation API.

## ESP32 BLE accessory protocol

Current source authorities/evidence:

- `AKCore/EpicEFIVirtualDash` Android and ESP32 firmware for original wire-format intent;
- EpicDash-JZ evolved BLE implementation/physical behaviour for reconnect/queue/lifecycle lessons;
- any future EpicHub-specific ESP32 firmware contract once deliberately versioned.

Current observed wire details:

- service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`;
- button characteristic `beb5483e-36e1-4688-b7f5-ea07361b26a8`;
- variable-data characteristic `...a9`;
- variable-request characteristic `...aa`;
- GPS/variable-set characteristic `...ab`;
- button mask is 16-bit little-endian in the original protocol;
- requested variable hashes and returned hash/value entries are big-endian;
- batched variable operations are supported by the observed implementation.

Authority caution:

The upstream repository currently declares no GitHub license. Protocol/behaviour may be reimplemented cleanly; wholesale source transplantation is not assumed permissible.

Future EpicHub rule:

UI/layouts address semantic accessory actions, not BLE characteristic details or raw GPIOs. The accessory protocol layer resolves those semantics into the required wire format. ESP32 firmware owns physical output mapping/failsafe policy.

## ESP32 CAN bridge behaviour

Current observed original protocol:

- button TX CAN ID `0x711`, hardware button-box category 27;
- variable request base `0x700 + ECU_ID`;
- variable response base `0x720 + ECU_ID`;
- GPS/variable-set base `0x780 + ECU_ID`.

These values remain protocol evidence, not yet a declaration that every mechanism must remain unchanged in final EpicHub/ESP32 firmware. Phase 1 must distinguish required compatibility from opportunities for a deliberately versioned improved accessory protocol.

## Location/GPS mapping

Authority:

- Android location APIs for acquisition semantics;
- physically/characterization-tested EpicDash-JZ mapping behaviour where compatible behaviour is required;
- ESP32/CAN firmware protocol for forwarded packed/raw representation.

Acquisition, channel mapping and accessory forwarding are separate responsibilities even when they originate from one Android location sample.

## MDF4

Primary authority:

ASAM MDF specification plus independent interoperability validation.

EpicDash/EpicDash-JZ are not MDF4 authority.

Implementation rule:

MDF4 remains behind EpicHub's own measurement-store/file-format abstraction. Rust crate behaviour must be validated against independent tooling/corpus before becoming accepted file-format authority.

## MSL / MLG / CSV interoperability

Authority depends on the exact external format/tool ecosystem. These formats are interoperability concerns and must not become the internal channel/data authority for EpicHub.

Specific reader/writer contracts will be added when implementation work begins.