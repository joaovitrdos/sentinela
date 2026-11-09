import { ApiError, api } from "./api";
import type { PropertyGeometry } from "./property-data";

const property = (id: string) => `/api/properties/${encodeURIComponent(id)}`;

export interface PropertyAlert {
    alertId: string;
    type: "FROST" | "HEAVY_RAIN" | "INMET" | "RIVER" | "NDVI";
    severity: "ATENCAO" | "PERIGO";
    title: string;
    message: string;
    disclaimer: string | null;
    createdAt: string;
}

export interface AlertSettings {
    enabled: boolean;
    frostTemperatureC: number;
    heavyRainMm: number;
    ndviDrop: number;
    riverRadiusKm: number;
}

export interface SprayHour {
    time: string;
    suitable: boolean;
    temperatureC: number;
    humidity: number;
    windKph: number;
    rainChance: number;
    precipitationMm: number;
    reasons: string[];
}

export interface SprayWindows {
    hours: SprayHour[];
    windows: { from: string; to: string; hours: number }[];
    source: string;
    disclaimer: string;
}

export interface IrrigationDay {
    date: string;
    forecast: boolean;
    et0Mm: number;
    cropDemandMm: number;
    rainMm: number;
    irrigationMm: number;
    deficitMm: number;
}

export interface IrrigationRecommendation {
    plotName: string;
    cropName: string | null;
    cropKnown: boolean;
    cropCoefficient: number;
    recommendedMm: number;
    daysWithoutData: number;
    days: IrrigationDay[];
    warnings: string[];
    method: string;
    source: string;
    disclaimer: string;
}

export interface CarProperty {
    code: string;
    status: string;
    condition: string;
    city: string;
    state: string;
    areaHectares: number | null;
    geometry: PropertyGeometry;
    simplified: boolean;
    ignoredParts: number;
    source: string;
}

export interface ClimatePeriod {
    from: string;
    to: string;
    rainMm: number;
    normalRainMm: number;
    anomalyPercent: number | null;
    driestRank: number;
    yearsCompared: number;
}

export interface ClimateMonth {
    month: string;
    complete: boolean;
    rainMm: number;
    normalRainMm: number;
    anomalyPercent: number | null;
    driestRank: number;
    yearsCompared: number;
    meanMaxTemperatureC: number;
    normalMaxTemperatureC: number;
}

export interface ClimateHistory {
    firstDate: string;
    lastDate: string;
    years: number;
    last30Days: ClimatePeriod | null;
    recentMonths: ClimateMonth[];
    normals: { month: number; meanRainMm: number; minRainMm: number; minYear: number; maxRainMm: number; maxYear: number }[];
    sources: { name: string; url: string; license: string }[];
}

export interface PlantingWindowOption {
    harvest: string;
    cycleCode: number;
    soilCode: number;
    climateName: string | null;
    managementCode: number;
    managementName: string;
    productivity: string | null;
    managementLevel: number | null;
    ordinance: string | null;
    risks: number[];
    openNow: boolean;
    window: { fromDecendio: number; toDecendio: number; from: string; to: string; bestRiskPercent: number } | null;
}

export interface PlantingWindow {
    status: "OK" | "MUNICIPALITY_NOT_FOUND" | "NOT_IMPORTED";
    municipality: string | null;
    uf: string | null;
    ibgeCode: number | null;
    crops: string[];
    crop: string | null;
    currentDecendio: number;
    options: PlantingWindowOption[];
    sources: { name: string; url: string; license: string }[];
}

export const advisoryApi = {
    alerts: (id: string, signal?: AbortSignal) => api<PropertyAlert[]>(`${property(id)}/alerts`, { signal }),
    alertSettings: (id: string, signal?: AbortSignal) => api<AlertSettings>(`${property(id)}/alerts/settings`, { signal }),
    saveAlertSettings: (id: string, body: AlertSettings) => api<AlertSettings>(`${property(id)}/alerts/settings`, { method: "PUT", body }),
    sprayWindows: (id: string, signal?: AbortSignal) => api<SprayWindows>(`${property(id)}/spray-windows`, { signal }),
    climateHistory: (id: string, signal?: AbortSignal) => api<ClimateHistory>(`${property(id)}/climate-history`, { signal }),
    plantingWindow: (id: string, crop: string | null, signal?: AbortSignal) =>
        api<PlantingWindow>(`${property(id)}/planting-window${crop ? `?crop=${encodeURIComponent(crop)}` : ""}`, { signal }),
    irrigation: (id: string, plotId: string, signal?: AbortSignal) =>
        api<IrrigationRecommendation>(`${property(id)}/plots/${encodeURIComponent(plotId)}/irrigation-recommendation`, { signal }),
    car: (code: string) => api<CarProperty>(`/api/car/${encodeURIComponent(code)}`),
};

/** O plano que vale aqui é o do dono da propriedade, e só o backend sabe qual é. */
export const isPlanBlocked = (error: unknown) =>
    error instanceof ApiError && error.status === 403 && error.serverMessage.trim().startsWith("Seu plano");
