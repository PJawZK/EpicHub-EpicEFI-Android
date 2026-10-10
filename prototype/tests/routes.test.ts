import { describe, expect, it } from "vitest";
import { ROUTES, WORKSPACES, routesForWorkspace } from "../src/app/routes";

describe("EpicHub prototype navigation contract", () => {
  it("keeps exactly four primary workspaces", () => {
    expect(WORKSPACES.map((workspace) => workspace.id)).toEqual([
      "dashboard",
      "tuner",
      "logger",
      "diagnostics",
    ]);
  });

  it("keeps six distinct functional routes", () => {
    expect(ROUTES.map((route) => route.id)).toEqual([
      "dashboard",
      "tuner",
      "tuning-tools",
      "logger",
      "analyzer",
      "diagnostics",
    ]);
  });

  it("groups routes visually without collapsing ownership", () => {
    expect(routesForWorkspace("tuner").map((route) => route.id)).toEqual([
      "tuner",
      "tuning-tools",
    ]);
    expect(routesForWorkspace("logger").map((route) => route.id)).toEqual([
      "logger",
      "analyzer",
    ]);
  });
});
