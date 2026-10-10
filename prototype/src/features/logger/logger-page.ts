import { renderFeatureIntro } from "../../ui/feature-intro";

export function renderLoggerPage(): HTMLElement {
  return renderFeatureIntro({
    title: "Logger",
    purpose: "Recording-session control, recording health and bounded live preview; Analyzer remains a sibling owner.",
    next: ["Live Logger", "Trigger Logger", "Recording health / drops / storage", "Open completed session in Analyzer"],
  });
}
