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
- generated INI owns runtime output layout, tune schema, page geometry, storage metadata, bounds, scaling and burn definitions used by EpicHub;
- board names must not be used to hardcode runtime/tune geometry;
- compiled representations are derived acceleration only and remain fingerprint-bound to the exact source INI.

Reference evidence:

- EpicDash-JZ generated-INI parser/compiler/tests;
- `reference/ecu/mainController-*.ini`;
- cross-board compatibility audit/tests.

## TunerStudio binary protocol

Primary authority:

EpicEFI/rusEFI firmware implementation in `firmware/console/binary/`, especially `tunerstudio.cpp` and related channel/framing code.

Current observed vocabulary includes query/signature, output-channel request, calibration reads, writes and Burn operations (`S/Q`, `O`, `R`, `C`, `B` in current audited paths).

Authority rule:

EpicHub implements the firmware protocol semantics; historical Android framing code is qualified behavioural evidence, not an independent protocol specification.

Runtime output size and tune-page geometry come from the exact generated profile, not constants copied from a particular ECU build.

## Tune mutation safety contract

Behaviour authority during Phase 1:

Physically accepted EpicDash-JZ tuning workflow plus exact current INI/firmware protocol reality.

Required preserved sequence for normal RAM mutation:

1. current live session/generation/signature/profile/tune identity;
2. semantic target resolution from exact current INI;
3. complete baseline verification;
4. bounded write;
5. exact acknowledgement;
6. exact range read-back;
7. complete expected TuneSnapshot reread/comparison;
8. verified RAM state or fail-closed uncertainty.

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
