# EpicHub State Ownership

## Purpose

EpicHub should have one clear mutable authority for each important state. Snapshots/projections may be copied for presentation/performance, but they must not silently become competing authorities.

## Android application state

Owner: Kotlin app/platform layer.

Examples:

- Activity/process foreground/background state;
- Android permission results;
- current user-selected navigation route;
- platform service attachment state.

Not owned here:

- ECU session truth;
- runtime channel values;
- tune image;
- MDF/log history.

## Android USB resource state

Owner: Kotlin `platform/usb` until/unless a selected native transport explicitly takes ownership of a duplicated/wrapped resource under a documented lifetime contract.

Examples:

- discovered `UsbDevice` facts;
- permission request/result;
- opened `UsbDeviceConnection` lifetime;
- claimed interfaces/endpoints;
- native FD handoff metadata if used.

ECU protocol/session authority remains Rust-side once the engine session begins.

## BLE/GATT connection state

Owner: Kotlin `Esp32GattClient`/Bluetooth platform domain.

Examples:

- scan/connecting/online/offline/retry state;
- active GATT object;
- discovered characteristics;
- current MTU/write scheduling state.

Accessory semantic state is separate.

## Accessory semantic state

Owner: Kotlin `AccessoryStateStore`.

Tracks:

- requested state/value;
- confirmation/acknowledgement state;
- last confirmed hardware-reported state where supported;
- pending/failure/unavailable state;
- correlation/timestamp information.

Physical relay/GPIO truth and failsafe ownership remain ESP32 firmware-side. The Android store is the app's best known semantic state, not an assertion that a relay is physically energized without feedback.

## Location state

Owner: Kotlin `AndroidLocationSource` publishes immutable current `LocationSample` values.

Consumers receive snapshots. Dashboard/accessory code must not independently mutate GPS truth.

## ECU connection/session state

Owner: one Rust ECU session.

Tracks at minimum:

- session/generation identity;
- transport attached/detached state;
- live firmware signature/recognition;
- accepted exact profile identity;
- streaming/preflight state;
- exclusive operation owner/state;
- disconnect invalidation;
- protocol health/errors relevant to session authority.

No second dashboard/tuner session owner is permitted.

## Profile state

Owner: Rust profile domain.

Authoritative source:

- exact imported generated INI bytes/content + fingerprint/signature identity.

Compiled schemas/caches are immutable derived projections tied to that exact source fingerprint.

No UI-owned or board-name-derived profile geometry may compete with this authority.

## Runtime channel state

Owner: Rust `ChannelStore`.

Tracks typed current observations including:

- value;
- source;
- timestamp;
- revision/sequence;
- validity/freshness;
- raw/physical representation where appropriate.

Dashboard, logger, analyzer, warnings and calculations consume projections/queries from this store or compatible file-backed measurement sources.

Compose receives immutable bounded display snapshots and never becomes high-rate channel authority.

## Tune state

Owner: Rust tuning domain once migrated/qualified.

Authoritative elements:

- current complete immutable verified `TuneSnapshot`;
- its exact profile/session/tune fingerprint identity;
- verified dirty-page state;
- mutation uncertainty state;
- explicit Burn transaction state.

Local UI draft/edit state may exist separately, but it must be visibly distinct from verified ECU RAM state.

A requested UI value is not ECU state until the complete write verification contract succeeds.

## Tune project persistence

Authoritative persisted project tuple:

- exact generated profile/INI;
- matching complete verified TuneSnapshot;
- version/fingerprint metadata required to prevent incompatible interpretation.

Compiled workspace/profile projections are caches only.

The new app may choose a different storage schema/path from EpicDash-JZ, but must preserve this authority principle.

## Logging session state

Owner: Rust logging domain.

Tracks:

- recording session identity;
- selected channels/config;
- start/stop state;
- writer state;
- samples/events received/written/dropped;
- file/recovery state;
- current byte count/duration/throughput.

Logger Compose state is a projection only.

## MDF/file state

Owner: Rust MDF/measurement writer or reader instance for each opened file.

Sidecar indexes/caches are derived/rebuildable unless explicitly documented otherwise.

A corrupt/stale sidecar must never override the measurement file's validated source data.

## Analyzer state

Heavy data/caches/query execution: Rust analysis domain.

Presentation interaction state such as currently selected channels, cursor position, viewport and open panels: Kotlin Compose/ViewModel.

The boundary should send semantic query/viewport requests and receive bounded results. Kotlin must not duplicate the entire log dataset.

## Dashboard layout/edit state

Owner for user-editable presentation layout: Kotlin dashboard/editor domain, persisted via an explicit versioned layout schema.

Channel values referenced by bindings remain Rust ChannelStore-owned.

Compiled/render-optimized layout projections, if introduced, are derived from the editable layout and may be rebuilt.

## Settings state

Owner: a future typed Kotlin settings store/repository to be specified before Phase 2.

Settings must not duplicate authoritative ECU/profile/tune values. A preference may affect presentation or requested behavior, but does not replace live/profile authority.

## Diagnostics state

Engine diagnostic event history: bounded Rust diagnostic event ring.

Android/platform diagnostic state: Kotlin platform diagnostics.

The Diagnostics UI combines projections but does not mutate subsystem authority.

## State vs event rule

Persistent/current facts use state/snapshot models.

One-time occurrences use events.

Examples:

- `RPM=3200` — state;
- `ESP32=ONLINE` — state;
- `TuneWriteFailed` — event;
- `AccessoryCommandAcknowledged` — event;
- `LoggerStarted` — event.

Events must not accidentally become a second mutable state model through callback accumulation.
