# EpicHub GitHub Pages Prototype Architecture

## Status

This document defines the first-class **GitHub Pages product/interaction prototype** used to design EpicHub while the Android product continues to be implemented and performance-qualified natively.

Authority: D-003 through D-006, D-016, D-022 through D-026.

The browser prototype is a development and specification surface. It is not a second production EpicHub runtime and it does not weaken the Kotlin/Compose + Rust production contract.

---

# 1. Why this exists

EpicHub development now runs in three coordinated lanes:

```text
Pages product/UX lane
→ rapidly establish layout, interaction, feature workflow and behavior contracts

Android performance/runtime lane
→ qualify Compose/Rust/JNI/rendering/allocation/lifecycle behavior on physical devices

Production translation lane
→ implement accepted Pages behavior natively against EpicHub owners/contracts
```

This separation lets product work continue while native performance investigation remains rigorous. Browser performance is useful for the prototype itself but is never evidence that the equivalent Compose implementation is fast enough.

---

# 2. Authority model

The Pages prototype may become authority for explicitly accepted:

- information architecture;
- layout and visual hierarchy;
- interaction behavior;
- user workflow and terminology;
- control placement and feature discoverability;
- responsive presentation recipes;
- feature-level state transitions that do not claim ECU/runtime truth.

The Pages prototype is **not** authority for:

- ECU protocol behavior;
- tune memory/page/offset semantics;
- firmware/profile identity;
- Android lifecycle/permission behavior;
- Kotlin↔Rust/JNI representation;
- production logging/file-format internals;
- production performance;
- safety or mutation authority;
- persistence formats merely because the prototype uses browser storage or JSON.

Accepted browser behavior is translated into Compose/Kotlin/Rust rather than embedded through WebView or retained as a hidden JavaScript state engine.

---

# 3. Physical repository layout

The browser surface lives under `prototype/` so its status is unmistakable:

```text
prototype/
├── index.html                 # mount point only; no application logic
├── package.json
├── tsconfig.json
├── vite.config.ts
├── README.md
├── src/
│   ├── main.ts                # bootstrap only
│   ├── app/                   # composition root, routing and shell orchestration
│   ├── model/                 # transport-neutral prototype semantic types
│   ├── ports/                 # capability interfaces consumed by features
│   ├── adapters/
│   │   ├── fixtures/          # deterministic/demo data sources
│   │   └── ts-shim/           # future external TS-Shim interoperability adapter
│   ├── ui/                    # genuinely shared presentation primitives
│   ├── features/
│   │   ├── dashboard/
│   │   ├── tuner/
│   │   ├── tuning-tools/
│   │   ├── logger/
│   │   ├── analyzer/
│   │   ├── diagnostics/
│   │   └── settings/
│   └── styles/                # global tokens/base/shell only
└── tests/                     # behavior/contract tests
```

Feature-specific state, actions, rendering and styling stay in that feature directory. A feature may split further into `view`, `state`, `actions`, `model`, `panels`, `components` or similar files when real responsibility justifies it.

Do not create empty package forests simply to match this drawing.

---

# 4. Dependency direction

```text
app composition root
├─ features
├─ adapters
└─ shared ui

features → model + ports + shared ui
adapters → model + ports
ports    → model
model    → no DOM, no transport, no feature UI
```

Forbidden shortcuts:

```text
feature → TS-Shim wire schema
feature → fixture implementation
feature → another feature's mutable state as a gateway
adapter → page DOM
model → browser transport
one giant global store containing every feature's private interaction state
```

The same domain-separation rule used by Android applies here:

```text
Analyzer prototype ≠ Logger state extension
Tuning Tools prototype ≠ Tuner state extension
```

They may share lower-level ports/models while remaining independently owned.

---

# 5. Anti-monolith rules

The prototype must not regress into one large HTML file plus one giant script and stylesheet.

Mandatory structure rules:

- `index.html` remains a mount/document shell only;
- `main.ts` remains bootstrap/dependency-composition only;
- routing and shell orchestration stay in `app/`, not in feature files;
- each functional route owns its own feature directory;
- shared UI is extracted only when more than one feature genuinely shares the concept;
- feature-specific CSS remains feature-local once it becomes substantial;
- global CSS is limited to tokens, reset/base rules and shell-wide layout;
- browser transport, reconnect, wire parsing and serialization stay in adapters;
- fixture generation stays outside feature code;
- no permanent `utils.ts`, `helpers.ts`, `manager.ts`, `app-state.ts` or equivalent dumping ground.

A file approaching roughly **350–500 lines** is a responsibility-review signal. It is not an automatic split threshold, but growth beyond it needs a clear reason. More important than line count is whether a file has more than one independent reason to change.

When a feature grows, split by responsibility before adding another large section to the same file.

---

