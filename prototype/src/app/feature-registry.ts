import type { RuntimeSnapshot } from "../model/runtime";
import type { RouteId } from "./routes";
import { renderAnalyzerPage } from "../features/analyzer/analyzer-page";
import { renderDashboardPage } from "../features/dashboard/dashboard-page";
import { renderDiagnosticsPage } from "../features/diagnostics/diagnostics-page";
import { renderLoggerPage } from "../features/logger/logger-page";
import { renderTunerPage } from "../features/tuner/tuner-page";
import { renderTuningToolsPage } from "../features/tuning-tools/tuning-tools-page";

export function renderFeature(route: RouteId, snapshot: RuntimeSnapshot | null): HTMLElement {
  switch (route) {
    case "dashboard":
      return renderDashboardPage(snapshot);
    case "tuner":
      return renderTunerPage();
    case "tuning-tools":
      return renderTuningToolsPage();
    case "logger":
      return renderLoggerPage();
    case "analyzer":
      return renderAnalyzerPage();
    case "diagnostics":
      return renderDiagnosticsPage();
  }
}
