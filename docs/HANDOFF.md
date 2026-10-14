# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the current head / PR / Foundation quality state of the active branch before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Active direction: **definition-driven multi-ECU tuner architecture**. Do not return to Mega144H7-specific routing, board-family protocol switches, VID/PID profile authority, or per-board JNI methods.

Historical pre-generic Phase 2 detail remains in `docs/HANDOFF_PHASE2_PRE_GENERIC_PROFILE.md`; preserve it.

---

# Current checkpoint — 2026-10-14

## Active branch

`phase2/tuner-live-source-ui-integration`

Base: exact qualified PR #52 head `b913873dde5d3b9c1da0c9612a6859263baa372a`.

Current continuation goal: expose the qualified live-source lifecycle to the app/tuner UI without moving protocol, identity, page decode, or tuning authority into Compose. The first commit on this branch refreshes this handoff only; inspect the current UI ownership before choosing the smallest integration surface.

## Latest qualified checkpoints

- **PR #52 — live source/session lifecycle authority**
  - exact qualified head: `b913873dde5d3b9c1da0c9612a6859263baa372a`;
  - Foundation run `38613548716` — PASS;
  - explicit `Disconnected`, `Connected`, `Loading`, `Live`, and `LoadFailed` states;
  - loaded local tune may remain available after disconnect but is no longer represented as live/current;
  - reconnect always requires fresh identity + load before returning to `Live`;
  - monotonically increasing load-attempt IDs prevent stale completion after disconnect/reconnect;
  - failed reload can retain the previous local snapshot without retaining live status.

- **PR #51 — authoritative all-page live tune loader**
  - exact qualified head: `73587501ee40e1a8abbb14c57f3ee75507abfa57`;
  - Foundation run `38611863965` — PASS;
  - exact selected bundle supplies authoritative page count;
  - operation is exactly `page count -> acquire pages 0..N-1 -> one atomic live install`;
  - failure on any page stops immediately and no partial page set is installed.

- **PR #50 — acquired live pages -> atomic `TuneSession`**
  - exact qualified head: `fd808d616754595fc0f82997083cd413dc94c0fc`;
  - Foundation run `38606774962` — PASS;
  - exact selected compiled bundle decodes the complete page set;
  - source origin `LiveController` is preserved in Rust projection/catalog metadata and Kotlin (`origin code = 3`);
  - decode/profile failure preserves the previous baseline, draft, revision and history exactly.

- **PR #49 — Android stateless read-only page orchestration**
  - exact qualified head: `a27745cb116c6a9d56de8995206053f4be605f16`;
  - Foundation run `38602249758` — PASS;
  - Rust owns request planning/framing/response validation; Kotlin owns only sequential USB execution;
  - no native acquisition session handle and no protocol semantics duplicated in Android.

- **PR #48 — stream-safe USB collection + Rust acquisition authority**
  - exact qualified head: `9d7e53e8ffaf50109c1c75c5fa6e8609d3950a90`;
  - Foundation run `38599655899` — PASS;
  - Android can collect one logical response over multiple bulk reads;
  - Rust validates chunk limits, status, CRC, payload length and complete page assembly.

- **PR #47 — `msEnvelope_1.0` read-only codec**
  - exact qualified head: `90235b3fc15111587012970fd21306c210209926`;
  - Foundation run `38596022964` — PASS;
  - request: BE length + body + BE IEEE CRC32(body);
  - response: BE length(status+payload) + status + payload + BE CRC32(status+payload).

- **PR #46 — definition-driven read-only page request planner**
  - exact qualified head: `71bfdec5a0ffd36cf1d337770bde92444a8958cb`;
  - Foundation run `38593799830` — PASS;
  - supports indexed `R%2i%2o%2c` and legacy non-indexed `R%2o%2c` without board switches;
  - request ranges are bounded by compiled profile page geometry and definition endianness.

- **PR #45 — Android/JNI coherent definition-bundle import**
  - exact qualified head: `c6df6c7036bfae73b1d438c39abd3e10261034dd`;
  - Foundation run `38592056689` — PASS;
  - Android passes root definition, explicit symbols/includes and semantic bindings; Rust remains resolver/compiler authority.

- **PR #44 — runtime registry owns coherent profile bundles**
  - exact qualified head: `1e0e5a896912d4c61886ae7904cea18d40523db1`;
  - Foundation run `38589578913` — PASS;
  - protocol-dependent access requires an exact coherent bundle; profile-only compatibility entries fail closed with `MissingCompiledBundle`.

- **PR #43 — one resolved definition -> compiled profile + protocol**
  - exact qualified head: `c9bee5ba49926d4b3cafdca59bc0b4a5cef2d323`;
  - Foundation run `38585755818` — PASS;
  - definition resolution occurs once and both byte interpretation and page protocol compile from the same resolved text.

- **PR #42 — owned compiled profile import -> native registry**
  - exact qualified head: `426e976cb18929d18a2199c7e2a0b41bda9eea23`;
  - Foundation run `38583789666` — PASS;
  - generic JNI import path, full Rust `u32` semantic ID range, atomic failed compile/duplicate handling.

- **PR #40 — owned dynamic compiled-profile import**
  - exact qualified head: `1fbc76766760d45ab54219ecbd17440750762c46`;
  - Foundation run `38581312190` — PASS.

- **PR #38 — native profile-registry handles**
  - exact qualified head: `0f0c4fe7df1a9e4bcf70c178591ff7501722995c`;
  - independent native registries behind opaque handles.

- **PR #37 — owning profile registry**
  - exact qualified head: `bfb9de7fad5f2d5f9ad2137cbf379850d2a96cfe`.

- **PR #36 — native runtime response -> exact profile authority**
  - exact qualified head: `775b7fd642db1956a8d23003b97925e237524774`;
  - Foundation run `38575153300` — PASS.

