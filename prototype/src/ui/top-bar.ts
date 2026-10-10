import type { RuntimeSnapshot, SourceStatus } from "../model/runtime";
import { routeById, type RouteId, type WorkspaceId } from "../app/routes";
import { renderRouteNav } from "./route-nav";
import { createSettingsPopover } from "./settings-popover";
import { createSourceStatus, type SourceStatusHandle } from "./source-status";
import { createWorkspaceMenu } from "./workspace-menu";

export interface TopBarHandle {
  element: HTMLElement;
  updateSource(snapshot: RuntimeSnapshot | null, status: SourceStatus): void;
  renderNavigation(
    workspace: WorkspaceId,
    route: RouteId,
    onWorkspace: (workspace: WorkspaceId) => void,
    onRoute: (route: RouteId) => void,
  ): void;
}

export function createTopBar(
  initialStatus: SourceStatus,
  onScreenLockChange: (locked: boolean) => void,
  onDashboardEditChange: (editing: boolean) => void,
): TopBarHandle {
  const element = document.createElement("header");
  element.className = "app-header";

  const headerLeft = document.createElement("div");
  headerLeft.className = "header-left";

  const workspaceMenu = createWorkspaceMenu();

  const activeContext = document.createElement("span");
  activeContext.className = "brand-context";

  const routeSlot = document.createElement("div");
  routeSlot.className = "header-route-slot";

  headerLeft.append(workspaceMenu.element, activeContext, routeSlot);

  const headerContext = document.createElement("div");
  headerContext.className = "header-context";
  const sourceStatus: SourceStatusHandle = createSourceStatus(initialStatus);
  headerContext.append(sourceStatus.element);

  const headerActions = document.createElement("div");
  headerActions.className = "header-actions";

  const editButton = document.createElement("button");
  editButton.type = "button";
  editButton.className = "header-action dashboard-edit";
  editButton.hidden = true;
  editButton.setAttribute("aria-pressed", "false");
  editButton.innerHTML = `
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 16.5-.7 4.2 4.2-.7L18.8 8.7l-3.5-3.5L4 16.5Z"/><path d="m13.9 6.6 3.5 3.5"/></svg>
    <span class="header-action__label">Edit</span>
  `;
  let dashboardEditing = false;
  editButton.addEventListener("click", () => {
    dashboardEditing = !dashboardEditing;
    editButton.setAttribute("aria-pressed", String(dashboardEditing));
    const label = editButton.querySelector<HTMLElement>(".header-action__label");
    if (label) label.textContent = dashboardEditing ? "Done" : "Edit";
    element.dataset.dashboardEditing = String(dashboardEditing);
    onDashboardEditChange(dashboardEditing);
  });

  const lockButton = document.createElement("button");
  lockButton.type = "button";
  lockButton.className = "header-action screen-lock";
  lockButton.setAttribute("aria-label", "Screen lock");
  lockButton.setAttribute("aria-pressed", "false");
  lockButton.innerHTML = `
    <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
    <span class="header-action__label">Lock</span>
  `;
  let locked = false;
  lockButton.addEventListener("click", () => {
    locked = !locked;
    lockButton.setAttribute("aria-pressed", String(locked));
    lockButton.title = locked ? "Screen lock enabled" : "Screen lock disabled";
    const label = lockButton.querySelector<HTMLElement>(".header-action__label");
    if (label) label.textContent = locked ? "Locked" : "Lock";
    onScreenLockChange(locked);
  });

  const problemButton = document.createElement("button");
  problemButton.type = "button";
  problemButton.className = "header-action problem-capture";
  problemButton.disabled = true;
  problemButton.title = "Problem capture placement is reserved; capture behavior is not implemented in the web prototype yet.";
  problemButton.innerHTML = `
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3v18"/><path d="M7 4h10l-2 4 2 4H7"/></svg>
    <span class="header-action__label">Problem</span>
  `;

  const settings = createSettingsPopover();
  headerActions.append(editButton, lockButton, problemButton, settings.element);
  element.append(headerLeft, headerContext, headerActions);

  const updateSource = (snapshot: RuntimeSnapshot | null, status: SourceStatus) => {
    sourceStatus.update(snapshot, status);
  };

  const renderNavigation = (
    workspace: WorkspaceId,
    route: RouteId,
    onWorkspace: (workspace: WorkspaceId) => void,
    onRoute: (route: RouteId) => void,
  ) => {
    workspaceMenu.update(workspace, onWorkspace);
    activeContext.textContent = routeById(route).label;
    activeContext.title = `EpicHub ${routeById(route).label}`;

    routeSlot.replaceChildren();
    const routeNav = renderRouteNav(workspace, route, onRoute);
    if (routeNav) routeSlot.append(routeNav);

    editButton.hidden = route !== "dashboard";
  };

  return { element, updateSource, renderNavigation };
}
