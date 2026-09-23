# EpicHub Source Inventory

## Purpose

This document records what each reference source is useful/authoritative for. Historical source-tree structure is never architectural authority for EpicHub.

Evidence is normalized into EpicHub responsibilities and then reimplemented under D-022. Historical language/framework/serialization choices are evidence only and do not override D-023.

## Authority vocabulary

- **Protocol authority** — source defines actual wire/file/firmware behaviour.
- **Qualified behaviour evidence** — implementation has meaningful software/physical qualification and is strong behavioural evidence.
- **Reference implementation** — useful example; implementation structure is not retained.
- **Interaction/design reference** — UX/interaction/performance evidence only.
- **Future-feature reference** — algorithms/workflows that may later enter EpicHub.

## Provenance / licence handling

- private/user-controlled `PJawZK` repositories are project evidence; actual copied/dependency code still requires licence/provenance review at adoption time;
- `AKCore/EpicEFIVirtualDash` exposed no declared GitHub repository licence in the reviewed metadata, so treat it as behaviour/protocol evidence unless permission/licensing is established separately;
- directly reviewed firmware contains upstream licence headers including GPL and Apache-licensed sources; use it to verify protocol truth, not as clean-slate source to transplant;
- external specifications/libraries retain their own licence/usage terms;
- fixture provenance must identify source and redistribution constraints before committing private/third-party artifacts.

## Runtime-language interpretation

Reference repositories may contain Java, JavaScript, TypeScript, React, WebView, JSON or other implementation technologies.

Those technologies describe the reference system only. EpicHub production runtime remains Kotlin/Jetpack Compose + Rust under D-003/D-023. A historical JSON/browser implementation never selects JSON/browser architecture for EpicHub.

---

# `PJawZK/EpicHub-EpicEFI-Android`

Role: **authoritative EpicHub implementation repository**.

Current state:

- Phase 1 accepted/closed;
- first Phase-2 Kotlin/Rust foundation merged and physically qualified on SM-A137F + SM-T500;
- active design work: `phase2/ui-diagnostics-foundation` / draft PR #3;
- current gate: UI shell + typed diagnostic/bug-capture foundation before real ECU/USB integration;
- no real ECU transport/tune mutation/logger-format/BLE migration/full analyzer implementation yet.

Authority:

- latest accepted EpicHub architecture/decisions;
- production implementation;
- current handoff/gap/implementation contracts.

When this repository deliberately changes a historical behaviour/structure, its accepted contract wins unless new protocol/evidence disproves it.

---

# `PJawZK/EpicDash-JZ`

Role: **primary qualified behavioural/reference source** for the evolved Android application.

Phase-1 examined baseline: `45de2752811edbd66b01441c0cff8311d3e62647`.

High-value evidence:

- Android lifecycle/permissions/USB/BLE/location lessons;
- STM32 USB candidate selection and descriptor handling;
- live TunerStudio signature recognition;
- exact generated-INI/profile authority;
- framed TunerStudio transport behaviour;
- runtime polling and source/session/generation/freshness semantics;
- complete TuneSnapshot read/persistence model;
- semantic scalar/array/table/curve-cell tuning;
- ACK/readback/full-snapshot verification;
- separate Burn + uncertainty/recovery behavior;
- reconnect/lifecycle/diagnostic lessons;
- MSL playback and dashboard/tuner interactions;
- regression/fault-injection/bench/physical evidence.

Do **not** preserve wholesale:

- giant multi-domain Activities/controllers;
- `UsbEcuManager.kt` as one future owner;
- Dashboard-owned global runtime state;
- WebView/JSON/JavaScript/DOM runtime path;
- permanent development-stage naming;
- mixed-purpose tuning/Burn classes;
- accidental/buggy/dead behavior uncovered by D-022 review.

Useful evidence locations include project/architecture/safety audits, regression tests, USB descriptors and generated ECU INIs.

Classification: **qualified behaviour evidence + reference implementation**.

---

# `AKCore/EpicEFIVirtualDash`

Role: **original EpicDash function/protocol intent and ESP32 bridge reference**.

Reviewed default branch: `master`.

Important wire/function evidence:

