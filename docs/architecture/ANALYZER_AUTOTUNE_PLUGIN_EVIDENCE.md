# Analyzer / Autotune Plugin Evidence

## Purpose

This document records reusable analysis, evidence, guided-capture and bounded-experiment lessons from the existing EPICEFI tuning plugin repositories.

These repositories are **future-feature evidence**, not architectural templates. Their TunerStudio/Swing integration, plugin APIs and historical class structures do not become EpicHub's Android architecture.

The useful target is one shared Rust analysis/evidence engine with domain-specific analysis profiles, while all ECU mutation remains behind EpicHub's single semantic tuning authority.

## Reviewed source states

### AE Tuner

Repository: `PJawZK/AE-Tuner-EPICEFI-`

Reviewed current `main`: `9ac4f5be9508972bb6c2be6396dc9527ca16842b`.

The repository records qualified private development `0.4.5-vehicle-test.16` and public stable `v0.4.5`; the exact private qualified runtime source is separately recorded by that repository.

High-value evidence:

- `README.md`;
- `docs/GENERAL_AE_TUNER_FRAMEWORK.md`;
- `docs/EVIDENCE_INDEX.md`;
- `docs/BLEND_DURATION_MODEL_CORRECTION.md`.

### PID Autotune

Repository: `PJawZK/epicefi-pid-autotune-plugin_Private`

Reviewed `main`: `a224e75aeb10f36939b34f33db244daab56cc7bf`, repository authority `0.5.31-RC1`.

High-value evidence:

- `README.md`;
- `docs/CURRENT_STATE.md`;
- `docs/CONTROLLER_PROFILE_ARCHITECTURE.md`.

`0.5.31-RC1` is software-qualified but, by its own repository authority, still a vehicle-validation candidate. Findings below preserve that distinction.

### TCharge Autotune

Repository: `PJawZK/epicefi-tcharge-autotune-plugin`

Reviewed `main`: `79777ff4318027f2a874a365cfc78a3ee9bf3dc5`.

High-value evidence:

- `README.md`;
- `analysis/TChargeModelResult.java`;
- `analysis/TChargeCoverageResult.java`;
- analysis package separation including sample construction, coverage, estimator, exhaust-delay and result models;
- separate Apply coordinator/backend/state package.

### Injection Angle

Repository: `PJawZK/Injection-Angle-TS-Plugin_Private`

Reviewed `main`: `bedee364528bfa5fe87afe4584d0ddaf9c4cc1f3`, version `1.7.1`.

High-value evidence:

- `README.md`;
- `InjectionMath.java` / fuel-estimator and guarded-write separation;
- `LogPhasingAnalyzer.java`;
- `LogPhasingAnalyzerTest.java`.

---

# 1. The common pipeline is evidence first, recommendation later

Across the mature plugin work, a tuning feature is not one function that ingests channels and returns a value to write.

The recurring reliable structure is:

```text
source/profile/calibration identity
→ input/channel readiness
→ capture/import
→ sample validity + rejection reasons
→ event/segment/coverage extraction
→ measurements/statistics/model fit
→ confidence / identifiability / comparability
→ optional bounded recommendation
→ reviewed experiment/proposal
→ separate guarded mutation authority
→ readback / test / keep-or-restore
```

These stages deliberately have different authority.

Examples:

- AE may have a physically valid measurement method while withholding the numerical Blend Duration conversion.
- TCharge keeps rejected model-fit attempts as diagnostic evidence while marking them non-Apply-eligible.
- PID explicitly matures profiles through observer/event/measurement stages before recommendation is allowed.
- Injection Angle can compute and compare timing tables while explicitly warning that the correct engine-specific timing is not established by the software calculation alone.

### EpicHub consequence

The analyzer API must represent **measured evidence, derived analysis and proposal authority separately**. A completed calculation is not automatically a recommendation; a recommendation is not automatically a write plan; and a write plan is never executed by the analyzer itself.

---

# 2. Evidence identity includes relevant calibration state

AE ranks evidence approximately as:

1. direct same-protocol A/B evidence under the current relevant calibration;
2. accumulated evidence under the same relevant calibration;
3. older/different-tune evidence as reference unless the relevant calibration fingerprint is demonstrably equivalent.

