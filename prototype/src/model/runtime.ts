export type PrototypeSourceKind = "fixture" | "ts-shim" | "offline";

export type PrototypeEvidenceLabel = "FIXTURE" | "TS-SHIM" | "OFFLINE PROTOTYPE";

export interface SourceStatus {
  kind: PrototypeSourceKind;
  label: PrototypeEvidenceLabel;
  connected: boolean;
  detail: string;
}

export interface RuntimeChannelValue {
  id: string;
  label: string;
  value: number;
  unit: string;
  valid: boolean;
}

export interface RuntimeSnapshot {
  sequence: number;
  capturedAtMs: number;
  source: SourceStatus;
  channels: readonly RuntimeChannelValue[];
}
