export interface SettingsPopoverHandle {
  element: HTMLElement;
}

const SETTINGS_GROUPS = [
  ["General", "Application-wide preferences and behavior"],
  ["Connections", "Persistent connection and auto-reconnect policy"],
  ["Warnings & alerts", "Rules, thresholds, debounce and cooldown"],
  ["Appearance", "Shared visual and presentation preferences"],
] as const;

export function createSettingsPopover(): SettingsPopoverHandle {
  const element = document.createElement("div");
  element.className = "settings-wrap";

  const button = document.createElement("button");
  button.type = "button";
  button.className = "settings-button header-icon-button";
  button.setAttribute("aria-label", "Settings");
  button.setAttribute("title", "Settings");
  button.setAttribute("aria-haspopup", "dialog");
  button.setAttribute("aria-expanded", "false");
  button.innerHTML = `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9.6 3.2h4.8l.6 2.1 1.8 1 2.1-.6 2.4 4.1-1.5 1.5v2l1.5 1.5-2.4 4.1-2.1-.6-1.8 1-.6 2.1H9.6L9 19.3l-1.8-1-2.1.6-2.4-4.1 1.5-1.5v-2L2.7 9.8l2.4-4.1 2.1.6 1.8-1 .6-2.1Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  `;

  const popover = document.createElement("div");
  popover.className = "settings-popover";
  popover.setAttribute("role", "dialog");
  popover.setAttribute("aria-label", "EpicHub settings");
  popover.hidden = true;

  const head = document.createElement("div");
  head.className = "settings-popover-head";
  head.innerHTML = "<strong>Settings</strong><small>Global EpicHub settings surface</small>";
  popover.append(head);

  for (const [title, detail] of SETTINGS_GROUPS) {
    const row = document.createElement("div");
    row.className = "settings-group-preview";
    const label = document.createElement("strong");
    label.textContent = title;
    const description = document.createElement("small");
    description.textContent = detail;
    row.append(label, description);
    popover.append(row);
  }

  const note = document.createElement("p");
  note.className = "settings-note";
  note.textContent = "The shell owns Settings placement only. Feature and policy owners will supply the actual settings content.";
  popover.append(note);

  element.append(button, popover);

  const setOpen = (open: boolean) => {
    popover.hidden = !open;
    button.setAttribute("aria-expanded", String(open));
  };

  button.addEventListener("click", () => setOpen(popover.hidden));
  button.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setOpen(false);
  });

  return { element };
}
