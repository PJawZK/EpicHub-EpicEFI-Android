import type { RuntimeSnapshot } from "../../model/runtime";

export function renderDashboardPage(snapshot: RuntimeSnapshot | null): HTMLElement {
  const section = document.createElement("section");
  section.className = "feature-page dashboard-page";

  const title = document.createElement("h1");
  title.textContent = "Dashboard";

  const note = document.createElement("p");
  note.className = "feature-page__note";
  note.textContent = "Fixture-backed groundwork for live widgets, Drive/Edit state and layout authority.";

  const grid = document.createElement("div");
  grid.className = "channel-grid";

  for (const channel of snapshot?.channels ?? []) {
    const card = document.createElement("article");
    card.className = "channel-card";

    const label = document.createElement("span");
    label.className = "channel-card__label";
    label.textContent = channel.label;

    const value = document.createElement("strong");
    value.className = "channel-card__value";
    value.textContent = `${channel.value.toFixed(channel.id === "rpm" ? 0 : 1)} ${channel.unit}`;

    card.append(label, value);
    grid.append(card);
  }

  section.append(title, note, grid);
  return section;
}
