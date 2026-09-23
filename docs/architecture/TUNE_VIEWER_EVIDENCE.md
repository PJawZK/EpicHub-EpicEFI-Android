# Tune Viewer Evidence

## Purpose

This document records reusable architecture and behaviour evidence from `PJawZK/PJawZK-EpicEFI-Tune-Viewer` for the clean-slate EpicHub application.

The Tune Viewer is a **reference implementation**, not a second tuning/profile authority and not a source-tree template. Its useful rules are normalized into EpicHub responsibilities; its React/browser implementation is not migrated wholesale.

## Reviewed source state

Repository: `PJawZK/PJawZK-EpicEFI-Tune-Viewer`

Reviewed `main`: `abfa325597d16cb362623c260ec92ed5bf5f37f1`

Package version: `1.0.0-rc.1`

The substantive V1 release-candidate hardening immediately before repository cleanup is commit `286de8663dc74856b9bad66cf8c2fb2612068f72`.

High-value reviewed files include:

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

The viewer consistently refuses to interpret a tune through an arbitrary or merely similar definition.

For local viewing:

```text
MSQ
→ parse firmware signature
→ resolve registry definition by exact signature
→ or require manually selected exact matching INI
→ compare definition signature with tune signature
→ only then expose semantic tune interpretation
```

For published tunes it additionally checks that catalog firmware identity, MSQ signature and selected/stored definition identity agree before interpretation.

No nearest-version fallback or board-name-based guessing is used.

### EpicHub consequence

Preserve the stronger already-established EpicHub authority model:

```text
live ECU / saved project
→ exact authoritative generated-profile identity
→ compiled semantic model
→ complete verified TuneSnapshot where required
→ semantic interpretation/edit eligibility
```

Tune Viewer confirms the value of exact identity gating, but its signature-only browser gate is not stronger than EpicHub's existing exact-profile fingerprint/session/TuneSnapshot rules.

---

# 2. Explicit semantic kinds prevent UI fallback mistakes

The viewer model distinguishes:

- scalar;
- bits;
- array;
- string;
- table;
- curve;
- dialog;
- menu.

Table, curve and dialog/menu structures are represented separately rather than inferred from a generic value at render time.

`TuneBrowser` resolves a target by explicit semantic type. It does not turn an unknown parameter into a curve/table merely because a renderer exists.

### EpicHub consequence

The clean tuner model should expose explicit semantic surface types from the authoritative profile compiler. Compose selects the correct component from that type; presentation must not guess a type from shape or fall back to a semantically different editor.

This directly supports the existing requirement that scalars remain scalars and tables/curves are intentional profile-defined structures.

---

# 3. Viewer parsing is capability evidence, not target parser authority

`src/msq.ts` contains a compact read-only MSQ parser. It extracts tune metadata and page constants and rejects malformed XML/missing required structure.

`src/ini.ts` demonstrates useful generated-INI constructs including:

- quoted/braced CSV-like token splitting;
- scalar/bits/array/string constants;
- page/offset/type/shape/units/scale/translate/min/max/digits metadata;
- bit-option macros and virtual label sets;
- table definitions;
- curve definitions;
- user-defined dialogs/panels;
- menu/group structures;
- condition-source extraction.

The condition evaluator is deliberately limited and may return unresolved/unknown rather than guessing.

### EpicHub consequence

Do **not** transplant this parser as a parallel Android/viewer parser.

EpicHub keeps one compatibility-tested generated-profile parser/compiler and one condition authority. Viewer/project-comparison surfaces consume compiled semantic output from that authority.

The viewer implementation is useful as an independent feature/fixture inventory and as cross-check evidence for generated-profile constructs.

---

# 4. Compact definition packs are a useful derived projection

The Tune Viewer build pipeline converts source `mainController.ini` files into compact generated definition packs.

Useful properties:

- exact firmware signature is the registry lookup identity;
- duplicate signatures are rejected while generating the registry;
- source metadata may carry ECU target/release/source-history information;
- constants use dictionaries for repeated kinds/types/units;
- tables, curves, menus, dialogs and label sets are compacted into a versioned pack;
- the pack is gzip-compressed;
- SHA-256 is calculated over the compressed bytes;
- the client verifies SHA-256 before decompression;
- loaded pack signature and declared definition/table counts are revalidated.

