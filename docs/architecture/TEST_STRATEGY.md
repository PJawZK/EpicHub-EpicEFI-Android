# EpicHub Test Strategy

## Purpose

Testability is an architecture requirement. Each major subsystem has an intended qualification path so correctness does not depend on UI/manual operation when a deterministic lower-level test is possible.

For inherited behavior, D-022 / `SOURCE_REIMPLEMENTATION_METHOD.md` adds a source-understanding gate: historical tests become candidate specifications only after the actual requirement/protocol invariant they prove is identified. Known old bugs require explicit negative/regression coverage rather than blind output parity.

D-023 adds another permanent rule: runtime/JNI tests validate typed/packed contracts directly. JSON/browser serialization is never used as a shortcut to test or implement the runtime state model.

---

# Evidence levels

- **Unit evidence** — deterministic pure-code behaviour.
- **Contract/golden evidence** — exact wire/schema/file/ABI vectors and cross-component boundaries.
- **Integration evidence** — real subsystem composition without necessarily using vehicle hardware.
- **Simulator/fault evidence** — timeout/corruption/disconnect/partial-write/uncertainty paths.
- **Bench physical evidence** — real ECU/ESP32/device hardware outside normal vehicle operation.
- **Device physical evidence** — Android phone/tablet/head-unit lifecycle/performance.
- **Vehicle evidence** — behavior requiring real operating vehicle conditions.

Software qualification and physical qualification are always reported separately.

---

# Accepted foundation evidence — already complete

The first Phase-2 Kotlin/Rust foundation is not a future test plan; it is already qualified.

Automated evidence on the accepted foundation covered:

- Rust format;
- Clippy with warnings-as-errors;
- Rust unit tests;
- Kotlin/JVM RuntimeSnapshot ABI tests;
- Android debug assembly;
- Rust build for `armeabi-v7a` + `arm64-v8a`;
- APK inspection proving both `libepichub_engine.so` variants;
- locked Rust dependency build/checks.

Physical execution of the exact qualified foundation APK passed on:

- Samsung SM-A137F;
- Samsung SM-T500.

Observed foundation facts included successful install/start, advancing RuntimeSnapshot sequence, valid/active synthetic channel values and no observed native/lifecycle crash during the reported checks.

Future tests extend that evidence; they do not make the already-qualified foundation hypothetical again.

---

# Kotlin unit tests

Expected targets as their subsystems appear:

- USB candidate policy;
- USB permission request state;
- BLE reconnect policy;
- BLE operation scheduling policy;
- legacy ESP32 message-specific codecs;
- bridge transport/request state transitions;
- future direct-output requested/confirmed/fault transitions only if that capability exists;
- ActionRouter/ActionPolicy decisions;
- warning-observation vs separate automation-action policy;
- GPS quality/mapping;
- UI ViewModel/reducer state where deterministic;
- responsive navigation/layout policy where pure enough;
- settings/layout schema validation;
- diagnostic capture interaction/reducer state;
- Android-side diagnostic event/metric schema adapters.

Android framework APIs should be isolated enough that pure policies/codecs do not require instrumentation tests.

---

# Rust unit/property tests

Expected targets:

- CRC/request framing/response codecs;
- firmware identity parsing;
- INI parser/model/compiler;
- condition parser/evaluator;
- runtime decoder against generated-profile fixtures;
- ChannelStore/revision/validity behavior;
- RuntimeSnapshot encoding/ABI versioning;
- expression parser/compiler/evaluator;
- tune value codecs;
- semantic tune-draft/edit operations;
- target resolution;
- write-plan invariants/non-overlap/bounds;
- expected TuneSnapshot construction;
- uncertainty/recovery state machines;
- Burn plan/state transitions;
- measurement reader/writer contracts;
- MLG codec/finalization behavior when implemented;
- MDF blocks/metadata/index/recovery when implemented;
- analysis/statistics/search/downsample algorithms;
- analysis-session provenance/rejection/comparability/identifiability;
- bounded diagnostics ring behavior;
- diagnostic metric aggregation;
- canonical diagnostic capture schema/CBOR codec where Rust owns the codec.

