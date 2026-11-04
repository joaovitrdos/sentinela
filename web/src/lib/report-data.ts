import { api, apiDownload } from "@/lib/api";

export type ReportFormat = "pdf" | "html" | "xls" | "odf" | "csv" | "rtf" | "txt";

export interface ReportOption {
    code: string;
    title: string;
}

export interface ReportRequest {
    format: ReportFormat;
    parameters: Record<string, string | number | boolean | string[] | null>;
}

export const listReports = (signal?: AbortSignal) => api<ReportOption[]>("/api/reports", { signal });

export const downloadReport = (reportCode: string, request: ReportRequest) =>
    apiDownload(`/api/reports/${encodeURIComponent(reportCode)}`, { method: "POST", body: request });
