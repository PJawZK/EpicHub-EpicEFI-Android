# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the current head of `phase2/tuner-profile-registry-ownership`, draft PR #36, and the latest Foundation quality state before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The active direction is **definition-driven multi-ECU Tuner architecture**. Do not return to Mega144H7-specific source-provider routing. The generic path is now qualified from definition compilation through Android USB candidate discovery, CDC topology resolution, bounded read-only transport, native runtime-identity validation, and exact native profile selection.

Historical pre-generic Phase 2 detail is preserved in `docs/HANDOFF_PHASE2_PRE_GENERIC_PROFILE.md`; do not replace or delete it.

---

# Current checkpoint — 2026-10-14

## Qualified stacked PRs

- **PR #23 — generic ECU profile architecture**
  - exact qualified head: `7b0cb2d4144e01ecc4fec51f8559ee92a0bb52b8`;
  - generic `ResolvedEcuProfile` and atomic `install_imported_pages()`.

- **PR #24 — definition identity/page geometry compiler**
  - exact qualified head: `374629e70feeee3186d6fdc0aeb1f0193dee193d`;
  - Foundation run `38542649432` — PASS.

- **PR #25 — calibration primitives/editor descriptors**
  - exact qualified head: `1b55955e9abb9ed94d0a6f712c76c31a039b4b5e`;
  - normalized scalar/array storage and table/curve bindings.

- **PR #26 — condition/include resolver + per-page protocol compiler**
  - exact qualified head: `309bbeafb98996a82f2a327ce25dcf5d8cadc0dc`;
  - unsupported definition logic fails closed;
  - protocol commands are per-page definition data, not board switches.

- **PR #27 — page codec + semantic snapshot assembler**
  - exact qualified head: `4a1560c0569403ddc41d882c76dabb3b86ba1812`;
  - bounded endian-aware codec, compiled page ownership, generic semantic table assembly;
  - covered Mega144H7 `TuneSnapshot` equality proven against the hand-coded oracle.

- **PR #28 — compiled definitions as `ResolvedEcuProfile`**
  - exact qualified head: `2a08a490ab3dc1155929dbb0f6c4af784c218dc1`;
  - Foundation run `38558750390` — PASS;
  - compiled profiles validate geometry, resolve snapshots, and install through the generic atomic source path.

- **PR #29 — runtime identity + generic profile registry**
  - exact qualified head: `35ca22e427c9b1c8ba24cf1979c0a23767a19147`;
  - exact full-signature selection only;
  - duplicate profile signatures rejected;
  - `UnknownController` distinct from `RecognizedControllerWithoutProfile`;
  - board/firmware descriptions are non-authoritative.

- **PR #30 — transport-neutral read-only runtime probe**
  - exact qualified head: `2057e5f07d9757705b6412e82b66459f9d57f121`;
  - bounded read-only query/response probe;
  - failed/unknown/no-profile probing preserves the existing tuning session.

- **PR #31 — authority-neutral Android USB candidate discovery**
  - exact qualified head: `1e8e5fd848c5cda064b2efc5f4a341ab86d60374`;
  - Foundation run `38565148212` — PASS;
  - all attached USB devices remain candidates; VID/PID/product strings never grant ECU/profile authority;
  - optional USB-host manifest capability, no whitelist or auto-launch filter.

- **PR #32 — Android USB CDC topology resolution**
  - exact qualified head: `e6349be74435739250fb3f731435c267ede883eb`;
  - Foundation run `38568414732` — PASS;
  - descriptor-derived CDC communications/data interfaces and bulk IN/OUT endpoints;
  - shifted interface numbers are supported;
  - ambiguous/multi-port/invalid topology fails closed.

- **PR #33 — permission-aware bounded Android USB read-only channel**
  - exact qualified head: `095c7050a552031ef7a36d74d3ec8c7f0c52b1db`;
  - Foundation run `38569652446` — PASS;
  - USB permission/open/claim and bounded request/response transactions;
  - no public arbitrary ECU write or Burn surface.

- **PR #35 — Android USB → native runtime-identity validation**
  - exact qualified head: `086f080f59b3a5448f2e763b35641f54634b4af5`;
  - Foundation run `38573356927` — PASS;
  - Android owns physical USB I/O; Rust owns signature-response validation;
  - one shared `runtime_identity_from_response()` rule set;
  - exact signature text preserved with explicit fail-closed JNI status mapping.

- **PR #36 — native runtime response → exact profile authority**
  - exact qualified head: `775b7fd642db1956a8d23003b97925e237524774`;
  - Foundation run `38575153300` — PASS;
  - `select_profile_from_runtime_response()` composes native response validation with the existing exact `ProfileRegistry`;
  - exact compiled Mega profile selection, shortened-signature rejection, recognized/no-profile distinction, and invalid-response rejection are tested.

