# EpicHub Test Strategy

## Purpose

Testability is an architecture requirement. Each major subsystem should have an intended qualification path before implementation so tests do not depend on UI/manual operation when a deterministic lower-level test is possible.

For inherited behavior, D-022 / `SOURCE_REIMPLEMENTATION_METHOD.md` adds a source-understanding gate: old tests are candidate specifications only after the actual requirement/protocol invariant they prove is identified. Known old bugs require explicit negative/regression coverage rather than blind output parity.

## Evidence levels

- **Unit evidence** — deterministic pure-code behaviour.
- **Contract/golden evidence** — exact wire/schema/file vectors and cross-component boundaries.
- **Integration evidence** — real subsystem composition without necessarily using physical vehicle hardware.
- **Simulator/fault evidence** — timeout/corruption/disconnect/partial-write and uncertainty paths.
- **Bench physical evidence** — real ECU/ESP32/device hardware outside normal vehicle operation.
- **Device physical evidence** — Android phone/tablet/head-unit lifecycle/performance.
- **Vehicle evidence** — behavior requiring real operating vehicle conditions.

Software qualification and physical qualification must be distinguished explicitly.

## Kotlin unit tests

Expected targets:

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
- UI ViewModel state reducers where deterministic;
- settings/layout schema validation.

Android framework APIs should be isolated enough that pure policies/codecs do not require instrumentation tests.

## Rust unit/property tests

Expected targets:

- CRC/framing/response codecs;
- firmware identity parsing;
- INI parser/model/compiler;
- condition parser/evaluator;
- runtime decoder against generated profile fixtures;
- ChannelStore/revision/validity behavior;
- expression parser/compiler/evaluator;
- tune value codecs;
- semantic tune-draft/edit operations where introduced;
- target resolution;
- write-plan invariants/non-overlap/bounds;
- expected TuneSnapshot construction;
- uncertainty/recovery state machines;
- Burn plan/state transitions;
- measurement reader/writer contracts;
- MLG codec/finalization behavior when implemented;
- MDF blocks/metadata/index/recovery when implemented;
- analysis/statistics/search/downsample algorithms;
- analysis-session provenance/rejection/comparability/identifiability when introduced.

Property/fuzz testing should be considered for parsers, codecs and corrupted-file/protocol inputs.

## Protocol golden vectors

Keep small explicit fixtures for:

- ECU framed request/response/CRC examples;
- generated-INI signatures and representative cross-board geometry;
- complete/partial TuneSnapshot reads;
- tune primitive encode/decode vectors;
- legacy ESP32 button/variable-request/variable-response/variable-set payloads;
- future accessory acknowledgement/version payloads only when that versioned capability exists.

Golden vectors document wire truth independently of UI code.

## Generated INI corpus

Maintain trustworthy complete generated profiles where available, with provenance.

Tests should ensure:

- exact signature extraction;
- exact source/profile fingerprint binding;
- runtime block schema compilation;
- tune page/scalar/array/table/curve metadata;
- conditions/menu hierarchy where supported;
- no board-name hardcoding;
- fail-closed behavior for unsupported/ambiguous constructs.

Do not manufacture fake complete profiles and call them production compatibility evidence.

## Tuning simulation/fault tests

Before physical write testing, simulate/fault-inject:

- stale generation/session;
- profile mismatch;
- baseline TuneSnapshot mismatch;
- duplicate/overlapping edit targets;
- value out of bounds/non-finite;
- write ACK missing/error;
- exact range read-back mismatch;
- disconnect after possible write transmission;
- complete post-write snapshot mismatch;
- Burn timeout/error/pending-state anomalies;
- interrupted recovery journal.

Expected result after ambiguous possible mutation: fail-closed uncertainty, never automatic replay.

## MLG interoperability

MLG is the first planned ecosystem-compatible writer after the format-neutral live acquisition path is proven.

Before production acceptance, validate against genuine/corroborated MLG corpus/tool behavior rather than one inferred file layout. Tests should cover as the exact format investigation establishes them:

- header/version/metadata structure;
- channel schema/type/scale representation;
- record/time ordering;
- writer finalization/EOF behavior;
- malformed/truncated input handling;
- reader/writer compatibility with MegaLogViewer/TunerStudio ecosystem tooling;
- file-size/write/read performance on target Android hardware.

MLG tests qualify an interoperability codec; they do not make MLG the ChannelStore/runtime model.

## MDF interoperability

Rust MDF writer/reader must not self-certify.

Validation should include:

1. Rust writes → independent ASAM-compatible tooling reads and compares metadata/sample values/timestamps;
2. independent tooling creates corpus → Rust reader compares values/metadata;
3. compressed/uncompressed variants;
4. truncated/interrupted files and recovery behavior;
5. large-file random range/channel queries;
6. unknown/unsupported constructs fail clearly without silent corruption.

File-size and CPU/throughput measurements are part of acceptance because mobile storage/performance are product requirements.

MDF4 qualification occurs after the format-neutral live path and MLG interoperability path, matching D-014; MDF is not a prerequisite for the first logger.

