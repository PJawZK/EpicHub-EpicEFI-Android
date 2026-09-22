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

# Completed deep-dive: logging / playback / MLG interoperability / MDF transition

Evidence: `LOGGING_INTEROP_EVIDENCE.md`.

Permanent findings:

- live acquisition, decode, timestamping and ChannelStore publication are format-neutral;
- the implementation order is deliberately **live path first → MLG writer → MLG optimization → MDF4 comparison/implementation → large analyzer**;
- MLG is the first writer because it gives fast TunerStudio/MegaLogViewer interoperability, not because it becomes EpicHub's runtime model;
- MDF4 remains the long-term native measurement-format candidate behind EpicHub's own measurement abstraction;
- MSL/CSV/MLG/MDF readers feed common file-backed channel/source semantics rather than Dashboard-specific JSON;
- the current MSL player contributes useful techniques: primitive columnar arrays, background parse, generation cancellation, binary-search playback and decoupled UI cadence;
- the current MSL player's hardcoded channel aliases, full selected-log materialization and JSON callback model do **not** transfer as architecture;
- writer/UI/analyzer paths remain independent and communicate through bounded sample/query contracts;
- mobile writer decisions prioritize file size first, then sustained read/write efficiency, CPU/thermal cost and recoverability;
- large-log analysis is lazy/range-based with rebuildable indexes/caches and viewport-sized spike-preserving min/max results;
- recovery/journal metadata never fabricates missing measurement data.

Remaining work:

- genuine executable MLG format/golden-vector corpus and compatibility qualification;
- MDF version/compression interoperability benchmarks;
- Android storage-path policy;
- writer power-loss/recovery implementation proof;
- identical-workload file-size/CPU/read/write benchmarking on target devices.

---

# Completed deep-dive: diagnostics / performance / lifecycle tracing

Evidence: `DIAGNOSTICS_EVIDENCE.md`.

Permanent findings:

- diagnostics are **observational**; production domains remain decision/action authority;
- structured events, warning incidents, performance metrics and lifecycle-owner traces are separate data types;
- event/metric histories are bounded;
- hot-path metric recording stays cheap/primitive while percentile aggregation/serialization is out-of-band;
- explicit units/kinds/stable metric IDs replace name-based unit inference;
- monotonic time owns durations/order; wall clock owns human chronology;
- p50/p95/p99/max matter for latency/jank/transport/analyzer qualification;
- warning acknowledgement never alters underlying rule/channel truth;
- correlation/session/generation IDs link Kotlin and Rust evidence without merging subsystem ownership;
- Kotlin owns Android/platform diagnostics, Rust owns engine diagnostics;
- Rust diagnostics cross JNI in bounded snapshots/batches rather than callbacks per event/sample;
- measurement logging and diagnostics remain distinct, with only explicit event integration;
- support export should offer both human-readable and stable machine-readable forms.

Remaining work: final typed event/metric schema, retention/persistence policy and export schema/format.

---

# Completed deep-dive: EpicEFI Tune Viewer parsing / comparison / visualization

Evidence: `TUNE_VIEWER_EVIDENCE.md`.

Reviewed source: `PJawZK/PJawZK-EpicEFI-Tune-Viewer` main `abfa325597d16cb362623c260ec92ed5bf5f37f1`, V1.0 RC1.

Permanent findings:

- exact profile identity gates semantic interpretation; no nearest-version or board-name fallback;
- scalar/bits/array/table/curve/dialog/menu are explicit semantic types, preventing accidental UI fallback to the wrong editor surface;
- the viewer's MSQ/INI parsers are useful capability/fixture evidence but do **not** become parallel EpicHub tuning/profile authority;
- compact gzip definition packs with SHA-256, exact-signature registry identity and count/signature revalidation are a useful model for optional **derived** compiled-profile projections;
- asynchronous definition/project-style loads reject stale completion through generation/active guards;
- full scalar/table/curve tune comparison is permitted only under exact matching firmware/definition identity in the viewer; EpicHub strengthens this to exact authoritative profile fingerprint/compiled identity;
- cross-profile comparison is informational and conservative, never edit/migration/write authority;
- arrays/tables/curves are not cross-profile comparable without an explicit qualified compatibility contract;
- positional table deltas become ambiguous when axes differ; EpicHub should default such tables to non-comparable unless an explicit regridding transform is requested and qualified;
- structural profile-definition diffing is useful diagnostics/migration evidence but not migration authorization;
- the reviewed viewer contains 2D calibration tables and SVG curves, **not** a separate Plotly/3D engine; its semantic/render-validation rules transfer, its React/browser renderer does not;
- comparison JSON is derived evidence only, not project/tune/ECU authority.

Durable decision: `D-018 — Full semantic tune comparison requires exact authoritative profile identity`.

Remaining work:

- exact saved-project/tune comparison identity schema;
- same-profile and cross-profile executable comparison goldens;
- changed-axis table comparison/regridding policy;
- decision whether a persisted compact compiled-profile projection provides worthwhile Android startup/search benefit;
- versioned comparison-report schema if retained as a product feature.

---

# Current inventory tranche

Primary next target: **AE / PID / TCharge / Injection Angle plugin repositories** for future Analyzer/autotune responsibilities.

Review goals:

- extract algorithms, required channels, gating/quality criteria and evidence capture flows independently of their TunerStudio/plugin UI shells;
- identify common analysis primitives that belong in the Rust analyzer rather than independent feature-specific engines;
- separate recommendations/proposals from write authority;
- identify reusable offline-log workflows so the same recorded session can feed multiple positive/negative analyses;
- capture fixture/replay needs for deterministic analyzer qualification;
- avoid copying plugin-specific architecture, UI assumptions or hardcoded tune context into EpicHub.

After this source review, Phase 1 should pivot from broad archaeology to closing the remaining explicit design/fixture/benchmark decisions and deciding whether the review gate is satisfied for Phase 2 scaffolding.