Earlier generic-profile/transport/compiler checkpoints #23-#35 remain valid. Do not reimplement them.

### Superseded PRs

- **#39** — closed/superseded; do not reopen/cherry-pick.
- **#41** — closed/superseded by the qualified #40 -> #42 chain; do not reopen/cherry-pick.

All qualified SHAs above are immutable checkpoints. Never append cleanup/docs commits to a qualified SHA; branch the next slice from it.

---

# Current authority chain

```text
transport candidate
    ↓
Android descriptor-resolved CDC transport
    ↓
bounded read-only identity transaction
    ↓
raw signature bytes
    ↓
Rust exact runtime identity validation
    ↓
exact coherent bundle selection
    ↓
compiled profile + compiled protocol from one resolved definition
    ↓
authoritative page count / page geometry
    ↓
definition-driven read request body
    ↓
msEnvelope request framing
    ↓
Android exact-length USB transaction (multi-bulk-read safe)
    ↓
Rust envelope / CRC / status / payload validation
    ↓
complete page assembly
    ↓
all authoritative pages acquired
    ↓
compiled profile decode
    ↓
complete TuneSnapshot(origin = LiveController)
    ↓
atomic engine-owned TuneSession replacement
    ↓
LiveTuneSourceLifecycle freshness / disconnect / retry state
```

Keep these identities separate:

1. **Transport candidate identity** — descriptive endpoint/device information only.
2. **Runtime controller identity** — exact full live signature.
3. **Resolved profile/bundle identity** — exact expected signature plus compiled definition/protocol authority.

Only exact #2 -> #3 selection grants interpretation/read authority.

USB VID/PID, product strings, board names, signature prefixes, case-insensitive matches and firmware-family names are never profile authority.

---

# Read-only path is now architecturally complete

Qualified code now covers:

- generic USB candidate discovery;
- descriptor-derived CDC topology;
- permission-aware open/claim/cleanup;
- bounded read-only USB transactions;
- exact live signature validation;
- exact registry/bundle selection;
- definition resolution and coherent profile+protocol compilation;
- page count/sizes and command templates;
- indexed and non-indexed read body planning;
- `msEnvelope_1.0` framing;
- logical response collection across multiple bulk transfers;
- CRC/status/payload validation;
- complete page assembly;
- complete all-page acquisition before install;
- exact compiled-profile page decode;
- atomic `TuneSession` replacement;
- explicit live/offline/disconnected lifecycle semantics.

No ECU calibration write or Burn authority has been added.

---

# Protocol evidence / limits

## Current Mega144H7 oracle

- profile name: `353`;
- signature: `rusEFI master.2026.08.26.MEGA144H7.2273317132`;
- fingerprint: `1546186d795690b45b4fb5b064f1bd225a3e5f8b102bad28f54fb96afd6390fa`;
- generation: `2_273_317_132`;
- pages: 55,764 and 5,000 bytes;
- little endian;
- `queryCommand = "S"`;
- `pageIdentifier = "\x00\x00", "\x00\x01"`;
- `pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"`;
- semantic IDs used by the compatibility mapping: 1001 VE, 1002 ignition, 1003 target mixture, 1004 MAP estimate.

The hand-coded Mega resolver remains a compatibility/test oracle only.

## Older uaEFI evidence

Older 2025 uaEFI is one-page and uses non-page-indexed `R%2o%2c`. Same family can therefore have materially different protocol geometry across firmware. Never select protocol by family/board name.

## USB / envelope constraints

- STM32 USB VID:PID `0483:5740` is transport metadata only;
- CDC communications class `0x02`, data class `0x0A`;
- interface numbers are descriptor-resolved and may shift when optional interfaces exist;
- current Android bounded response frame limit: 65,536 bytes;
- `msEnvelope_1.0` response overhead plus 16-bit length semantics gives practical Android page-read payload chunk max 65,529 bytes;
- ECU success status for the verified current read path: `0x00`;
- command-body field endianness comes from the definition; outer envelope length/CRC framing is big endian.

See `docs/architecture/ECU_PROTOCOL_DIRECT_VERIFICATION.md` for direct firmware-source evidence.

---

# Engine/tuning invariants — do not regress

- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine instance;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision guard and Undo/Redo;
- 2D/3D are peers over the same semantic table state;
- preview is non-mutating until Apply;
- source installation validates completely before session replacement;
- failed install preserves prior baseline/draft/revision/history;
- semantic IDs are stable and not raw-offset identities;
- no fabricated values;
- source/profile/draft/write/Burn remain separate;
- retained local data after disconnect must never be presented as still live/current;
- reconnect must revalidate exact identity and perform a fresh complete load before regaining live status.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

Do not reintroduce `ReusableContentHost`.

---

# Immediate next work

1. Inspect current app/tuner UI ownership and connect the qualified `LiveTuneSourceLifecycle` to a small UI-facing state owner without moving protocol semantics into Compose.
2. Make connection/load states visible and unambiguous: disconnected with retained local snapshot, connected but not loaded, loading, live/current, load failed with retained local snapshot when applicable.
3. A successful live load should make the engine's newly installed live `TuneSession` the active tuner source through the existing catalog/projection APIs; do not create a parallel table model.
4. Disconnect should not destroy the already-loaded local tune unless the user explicitly replaces/closes it, but UI must stop calling it live.
5. Reconnect must re-probe/revalidate signature and run a fresh complete load before live/current status is restored.
6. Keep all ECU RAM write / calibration write / Burn work out of this UI integration slice.
7. After the UI/session lifecycle boundary is qualified, revisit the separate design for write-to-RAM vs Burn lifecycle/capability/reconciliation. Do not infer it from the read path.
