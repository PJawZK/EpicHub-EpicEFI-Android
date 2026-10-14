# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the current head of `phase2/tuner-readonly-runtime-probe`, draft PR #29, and the latest Foundation quality run before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The active direction is **definition-driven multi-ECU Tuner architecture**. Do not return to Mega144H7-specific source-provider routing. The compiled profile path is now qualified through raw-page decode, semantic snapshot assembly, generic source install, runtime identity, and exact profile selection.

Historical pre-generic Phase 2 detail is preserved in `docs/HANDOFF_PHASE2_PRE_GENERIC_PROFILE.md`; do not replace or delete it.

---

# Current checkpoint — 2026-10-14

## Qualified stacked PRs

- **PR #23 — generic ECU profile architecture**
  - branch: `phase2/tuner-generic-ecu-profile-architecture`
  - exact qualified head: `7b0cb2d4144e01ecc4fec51f8559ee92a0bb52b8`
  - generic `ResolvedEcuProfile` and atomic `install_imported_pages()`.

- **PR #24 — definition identity/page geometry compiler**
  - branch: `phase2/tuner-ecu-definition-compiler`
  - exact qualified head: `374629e70feeee3186d6fdc0aeb1f0193dee193d`
  - Foundation run `38542649432` — PASS.

- **PR #25 — calibration primitives/editor descriptors**
  - exact qualified head: `1b55955e9abb9ed94d0a6f712c76c31a039b4b5e`
  - normalized scalar/array storage and table/curve editor bindings.

- **PR #26 — condition/include resolver + per-page protocol compiler**
  - exact qualified head: `309bbeafb98996a82f2a327ce25dcf5d8cadc0dc`
  - unsupported definition logic fails closed;
  - protocol commands are per-page definition data, not board switches.

- **PR #27 — page codec + semantic snapshot assembler**
  - branch: `phase2/tuner-definition-page-codec`
  - exact qualified head: `4a1560c0569403ddc41d882c76dabb3b86ba1812`
  - bounded endian-aware U8/S8/U16/S16/U32/S32/F32 codec;
  - compiled page ownership;
  - generic semantic table assembler;
  - complete covered Mega144H7 `TuneSnapshot` equality against the qualified hand-coded oracle.

- **PR #28 — compiled definitions as real `ResolvedEcuProfile`**
  - branch: `phase2/tuner-compiled-resolved-profile`
  - exact qualified head: `2a08a490ab3dc1155929dbb0f6c4af784c218dc1`
  - Foundation run `38558750390` — PASS;
  - `CompiledResolvedEcuProfile` validates page geometry, resolves snapshots, and installs through the generic atomic source path with no Mega-specific installer.

- **PR #29 — runtime identity + generic profile registry**
  - branch: `phase2/tuner-profile-registry-runtime-identity`
  - exact qualified head: `35ca22e427c9b1c8ba24cf1979c0a23767a19147`
  - Foundation run `38562361749` — PASS;
  - exact full-signature selection only;
  - duplicate profile signatures rejected;
  - `UnknownController` remains distinct from `RecognizedControllerWithoutProfile`;
  - board/firmware descriptions are non-authoritative;
  - integration path proven: runtime identity → exact compiled profile → generic atomic source install.

All qualified heads above are immutable checkpoints. Do not append documentation or cleanup commits to them; branch the next slice from the exact qualified SHA.

## Active branch

`phase2/tuner-readonly-runtime-probe`

Base: exact qualified PR #29 head `35ca22e427c9b1c8ba24cf1979c0a23767a19147`.

Current slice: define and qualify a **read-only, transport-neutral runtime identity probe boundary**. This layer must produce `RuntimeControllerIdentity` before profile selection while remaining incapable of ECU writes/Burn.

---

# Product architecture authority