The generator uses the same `src/ini.ts` parser rather than maintaining a second build-only parser.

### EpicHub consequence

A compact compiled profile projection can be valuable for fast project/UI startup, search or comparison, but it remains **derived acceleration**:

```text
exact source profile + authoritative compiler
→ versioned compiled projection
→ identity/integrity metadata
→ disposable cache / packaged projection
```

It must never become an alternate tune/profile authority. Any projection must be bound to exact source/profile identity and be safely rebuildable.

---

# 5. Async work is generation guarded

`LocalTuneViewer` increments a load generation and checks it after asynchronous definition resolution. A result belonging to an older tune selection is discarded instead of overwriting the current one.

Published/catalog surfaces use equivalent active/cancel guards around asynchronous effects.

### EpicHub consequence

This reinforces an existing permanent rule: long-running profile/project/file/analyzer operations need request/session/generation identity. Completion alone does not make a result current.

Examples:

- profile compilation;
- project open;
- comparison loading;
- log/analyzer queries;
- metadata/catalog refresh;
- any operation whose source can change before completion.

---

# 6. Tune comparison has two deliberately different modes

`compareTunes()` declares a full exact-firmware mode only when:

```text
tune A signature == tune B signature
AND INI A signature == INI B signature
AND tune A signature == INI A signature
```

Under that exact-match condition it compares scalar settings, tables and curves.

When firmware differs it deliberately degrades to a shared-name non-array scalar intersection:

- both definitions must contain the name;
- arrays are excluded;
- tables are excluded;
- curves are excluded;
- units are omitted when the two definitions disagree;
- the UI explicitly warns that equal names do not prove equal semantics across firmware versions.

### EpicHub consequence

This is a strong safety/product rule, but EpicHub should use its stronger notion of **exact authoritative profile identity**, not only a signature string.

Recommended target policy:

```text
same exact profile identity
→ full semantic comparison may be offered

different profile identity
→ informational compatibility/intersection report only
→ never write authority
→ never automatic migration authority
```

Cross-profile array/table/curve comparison remains disabled unless EpicHub later defines and qualifies an explicit compatibility/migration contract.

---

# 7. Axis changes make positional table deltas ambiguous

For exact-firmware table comparison, Tune Viewer detects whether X or Y bins differ. It still computes/display deltas by positional array index and warns that the user must inspect both sides.

That is acceptable as a read-only viewer warning, but it is not a safe basis for automated interpretation.

### EpicHub consequence

If table axes differ, the default EpicHub semantic comparison should treat direct cell-by-cell delta as **non-comparable/ambiguous** unless an explicit transform exists.

A future regridding/interpolation comparison would require a separately defined policy including:

- axis identity/units;
- monotonicity/order assumptions;
- interpolation method;
- extrapolation policy;
- missing/out-of-range handling;
- numerical tolerance;
- clear indication that transformed values are derived rather than original tune cells.

No transformed comparison may become implicit write/migration authority.

---

# 8. Definition evolution comparison is useful diagnostic evidence

`definitionCompare.ts` compares definitions by stable semantic identity and reports added/removed/changed:

- settings;
- tables;
- curves;
- dialogs;
- menus.

Its stable serialization sorts object keys so property-order changes do not become false semantic differences.

The accompanying test covers added/removed/changed items, property-order stability and previous-firmware ordering by target/date.

### EpicHub consequence

Profile-definition diffing belongs in compatibility/developer/project-migration diagnostics. It can explain why a project differs after firmware/profile change, but it does not itself prove value compatibility or authorize migration.

Useful target outputs include:

- structural profile diff;
- exact identity/provenance of each side;
- changed storage/scale/bounds/options/shape;
- changed tuner surfaces/conditions;
- explicit compatibility status per semantic target.

---

# 9. Visualization lessons are semantic, not renderer architecture

The reviewed Tune Viewer does **not** contain a separate Plotly/Three.js/3D calibration renderer.

Its current tune visualization is primarily:

- two-dimensional HTML calibration grids;
- SVG curve plots;
- definition-driven labels/units/axis ranges;
- data-derived padded axis ranges when explicit ranges are unavailable;
- bounded tick counts;
- explicit incomplete/invalid-shape messages.

