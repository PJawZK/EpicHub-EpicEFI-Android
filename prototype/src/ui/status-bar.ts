import type { RouteId } from "../app/routes";
import { routeById } from "../app/routes";
import type { RuntimeSnapshot, SourceStatus } from "../model/runtime";

export interface StatusBarHandle {
  element: HTMLElement;
  updateRoute(route: RouteId): void;
  updateRuntime(snapshot: RuntimeSnapshot | null, status: SourceStatus): void;
}

export function createStatusBar(initialStatus: SourceStatus): StatusBarHandle {
  const element = document.createElement("footer");
  element.className = "status-bar";

  const appStatus = document.createElement("span");
  appStatus.className = "status-bar__state";
  appStatus.textContent = "Ready";

  const mode = document.createElement("span");
  mode.textContent = "Web prototype";

  const route = document.createElement("span");
  route.className = "status-bar__route";
  route.textContent = "Dashboard";

  const grow = document.createElement("span");
  grow.className = "status-bar__grow";

  const source = document.createElement("span");
  source.className = "status-bar__source";

  const updateRoute = (routeId: RouteId) => {
    route.textContent = routeById(routeId).label;
  };

  const updateRuntime = (snapshot: RuntimeSnapshot | null, status: SourceStatus) => {
    source.textContent = snapshot ? `${status.label} · seq ${snapshot.sequence}` : `${status.label} · waiting`;
  };

  updateRuntime(null, initialStatus);
  element.append(appStatus, mode, route, grow, source);

  return { element, updateRoute, updateRuntime };
}
