# EpicHub Dashboard Model and TunerStudio Import

Status: foundation contract

## Purpose

EpicHub Dashboard is reimplemented as an EpicHub-owned declarative dashboard system. It does not embed TunerStudio, execute TunerStudio JavaScript, or use a WebView as the production dashboard runtime.

The browser prototype uses TypeScript because it runs in a browser, but dashboard documents themselves are data. The production Android implementation translates the same accepted dashboard concepts into Compose/Kotlin/Rust without a JavaScript runtime.

## Dashboard document authority

The prototype `DashboardDocument` is the initial executable specification for the dashboard model. A document owns:

- source canvas width/height and background;
- ordered widgets and z-order;
- exact source-space x/y/width/height geometry;
- channel bindings;
- display labels and units;
- numeric precision;
- min/max ranges;
- warning/critical thresholds;
- widget visual style overrides;
- source provenance.

Source-space coordinates are preserved instead of immediately converting an imported layout into a responsive grid. Rendering scales the complete canvas while retaining relative position and size. This is required for visual-layout conversion.

## Initial widget vocabulary

The initial EpicHub vocabulary is intentionally small and native:

- radial gauge;
- digital value;
- linear bar;
- indicator;
- static label.

Later widget families may be added when the product needs them. A TunerStudio style name does not automatically become an EpicHub renderer class; multiple foreign styles can map to one EpicHub widget family plus style properties.

## Editing model

Dashboard editing should recreate the useful parts of TunerStudio Designer rather than reproduce its implementation.

The intended workflow is:

1. Enter Dashboard Edit mode.
2. Add a widget from the EpicHub widget palette.
3. Select one or more widgets.
4. Move and resize directly on the source canvas.
5. Use a right-side inspector for channel, range, thresholds, precision and appearance.
6. Lock widgets or the full layout.
7. Duplicate/delete/alignment operations act on the selection.
8. Exit Edit mode to return to the low-interaction Drive presentation.

Editor state is not ECU state. Layout changes are local dashboard-document mutations and never enter the ECU tuning mutation/Burn path.

## TunerStudio `.dash` compatibility

Compatibility is implemented as conversion, not emulation:

```text
TunerStudio .dash file
        ↓
format parser
        ↓
foreign dashboard intermediate representation
        ↓
conversion + import report
        ↓
EpicHub DashboardDocument
        ↓
EpicHub renderer/editor
```

The importer must preserve when available:

- source dashboard dimensions/aspect ratio;
- widget x/y/width/height;
- z-order;
- gauge/channel identity;
- title and units;
- min/max and warning/critical limits;
- decimal precision;
- colors and basic styling;
- background/indicator/gauge image assets;
- text/font intent where legally and technically usable.

Channel identity and visual geometry are separate concerns. An imported widget can preserve its exact visual position even when its original TunerStudio output channel has not yet been mapped to an EpicHub runtime channel.

## No foreign code execution

Import is data-only.

- No JavaScript from a dashboard is executed.
- No TunerStudio expressions are evaluated merely because they appear in an imported file.
- No click action is given controller authority automatically.
- No raw page/offset/byte action becomes an EpicHub action.
- No external file is allowed to bypass EpicHub warning, command or tuning authorities.

If a foreign feature cannot be mapped safely, the importer records it as unsupported.

## Import report

Conversion returns both the converted document and structured issues. Unsupported or approximate mappings must never disappear silently.

Examples:

- exact layout / exact binding;
- exact layout / channel unresolved;
- exact layout / approximate gauge style;
- image retained / font substituted;
- click action not imported;
- expression-backed value unresolved;
- unknown component preserved as unsupported metadata in a future parser stage.

This deliberately separates visual fidelity from functional fidelity.

## TS-Shim relationship

TS-Shim is a runtime data/command adapter, not the dashboard layout engine.

A converted dashboard may bind imported TunerStudio channel names through a mapping layer to EpicHub channel identity. TS-Shim can later satisfy those EpicHub channel capabilities, but the Dashboard feature does not know WebSocket URLs, JSON keys or TunerStudio protocol details.

```text
DashboardDocument widget binding
        ↓
EpicHub channel identity
        ↓
RuntimeSource port
        ↓
Fixture / TS-Shim / future native source
```

## Next qualification step for `.dash`

Do not guess the complete TunerStudio file schema from screenshots or documentation. Before implementing the parser, qualify against real exported `.dash` files used by this project, including at least:

- a simple stock-gauge layout;
- a layout with background/custom gauge images;
- indicators;
- multiple gauge styles;
- custom fonts if available;
- action-bearing widgets if available.

The parser should be fixture-tested against those files, with conversion snapshots proving geometry and property preservation.
