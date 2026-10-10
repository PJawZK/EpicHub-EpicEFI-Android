import { renderFeatureIntro } from "../../ui/feature-intro";

export function renderDiagnosticsPage(): HTMLElement {
  return renderFeatureIntro({
    title: "Diagnostics",
    purpose: "Evidence, performance, events, warnings/incidents and problem-capture review without becoming subsystem control authority.",
    next: ["Overview / Events / Metrics", "Sessions / generations", "Problem Captures", "Performance evidence"],
  });
}
