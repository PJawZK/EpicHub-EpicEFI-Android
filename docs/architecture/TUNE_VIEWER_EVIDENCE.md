# Tune Viewer Evidence

## Purpose

This document records reusable architecture and behaviour evidence from `PJawZK/PJawZK-EpicEFI-Tune-Viewer` for EpicHub.

The Tune Viewer is a **reference implementation**, not a second tuning/profile authority and not a source-tree template. Its TypeScript/React/browser/JSON implementation technologies describe that reference system only. EpicHub production runtime remains Kotlin/Jetpack Compose + Rust under D-003/D-023, and serialization is introduced only at explicit persistence/export/interoperability boundaries.

## Reviewed source state

Repository: `PJawZK/PJawZK-EpicEFI-Tune-Viewer`

Reviewed `main`: `abfa325597d16cb362623c260ec92ed5bf5f37f1`

Package version: `1.0.0-rc.1`

Substantive V1 release-candidate hardening before repository cleanup: `286de8663dc74856b9bad66cf8c2fb2612068f72`.

High-value reviewed files included:

- `src/model.ts`;
- `src/msq.ts`;
- `src/ini.ts`;
- `src/condition.ts`;
- `src/definitionRegistry.ts`;
- `src/definitionCompare.ts`;
- `src/LocalTuneViewer.tsx`;
- `src/TuneBrowser.tsx`;
- `src/TuneCompare.tsx`;
- `src/compare.ts`;
- `src/tuneLibrary.ts`;
- `src/PublishedTunePage.tsx`;
- `scripts/build-definition-registry.mjs`;
- `scripts/test-definition-compare.mjs`.

---

# 1. Exact identity gates interpretation

The viewer refuses to interpret a tune through an arbitrary or merely similar definition.

Local flow:

```text
MSQ
→ parse firmware signature
→ resolve registry definition by exact signature
→ or require manually selected exact matching INI
→ compare definition signature with tune signature
→ only then expose semantic interpretation
```

Published tunes additionally require catalog firmware identity, MSQ signature and selected/stored definition identity to agree.

No nearest-version fallback or board-name guessing is used.

### EpicHub consequence

EpicHub preserves the stronger authority model already established elsewhere:

```text
live ECU / saved project
→ exact authoritative generated-profile identity
→ compiled semantic model
→ complete verified TuneSnapshot where required
→ semantic interpretation/edit eligibility
```

Tune Viewer confirms the value of exact identity gating, but its signature-only browser gate does not replace EpicHub's exact source/profile fingerprint, session and TuneSnapshot authority.

---

# 2. Explicit semantic kinds prevent editor fallback mistakes

The viewer distinguishes:

- scalar;
- bits;
- array;
- string;
- table;
- curve;
- dialog;
- menu.

Tables, curves and dialog/menu structures are represented explicitly rather than inferred from a generic value during rendering.

### EpicHub consequence

The authoritative profile compiler exposes explicit semantic surface types. Compose chooses a component from the semantic type and must not guess a type from shape or fall back to a semantically different editor.

This supports the existing requirement that parameters remain parameters and tables/curves are intentional profile-defined structures.

---

# 3. Viewer parsing is capability evidence, not target parser authority

The viewer's read-only MSQ/INI parsers demonstrate useful constructs and failure cases, including:

- firmware-signature extraction;
- page constants;
- scalar/bits/array/string definitions;
- offset/type/shape/units/scale/translate/min/max/digits metadata;
- bit-option macros and virtual label sets;
- table and curve definitions;
- dialogs/panels;
- menus/groups;
- condition-source extraction.

Its condition evaluator is deliberately limited and can return unresolved/unknown rather than guessing.

### EpicHub consequence

Do **not** transplant these TypeScript parsers as a parallel Android/viewer authority.

EpicHub keeps one compatibility-tested generated-profile parser/compiler and one condition authority. Viewer/project comparison consumes compiled semantic output from that authority.

The viewer remains useful as independent construct/fixture inventory and cross-check evidence.

---

# 4. Compact definition packs are useful derived projections

The Tune Viewer build pipeline converts source `mainController.ini` files into compact generated definition packs.

Useful properties:

- exact firmware signature is registry identity;
- duplicate signatures are rejected;
- source metadata may carry ECU target/release/history information;
- repeated kinds/types/units use dictionaries;
- tables, curves, menus, dialogs and label sets are compacted into a versioned pack;
- the pack is gzip-compressed;
- SHA-256 covers the compressed bytes;
- the client verifies SHA-256 before decompression;
- loaded signature and declared counts are revalidated.

