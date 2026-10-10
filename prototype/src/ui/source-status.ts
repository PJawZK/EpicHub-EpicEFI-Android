import type { RuntimeSnapshot, SourceStatus } from "../model/runtime";

export interface SourceStatusHandle {
  element: HTMLElement;
  update(snapshot: RuntimeSnapshot | null, status: SourceStatus): void;
}

export function createSourceStatus(initialStatus: SourceStatus): SourceStatusHandle {
  const element = document.createElement("div");
  element.className = "source-status";

  const authority = document.createElement("span");
  authority.className = "source-status__authority";

  const detail = document.createElement("span");
  detail.className = "source-status__detail";

  const sequence = document.createElement("span");
  sequence.className = "source-status__sequence";

  element.append(authority, detail, sequence);

  const update = (snapshot: RuntimeSnapshot | null, status: SourceStatus) => {
    authority.textContent = status.label;
    authority.dataset.kind = status.kind;
    authority.dataset.connected = String(status.connected);
    detail.textContent = status.detail;
    sequence.textContent = snapshot ? `#${snapshot.sequence}` : "—";
  };

  update(null, initialStatus);
  return { element, update };
}
