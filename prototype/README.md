# EpicHub Pages Prototype

This directory contains EpicHub's GitHub Pages product/interaction prototype.

It is a development/specification surface, **not** the production Android runtime. Accepted behavior is translated into Kotlin/Jetpack Compose + Rust according to `docs/architecture/PAGES_PROTOTYPE_ARCHITECTURE.md`.

## Goals

- iterate layout and workflows quickly;
- keep feature ownership aligned with production EpicHub;
- avoid single-file HTML/TypeScript/CSS monoliths;
- use deterministic fixtures before live connectivity exists;
- provide explicit adapter seams for a future TS-Shim connection;
- preserve clear fixture/live/stale/error truth in the UI.

## Structure

```text
src/
├── main.ts
├── app/          bootstrap composition, routing and shell orchestration
├── model/        transport-neutral prototype semantic types
├── ports/        typed capabilities consumed by features
├── adapters/     fixtures and future external-tool adapters
├── ui/           genuinely shared presentation primitives
├── features/     one directory per EpicHub functional route
└── styles/       global tokens/base/shell only
```

Feature-local code should remain feature-local. Do not move code into `ui/` or another shared location merely to shorten a feature file.

`index.html` and `src/main.ts` must stay intentionally small.

## Commands

From this directory:

```bash
npm install
npm run dev
npm run typecheck
npm test
npm run build
```

The toolchain versions intentionally match the current EpicScope web foundation where practical so both projects use a familiar lightweight TypeScript/Vite workflow.

## Source modes

The first source mode is `FIXTURE`. A future TS-Shim adapter belongs under `src/adapters/ts-shim/` and must implement the typed ports rather than leaking shim transport/wire details into pages.

The prototype must always make source authority visible. Fixture values must not look like verified live ECU truth.

## File-growth rule

A file around 350–500 lines is a responsibility-review signal. Split by reason-to-change before simply extending a large file. Avoid generic dumping grounds such as `utils.ts`, `helpers.ts`, `manager.ts` or a global all-feature state object.
