import {
  hashForRoute,
  routeById,
  routesForWorkspace,
  type RouteId,
  type WorkspaceId,
} from "./routes";

export class NavigationState {
  private currentRouteId: RouteId;
  private lastTunerRoute: RouteId = "tuner";
  private lastLoggerRoute: RouteId = "logger";

  constructor(initialRoute: RouteId) {
    this.currentRouteId = initialRoute;
    this.remember(initialRoute);
  }

  currentRoute(): RouteId {
    return this.currentRouteId;
  }

  currentWorkspace(): WorkspaceId {
    return routeById(this.currentRouteId).workspace;
  }

  selectRoute(route: RouteId): void {
    this.currentRouteId = route;
    this.remember(route);
    window.location.hash = hashForRoute(route);
  }

  selectWorkspace(workspace: WorkspaceId): void {
    if (workspace === "tuner") {
      this.selectRoute(this.lastTunerRoute);
      return;
    }
    if (workspace === "logger") {
      this.selectRoute(this.lastLoggerRoute);
      return;
    }

    const route = routesForWorkspace(workspace)[0];
    if (!route) {
      throw new Error(`Workspace ${workspace} has no routes`);
    }
    this.selectRoute(route.id);
  }

  syncFromLocation(route: RouteId): void {
    this.currentRouteId = route;
    this.remember(route);
  }

  private remember(route: RouteId): void {
    const workspace = routeById(route).workspace;
    if (workspace === "tuner") {
      this.lastTunerRoute = route;
    } else if (workspace === "logger") {
      this.lastLoggerRoute = route;
    }
  }
}
