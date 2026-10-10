import type { DashboardThresholds } from "../model/dashboard-document";

export type DashboardValueState = "normal" | "warning" | "critical";

export function dashboardValueState(
  value: number | null,
  thresholds?: DashboardThresholds,
): DashboardValueState {
  if (value === null || !thresholds) return "normal";

  if (
    (thresholds.lowCritical !== undefined && value <= thresholds.lowCritical) ||
    (thresholds.highCritical !== undefined && value >= thresholds.highCritical)
  ) {
    return "critical";
  }

  if (
    (thresholds.lowWarning !== undefined && value <= thresholds.lowWarning) ||
    (thresholds.highWarning !== undefined && value >= thresholds.highWarning)
  ) {
    return "warning";
  }

  return "normal";
}

export function clampNormalized(value: number | null, minimum: number, maximum: number): number {
  if (value === null || !Number.isFinite(value) || maximum <= minimum) return 0;
  return Math.min(1, Math.max(0, (value - minimum) / (maximum - minimum)));
}
