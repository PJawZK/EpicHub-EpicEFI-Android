# EpicHub Tuning Tools Foundation

## Purpose

EpicHub must expose the former TunerStudio-plugin capabilities as native application tools without recreating a plugin runtime, mini-app ecosystem or second tuning authority.

The working product label is **Tuning Tools**. A shorter compact-navigation label may be `Tools`. The exact user-facing label may still be refined during visual design without changing this responsibility contract.

Authority: D-020, D-022, D-023 and D-024.

---

# 1. These are native tools, not plugins

Historical AE, PID, TCharge and Injection Angle projects are evidence sources for useful behavior, algorithms, capture workflows, qualification rules and failure modes.

EpicHub does **not** preserve their historical plugin packaging model.

There is no requirement for:

- a TunerStudio plugin API;
- runtime JAR/plugin loading;
- Java/JavaScript plugin execution;
- arbitrary third-party code loading;
- per-tool independent channel stores;
- per-tool independent ECU connections;
- per-tool write authority;
- a WebView mini-application for each tool.

Each retained capability is reimplemented piece-by-piece under D-022 and integrated into EpicHub's normal Kotlin/Compose + Rust architecture.

---

# 2. Product/UI role

`Tuning Tools` is a first-class functional destination in the EpicHub information architecture from the ground UI stage.

It must exist in the initial application shell even before every tool algorithm has been migrated.

The shell may initially show tool cards such as:

```text
Acceleration Enrichment
PID Controller
TCharge
Injection Angle
```

Each card exposes an honest capability/maturity state, for example:

```text
Planned
Evidence review
Read-only analysis
Guided capture
Recommendation qualified
Bounded experiment qualified
Unavailable for current profile/source
```

An unavailable or not-yet-implemented tool remains visible when that is useful for product structure, but it must not pretend to be operational.

---

# 3. Tuning Tools versus Analyzer versus Tuner

These three destinations have different responsibilities.

## Analyzer

Generic measurement exploration:

- files/live sources;
- channels;
- plots;
- statistics;
- comparisons;
- event/search/query work;
- reusable analysis views.

## Tuning Tools

Guided domain workflows:

- AE capture/analysis/recommendation;
- PID characterization/tuning assistance;
- TCharge evidence/model work;
- Injection Angle analysis;
- future deliberately accepted tuning-assistance workflows.

A tool may use the same source, channel, query, statistics and plotting infrastructure as Analyzer, but it adds domain-specific prerequisites, capture guidance, evidence classification, confidence/identifiability and recommendation policy.

## Tuner

Manual/profile-driven semantic calibration browsing and editing plus the only user-facing route into the qualified tuning mutation authority.

A Tuning Tool may produce a semantic recommendation or `ExperimentProposal`, but it never gains its own raw ECU write/Burn path. D-019 remains authoritative.

---

# 4. Runtime ownership

## Kotlin / Compose owns

- Tuning Tools navigation;
- tool cards and maturity/status presentation;
- guided-capture instructions and user interaction;
- progress/result presentation;
- plot/inspector composition from prepared data;
- presentation-only selection/filter/layout state;
- Android lifecycle/platform integration.

## Rust owns

- shared analysis session/provenance model;
- semantic channel contracts;
- validity/rejection accounting;
- event/segment/visit detection;
- grouped/statistical calculations;
- comparability/coverage/identifiability;
- domain profiles/models;
- deterministic replay;
- recommendations and confidence evidence;
- bounded experiment proposals where qualified;
- retained immutable completed analysis sessions.

## Tuning authority owns

- exact current profile/tune identity;
- semantic target resolution;
- value quantization/constraints;
- any RAM write/read-back verification;
- Restore/uncertainty behavior;
- Burn.

The Tuning Tools layer cannot bypass these boundaries.

---

# 5. Shared analysis platform

The historical plugin projects converge onto one Rust analysis platform under D-020.

Conceptual flow:

