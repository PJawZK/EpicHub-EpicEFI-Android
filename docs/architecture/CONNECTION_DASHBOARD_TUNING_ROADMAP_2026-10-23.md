# EpicHub Connection, Dashboard and Full-Tuning Roadmap

Audit date: 2026-10-23. Source branch: `current-work` at `947443320e0486fd19cbdc1beda3520d858192ed` (production tree identical to qualified `main` `9c5e332b737085e93ab84531199909b9d5b5c9a6`). This is an audit/planning record, **not** implementation qualification. Preserve the exact-head Foundation policy in `docs/HANDOFF.md`.

## Product direction (user-approved)

EpicHub focuses on **ECU connection, live gauges/dashboard and complete ECU tuning**. EpicScope is the mature dedicated Analyzer; do not build a competing standalone Analyzer in EpicHub. Preserve optional future interoperability (channel/source identities, tune fingerprints, exported recordings) without adding an integration dependency now. Logging may serve capture/interoperability but must not displace connection/gauges/tuning. Keep old structural routes only as compatibility/navigation placeholders pending a separately qualified UI decision.

## Verified source inventory and gaps

| Area | Existing source/evidence | Observed status | Missing proof/functionality |
| --- | --- | --- | --- |
| USB discovery/permissions | `platform/transport/usb/AndroidUsbCandidateDiscovery.kt`, `AndroidUsbPermissionRequestOwner.kt`, `SettingsScreen.kt` | Code present with explicit user flow | Hardware hotplug/reconnect/per-device qualification |
| USB connection | `AndroidUsbLiveConnectionSession.kt`, `LiveTuneConnectionOwner.kt` | Explicit retained USB channel, definition-query probing, exact selection | Multiple ECU transports and families physically qualified; continuous runtime session |
| Profile definition import | `DefinitionBundlePackage.kt`, `DefinitionDrivenRuntimeProbePlan.kt`, `NativeProfileRegistry.kt` | Definition/profile driven, no board-name selection authority | Generated-INI coverage matrix and full setting/runtime interpretation |
| Live tune acquisition | `UsbReadOnlyTuneLoader.kt`, `NativeLiveTuneInstaller.kt`, `LiveTuneSourceLifecycle.kt` | Definition-authorized read-only tune pages, semantic install | Distinguish clearly from streaming O/runtime channels |
| Runtime projection | `engine/RuntimeSnapshot.kt`, `EpicHubApp.kt` | ABI v1, 96 bytes, maximum 4 channels; synthetic runtime source used in UI | Definition-derived runtime channel catalog, scalable batched acquisition/decoding/store, display-rate projection, reconnect/stale handling |
| Dashboard | `ui/dashboard/DashboardScreen.kt` | Explicit structural placeholder with synthetic text channels | Native gauge library, layout/editor/persistence, units, warnings, low-latency real live channel display, four presentation classes |
| 2D/3D Tuner | `ui/tuner/TunerScreen.kt`, `engine/TunerTableCatalog.kt`, `TunerTableSelectionOwner.kt` | Qualified native multi-table editing, shared history; session epoch presentation refresh | Interaction parity checklist against accepted Table Interaction Lab; physical usability verification |
| Full ECU configuration | `docs/architecture/ECU_DEFINITION_COMPILER.md`, `ECU_CALIBRATION_COMPILER.md` and native Rust source | Compiled normalized calibration primitives and table/curve descriptors described; table UI exists | Full scalars, bitfields, enums, conditions, dialogs, curves, complete settings UI and read/write semantics; test per actual INI |
| RAM writing | `NativeGuardedWriteBatch.kt`, `UsbGuardedRamWriteExecutor.kt`, `RamSynchronizationState.kt` | Qualified separate guarded volatile Write-to-RAM and retained provenance | Real ECU hardware/fault qualification; project backups/uncertainty journal |
| Burn | `NativeGuardedBurn.kt`, `UsbGuardedBurnExecutor.kt`, `LiveTuneBurn.kt` | Qualified physical USB Burn ACK then full-page CRC routing, not UI exposed | **First correctness work:** native proof-state order enforcement; then page-scoped nonvolatile provenance, UI and hardware validation |
| Logger | `ui/logger/LoggerScreen.kt` | Explicit structural placeholder, no attached writer | Optional later reliable interoperable recording; not a standalone Analyzer |
| Analyzer | `ui/analyzer/AnalyzerScreen.kt` | Placeholder | **Deprioritized:** no competing Analyzer implementation; EpicScope remains analysis product |

