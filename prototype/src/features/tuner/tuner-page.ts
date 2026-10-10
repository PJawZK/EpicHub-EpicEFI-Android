import { renderFeatureIntro } from "../../ui/feature-intro";

export function renderTunerPage(): HTMLElement {
  return renderFeatureIntro({
    title: "Tuner",
    purpose: "Manual semantic calibration browsing/editing with explicit verified/pending/RAM/Burn authority states.",
    next: ["System → Category → Feature", "Parameters / Tables / Curves", "Pending edits / Write RAM / Burn separation"],
  });
}
