const BASE_URL = (import.meta.env.VITE_API_URL ?? "/api").replace(/\/$/, "");

export const apiUrl = (path: string) => BASE_URL + path;

// Mensagem do backend quando o token falta, expirou ou foi revogado (SecurityConfig.authenticationEntryPoint).
const SESSION_REJECTED = "Autenticação necessária para acessar este recurso";
export const SYSTEM_MAINTENANCE_EVENT = "sentinela:maintenance";

export class ApiError extends Error {
    /** 0 = falha de rede (servidor fora do ar, sem internet, CORS). */
    readonly status: number;
    /** Texto puro devolvido pelo backend (GlobalExceptionHandler responde String). */
    readonly serverMessage: string;

    constructor(status: number, serverMessage: string) {
        super(serverMessage || `HTTP ${status}`);
        this.status = status;
        this.serverMessage = serverMessage;
    }

    get isSessionRejected() {
        return this.status === 401 && this.serverMessage.trim() === SESSION_REJECTED;
    }

    get isMaintenance() {
        if (this.status !== 503) return false;
        try {
            return JSON.parse(this.serverMessage).code === "SYSTEM_MAINTENANCE";
        } catch {
            return false;
        }
    }
}

/** Ligado pelo AuthProvider: de onde vem o token e o que fazer quando a sessao cai. */
export const apiSession = {
    getToken: (): string | null => null,
    onSessionRejected: (): void => {},
};

interface RequestOptions {
    method?: "GET" | "POST" | "PUT" | "DELETE";
    body?: unknown;
    signal?: AbortSignal;
}

async function request(path: string, { method = "GET", body, signal }: RequestOptions, accept: string): Promise<Response> {
    const token = apiSession.getToken();
    const headers: Record<string, string> = { Accept: accept };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = `Bearer ${token}`;

    let response: Response;
    try {
        response = await fetch(BASE_URL + path, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined,
            signal,
        });
    } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") throw err;
        throw new ApiError(0, "");
    }

    if (!response.ok) {
        const text = await response.text().catch(() => "");
        const error = new ApiError(response.status, text);
        if (error.isMaintenance) window.dispatchEvent(new Event(SYSTEM_MAINTENANCE_EVENT));
        if (token && error.isSessionRejected) apiSession.onSessionRejected();
        throw error;
    }

    return response;
}

export async function api<T = void>(path: string, options: RequestOptions = {}): Promise<T> {
    const response = await request(path, options, "application/json, text/plain");

    if (response.status === 204) return undefined as T;

    const contentType = response.headers.get("content-type") ?? "";
    return (contentType.includes("json") ? await response.json() : await response.text()) as T;
}

/** Downloads protected images without putting the session token in their URL. */
export async function apiBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
    const response = await request(path, options, "image/png, application/octet-stream");
    return response.blob();
}

export async function apiDownload(path: string, options: RequestOptions = {}): Promise<{ blob: Blob; filename: string | null }> {
    const response = await request(path, options, "*/*");
    const disposition = response.headers.get("content-disposition") ?? "";
    const encodedFilename = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const plainFilename = disposition.match(/filename="?([^";]+)"?/i)?.[1];
    const filename = encodedFilename ? decodeURIComponent(encodedFilename) : plainFilename ?? null;
    return { blob: await response.blob(), filename };
}

export async function apiUpload<T>(path: string, file: File): Promise<T> {
    const form = new FormData();
    form.append("file", file);
    const token = apiSession.getToken();
    let response: Response;
    try {
        response = await fetch(BASE_URL + path, {
            method: "POST", body: form,
            headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
    } catch { throw new ApiError(0, ""); }
    if (!response.ok) {
        const error = new ApiError(response.status, await response.text().catch(() => ""));
        if (token && error.isSessionRejected) apiSession.onSessionRejected();
        throw error;
    }
    return response.json() as Promise<T>;
}
