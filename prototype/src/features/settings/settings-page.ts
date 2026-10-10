import { renderFeatureIntro } from "../../ui/feature-intro";

export function renderSettingsPage(): HTMLElement {
  return renderFeatureIntro({
    title: "Settings",
    purpose: "Global policy/configuration grouped by responsibility rather than by whichever feature exposes a shortcut.",
    next: ["Connections", "Warnings & alerts", "Logging & storage", "Appearance / units", "Diagnostics & privacy"],
  });
}
