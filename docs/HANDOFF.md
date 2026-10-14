# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the current head of `phase2/tuner-live-source-ui-integration`, draft PR #53, and the latest Foundation quality state before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The active direction is **definition-driven multi-ECU Tuner architecture with qualified read-only live tune loading and explicit live-source lifecycle semantics**. Do not return to Mega144H7-specific source-provider routing or transport metadata as profile authority.

Historical pre-generic Phase 2 detail is preserved in `docs/HANDOFF_PHASE2_PRE_GENERIC_PROFILE.md`; do not replace or delete it.

---

# Current checkpoint — 2026-10-14

## Qualified stacked PRs — generic profile and live read chain

- **PR #38 — native profile-registry handles**
  - exact qualified head: `0f0c4fe7df1a9e4bcf70c178591ff7501722995c`;
  - opaque registry handles own independent runtime registries.

- **PR #40 — owned dynamic compiled-profile import**
  - exact qualified head: `1fbc76766760d45ab54219ecbd17440750762c46`;
  - Foundation run `38581312190` — PASS.

- **PR #42 — profile import -> native registry JNI**
  - exact qualified head: `426e976cb18929d18a2199c7e2a0b41bda9eea23`;
  - Foundation run `38583789666` — PASS.

- **PR #43 — coherent definition/profile/protocol bundle compiler**
  - exact qualified head: `c9bee5ba49926d4b3cafdca59bc0b4a5cef2d323`;
  - Foundation run `38585755818` — PASS;
  - profile and protocol compile from the exact same resolved definition.

- **PR #44 — runtime registry owns coherent compiled bundles**
  - exact qualified head: `1e0e5a896912d4c61886ae7904cea18d40523db1`;
  - Foundation run `38589578913` — PASS;
  - profile-only authority cannot grant protocol/page access without the coherent bundle.

- **PR #45 — Android/JNI coherent bundle import**
  - exact qualified head: `c6df6c7036bfae73b1d438c39abd3e10261034dd`;
  - Foundation run `38592056689` — PASS.

- **PR #46 — bounded read-only page request planner**
  - exact qualified head: `71bfdec5a0ffd36cf1d337770bde92444a8958cb`;
  - Foundation run `38593799830` — PASS;
  - supports indexed `R%2i%2o%2c` and non-indexed `R%2o%2c` from definition authority.

- **PR #47 — `msEnvelope_1.0` read-only codec**
  - exact qualified head: `90235b3fc15111587012970fd21306c210209926`;
  - Foundation run `38596022964` — PASS;
  - request/response framing remains separate from read-body planning.

- **PR #48 — hardened read-only page acquisition boundaries**
  - exact qualified head: `9d7e53e8ffaf50109c1c75c5fa6e8609d3950a90`;
  - Foundation run `38599655899` — PASS;
  - Android exact-length segmented USB response collection;
  - Rust acquisition authority validates status/CRC/payload and assembles complete pages.

- **PR #49 — native read-only page authority -> Android USB**
  - exact qualified head: `a27745cb116c6a9d56de8995206053f4be605f16`;
  - Foundation run `38602249758` — PASS;
  - stateless JNI plan/verify API; Kotlin owns only sequential USB orchestration.

- **PR #50 — acquired live pages -> atomic `TuneSession` install**
  - exact qualified head: `fd808d616754595fc0f82997083cd413dc94c0fc`;
  - Foundation run `38606774962` — PASS;
  - live controller source origin code `3`;
  - exact selected bundle decodes complete pages before atomic session replacement.

- **PR #51 — authoritative all-page live tune loader**
  - exact qualified head: `73587501ee40e1a8abbb14c57f3ee75507abfa57`;
  - Foundation run `38611863965` — PASS;
  - exact bundle page count -> acquire every page -> one final atomic install;
  - partial acquisition never installs.

- **PR #52 — live-source lifecycle authority**
  - exact qualified head: `b913873dde5d3b9c1da0c9612a6859263baa372a`;
  - Foundation run `38613548716` — PASS;
  - explicit `Disconnected`, `Connected`, `Loading`, `Live`, `LoadFailed` states;
  - retained local tune state is not treated as still live after disconnect;
  - reconnect requires a fresh load;
  - stale attempts are invalidated.

- **PR #53 — active tune catalog drives current Tuner editor**
  - exact qualified head: `e72e6d072cc14e67b98cf2defdde2f5b65b8b597`;
  - Foundation run `38615751170` — PASS;
  - removes the hardcoded offline semantic table ID from the editor path;
  - `ActiveTunerTable` resolves the current engine-owned `TuneSession` catalog;
  - deterministic current single-table default is the first catalog entry, with empty catalogs failing closed;
  - `OfflineTunerFixture` remains only as a compatibility façade and delegates to the active catalog-driven path.

