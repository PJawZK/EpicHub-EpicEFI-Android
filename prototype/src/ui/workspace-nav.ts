import {
  WORKSPACES,
  type WorkspaceId,
} from "../app/routes";

export function renderWorkspaceNav(
  activeWorkspace: WorkspaceId,
  onSelect: (workspace: WorkspaceId) => void,
): HTMLElement {
  const nav = document.createElement("nav");
  nav.className = "workspace-nav";
  nav.setAttribute("aria-label", "EpicHub workspaces");

  for (const workspace of WORKSPACES) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "workspace-nav__item";
    button.textContent = workspace.label;
    button.dataset.active = String(workspace.id === activeWorkspace);
    button.addEventListener("click", () => onSelect(workspace.id));
    nav.append(button);
  }

  return nav;
}
