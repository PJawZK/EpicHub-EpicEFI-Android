# EpicHub UI Foundation Review

Status: **current visual shell rejected; architecture contracts retained**

## Why this review exists

The first EpicScope-inspired Pages shell was promoted too quickly from an implementation experiment into an implied accepted product direction. That was incorrect.

The current shell implementation under `prototype/src/app`, `prototype/src/ui` and `prototype/src/styles` is useful only as implementation evidence. Its visual composition, density, control placement and responsive behavior are **not accepted UI authority**.

This review does not invalidate D-023 through D-026, the four-workspace/six-route information architecture, the modular Pages architecture, or the declarative Dashboard model. It resets only the shared visual/application-shell foundation.

## Retained foundation

The following remain valid unless separately changed:

- Android production runtime = Kotlin/Jetpack Compose + Rust;
- Pages = modular product/interaction lab, not production runtime;
- four primary workspaces: Dashboard, Tuner, Logger, Diagnostics;
- six distinct domain routes: Dashboard, Tuner, Tuning Tools, Logger, Analyzer, Diagnostics;
- Settings remains global;
- Analyzer and Tuning Tools remain separately owned despite visual grouping;
- fixture/demo/source provenance must be unmistakable;
- Dashboard document/import model remains separate from ECU mutation authority;
- source/transport adapters remain outside feature UI code;
- anti-monolith structure under `prototype/` remains valid.

## Rejected assumptions in the current shell

### 1. One 54 px row was chosen before hierarchy was solved

The existing shell compresses workspace selection, leaf-route context, local-route switching, source authority and global actions into one row. That is an implementation shortcut, not a justified product hierarchy.

The architecture contract explicitly allows workspace-local controls to occupy their own contextual layer. The visual structure must be derived from user tasks and the four presentation classes rather than from a predetermined single-row header.

### 2. Desktop EpicScope density was copied too literally

EpicScope is useful evidence for:

- strong information hierarchy;
- keeping the main work surface large;
- contextual rather than indiscriminate controls;
- a product/logo switcher;
- compact, low-noise chrome.

It is **not** dimensional authority for EpicHub. EpicHub is Android-first and must be designed for touch, phone/tablet geometry and in-car readability. Small desktop-style 9–10 px labels and 30–34 px controls cannot be accepted by analogy alone.

### 3. Global and feature-local actions were mixed

The current shared header contains Dashboard Edit, Screen Lock, reserved Problem Capture and Settings together.

That is not yet justified:

- Dashboard Edit is feature-local presentation/document state;
- Screen Lock has no accepted interaction contract yet;
- Problem Capture is a global capability but the current disabled placeholder is not product value;
- Settings is truly global.

Their eventual placement must follow responsibility and frequency, not available empty space.

### 4. Disabled/future controls consume permanent chrome

The disabled Problem control is especially weak. EpicScope's product rules correctly reject empty/disabled top-level placeholders before the capability exists. EpicHub should apply the same principle.

A future capability may reserve architecture/state ownership without occupying permanent visible UI.

### 5. Source identity/provenance was treated as a badge-placement problem

`FIXTURE` visibility is mandatory, but the source model is broader than a centered badge. The shared foundation must define how the user understands:

- fixture/demo/offline/live state;
- ECU/source identity;
- connection quality/state;
- stale/invalid data;
- logging/recording state when relevant;
- warning/incident state when relevant.

Only after that semantic hierarchy is defined should placement be chosen.

### 6. Bottom status bar was added without a proven responsibility

The current bottom strip repeats route/source information and permanently consumes vertical space. It has not earned a permanent place.

A bottom bar should exist only if it owns information/actions that are persistent, high-value and not already communicated more effectively elsewhere.

### 7. Route identity is duplicated

For nested workspaces the current shell can show the route name beside `EpicHub` and again inside the local route selector. This adds chrome without adding orientation.

Workspace identity, leaf-route identity and local switching need one coherent treatment.

### 8. Responsive behavior was treated as subtraction

The current CSS mostly hides or truncates items at breakpoints. That is not a phone/tablet presentation system.