Property/fuzz testing should be considered for parsers, codecs, corrupted files, protocol inputs and versioned binary diagnostic/archive decoders.

---

# Runtime/JNI boundary tests

The accepted RuntimeSnapshot ABI v1 tests remain permanent regression coverage.

Test:

- create/close/recreate lifecycle;
- stale/invalid handles;
- ABI version mismatch;
- RuntimeSnapshot size/layout/endian contract;
- direct-buffer publication/read consistency;
- snapshot sequence monotonicity;
- close/read serialization and late access safety;
- Rust panic containment so no panic unwinds across JNI into Android/Kotlin;
- event-buffer overflow policy as the bounded event drain is added;
- plot/result buffer bounds later;
- repeated Activity recreation does not duplicate native engine/session ownership;
- no unexpected high-frequency JNI call regression;
- low-rate individual external events remain low-rate and do not establish a per-sample ECU pattern;
- no JSON/string/object-graph runtime bridge is introduced as the ABI expands.

Whenever an ABI changes, keep explicit old-version rejection/golden tests rather than silently reinterpreting bytes.

---

# Diagnostics foundation tests

The active UI/diagnostics slice requires executable qualification before real ECU integration.

## Event/metric storage

Test:

- bounded Kotlin event ring wraps/evicts deterministically;
- bounded Rust event ring wraps/evicts deterministically;
- memory stays bounded under event storms;
- event sequence/monotonic ordering is preserved;
- stable event code/domain/severity/typed fields survive native drain;
- metric counters/gauges/durations/rates aggregate deterministically;
- p50/p95/p99/max match reference vectors;
- diagnostic overflow/high-water evidence is visible without changing production-domain behavior.

## Owner/session/correlation

Test:

- owner create/destroy transitions are ordered;
- late event after destroyed owner is observable;
- stale generation/session evidence remains visible but cannot mutate current state;
- Kotlin/Rust correlation IDs survive coarse bounded transfer;
- one logical operation can be reconstructed across Kotlin/Rust events.

## Capture workflows

Test:

- recent-problem capture includes bounded pre-trigger and post-trigger windows;
- reproduction capture markers preserve order/timestamps;
- capture finalization produces an immutable snapshot;
- later live events do not mutate finalized capture;
- Activity recreation does not duplicate capture ownership;
- bounded maximum capture duration/storage policy behaves deterministically;
- prior-exit/crash summary does not claim evidence the platform did not provide.

## Diagnostic archive / D-023

Canonical diagnostic archive tests must prove:

- versioned CBOR machine data round-trips exactly for supported schema versions;
- malformed/truncated/unsupported versions fail explicitly;
- deterministic ordering/identity fields needed for comparison are preserved;
- `report.txt` is generated from the same immutable typed capture, not maintained as a second authority;
- manifest/schema/integrity metadata agrees with contained artifacts;
- routine archive does **not** silently contain full measurement logs, TuneSnapshot/project, profile/INI contents, GPS history, credentials or user documents;
- explicit selected attachments are identified/provenanced separately;
- archive/export generation happens away from UI/acquisition hot paths;
- no JSON/NDJSON files are required by the canonical diagnostic archive;
- any future JSON conversion is tested only as an explicit tooling/export adapter, never as runtime/canonical truth.

## Diagnostics physical qualification

On SM-A137F and SM-T500 deliberately exercise benign failures such as:

- synthetic stale-generation event;
- ring wrap;
- event burst;
- capture recent problem;
- reproduction capture + markers;
- capture export/share;
- Activity/orientation recreation while diagnostics is active.

Inspect the resulting report/archive for practical human usefulness, not only parseability.

---

# Protocol golden vectors

Keep small explicit fixtures for:

- ECU framed request/response/CRC examples;
- generated-INI signatures and representative cross-board geometry;
- complete/partial TuneSnapshot reads;
- tune primitive encode/decode vectors;
- legacy ESP32 button/variable-request/variable-response/variable-set payloads;
- future accessory acknowledgement/version payloads only when that versioned capability exists.

Golden vectors document wire truth independently of UI code.

---

