import {
  routesForWorkspace,
  type RouteId,
  type WorkspaceId,
} from "../app/routes";

const COMPACT_ROUTE_LABELS: Partial<Record<RouteId, string>> = {
  "tuning-tools": "Tools",
};

export function compactRouteLabel(routeId: RouteId, label: string): string {
  return COMPACT_ROUTE_LABELS[routeId] ?? label;
}

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
    button.dataset.active = String(active);
    button.dataset.route = route.id;
    button.setAttribute("aria-current", active ? "page" : "false");
    button.setAttribute("aria-label", route.label);

    const fullLabel = document.createElement("span");
    fullLabel.className = "route-nav__label route-nav__label--full";
    fullLabel.textContent = route.label;

    const compactLabel = document.createElement("span");
    compactLabel.className = "route-nav__label route-nav__label--compact";
    compactLabel.textContent = compactRouteLabel(route.id, route.label);

    button.append(fullLabel, compactLabel);
    button.addEventListener("click", () => onSelect(route.id));
    nav.append(button);
  }

  return nav;
}
