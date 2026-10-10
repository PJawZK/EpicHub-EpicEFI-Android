import type { RouteId, WorkspaceId } from "./routes";
import { renderRouteNav } from "../ui/route-nav";
import { createStatusStrip, type StatusStripHandle } from "../ui/status-strip";
import { renderWorkspaceNav } from "../ui/workspace-nav";
import type { SourceStatus } from "../model/runtime";

export interface ShellHandle {
  element: HTMLElement;
  status: StatusStripHandle;
  content: HTMLElement;
  renderNavigation(
    workspace: WorkspaceId,
    route: RouteId,
    onWorkspace: (workspace: WorkspaceId) => void,
    onRoute: (route: RouteId) => void,
  ): void;
}

export function createShell(initialStatus: SourceStatus): ShellHandle {
  const element = document.createElement("div");
  element.className = "epichub-shell";

  const status = createStatusStrip(initialStatus);
  const navigation = document.createElement("div");
  navigation.className = "epichub-shell__navigation";

  const content = document.createElement("main");
  content.className = "epichub-shell__content";

  element.append(status.element, navigation, content);

  const renderNavigation = (
    workspace: WorkspaceId,
    route: RouteId,
    onWorkspace: (workspace: WorkspaceId) => void,
    onRoute: (route: RouteId) => void,
  ) => {
    navigation.replaceChildren(renderWorkspaceNav(workspace, onWorkspace));
    const routeNav = renderRouteNav(workspace, route, onRoute);
    if (routeNav) {
      navigation.append(routeNav);
    }
  };

  return { element, status, content, renderNavigation };
}
