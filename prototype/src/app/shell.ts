import type { RuntimeSnapshot, SourceStatus } from "../model/runtime";
import type { RouteId, WorkspaceId } from "./routes";
import { createStatusBar, type StatusBarHandle } from "../ui/status-bar";
import { createTopBar, type TopBarHandle } from "../ui/top-bar";

export interface ShellHandle {
  element: HTMLElement;
  content: HTMLElement;
  updateRuntime(snapshot: RuntimeSnapshot | null, status: SourceStatus): void;
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
  element.dataset.screenLocked = "false";

  const topBar: TopBarHandle = createTopBar(initialStatus, (locked) => {
    element.dataset.screenLocked = String(locked);
  });
  const statusBar: StatusBarHandle = createStatusBar(initialStatus);

  const content = document.createElement("main");
  content.className = "epichub-shell__content";

  element.append(topBar.element, content, statusBar.element);

  const updateRuntime = (snapshot: RuntimeSnapshot | null, status: SourceStatus) => {
    topBar.updateSource(snapshot, status);
    statusBar.updateRuntime(snapshot, status);
  };

  const renderNavigation = (
    workspace: WorkspaceId,
    route: RouteId,
    onWorkspace: (workspace: WorkspaceId) => void,
    onRoute: (route: RouteId) => void,
  ) => {
    topBar.renderNavigation(workspace, route, onWorkspace, onRoute);
    statusBar.updateRoute(route);
  };

  return { element, content, updateRuntime, renderNavigation };
}
