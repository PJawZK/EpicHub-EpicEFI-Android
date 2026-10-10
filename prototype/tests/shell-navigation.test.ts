import { describe, expect, it } from "vitest";
import { compactRouteLabel } from "../src/ui/route-nav";
import { nextWorkspaceMenuIndex } from "../src/ui/workspace-menu";

describe("shared shell navigation", () => {
  it("wraps workspace-menu keyboard movement", () => {
    expect(nextWorkspaceMenuIndex(0, "ArrowUp", 4)).toBe(3);
    expect(nextWorkspaceMenuIndex(3, "ArrowDown", 4)).toBe(0);
    expect(nextWorkspaceMenuIndex(2, "Home", 4)).toBe(0);
    expect(nextWorkspaceMenuIndex(1, "End", 4)).toBe(3);
  });

  it("chooses a useful first menu target when nothing is focused", () => {
    expect(nextWorkspaceMenuIndex(-1, "ArrowDown", 4)).toBe(0);
    expect(nextWorkspaceMenuIndex(-1, "ArrowUp", 4)).toBe(3);
    expect(nextWorkspaceMenuIndex(-1, "ArrowDown", 0)).toBe(-1);
  });

  it("uses a compact local-route label only where the full label is too wide", () => {
    expect(compactRouteLabel("tuning-tools", "Tuning Tools")).toBe("Tools");
    expect(compactRouteLabel("analyzer", "Analyzer")).toBe("Analyzer");
    expect(compactRouteLabel("logger", "Logger")).toBe("Logger");
  });
});
