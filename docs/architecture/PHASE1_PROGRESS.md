# Phase 1 Progress

## Final status

**Phase 1 is accepted and closed as of 2026-09-23.**

The dedicated architecture archaeology, responsibility mapping, source-evidence review and cross-document consistency audit completed with **46 / 46 review items satisfied**. Explicit user acceptance was given before production Android/Rust scaffolding began.

The accepted architecture baseline includes:

- clean-slate Kotlin/Jetpack Compose + Rust direction;
- Android/platform ownership in Kotlin;
- high-volume runtime/channel/logging/analysis ownership in Rust;
- one ECU protocol/session authority and one runtime ChannelStore;
- exact profile/signature/TuneSnapshot authority rules;
- guarded semantic Write/Burn/recovery contracts;
- legacy ESP32 BLE↔CAN bridge separation from any future direct-output capability;
- format-neutral live acquisition with the locked sequence **live path → MLG interoperability → MDF4 evaluation/implementation → scalable Analyzer**;
- one shared analysis/evidence platform with domain profiles;
- semantic tune-draft ownership in Rust and interaction/presentation state in Kotlin;
- mandatory D-022 piece-by-piece historical-source review and clean reimplementation at each inherited subsystem's implementation-entry gate;
- initial Android identity/support baseline:
  - `com.epicefi.epichub`;
  - `minSdk 26`;
  - `targetSdk 36`;
  - `compileSdk 37`;
  - production `armeabi-v7a` + `arm64-v8a`.

## Closure authority

Primary Phase 1 authority remains:

- `DECISIONS.md`;
- `PHASE1_GATE_ASSESSMENT.md`;
- `REVIEW_CHECKLIST.md`;
- `GAP_ANALYSIS.md`;
- `SOURCE_REIMPLEMENTATION_METHOD.md`;
- the domain contract/evidence documents referenced by those files.

`REVIEW_CHECKLIST.md` is the formal closure record. Git history preserves the detailed pre-acceptance progression and audit history.

## Work intentionally carried forward

Acceptance does **not** mean every implementation detail is preselected. `GAP_ANALYSIS.md` intentionally carries forward measured and feature-entry work including:

- USB runtime ownership PoCs;
- packed RuntimeSnapshot/JNI lifecycle and performance;
- dual-ABI physical qualification on SM-A137F and SM-T500;
- Compose frame/jank measurements;
- genuine ECU/INI/MLG/MDF qualification corpora;
- concrete persistence/analyzer/diagnostic schemas when those features enter implementation;
- deliberately deferred product scope such as rich table operations, dashboard customization, direct ESP32 outputs and specialized GPU rendering.

Those items no longer block Phase 1 because their ownership, evidence requirement and implementation-entry gates are already defined.

## Phase 2 entry

The first production slice is deliberately small and non-mutating:

```text
Android application/bootstrap
→ Compose host
→ dual-ABI Rust shared library
→ NativeEngine create/close/recreate
→ versioned packed RuntimeSnapshot ABI
→ minimal Rust ChannelStore
→ synthetic/native test-channel publication
→ Compose display of snapshot
→ bounded native event drain
→ JNI/snapshot lifecycle diagnostics
```

Explicitly excluded from this first slice:

- ECU mutation;
- Burn;
- MDF writer;
- full Tuner migration;
- plugin ports.

After the foundation is qualified, continue with the accepted sequence:

```text
format-neutral live ECU path
→ MLG writer/interoperability
→ MDF4 evaluation/implementation
→ scalable Analyzer
```

Every inherited subsystem continues to pass D-022 at implementation entry. Phase 1 is not reopened merely because later measured evidence causes a deliberate Phase 2 architecture refinement.