For table presentation, Y may be displayed high-to-low while the underlying tune array remains in authoritative definition order.

### EpicHub consequence

Do not port the React/SVG implementation as the Compose renderer.

Transfer these rules instead:

- validate shape before rendering;
- semantic axes/labels/units come from the compiled profile;
- display order is presentation state only;
- incomplete structures render as explicit unavailable/error states;
- curve scale fallback is presentation behavior, not tune mutation;
- 3D table inspection remains an independent Compose presentation feature already covered by `UI_INTERACTION_EVIDENCE.md` and D-016.

---

# 10. Comparison/export output is derived evidence

Tune Compare can generate a JSON report containing:

- file labels;
- firmware signatures;
- definition labels;
- exact-match mode;
- changed/unchanged counts;
- changed scalar summaries;
- changed table/curve summaries;
- names only present on one side.

It intentionally does not export the complete tune contents and remains read-only.

### EpicHub consequence

Project/tune comparison reports are useful portable **derived evidence**. They do not become project authority, TuneSnapshot authority, ECU state evidence or a write plan.

The eventual EpicHub report schema should be versioned and include exact source/profile/project fingerprints sufficient to reproduce or invalidate the interpretation.

---

# 11. Catalog/library state is convenience state

The web Tune Hub library validates catalog schema and duplicate IDs, supports explicit index invalidation, detects lineage cycles and maintains browser-session overrides/hiding.

Those mechanisms are useful web-library behaviour, not local EpicHub project authority.

### EpicHub consequence

Transfer only the general rules:

- validate versioned catalog/index schemas;
- make cache invalidation explicit;
- detect cycles in lineage graphs;
- bind derived data to source identity;
- never let cached metadata override exact local project/profile authority.

---

# 12. Test/fixture value and limits

Current Tune Viewer tests provide useful policy examples for:

- definition structural diff;
- firmware identity/history ordering;
- publication/validation/lifecycle metadata;
- tune structure policy.

The definition-pack generator also includes a small self-test covering signature, constants, table, curve, dialog and menu preservation.

These are useful seeds, but they are **not** a sufficient EpicHub generated-INI compatibility corpus. EpicHub still needs genuine profile fixtures, malformed/unsupported cases, canonical compiled-output goldens and tune-comparison goldens across representative firmware generations.

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
| JSON comparison report | derived comparison export | Rust comparison schema + Kotlin SAF/share transport |
| web catalog cache | optional remote/catalog feature only | separate product feature; never project authority |

---

# Intentionally not transferred

Do not migrate:

- React component structure;
- browser DOM/SVG rendering path as EpicHub's native renderer;
- DOMParser-based MSQ parsing as a parallel tune authority;
- `src/ini.ts` as a second generated-profile authority;
- the limited viewer condition evaluator as write eligibility authority;
- browser catalog caches as project state;
- signature-only equality as a substitute for EpicHub's stronger profile fingerprint/authority model;
- positional table deltas across changed axes as an automated semantic conclusion;
- any assumption that this repository already supplies EpicHub's desired 3D tuner renderer.

---

# Remaining feature-entry/deferred work exposed by this review

These items are intentionally classified by `GAP_ANALYSIS.md`; they are not unresolved Phase 1 ownership questions.

1. Define the exact comparison identity contract for saved projects/tunes: source profile fingerprint, compiled-profile fingerprint, tune fingerprint and project generation.
2. Define table comparison behavior when axes differ. Default should be non-comparable unless a separately qualified regridding mode is requested.
3. Build executable golden fixtures for same-profile scalar/bit/array/table/curve comparison.
4. Build cross-profile compatibility fixtures proving that unsupported/incompatible structures fail conservatively.
5. Decide whether a compact compiled-profile projection materially improves Android startup/search enough to justify a persisted cache format.
6. Define a versioned derived comparison-report schema if project/tune compare is retained as a product feature.
7. Keep profile-definition evolution diffing diagnostic/read-only until a separately qualified migration system exists.

## Phase 1 conclusion

Tune Viewer contributes **identity, semantic classification, comparison and derived-cache lessons**, not a second parser stack or renderer architecture.

The strongest transferable rule is:

> Full semantic tune comparison requires exact authoritative profile identity. Cross-profile comparison is informational and conservative; it never grants edit, migration or ECU write authority.
