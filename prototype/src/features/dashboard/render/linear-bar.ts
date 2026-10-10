import type { RuntimeSnapshot } from "../../../model/runtime";
import type { DashboardDocument, LinearBarWidget } from "../model/dashboard-document";
import { createWidgetFrame } from "./widget-frame";
import { clampNormalized, dashboardValueState } from "./widget-state";
import { formatDashboardValue, resolveDashboardValue } from "./widget-value";

export function renderLinearBar(
  dashboard: DashboardDocument,
  widget: LinearBarWidget,
  snapshot: RuntimeSnapshot | null,
): HTMLElement {
  const element = createWidgetFrame(dashboard, widget);
  const resolved = resolveDashboardValue(snapshot, widget.binding);
  const normalized = clampNormalized(resolved.value, widget.minimum, widget.maximum);
  element.dataset.valueState = dashboardValueState(resolved.value, widget.thresholds);
  element.dataset.orientation = widget.orientation;
  element.style.setProperty("--bar-fill", `${normalized * 100}%`);

  const title = document.createElement("span");
  title.className = "dashboard-widget__title";
  title.textContent = widget.title ?? resolved.label;

  const track = document.createElement("div");
  track.className = "dashboard-linear__track";
  const fill = document.createElement("span");
  fill.className = "dashboard-linear__fill";
  track.append(fill);

  const value = document.createElement("strong");
  value.className = "dashboard-linear__value";
  value.textContent = `${formatDashboardValue(resolved)} ${resolved.unit}`.trim();

  element.append(title, track, value);
  return element;
}
