# EpicHub Naming Rules

## Goal

Names describe permanent responsibility and domain meaning, not project history or the file from which behaviour originated.

## Prohibited permanent naming patterns

Do not use implementation-stage/version labels as accepted production names merely to distinguish development generations:

- `T3`, `T4`, `T6`, `B2`, `W4`, etc.;
- `V2`, `V3`, `New`, `Old`, `Next`;
- historical project prefixes where they do not describe the current domain.

Temporary experiment names such as `UsbTransportT2` are permitted only while explicitly comparing alternatives. The accepted implementation is renamed descriptively before integration.

Avoid vague dumping-ground names such as:

- `Utils`;
- `Helpers`;
- `Misc`;
- `Common` as a generic catch-all;
- `DataManager`;
- `SystemManager`;
- `EpicHubManager`;
- `DataHub`.

`Manager` is not absolutely forbidden, but should be used only when management/orchestration is genuinely the single responsibility and no more precise domain term exists.

## Preferred responsibility suffixes

- `Source` — produces data.
- `Store` — owns mutable/current state.
- `Reader` — reads a format/device/resource without owning its lifecycle broadly.
- `Writer` — writes a format/resource.
- `Codec` — transforms between representations.
- `Client` — communicates with an external endpoint/service/device protocol.
- `Gateway` — adapts one domain into another.
- `Policy` — pure decisions/rules.
- `Registry` — indexed stable definitions.
- `Router` — directs commands/actions to owners/destinations.
- `Session` — lifetime-bounded operation/context.
- `Transaction` — guarded operation with explicit start/success/failure semantics.
- `Snapshot` — immutable point-in-time state.
- `Plan` — immutable description of intended work, not execution.
- `Verifier` — validates observed result against an expected contract.
- `Screen` — Compose screen/presentation surface.
- `ViewModel` — screen-facing presentation orchestration/state owner.
- `UiState` — immutable Compose-facing state.

## Domain before transport

Name a concept after what it means, not merely how it is transported.

For the reviewed existing ESP32 BLE↔CAN bridge, prefer:

`Esp32BridgeGateway`

instead of:

`BleButtonManager`

or an over-broad `Esp32AccessoryGateway` that could blur legacy bridge semantics with a future direct-output protocol.

If a future versioned relay/GPIO capability is added, give it a separate semantic owner such as:

`Esp32DirectOutputGateway`

Transport-specific classes remain transport-specific, for example:

`AndroidBleScanner`, `Esp32GattClient`, `BleWriteScheduler`.

Likewise, USB is a transport and is not itself the ECU domain.

## One reason to change

A production file should have one primary reason to change. There is no rigid line-count limit, but files that grow beyond roughly 400–600 lines should trigger a responsibility review. Large parsers/protocol tables may be legitimate; large Activities/ViewModels/controllers owning unrelated systems generally are not.

Prefer small cohesive private support types inside the same file when they exist solely to implement that file's responsibility. Split when another responsibility, lifecycle, state authority or test boundary appears.

## Historical / compatibility terminology

Historical names may appear in:

- source/reference citations;
- responsibility mapping documents;
- tests specifically describing historical behaviour;
- Git history.

They should not dictate new production names.

`Legacy` is allowed in production only when distinguishing a concrete compatibility protocol/data/device generation is part of the responsibility itself. For example, `LegacyButtonBridgeCodec` is acceptable because it explicitly separates the existing bridge wire protocol from any future versioned direct-output protocol. Do not use `Legacy` merely to mean “old implementation we have not cleaned up.”

## Rust conventions

Rust crate/module/file names use descriptive snake_case domain terms, for example:

- `channel_store.rs`;
- `request_codec.rs`;
- `tune_snapshot.rs`;
- `write_transaction.rs`;
- `burn_verifier.rs`;
- `resolution_pyramid.rs`.

Avoid crates that merely collect unrelated helpers.

## Kotlin conventions

The locked Android application ID and Kotlin root namespace are:

`com.epicefi.epichub`

Kotlin package names follow domain ownership rather than historical Activities/classes.

Compose presentation types should not use transport/protocol terminology unless the UI is explicitly a diagnostics surface for that transport/protocol.