# EpicHub Current Handoff

## Continuation instruction

Read this file first, then verify the exact `main`, `current-work`, and Foundation state before changing anything.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Branch model:

- `main` = latest fully qualified baseline.
- `current-work` = the only active development branch and must remain based on / ahead of `main`.
- Historical commits, closed PRs, and workflow runs are the audit trail; do not recreate long-lived phase/feature branches.
- Every meaningful `current-work` checkpoint must pass the complete Foundation workflow on its exact SHA before it is qualified.
- Advance `main` only to an exact already-qualified `current-work` SHA.
- `fetch_commit_workflow_runs` is not authoritative here because Foundation is push-triggered; query the Actions workflow/runs directly.

---

# Latest qualified implementation — 2026-10-17

Latest qualified implementation checkpoint:

`9c5e332b737085e93ab84531199909b9d5b5c9a6`

Foundation quality run:

`38982149150`

That exact SHA passed the full Foundation gate:

- exact source SHA verification;
- Rust format;
- Rust Clippy with warnings denied;
- all Rust tests;
- Android JVM tests;
- Android debug build;
- Android performance build;
- Android Macrobenchmark harness build;
- APK signature verification;
- production ARM native-library verification;
- physical benchmark-kit preparation;
- all qualified artifact publication.

`main` and `current-work` were both at that SHA before this handoff-only documentation update.

Important recent qualified checkpoints:

- `5b585b6096c862d704d90fec61c7e6bdd8edb10e`, Foundation `38949529443` — firmware-backed Burn envelope codec/verifier. Burn request is definition-owned; EpicEFI Burn success status is `4`, not normal command success `0`; Burn ACK is not persistence proof.
- `d894c50b683e814723ca58faa13a30e64265e7e5`, Foundation `38954935032` — opaque native guarded Burn plan/store + JNI + Android owner. Android receives only opaque-handle-owned staged requests and cannot construct arbitrary Burn geometry.
- `c8c4a8bf52b0734aa09875b2eb48fc434b9bed96`, Foundation `38981181683` — serialized physical USB Burn executor. Exact sequence is Burn request/ACK verification, then full-page post-Burn CRC request/verification, all inside `EngineAuthoritySerialization`.
- `9c5e332b737085e93ab84531199909b9d5b5c9a6`, Foundation `38982149150` — high-level live Burn routing through exact live identity and the retained authorized USB session, with no Burn UI exposure.

Earlier qualified history contains the complete volatile RAM-write chain, explicit RAM provenance/synchronization state, zero-prefix interruption invalidation, serialized JIT authority → USB transfer → reconciliation, and the visible Write-to-RAM workflow. Do not rebuild those boundaries.

---

# Core invariants

Keep these distinctions explicit:

`TuneDraft Apply != ECU RAM write != Burn / nonvolatile persistence`

Additional authority invariants:

- Rust remains semantic tuning and ECU definition/profile authority.
- Exact full runtime signature selects the exact compiled definition/profile/protocol bundle.
- Definition-owned semantic IDs and page protocol metadata own geometry; Android/UI must not construct raw page/offset commands.
- One engine-owned `TuneSession` remains the semantic editing authority.
- The retained exact source page image remains the byte authority for guarded writes and Burn eligibility.
- RAM writes remain guarded by retained-before precondition read, exact verification, write ACK verification, post-write readback, exact verification, and verified-prefix reconciliation.
- A Burn is eligible only for a verified live-controller source whose RAM provenance confirms the current draft revision.
- Burn is definition-page scoped. Multi-page ECUs may have distinct `B%2i` commands and may have pages with no Burn capability.
- Burn request framing and response verification remain native/definition-owned.
- EpicEFI Burn success ACK status is `4`; ordinary status `0` must not be accepted as Burn success.
- Burn ACK alone never proves persistence.
- Current persistence proof contract is: verified Burn-specific ACK followed by a matching definition-owned full-page post-Burn CRC response for the exact guarded source page.
- All physical Burn transfer and native verification run under `EngineAuthoritySerialization`, preventing tune/profile/source authority mutation across the operation.
- Burn is not exposed in Compose/UI yet.

---

# Current Burn architecture

The qualified path now reaches the retained physical USB channel:

```text
verified live TuneSession + exact retained source image
    ↓
RAM provenance confirms current draft revision
    ↓
plan_selected_engine_guarded_page_burn(...)
    ↓
opaque GuardedBurn plan handle
    ↓
NativeGuardedBurn (Android opaque owner)
    ↓
EngineAuthoritySerialization::exclusive
    ↓
stage definition-owned framed Burn request
    ↓
retained authorized UsbReadOnlyTransactionChannel
    ↓
verify Burn-specific ACK (status 4)
    ↓
stage definition-owned full-page CRC request
    ↓
same retained authorized USB channel
    ↓
verify CRC matches exact guarded source page
    ↓
UsbGuardedBurnResult.PersistedAndVerified
```