```text
ChannelSource / MeasurementStore
        │
        ▼
shared analysis primitives
        │
        ├─ provenance / validity / rejection
        ├─ events / segments / visits
        ├─ statistics / coverage / comparability
        ├─ validation / identifiability
        └─ bounded search/model helpers
        │
        ▼
domain profile
        │
        ├─ AE
        ├─ PID
        ├─ TCharge
        └─ Injection Angle
        │
        ▼
measurement/result/recommendation/proposal
```

The common platform prevents each historical plugin from rebuilding its own logging, source, charting, persistence and ECU communication stack.

---

# 6. Acquisition modes

A tool explicitly declares which evidence modes it supports:

```text
OFFLINE_EXISTING_LOG
LIVE_PASSIVE_CAPTURE
GUIDED_CAPTURE
BOUNDED_ACTIVE_EXPERIMENT
```

Existing evidence should be reused when sufficient. Guided capture is not mandatory merely because the historical plugin had a capture page.

A bounded active experiment is a higher authority level than analysis/recommendation and remains subject to D-019 plus the tool's qualification maturity.

---

# 7. Ground layout

## Wide

```text
┌──────────────────────────────────────────────────────────────┐
│ Tuning Tools | source/session | profile | tool status        │
├──────────────────┬──────────────────────────────┬─────────────┤
│ tool browser     │ active tool workspace        │ evidence /  │
│ categories       │ guidance / plots / results   │ details /   │
│ maturity badges  │ capture / recommendation     │ provenance  │
└──────────────────┴──────────────────────────────┴─────────────┘
```

## Compact

- tool selector/cards first;
- active tool workspace occupies full width;
- evidence/details/provenance move to a bottom sheet or subpage;
- capture controls remain reachable without crowding the result surface.

The first UI implementation may use fixture content, but it must use the real final navigation/state architecture rather than a temporary throwaway page.

---

# 8. Tool card / capability contract

A tool descriptor should eventually expose typed metadata conceptually equivalent to:

```text
TuningToolDescriptor
- toolId
- displayName
- purpose
- maturity
- supported acquisition modes
- required channel contract
- profile/configuration prerequisites
- result kinds
- experiment capability
```

This is **not** a dynamic plugin manifest format. The production registry may be statically compiled/typed unless a future product requirement independently justifies an extension mechanism.

No JSON/plugin manifest loader is introduced merely to make the tools look extensible.

---

# 9. Diagnostics

Every Tuning Tool participates in the common diagnostic system.

At minimum diagnostics should be able to correlate:

- tool start/stop;
- source/session/profile identity;
- required/missing channels;
- sample rejection reasons/counts;
- capture/analysis phase transitions;
- analysis duration;
- result disposition/maturity;
- recommendation/proposal generation;
- stale-result rejection;
- bounded experiment handoff/result where applicable.

Tool diagnostics remain observational and use the shared diagnostic capture path.

---

# 10. Conversion rule from historical plugins

For each historical plugin-derived tool:

```text
review relevant source behavior under D-022
→ classify required/useful/buggy/obsolete behavior
→ extract the domain contract
→ map shared behavior onto common Rust analysis primitives
→ implement only domain-specific policy/model where needed
→ build Compose workflow on top of typed results
→ qualify replay/capture/recommendation separately
→ qualify active experiments separately if retained
```

Do not translate a Java/TunerStudio class tree mechanically into Rust modules.

The goal is to preserve proven knowledge while removing duplicated architecture and accumulated plugin-specific technical debt.

---

# 11. Initial shell acceptance

Before real ECU integration, the UI foundation is considered structurally ready for Tuning Tools when:

- the destination/page exists in the real application shell;
- AE/PID/TCharge/Injection Angle can be represented as tool cards/entries even if some are not yet implemented;
- unavailable/not-qualified states are explicit;
- tool pages can consume the same synthetic/shared channel projection used by other destinations;
- tool diagnostics can emit into the shared diagnostic system;
- no dynamic plugin runtime or second ECU/channel authority has been introduced;
- phone/tablet layouts provide a usable tool-selection/workspace pattern.

## Summary

> EpicHub keeps the useful capabilities discovered in the historical TunerStudio plugins, but not the plugin architecture. They become native **Tuning Tools**: Compose workflows over common Rust analysis/domain-profile infrastructure, with all ECU mutation remaining behind the single semantic tuning authority.