import { WORKSPACES, type WorkspaceId } from "../app/routes";

const WORKSPACE_DESCRIPTIONS: Record<WorkspaceId, string> = {
  dashboard: "Live vehicle and ECU overview",
  tuner: "Manual tuning and guided tools",
  logger: "Recording and measurement analysis",
  diagnostics: "Health, incidents and problem evidence",
};

export interface WorkspaceMenuHandle {
  element: HTMLElement;
  update(activeWorkspace: WorkspaceId, onSelect: (workspace: WorkspaceId) => void): void;
}

export function createWorkspaceMenu(): WorkspaceMenuHandle {
  const element = document.createElement("div");
  element.className = "brand-switch";

  const button = document.createElement("button");
  button.type = "button";
  button.className = "brand brand-button";
  button.setAttribute("aria-label", "EpicHub workspace menu");
  button.setAttribute("aria-haspopup", "menu");
  button.setAttribute("aria-expanded", "false");
  button.innerHTML = `
    <span class="brand-product">EpicHub</span>
    <span class="brand-chevron" aria-hidden="true"></span>
  `;

  const menu = document.createElement("div");
  menu.className = "global-switch-menu";
  menu.setAttribute("role", "menu");
  menu.hidden = true;

  const head = document.createElement("div");
  head.className = "global-switch-head";
  head.innerHTML = "<strong>EpicHub</strong><small>Choose workspace</small>";

  const choices = document.createElement("div");
  choices.className = "global-switch-choices";
  menu.append(head, choices);
  element.append(button, menu);

  const setOpen = (open: boolean) => {
    menu.hidden = !open;
    button.setAttribute("aria-expanded", String(open));
  };

  button.addEventListener("click", () => setOpen(menu.hidden));
  button.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setOpen(false);
  });

  const update = (activeWorkspace: WorkspaceId, onSelect: (workspace: WorkspaceId) => void) => {
    choices.replaceChildren();

    for (const workspace of WORKSPACES) {
      const choice = document.createElement("button");
      const active = workspace.id === activeWorkspace;
      choice.type = "button";
      choice.className = `global-module-choice${active ? " global-module-choice--active" : ""}`;
      choice.setAttribute("role", "menuitem");
      choice.setAttribute("aria-current", active ? "page" : "false");

      const text = document.createElement("span");
      const label = document.createElement("strong");
      label.textContent = workspace.label;
      const detail = document.createElement("small");
      detail.textContent = WORKSPACE_DESCRIPTIONS[workspace.id];
      text.append(label, detail);

      const state = document.createElement("span");
      state.className = "module-state";
      state.textContent = active ? "Active" : "Open";

      choice.append(text, state);
      choice.addEventListener("click", () => {
        setOpen(false);
        onSelect(workspace.id);
      });
      choices.append(choice);
    }
  };

  return { element, update };
}
