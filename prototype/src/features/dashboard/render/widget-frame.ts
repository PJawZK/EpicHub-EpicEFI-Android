import type {
  DashboardDocument,
  DashboardVisualStyle,
  DashboardWidget,
} from "../model/dashboard-document";

export function createWidgetFrame(
  document: DashboardDocument,
  widget: DashboardWidget,
): HTMLElement {
  const element = documentNode("article");
  element.className = `dashboard-widget dashboard-widget--${widget.kind}`;
  element.dataset.widgetId = widget.id;
  element.style.left = `${(widget.rect.x / document.canvas.width) * 100}%`;
  element.style.top = `${(widget.rect.y / document.canvas.height) * 100}%`;
  element.style.width = `${(widget.rect.width / document.canvas.width) * 100}%`;
  element.style.height = `${(widget.rect.height / document.canvas.height) * 100}%`;
  element.style.zIndex = String(widget.zIndex);
  applyStyle(element, widget.style);
  return element;
}

function applyStyle(element: HTMLElement, style?: DashboardVisualStyle): void {
  if (!style) return;
  if (style.foreground) element.style.setProperty("--widget-foreground", style.foreground);
  if (style.background) element.style.setProperty("--widget-background", style.background);
  if (style.border) element.style.setProperty("--widget-border", style.border);
  if (style.accent) element.style.setProperty("--widget-accent", style.accent);
  if (style.warning) element.style.setProperty("--widget-warning", style.warning);
  if (style.danger) element.style.setProperty("--widget-danger", style.danger);
  if (style.fontScale) element.style.setProperty("--widget-font-scale", String(style.fontScale));
  if (style.opacity !== undefined) element.style.opacity = String(style.opacity);
}

function documentNode<K extends keyof HTMLElementTagNameMap>(tag: K): HTMLElementTagNameMap[K] {
  return window.document.createElement(tag);
}
