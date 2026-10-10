import type { RuntimeSnapshot, SourceStatus } from "../model/runtime";

export interface StatusStripHandle {
  element: HTMLElement;
  update(snapshot: RuntimeSnapshot | null, status: SourceStatus): void;
}

export function createStatusStrip(initialStatus: SourceStatus): StatusStripHandle {
  const element = document.createElement("section");
  element.className = "status-strip";

  const brand = document.createElement("strong");
  brand.textContent = "EpicHub";

  const authority = document.createElement("span");
  authority.className = "status-strip__authority";

  const sourceDetail = document.createElement("span");
  sourceDetail.className = "status-strip__detail";

  const sequence = document.createElement("span");
  sequence.className = "status-strip__sequence";

  element.append(brand, authority, sourceDetail, sequence);

  const update = (snapshot: RuntimeSnapshot | null, status: SourceStatus) => {
    authority.textContent = status.label;
    authority.dataset.connected = String(status.connected);
    sourceDetail.textContent = status.detail;
    sequence.textContent = snapshot ? `seq ${snapshot.sequence}` : "waiting";
  };

  update(null, initialStatus);
  return { element, update };
}