## Dependency-ordered implementation and acceptance checklist

### 0. Repository qualification and factual baseline
- Verify exact current-work Foundation run; advance `main` only to **the exact passing SHA** via branch-ref update, not file commit.
- Reconcile stale `README.md`, `docs/architecture/GAP_ANALYSIS.md` and `docs/architecture/README.md` phase/PR status (old PR #3 and early RAM stages are no longer active).
- Record automated qualification **separately** from device-specific physical tests, compatibility fixtures and product completion.
- No production changes in this documentation audit.

### 1. Preserve the active Burn correctness gate
- Native opaque plan state machine: `Planned -> BurnAckVerified -> PostBurnCrcVerified`, one-shot success and out-of-order/retry/close/stale/race tests.
- Native, page-specific nonvolatile provenance; unknown remains unknown after live RAM acquisition.
- Only then design explicit user Burn workflow and physical failure verification. Never combine Apply, Write RAM and Burn.

### 2. Complete generic ECU connection/runtime authority
- Build actual generated INI corpus: Mega144H7, uaEFI, additional distinct ECU firmware/generations. Confirm queries/envelope/signature/response/status/page commands, unsupported capability reasons and malformed variants.
- Trace current exact-signature selection and package import into runtime channel definitions: channel offsets/types/scaling/units/validity, channel count and sampling capabilities. Do not hardcode Mega144H7.
- Implement format-neutral sustained runtime acquisition/decoding/timebase/channel-store, bounded JNI snapshot batches (not per channel/sample), source/session generation, stale/timeout/reconnect/permission revocation behavior.
- Benchmark latency, throughput, dropping, CPU/allocation, thermal, unplug/replug and concurrent tuner operations on SM-A137F and SM-T500.
- Use one verified ECU session and one semantic channel authority shared by Dashboard and Tuner.

### 3. Working native gauges/dashboard
- Definition-derived, stable channel registry and units; display gauges with validity, stale/unknown and disconnect status; no synthetic production presentation.
- Gauge layout model, persisted multi-page user layouts, responsive phone/tablet portrait/landscape recipes, performance under sustained telemetry.
- Define user-configurable visual warning rules separately from diagnostics observational authority.
- Confirm on physical devices before treating as product-complete.

### 4. Complete tuning controls
- For a representative generated EpicEFI INI, inventory every exposed `scalar`, `array`, `bits`, `enum`, `curve`, `table`, `dialog`, visibility/condition and page command. Mark `implemented`, `unsupported by definition`, `not implemented` or `validated`; never infer completeness from compiled primitives.
- Extend native semantic catalog and Compose controls for non-table settings and curves with exact profile authority, constraints, quantization, selection and Undo/Redo.
- Keep existing 2D/3D table editor and source-aware lifecycle; compare approved Table Interaction Lab interaction inventory before any redo.
- Bind all mutations through the single guarded native authority. Validate RAM and persistence paths separately per supported setting and page.

### 5. Standalone tune management and completeness
- Versioned project/tune store, import/export, backup, exact-profile compare, reconnect reconciliation, error/uncertainty journal and process-death recovery.
- Validate alternate ECU profiles, firmware changes, offline mode and device rotation/recreation.
- Supporting file logging/export interoperability may follow the common runtime acquisition path; postpone full Analyzer. Optional future EpicScope integration is separately planned, not an EpicHub blocker.

## Gate evidence format

For each capability record: **source path / implementation state / exact test-run SHA and ID / physical device & firmware / observed behavior / limitations / next action**. Do not conflate code-present, automated-green, physically-qualified, and complete. `docs/HANDOFF.md` remains the single active implementation continuation authority; this file is a product-priority and gap audit, not a competing handoff.
