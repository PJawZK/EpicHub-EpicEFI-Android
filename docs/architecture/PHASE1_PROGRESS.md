# Phase 1 Progress

## Status

Phase 1 is active on `phase1/architecture-inventory`.

Repository-backed architecture/evidence work is progressing domain-by-domain. Production Android/Rust scaffolding has **not** started.

## Completed architecture foundation

- clean-slate workflow/handoff rules;
- durable decision log and permanent naming rules;
- source and responsibility inventories;
- protocol authorities and gap analysis;
- provisional concrete codebase topology;
- responsibility destination mapping;
- dependency/state/data/threading/FFI/action/safety contracts;
- test strategy/review gate.

---

# Completed deep-dive: ECU USB / protocol / generated INI

Permanent responsibilities are now known for:

- Android USB discovery/permission/CDC topology/control;
- one ECU session/generation/retry owner;
- asymmetric request/response framing and typed protocol status;
- synchronization/probe policy;
- `S/Q`, `O`, `R`, `C`, `B` semantics;
- exact live signature + generated-INI authority;
- INI syntax parsing versus supported-subset validation;
- runtime/tune/tuner-surface schema compilation;
- primitive codecs;
- INI condition parsing/evaluation/write authority;
- runtime output polling/decoding;
- verified TuneSnapshot/RAM-write/Burn transaction boundaries.

Remaining work is fixture/benchmark evidence, not responsibility discovery:

- ECU protocol golden vectors;
- broader genuine INI corpus;
- Kotlin-batched vs Android-opened-native-FD sustained USB benchmark.

---

# Completed deep-dive: ESP32 legacy bridge / Android BLE

New evidence document: `ESP32_ACCESSORY_EVIDENCE.md`.

Permanent findings:

- original ESP32 is a BLE↔CAN bridge with local ADC/digital inputs, not a direct relay/GPIO output controller;
- service/characteristic and CAN wire behavior is inventoried;
- historical `GPS_DATA` characteristic is generic variable-set transport;
- legacy variable requests/responses have implicit batch/timeout behavior that must not become hidden app authority;
- original disconnect does not provide a proven zero/release button-mask behavior;
- ESP task watchdog is liveness only, not output-state failsafe;
- EpicDash-JZ reconnect/stale-GATT/scan/MTU lifecycle lessons are preserved;
- three separate characteristic write queues are rejected as final ownership; target is one connection-wide GATT operation scheduler;
- BLE write completion is transport evidence, not downstream CAN/action confirmation;
- future direct relay/GPIO control is a separate versioned capability requiring ACK/state/interlock/failsafe design.

Remaining work is product/fixture/physical evidence:

- choose which legacy bridge features remain clean-app requirements;
- bench/source verify button disconnect/release consumer semantics;
- BLE/CAN golden vectors;
- direct-output extension only if later required.

---

# Completed deep-dive: tuning project persistence / recovery

New evidence document: `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md`.

Permanent state categories are now explicit:

1. project authority = exact generated profile source + last complete verified TuneSnapshot;
2. derived acceleration = exact-identity-bound disposable compiled/profile/projection caches;
3. portable user backup = validated archive representation of project authority only;
4. operation uncertainty journal = tiny durable marker before possible `C`/`B` transmission;
5. UI draft = unverified local edit state, never automatically ECU/project authority.

Resolved rules:

- no semantic value sidecar/compiled cache becomes alternate authority;
- project updates are transactional generations with rollback on incomplete proof;
- saved/offline project does not grant hardware write authority;
- backup import validates exact profile/snapshot identity before local commit;
- Kotlin owns Android document/URI interaction, not tuning schema/integrity decisions;
- operation journal is fsynced and reread before possible hardware mutation;
- present/corrupt journal blocks further mutation and requires resynchronization;
- journal metadata never proves ECU state and never permits blind replay;
- T3/T6 pilot restore/power-cycle workflows are qualification evidence, not normal tuning UX.

Remaining implementation/product work:

- final compact project/backup format;
- generic batch-capable operation-journal schema;
- offline draft/reconnect conflict/reconciliation UX;
- persistence fault-injection corpus.

---

# Current inventory tranche

Primary next target: **Dashboard/editor/Tuner UI interaction, responsive behavior and performance responsibilities**.

Review goals:

- distinguish approved EpicHub visual/layout authority from old implementation mechanisms;
- inventory Dashboard runtime vs editor state/gesture ownership;
- inventory Tuner hierarchy, parameter/curve/table semantics, multi-cell/table editing and 3D visualization behavior;
- inventory orientation/phone/tablet responsive rules and lifecycle state preservation;
- review Epic-DashTune-UXUI only for features/interactions/responsive/performance techniques, not visual design transfer;
- identify which interaction state belongs Kotlin/Compose and which data/semantic state remains Rust-owned;
- prevent WebView-era interaction workarounds or giant state payloads from becoming new architecture.

## Subsequent tranches

- logging/playback/MLG/interoperability and MDF4 transition;
- diagnostics/performance architecture;
- Tune Viewer visualization/parsing lessons;
- tuning plugin algorithms for future analyzer/autotune responsibilities.
