# Analyzer / Autotune Responsibility Map

## Purpose

This supplements `RESPONSIBILITY_INVENTORY.md` and `RESPONSIBILITY_DESTINATIONS.md` with the additional responsibilities exposed by the AE/PID/TCharge/Injection Angle archaeology.

Historical plugin classes are not migration units. These are permanent capability responsibilities.

Classification vocabulary follows `RESPONSIBILITY_INVENTORY.md`: `PRESERVE`, `REDESIGN`, `MERGE`, `SPLIT`, `REFERENCE`, `REMOVE`.

---

## `analysis.session.identity`

Purpose: bind one completed analysis to immutable source/live-session identity, exact profile identity, relevant calibration fingerprint, channel binding, algorithm/profile version and capture/query window.

Classification: `MERGE` + `REDESIGN` from plugin-local provenance models.

Evidence: `SUPPORTED`/`PROVEN` need across AE/TCharge/PID workflows; exact clean schema `PROPOSED`.

Future owner: Rust `epichub-analysis` session/provenance model.

Planned destination: analysis session/provenance module; persistence adapter only if completed sessions are deliberately stored.

Mutable state: in-progress staging owned by one analysis job; completed `AnalysisSession` immutable.

Tests: deterministic replay identity, stale calibration/profile/source invalidation, algorithm-version invalidation.

---

## `analysis.channel-contract`

Purpose: declare required/optional semantic channels and resolve them against live/file-backed source schemas without hardcoded TunerStudio-name logic inside each algorithm.

Classification: `MERGE` + `REDESIGN`.

Evidence: `PROVEN` need in all reviewed plugins.

Future owner: Rust analysis profile consuming `epichub-channels`/profile/interop semantic bindings.

Tests: required missing, optional missing, ambiguous alias, stale/invalid source, exact semantic binding.

---

## `analysis.validity-rejection`

Purpose: retain explicit valid/missing/stale/invalid/rejected status and rejection reason accounting.

Invariant: missing/unresolved is never numerical zero.

Classification: `PRESERVE` behaviour / `MERGE` shared implementation.

Evidence: `PROVEN` across AE/TCharge/PID/Injection Angle.

Future owner: Rust analysis evidence layer using ChannelStore validity.

Tests: contaminant gates, missing/stale channels, discontinuities, rejection counts and reasons.

---

## `analysis.evidence-provenance-kind`

Purpose: distinguish direct observations, derived-from-direct values, interpolated guidance, model predictions and reference-only evidence.

Invariant: interpolation/prediction may guide collection but cannot manufacture direct evidence or mutation eligibility.

Classification: `ADD FORMAL OWNER` from repeated plugin semantics.

Evidence: `PROVEN` need; target representation `PROPOSED`.

Future owner: Rust analysis evidence model.

---

## `analysis.segment-event-visit`

Purpose: detect context/state segments, transitions, settling/exclusion windows, event identities and independent visit blocks.

Classification: `MERGE` + `REDESIGN`.

Evidence: `PROVEN`/`SUPPORTED` across Injection Angle, AE, PID and TCharge.

Future owner: shared Rust analysis primitives, configured by domain profile.

Tests: transition boundaries, settling windows, gap handling, monotonic state-machine behavior, independent-block separation.

---

## `analysis.grouped-statistics`

Purpose: streaming/grouped statistics by semantic context key without requiring whole-log materialization.

Classification: `MERGE` + `REDESIGN`.

Evidence: `SUPPORTED` by existing plugin implementations.

Future owner: Rust analysis statistics module.

Tests: running aggregates versus reference calculations, large-stream bounded memory.

---

## `analysis.coverage`

Purpose: represent direct sample support, independent visits and explanatory-variable span; optionally generate non-authoritative interpolation/guidance for missing regions.

Classification: `PRESERVE` behavior / `MERGE` implementation.

Evidence: TCharge `PROVEN`/qualified workflow; generalization `PROPOSED`.

Future owner: Rust analysis coverage module plus domain profile thresholds.

Tests: direct/interpolated separation, repeat-block thresholds, span thresholds, no interpolation promotion to direct-ready.

---

## `analysis.blocked-validation`

Purpose: split training/validation by independent event/time block rather than neighboring high-rate samples.

Classification: `PRESERVE` statistical intent / `MERGE` primitive.

Evidence: `SUPPORTED`/qualified by TCharge.

Future owner: Rust analysis validation helpers.

Tests: no event leakage, deterministic split/replay, minimum independent held-out support.

---

## `analysis.comparability`

Purpose: decide whether individually valid events/results may be compared under one domain-defined context and tolerances.

Classification: `ADD FORMAL OWNER` from plugin-local logic.

Evidence: `PROVEN` need across AE, PID and Injection Angle.

Future owner: domain analysis profile using shared comparison/result vocabulary.

Result examples: `IMPROVED`, `SIMILAR`, `REGRESSED`, `INSUFFICIENT`, `NOT_COMPARABLE`.

Tests: valid-but-incompatible events must not be silently pooled.

---

## `analysis.identifiability`

Purpose: separate numerical fit/search result from whether the data actually identifies the requested parameter/model.

Classification: `PRESERVE` behaviour / `MERGE` support primitives.

Evidence: `PROVEN`/qualified TCharge and PID lessons.

Future owner: Rust domain analysis/model result.

States may include `IDENTIFIED`, `BOUNDARY_LIMITED`, `SOURCE_LIMITED`, `INSUFFICIENT_COVERAGE`, `CONTAMINATED`, `NOT_COMPARABLE`, `UNSUPPORTED_CONFIGURATION`.

