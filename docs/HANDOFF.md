# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the current head of `phase2/tuner-controller-usb-connect-ui`, the latest open stacked PR, and its exact Foundation quality state before continuing.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

The active direction is **definition-driven multi-ECU tuning with explicit profile-package ownership, exact runtime identity authority, qualified read-only live tune loading, and truthful live-source lifecycle state**.

Do not return to Mega144H7-specific routing, USB metadata as profile authority, or any design that conflates local draft edits with ECU RAM writes/Burn.

Historical pre-generic Phase 2 detail remains in `docs/HANDOFF_PHASE2_PRE_GENERIC_PROFILE.md`; do not delete it.

---

# Current checkpoint — 2026-10-15

## Qualified generic definition/live-read chain

- PR #38 — native profile-registry handles: `0f0c4fe7df1a9e4bcf70c178591ff7501722995c`.
- PR #40 — owned dynamic compiled-profile import: `1fbc76766760d45ab54219ecbd17440750762c46`, run `38581312190`.
- PR #42 — profile import -> native registry JNI: `426e976cb18929d18a2199c7e2a0b41bda9eea23`, run `38583789666`.
- PR #43 — coherent definition/profile/protocol bundle compiler: `c9bee5ba49926d4b3cafdca59bc0b4a5cef2d323`, run `38585755818`.
- PR #44 — runtime registry owns coherent bundles: `1e0e5a896912d4c61886ae7904cea18d40523db1`, run `38589578913`.
- PR #45 — Android/JNI coherent bundle import: `c6df6c7036bfae73b1d438c39abd3e10261034dd`, run `38592056689`.
- PR #46 — bounded definition-driven page-read planner: `71bfdec5a0ffd36cf1d337770bde92444a8958cb`, run `38593799830`.
- PR #47 — `msEnvelope_1.0` read-only codec: `90235b3fc15111587012970fd21306c210209926`, run `38596022964`.
- PR #48 — exact-length USB collection + native acquisition authority: `9d7e53e8ffaf50109c1c75c5fa6e8609d3950a90`, run `38599655899`.
- PR #49 — native read authority -> Android USB orchestration: `a27745cb116c6a9d56de8995206053f4be605f16`, run `38602249758`.
- PR #50 — verified live pages -> atomic `TuneSession` install: `fd808d616754595fc0f82997083cd413dc94c0fc`, run `38606774962`.
- PR #51 — authoritative all-page live tune loader: `73587501ee40e1a8abbb14c57f3ee75507abfa57`, run `38611863965`.
- PR #52 — live-source lifecycle authority: `b913873dde5d3b9c1da0c9612a6859263baa372a`, run `38613548716`.

## Qualified UI/profile-package ownership chain

- PR #53 — active tune catalog drives current Tuner editor: `e72e6d072cc14e67b98cf2defdde2f5b65b8b597`, run `38615751170`.
  - editor no longer depends on hardcoded offline semantic table ID 7;
  - current table comes from the engine-owned `TunerTableCatalog`.
- PR #54 — truthful live-source status contract: `9a89bbf8cef87d1b1566f043a0cae0fae96f688a`, run `38622688005`.
- PR #55 — transactional definition-bundle catalog ownership: `8e823b4544fb504f72b9ed1f6bc74db71c007cc6`, run `38624214366`.
- PR #56 — live connection/profile ownership: `6f01927d87f77f7696bcfcd6791fb6e0d729eca1`, run `38625907509`.
- PR #57 — bounded definition-bundle ZIP package decoder/catalog: `9e8513e8a0342087bcd2fa22b1c8a763cb7562a7`, run `38635706192`.
- PR #58 — Android document-provider profile catalog: `556a0576904393d65d7eb75544ebb98e85ad434c`, run `38637194954`.
- PR #59 — transactional selected-profile ownership: `ebd0355aef8402a794722e33647309a4973eab7b`, run `38638787870`.
- PR #60 — transactional SAF persisted-grant/profile replacement: `18be5a8408306b2ccf661ca4b16b9798fc02e731`, run `38640276627`.
  - acquire only new grants -> fully validate/open replacement -> atomic authority swap -> release obsolete grants;
  - failed replacement preserves previous profile authority and grants.