# Generated INI corpus

Maintain trustworthy complete generated profiles with provenance where available.

Tests cover:

- exact signature extraction;
- exact source/profile fingerprint binding;
- runtime block schema compilation;
- tune page/scalar/array/table/curve metadata;
- conditions/menu hierarchy where supported;
- no board-name hardcoding;
- fail-closed behavior for unsupported/ambiguous constructs.

Do not manufacture fake complete profiles and call them production compatibility evidence.

---

# USB transport comparison

Before selecting final sustained runtime USB ownership, measure candidate paths on the same ECU/profile/device/load:

```text
A. Kotlin USB I/O/decode reference
B. Kotlin USB I/O → coarse Rust batches/decode
C. Android open/claim → proven native FD/handle → Rust I/O/decode
```

Measure at least:

- connect/signature/preflight latency;
- sustained frames/bytes/sec;
- CPU;
- allocations/GC for managed paths;
- JNI calls/bytes;
- dropped/corrupt frames;
- unplug/replug/reconnect;
- screen off/on/background/foreground;
- repeated connect cycles;
- resource ownership/close correctness;
- temperature/battery during extended load.

A native-FD path does not replace proven behavior merely because a microbenchmark is faster. D-022 requires historical transport/lifecycle/failure behavior review before production selection.

---

# Tuning simulation/fault tests

Before physical write testing, simulate/fault-inject:

- stale generation/session;
- profile mismatch;
- baseline TuneSnapshot mismatch;
- duplicate/overlapping edit targets;
- out-of-bounds/non-finite values;
- write ACK missing/error;
- exact range readback mismatch;
- disconnect after possible write transmission;
- complete post-write snapshot mismatch;
- Burn timeout/error/pending-state anomalies;
- interrupted/corrupt recovery journal.

Expected outcome after ambiguous possible mutation: fail-closed uncertainty, never automatic replay.

---

# MLG interoperability

MLG is the first planned ecosystem-compatible writer after the format-neutral live path is proven.

Before production acceptance, validate against genuine/corroborated MLG corpus/tool behavior rather than one inferred layout. Cover as exact format work establishes it:

- header/version/metadata structure;
- channel schema/type/scale;
- record/time ordering;
- finalization/EOF behavior;
- malformed/truncated handling;
- MegaLogViewer/TunerStudio compatibility;
- file-size/write/read performance on target Android hardware.

MLG tests qualify an interoperability codec; they never make MLG the ChannelStore/runtime model.

---

# MDF interoperability

Rust MDF implementation must not self-certify.

Validate:

1. EpicHub writes → independent ASAM-compatible tooling reads and compares metadata/samples/timestamps;
2. independent tooling/corpus writes → EpicHub reader compares values/metadata;
3. compressed/uncompressed candidates;
4. truncated/interrupted files and recovery;
5. large-file random range/channel queries;
6. unsupported constructs fail explicitly without silent corruption.

File-size and CPU/throughput measurements are part of acceptance because mobile storage/performance are product requirements.

MDF4 qualification occurs after live acquisition and MLG interoperability per D-014.

---

# BLE / legacy ESP32 bridge tests

Legacy bridge tests cover:

- discovery/filtering/service validation;
- connect/service discovery;
- reconnect/backoff/stale-GATT rejection;
- one connection-wide operation ordering/pressure;
- button/variable-request/variable-response/variable-set codecs;
- partial timeout behavior;
- transport completion vs downstream-unknown state;
- Android process/app disconnect;
- legacy firmware watchdog/liveness and advertising/reconnect;
- concurrent variable notifications and user commands.

The legacy watchdog is a **liveness mechanism**, not a proven relay/output failsafe. GATT write/disconnect must not be asserted as physical-output confirmation/failsafe behavior the protocol does not provide.

---

# Future direct ESP32 output tests

Only if a new versioned direct relay/GPIO capability is introduced, physically verify:

- capability/version negotiation;
- semantic output mapping;
- application acknowledgement;
- confirmed output state/fault telemetry;
- boot/default output state;
- electrical/interlock policy;
- watchdog/disconnect/app-loss behavior;
- actual hardware state versus Android/protocol state.