All qualified heads above are immutable checkpoints. Never append documentation or cleanup commits to them; branch the next slice from the exact qualified SHA.

## Active branch

`phase2/tuner-profile-registry-ownership`

Base: exact qualified PR #36 head `775b7fd642db1956a8d23003b97925e237524774`.

Current slice: establish **production-capable ownership/lifetime for dynamically compiled profiles and their registry** without hardcoding Mega144H7 or creating a second metadata-only identity registry.

---

# Product architecture authority

```text
transport candidate
    ↓
Android descriptor-resolved transport
    ↓
bounded read-only transaction
    ↓
raw signature response bytes
    ↓
Rust runtime_identity_from_response()
    ↓
RuntimeControllerIdentity(full exact live signature)
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

Qualified compiler stages cover:

- resolved simple definition conditions/includes with unsupported grammar failing closed;
- exact signature, query/version command, envelope and endianness;
- page count/sizes;
- page identifiers and per-page read/write/Burn/CRC templates;
- scalar/array calibration primitives: type/offset/dimensions/units/scale/translate/bounds/precision;
- table and curve editor bindings;
- explicit page ownership;
- bounded engineering-value decode/encode;
- semantic table assembly into `TuneSnapshot`;
- concrete `CompiledResolvedEcuProfile` implementing `ResolvedEcuProfile`.

The hand-coded Mega144H7 resolver is now a compatibility/reference oracle, not the desired production architecture.

---

# Android/USB transport state

Qualified Android layers now provide:

- generic USB candidate discovery with unknown devices retained;
- descriptor-derived CDC topology (`0x02` communications + `0x0A` data + unique bulk IN/OUT);
- no hardcoded CDC interface numbers;
- permission-aware open/claim/cleanup;
- bounded read-only request/response transactions;
- Android → native raw signature-response handoff;
- native strict UTF-8/nonempty/bounded identity validation.

The physical USB layer still does **not** grant ECU/profile authority and exposes no public calibration write/Burn API.

Permission-request broadcast/UI lifecycle is not yet implemented; absence of permission is currently an explicit `PermissionRequired` result.

---

# Real definition/protocol evidence already established

## Mega144H7

- profile name: `353`;
- signature: `rusEFI master.2026.08.26.MEGA144H7.2273317132`;
- fingerprint: `1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa`;
- generation: `2_273_317_132`;
- little endian;
- pages: 55,764 and 5,000 bytes;
- `queryCommand = "S"`;
- `pageIdentifier = "\x00\x00", "\x00\x01"`;
- `pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"`;
- `burnCommand = "B%2i", "B%2i"`.

Stable Mega semantic table IDs currently used by the compatibility mapping:

- 1001 VE;
- 1002 ignition;
- 1003 target mixture;
- 1004 MAP estimate.

Do not assume unrelated ECUs share these numeric IDs automatically.

## uaEFI evidence

Older 2025 uaEFI is one-page/non-page-indexed while later definitions may use multiple page-indexed commands. ECU/board family name is therefore not protocol authority; the exact firmware definition owns command geometry.

## Direct firmware protocol evidence

`docs/architecture/ECU_PROTOCOL_DIRECT_VERIFICATION.md` records direct source verification that current EpicEFI/rusEFI firmware uses:

- STM32 USB VID:PID `0483:5740` as transport identity only;
- CDC communications class `0x02`, data class `0x0A`;
- bulk OUT/IN endpoints with interface numbers allowed to shift;
- full generated TunerStudio signature as query response;
- asymmetric CRC request/response framing;
- distinct read/write/Burn commands and results.

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

1. Establish an owning runtime profile registry/provider capable of retaining dynamically compiled `ResolvedEcuProfile` objects safely for the session lifetime.
2. Do **not** hardcode Mega144H7 into a global registry or create a parallel metadata-only registry just for JNI.
3. Preserve duplicate-signature rejection and exact full-signature selection semantics in the owning form.
4. Prove dynamic profile ownership remains valid after the caller/compiler scope ends.
5. Prove multiple profiles and recognized-without-profile signatures coexist without ambiguity.
6. Then expose a small generic native registry/provider handle ABI so Android can register/import compiled definitions and classify a validated runtime identity through the canonical `select_profile_from_runtime_response()` path.
7. Only after exact profile selection should live page acquisition be introduced using that selected profile's compiled protocol/page geometry.
8. Keep calibration RAM write and Burn out of scope until live readback/full-snapshot reconciliation is designed and qualified.

No board-name/VID/PID fallback belongs in any of these stages.
