# EpicHub State Ownership

## Purpose

EpicHub has one clear mutable authority for each important state. Snapshots/projections/caches may be copied for presentation/performance, but they never silently become competing authorities.

D-023 applies across all state below: live/current state is typed Kotlin/Rust state. A serialized file representation exists only at an explicit persistence/export/interoperability boundary and never becomes a second runtime authority.

---

## Android application state

Owner: Kotlin app/platform layer.

Examples:

- Activity/process foreground/background state;
- Android permission results;
- current navigation route;
- platform service attachment state.

Not owned here: ECU session truth, runtime channels, tune image, logs.

## Android USB resource state

Owner: Kotlin `platform/usb` until/unless a selected native transport explicitly takes ownership of a duplicated/wrapped resource under a documented lifetime contract.

Examples:

- discovered device facts;
- permission request/result;
- opened `UsbDeviceConnection` lifetime;
- claimed interfaces/endpoints;
- native FD/handle handoff metadata if used.

ECU protocol/session authority is separate.

## BLE/GATT connection state

Owner: Kotlin `Esp32GattClient` + one connection-wide BLE operation scheduler.

Tracks:

- scan/connecting/online/offline/retry state;
- active GATT object;
- discovered legacy/versioned characteristics;
- MTU/priority state;
- one ordered GATT operation queue/in-flight operation.

This state says what Android BLE transport is doing, not what a downstream CAN consumer or physical output has done.

## Legacy ESP32 bridge semantic state

Owner: Kotlin `Esp32BridgeGateway`/bridge state model.

Tracks where useful:

- current requested/last-sent button mask;
- bridge variable requests and correlation/timeouts/partial results;
- transport completion/failure;
- bridge availability;
- downstream state as `unknown` unless independently observed.

A successful GATT write is not a downstream CAN/action acknowledgement.

The reviewed original bridge does not expose confirmed physical relay/GPIO output state because it is not a direct-output controller.

## Future direct ESP32 output state

Owner only if a versioned direct-output capability is deliberately added: Kotlin direct-output semantic state store/gateway backed by explicit protocol acknowledgement/state feedback.

Tracks:

- requested output state;
- pending/accepted/rejected request;
- confirmed hardware-reported state where protocol provides it;
- hardware fault/unavailable state.

Physical output truth, boot/default state, interlocks and disconnect/watchdog fail-safe remain ESP32 firmware authority.

No direct-output state is inferred from legacy button bridge traffic.

## Location state

Owner: Kotlin `AndroidLocationSource` publishes immutable `LocationSample` values.

Consumers receive snapshots. GPS-specific encoders may adapt those samples to the generic legacy variable-set bridge but do not become location authority.

---

## ECU connection/session state

Owner: one Rust ECU session.

Tracks at minimum:

- session/generation identity;
- attached/detached state after transport handoff;
- live firmware signature/recognition;
- accepted exact profile identity;
- synchronization/streaming state;
- exclusive operation owner/state;
- disconnect invalidation;
- protocol health relevant to authority.

No second dashboard/tuner session owner is permitted.

## Profile state

Owner: Rust profile domain.

Authoritative source:

- exact imported generated INI bytes/content;
- its exact source fingerprint/signature identity.

Signature equality is the live compatibility gate; the exact source fingerprint identifies the selected profile source used for compiled schemas/caches/project/tuning state.

Compiled schemas/caches are immutable derived projections tied to that source identity.

No UI-owned or board-name-derived geometry competes with this authority.

## Runtime channel state

Owner: Rust `ChannelStore`.

Tracks typed observations including:

- value/raw representation where appropriate;
- source;
- timestamp;
- revision/sequence;
- validity/freshness.

Dashboard, logger, analyzer, warnings and calculations consume snapshots/queries. Compose never becomes high-rate channel authority.

The accepted Phase-2 foundation already establishes a minimal engine-owned Rust ChannelStore and packed RuntimeSnapshot projection.

## Live tune state

Owner: Rust tuning domain once migrated/qualified.

Authoritative elements:

- current complete immutable verified `TuneSnapshot`;
- exact ECU/profile/session/tune identity;
- verified dirty-page state;
- mutation uncertainty state;
- explicit Burn transaction state.

A requested UI value is not ECU RAM state until the write verification contract succeeds.

---

# Tuning persistence categories

These categories are deliberately independent.

## 1. Persisted project authority

Owner: Rust tuning/project domain.

Authoritative persisted tuple:

```text
exact generated INI/profile source
+ last complete verified TuneSnapshot
+ version/identity metadata required to validate the tuple
```

The app may use a different storage format/path from EpicDash-JZ, but this authority principle is permanent. The eventual storage codec must be selected deliberately under D-023 rather than defaulting to JSON.

A project with only a valid INI is definition-only. A saved snapshot is accepted only when it belongs to that exact profile identity.

## 2. Derived tuning acceleration

Owner: Rust profile/tuning cache layer.

Examples:

- compiled profile/schema;
- semantic tuner projection cache;
- decoded/optimized lookup structures.

Rules:

- exact source/profile fingerprint-bound;
- disposable/rebuildable;
- stale async work cannot install a cache for a newer project generation;
- never an alternate authority.

Historical giant WebView/bootstrap JSON caches do not carry forward.

## 3. User recovery / portable backup

Authority: no independent tune authority. It is a portable representation of the project authority tuple.

Schema/integrity owner: Rust project backup codec.

Android URI/picker/export/import transport owner: Kotlin storage/document layer.

Restoring a backup must:

1. validate archive/schema/size;
2. parse/validate exact profile source;
3. validate optional complete TuneSnapshot against that profile;
4. transactionally commit a new local project-authority generation;
5. roll back on incomplete commit/verification.

