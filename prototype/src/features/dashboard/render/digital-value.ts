import type { RuntimeSnapshot } from "../../../model/runtime";
import type { DashboardDocument, DigitalValueWidget } from "../model/dashboard-document";
import { createWidgetFrame } from "./widget-frame";
import { dashboardValueState } from "./widget-state";
import { formatDashboardValue, resolveDashboardValue } from "./widget-value";

export function renderDigitalValue(
  dashboard: DashboardDocument,
  widget: DigitalValueWidget,
  snapshot: RuntimeSnapshot | null,
): HTMLElement {
  const element = createWidgetFrame(dashboard, widget);
  const resolved = resolveDashboardValue(snapshot, widget.binding);
  element.dataset.valueState = dashboardValueState(resolved.value, widget.thresholds);

  const title = document.createElement("span");
  title.className = "dashboard-widget__title";
  title.textContent = widget.title ?? resolved.label;

  const valueRow = document.createElement("div");
  valueRow.className = "dashboard-digital__value-row";

  const value = document.createElement("strong");
  value.className = "dashboard-widget__value dashboard-digital__value";
  value.textContent = formatDashboardValue(resolved);

  const unit = document.createElement("span");
  unit.className = "dashboard-widget__unit";
  unit.textContent = resolved.unit;

  valueRow.append(value, unit);
  element.append(title, valueRow);
  return element;
}
