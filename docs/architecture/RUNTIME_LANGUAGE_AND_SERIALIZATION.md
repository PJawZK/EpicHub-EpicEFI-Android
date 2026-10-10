# EpicHub Runtime Language and Serialization Contract

## Purpose

EpicHub deliberately uses a small **production** language surface:

- **Kotlin + Jetpack Compose** for Android-facing application/UI/platform work;
- **Rust** for the native engine, runtime channels, ECU/data/logging/analysis work and qualified semantic tuning.

This document prevents serialization formats or browser techniques from quietly becoming a third production application architecture.

Authority: D-023, refined for the first-class development prototype by D-026.

---

# 1. Production runtime languages

Normal EpicHub product logic is implemented in Kotlin or Rust according to the established ownership boundaries.

Repository/build/tooling may use Gradle Kotlin DSL, Cargo TOML, GitHub Actions YAML, Android XML and external protocol/profile/file formats. Those are tooling/interoperability formats, not additional production application runtimes.

EpicHub does **not** introduce a JavaScript runtime, browser application layer, WebView state engine or DOM-based architecture into the Android production runtime.

Historical React/TypeScript/JavaScript/WebView implementations remain evidence only.

---

# 2. GitHub Pages development surface

Under D-026, EpicHub deliberately maintains a modular TypeScript/HTML/CSS GitHub Pages product/interaction prototype under `prototype/`.

This is **development/specification code outside the Android production runtime**.

The Pages surface may be used to:

- prototype layouts and responsive presentation recipes;
- implement realistic interaction/workflow behavior;
- run fixture-backed feature demonstrations;
- test browser-side feature contracts;
- connect to explicit external tooling adapters such as a future TS-Shim.

An accepted Pages feature may become visual/interaction/workflow authority, but production implementation is translated into Compose/Kotlin/Rust.

Prototype code must not quietly become Android runtime code by:

- embedding it in a WebView;
- keeping its TypeScript/JavaScript state engine behind a Compose shell;
- routing normal Android runtime state through browser messages;
- treating DOM/CSS structure as production domain ownership;
- copying prototype-only shortcuts into production safety/state paths.

The correct relationship is:

```text
modular Pages feature
→ accepted visual + interaction + workflow contract
→ explicit native translation contract
→ Compose/Kotlin/Rust implementation
→ native/device qualification
```

This allowance does not weaken any D-023 production rule.

---

# 3. Runtime state is typed

In-memory production application/domain state uses explicit Kotlin or Rust types.

Examples:

```text
RuntimeSnapshot
DiagnosticEvent
MetricSnapshot
AnalysisSession
TuneSnapshot
TuneDraft
AppAction
BridgeRequestState
```

Do not replace typed production domain state with generic dictionaries/maps whose meaning is recovered from string keys at runtime unless an external protocol is inherently key/value based and its adapter immediately converts it into typed domain state.

The Pages prototype may define its own TypeScript semantic models/ports to keep feature code typed and modular. Those browser types are specification/development models; they do not become production authority automatically.

---

# 4. No serialization message bus inside the Android application

The following remain prohibited as ordinary production internal communication:

```text
Kotlin → JSON → Rust
Rust → JSON → Kotlin
Kotlin → CBOR → Rust
Rust → CBOR → Kotlin
JSON strings passed through JNI
generic serialized documents used as live ChannelStore/UI/tuning state
WebView/JavaScript bridge state
```

The Kotlin↔Rust boundary uses:

- semantic JNI operations;
- explicit primitive arguments for small operations;
- versioned packed binary ABI buffers for bounded/high-rate snapshots/results;
- typed bounded batches for external-source ingestion;
- bounded typed event/result drains.

Serialization codecs do not sit in RuntimeSnapshot or other hot paths merely because they are convenient.

---

# 5. Serialization exists only at explicit boundaries

A production serialized format is justified when bytes must outlive/leave the in-memory model or an external ecosystem mandates a format.

Examples:

```text
external generated INI/profile
MSL / CSV imports
MLG interoperability
MDF4 measurement storage
project/archive persistence once specified
diagnostic capture export
user-selected import/export
external-tool interoperability
```

Each boundary owns a deliberate codec/schema and converts immediately to/from typed domain structures.

A file format never becomes the internal runtime model.