### EpicHub consequence

A compact compiled-profile projection may be useful for startup/search/comparison, but it remains **derived acceleration**:

```text
exact source profile + authoritative compiler
→ versioned compiled projection
→ identity/integrity metadata
→ disposable cache / packaged projection
```

It never becomes alternate tune/profile authority. Persistence is justified only by measured benefit and remains exact-source-bound and rebuildable.

D-023 also applies: the eventual projection format is chosen deliberately at the persistence boundary; a historical viewer JSON/browser representation does not select EpicHub's format.

---

# 5. Async work is generation guarded

`LocalTuneViewer` increments a load generation and discards asynchronous completion belonging to an older selection. Published/catalog surfaces use equivalent active/cancel guards.

### EpicHub consequence

Long-running profile/project/file/analyzer work carries request/session/generation identity. Completion alone does not make a result current.

Examples include:

- profile compilation;
- project open;
- tune comparison;
- log/analyzer queries;
- metadata/catalog refresh;
- any operation whose source can change before completion.

---

# 6. Tune comparison deliberately separates exact and cross-profile modes

`compareTunes()` permits a full exact-firmware mode only when tune and definition signatures agree on both sides.

Under that exact condition it compares scalars, tables and curves.

When firmware differs it deliberately degrades to a shared-name non-array scalar intersection:

- both definitions contain the name;
- arrays excluded;
- tables excluded;
- curves excluded;
- units omitted when definitions disagree;
- UI warns that equal names do not prove equal semantics across firmware versions.

### EpicHub consequence

EpicHub uses the stronger gate of **exact authoritative profile identity**, not only a signature string:

```text
same exact profile identity
→ full semantic comparison may be offered

different profile identity
→ informational compatibility/intersection only
→ never write authority
→ never automatic migration authority
```

Cross-profile arrays/tables/curves remain non-comparable unless a separately specified and qualified transformation/compatibility contract exists.

---

# 7. Axis changes make positional table deltas ambiguous

The viewer detects changed X/Y bins but may still show positional array-index deltas with a warning. That is acceptable for a read-only viewer but is not a safe automated interpretation.

### EpicHub consequence

If table axes differ, direct cell-by-cell delta is **non-comparable/ambiguous by default**.

Any future regridding/interpolation comparison requires explicit rules for:

- axis identity/units;
- monotonicity/order;
- interpolation method;
- extrapolation policy;
- missing/out-of-range handling;
- numerical tolerance;
- provenance indicating transformed values are derived.

Transformed comparison never silently becomes tune data, migration authority or write authority.

---

# 8. Definition evolution comparison is useful diagnostic evidence

The viewer compares definitions by stable semantic identity and reports added/removed/changed settings, tables, curves, dialogs and menus. Stable serialization prevents property-order changes from creating false semantic differences.

### EpicHub consequence

Profile-definition diffing belongs in compatibility/developer/project-migration diagnostics. It can explain why profiles differ but does not prove value compatibility or authorize migration.

Useful outputs include:

- structural profile diff;
- exact identity/provenance of each side;
- changed storage/scale/bounds/options/shape;
- changed tuner surfaces/conditions;
- explicit compatibility disposition per semantic target.

---

# 9. Visualization lessons are semantic, not renderer architecture

The reviewed Tune Viewer does **not** contain a separate Plotly/Three.js/3D calibration engine.

Its visualization is primarily:

- two-dimensional HTML grids;
- SVG curves;
- definition-driven labels/units/axis ranges;
- data-derived padded ranges when explicit ranges are unavailable;
- bounded tick counts;
- explicit incomplete/invalid-shape messages.

Presentation may display Y high-to-low while underlying tune arrays remain in authoritative definition order.

### EpicHub consequence

Do not port React/SVG/browser rendering as EpicHub's renderer.

Transfer only the semantic rules:

- validate shape before render;
- axes/labels/units come from compiled profile;
- display order is presentation state only;
- incomplete structures render explicit unavailable/error state;
- scale fallback is presentation behavior, not tune mutation;
- 3D table inspection is an independent Compose presentation feature governed by `UI_INTERACTION_EVIDENCE.md` and D-016.

---

# 10. Comparison/export output is derived evidence

Historical Tune Compare can generate a **JSON report** containing file/firmware/definition labels, exact-match mode, change counts and scalar/table/curve summaries. That JSON choice is a fact about the Tune Viewer implementation.

