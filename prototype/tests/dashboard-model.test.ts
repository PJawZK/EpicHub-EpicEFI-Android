import { describe, expect, it } from "vitest";
import { DEFAULT_DASHBOARD } from "../src/features/dashboard/fixtures/default-dashboard";
import { dashboardValueState } from "../src/features/dashboard/render/widget-state";

describe("dashboard foundation", () => {
  it("keeps the default layout inside its source canvas with unique widget ids", () => {
    const ids = new Set<string>();

    for (const widget of DEFAULT_DASHBOARD.widgets) {
      expect(ids.has(widget.id)).toBe(false);
      ids.add(widget.id);
      expect(widget.rect.x).toBeGreaterThanOrEqual(0);
      expect(widget.rect.y).toBeGreaterThanOrEqual(0);
      expect(widget.rect.x + widget.rect.width).toBeLessThanOrEqual(DEFAULT_DASHBOARD.canvas.width);
      expect(widget.rect.y + widget.rect.height).toBeLessThanOrEqual(DEFAULT_DASHBOARD.canvas.height);
    }
  });

  it("classifies warning and critical thresholds deterministically", () => {
    const thresholds = { lowCritical: 10, lowWarning: 20, highWarning: 80, highCritical: 90 };
    expect(dashboardValueState(5, thresholds)).toBe("critical");
    expect(dashboardValueState(15, thresholds)).toBe("warning");
    expect(dashboardValueState(50, thresholds)).toBe("normal");
    expect(dashboardValueState(85, thresholds)).toBe("warning");
    expect(dashboardValueState(95, thresholds)).toBe("critical");
  });
});