---

# 6. JSON policy

JSON is **not** an EpicHub production default application format.

Prohibited production uses include:

- runtime state authority;
- Kotlin↔Rust communication;
- ChannelStore values;
- Compose state transport;
- tune/profile authority caches merely for convenience;
- diagnostic event transport between domains;
- hidden internal message buses.

JSON may be used when an explicit external/tooling interoperability requirement justifies it. Such use must be isolated to the relevant adapter/export surface.

This applies directly to the Pages prototype's future TS-Shim adapter: if the external shim contract uses JSON/WebSocket/HTTP, those messages live inside `prototype/src/adapters/ts-shim/` (or equivalent), are translated into typed prototype models immediately, and do not leak shim-shaped state into feature code.

Historical EpicDash-JZ/workspace JSON, Diagnostics-Core JSON and Tune Viewer JSON remain evidence of those systems rather than EpicHub production-format decisions.

Prototype fixtures may naturally use JavaScript/TypeScript objects or JSON-like data. They are development machinery only.

---

# 7. Diagnostic capture format

EpicHub diagnostic runtime state remains typed Kotlin/Rust state and bounded packed/batched JNI data.

The canonical diagnostic capture export is a versioned binary CBOR document plus a human-readable text projection:

```text
EpicHub-Diagnostics-<captureId>.zip
├─ capture.cbor
├─ report.txt
└─ attachments/
```

`capture.cbor` contains the complete canonical diagnostic capture schema as applicable, including schema/version, capture/build/device identity, source/session/profile summaries, health snapshots, events, metrics, lifecycle owners/transitions, markers, notes and attachment metadata/hashes.

`report.txt` is generated from the same immutable typed capture and is not a competing source of truth.

CBOR is used only at the capture persistence/export boundary. It is not Kotlin↔Rust transport and is not the live diagnostic ring representation.

---

# 8. Other persistence formats are not automatically CBOR

D-023 does not replace all storage formats with CBOR.

Each domain selects the explicit boundary format for its requirements:

- MLG remains an interoperability format;
- MDF4 remains the long-term native measurement-format candidate;
- generated INI remains external profile authority where applicable;
- future project backup/journal formats are selected/versioned deliberately;
- CSV/MSL remain isolated interoperability formats where retained.

The rule is **typed production runtime first, explicit serializer only at a justified boundary**.

---

# 9. Schema and compatibility requirements

Every persisted/exported internal EpicHub schema must define as applicable:

- schema/version;
- byte order/encoding;
- size/count bounds;
- required/optional fields;
- forward/backward compatibility policy;
- corruption/integrity behavior;
- source/profile/session identity where semantically required.

Unsupported versions fail explicitly.

External tooling adapters such as TS-Shim also require an explicit version/capability contract before the browser prototype relies on them.

---

# 10. Performance rule

A serializer must not be inserted into a production hot path without measurement proving it is necessary and acceptable.

For high-rate production paths prefer:

```text
typed native structures
→ bounded/packed buffers
→ minimal copies
```

Browser prototype performance is evaluated separately. It can guide prototype engineering but does not qualify the equivalent Android/Compose implementation.

---

# 11. Review rule

Any proposal introducing JSON, CBOR, protobuf, MessagePack, XML or another generic serializer into **production** code must answer:

1. What explicit file/interoperability boundary requires serialization?
2. Why cannot typed Kotlin/Rust state or the existing packed ABI satisfy the runtime need?
3. Which component owns the codec?
4. Is the representation authoritative, derived or interoperability-only?
5. What versioning/bounds/integrity rules apply?
6. Can it leak upward and become domain state?

Pages prototype adapters are reviewed differently:

1. Is this an explicit external/tooling boundary?
2. Does transport/wire parsing stay inside the adapter?
3. Are features consuming typed semantic ports rather than shim-shaped messages?
4. Is fixture/live/stale/disconnected authority clear?
5. Could the adapter accidentally grant browser UI unsafe mutation authority?

## Summary

> EpicHub production logic is Kotlin/Compose + Rust. The modular TypeScript/Vite GitHub Pages prototype is a first-class development/specification surface, not a third production runtime. Production state and JNI remain typed/packed; serializers stay at explicit persistence/export/interoperability boundaries, including any future TS-Shim adapter.