It also recognizes that changing an upstream AE setting can invalidate dependent downstream evidence.

TCharge re-reads causal ECU state immediately before capture, after capture/before analysis and again before Apply. Changes to relevant mode, estimator, table, rate, cylinder/stroke or causal prerequisite state invalidate the run.

PID proof records similarly become stale when a proven value changes; changing Bias axis geometry invalidates all stored knot proof.

### EpicHub consequence

Every durable `AnalysisSession` needs explicit provenance, not just a filename/time range.

At minimum:

```text
AnalysisSessionIdentity
- source identity
    - live session/generation OR immutable log fingerprint
- profile identity/fingerprint
- relevant calibration fingerprint
- channel binding/schema identity
- algorithm/profile version
- capture/query window
```

A domain profile defines which tune/configuration values are **causally relevant** to its evidence fingerprint. The entire tune need not invalidate every analysis, but relevant upstream changes must never be silently pooled.

Derived results are invalidated when their required identity/provenance no longer matches.

---

# 3. Missing and rejected data are not zero

AE explicitly records that missing/unresolved channels are unknown, not zero.

TCharge rejects observations when required same-sample evidence is stale/unavailable and tracks contamination/rejection causes. Optional subscription can remain possible for diagnostics, but absence of safety evidence can still block Apply eligibility.

Injection Angle requires its essential log channels and refuses analysis when one is missing; optional channels simply reduce available comparison context.

PID distinguishes poor controller behavior—which may be valuable measured evidence—from missing/stale channels and data-integrity failures, which are not quantitative evidence.

### EpicHub consequence

Analysis input values use explicit validity:

```text
Valid(value)
Missing
Stale
Invalid
Rejected(reason)
```

No analysis primitive may coerce missing/stale/unsupported evidence to numerical zero merely for convenience.

Rejection accounting is first-class output, so a user can tell the difference between:

- insufficient coverage;
- contaminated samples;
- source/channel failure;
- non-comparable operating conditions;
- a genuinely poor physical response.

---

# 4. Direct evidence, derived values and interpolated guidance are distinct

TCharge's coverage model provides the clearest implementation proof.

A cell carries direct evidence properties such as:

- accepted/eligible sample count;
- independent visit blocks;
- thermal-driver span.

Direct evidence levels progress through `EMPTY`, `VISITED`, `REPEATED`, `THERMAL_READY`.

Neighbor interpolation can display guidance such as `~NN%`, but an interpolated empty cell remains `EMPTY` internally and cannot become `isDirectReady()`.

AE similarly separates physical Blend catch-up timing from current-tune Effective MAP replay: replay is diagnostic context only and cannot accept/reject/time/weight the physical event.

### EpicHub consequence

Evidence provenance should distinguish at least:

```text
DIRECT_OBSERVATION
DERIVED_FROM_DIRECT
INTERPOLATED_GUIDANCE
MODEL_PREDICTION
REFERENCE_ONLY
```

A domain profile's confidence gate explicitly states which kinds may contribute.

**Interpolation/prediction may guide where to gather evidence next, but cannot silently create observed evidence or increase write eligibility.**

---

# 5. Independent visits/blocks matter more than raw sample count

TCharge does not treat adjacent high-rate samples as independent evidence.

Its confidence model uses:

- repeated independent time blocks;
- within-cell thermal-driver variation;
- blocked held-out validation;
- minimum independent held-out support.

Its coverage guide counts separated 3-second visit blocks rather than only samples.

PID event identification similarly uses repeated distinct transitions, including bidirectional opening/closing evidence where the plant is asymmetric.

AE groups comparable physical events rather than treating every callback sample as an independent repetition.

### EpicHub consequence

The generic analysis library needs explicit support for:

- visit/event/block identities;
- minimum separation time;
- segment-level rather than sample-level validation splits;
- repeated-condition support;
- within-group explanatory-variable span.

Train/validation splitting must avoid leakage from interleaved neighboring samples of the same physical event.

---

# 6. Segmentation and settling windows are reusable primitives

Injection Angle's focused MSL analyzer is a compact example:

