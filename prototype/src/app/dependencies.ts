import { FixtureRuntimeSource } from "../adapters/fixtures/fixture-runtime-source";
import type { RuntimeSource } from "../ports/runtime-source";

export interface PrototypeDependencies {
  runtimeSource: RuntimeSource;
}

export function createPrototypeDependencies(): PrototypeDependencies {
  return {
    runtimeSource: new FixtureRuntimeSource(),
  };
}