`UI_FOUNDATION_LAYOUT.md` already states that Tablet Landscape, Tablet Portrait, Phone Landscape and Phone Portrait may each have deliberate presentation authority. The next foundation must design those classes explicitly instead of starting from desktop width and removing items until it fits.

### 9. Interaction states are placeholders rather than product contracts

Current `screenLocked` and `dashboardEditing` dataset state prove wiring only. They do not define:

- what becomes non-interactive;
- what remains reachable;
- how lock is entered/exited safely;
- how edit mode changes navigation/chrome;
- whether edit/lock survive route/orientation/lifecycle changes.

Visual controls must not be treated as accepted until those semantics exist.

### 10. The shell was evaluated as isolated chrome instead of an application frame

A shared shell cannot be judged without representative content occupying it. The next foundation review must exercise at least:

- Dashboard drive surface;
- Tuner editing surface;
- Logger/Analyzer dense data surface;
- Diagnostics evidence surface;
- Settings overlay/surface;
- disconnected/fixture/live/error states.

The shell is successful only if these very different work surfaces fit naturally inside it.

## EpicScope lessons to retain

Use EpicScope as principle evidence, not as a template.

Retain:

- logo/product switcher can be effective;
- prioritize the active workflow rather than every possible feature;
- maximize meaningful work-surface area;
- avoid permanent placeholders for capabilities that do not exist yet;
- keep global context global and pane/feature context local;
- use contextual controls near the work they affect;
- do not duplicate active context in multiple chrome locations.

Do not inherit automatically:

- exact header height;
- desktop button sizes;
- tiny interface typography;
- hover-first behavior;
- desktop breakpoint assumptions;
- Logger-specific toolbar composition;
- EpicScope domain ownership.

## Required foundation sequence before another shell implementation

### A. Define shell responsibilities first

For every candidate item classify it as one of:

- permanent global identity/navigation;
- global runtime/status truth;
- workspace-local navigation;
- route-local action;
- transient/contextual action;
- overlay/sheet/settings action;
- not visible until capability exists.

Do not place controls before this classification is complete.

### B. Define the four presentation classes explicitly

Create shell wireframes/contracts for:

1. Tablet Landscape
2. Tablet Portrait
3. Phone Landscape
4. Phone Portrait

These may share components but do not have to share the same chrome geometry.

### C. Define minimum interaction/readability constraints

Before styling:

- touch target policy;
- primary/secondary text scale policy;
- glance/readability priority for in-car use;
- icon-only control rules;
- landscape/portrait collapse rules;
- safe-area/system-bar handling;
- keyboard/mouse behavior for the Pages prototype without letting desktop needs dictate Android geometry.

### D. Exercise the shell with representative route surfaces

Build minimal structural fixtures for each major workspace before polishing one feature. This is specifically to expose bad shared-shell assumptions early.

### E. Only then establish visual tokens

Color, spacing, typography, surface elevation and control styling should follow the structural decisions rather than define them prematurely.

## Acceptance gate for the replacement foundation

The shared foundation is not accepted until all of these are true:

- global vs workspace-local vs route-local responsibilities are visibly distinct;
- Dashboard/Tuner/Logger/Diagnostics all fit without the shell favoring one domain;
- the four presentation classes are intentionally designed rather than breakpoint leftovers;
- touch/readability constraints are explicit and met;
- source/provenance remains unmistakable in every presentation class;
- no disabled/future placeholder consumes permanent top-level chrome;
- no route/source information is duplicated merely to fill a bar;
- feature-local actions do not leak into global chrome without a deliberate reason;
- the work surface remains dominant;
- the user explicitly accepts the replacement base before feature-depth work resumes.

## Current implementation treatment

Until replacement work is accepted:

- treat `prototype/src/ui/top-bar.ts`, `workspace-menu.ts`, `route-nav.ts`, `source-status.ts`, `status-bar.ts` and associated shell CSS as **rejected visual implementation**, not design authority;
- do not refine the rejected shell incrementally;
- do not translate it into Compose;
- preserve it only long enough to support comparison/migration while the replacement foundation is established;
- the declarative Dashboard model may remain in the branch, but its current surrounding shell does not establish Dashboard presentation authority.
