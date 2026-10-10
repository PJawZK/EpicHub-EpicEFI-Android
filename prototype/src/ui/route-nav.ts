import {
  routesForWorkspace,
  type RouteId,
  type WorkspaceId,
} from "../app/routes";

export function renderRouteNav(
  workspace: WorkspaceId,
  activeRoute: RouteId,
  onSelect: (route: RouteId) => void,
): HTMLElement | null {
  const routes = routesForWorkspace(workspace);
  if (routes.length <= 1) {
    return null;
  }

  const nav = document.createElement("nav");
  nav.className = "route-nav";
  nav.setAttribute("aria-label", `${workspace} routes`);

  for (const route of routes) {
    const button = document.createElement("button");
    const active = route.id === activeRoute;
    button.type = "button";
    button.className = "route-nav__item";
    button.textContent = route.label;
    button.dataset.active = String(active);
    button.setAttribute("aria-current", active ? "page" : "false");
    button.addEventListener("click", () => onSelect(route.id));
    nav.append(button);
  }

  return nav;
}
