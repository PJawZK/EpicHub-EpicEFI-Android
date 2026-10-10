export type WorkspaceId = "dashboard" | "tuner" | "logger" | "diagnostics";
export type RouteId =
  | "dashboard"
  | "tuner"
  | "tuning-tools"
  | "logger"
  | "analyzer"
  | "diagnostics";

export interface WorkspaceDefinition {
  id: WorkspaceId;
  label: string;
}

export interface RouteDefinition {
  id: RouteId;
  label: string;
  workspace: WorkspaceId;
}

export const WORKSPACES: readonly WorkspaceDefinition[] = [
  { id: "dashboard", label: "Dashboard" },
  { id: "tuner", label: "Tuner" },
  { id: "logger", label: "Logger" },
  { id: "diagnostics", label: "Diagnostics" },
];

export const ROUTES: readonly RouteDefinition[] = [
  { id: "dashboard", label: "Dashboard", workspace: "dashboard" },
  { id: "tuner", label: "Tuner", workspace: "tuner" },
  { id: "tuning-tools", label: "Tuning Tools", workspace: "tuner" },
  { id: "logger", label: "Logger", workspace: "logger" },
  { id: "analyzer", label: "Analyzer", workspace: "logger" },
  { id: "diagnostics", label: "Diagnostics", workspace: "diagnostics" },
];

const ROUTE_BY_ID = new Map(ROUTES.map((route) => [route.id, route]));

export function routeById(id: RouteId): RouteDefinition {
  const route = ROUTE_BY_ID.get(id);
  if (!route) {
    throw new Error(`Unknown EpicHub prototype route: ${id}`);
  }
  return route;
}

export function routesForWorkspace(workspace: WorkspaceId): readonly RouteDefinition[] {
  return ROUTES.filter((route) => route.workspace === workspace);
}

export function routeFromHash(hash: string): RouteId {
  const candidate = hash.replace(/^#\/?/, "") as RouteId;
  return ROUTE_BY_ID.has(candidate) ? candidate : "dashboard";
}

export function hashForRoute(route: RouteId): string {
  return `#/${route}`;
}
