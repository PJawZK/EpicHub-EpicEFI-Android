import type { DashboardDocument } from "../model/dashboard-document";

export type DashboardImportSeverity = "info" | "warning" | "unsupported";

export interface DashboardImportIssue {
  severity: DashboardImportSeverity;
  code: string;
  message: string;
  sourceObjectId?: string;
}

export interface DashboardImportResult {
  dashboard: DashboardDocument;
  issues: readonly DashboardImportIssue[];
}

/**
 * Contract for external dashboard converters.
 *
 * Importers translate foreign layout data into EpicHub's declarative DashboardDocument.
 * Imported scripts/actions are never executed. Unsupported behavior must be reported rather
 * than silently dropped so visual fidelity and functional fidelity can be reviewed separately.
 */
export interface DashboardImporter {
  readonly formatId: string;
  canImport(fileName: string, bytes: Uint8Array): boolean;
  import(fileName: string, bytes: Uint8Array): Promise<DashboardImportResult>;
}