## JNI/FFI boundary tests

Test:

- create/close/recreate lifecycle;
- stale/invalid handles;
- ABI version mismatch;
- snapshot buffer publication/read consistency;
- event buffer overflow policy;
- plot-buffer bounds;
- errors never panic/unwind into Java/Kotlin;
- repeated Activity recreation does not duplicate native engine/session ownership;
- no unexpected high-frequency JNI call regression;
- low-rate individual external events remain low-rate and do not accidentally establish a per-sample ECU pattern.

## USB transport comparison

Before selecting final runtime USB ownership, measure the candidate paths with the same ECU/profile/device/load:

- Kotlin I/O/decode reference;
- Kotlin I/O → batched Rust processing;
- Android-opened native FD → Rust/native I/O if feasible.

Measure at least:

- connect/signature/preflight latency;
- sustained frames/bytes/sec;
- application CPU;
- allocations/GC for managed paths;
- JNI calls/bytes;
- dropped/corrupt frames;
- disconnect/unplug/replug recovery;
- screen off/on/background/foreground behavior;
- repeated connect cycles;
- temperature/battery during extended logging.

No native-FD transport replaces proven behaviour merely because a microbenchmark is faster. D-022 requires the relevant EpicDash-JZ transport/lifecycle/failure behavior to be reviewed before either production candidate is accepted.

## BLE / legacy ESP32 bridge tests

Legacy bridge tests cover:

- discovery/filtering/service validation;
- connect/service discovery;
- reconnect/backoff/stale-GATT rejection;
- one connection-wide operation ordering/pressure;
- button/variable-request/variable-response/variable-set codecs;
- partial timeout behavior;
- transport completion vs downstream-unknown state;
- Android process/app disconnect;
- legacy firmware watchdog/liveness and advertising/reconnect behavior;
- concurrent variable notifications and user commands.

The legacy firmware watchdog is a **liveness mechanism**, not a proven relay/output failsafe. A GATT write or legacy bridge disconnect must not be tested/asserted as physical-output confirmation/failsafe behavior that the protocol does not provide.

## Future direct ESP32 output tests

Only if a new versioned direct relay/GPIO capability is introduced, physically verify:

- capability/version negotiation;
- semantic output mapping;
- application-level acknowledgement;
- confirmed output state/fault telemetry;
- boot/default output state;
- electrical/interlock policy;
- watchdog/disconnect/app-loss behavior;
- actual hardware state versus Android UI/protocol state.

Physical relay/output tests qualify the ESP32 firmware behavior, not merely Android UI state.

## Compose/UI performance tests

Do not equate application render-function calls with actual displayed frames.

Measure:

- frame time/jank;
- touch-to-visible response where practical;
- recomposition/layout/draw hotspots;
- dashboard widget-count scaling;
- plot pan/zoom latency;
- 3D table rotation/render performance;
- portrait/landscape/phone/tablet/head-unit layouts;
- lifecycle recreation.

Heavy engine work should be active during representative tests, not only an idle demo screen.

## Logging soak tests

Representative long sessions should verify:

- zero application-level dropped samples within declared supported rates;
- bounded memory/queue depth;
- writer finalization/recovery behavior for the selected format;
- sustained CPU/thermal/battery behavior;
- storage-full/write-error handling;
- UI remains responsive while recording;
- analyzer/background work does not starve acquisition/writer.

The same source workload should be reusable when comparing MLG and later MDF4 so file-size/performance decisions are meaningful.

## Analyzer tests and performance

Use deterministic historical/replay fixtures where provenance permits, plus increasing dataset sizes and identical queries.

Validate:

- source/profile/relevant-calibration/algorithm identity binding;
- missing/stale/rejected evidence remains explicit;
- direct evidence is not manufactured from interpolation/model guidance;
- independent event/visit-block handling;
- valid-but-non-comparable disposition;
- boundary/source-limited identifiability;
- quantization-aware bounded active experiment proposals;
- deterministic replay for an immutable source/profile/version tuple;
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

## Qualification progression for ECU mutation

Any clean-slate tuning implementation should advance through deliberate gates:

1. D-022 source/behavior/bug review for the slice;
2. pure parser/codec/policy tests;
3. existing qualified fixtures and golden vectors;
4. simulator/fault-injection parity with required behavior;
5. bench ECU read-only parity;
6. bench RAM write/read-back parity;
7. explicit Burn parity;
8. physical Android lifecycle/reconnect tests;
9. vehicle validation where required.

The existing physically accepted EpicDash-JZ workflow is behavioral evidence/oracle for its proven safety invariants until EpicHub demonstrates equivalent or deliberately improved semantics. Its code structure and accidental outputs are not parity targets.

## CI direction

As the corresponding production code appears, CI should enforce at least:

- Kotlin unit/lint/build;
- Rust fmt/clippy/test;
- architecture/dependency checks where practical;
- protocol/profile/measurement/analysis fixture tests;
- JNI build/link validation for both supported production ABIs;
- no committed secrets/generated device artifacts.

Physical qualification remains separately recorded and must not be implied by CI PASS.