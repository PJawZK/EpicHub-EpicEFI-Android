# EpicHub Safety Boundaries

## Purpose

EpicHub controls or influences systems that can affect a running vehicle. Safety boundaries must remain explicit even as implementation language/UI changes.

## UI boundary

Compose/UI may request semantic operations and display state. It must not hold production authority for:

- raw ECU page/offset/width/encoded bytes;
- protocol frames/CRC;
- direct Burn commands;
- raw ESP32 GPIO numbers/electrical output mapping.

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

## Burn boundary

Burn is always separate and explicit after verified RAM state. It requires firmware flash-state/progression evidence and complete post-Burn tune verification.

No automatic Burn from ordinary edits/rules/scripts.

## Transport ownership boundary

There is one authoritative ECU protocol session owner. Dashboard/logging/tuner must not create independent competing ECU connections/snapshot readers.

## ESP32 hardware boundary

EpicHub sends semantic accessory requests. ESP32 firmware owns:

- physical output mapping;
- electrical/hardware interlocks;
- watchdog/disconnect fallback;
- output fail-safe behavior.

Android process failure or BLE loss must not be able to leave safety-sensitive outputs in an undefined state.

## Automation boundary

Rules/custom math do not gain unrestricted action authority. Each semantic action defines permitted sources/guards. ECU tune writes/Burn and safety-sensitive accessory actions require explicit policy appropriate to their risk.

## Logging/analysis boundary

Logging/analysis never mutates ECU/accessory state merely because an expression detects a condition. Any action must traverse the semantic ActionRouter and owning domain safety policy.

## Recovery boundary

Recovery markers/journals report uncertainty and support deliberate resynchronization. They do not prove hardware state by themselves and do not authorize automatic replay.

## Raw data truth

Acquisition/channel truth should preserve raw observed ECU/sensor values unless a documented physical conversion applies. Presentation may visually clamp/saturate a gauge, but that does not alter logged/analyzed source truth.

## Diagnostics boundary

Diagnostics observe/report. Instrumentation must not change production transport/tuning decisions except where a deliberate protective fail-closed policy explicitly consumes a validated health condition.