It intentionally stays read-only and does not export the complete tune contents.

### EpicHub consequence

Project/tune comparison reports are useful portable **derived evidence**. They never become project authority, TuneSnapshot authority, ECU state proof or a write plan.

EpicHub's eventual comparison-report schema must:

- be versioned;
- include exact source/profile/project fingerprints sufficient to reproduce/invalidate the interpretation;
- use a deliberately selected encoding at the explicit export boundary;
- not default to JSON merely because the reference viewer did.

If a human-readable rendering is needed, generate it from the same typed immutable comparison result rather than maintaining a separate authority.

---

# 11. Catalog/library state is convenience state

The web Tune Hub library validates catalog schema/duplicate IDs, supports explicit index invalidation, detects lineage cycles and maintains browser-session overrides/hiding.

Those are useful web-library behaviours, not local EpicHub project authority.

### EpicHub consequence

Transfer only the general rules:

- validate versioned catalog/index schemas;
- make cache invalidation explicit;
- detect cycles in lineage graphs;
- bind derived data to source identity;
- never let cached metadata override exact local project/profile authority.

Browser-session/cache implementation details are not transferred.

---

# 12. Test/fixture value and limits

Tune Viewer tests provide useful policy examples for:

- definition structural diff;
- firmware identity/history ordering;
- publication/validation/lifecycle metadata;
- tune structure policy.

Its definition-pack generator also self-tests representative signature/constants/table/curve/dialog/menu preservation.

These are useful seeds but are **not** a sufficient EpicHub generated-INI compatibility corpus. EpicHub still needs genuine profile fixtures, malformed/unsupported cases, canonical compiled-output goldens and tune-comparison goldens across representative firmware generations.

---

# Permanent responsibility assignments

| Viewer evidence | EpicHub responsibility | Target owner |
|---|---|---|
| exact signature definition gate | exact profile/project interpretation gate | Rust profile/session/project authority |
| explicit scalar/bits/array/table/curve types | compiled semantic tuner model | Rust profile compiler/tune model |
| compact hashed definition pack | optional derived compiled-profile projection/cache | Rust profile/project persistence domain |
| load generation guard | stale-result rejection | initiating Kotlin/Rust operation owner via generation/request ID |
| exact-profile tune compare | semantic project/tune comparison | Rust tune/project comparison service |
| cross-profile scalar intersection | informational compatibility comparison | Rust comparison/analysis service; read-only |
| definition structural diff | profile evolution diagnostics | Rust profile diagnostics/comparison |
| 2D grid/SVG curve behaviour | presentation rules only | Compose tuner/compare UI |
| historical JSON comparison report | **versioned derived comparison export**; encoding selected only at explicit export boundary | Rust comparison schema + Kotlin SAF/share transport |
| web catalog cache | optional remote/catalog feature only | separate product feature; never project authority |

---

# Intentionally not transferred

Do not migrate:

- React component structure;
- browser DOM/SVG rendering as EpicHub native renderer;
- DOMParser-based MSQ parsing as parallel tune authority;
- `src/ini.ts` as a second generated-profile authority;
- limited viewer condition evaluation as write eligibility authority;
- browser catalog caches as project state;
- signature-only equality as substitute for stronger EpicHub profile authority;
- positional table deltas across changed axes as automated semantic conclusion;
- any assumption that this repository supplies EpicHub's desired 3D tuner renderer;
- JSON/browser state architecture as EpicHub runtime or default persistence design.

---

# Remaining feature-entry/deferred work exposed by this review

These are tracked by `GAP_ANALYSIS.md`; they are not ownership ambiguities.

1. Define the exact comparison identity tuple for saved projects/tunes.
2. Keep changed-axis tables non-comparable by default unless a qualified regridding mode is explicitly introduced.
3. Build same-profile scalar/bit/array/table/curve comparison goldens.
4. Build cross-profile incompatibility fixtures.
5. Persist compact compiled-profile projection only if startup/search measurements justify it.
6. Define a versioned derived comparison-report schema if comparison export is retained.
7. Keep profile-definition evolution diffing diagnostic/read-only until a separately qualified migration system exists.

## Current conclusion

Tune Viewer contributes **identity, semantic classification, comparison and derived-cache lessons**, not a second parser stack, renderer architecture or serialization architecture.

The strongest transferable rule remains:

> Full semantic tune comparison requires exact authoritative profile identity. Cross-profile comparison is informational and conservative; it never grants edit, migration or ECU write authority.