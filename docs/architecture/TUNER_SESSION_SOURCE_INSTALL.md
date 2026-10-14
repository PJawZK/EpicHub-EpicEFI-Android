# TuneSession source installation and semantic table catalog

## Purpose

This slice turns a resolved tune snapshot into an explicit engine-session authority switch.

The operation is intentionally not represented as a table edit:

```text
source bytes
    ↓
profile decoder / full validation
    ↓
TuneSnapshot candidate
    ↓
atomic session replacement
    ↓
new TuneSession
 ├─ immutable baseline
 └─ new TuneDraft revision 0
```

A source switch and a `TuneDraft` edit have different semantics and histories. They must never share an undo stack.

## Atomic install rule

`install_mega144h7_imported_pages` resolves the complete candidate snapshot before it obtains mutation authority over the active engine session.

If decoding or validation fails:

- the existing `TuneSession` remains installed;
- its baseline remains unchanged;
- its draft revision remains unchanged;
- its Undo/Redo history remains available;
- no partial table replacement is possible.

Only after the candidate `TuneSnapshot` is valid is the existing `TuneSession` replaced in one assignment.

The new session starts with:

- the resolved source snapshot as immutable baseline;
- an independent `TuneDraft` cloned from that baseline;
- revision `0`;
- empty Undo/Redo history.

## Current source authority

The first public install path is explicitly named **imported** Mega144H7 tune pages. It therefore installs snapshots with `TuneSnapshotOrigin::ImportedFile`.

This is deliberate. The same profile decoder can later be fed by verified live ECU page acquisition, but this slice does not label arbitrary page bytes as live controller state.

The startup synthetic table remains only as the fallback session created before a real source is installed. After a successful install it is no longer part of the active TuneSession: semantic table ID `7` disappears and the resolved Mega144H7 semantic table IDs become authoritative.

## JNI boundary

The Kotlin/native source transaction is coarse:

```text
page 1 ByteArray ─┐
                  ├─ Kotlin direct buffers ── one JNI install call ── Rust resolver/session swap
page 2 ByteArray ─┘
```

The native bridge validates exact page capacities again. The current Mega144H7 source is 55,764 + 5,000 bytes, so copying the two source buffers once at the authority boundary is intentionally preferred over exposing page fragments or per-value JNI calls.

No table values are transported individually across JNI.

## Semantic table catalog

A source switch changes which semantic calibration objects exist. Kotlin therefore discovers tables from the active TuneSession through one packed, versioned catalog buffer.

Catalog ABI v1 contains:

- ABI version;
- header and entry sizes;
- table count;
- profile generation;
- current draft revision;
- source origin;
- total byte size;
- sorted table entries with semantic table ID, rows, columns, display precision and UTF-8 name range.

The catalog is sorted by semantic table ID so its ordering is deterministic even though the underlying snapshot uses a map.

The first resolved Mega144H7 catalog contains:

- 1001 — VE Table;
- 1002 — Ignition Table;
- 1003 — Target Lambda / AFR;
- 1004 — MAP Estimate.

The synthetic fallback catalog contains only table 7 until a source install occurs.

## Generation and revision

The catalog publishes both `profile_generation` and `draft_revision` because they answer different questions:

- `profile_generation` identifies the resolved calibration definition/profile authority;
- `draft_revision` identifies edits within the currently installed TuneSession.

A source replacement resets draft revision to zero. UI state and future preview tokens must therefore also bind to the profile/source generation rather than treating revision alone as globally unique.

## Authority boundary

Still explicitly:

`source install != TuneDraft Apply != ECU RAM write != Burn`

This slice does not add:

- ECU RAM writes;
- Burn/nonvolatile persistence;
- live controller page acquisition;
- MSQ XML parsing;
- automatic startup import;
- profile migration;
- source checkpoint/restore UI.

## Qualification expectations

Regression coverage proves:

- successful install replaces baseline, draft and history together;
- old synthetic table IDs are absent after install;
- resolved semantic tables are available after install;
- failed decode preserves the old session and its Undo history;
- source replacement is engine-local;
- the packed catalog follows the installed session generation/revision;
- invalid engine handles cannot create hidden sessions;
- Kotlin rejects malformed catalog ranges.
