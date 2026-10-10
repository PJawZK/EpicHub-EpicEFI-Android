import type { RuntimeSnapshot } from "../../model/runtime";
import { DEFAULT_DASHBOARD } from "./fixtures/default-dashboard";
import { renderDashboardCanvas } from "./render/dashboard-canvas";

export function renderDashboardPage(snapshot: RuntimeSnapshot | null): HTMLElement {
  const section = document.createElement("section");
  section.className = "dashboard-page";

  const canvas = renderDashboardCanvas(DEFAULT_DASHBOARD, snapshot);
  section.append(canvas);
  return section;
}
