import type { RuntimeSnapshot } from "../../model/runtime";
import type {
  RuntimeSnapshotListener,
  RuntimeSource,
  RuntimeSourceUnsubscribe,
} from "../../ports/runtime-source";

const FIXTURE_STATUS = {
  kind: "fixture",
  label: "FIXTURE",
  connected: true,
  detail: "Deterministic browser fixture — not live ECU data",
} as const;

export class FixtureRuntimeSource implements RuntimeSource {
  readonly id = "fixture-runtime";

  private sequence = 0;

  status() {
    return FIXTURE_STATUS;
  }

  async start(listener: RuntimeSnapshotListener): Promise<RuntimeSourceUnsubscribe> {
    const emit = () => {
      this.sequence += 1;
      listener(this.snapshot(this.sequence));
    };

    emit();
    const timer = window.setInterval(emit, 250);
    return () => window.clearInterval(timer);
  }

  private snapshot(sequence: number): RuntimeSnapshot {
    const phase = sequence / 14;
    const rpm = 820 + Math.sin(phase) * 35;
    const map = 52 + Math.sin(phase * 0.72) * 3.5;
    const throttle = 1.2 + Math.max(0, Math.sin(phase * 0.31)) * 1.8;
    const lambda = 0.98 + Math.sin(phase * 0.45) * 0.02;
    const coolant = 78 + Math.sin(phase * 0.1) * 1.5;

    return {
      sequence,
      capturedAtMs: Date.now(),
      source: FIXTURE_STATUS,
      channels: [
        { id: "rpm", label: "RPM", value: rpm, unit: "rpm", valid: true },
        { id: "map", label: "MAP", value: map, unit: "kPa", valid: true },
        { id: "tps", label: "TPS", value: throttle, unit: "%", valid: true },
        { id: "lambda", label: "Lambda", value: lambda, unit: "λ", valid: true },
        { id: "clt", label: "Coolant", value: coolant, unit: "°C", valid: true },
      ],
    };
  }
}
