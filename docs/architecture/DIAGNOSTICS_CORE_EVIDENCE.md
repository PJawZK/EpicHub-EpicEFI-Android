# Diagnostics-Core Evidence

## Purpose

This document records the Phase 1 review of `PJawZK/Diagnostics-Core` and clarifies how its cross-project diagnostic definitions relate to EpicHub's already-established ChannelStore, warning and diagnostics architecture.

It supplements `DIAGNOSTICS_EVIDENCE.md`; it does not replace D-017 or create an external runtime authority.

## Reviewed source state

Repository: `PJawZK/Diagnostics-Core`

Reviewed `main`: `8b497461ebcdef70e9cc0a406e7b987988df7aa3`.

High-value sources:

- `README.md`;
- `docs/ARCHITECTURE.md`;
- `docs/CONSUMER_INTEGRATION.md`;
- `definitions/core.json`;
- `adapters/core.json`;
- `schemas/core.schema.json`;
- `test-vectors/core.json`;
- `diagnostics_core.py` as reference implementation/tooling.

The repository also contains substantial KiCad/container tooling. That tooling is useful to its broader shared-project purpose but is not Android/EpicHub runtime architecture.

---

# 1. Canonical identity + adapters is useful evidence

Diagnostics-Core separates:

1. canonical channel identity, units and validity;
2. warning rules;
3. diagnostic tests;
4. consumer/protocol aliases/adapters;
5. tools/test vectors.

Its intended flow is:

```text
consumer source
→ adapter
→ canonical channel sample
→ validity / warning / diagnostic profile
→ findings/events/UI
```

This is compatible with EpicHub's one-ChannelStore rule **if treated as a design/reference pattern**, not as a second live store or a competing channel schema loaded beside the exact ECU profile.

### EpicHub consequence

`epichub-channels` should use stable semantic channel IDs and explicit source aliases/bindings. File-format/TunerStudio/EpicDash names belong at adapters/import boundaries rather than becoming the runtime channel identity.

The exact connected generated profile remains authority for which ECU channels actually exist, their raw location/scaling and live source semantics. A canonical EpicHub semantic ID may bind to that authoritative source definition; it does not override it.

---

# 2. Validity is part of channel semantics

The reviewed definitions carry per-channel concepts such as:

- data type;
- units;
- plausible/valid range;
- stale timeout;
- missing-data policy;
- external aliases.

This reinforces existing EpicHub analysis/diagnostic findings: missing/stale data cannot be silently converted to zero.

### EpicHub consequence

A runtime semantic channel schema should be able to represent:

```text
identity
value type
unit
source/profile binding
freshness timestamp
validity state
optional plausible-range metadata
```

However, generic plausible ranges or stale timings from Diagnostics-Core are **not automatically authoritative defaults** for every ECU/vehicle. They require EpicHub-specific review/profile/product policy before adoption.

---

# 3. Warning definitions are declarative evidence, not hidden control policy

Diagnostics-Core warning entries demonstrate a useful declarative form:

```text
warning id
severity
dependent channel conditions
debounce
clear debounce
latching
recommended action text
```

Examples include battery, trigger sync, fuel-pressure and boost-related warnings.

Some `recommended_action` prose suggests actions such as avoiding writes or shutting down. Under EpicHub D-017, that text is **advice/presentation only** unless a separate production safety domain explicitly implements and qualifies such a policy.

### EpicHub consequence

A warning engine can consume semantic ChannelStore values and produce warning incidents, but:

- the rule does not own transport/tuning/logger state transitions;
- warning acknowledgement does not change channel truth;
- a warning's human recommended action does not execute automatically;
- any safety interlock that reacts automatically is a separate named domain policy with its own tests/authority.

This prevents a JSON warning definition from becoming an accidental ECU-write/reconnect/shutdown controller.

---

# 4. Versioned definitions and test vectors are valuable

Diagnostics-Core recommends pinning a release/commit and exporting that identity in support bundles. It keeps definitions machine-readable and provides executable test vectors.

This is strong evidence for versioning diagnostic semantics independently from presentation.

### EpicHub consequence

Where EpicHub has persisted/declarative warning or canonical-semantic definitions, they should be:

- schema-versioned;
- tied to app/build/profile identity as appropriate;
- validated at build/load time;
- covered by deterministic vectors;
- included by version/fingerprint in diagnostic reports.

Do not require runtime network access to Diagnostics-Core.

---

# 5. Consumer adapters remain boundary code

Diagnostics-Core deliberately isolates consumer-specific names in adapters.

For EpicHub, equivalent adapter responsibilities include:

- generated ECU output-channel name → semantic ChannelId;
- MSL/MLG/CSV external name → semantic ChannelId;
- legacy ESP32 variable hash/source → semantic ChannelId;
- Android location field → semantic ChannelId.

### EpicHub consequence

Alias resolution belongs at source/import integration boundaries. Analysis profiles, warnings, dashboards and expressions should prefer semantic IDs rather than each maintaining their own TunerStudio-name alias lists.

Unresolved or ambiguous mappings fail explicitly or remain unavailable; they do not guess a semantically similar channel merely by label.

---

# 6. Diagnostics-Core does not replace EpicHub diagnostics ownership

Diagnostics-Core is designed for many consumers: EpicDash-JZ, EpicTuner, Pi, ESP32, log tools and future software. EpicHub has a more specific integrated architecture.

Therefore the following remain authoritative in EpicHub:

- Rust `ChannelStore` for current runtime channel state;
- each production domain for its own state transitions;
- structured Kotlin/Rust diagnostic event/metric contracts from `DIAGNOSTICS_EVIDENCE.md`;
- warning/incident projection as observational state;
- exact ECU generated profile for live channel/storage semantics;
- EpicHub's tuning safety authority for write/Burn decisions.

Diagnostics-Core may seed naming, definition/test-vector ideas or generated build artifacts after review. It is not a runtime dependency requirement and does not gain control authority.

---

# 7. Provenance / licensing treatment

Diagnostics-Core is a private `PJawZK` project and is treated as user-controlled project reference material for this architecture effort.

EpicHub Phase 1 still follows the broader clean-slate rule: transfer semantics, tested behavior and definitions deliberately; do not mechanically copy repository structure or hidden tool assumptions.

Third-party/material code embedded in any reference repository remains subject to its own headers/licence. The new EpicHub implementation must keep dependency/source licensing explicit at the actual code-adoption point.

---

# Target responsibility assignments

| Diagnostics-Core evidence | EpicHub target owner |
|---|---|
| stable semantic channel IDs | `epichub-channels` |
| source/format aliases | source adapters / `epichub-interop` / profile binding |
| freshness/validity | `epichub-channels` + source owner |
| warning rule evaluation | Rust warning/diagnostic projection consuming ChannelStore |
| warning incident presentation | Compose diagnostics/dashboard UI |
| automatic safety interlock | **not** warning engine; explicit production domain policy only |
| deterministic warning vectors | cross-system fixture/test corpus |
| diagnostic definition/report identity | diagnostics schema/report |
| KiCad/container tooling | out of EpicHub Android runtime scope |

## Phase 1 conclusion

Diagnostics-Core closes the outstanding shared-diagnostics reference review without changing the established architecture.

Its strongest transferable lesson is:

> Stable semantic channel IDs, explicit validity, declarative warning rules, adapters and executable vectors are useful shared contracts—but definitions remain observational and cannot become a second ChannelStore, generated-profile authority or hidden safety controller.