Tests: search-boundary optimum, one-sided support, unexcited direction, insufficient held-out support.

---

## `analysis.domain-profile`

Purpose: own domain-specific causal prerequisites, channel contract, event semantics, sample rejection, context/comparability, metrics/formulas/model, confidence gates and recommendation policy while reusing common infrastructure.

Classification: `REDESIGN` from separate plugin architectures.

Evidence: PID controller-profile architecture plus cross-plugin convergence `SUPPORTED`/strong.

Future owner: Rust `epichub-analysis` domain profiles/models.

Initial candidate families: AE, PID, TCharge, injection phasing. Product priority remains separate.

---

## `analysis.capability-maturity`

Purpose: expose whether a profile is qualified only for observation, measurement, recommendation and/or bounded experiment.

Classification: `ADD FORMAL OWNER`.

Evidence: explicit PID maturity model plus AE/TCharge qualification history `SUPPORTED`.

Future owner: Rust analysis profile metadata + repository qualification evidence; Compose projects it honestly.

Invariant: software execution success never implies physical/numerical recommendation qualification.

---

## `analysis.completed-session-retention`

Purpose: keep the last completed immutable analysis available while a replacement capture/analysis is staged; replace atomically only after the new analysis succeeds.

Classification: `PRESERVE` strong TCharge behavior / `REDESIGN` generic implementation.

Evidence: `PROVEN` in TCharge workflow.

Future owner: Rust analysis session store.

Tests: failed start/capture/analysis leaves prior completed session untouched.

---

## `analysis.multi-analysis-reuse`

Purpose: allow one immutable recorded session to feed multiple compatible analyses when each task's channel/evidence/calibration requirements are satisfied.

Classification: `ADD FORMAL OWNER` / `MERGE` existing offline/live workflows.

Evidence: TCharge retained-session design and Injection Angle offline analysis `SUPPORTED`; target generalization `PROPOSED`.

Future owner: Rust analysis query/profile layer.

Invariant: Guided Capture is not mandatory when existing evidence is sufficient.

Tests: same source produces deterministic multiple-profile results; insufficient task-specific coverage is reported rather than inventing data.

---

## `analysis.guided-capture`

Purpose: coach the user toward missing direct evidence/coverage while preserving the same final analysis contract used for offline logs.

Classification: `PRESERVE` useful capability / `REDESIGN` Android presentation.

Evidence: AE/TCharge workflows `SUPPORTED`/partly vehicle-qualified.

Future owner: Rust coverage/readiness state + Compose task-specific guidance UI.

Invariant: guidance/interpolation does not create direct evidence.

---

## `analysis.active-experiment-plan`

Purpose: describe a bounded temporary candidate test in semantic terms without owning ECU memory writes.

Classification: `MERGE` + `REDESIGN` plugin-specific candidate writers.

Evidence: guarded Apply/Restore patterns `PROVEN`/`SUPPORTED` across plugins.

Future owner: Rust analysis creates `ExperimentProposal`; Rust `epichub-tuning` resolves/quantizes/verifies/executes through the sole ECU session.

Invariant: no analyzer raw page/offset writer and no analyzer Burn.

Tests: stale baseline, unrepresentable request, quantized requested/effective values, bounded step/timeout/retry, exact readback, failed candidate Restore.

---

## `analysis.replay-golden`

Purpose: rerun a known immutable log/session through a specific profile/algorithm version and compare accepted/rejected events, metrics/model status and recommendation eligibility deterministically.

Classification: `ADD` qualification responsibility.

Evidence: existing plugin tests/log evidence `SUPPORTED`; unified corpus `PROPOSED`.

Future owner: cross-system Rust analysis fixture/test harness.

---

# Removed historical ownership models

## `plugin-specific-autotune-mini-app`

Classification: `REMOVE` as EpicHub architecture.

Replacement: shared analysis engine + domain profiles + Compose presentation.

## `plugin-specific-raw-ecu-writer`

Classification: `REMOVE`.

Replacement: one semantic tuning authority for all RAM Write/Restore/Burn behavior.

## `hardcoded-ts-alias-analysis-identity`

Classification: `REMOVE`.

Replacement: semantic ChannelId bindings at source/import/profile adapter boundaries.

## `guided-capture-required-for-every-task`

Classification: `REMOVE` assumption.

Replacement: task-declared acquisition modes; qualifying existing logs are valid first-class evidence sources.

## `sample-count-equals-independent-evidence`

Classification: `REMOVE` assumption.

Replacement: event/visit/block identity and blocked validation.

## `best-search-score-implies-identification`

Classification: `REMOVE` assumption.

Replacement: explicit identifiability and boundary/source-limited states.

---

# Destination summary

```text
Live ChannelStore / MeasurementStore
            │
            ▼
      AnalysisSession
      ├─ provenance/identity
      ├─ channel contract
      ├─ accepted/rejected evidence
      ├─ segments/events/visits
      ├─ grouped statistics/coverage
      └─ immutable source window
            │
            ▼
      Domain Analysis Profile
      ├─ causal prerequisites
      ├─ metrics/model
      ├─ comparability
      ├─ validation/identifiability
      └─ optional recommendation
            │
            ├──────────────► Plot/report/Compose projection
            │
            └─ optional ExperimentProposal
                          │
                          ▼
                  sole Tuning Authority
                  resolve/quantize/write/readback/restore
```

This closes the plugin-derived responsibility/destination gap without importing their historical TunerStudio/Swing architecture.