```text
transport candidate
    ↓
read-only runtime identity probe
    ↓
RuntimeControllerIdentity(full live signature)
    ↓
ProfileRegistry exact match
    ↓
CompiledResolvedEcuProfile
    ↓
protocol/page geometry validation + source acquisition
    ↓
complete TuneSnapshot
    ↓
engine-owned TuneSession / TuneDraft
```

Keep these identities separate:

1. **Transport candidate identity** — endpoint/adapter metadata only; says where a controller might be reachable.
2. **Runtime controller identity** — full live signature plus optional descriptive firmware/board information.
3. **Resolved profile identity** — exact expected signature, definition fingerprint/generation, compiled schema and capabilities.

Only #3 selected by exact #2 grants interpretation/tuning authority.

USB VID/PID, board labels, endpoint names, signature prefixes, case-insensitive matches and descriptive firmware strings are never sufficient authority.

Unknown firmware and recognized firmware without an installed profile remain reportable states but must not produce a tune snapshot, ECU write, or Burn action.

---

# Definition/compiler state

TunerStudio/rusEFI INI is an **input/compiler source**, not runtime architecture to copy.

Qualified compiler stages now cover:

- resolved simple definition conditions/includes with unsupported grammar failing closed;
- exact signature, query/version command, envelope and endianness;
- page count/sizes;
- page identifiers and per-page read/write/Burn/CRC templates;
- scalar/array calibration primitives, type/offset/dimensions/units/scale/translate/bounds/precision;
- table and curve editor bindings;
- explicit page ownership;
- bounded engineering-value decode/encode;
- semantic table assembly into `TuneSnapshot`;
- concrete `CompiledResolvedEcuProfile`.

The hand-coded Mega144H7 resolver remains only a compatibility/reference oracle while parity coverage is expanded; it is no longer the desired production architecture.

---

# Real definition evidence already established

## Mega144H7

- profile name: `353`
- signature: `rusEFI master.2026.08.26.MEGA144H7.2273317132`
- fingerprint: `1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa`
- generation: `2_273_317_132`
- little endian;
- pages: 55,764 and 5,000 bytes;
- `queryCommand = "S"`;
- `pageIdentifier = "\x00\x00", "\x00\x01"`;
- `pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"`;
- `burnCommand = "B%2i", "B%2i"`.

Current stable Mega semantic table IDs:

- 1001 VE;
- 1002 ignition;
- 1003 target mixture;
- 1004 MAP estimate.

Do not assume unrelated ECUs share these numeric IDs automatically.

## uaEFI evidence

Older 2025 uaEFI is one-page/non-page-indexed while later definitions may use multiple page-indexed commands. This proves that even an ECU/board family name is not protocol authority; the exact firmware definition owns command geometry.

---

# Proven engine/tuning invariants

Do not regress:

- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine instance;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision guard and Undo/Redo;
- 2D and 3D are peers over the same semantic table state;
- preview is non-mutating until Apply;
- atomic source installation validates completely before session replacement;
- failed source install preserves prior baseline/draft/revision/history;
- stable semantic IDs are independent of raw byte offsets;
- no fabricated tune values;
- source/profile/draft/write/Burn remain separate.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

Do not reintroduce `ReusableContentHost`.

---

# Immediate next work

1. Implement a transport-neutral read-only signature probe interface that cannot express write/Burn operations.
2. Keep the probe strategy parameterized; do not require a selected ECU profile to know how to ask for identity.
3. Bound probe requests/responses and reject empty/malformed identity responses fail closed.
4. Prove transport-candidate metadata alone cannot select a profile.
5. Prove failed/unknown/no-profile probes never mutate `TuneSession`.
6. Prove exact live signature → `RuntimeControllerIdentity` → `ProfileRegistry` → compiled profile remains the only successful authority path.
7. Qualify the exact branch head through the full Foundation pipeline.
8. Only after that add concrete Android/USB/serial transport adapters; keep those adapters below the same read-only probe contract.

No live ECU RAM write or Burn belongs in this stage.
