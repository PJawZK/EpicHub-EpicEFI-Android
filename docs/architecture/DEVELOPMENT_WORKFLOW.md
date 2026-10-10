# EpicHub Development Workflow

## Purpose

EpicHub is developed as a clean-slate product with two deliberate implementation surfaces:

- the **native Android product** — Kotlin/Jetpack Compose + Rust;
- the **GitHub Pages product/interaction prototype** — modular TypeScript/HTML/CSS used for fast workflow/layout development.

The browser surface is a development/specification tool under D-026. It does not replace or enter the Android production runtime under D-023.

Earlier EpicDash, EpicDash-JZ, EpicEFI firmware and related projects remain evidence sources, not source-tree templates.

---

# Repository workflow

`main` is the integration branch.

Substantial work uses descriptive feature/task branches and PRs. Current work uses `phase2/ui-diagnostics-foundation` / draft PR #3.

Generated binaries, signing material, secrets, personal paths, device identifiers and local build state are not committed.

The repository is durable project memory. `docs/HANDOFF.md` is the single current handoff; Git history is the archive.

---

# Parallel development lanes

EpicHub work should now be separated intentionally:

```text
A. Pages product/UX lane
   layout + interaction + workflow + behavior contracts

B. Android performance/runtime lane
   Compose + Kotlin + Rust + JNI + lifecycle + physical performance

C. Production translation lane
   accepted Pages behavior → native implementation → qualification
```

These lanes may proceed in parallel.

A difficult native rendering/performance investigation does not freeze product/UI design. Likewise, a polished Pages feature is not production-complete until translated and qualified natively.

## Lane A — Pages prototype

Use `prototype/` to establish:

- information architecture;
- layouts and responsive presentation recipes;
- user workflows and terminology;
- interaction details;
- feature state transitions;
- empty/loading/error/stale/disconnected states;
- fixture-driven functional demonstrations;
- future TS-Shim interoperability through explicit adapters.

Prototype feature code must remain modular by responsibility according to `PAGES_PROTOTYPE_ARCHITECTURE.md`.

The browser surface may use TypeScript, DOM/CSS, fixture data, browser storage and external-tool serialization where useful. None of those choices become native runtime authority automatically.

## Lane B — native performance/runtime

Continue physical Android qualification independently:

- Macrobenchmark/Perfetto scenarios;
- Compose composition/recomposition/layout/draw behavior;
- allocation/GC/CPU/memory;
- JNI cost and ABI correctness;
- lifecycle/state restoration;
- Android platform ownership;
- Rust hot paths and data ownership.

Browser performance never substitutes for this evidence.

## Lane C — production translation

Once a substantial Pages feature is explicitly accepted, create/maintain a translation contract recording:

```text
accepted prototype feature/path
visual + interaction authority
inputs / outputs
actions
important state transitions
empty/loading/error/stale behavior
identity/provenance requirements
production owner(s)
Compose responsibility
Kotlin workflow/platform responsibility
Rust/domain responsibility
prototype-only shortcuts to discard
native qualification required
```

Then implement from scratch against the production owners/contracts rather than copying browser control flow mechanically.

---

# Pages file-growth workflow

Do not recreate the old pattern of one huge HTML file containing the whole application.

Permanent rules:

- `prototype/index.html` is document/mount only;
- `prototype/src/main.ts` is bootstrap only;
- shell/routing/composition belongs in `src/app/`;
- one functional route owns one feature directory under `src/features/`;
- feature-local state/actions/panels/styles stay feature-local until sharing is justified;
- common UI is extracted only after a real shared concept exists;
- fixture generation lives in adapters, not pages;
- TS-Shim/network/wire parsing lives in `adapters/ts-shim/`, not pages;
- avoid `utils.ts`, `helpers.ts`, giant global state and catch-all managers;
- a file around 350–500 lines is a responsibility-review signal, not a target.

Prefer several small permanent owners over a monolith that later requires a risky split.

---

# TS-Shim development workflow

The Pages foundation deliberately prepares for a future TS-Shim connection.

Before implementation:

1. review the actual shim external contract;
2. define the narrow typed prototype port(s) needed by features;
3. keep endpoint/reconnect/wire schema/serialization inside `prototype/src/adapters/ts-shim/`;
4. translate shim data immediately into transport-neutral prototype models;
5. keep the same feature workflows runnable against fixture adapters;
6. start read-only unless mutation identity/safety/verification is separately accepted.

If the shim uses JSON/WebSocket/HTTP, that is an explicit tooling/interoperability boundary permitted by D-023. It does not establish JSON/JS communication inside the Android product.

---

# Source authority

When historical/external sources disagree, default production authority remains:

1. firmware/protocol reality;
2. physically verified EpicDash-JZ behaviour;
3. exact generated INI/profile behaviour;
4. original EpicDash protocol/function intent;
5. other experimental/UI/tool/plugin repositories.