```text
stream log rows
→ reconstruct EOI from SOI + PW × RPM × 0.006
→ normalize 720° angle
→ classify timing group + fan state
→ detect state transition
→ discard first 10 s after each transition
→ aggregate settled samples
→ compare only within matching fan state
```

The implementation reads the log line-by-line, so the analysis does not require a whole file in memory.

PID also depends on stateful segmentation: e.g. boost tuning should distinguish spool/open-loop, closed-loop engagement, target approach, overshoot and settled tracking instead of treating an entire pull as one homogeneous PID event.

AE Blend Duration follows a physical event state machine and explicitly forbids retroactively searching pre-plateau samples to manufacture catch-up completion.

### EpicHub consequence

Reusable Rust primitives should include:

- state/condition segmenter;
- transition markers;
- pre/post-transition exclusion/settling window;
- event state machines with monotonic progression;
- grouped streaming statistics;
- context-keyed comparability groups.

The context key is domain-defined; examples include fan state, gear, RPM/load band, target band or latched tune-table bin.

---

# 7. Comparability is explicit, not assumed

AE retains physically valid events even when they are not mutually comparable, then combines only compatible RPM/load/TPS-step/gear cohorts for repeatability review.

Its current Blend workflow lets the first valid event establish a natural TPS-step cohort and prevents gradual cohort drift.

Injection Angle compares timing groups only within the same fan state and warns that road-load comparisons need matched speed/grade/throttle/load/temperature.

PID requires comparable operating centers/transition directions and treats `NOT_COMPARABLE` as a real candidate result, not a hidden failure.

### EpicHub consequence

A generic comparison engine should return a typed disposition such as:

```text
IMPROVED
SIMILAR
REGRESSED
INSUFFICIENT
NOT_COMPARABLE
```

with explicit reasons/metrics.

Domain profiles own the comparability key/tolerances. The engine must not average incompatible valid events merely because they belong to the same log.

---

# 8. Confidence and identifiability are not one scalar score

TCharge demonstrates several independent failure modes:

- too little independent held-out support;
- inadequate repeated cells;
- inadequate thermal-driver span;
- a breakpoint candidate at an evidence-supported search boundary;
- one-sided support;
- exhaust delay optimum at the search ceiling;
- a limiter direction not sufficiently excited.

A best numerical score at the edge of the search domain is treated as **unidentified**, not as a valid optimum.

Rejected attempted fits can retain attempted parameters/RMSE/sample count for diagnostics while remaining non-Apply-eligible.

PID likewise distinguishes `SOURCE_LIMITED`, insufficient, non-comparable and actual regression.

### EpicHub consequence

Do not collapse model status into `score >= threshold`.

Useful generic status dimensions include:

```text
EvidenceReadiness
Comparability
Identifiability
ValidationSupport
Confidence
RecommendationEligibility
```

Suggested model states:

```text
IDENTIFIED
BOUNDARY_LIMITED
SOURCE_LIMITED
INSUFFICIENT_COVERAGE
CONTAMINATED
NOT_COMPARABLE
UNSUPPORTED_CONFIGURATION
```

The numerical best candidate may be retained as diagnostic evidence even when recommendation eligibility is false.

---

# 9. Firmware/controller quantization must be inside the experiment model

PID vehicle evidence exposed a critical generic lesson.

A requested 16.25% position became 16.50% through the actual controller table representation. The old loop compared against the unrepresentable requested target and could retry indefinitely.

The corrected implementation resolves experiment endpoints through the same quantization as the write representation **before** the experiment, reports requested versus effective values and bounds acquisition by retry count/time.

It also moves to distant Bias targets through bounded representable waypoints and discards movement evidence before beginning local proof capture.

Injection Angle similarly rounds final EOI writes to the controller's actual whole-degree resolution before verification.

### EpicHub consequence

Any active experiment must plan in the controller's real semantic/storage representation:

```text
requested semantic target
→ tuning target resolver / quantizer
→ effective writable target
→ safety/step/limit check
→ experiment command
```

The analyzer never assumes a requested floating-point number can physically exist in the ECU.

Reports retain both requested and effective values.