Physical output qualification proves ESP32 firmware behavior, not merely Android UI state.

---

# Compose/UI tests and performance

The active UI foundation is qualified before real ECU complexity is attached.

Test at minimum:

- primary destinations Dashboard/Tuner/Logger/Analyzer/Diagnostics;
- Settings as global surface;
- wide rail and compact bottom-navigation behavior;
- phone/tablet portrait+landscape reflow;
- navigation/semantic selection survives resize/recreation where specified;
- diagnostics capture remains globally reachable;
- modal/pointer ownership does not leak actions underneath;
- inactive destinations do not consume high-rate display work unnecessarily.

Measure:

- frame time/jank p50/p95/p99/max where practical;
- touch-to-visible response;
- recomposition/layout/draw hotspots;
- dashboard widget-count scaling;
- plot pan/zoom latency when implemented;
- 3D rotation/render performance when implemented;
- lifecycle recreation;
- diagnostic ring/capture enabled vs minimal diagnostic verbosity.

Do not equate render-function calls with actual displayed frames. Representative native engine/diagnostic load should be active during physical performance tests.

---

# Logging soak tests

Representative long sessions verify:

- zero application-level dropped samples within declared supported rates;
- bounded memory/queue depth;
- writer finalization/recovery;
- sustained CPU/thermal/battery;
- storage-full/write-error handling;
- responsive UI while recording;
- analyzer/background work does not starve acquisition/writer.

Reuse the same source workload when comparing MLG and later MDF4 so size/performance decisions are meaningful.

---

# Analyzer tests and performance

Use deterministic historical/replay fixtures where provenance permits, plus increasing dataset sizes and identical queries.

Validate:

- source/profile/relevant-calibration/algorithm identity binding;
- missing/stale/rejected evidence remains explicit;
- direct evidence is not manufactured from interpolation/model guidance;
- independent event/visit-block handling;
- valid-but-non-comparable disposition;
- boundary/source-limited identifiability;
- quantization-aware bounded active experiment proposals;
- deterministic replay for immutable source/profile/version tuple;
- cancellation/replacement of obsolete queries.

Measure:

- file-open/index latency;
- first viewport latency;
- cached pan/zoom latency;
- uncached arbitrary-range latency;
- event search/statistics latency;
- memory residency;
- plot packet size.

Performance should scale primarily with requested viewport/query work rather than entire file size where architecture permits.

---

# Qualification progression for ECU mutation

Any clean-slate tuning implementation advances through deliberate gates:

1. D-022 source/behavior/bug review for the slice;
2. pure parser/codec/policy tests;
3. qualified fixtures/golden vectors;
4. simulator/fault-injection parity with required behavior;
5. bench ECU read-only parity;
6. bench RAM write/readback parity;
7. explicit Burn parity;
8. physical Android lifecycle/reconnect tests;
9. vehicle validation where required.

EpicDash-JZ remains behavioral evidence/oracle for proven safety invariants until EpicHub demonstrates equivalent or deliberately improved semantics. Its code structure and accidental outputs are not parity targets.

---

# CI direction

Current CI already enforces the foundation Rust/Kotlin/Android/dual-ABI baseline.

As corresponding production code appears, extend CI with:

- Kotlin unit/lint/build;
- Rust fmt/clippy/test;
- architecture/dependency checks where practical;
- protocol/profile/measurement/analysis fixtures;
- diagnostic CBOR schema/roundtrip/golden tests;
- JNI build/link validation for both production ABIs;
- artifact inspection;
- no committed secrets/generated device artifacts.

Physical qualification remains separately recorded and must never be implied merely by CI PASS.

## Current acceptance order

The active sequence is:

```text
accepted synthetic Kotlin/Rust foundation
→ UI shell + diagnostics implementation
→ CI + physical qualification on SM-A137F / SM-T500
→ read-only live ECU D-022 gate and transport benchmark/implementation
→ later feature-specific qualification gates
```

No real ECU transport is attached before the current UI/diagnostics foundation is itself proven stable enough to make subsequent debugging and performance evidence trustworthy.