- BLE service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`;
- button characteristic `...a8`: Android→ESP32 16-bit little-endian mask;
- variable data `...a9`: ESP32→Android batched `[hash int32 + float32]`, big-endian;
- variable request `...aa`: batched big-endian hashes;
- historical `...ab` characteristic is generic variable-set despite GPS-oriented naming;
- CAN button frame `0x711` / category 27;
- variable request/response bases `0x700` / `0x720` + ECU ID;
- variable-set base `0x780` + ECU ID.

The original Android and ESP32 implementations deliberately concentrated many responsibilities. They are protocol/behaviour evidence, not target structure.

Classification: **protocol/function-intent evidence + reference implementation**.

---

# `PJawZK/epicefi_fw_jzfork`

Role: **firmware/protocol reality**.

Phase-1 direct verification head: `6a300295650ed131e57d8881afb2f00fd5291d47`.

Authority areas:

- STM32 serial-over-USB descriptors/CDC topology;
- TunerStudio signature/query behavior;
- command vocabulary/framing/status;
- runtime output definitions;
- generated calibration geometry/INI data;
- RAM write/Burn semantics;
- board/build identity;
- applicable CAN variable/button support.

Directly verified:

- VID/PID `0483:5740` + CDC topology;
- generated `TS_SIGNATURE` via `getTsSignature()`;
- plain + CRC-framed query behavior;
- request CRC over body excluding length;
- response `length(status+payload) + status + payload + CRC(status+payload)`;
- bounded read/chunk-write/Burn dispatch + distinct Burn acknowledgement.

Authority conclusions:

- VID/PID is transport-candidate evidence, not board/profile identity;
- complete live TunerStudio signature is firmware identity evidence;
- exact matching generated profile controls runtime/tune geometry;
- board name never replaces exact generated-profile authority.

Classification: **protocol authority**.

---

# `PJawZK/Epic-DashTune-UXUI`

Role: **interaction/responsive/performance reference**.

Use for:

- interaction patterns;
- responsive layout behavior;
- editor/graph/table ideas;
- measurable rendering/performance techniques.

Do not use as:

- visual authority when it conflicts with accepted EpicHub references;
- ECU/profile/protocol authority;
- a source-tree/framework template.

Classification: **interaction/design reference**.

---

# `PJawZK/PJawZK-EpicEFI-Tune-Viewer`

Role: **qualified read-only reference for tune/profile interpretation/comparison/definition evolution/derived definition packaging**.

Reviewed `main`: `abfa325597d16cb362623c260ec92ed5bf5f37f1` (`1.0.0-rc.1`).

Useful evidence:

- exact-signature definition gating;
- explicit scalar/bits/array/table/curve/dialog/menu kinds;
- conservative cross-firmware comparison;
- structural definition diff;
- exact-signature compact definition registry with integrity checks;
- async stale-result rejection;
- read-only comparison/reporting boundaries;
- shape validation/presentation-only axis ordering.

Limits:

- TypeScript MSQ/INI parsers are not EpicHub profile/tune authority;
- limited viewer condition evaluator is not write authority;
- React/browser/SVG structure does not migrate;
- JSON comparison/export is historical viewer behavior, not EpicHub target serialization;
- browser caches are convenience state only.

Classification: **qualified read-only behaviour evidence + reference implementation**.

---

# Tuning plugin repositories

Reviewed:

- AE: `PJawZK/AE-Tuner-EPICEFI-` @ `9ac4f5be9508972bb6c2be6396dc9527ca16842b`;
- PID: `PJawZK/epicefi-pid-autotune-plugin_Private` @ `a224e75aeb10f36939b34f33db244daab56cc7bf`;
- TCharge: `PJawZK/epicefi-tcharge-autotune-plugin` @ `79777ff4318027f2a874a365cfc78a3ee9bf3dc5`;
- Injection Angle: `PJawZK/Injection-Angle-TS-Plugin_Private` @ `bedee364528bfa5fe87afe4584d0ddaf9c4cc1f3`.

Role: **future-feature algorithm/evidence/workflow references**.

Transferred lessons include evidence provenance, rejection accounting, direct-vs-derived evidence, independent visits/blocked validation, segmentation/comparability, identifiability, quantization-aware bounded experiments, immutable reusable analysis sessions and one existing semantic tuning authority for candidate Apply/Restore.

They are not ported as independent plugin mini-apps/frameworks.

Classification: **future-feature evidence + repository-specific qualified algorithm/workflow reference**.

---

# `PJawZK/Diagnostics-Core`

Role: **cross-project diagnostic/channel/warning-definition reference**.

Reviewed `main`: `8b497461ebcdef70e9cc0a406e7b987988df7aa3`.

Useful lessons:

- stable semantic channel IDs;
- source/consumer alias adapters;
- explicit type/unit/freshness/missing validity;
- declarative warning rules;
- versioned schemas;
- executable diagnostic test vectors;
- consumer/report provenance.

Limits:

- not a second EpicHub ChannelStore;
- generic ranges/stale timings are not automatically authoritative;
- warning definitions remain observational;
- exact generated profile owns live ECU channel geometry/scaling;
- its JSON definition files are **reference-repository artifacts**, not a decision to use JSON in EpicHub runtime or diagnostic capture;
- KiCad/container tooling is outside EpicHub Android runtime architecture.

Classification: **reference implementation + definition/test-vector evidence**.

---

# External specifications/libraries

External authority is required where historical EpicDash code cannot define the contract, including:

- ASAM MDF specification and independent MDF tooling;
- current Android/NDK/JNI/USB/BLE/Compose contracts;
- Rust crate/library documentation;
- CBOR specification/selected codec behavior for diagnostic export;
- third-party dependency licences.

Selecting an external library does not make its object model or serialization style an EpicHub domain architecture. It remains behind the relevant explicit boundary.