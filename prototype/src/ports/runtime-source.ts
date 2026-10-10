import type { RuntimeSnapshot, SourceStatus } from "../model/runtime";

export type RuntimeSnapshotListener = (snapshot: RuntimeSnapshot) => void;
export type RuntimeSourceUnsubscribe = () => void;

export interface RuntimeSource {
  readonly id: string;

  status(): SourceStatus;

  start(listener: RuntimeSnapshotListener): Promise<RuntimeSourceUnsubscribe>;
}
