import type { RuntimeSnapshot } from "../model/runtime";
import type { PrototypeDependencies } from "./dependencies";
import { renderFeature } from "./feature-registry";
import { NavigationState } from "./navigation-state";
import { routeById, routeFromHash, type RouteId } from "./routes";
import { createShell } from "./shell";

export async function createApp(
  root: HTMLElement,
  dependencies: PrototypeDependencies,
): Promise<() => void> {
  const initialRoute = routeFromHash(window.location.hash);
  const navigation = new NavigationState(initialRoute);
  const initialStatus = dependencies.runtimeSource.status();
  const shell = createShell(initialStatus);
  let latestSnapshot: RuntimeSnapshot | null = null;

  root.replaceChildren(shell.element);

  const render = () => {
    const route = navigation.currentRoute();
    const workspace = routeById(route).workspace;

    shell.renderNavigation(
      workspace,
      route,
      (nextWorkspace) => navigation.selectWorkspace(nextWorkspace),
      (nextRoute) => navigation.selectRoute(nextRoute),
    );
    shell.content.replaceChildren(renderFeature(route, latestSnapshot));
  };

  const onHashChange = () => {
    const nextRoute = routeFromHash(window.location.hash);
    navigation.syncFromLocation(nextRoute);
    render();
  };

  window.addEventListener("hashchange", onHashChange);
  shell.updateRuntime(null, initialStatus);
  render();

  const unsubscribe = await dependencies.runtimeSource.start((snapshot) => {
    latestSnapshot = snapshot;
    shell.updateRuntime(snapshot, snapshot.source);

    if (navigation.currentRoute() === "dashboard") {
      shell.content.replaceChildren(renderFeature("dashboard" as RouteId, latestSnapshot));
    }
  });

  return () => {
    unsubscribe();
    window.removeEventListener("hashchange", onHashChange);
  };
}
