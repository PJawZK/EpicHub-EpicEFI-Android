import { renderFeatureIntro } from "../../ui/feature-intro";

export function renderAnalyzerPage(): HTMLElement {
  return renderFeatureIntro({
    title: "Analyzer",
    purpose: "Generic measurement exploration/query over shared measurement contracts, not Logger UI state.",
    next: ["Graphs / timeline / cursor", "Scatter / histogram / binned tables", "A/B and statistics", "Math / derived channels"],
  });
}
