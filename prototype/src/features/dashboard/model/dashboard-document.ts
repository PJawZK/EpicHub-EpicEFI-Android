export type DashboardWidgetKind =
  | "radial-gauge"
  | "digital-value"
  | "linear-bar"
  | "indicator"
  | "label";

export interface DashboardRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DashboardThresholds {
  lowCritical?: number;
  lowWarning?: number;
  highWarning?: number;
  highCritical?: number;
}

export interface DashboardVisualStyle {
  foreground?: string;
  background?: string;
  border?: string;
  accent?: string;
  warning?: string;
  danger?: string;
  fontScale?: number;
  opacity?: number;
}

export interface DashboardChannelBinding {
  channelId: string;
  label?: string;
  unit?: string;
  decimals?: number;
}

interface DashboardWidgetBase {
  id: string;
  kind: DashboardWidgetKind;
  rect: DashboardRect;
  zIndex: number;
  locked?: boolean;
  title?: string;
  style?: DashboardVisualStyle;
}

export interface RadialGaugeWidget extends DashboardWidgetBase {
  kind: "radial-gauge";
  binding: DashboardChannelBinding;
  minimum: number;
  maximum: number;
  thresholds?: DashboardThresholds;
}

export interface DigitalValueWidget extends DashboardWidgetBase {
  kind: "digital-value";
  binding: DashboardChannelBinding;
  thresholds?: DashboardThresholds;
}

export interface LinearBarWidget extends DashboardWidgetBase {
  kind: "linear-bar";
  binding: DashboardChannelBinding;
  minimum: number;
  maximum: number;
  orientation: "horizontal" | "vertical";
  thresholds?: DashboardThresholds;
}

export interface IndicatorWidget extends DashboardWidgetBase {
  kind: "indicator";
  binding: DashboardChannelBinding;
  activeAbove?: number;
  activeBelow?: number;
  activeText?: string;
  inactiveText?: string;
}

export interface LabelWidget extends DashboardWidgetBase {
  kind: "label";
  text: string;
}

export type DashboardWidget =
  | RadialGaugeWidget
  | DigitalValueWidget
  | LinearBarWidget
  | IndicatorWidget
  | LabelWidget;

export interface DashboardCanvasDefinition {
  width: number;
  height: number;
  background: string;
}

export interface DashboardSourceIdentity {
  kind: "epichub" | "tunerstudio-import";
  fileName?: string;
  importedAtMs?: number;
}

export interface DashboardDocument {
  schemaVersion: 1;
  id: string;
  name: string;
  canvas: DashboardCanvasDefinition;
  source: DashboardSourceIdentity;
  widgets: readonly DashboardWidget[];
}
