import { ApiError, api, apiBlob } from "@/lib/api";

export type PropertyCoordinate = [number, number];

export interface PropertyGeometry {
    type: "Polygon";
    coordinates: PropertyCoordinate[][];
}

export interface SavedProperty {
    propertyId: string;
    name: string;
    teamTitle: string | null;
    city: string;
    state: string;
    latitude: number | null;
    longitude: number | null;
    areaHectares: number | null;
    geometry: PropertyGeometry | null;
    cropName: string | null;
    plotName: string | null;
    plantingDate: string | null;
    cropStage: string | null;
    createdAt: string;
    updatedAt: string;
    accessRole: "VIEWER" | "EDITOR" | "ADMIN";
    teamOwner: boolean;
}

export type TeamRole = "VIEWER" | "EDITOR" | "ADMIN";
export interface TeamOverview {
    propertyId: string;
    propertyName: string;
    title: string;
    enabled: boolean;
    owner: boolean;
    myRole: TeamRole;
    members: Array<{ userId: string; name: string; email: string; role: TeamRole; joinedAt: string; owner: boolean }>;
    invitations: Array<{ invitationId: string; email: string; role: TeamRole; expiresAt: string }>;
}

export interface PropertyRequest {
    name: string;
    city: string;
    state: string;
    geometry: PropertyGeometry;
    cropName: string;
    plotName: string;
    plantingDate: string | null;
    cropStage: string;
}

export type PropertyActivityType = "IRRIGATION" | "RAINFALL" | "INSPECTION" | "INCIDENT";
export interface PropertyActivity {
    activityId: string;
    type: PropertyActivityType;
    date: string;
    time: string | null;
    notes: string;
    amountMm: number | null;
    createdAt: string;
    plotId: string | null;
}
export interface PropertyActivityRequest {
    type: PropertyActivityType;
    date: string;
    time: string | null;
    notes: string;
    amountMm: number | null;
    plotId: string | null;
}

export interface Plot {
    plotId: string;
    name: string;
    cropName: string | null;
    plantingDate: string | null;
    cropStage: string | null;
    areaHectares: number | null;
}
export type PlotRequest = Omit<Plot, "plotId">;
export interface FieldAction {
    actionId: string;
    plotId: string | null;
    kind: "INSPECTION" | "IRRIGATION";
    title: string;
    responsibleName: string;
    dueDate: string;
    status: "OPEN" | "DONE";
    sourceNote: string | null;
    completionNote: string | null;
    completedAt: string | null;
    createdAt: string;
}
export type FieldActionRequest = Pick<FieldAction, "kind" | "title" | "responsibleName" | "dueDate" | "plotId" | "sourceNote">;

export interface NdviObservation {
    date: string;
    meanNdvi: number;
    minNdvi: number;
    maxNdvi: number;
    validPixelPercentage: number | null;
}

export interface PropertyMonitoringData {
    configured: boolean;
    status: "NOT_CONFIGURED" | "NO_DATA" | "READY" | "PARTIAL" | "BOUNDARY_REQUIRED";
    message: string | null;
    from: string;
    to: string;
    observations: NdviObservation[];
    latestDate: string | null;
    previousDate: string | null;
    previousMeanNdvi: number | null;
    changeNdvi: number | null;
    source: string;
    bounds: [number, number, number, number] | [];
    resolutionMeters: number | null;
}

export interface PropertyImageOverlay {
    url: string;
    bounds: [number, number, number, number];
    date: string;
}

const endpoint = "/api/properties";

export const listProperties = (signal?: AbortSignal) => api<SavedProperty[]>(endpoint, { signal });
export const saveProperty = (request: PropertyRequest, id?: string) =>
    api<SavedProperty>(id ? `${endpoint}/${encodeURIComponent(id)}` : endpoint, { method: id ? "PUT" : "POST", body: request });
export const deleteProperty = (id: string) => api(`${endpoint}/${encodeURIComponent(id)}`, { method: "DELETE" });
const teamEndpoint = (id: string) => `${endpoint}/${encodeURIComponent(id)}/team`;
export const loadTeam = (id: string, signal?: AbortSignal) => api<TeamOverview>(teamEndpoint(id), { signal });
export const setTeamMode = (id: string, enabled: boolean) => api<TeamOverview>(`${teamEndpoint(id)}/mode`, { method: "PUT", body: { enabled } });
export const setTeamTitle = (id: string, title: string) => api<TeamOverview>(`${teamEndpoint(id)}/title`, { method: "PUT", body: { title } });
export const leaveTeam = (id: string) => api<void>(`${teamEndpoint(id)}/me`, { method: "DELETE" });
export const inviteTeamMember = (id: string, email: string, role: TeamRole) => api<TeamOverview>(`${teamEndpoint(id)}/invitations`, { method: "POST", body: { email, role } });
export const cancelTeamInvitation = (id: string, invitationId: string) => api<TeamOverview>(`${teamEndpoint(id)}/invitations/${encodeURIComponent(invitationId)}`, { method: "DELETE" });
export const changeTeamRole = (id: string, userId: string, role: TeamRole) => api<TeamOverview>(`${teamEndpoint(id)}/members/${encodeURIComponent(userId)}`, { method: "PUT", body: { role } });
export const removeTeamMember = (id: string, userId: string) => api<TeamOverview>(`${teamEndpoint(id)}/members/${encodeURIComponent(userId)}`, { method: "DELETE" });
export const acceptTeamInvitation = (token: string) => api<void>("/api/team-invitations/accept", { method: "POST", body: { token } });
export const listPropertyActivities = (id: string, signal?: AbortSignal) => api<PropertyActivity[]>(`${endpoint}/${encodeURIComponent(id)}/activities`, { signal });
export const createPropertyActivity = (id: string, body: PropertyActivityRequest) =>
    api<PropertyActivity>(`${endpoint}/${encodeURIComponent(id)}/activities`, { method: "POST", body });
