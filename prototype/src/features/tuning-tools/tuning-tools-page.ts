import { renderFeatureIntro } from "../../ui/feature-intro";

export function renderTuningToolsPage(): HTMLElement {
  return renderFeatureIntro({
    title: "Tuning Tools",
    purpose: "Guided native-tool workflows modeled independently from Tuner presentation state.",
    next: ["Acceleration Enrichment", "PID / controller analysis", "TCharge", "Injection phasing"],
  });
}
