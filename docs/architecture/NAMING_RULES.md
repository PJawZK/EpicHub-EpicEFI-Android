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
- `ViewModel` — screen-facing orchestration/state owner.
- `UiState` — immutable Compose-facing state.

## Domain before transport

Name a concept after what it means, not merely how it is transported.

Prefer:

`Esp32AccessoryGateway`

instead of:

`BleButtonManager`

when the responsibility is accessory semantics.

Transport-specific classes should remain transport-specific, for example:

`AndroidBleScanner`, `Esp32GattClient`, `BleWriteScheduler`.

Likewise, USB is a transport and is not itself the ECU domain.

## One reason to change

A production file should have one primary reason to change. There is no rigid line-count limit, but files that grow beyond roughly 400–600 lines should trigger a responsibility review. Large parsers/protocol tables may be legitimate; large Activities/ViewModels/controllers owning unrelated systems generally are not.

Prefer small cohesive private support types inside the same file when they exist solely to implement that file's responsibility. Split when another responsibility, lifecycle, state authority or test boundary appears.

## Historical terminology

Historical names may appear in:

- source/reference citations;
- migration/responsibility mapping documents;
- tests specifically describing historical behaviour;
- Git history.

They should not dictate new production names.

If an old protocol is still the current required protocol, name its new implementation after its actual role rather than calling it `Legacy`. Use `Legacy` only when compatibility with obsolete data/devices is itself the reason the code exists.

## Rust conventions

Rust crate/module/file names use descriptive snake_case domain terms, for example:

- `channel_store.rs`;
- `frame_codec.rs`;
- `tune_snapshot.rs`;
- `write_transaction.rs`;
- `burn_verifier.rs`;
- `resolution_pyramid.rs`.

Avoid crates that merely collect unrelated helpers.

## Kotlin conventions

Kotlin package names follow domain ownership rather than historical Activities/classes. A likely root namespace is `com.epicefi.epichub`; the final application ID/namespace is locked before production scaffold creation.

Compose presentation types should not use transport/protocol terminology unless the UI is explicitly a diagnostics surface for that transport/protocol.
