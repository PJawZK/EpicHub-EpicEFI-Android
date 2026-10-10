import type { RuntimeSnapshot } from "../../../model/runtime";
import type { DashboardDocument, DashboardWidget } from "../model/dashboard-document";
import { renderDigitalValue } from "./digital-value";
import { renderIndicator } from "./indicator";
import { renderLabel } from "./label";
import { renderLinearBar } from "./linear-bar";
import { renderRadialGauge } from "./radial-gauge";

export function renderDashboardCanvas(
  dashboard: DashboardDocument,
  snapshot: RuntimeSnapshot | null,
): HTMLElement {
  const viewport = document.createElement("div");
  viewport.className = "dashboard-canvas-viewport";

  const canvas = document.createElement("div");
  canvas.className = "dashboard-canvas";
  canvas.style.aspectRatio = `${dashboard.canvas.width} / ${dashboard.canvas.height}`;
  canvas.style.background = dashboard.canvas.background;
  canvas.dataset.dashboardId = dashboard.id;

  for (const widget of [...dashboard.widgets].sort((a, b) => a.zIndex - b.zIndex)) {
    canvas.append(renderWidget(dashboard, widget, snapshot));
  }

  viewport.append(canvas);
  return viewport;
}

function renderWidget(
  dashboard: DashboardDocument,
  widget: DashboardWidget,
  snapshot: RuntimeSnapshot | null,
): HTMLElement {
  switch (widget.kind) {
    case "radial-gauge":
      return renderRadialGauge(dashboard, widget, snapshot);
    case "digital-value":
      return renderDigitalValue(dashboard, widget, snapshot);
    case "linear-bar":
      return renderLinearBar(dashboard, widget, snapshot);
    case "indicator":
      return renderIndicator(dashboard, widget, snapshot);
    case "label":
      return renderLabel(dashboard, widget);
  }
}
