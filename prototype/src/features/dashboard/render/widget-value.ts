import type { RuntimeChannelValue, RuntimeSnapshot } from "../../../model/runtime";
import type { DashboardChannelBinding } from "../model/dashboard-document";

export interface DashboardResolvedValue {
  channel: RuntimeChannelValue | null;
  label: string;
  unit: string;
  decimals: number;
  value: number | null;
  valid: boolean;
}

export function resolveDashboardValue(
  snapshot: RuntimeSnapshot | null,
  binding: DashboardChannelBinding,
): DashboardResolvedValue {
  const channel = snapshot?.channels.find((candidate) => candidate.id === binding.channelId) ?? null;

  return {
    channel,
    label: binding.label ?? channel?.label ?? binding.channelId,
    unit: binding.unit ?? channel?.unit ?? "",
    decimals: binding.decimals ?? 1,
    value: channel?.value ?? null,
    valid: channel?.valid ?? false,
  };
}

export function formatDashboardValue(value: DashboardResolvedValue): string {
  if (!value.valid || value.value === null || !Number.isFinite(value.value)) return "—";
  return value.value.toFixed(value.decimals);
}
