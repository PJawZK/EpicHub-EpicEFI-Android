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

Permanent responsibilities are known for Android USB/CDC, one ECU session, asymmetric request/response framing, sync/probe policy, `S/Q/O/R/C/B`, exact signature/profile authority, generated-INI parsing/support/schema/conditions, runtime output and verified tune Read/Write/Burn transactions.

Remaining work is fixture/benchmark evidence:

- ECU protocol golden vectors;
- broader genuine INI corpus;
- Kotlin-batched vs Android-opened-native-FD sustained USB benchmark.

---

# Completed deep-dive: ESP32 legacy bridge / Android BLE

Evidence: `ESP32_ACCESSORY_EVIDENCE.md`.

Permanent findings:

- original ESP32 is a BLE↔CAN bridge with local ADC/digital inputs, not a direct relay/GPIO output controller;
- generic variable-set transport replaces historical GPS-specific protocol naming;
- evolved scan/reconnect/stale-GATT/MTU behavior is retained as Android BLE evidence;
- target architecture has one connection-wide GATT operation scheduler;
- GATT completion is transport evidence, not downstream confirmation;
- direct relay/GPIO output is a separate future versioned capability.

Remaining work: product scope, disconnect/release bench evidence, BLE/CAN golden vectors, optional future output extension.

---

# Completed deep-dive: tuning project persistence / recovery

Evidence: `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md`.

Permanent categories:

1. exact profile + complete verified TuneSnapshot project authority;
2. exact-identity-bound disposable derived caches;
3. portable backup as validated representation of project authority;
4. durable pre-transmission operation uncertainty journal;
5. local UI draft as unverified edit state.

No cache/backup/journal/draft becomes alternate ECU/tune authority. Operation journals are durable before possible hardware mutation and never authorize blind replay.

Remaining work: compact project/backup format, batch-capable journal schema, offline reconciliation UX and fault-injection corpus.

---

# Completed deep-dive: Dashboard / Tuner interaction / responsive / performance

Evidence: `UI_INTERACTION_EVIDENCE.md`.

Sources:

- EpicDash-JZ `UI_SPEC.md` and `TUNER_UI_SIDEQUEST.md` for visual/layout and integrated Tuner authority;
- Epic-DashTune-UXUI only for interaction/responsive/performance techniques.

Permanent findings:

- accepted EpicDash-JZ reference family remains visual/layout authority; UXUI styling does not migrate;
- one semantic/component model reflows by available geometry rather than device-specific application variants;
- Rust owns runtime/tuning semantic truth, Compose owns layout/gesture/pointer/camera/presentation state;
- semantic table selection is separate from touch gesture state;
- X/Y reverse is presentation-only, not data reorder;
- pinch/range selection requires explicit gesture arbitration;
- 3D is view-only unless separately accepted for editing;
- modal/fullscreen surfaces own their pointer streams;
- static geometry is cached/constructed once and dynamic layers updated minimally;
- only active UI surfaces consume high-rate display updates where practical;
- undo/redo operates on local semantic edits, not implicit ECU rollback;
- project/live/draft/RAM-dirty/Burn/uncertain states remain explicit in presentation.

Useful performance evidence: UXUI's WebView/SVG r21 benchmark reduced synthetic gauge update cost by ~90% by replacing hundreds of dynamic segment updates with static geometry + minimal pointer transforms. The exact speedup is not a Compose forecast; the invalidation principle is what transfers.

Remaining work: rich table-edit/customization product scope and real Compose interaction/frame benchmarks.

---

# Current inventory tranche

Primary next target: **logging/playback/MLG interoperability and the transition architecture toward MDF4**.

Review goals:

- inventory current EpicDash-JZ MSL playback/CSV/logging state and ownership;
- incorporate existing project decisions that live acquisition path is optimized first, then MLG writer, then MDF4 evaluation;
- separate live acquisition/session/channel storage from concrete file format;
- preserve MLG/MegaLogViewer interoperability without making MLG the internal measurement model;
- define how live and file playback use the same `ChannelSource`/measurement query path;
- inventory file-size/performance/recovery requirements for Android phones/tablets;
- establish the writer/read/index/recovery responsibility split before implementation;
- avoid making UI or a format parser the measurement authority.

## Subsequent tranches

- diagnostics/performance architecture;
- Tune Viewer visualization/parsing lessons;
- tuning plugin algorithms for future analyzer/autotune responsibilities.