Active target acquisition is always bounded by explicit timeout/retry/step/abort rules.

---

# 10. Analysis sessions and ECU Apply/Restore state are separate

TCharge deliberately retains a completed analysis session independently from temporary ECU candidate Apply state.

One retained session contains the reviewed baseline, subscription/channel evidence, captured samples, complete analysis results, all model candidates and final direct coverage.

The workflow can therefore be:

```text
capture once
→ analyze candidates A/B/C
→ Apply A
→ physical test/log
→ Restore baseline
→ Apply B
→ physical test/log
→ Restore baseline
→ Apply C
```

No recapture is required merely to select another candidate from the same valid source evidence.

Starting a new capture is non-destructive: the previous completed session remains until the new capture **and analysis** succeed.

AE similarly retains incomplete stopped evidence for continuation instead of treating `capture stopped` as `evidence accepted`.

### EpicHub consequence

`AnalysisSession` is immutable once completed.

Starting/running a replacement analysis uses staging state. The currently accepted completed session is atomically replaced only after the new analysis succeeds.

Candidate selection, temporary ECU test Apply and Restore are separate state machines.

This directly enables the desired EpicHub workflow where one imported/logged driving session can feed multiple compatible analyses—including related positive and negative/decel views—without driving the same test again merely because another analyzer is selected.

A new capture remains necessary when:

- required channels/evidence are absent;
- the relevant calibration fingerprint changed;
- the requested analysis requires an operating region/event type not present;
- causal prerequisites differ;
- a deliberate candidate Apply creates the new physical state whose outcome now needs measurement.

---

# 11. Offline log and live capture should feed the same analysis contract

The plugin family currently has mixed acquisition mechanisms: TunerStudio live callbacks, plugin-owned captured samples and focused MSL import.

The algorithms themselves generally consume semantic samples/events rather than requiring Swing UI concepts.

### EpicHub consequence

Normalize acquisition before domain analysis:

```text
Live ChannelStore range / guided capture
                    \
                     → AnalysisInput / EvidenceSource
                    /
MLG / MDF4 / MSL imported measurement range
```

A domain analysis profile receives the same typed channels, validity/provenance and event timestamps regardless of source.

Live guidance may add interactive coverage/driver coaching, but final offline replay of the same captured samples should reproduce the same deterministic analysis result when the algorithm/profile version and relevant identities match.

This is a key qualification strategy: historical logs become deterministic golden/replay fixtures.

---

# 12. Domain profiles share infrastructure but retain domain semantics

The PID repository explicitly separates a shared tuning engine from controller profiles.

Shared concepts include:

- controller/profile discovery;
- required/optional channel readiness;
- capture lifecycle;
- sample integrity/discontinuity;
- baseline snapshots;
- event storage/history;
- bounded candidate changes;
- before/after comparison;
- keep/review/rollback decisions;
- export/diagnostics.

A domain profile owns:

- process/target/output semantics;
- required/context channels;
- contamination/rejection rules;
- grouping/comparability;
- event detector/state machine;
- response metrics;
- safety gates;
- model/recommendation policy.

It specifically warns not to force feed-forward tuning through PID-response mathematics merely because both use common capture infrastructure.

### EpicHub consequence

Use a common Rust `epichub-analysis` foundation with domain analysis profiles/models, rather than one universal "autotune algorithm".

Illustrative target split:

```text
epichub-analysis
├─ session / provenance
├─ channel requirements / bindings
├─ validity / rejection accounting
├─ segment / event / visit blocks
├─ grouped statistics
├─ comparability
├─ coverage maps
├─ blocked validation split
├─ optimization/search support
├─ candidate comparison
├─ replay/report schema
└─ profiles/
   ├─ ae/
   ├─ pid/
   ├─ tcharge/
   └─ injection_phasing/
```

Exact crate/module count remains provisional; responsibility boundaries are the authority.

---

# 13. Capability maturity should be explicit

PID defines a useful maturity ladder:

```text
mapping
→ observer
→ event extraction
→ measurement
→ recommendation
→ iteration/rollback
→ refinement
→ mature
```

This maps well to the actual AE/TCharge history too: several tasks have trustworthy acquisition or measurement before trustworthy numerical recommendations.