export const deletePropertyActivity = (id: string, activityId: string) =>
    api(`${endpoint}/${encodeURIComponent(id)}/activities/${encodeURIComponent(activityId)}`, { method: "DELETE" });
const plotEndpoint = (id: string) => `${endpoint}/${encodeURIComponent(id)}/plots`;
export const listPlots = (id: string, signal?: AbortSignal) => api<Plot[]>(plotEndpoint(id), { signal });
export const savePlot = (id: string, body: PlotRequest, plotId?: string) =>
    api<Plot>(plotId ? `${plotEndpoint(id)}/${encodeURIComponent(plotId)}` : plotEndpoint(id), { method: plotId ? "PUT" : "POST", body });
export const deletePlot = (id: string, plotId: string) =>
    api(`${plotEndpoint(id)}/${encodeURIComponent(plotId)}`, { method: "DELETE" });
const actionEndpoint = (id: string) => `${endpoint}/${encodeURIComponent(id)}/actions`;
export const listFieldActions = (id: string, signal?: AbortSignal) => api<FieldAction[]>(actionEndpoint(id), { signal });
export const createFieldAction = (id: string, body: FieldActionRequest) =>
    api<FieldAction>(actionEndpoint(id), { method: "POST", body });
export const completeFieldAction = (id: string, actionId: string, body: { completionNote: string; amountMm: number | null }) =>
    api<FieldAction>(`${actionEndpoint(id)}/${encodeURIComponent(actionId)}/complete`, { method: "POST", body });
export const deleteFieldAction = (id: string, actionId: string) =>
    api(`${actionEndpoint(id)}/${encodeURIComponent(actionId)}`, { method: "DELETE" });

export const loadPropertyMonitoring = (id: string, from: string, to: string, maxCloudCoverage: number, signal?: AbortSignal) => {
    const query = new URLSearchParams({ from, to, maxCloudCoverage: String(maxCloudCoverage) });
    return api<PropertyMonitoringData>(`${endpoint}/${encodeURIComponent(id)}/monitoring?${query}`, { signal });
};

export const loadPropertyImage = (id: string, date: string, maxCloudCoverage: number, signal?: AbortSignal) => {
    const query = new URLSearchParams({ date, maxCloudCoverage: String(maxCloudCoverage) });
    return apiBlob(`${endpoint}/${encodeURIComponent(id)}/ndvi.png?${query}`, { signal });
};

export const propertyErrorMessage = (error: unknown, fallback: string) => {
    if (!(error instanceof ApiError)) return fallback;
    if (error.status === 0) return "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.";
    if (error.status === 401) return "Sua sessão expirou. Entre novamente para acessar suas propriedades.";
    if (error.status === 404) return "A propriedade não foi encontrada ou não está disponível para sua conta.";
    if (error.status === 429) return "O limite de consultas foi atingido. Aguarde antes de atualizar novamente.";
    if (error.status < 400 || error.status >= 500) return fallback;
    const text = error.serverMessage.trim();
    if (text && text.length < 500 && !text.startsWith("<")) {
        try {
            const parsed = JSON.parse(text) as { message?: string; detail?: string };
            return parsed.message ?? parsed.detail ?? fallback;
        } catch {
            return text;
        }
    }
    return fallback;
};

export const openPropertyRing = (geometry: PropertyGeometry | null): PropertyCoordinate[] => {
    const ring = geometry?.coordinates[0] ?? [];
    if (ring.length < 2) return ring;
    const first = ring[0];
    const last = ring[ring.length - 1];
    return (first[0] === last[0] && first[1] === last[1] ? ring.slice(0, -1) : ring).map(([lon, lat]) => [lon, lat]);
};

export const closedPropertyGeometry = (vertices: PropertyCoordinate[]): PropertyGeometry => ({
    type: "Polygon",
    coordinates: [[...vertices.map(([lon, lat]): PropertyCoordinate => [lon, lat]), [...vertices[0]] as PropertyCoordinate]],
});

/** Spherical area preview; the server validates and calculates the stored area. */
export const approximateHectares = (vertices: PropertyCoordinate[]) => {
    if (vertices.length < 3) return 0;
    let sum = 0;
    const radians = Math.PI / 180;
    for (let i = 0; i < vertices.length; i++) {
        const a = vertices[i];
        const b = vertices[(i + 1) % vertices.length];
        sum += (b[0] - a[0]) * radians * (2 + Math.sin(a[1] * radians) + Math.sin(b[1] * radians));
    }
    return Math.abs((sum * 6_371_008.8 ** 2) / 2) / 10_000;
};