An **explicitly accepted EpicHub Pages feature** may become authority for its own UI/interaction/workflow behavior, but never for ECU/protocol/tune-memory truth merely because the browser demonstrates it.

Within EpicHub, follow `docs/architecture/README.md` authority ordering.

---

# Clean reimplementation rule — D-022

Before implementing inherited production behavior, follow `SOURCE_REIMPLEMENTATION_METHOD.md`:

```text
semantic behavior slice
→ trace old implementation/callers/state/lifecycle/errors/tests/bug history
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ define clean owner/API/state/thread/lifetime/failure contract
→ implement from scratch
→ qualify against protocol truth/required behavior/focused fixtures
→ regression-test historical failures
→ benchmark hot paths
→ accept narrow slice
```

Prohibited:

```text
copy old subsystem
→ make it compile
→ rename/refactor later
```

The Pages prototype does not bypass D-022 for production protocol/domain behavior. It may help define the target workflow after the underlying required behavior is understood.

---

# Runtime language / serialization rule — D-023

Production application logic remains Kotlin/Jetpack Compose + Rust.

Do not introduce JavaScript/WebView/DOM application runtime or serialized-document messaging as a convenience production architecture.

The TypeScript Pages prototype is explicitly outside that runtime boundary under D-026.

Before adding JSON, CBOR, XML, protobuf, MessagePack or another generic serializer to production, identify the explicit persistence/export/external-interoperability boundary that requires it.

Prototype-to-TS-Shim serialization is allowed only inside the external adapter boundary.

---

# Architecture changes

Architecture documents are implementation contracts. A change in responsibility, authority, dependency direction, transport ownership, safety policy, runtime-language policy, Pages/native boundary, serialization boundary or Kotlin/Rust boundary updates the corresponding documents in the same workstream.

New code must have an obvious permanent owner/home before implementation.

### Cross-document consistency rule

Before accepting any durable architecture change:

1. identify every document restating the changed concept;
2. update them together, or explicitly mark old text as historical evidence;
3. search the active branch/PR for superseded names, ownership statements and stale implementation order;
4. resolve contradictions rather than relying on chronology;
5. verify `GAP_ANALYSIS.md`, current handoff and active implementation contracts remain truthful.

Current stale-term checks include inappropriate production uses of `JSON`, `WebView`, `JavaScript`, `TypeScript`, browser runtime/state, old navigation counts, old phase status and obsolete authority wording.

TypeScript/JavaScript references under `prototype/` or D-026 documentation are legitimate and must not be mistaken for production-runtime leakage.

---

# Testing / qualification discipline

Every accepted slice gets the cheapest strong proof available before adding complexity.

Native examples:

- Rust/JVM unit tests;
- ABI/schema tests;
- fault-injection/lifecycle tests;
- Android CI builds;
- device/bench qualification;
- Macrobenchmark/Perfetto for hot UI paths.

Pages examples:

- TypeScript strict typecheck;
- Vitest contract/behavior tests;
- Vite production build;
- deterministic fixture states;
- screenshot/interaction review;
- later adapter contract tests for TS-Shim.

Pages tests prove prototype behavior, not Android performance/safety. Native tests prove production behavior, not that the product interaction is well designed.

---

# Prototype acceptance and translation

A Pages feature should not be translated merely because it renders.

Accept it when:

- the workflow is intentional;
- fixture/live authority is unmistakable;
- feature ownership is clear;
- visual nesting has not become state/dependency nesting;
- adapter details are absent from feature code;
- important failure/stale/empty states exist;
- the intended production owners are known.

After translation, compare the native feature against the accepted prototype and then qualify it physically.

---

# Chat handoff workflow

There is exactly one current handoff file:

`docs/HANDOFF.md`

When the user requests **"Create the EpicHub handoff"**:

1. inspect actual branch/head/PR/CI/physical qualification state;
2. inspect current Pages prototype state when relevant;
3. incorporate only durable decisions, completed work, unresolved issues and next action;
4. replace/update `docs/HANDOFF.md` rather than append stale history;
5. point to repository authority docs instead of duplicating them;
6. commit the handoff update;
7. return a minimal continuation instruction.

Preferred continuation instruction:

`Read PJawZK/EpicHub-EpicEFI-Android/docs/HANDOFF.md, verify the repository state, and continue from there.`

---

# Current development gate

Phase 1 is closed and the first Phase-2 native foundation is qualified.

Current work is intentionally split:

```text
Native lane:
continue PR #3 performance/physical qualification
→ accept a stable native foundation
→ continue D-022 production subsystem work

Pages lane:
modular prototype shell + fixture boundary
→ iterate Dashboard/Tuner/Tools/Logger/Analyzer/Diagnostics/Settings
→ accept feature behavior
→ translate mature slices into native production

Interop lane:
review exact TS-Shim contract
→ implement external adapter behind typed prototype ports
```

ECU mutation/Burn remains behind the single semantic tuning authority and is not granted to the browser prototype by default.