The high-level routing is deliberately below UI:

- `NativeLiveTuneBurnOperation` creates/owns the opaque guarded Burn plan and invokes the serialized executor.
- `LiveTuneConnectionOwner.burnPageToNonvolatile(...)` resolves the exact live runtime identity from lifecycle state; connected-but-not-loaded state cannot Burn.
- `AndroidUsbLiveConnectionSessionOwner.burnPageToNonvolatile(...)` supplies only the already-retained authorized USB channel.
- A disconnected, closed, stale, non-live, RAM-unconfirmed, profile-stale, source-changed, protocol-failed, CRC-failed, or transport-failed path remains explicit and fail-closed.

Focused JVM tests prove:

- Burn stage must succeed before CRC stage executes;
- rejected Burn ACK stops before CRC;
- CRC mismatch is not reported as persistence success;
- transport failure remains stage-specific;
- invalid timeout fails before native/USB work;
- one serializer scope covers both physical stages and both native verification calls;
- live routing forwards exact live identity, internal definition page, and caller/retained channel;
- connected-but-not-loaded, closed, and disconnected states cannot reach Burn authority.

---

# Immediate next correctness boundary

Before recording persistent/nonvolatile provenance or exposing any Burn control in the UI, tighten the opaque native Burn handle into a native proof-state machine.

Required native sequence:

```text
Planned
  ↓ verified Burn-specific ACK
BurnAckVerified
  ↓ verified matching post-Burn full-page CRC
PostBurnCrcVerified
```

Why this is required:

- the Kotlin physical executor already executes Burn then CRC in the correct order;
- however the opaque native handle currently exposes Burn and CRC verification as independent calls;
- native persistence evidence must not be recordable from a CRC response alone;
- native authority should enforce the proof sequence even if a caller misuses the JNI wrapper.

Recommended next slice:

1. Add execution/proof state to the opaque guarded Burn plan store, not Kotlin bookkeeping.
2. Reject CRC verification before Burn ACK has been successfully verified.
3. Prefer making the handle one-shot after full proof: no second Burn/CRC staging from a completed plan.
4. Preserve the existing lock-order rule: retain plan/state safely, release the plan-store mutex before entering profile/engine authority, then transition state only after successful native verification.
5. Add dedicated native error/status mapping for invalid Burn proof-stage ordering rather than collapsing it into transport failure.
6. Test out-of-order CRC, duplicate/repeated stages, stale authority during each transition, close/race behavior, and normal `Planned → BurnAckVerified → PostBurnCrcVerified` flow.

Only after that state machine is qualified should persistent provenance be recorded.

---

# Persistence provenance after proof-state qualification

There is currently no native nonvolatile persistence provenance model. Do not treat the existing `RamSourceProvenance` as proof of flash/nonvolatile state.

The next layer after the proof-state machine should record native evidence tied to at least:

- exact profile generation;
- exact draft revision;
- definition page number;
- exact page CRC32;
- proof method (`Burn ACK + post-Burn page CRC`);
- session/source authority that produced the evidence.

Important lifecycle semantics:

- fresh controller page acquisition proves current RAM bytes, not nonvolatile persistence; persistence evidence should begin unknown unless separately verified;
- local draft edits should make a previously verified persisted revision visibly older, not erase history;
- RAM writes should not automatically claim persistence;
- a new source/session/profile install must invalidate persistence evidence belonging to the previous authority;
- re-Burning one page should update that page's persistence evidence without claiming unrelated pages are persisted;
- only exact post-Burn CRC verification after the required Burn ACK may advance persistence evidence.

A separate app-facing persistence snapshot/state can then be designed, analogous to RAM synchronization state but page-aware and explicitly nonvolatile.

---

# UI boundary — still intentionally not started

Do not add a Burn button yet.

Before user-facing Burn:

1. qualify native Burn proof-state ordering;
2. qualify nonvolatile persistence provenance and an app-facing read-only state projection;
3. decide how the selected semantic table maps to definition page(s) without presenting raw page numbers as normal UX;
4. define confirmation and failure presentation that clearly distinguishes:
   - local Apply;
   - volatile RAM current;
   - nonvolatile page persistence verified;
   - unknown/stale persistence;
5. keep multi-page/partial Burn truthful — never claim the whole tune is persisted when only one page has proof.

---

# Repository discipline

At the start of the next session:

1. inspect `main` and `current-work` heads;
2. inspect the latest Foundation run on `current-work`;
3. if this handoff-only SHA is fully green, promote that exact SHA to `main` and verify both refs;
4. if it is not green, fix only the exact failing gate;
5. continue with the native guarded-Burn proof-state machine above.

Do not recreate old phase branches, old RAM-write work, or the already-qualified Burn planner/envelope/opaque-wrapper/USB-routing layers.
