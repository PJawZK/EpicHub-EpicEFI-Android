# EpicHub Safety Boundaries

## Purpose

EpicHub controls or influences systems that can affect a running vehicle. Safety boundaries must remain explicit even as implementation language/UI changes.

## UI boundary

Compose/UI may request semantic operations and display state. It must not hold production authority for:

- raw ECU page/offset/width/encoded bytes;
- protocol frames/CRC;
- direct Burn commands;
- raw ESP32 characteristic/GPIO/CAN implementation details as semantic action identity.

## ECU identity boundary

A USB transport candidate is not ECU/profile authority.

Required progression:

```text
safe transport candidate
→ live firmware signature
→ exact generated-profile signature match
→ protocol preflight
→ complete tune/runtime authority
```

Unknown/mismatched identity fails closed before tune/runtime interpretation requiring the profile.

## Tune mutation boundary

Every production tune mutation requires current session/generation/profile/tune identity and semantic target resolution from the exact accepted profile.

A mutation is successful only after the required ACK, exact edited-range readback and complete expected TuneSnapshot verification.

If failure occurs after possible transmission and final state is not proven, enter uncertainty. Do not retry/replay automatically.

INI condition/write authority is engine-owned. Compose may display whether a target is enabled/visible/writable, but it does not independently override an unsupported or false condition.

## Burn boundary

Burn is always separate and explicit after verified RAM state. It requires firmware flash-state/progression evidence and complete post-Burn tune verification.

No automatic Burn from ordinary edits/rules/scripts.

## Transport ownership boundary

There is one authoritative ECU protocol session owner. Dashboard/logging/tuner must not create independent competing ECU connections/snapshot readers.

Android USB discovery/permission/CDC setup is separate from ECU protocol/session authority. The eventual Kotlin-vs-native sustained bulk-I/O decision must not create two protocol owners.

## Legacy ESP32 bridge boundary

The reviewed original ESP32 firmware is a BLE↔CAN bridge with local ADC/digital inputs. It does not provide a reviewed BLE-controlled relay/GPIO output subsystem.

Legacy bridge safety therefore means:

- do not treat Android GATT write completion as proof that a downstream CAN action was applied;
- do not invent requested-vs-confirmed physical state where the protocol exposes no acknowledgement/feedback;
- preserve explicit connection/offline/unknown state;
- define and test button-mask disconnect/release semantics rather than assuming the legacy firmware safely releases them;
- keep BLE/GATT transport state distinct from downstream ECU/CAN state.

The original task watchdog protects firmware liveness; it does not prove a downstream output-safe state.

## Future ESP32 physical-output boundary

If EpicHub deliberately adds direct ESP32 relay/GPIO output control, that is a new versioned hardware capability requiring its own safety contract.

For direct physical outputs the ESP32 firmware must own:

- physical output mapping;
- boot/default state;
- electrical/hardware interlocks;
- command validation;
- watchdog/disconnect fallback;
- output fail-safe behavior;
- hardware state/fault reporting where required.

Android process failure or BLE loss must not be able to leave a safety-sensitive direct output in an undefined state.

No such implementation is considered inherited or proven merely because the legacy bridge exists.

## BLE operation boundary

Android GATT operations have one explicit connection-wide scheduling owner in EpicHub. Semantic button, variable-request and variable-set producers do not each own independent write pipelines.

Queue acceptance and GATT completion are transport observations, not application-level hardware acknowledgements.

## Automation boundary

Rules/custom math do not gain unrestricted action authority. Each semantic action defines permitted sources/guards. ECU tune writes/Burn and future safety-sensitive accessory outputs require explicit policy appropriate to their risk.

## Logging/analysis boundary

Logging/analysis never mutates ECU/accessory state merely because an expression detects a condition. Any action must traverse the semantic ActionRouter and owning domain safety policy.

## Recovery boundary

Recovery markers/journals report uncertainty and support deliberate resynchronization. They do not prove hardware state by themselves and do not authorize automatic replay.

## Raw data truth

Acquisition/channel truth should preserve raw observed ECU/sensor values unless a documented physical conversion applies. Presentation may visually clamp/saturate a gauge, but that does not alter logged/analyzed source truth.

## Diagnostics boundary

Diagnostics observe/report. Instrumentation must not change production transport/tuning decisions except where a deliberate protective fail-closed policy explicitly consumes a validated health condition.