- PR #61 — explicit SAF profile-package picker: `0bdf02e74f59d79731c815bc3bd07494840435ca`, run `38648534948`.
  - Settings > Connections has explicit Select/Replace/Clear actions;
  - `OpenMultipleDocuments` selection is cancel-safe;
  - package replacement/clear runs off the main thread;
  - selecting profile packages does **not** connect/probe/load USB/controller state;
  - profile-document owner closes before `NativeEngine`.

PR #39 and PR #41 remain closed/superseded duplicate paths.

All qualified SHAs above are immutable checkpoints. Never append cleanup or handoff commits directly to a qualified head; branch the next slice from it.

---

# Active branch

`phase2/tuner-controller-usb-connect-ui`

Base: exact qualified PR #61 head `0bdf02e74f59d79731c815bc3bd07494840435ca`.

The handoff refresh is the first commit on this branch. The intended implementation slice is the **explicit controller/USB connection presentation and permission flow**, consuming an already selected profile authority without merging profile selection and controller connection.

Before coding, inspect current `LiveTuneConnectionOwner`, USB discovery/topology/channel/permission APIs, and any parallel PR #62+ work.

---

# Product architecture authority

```text
explicit profile package selection
    ↓
coherent compiled profile/protocol registry authority

USB transport candidate
    ↓
descriptor-resolved CDC transport
    ↓
explicit permission/open action
    ↓
read-only runtime identity probe
    ↓
exact full live signature
    ↓
exact compiled bundle selection
    ↓
read-only authoritative page acquisition
    ↓
complete verified page set
    ↓
compiled profile decode
    ↓
atomic LiveController TuneSnapshot install
    ↓
engine-owned TuneSession / TuneDraft
    ↓
active tune catalog / 2D + 3D editor
```

Keep these concepts separate:

1. **Profile package selection** — which definitions/bundles EpicHub is allowed to understand.
2. **Transport candidate** — where a controller might be reachable.
3. **Runtime controller identity** — exact full live signature read from the controller.
4. **Resolved bundle authority** — exact definition/profile/protocol selected by that signature.
5. **Local live-source lifecycle** — connected/loading/live/failed/disconnected-retained state.

USB VID/PID, product strings, board names, endpoint names, family names, and signature prefixes never grant profile/tune authority.

---

# Live-source lifecycle semantics

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

stale completion after disconnect/reconnect
    -> ignored
```

Retained local tune data is useful offline but is never proof that a newly connected ECU still contains the same calibration.

---

# Proven invariants

Do not regress:

- one Rust semantic tuning authority;
- one engine-owned `TuneSession` per engine instance;
- immutable baseline + mutable `TuneDraft`;
- Rust-owned revision/history/Undo/Redo;
- 2D and 3D are peers over the same semantic state;
- preview is non-mutating until Apply;
- atomic source installation validates completely before replacement;
- failed source/profile replacement preserves previous valid authority/session;
- stable semantic IDs are not raw offsets;
- no fabricated tune values;
- retained-after-disconnect != live/current;
- profile selection != transport connection;
- transport candidate != runtime identity != profile authority.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

No qualified stage through PR #61 writes calibration RAM or issues Burn.

Do not reintroduce `ReusableContentHost`.

---

# Immediate next work

1. Inspect the qualified Android USB discovery/topology/permission/channel APIs and `LiveTuneConnectionOwner`.
2. Add an explicit controller connection action only when a profile package is selected and a USB candidate is chosen.
3. Permission-required must remain an explicit UI state/action; do not silently auto-request/connect from profile selection.
4. On successful read-only identity probe, pass exact signature to the selected profile connection owner; no VID/PID/family fallback.
5. Present truthful connection/load state using the existing live-source lifecycle contract.
6. Keep page load explicit and read-only unless the already-qualified owner intentionally composes probe+load.
7. Keep ECU RAM write/Burn out of scope.