### EpicHub consequence

Every analysis profile should expose a capability/maturity contract in code/tests/documentation.

A screen must not imply "autotune" authority when the current profile only has measurement or diagnostic maturity.

For example:

```text
OBSERVE_ONLY
MEASURE
RECOMMEND
EXPERIMENT
```

may be a useful product-facing capability projection, while detailed qualification records remain in repository tests/evidence.

Software qualification, physical measurement qualification and numerical recommendation qualification remain separate claims.

---

# 14. Active experiments never create a second writer

AE, TCharge, PID and Injection Angle all converge on guarded temporary RAM changes with no automatic Burn.

Their existing implementations have separate plugin-specific writers because they are separate historical plugins. EpicHub must **not** reproduce that multiplicity.

### EpicHub consequence

A domain analyzer can construct a semantic experiment request such as:

```text
ExperimentProposal
- source AnalysisSession ID
- relevant baseline fingerprint
- intended semantic targets
- requested semantic values
- test purpose
- bounded experiment policy
```

But execution is always:

```text
Analyzer proposal
→ EpicHub semantic tuning authority
→ current live identity/stale-baseline verification
→ target resolution + quantization
→ guarded RAM Write
→ exact readback/full verification
→ experiment state
→ explicit Restore or later explicit Burn through normal tuner workflow
```

The analyzer has no raw page/offset/byte writer and cannot Burn.

A failed/inconclusive candidate does not become retained just because the experiment produced data. PID's conservative rule—only a genuinely `IMPROVED` result survives an autonomous candidate test, while `SIMILAR`, `REGRESSED`, `INSUFFICIENT` and `NOT_COMPARABLE` restore the verified baseline—is strong evidence for fail-conservative iteration policy.

---

# 15. Guided capture is useful, but it is not required for every analysis

AE and TCharge demonstrate valuable eyes-up/coverage guidance when the driver must deliberately create otherwise-missing excitation or repeated regions.

However, Injection Angle proves that useful A/B analysis can simply import an existing log, segment it and compare settled groups.

### EpicHub consequence

Each analysis task declares its acquisition modes:

```text
OFFLINE_EXISTING_LOG
LIVE_PASSIVE_CAPTURE
GUIDED_CAPTURE
BOUNDED_ACTIVE_EXPERIMENT
```

Do not require Guided Capture when existing recorded evidence already satisfies the exact same analysis contract.

The application should prefer reuse of a qualifying existing immutable session/log because it preserves comparability across related analyses and avoids unnecessary new physical variation.

Guided Capture is justified when it improves or creates evidence that the existing log cannot supply, not because the feature architecture assumes every tuning task must own a driving wizard.

---

# 16. Specific reusable primitives demonstrated by the plugin family

## Evidence/capture primitives

- required/optional semantic channel contract;
- channel freshness/validity;
- contamination/rejection reason counters;
- stable baseline qualification;
- event/visit/block identity;
- generation/cancellation identity;
- direct vs derived/interpolated evidence;
- direct coverage maps and revisit suggestions;
- state-transition exclusion/settling windows;
- context/comparability keys.

## Analysis primitives

- running/grouped statistics;
- min/max/span and support counts;
- event response metrics;
- bidirectional/asymmetric event grouping;
- blocked training/validation split;
- nuisance/intercept terms for repeated operating cells;
- bounded grid/search optimization;
- boundary-optimum/identifiability detection;
- delay search;
- firmware-model replay where explicitly diagnostic;
- quantization-aware proposed values;
- candidate comparison with typed disposition.

## Result primitives

- immutable completed `AnalysisSession`;
- accepted/rejected sample accounting;
- evidence coverage projection;
- model candidate set;
- attempted rejected candidate diagnostics;
- confidence/identifiability/readiness;
- recommendation eligibility;
- exact provenance/algorithm version;
- reproducible report/export.

---

# 17. What should not be transferred

Do not transfer:

- Swing/TunerStudio plugin UI architecture;
- one plugin-specific writer per tuner;
- hardcoded TunerStudio channel aliases as EpicHub's semantic channel model;
- giant UI/controller classes such as historical plugin panels;
- plugin-local tune authority that duplicates EpicHub profile/tuning authority;
- fixed vehicle/tune values from development documentation as runtime defaults;
- a heuristic label as if it were measured truth;
- interpolated coverage as real observations;
- adjacent callback samples as independent validation observations;
- best-at-search-boundary results as valid identified optima;
- unbounded retry loops for active experiments;
- silent approximation of unsupported controller/fueling modes;
- automatic Burn behavior.

---

# 18. Target responsibility assignments

| Evidence responsibility | EpicHub target owner |
|---|---|
| live/file source ranges | `epichub-channels` + `epichub-measurement` |
| analysis session identity/provenance | `epichub-analysis` |
| semantic channel requirements/bindings | `epichub-analysis` consuming compiled channel/profile metadata |
| sample validity/rejection accounting | `epichub-analysis` |
| segmentation/events/visit blocks | `epichub-analysis` |
| grouped statistics/coverage/comparability | `epichub-analysis` |
| domain-specific formulas/model/recommendation | domain profiles under `epichub-analysis` |
| viewport plots/heatmaps | `epichub-plotting` from analysis results |
| guided-capture presentation | Compose `ui/analyzer` / task-specific presentation |
| semantic candidate Apply/Restore | sole `epichub-tuning` authority + ECU session |
| file import/export | measurement/interop + Kotlin SAF transport |
| diagnostics/report transport | analysis result schema + diagnostics/export presentation |

---

# 19. Required qualification fixtures

The plugin repositories contain useful tests, but EpicHub needs format-neutral deterministic goldens.

Representative fixture families:

```text
tests/fixtures/analysis/
├─ ae/
│  ├─ movement-timing/
│  ├─ blend-duration/
│  └─ decel/
├─ pid/
│  ├─ dc-iac-bias/
│  ├─ dc-iac-position/
│  └─ idle/
├─ tcharge/
│  ├─ rpm-tps/
│  ├─ air-mass/
│  ├─ table/
│  └─ rate-limit/
└─ injection-phasing/
```

Each fixture should bind:

- immutable source/log fingerprint;
- semantic channel mapping;
- relevant calibration/profile fingerprint;
- algorithm/profile version;
- accepted/rejected events/samples;
- expected measurements/model status;
- expected recommendation eligibility;
- if applicable, expected proposed semantic targets but **not** automatic write execution.

Important edge fixtures include:

- missing/stale channels;
- logging gaps/discontinuities;
- incompatible calibration fingerprints;
- contamination/transient state;
- adjacent samples that must stay one validation block;
- insufficient independent visits;
- one-sided/boundary-limited searches;
- quantization-unreachable requested target;
- active-experiment timeout;
- non-comparable valid events;
- interpolated-only coverage;
- candidate regression/insufficient result causing Restore.

---

# Remaining Phase 1 decisions exposed by this review

1. Define the concrete `AnalysisSession` / provenance schema and relevant-calibration fingerprint mechanism.
2. Define the domain-profile interface: channel contract, sample gate, segment/event extraction, measurement/model, comparability and optional recommendation.
3. Define the typed analysis status/disposition vocabulary without over-generalizing domain-specific confidence.
4. Decide how completed analysis sessions are persisted/indexed relative to immutable source logs.
5. Define the semantic `ExperimentProposal` handoff to `epichub-tuning`; analyzers never gain raw write authority.
6. Build deterministic replay goldens from the existing validated vehicle/log datasets where redistribution/storage permits.
7. Define Guided Capture as an optional acquisition capability layered on the same analysis contract, not as a mandatory workflow for all tasks.
8. Prioritize which existing plugin analyses become initial EpicHub profiles after the generic analyzer/log infrastructure is proven.

## Phase 1 conclusion

The plugin family is evidence for a **shared analysis/evidence platform with domain profiles**, not four plugin ports.

The most important transferable rule is:

> Observations, derived analysis, recommendations and ECU mutation are separate authority levels. Evidence is identity-bound and reproducible; guidance cannot fabricate evidence; and any candidate test must use the single existing semantic tuning transaction authority with exact verification/Restore semantics.
