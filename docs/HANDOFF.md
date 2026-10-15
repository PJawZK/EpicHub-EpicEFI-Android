# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the exact current head / PR / Foundation state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Active direction: **definition-driven multi-ECU tuning with explicit profile authority, exact runtime identity, read-only live acquisition, atomic local TuneSession installation, explicit Android USB connection/load ownership, and retained exact source-page authority for future safe write/reconciliation work**.

Do not return to Mega144H7-specific routing, USB metadata as profile authority, or any design that conflates local `TuneDraft` edits with ECU RAM write/Burn.

Historical pre-generic Phase 2 detail remains in `docs/HANDOFF_PHASE2_PRE_GENERIC_PROFILE.md`; preserve it.

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

## Qualified UI/profile/Android ownership chain

- PR #53 — active tune catalog drives current Tuner editor: `e72e6d072cc14e67b98cf2defdde2f5b65b8b597`.
  - removes the hardcoded offline semantic table ID from the editor path;
  - adds catalog-driven `ActiveTunerTable` over the single engine-owned `TuneSession`;
  - retains `OfflineTunerFixture` only as a compatibility alias delegating to `ActiveTunerTable`;
  - deterministic default table selection comes from the native catalog's semantic-ID ordering;
  - the mature 2D/3D preview/apply/undo/redo engine remains unchanged.
- PR #54 — truthful live-source status contract: `9a89bbf8cef87d1b1566f043a0cae0fae96f688a`, run `38622688005`.
- PR #55 — transactional definition-bundle catalog ownership: `8e823b4544fb504f72b9ed1f6bc74db71c007cc6`, run `38624214366`.
- PR #56 — live connection/profile ownership: `6f01927d87f77f7696bcfcd6791fb6e0d729eca1`, run `38625907509`.
- PR #57 — bounded definition-bundle ZIP package decoder/catalog: `9e8513e8a0342087bcd2fa22b1c8a763cb7562a7`, run `38635706192`.
- PR #58 — Android document-provider profile catalog: `556a0576904393d65d7eb75544ebb98e85ad434c`, run `38637194954`.
- PR #59 — transactional selected-profile ownership: `ebd0355aef8402a794722e33647309a4973eab7b`, run `38638787870`.
- PR #60 — transactional SAF persisted-grant/profile replacement: `18be5a8408306b2ccf661ca4b16b9798fc02e731`, run `38640276627`.
- PR #61 — explicit SAF profile-package picker UI: `0bdf02e74f59d79731c815bc3bd07494840435ca`, run `38648534948`.
- PR #62 — explicit Android USB permission owner: `0cee17f75fe3c38a214427982bdb0fd4c829a764`, run `38649649854`.
- PR #63 — USB candidate + permission UI: `dc6a5f21ad169bbf1866ac330cd2c167d48c4de7`, run `38650760935`.
- PR #64 — definition-driven runtime probe query compiler: `21070dcf9d84b462cf2f2db825d07a4dc39695de`, run `38685695091`.
- PR #65 — exact profile authority for definition-driven connect: `214f278090b66ffaff6af7a973880e2415255060`, run `38686491511`.
- PR #66 — Android USB authorized connection-session owner: `5afd1c8f628d4f1f56ca047b20707365e78c2881`, run `38687259217`.
- PR #67 — explicit Settings Connect / Disconnect / read-only Load UI, **including app-level callback/session wiring**: `68b401ff45349b88129ab08d08bfbfccd9fda78b`, run `38695474141`.
- PR #68 — retained authoritative live source-page images: `fbf8a5e6b1016e708a576b21ca1af47b1d353e2a`, run `38698255601`.
  - `TuneSourcePageImage` owns an exact deep copy of the complete verified source page set;
  - `EngineState` owns that raw source authority beside the semantic `TuneSession`, not inside the semantic table model;
  - live install validates/decodes completely before atomically replacing both semantic session and source page image;
  - failed decode, unknown signature, invalid engine handle, or caller-buffer mutation cannot partially replace or mutate retained authority;
  - initial offline/synthetic sessions have no source-page authority;
  - source-page mutation/inspection is not exposed as a production write API in this checkpoint.

PR #39 and PR #41 remain closed/superseded duplicate paths.

All qualified SHAs above are immutable checkpoints. Never append cleanup or handoff commits directly to them; branch from the exact qualified SHA.

---

# Reconciled earlier UI-integration snapshot

The earlier continuation snapshot around:

- qualified PR #51 `73587501ee40e1a8abbb14c57f3ee75507abfa57`;
- qualified PR #52 `b913873dde5d3b9c1da0c9612a6859263baa372a`;
- `phase2/tuner-live-source-ui-integration` at handoff-only commit `52a0bd0a0b6eac691972cf5a069c1aaa7ba1dade`;
- the observation that `TunerScreen` still called `OfflineTunerFixture`;

is valid historical context but is **not** the current continuation point.

That branch advanced and qualified as PR #53. The editor path is now catalog-driven through `ActiveTunerTable`; `OfflineTunerFixture` remains only as a compatibility name for callers/tests and delegates to the active engine-owned session. PR #54 then added the truthful UI-facing live-source status contract, and PR #55-#67 completed profile-package ownership, Android SAF/USB permission, exact-authorized connection ownership, and explicit Connect / Disconnect / Load presentation.

Do not redo the old “replace hardcoded offline table identity” work or create a second 2D/3D table model.

---

## Important #67 qualification note

