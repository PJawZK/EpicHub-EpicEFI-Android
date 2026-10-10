import type { RuntimeSnapshot } from "../../../model/runtime";
import type { DashboardDocument, IndicatorWidget } from "../model/dashboard-document";
import { createWidgetFrame } from "./widget-frame";
import { resolveDashboardValue } from "./widget-value";

function indicatorActive(widget: IndicatorWidget, value: number | null): boolean {
  if (value === null || !Number.isFinite(value)) return false;
  if (widget.activeAbove !== undefined && value >= widget.activeAbove) return true;
  if (widget.activeBelow !== undefined && value <= widget.activeBelow) return true;
  return false;
}

export function renderIndicator(
  dashboard: DashboardDocument,
  widget: IndicatorWidget,
  snapshot: RuntimeSnapshot | null,
): HTMLElement {
  const element = createWidgetFrame(dashboard, widget);
  const resolved = resolveDashboardValue(snapshot, widget.binding);
  const active = indicatorActive(widget, resolved.value);
  element.dataset.active = String(active);

  const light = document.createElement("span");
  light.className = "dashboard-indicator__light";

  const text = document.createElement("div");
  text.className = "dashboard-indicator__text";

  const title = document.createElement("span");
  title.className = "dashboard-widget__title";
  title.textContent = widget.title ?? resolved.label;

  const state = document.createElement("strong");
  state.textContent = active ? (widget.activeText ?? "ON") : (widget.inactiveText ?? "OFF");

  text.append(title, state);
  element.append(light, text);
  return element;
}