Closed/superseded branches remain closed:

- PR #39 — superseded duplicate;
- PR #41 — superseded duplicate import/JNI path.

All qualified heads above are immutable checkpoints. Never append cleanup/docs commits to a qualified head; branch the next slice from the exact qualified SHA.

---

# Active branch

`phase2/tuner-live-source-ui-integration`

Exact qualified checkpoint: PR #53 head `e72e6d072cc14e67b98cf2defdde2f5b65b8b597`.

Current continuation after #53 qualification:

1. branch from exact qualified #53;
2. expose the qualified #52 live-source lifecycle state to app/tuner presentation;
3. show clearly whether the current local tables are live/current, retained after disconnect, connected-but-not-loaded, loading, or failed;
4. do not reintroduce source-specific table IDs or a second UI table model;
5. keep the mature 2D/3D editor operating on the same engine-owned catalog/projection APIs;
6. USB connection/permission controls may follow separately if they cannot be introduced without widening this UI-status slice;
7. ECU RAM writes and Burn remain out of scope.

---

# Product architecture authority

```text
transport candidate
    ↓
Android descriptor-resolved transport
    ↓
read-only runtime identity probe
    ↓
exact live signature
    ↓
coherent compiled definition/profile/protocol bundle
    ↓
bounded page-read planning
    ↓
msEnvelope framing
    ↓
exact-length Android USB transaction
    ↓
native CRC/status/payload verification
    ↓
complete authoritative page set
    ↓
compiled profile decode
    ↓
atomic LiveController TuneSnapshot install
    ↓
engine-owned TuneSession / TuneDraft
    ↓
active tune catalog / 2D+3D UI
```

Keep these identities separate:

1. **Transport candidate identity** — endpoint/adapter metadata only.
2. **Runtime controller identity** — exact full live signature.
3. **Resolved profile/bundle identity** — compiled definition + protocol authority.
4. **Local tune source lifecycle** — whether current local data is live/current, retained/offline, loading, failed, or disconnected.

USB VID/PID, product strings, board labels, endpoint names, signature prefixes, and family names never grant profile/tune authority.

---

# Qualified live-source lifecycle semantics

A local `TuneSession` snapshot can remain useful after transport loss, but UI and logic must not pretend it remains live.

Required semantics:

```text
Live + disconnect
    -> Disconnected(retainedSource = previous live snapshot metadata)

Disconnected + reconnect
    -> Connected(exact runtime identity)
    -> requires fresh live load before returning to Live

Connected/Live + load start
    -> Loading(attemptId)

Loading + successful current attempt
    -> Live(new retained source)

Loading + failure
    -> LoadFailed(previous retained source may remain locally usable)

stale attempt completion after disconnect/reconnect
    -> ignored
```

Do not use retained local state as proof that the connected ECU still has identical calibration.

---

# Definition/compiler and protocol authority

TunerStudio/rusEFI INI remains an **input/compiler source**, not runtime architecture to copy.

Production direction is EpicHub-owned normalized compiled definitions/bundles with:

- exact signature;
- definition fingerprint/generation;
- endianness and envelope;
- page geometry;
- per-page read/write/Burn templates;
- scalar/array calibration metadata;
- table/curve editor bindings;
- explicit semantic bindings;
- coherent protocol authority compiled from the same resolved definition.

The hand-coded Mega144H7 resolver remains a compatibility/reference oracle only.

---

# Tuner UI state after PR #53

The Tuner editing path no longer depends on hardcoded offline table ID `7`.

Current UI authority:

```text
NativeEngine current TuneSession
    ↓
TunerTableCatalog
    ↓
ActiveTunerTable.default/current table
    ↓
TunerSessionClient(handle, semantic table ID)
    ↓
projection / preview / apply / undo / redo
```

The current screen still presents one table at a time. Multi-table selection is future UI work; it should use the same catalog rather than another model.

The next UI slice should surface live-source lifecycle/status without rewriting table interaction logic.

---

# Proven tuning invariants

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
- source/profile/draft/write/Burn remain separate;
- retained-after-disconnect != live/current.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

Do not reintroduce `ReusableContentHost`.

---

# Write/Burn boundary

No qualified stage through PR #53 writes calibration RAM or issues Burn.

Current live chain is strictly:

```text
ECU read -> validate -> decode -> local TuneSession
```

Do not add calibration write or Burn implicitly while doing UI/session integration.