Selecting/importing a backup never grants live ECU mutation authority.

The concrete archive representation remains a feature-entry decision governed by D-023.

## 4. Tuning operation uncertainty journal

Owner: Rust tuning transaction/recovery domain.

Purpose: record durably that an ECU-changing operation may have crossed the point of possible transmission and final hardware state is not yet proven.

This journal is **not** a TuneSnapshot and is **not** project authority.

Required lifecycle:

```text
verified operation plan
→ durable journal write + fsync + reread
→ possible C/B transmission permitted
→ ACK/readback/full snapshot or Burn evidence
→ final state proven
→ durable journal clear
```

If the process dies/crashes/disconnects after marker durability but before proven clear, the next session is recovery-required.

Present or corrupt journal blocks new mutation until deliberate resynchronization. No automatic replay.

Journal identity should bind at least:

- operation ID/kind/phase;
- ECU signature/profile fingerprint;
- source session/generation;
- baseline tune fingerprint;
- expected tune fingerprint;
- write-plan fingerprint/affected pages;
- Burn request/status identity where relevant.

Historical scalar-pilot raw-byte fields and forced restore/power-cycle sequences are proof machinery, not permanent UX requirements.

### Project snapshot vs journal rule

A clean saved TuneSnapshot does not silently clear an uncertainty journal. Conversely, a journal does not replace the saved TuneSnapshot.

After uncertainty, a fresh live complete read/status verification establishes what the ECU actually contains before any new write/Burn.

## 5. Semantic tune draft state

Owner: Rust tuning domain.

This is the authoritative local unverified semantic edit set when EpicHub supports staged tune edits, calculations, batch table operations or semantic undo/redo. It is explicitly distinct from:

- saved project snapshot;
- verified live ECU RAM;
- operation journal.

A semantic draft may be derived from a verified/saved baseline, but it never becomes ECU or project authority until the corresponding explicit verification/persistence flow succeeds.

Whether semantic drafts survive process death is a product decision; persistence of a draft must never promote it to verified tune authority.

## 6. Tuner presentation/edit interaction state

Owner: Kotlin Tuner ViewModel/Compose.

Examples:

- selected target/cell/range;
- focused text field and in-progress textual input before semantic validation;
- zoom/pan/panel/camera state;
- dialog/modal state;
- transient gesture state.

Kotlin submits semantic edit intent to Rust; it does not retain a second authoritative semantic tune draft/history.

---

## Logging session state

Owner: Rust logging domain.

Tracks session identity, configuration, writer state, samples/events received/written/dropped, file recovery state, byte count/duration/throughput.

Logger Compose state is a projection only.

## Measurement-file state

Owner: the Rust measurement/source reader or selected `MeasurementWriter` for each opened/active file.

Format-specific owners such as MLG interop or MDF may hold their own validated format state. Sidecar indexes/caches are derived/rebuildable unless explicitly documented otherwise. Stale/corrupt sidecars never override validated measurement data.

No measurement-file reader/writer becomes ChannelStore or logging-session authority.

## Analyzer state

Heavy dataset/query/cache state: Rust analysis domain.

Presentation interaction state—selected channels, viewport, cursor, open panels: Kotlin ViewModel/Compose.

Kotlin never duplicates the entire log dataset.

## Dashboard layout/edit state

Owner: Kotlin dashboard/editor domain, persisted through an explicit versioned layout schema.

Bound runtime channel values remain Rust ChannelStore-owned.

The persisted layout schema is a deliberate UI persistence boundary; its eventual encoding must follow D-023 and cannot become runtime ChannelStore state.

## Settings state

Owner: Kotlin `SettingsStore` (typed application/user preferences).

The concrete Android persistence backend is an implementation choice, but ownership is locked. Preferences cannot duplicate authoritative ECU/profile/tune values or become a hidden domain-state store.

## Diagnostics live state

Rust engine diagnostic event/metric history: bounded Rust diagnostics.

Android/platform diagnostic event/metric history: bounded Kotlin diagnostics.

Lifecycle owner/generation records remain with the diagnostic layer that observes those owners.

Diagnostics UI combines immutable projections only and never reconstructs subsystem authority from log strings.

Rust→Kotlin diagnostic transfer uses bounded typed/packed batches; neither side stores live diagnostics as JSON/CBOR documents.

## Diagnostic capture state

Owner: Kotlin diagnostic capture coordinator for capture lifecycle/assembly because the finalized capture combines Android/platform facts with bounded Rust diagnostic projections and ultimately uses Android document/export facilities.

Inputs remain typed projections from their actual owners. The coordinator does not become ECU/ChannelStore/tuning authority.

Capture lifecycle:

```text
Idle
→ recent-problem or reproduction capture staging
→ bounded pre/post-trigger evidence assembled
→ immutable DiagnosticCapture finalized
→ optional persistence/export
```

A finalized `DiagnosticCapture` is immutable. Later live events do not mutate it.

The canonical persisted/exported representation is versioned `capture.cbor` plus generated `report.txt`; that serialized representation is produced only at the persistence/export boundary and does not replace the typed capture model in memory.

Explicit optional attachments remain separate artifacts and do not silently enter the capture.

---

# State vs event rule

Persistent/current facts use state/snapshot models. One-time occurrences use events.

Examples:

- `RPM=3200` — state;
- `ESP32 bridge ONLINE` — state;
- `TuneWriteFailed` — event;
- `BleWriteCompleted` — transport event;
- `DirectOutputConfirmed` — event only if a versioned protocol actually supplied confirmation;
- `LoggerStarted` — event.

Events must not become a second mutable state model through callback accumulation or generic serialized-document accumulation.