# EpicHub Runtime Language and Serialization Contract

## Purpose

EpicHub deliberately uses a small production-language surface:

- **Kotlin + Jetpack Compose** for Android-facing application/UI/platform work;
- **Rust** for the native engine, runtime channels, ECU/data/logging/analysis work and qualified semantic tuning.

This document prevents serialization formats or historical WebView/browser techniques from quietly becoming a third application architecture.

Authority: D-023 in `DECISIONS.md`.

---

# 1. Production runtime languages

Normal EpicHub product logic is implemented in Kotlin or Rust according to the established ownership boundaries.

Repository/build tooling may necessarily use declarative/build formats such as Gradle Kotlin DSL, Cargo TOML, GitHub Actions YAML, Android XML resources/manifests and external protocol/profile/file formats. Those are tooling/interoperability formats, not additional application runtimes.

EpicHub does **not** introduce a JavaScript runtime, browser application layer, WebView state engine or DOM-based application architecture.

Historical React/TypeScript/JavaScript/WebView implementations remain evidence only.

---

# 2. Runtime state is typed

In-memory application/domain state uses explicit Kotlin or Rust types.

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

Do not replace typed domain state with generic dictionaries/maps whose meaning is recovered from string keys at runtime unless the external protocol itself is inherently key/value based and the adapter immediately converts it into typed domain state.

---

# 3. No serialization message bus inside the application

The following are prohibited as ordinary internal runtime communication:

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

Serialization codecs do not sit in the RuntimeSnapshot hot path merely because they are convenient.

---

# 4. Serialization exists only at explicit boundaries

A serialized format is justified when bytes must outlive or leave the in-memory runtime model, or when an external ecosystem mandates a format.

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

A file format never becomes the internal runtime state model.

---

# 5. JSON policy

JSON is **not** an EpicHub default application format.

Prohibited uses include:

- runtime state authority;
- Kotlin↔Rust communication;
- ChannelStore values;
- Compose state transport;
- tune/profile authority caches merely for convenience;
- diagnostic event transport between domains;
- hidden internal message buses.

JSON may be used only when an explicit external/tooling interoperability requirement justifies it. Such use must be isolated to the relevant adapter/export tool and must not leak JSON-shaped state into core domains.

Historical files such as EpicDash-JZ `values.json`, WebView workspace JSON, Diagnostics-Core reference JSON and Tune Viewer JSON reports are **evidence of those systems**, not EpicHub target-format decisions.

Optional developer tooling may convert an authoritative EpicHub format to JSON for inspection. The converted JSON is a projection, never the canonical source.

---

# 6. Diagnostic capture format

EpicHub diagnostic runtime state remains typed Kotlin/Rust state and bounded packed/batched JNI data.

The canonical diagnostic capture export is a versioned binary CBOR document plus a human-readable text projection:

```text
EpicHub-Diagnostics-<captureId>.zip
├─ capture.cbor
├─ report.txt
└─ attachments/          # only explicitly selected artifacts
```

`capture.cbor` contains the complete canonical diagnostic capture schema, including as applicable:

- schema/version;
- capture/build/device identity;
- source/session/profile summaries;
- subsystem health snapshots;
- events;
- metrics;
- lifecycle owners/transitions;
- markers;
- user note/expected/actual behavior;
- attachment metadata and hashes.

`report.txt` is generated from the same immutable typed capture and is not a competing source of truth.

CBOR is used **only at the capture persistence/export boundary**. It is not the Kotlin↔Rust event transport and is not the live diagnostic ring representation.

If a developer or external tool later needs JSON/NDJSON, conversion happens explicitly from `capture.cbor` outside the runtime authority path.

---

# 7. Other persistence formats are not automatically CBOR

D-023 does not replace all storage formats with CBOR.

Each domain selects its explicit boundary format for its real requirements:

- MLG remains an interoperability format;
- MDF4 remains the long-term native measurement-format candidate;
- generated INI remains external profile authority where applicable;
- future project backup/journal formats are selected and versioned deliberately;
- CSV/MSL remain isolated interoperability formats where retained.

The rule is **typed runtime first, explicit serializer only at a justified boundary**.

---

# 8. Schema and compatibility requirements

Every persisted/exported internal EpicHub schema must define as applicable:

- schema/version;
- byte order/encoding;
- size/count bounds;
- required and optional fields;
- forward/backward compatibility policy;
- corruption/integrity behavior;
- source/profile/session identity where semantically required.

Unsupported versions fail explicitly. A decoder must never silently reinterpret a different schema generation.

---

# 9. Performance rule

A serialization format must not be inserted into a hot path without measurement proving it is necessary and acceptable.

For high-rate paths prefer:

```text
typed native structures
→ bounded/packed buffers
→ minimal copies
```

rather than:

```text
object graph
→ text/binary serialization
→ allocation/copy
→ parse
→ object graph
```

Diagnostics, logging and analysis measure their own serialization/export cost outside acquisition/UI-critical paths.

---

# 10. Review rule

Any proposal that introduces JSON, CBOR, protobuf, MessagePack, XML or another generic serializer must answer:

1. What explicit file/interoperability boundary requires serialization?
2. Why cannot typed Kotlin/Rust state or the existing packed ABI satisfy the runtime need?
3. Which component owns the codec?
4. Is the serialized representation authoritative, derived or interoperability-only?
5. What versioning/bounds/integrity rules apply?
6. Can the format accidentally leak upward and become domain state?

If there is no explicit boundary, the serializer does not belong in the production runtime architecture.

## Summary

> EpicHub application logic is Kotlin/Compose + Rust. Runtime state and cross-language communication remain typed/packed. Serialization is isolated to explicit persistence/export/interoperability boundaries. JSON is never the default internal representation, and the canonical diagnostic capture is versioned CBOR + human-readable text.