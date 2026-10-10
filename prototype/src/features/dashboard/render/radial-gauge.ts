import type { RuntimeSnapshot } from "../../../model/runtime";
import type { DashboardDocument, RadialGaugeWidget } from "../model/dashboard-document";
import { createWidgetFrame } from "./widget-frame";
import { clampNormalized, dashboardValueState } from "./widget-state";
import { formatDashboardValue, resolveDashboardValue } from "./widget-value";

export function renderRadialGauge(
  dashboard: DashboardDocument,
  widget: RadialGaugeWidget,
  snapshot: RuntimeSnapshot | null,
): HTMLElement {
  const element = createWidgetFrame(dashboard, widget);
  const resolved = resolveDashboardValue(snapshot, widget.binding);
  const normalized = clampNormalized(resolved.value, widget.minimum, widget.maximum);
  const sweep = 270;
  const start = 225;
  const angle = start + normalized * sweep;

  element.dataset.valueState = dashboardValueState(resolved.value, widget.thresholds);
  element.style.setProperty("--gauge-angle", `${angle}deg`);

  const dial = document.createElement("div");
  dial.className = "dashboard-radial__dial";

  const needle = document.createElement("span");
  needle.className = "dashboard-radial__needle";

  const center = document.createElement("span");
  center.className = "dashboard-radial__center";

  const minimum = document.createElement("small");
  minimum.className = "dashboard-radial__minimum";
  minimum.textContent = String(widget.minimum);

  const maximum = document.createElement("small");
  maximum.className = "dashboard-radial__maximum";
  maximum.textContent = String(widget.maximum);

  const text = document.createElement("div");
  text.className = "dashboard-radial__text";

  const title = document.createElement("span");
  title.className = "dashboard-widget__title";
  title.textContent = widget.title ?? resolved.label;

  const value = document.createElement("strong");
  value.className = "dashboard-widget__value";
  value.textContent = formatDashboardValue(resolved);

  const unit = document.createElement("span");
  unit.className = "dashboard-widget__unit";
  unit.textContent = resolved.unit;

  text.append(title, value, unit);
  dial.append(needle, center, minimum, maximum, text);
  element.append(dial);
  return element;
}
