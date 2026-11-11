# TS-Optim → EpicHub: evidence-led architecture comparison and backlog

Date: 2026-11-11  
EpicHub source revision reviewed: `5d437d816c707868c272bf4b516e7e1fd5316eec` (`current-work`, matching `main` when reviewed).  
EpicHub exact-head Foundation: [40697697432](https://github.com/PJawZK/EpicHub-EpicEFI-Android/actions/runs/40697697432), **success**. This verifies the *prior* code baseline, **not this new documentation commit**.  
TS-Optim research branch: `PJawZK/TS-Optim@setup/epicwolf-rea-foundation`; original TunerStudio 3.3.01 main-JAR SHA-256 `3ebafb867fef3722816e768879501845904f81bc5e13249c77d170e9adf61cf8`.

## Purpose and ground rules

Use actual TunerStudio behavior as a **compatibility oracle and source of test questions**, not as production source code or a mandate to recreate internal design. TS-Optim supplies static structural/bytecode findings and limited user-run diagnostics. The following is a review of current checked-in EpicHub sources, **not** a run of EpicHub on an Android device, a proved TunerStudio runtime bug, or a validated feature-equivalence comparison.

EpicHub remains **Connect · Monitor · Tune**; EpicScope owns deeper Analyzer workflows. Kotlin/Jetpack Compose owns Android UI and USB lifecycle; Rust owns tuning semantics, INI/profile authority, readback and source reconciliation. Keep the one-USB-owner, one semantic authority, and `TuneDraft Apply != ECU RAM write != Burn` invariants.

## Evidence comparison

| Area | TS-Optim finding and confidence | EpicHub implementation reviewed | Disposition |
| --- | --- | --- | --- |
| Serial connect/failure | Original `aD.a.r():boolean` calls `aD.a.f():void` at offset 37; branch/handler structure is proven, but call provenance and error semantics are not. TS-022. | `LiveTuneConnectionOwner.kt`, `LiveTuneSourceLifecycle.kt`, `AndroidUsbLiveConnectionSession.kt` and `UsbPermissionRequestCoordinator.kt` retain explicit connection/profile/USB ownership and typed failures. | **No defect inferred.** Use TS findings to challenge disconnect, permission-loss and stale callback tests. Gate B has priority. |
| RAM-write verification/recovery | TS critical paths reviewed structurally only. No verified TunerStudio write/burn recovery contract is established; do not claim TunerStudio is safer or less safe. | `LiveTuneRamWrite.kt`, `UsbNativeGuardedWriteBatchExecutor.kt`, `UsbVerifiedGuardedWriteBatchExecutor.kt`, `EngineAuthoritySerialization.kt` and Rust `engine_guarded_write_batch.rs` implement staged precondition/write/readback, serialization and verified-prefix reconciliation. Tests include interrupted batches. | **Existing capability, not a requested rewrite.** Expand interleaving and loaded-JNI proof without changing live authority. |
| Burn / nonvolatile safety | No complete original burn transaction analysis yet. | `LiveTuneBurn.kt`, `NativeGuardedBurn.kt` and `docs/architecture/SAFETY_BOUNDARIES.md` require distinct explicit Burn and post-Burn verification. Handoff says Burn UI remains disabled pending gates. | Preserve current gating. No new Burn controls from this transfer. |
| 2D selection/edit model | Original `BinTableView.H()` and keyboard entrypoints reach `b(double)` and deferred flush `O()` (TS-011). Static queue replay mechanism identified; independent probe demonstrates effects, but active app edits/repeated notifications not established in TS-020. | Rust `tune_session.rs`, Kotlin `TunerTableSelectionOwner.kt`, `TunerCommandRouter.kt`, `TunerScreen.kt`, 2D/3D projection consumers; Rust owns revision, validation, history, one shared projection. | **Architecturally different.** Do NOT port the Java queue or inject notification clearing into the native domain. Compare *render/event invalidation* separately before proposing optimization. |
| Table editing semantics | TS keyboard/selection behavior partially traced; exact gesture and undo equivalence not proven. | `TunerCommandRouter.kt` handles selection, preview, apply, undo/redo, brush and keyboard routes; `TunerTableSelectionOwner.kt` requires revision for preview/apply and checks epoch around presentation snapshot. | Add **offline regression corpus** for selection and stale projection, not a second edit authority. |
| INI resolution | TS ownership indexed, but complete vendor expression, conditional and page compiler semantics are NOT yet proven by TS-Optim. | Rust `ecu_definition_resolver.rs` handles bounded `#if SYMBOL`, `#else`, `#endif`, `#define`, explicit includes; `ecu_definition.rs` and `compiled_ecu_profile.rs` build strict profile authority. `ECU_DEFINITION_RESOLUTION_PROTOCOL.md` explicitly rejects `#elif` and compound conditions. | **Actionable compatibility work:** pin real non-proprietary/source-authorized INI fixtures; classify accepted/unsupported syntax without silently accepting unknown grammar. |
| Persistence / backups | Original `h.i.g():void` has `File.renameTo` at 108, branch-check at 111 and another `renameTo` at 162 with `pop` at 165. File roles and actual failure state still unproven. TS-022; hypothesis only. | `TUNING_PERSISTENCE_RECOVERY_EVIDENCE.md` records desired atomic generation commits and recovery from historical EpicDash source, not automatic proof that EpicHub currently implements all durable persistence. | Capture **failure-injection requirements** for eventual EpicHub project persistence. Do not copy the original algorithm or claim an EpicHub defect. |
| Diagnostics | TS-020 protected session found real visible 32×32 `BinTableView` on 60/60 scans but observed zero edited-model events; no performance conclusion. | `diagnostics/*`, `NativeDiagnosticBatch.kt`, problem capture and performance monitors already exist. | Adapt diagnostic scenarios only when they respect existing bounded capture and privacy rules. |
| UI / rendering | TS queue/cache/EDT hypotheses H-001–H-003, no original-vs-EpicHub benchmark. | Compose UI with Rust-packed projection, shared 2D/3D state; no Swing EDT. | No cross-framework optimization claim. Benchmark actual Compose frame time and projection churn only after correct implementation and controlled fixtures. |

## Prioritized transfer backlog — scoped and non-mutating

### P0 — Preserve EpicHub active Gate B (blocking)

**Owner:** EpicHub Gate B.  
**Action:** Complete real loaded-JNI Android instrumentation with retained fake USB session, disconnect/reconnect/permission revocation, profile replacement, stale callbacks, close/unregister, and concurrent sample/load/write/Burn-authority interleavings as specified in `docs/HANDOFF.md`.  
**Acceptance:** Exact-head Foundation green **and actual device/emulator instrumentation execution** with recorded results. Build of `androidTest` APK is not execution. No live ECU and no Burn UI.  
**Transfer value:** TS-Optim serial analysis adds specific questions, not new assumptions about implementation.

### P1 — Definition compatibility corpus (after Gate B, may prepare offline fixtures earlier)

**Owner:** Rust profile/definition compiler.  
**Candidate fixtures:** missing includes, include cycles, nested conditional branches, unsupported `#elif`, compound expressions, duplicate/invalid keys, quoted semicolon handling, page geometry mismatch, table mapping/profile-generation changes.  
**Acceptance:** Each fixture has source provenance and an expected typed acceptance/rejection or explicit unresolved result. Run through real native resolver/compiler and ensure there is no partial authority installation on rejection. If comparing to TunerStudio, obtain separately observed reference results; don't infer compatibility from keywords or copied vendor source.  
**Sources:** `rust/engine/src/ecu_definition_resolver.rs`, `ecu_definition.rs`, `compiled_ecu_profile.rs`, existing Rust unit tests.

### P2 — Offline table event/projection equivalence (after Gate B, no ECU)

**Owner:** Rust TuneSession + Kotlin/Compose Tuner.  
**Candidate scenarios:** editing A twice in one preview/apply cycle; editing B; editing A again; switching tables mid-preview; undo/redo after selection change; obsolete draft revision; profile-generation/epoch change during projection fetch; repeated brush edits and screen recreation.  
**Acceptance:** Expected values, one logical history transaction per semantic command, stale-preview rejection, no presentation snapshot from mixed epochs, bounded resource use, no unintended repeated UI invalidation. Measure only if instrumentation establishes event-level comparability.  
**Important:** TS-Optim standalone Java notification JAR remains a research fixture. It is **not** a direct Android integration candidate; no proof EpicHub uses TunerStudio's retained-coordinate queue.

### P3 — Persistence failure injection (after persisted project-authority implementation exists)

**Owner:** future project/recovery store.  
**Candidate scenarios:** failed temp-file write, fsync, replace/rename false, simulated process interruption between generation files, reread mismatch, stale profile fingerprint, unavailable storage.  
**Acceptance:** Previous verified generation remains authoritative unless complete new generation is durably committed and reread; no automatic ECU replay or Burn. A TS-Optim `renameTo` result discarded at one original site is a *test-motivation*, not evidence EpicHub shares that defect.

### P4 — Controlled benchmarks / UI tuning (deferred)

**Owner:** performance + Tuner UI.  
**Action:** establish profile-driven repeatable table size, edits, projection reads, device/ABI, thermal and frame timing.  
**Acceptance:** Functional gate green first; then baseline-versus-candidate median/variance and regression report. Do not use the presence of the Java queue prototype as proof of an Android performance improvement.

## Change policy

- No changes to current EpicHub runtime code, USB authority, tuning values, Burn, project persistence, or INI acceptance in this knowledge-transfer commit.
- All production changes require their own acceptance gate, one branch at a time, conforming to `docs/HANDOFF.md`. Do not advance Gate C ahead of Gate B.
- Document observation separately from interpretation and from intended behavior. The latest code and exact-head CI supersede historical handoff/README checkpoint claims.
- Keep TunerStudio original bytecode/disassembly, proprietary assets and license/entitlement internals out of EpicHub source. Implement independent code with original tests and a traceable compatibility rationale.

## Decision

**Transfer validated behavior and test obligations, not internals.** EpicHub already has more robust semantic tuning/write boundaries than the earlier TS-Optim proposal assumed. The first immediate actionable value from this comparison is the scoped regression backlog, especially Gate B JNI safety and INI compatibility cases. No new application or Swing-derived queue should be added.