An earlier #67 head `1a7e137f4f2f0c5dfbe01cb54a3e2608da089204` passed CI but was found during review to expose Connect/Disconnect/Load buttons whose `EpicHubApp` callbacks were still omitted, meaning the buttons used shell no-op defaults. It is **not** the qualified checkpoint.

Qualified #67 head `68b401ff...` fixes this by:

- owning `AndroidUsbLiveConnectionSessionOwner` in `EpicHubApp`;
- routing explicit Connect, Disconnect, and Load actions to it;
- running connection/load I/O off the UI thread;
- disconnecting the physical/live session before selected profile authority is replaced or cleared;
- keeping permission, connection, and tune load as separate explicit actions.

---

# Current continuation branch

`phase2/tuner-write-reconciliation-foundation`

Base: exact qualified PR #68 head `fbf8a5e6b1016e708a576b21ca1af47b1d353e2a`.

The first commit on this branch refreshes this handoff only. Treat PR #68 as the immutable source-page-image ownership checkpoint.

The read-only live path, active editor source, profile/package ownership, Android USB permission/connect/load UI, and retained exact source page image are already qualified. Do not reopen those boundaries unless a concrete defect is found.

The next work is a **write-to-RAM/readback/reconciliation design foundation**, not immediate write execution.

---

# Product architecture authority

```text
explicit profile package selection
    ↓
coherent compiled profile/protocol registry authority

USB transport candidate
    ↓
explicit Android USB permission
    ↓
descriptor-resolved CDC read-only channel
    ↓
explicit Connect
    ↓
definition-owned runtime probe query
    ↓
exact full live signature
    ↓
exact compiled bundle/profile selection
    ↓
authorized retained read-only USB connection session
    ↓
explicit Load tune
    ↓
read-only authoritative page acquisition
    ↓
complete verified page set
    ↓
retain exact TuneSourcePageImage
    +
compiled profile decode
    ↓
atomic LiveController TuneSnapshot + source-image install
    ↓
engine-owned TuneSession / TuneDraft
    ↓
active tune catalog / 2D + 3D editor
```

Keep these concepts separate:

1. **Profile package selection** — definitions EpicHub is allowed to understand.
2. **Transport candidate** — where a controller might be reachable.
3. **USB permission** — Android authorization to open the selected device.
4. **Runtime controller identity** — exact full live signature.
5. **Resolved bundle authority** — exact compiled profile/protocol selected by that signature.
6. **Physical connection ownership** — retained authorized read-only USB channel.
7. **Tune load** — separate explicit complete read/decode/atomic local install.
8. **Local live-source lifecycle** — connected/loading/live/failed/disconnected-retained truth.
9. **Retained source-page authority** — exact bytes from the successful live load that produced the current semantic session.
10. **Local draft state** — semantic edits in `TuneDraft`; not proof of ECU RAM state and not proof of persisted/Burned state.

USB VID/PID, product strings, board names, endpoint names, family names, and signature prefixes never grant profile/tune/write authority.

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
- profile selection != USB permission != transport connection != tune load;
- transport candidate != runtime identity != profile authority;
- changing/clearing profile authority disconnects any retained controller channel first;
- exact retained source pages are owned beside, not inside, the semantic `TuneSession`;
- raw source-page authority is replaced only together with a successfully decoded live source;
- caller-owned page buffers cannot mutate retained source authority after install.

Critical invariant:

```text
TuneDraft Apply != ECU RAM write != Burn
```

No qualified stage through PR #68 writes calibration RAM or issues Burn.

Do not reintroduce `ReusableContentHost`.

---

# Immediate next work — write/reconciliation foundation

Do **not** begin by sending a write command. First establish the authority and reconciliation model.

1. Audit the compiled definition/protocol write capabilities already available per exact bundle: page write command shape, page geometry, endianness, Burn/persist capability, and any readback command requirements. Missing capability must fail closed.
2. Define a page patch/diff model that starts from the retained exact `TuneSourcePageImage` and changes only definition-owned bytes corresponding to intentional semantic `TuneDraft` changes. Untouched bytes must remain byte-for-byte from the authoritative source image; never reconstruct/fabricate unknown page contents.
3. Reuse the existing validated page codec for bounded primitive encoding. Do not make raw offsets or UI table coordinates into write authority.
4. Define explicit state separation for:
   - local semantic `TuneDraft`;
   - retained source page image from the last successful load/readback;
   - planned ECU RAM patch;
   - ECU RAM state confirmed by readback/reconciliation;
   - persisted/Burned ECU state.
5. Design write-to-RAM as a separate explicit action/capability from local Apply. A local Apply must remain local even when connected.
6. Require a post-write readback/reconciliation path before treating ECU RAM as synchronized. Define how partial write failure, disconnect, stale runtime identity, CRC/status failure, and readback mismatch invalidate synchronization.
7. Keep Burn/persist as a later, separate explicit capability/action. Do not infer persisted state from a successful RAM write.
8. Exact runtime identity + exact coherent bundle authority must be revalidated for any write-capable operation; never authorize writes by VID/PID, board/family name, product string, endpoint, or signature prefix.
9. Preserve current disconnect semantics. Retained local draft/source data may remain usable offline, but any RAM/persist synchronization claim must become stale/unknown when the controller session is lost unless reconciliation proves otherwise after reconnect.
10. Add no automatic write/Burn side effects to existing 2D/3D editor operations.

The first implementation slice should preferably remain pure planning/authority code with tests over retained page images and compiled definitions before any Android USB write execution is introduced.
