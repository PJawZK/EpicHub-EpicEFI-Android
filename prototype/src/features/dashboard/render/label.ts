import type { DashboardDocument, LabelWidget } from "../model/dashboard-document";
import { createWidgetFrame } from "./widget-frame";

export function renderLabel(dashboard: DashboardDocument, widget: LabelWidget): HTMLElement {
  const element = createWidgetFrame(dashboard, widget);
  element.classList.add("dashboard-label");
  element.textContent = widget.text;
  return element;
}