# 6. Shared behavior, not shared browser implementation

The prototype should mirror production semantic boundaries where useful, but browser source is not copied mechanically into Kotlin/Rust.

For each accepted substantial feature, record a translation contract covering:

```text
feature / accepted prototype path
visual + interaction authority
inputs
outputs
user actions
state machine / important states
error/empty/loading states
identity/provenance requirements
production owner(s)
Compose responsibility
Kotlin platform/workflow responsibility
Rust/domain responsibility
prototype-only behavior to discard
qualification required after translation
```

This makes the prototype an executable specification rather than an accidental second codebase.

---

# 7. Fixture/demo truth

The prototype starts from fixture adapters so feature work is not blocked by ECU connectivity.

Fixture-backed state must be unmistakably labelled, for example:

```text
FIXTURE
DEMO
SYNTHETIC
OFFLINE PROTOTYPE
```

A fixture may model realistic workflows, errors and edge cases, but it may not present itself as verified/live ECU truth.

Deterministic fixtures are preferred for repeatable tests and screenshots.

---

# 8. TS-Shim groundwork

The Pages prototype is intentionally structured so a future TS-Shim connection can be added as an **adapter**, not woven through the UI.

Conceptual path:

```text
EpicHub feature
→ typed prototype port
→ adapters/ts-shim
→ explicit external transport/protocol
→ TS-Shim
→ ECU / TunerStudio-compatible source
```

The TS-Shim adapter owns, as applicable:

- endpoint discovery/configuration;
- connection/reconnect behavior;
- WebSocket/HTTP or other transport mechanics;
- external wire schema/version negotiation;
- serialization/deserialization;
- capability discovery;
- translation from shim messages into prototype semantic models;
- explicit errors/staleness/disconnect state.

Feature code consumes semantic ports and must not know socket URLs, JSON keys, request opcodes or shim-specific connection state.

If the shim uses JSON, WebSocket messages or another serialized format, that is an explicit **external tooling interoperability boundary** permitted by D-023. It does not select JSON or a browser message bus for the Android production runtime.

The initial shim integration should be read-only unless and until command/mutation capability, identity, safety and verification contracts are separately accepted. A browser prototype must never gain raw ECU page/offset/byte authority merely because the shim can expose it.

The same port can be backed by fixtures or TS-Shim, which allows identical UI/workflow tests to run without hardware and then against a live external source.

---

# 9. GitHub Pages deployment

The prototype is built as a static Vite/TypeScript site and may be published through GitHub Pages.

Pages deployment is independent from Android Foundation qualification:

```text
prototype changes
→ TypeScript typecheck
→ prototype tests
→ Vite production build
→ optional GitHub Pages deployment

Android changes
→ existing Android/Rust/Foundation quality gates
```

A Pages failure does not redefine Android runtime correctness, and an Android performance pass does not prove browser prototype behavior.

The initial Pages workflow is deliberately manual until repository Pages settings are confirmed. Once stable, deployment may be promoted to automatic `main` publication.

---

# 10. Relationship to EpicScope

EpicHub intentionally borrows the successful **modular web-development method** used by EpicScope: small bootstrap, separate app/adapters/components/pages/state-style responsibilities, TypeScript, Vite and GitHub Pages.

It does **not** copy EpicScope domain ownership. EpicHub keeps its own Dashboard/Tuner/Tuning Tools/Logger/Analyzer/Diagnostics contracts and Android production architecture.

Reusable interaction ideas may later be deliberately shared or reimplemented when their semantics match, but neither repository becomes a dumping ground or hidden dependency of the other.

---

# 11. First implementation order

The prototype should grow in vertical slices rather than one huge shell file:

```text
1. shell + navigation + fixture source boundary
2. Dashboard
3. Tuner
4. Tuning Tools
5. Logger
6. Analyzer
7. Diagnostics
8. Settings / connection-policy surfaces
9. TS-Shim adapter once its external contract is ready
```

This order is not a requirement that one feature be visually final before another can start. It defines safe ownership and integration seams.

---

# 12. Acceptance rule

A Pages feature is ready to translate when:

- its user workflow is deliberate rather than a placeholder accident;
- fixture/demo truth is explicit;
- state ownership is clear;
- it does not depend on another feature merely because of visual nesting;
- adapter-specific details do not leak into the feature;
- important empty/error/stale/disconnected states are represented;
- the translation contract identifies native production owners;
- browser-only shortcuts to discard are explicit.

Production acceptance still requires native implementation and Android/device qualification.

## Summary

> GitHub Pages is EpicHub's fast product and interaction laboratory. The repository keeps it modular, typed and adapter-driven so accepted behavior translates cleanly into the native Android product and so a future TS-Shim can plug in at an explicit external interoperability boundary rather than infecting feature code.
