# EpicHub Test Strategy

## Purpose

Testability is an architecture requirement. Each major subsystem should have an intended qualification path before implementation so tests do not depend on UI/manual operation when a deterministic lower-level test is possible.

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
- BLE write scheduling policy;
- ESP32 message codecs;
- accessory requested/confirmed state transitions;
- ActionRouter/policy decisions;
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
- target resolution;
- write-plan invariants/non-overlap/bounds;
- expected TuneSnapshot construction;
- uncertainty/recovery state machines;
- Burn plan/state transitions;
- MDF blocks/metadata/index/recovery;
- analysis/statistics/search/downsample algorithms.

Property/fuzz testing should be considered for parsers, codecs and corrupted-file/protocol inputs.

## Protocol golden vectors

Keep small explicit fixtures for:

- ECU framed request/response/CRC examples;
- generated-INI signatures and representative cross-board geometry;
- complete/partial TuneSnapshot reads;
- tune primitive encode/decode vectors;
- ESP32 button/variable/GPS protocol payloads;
- future accessory acknowledgement/version payloads.

Golden vectors document wire truth independently of UI code.

## Generated INI corpus

Maintain trustworthy complete generated profiles where available, with provenance.

Tests should ensure:

- exact signature extraction;
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
- no unexpected high-frequency JNI call regression.

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

No native-FD transport replaces proven behaviour merely because a microbenchmark is faster.

## BLE/ESP32 tests

Test separately:

- discovery/filtering;
- connect/service discovery;
- reconnect/backoff;
- queue ordering/pressure;
- button/variable/GPS codecs;
- requested vs confirmed state;
- no-response/failure state;
- Android process/app disconnect;
- ESP32 hardware failsafe behavior;
- concurrent high-rate variable notifications and user commands.

Physical relay/output tests must verify the ESP32 firmware behavior, not only Android UI state.

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
- file finalization/recovery;
- sustained CPU/thermal/battery behavior;
- storage-full/write-error handling;
- UI remains responsive while recording;
- analyzer/background work does not starve acquisition/writer.

## Analyzer performance tests

Use increasing dataset sizes and identical queries to measure:

- file-open/index latency;
- first viewport latency;
- cached pan/zoom latency;
- uncached arbitrary-range latency;
- event search/statistics latency;
- memory residency;
- plot packet size;
- cancellation/replacement of obsolete queries.

Performance should scale primarily with requested viewport/query work rather than entire file size where architecture permits.

## Qualification progression for ECU mutation

Any clean-slate tuning implementation should advance through deliberate gates:

1. pure parser/codec/policy tests;
2. existing qualified fixtures and golden vectors;
3. simulator/fault-injection parity;
4. bench ECU read-only parity;
5. bench RAM write/read-back parity;
6. explicit Burn parity;
7. physical Android lifecycle/reconnect tests;
8. vehicle validation where required.

The existing physically accepted EpicDash-JZ workflow is the behavioral oracle until EpicHub demonstrates equivalent or deliberately improved safety semantics.

## CI direction

Before production work grows, CI should eventually enforce at least:

- Kotlin unit/lint/build;
- Rust fmt/clippy/test;
- architecture/dependency checks where practical;
- protocol/profile fixture tests;
- JNI build/link validation for supported ABI(s);
- no committed secrets/generated device artifacts.

Physical qualification remains separately recorded and must not be implied by CI PASS.
