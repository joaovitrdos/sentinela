import type { FeatureCollection, MultiLineString } from "geojson";
import { normalizeText } from "@/components/app/brazil-map/geo-data";
import { api } from "@/lib/api";

// Dados hidrologicos vindos do backend (que le o SNIRH/ANA). O SNIRH nao libera CORS, por isso nao vem direto.

export type ThemeId = "rain" | "level" | "stations";

export interface RainReading {
    codigo: number;
    nome: string;
    municipio: string | null;
    uf: string | null;
    bacia: string | null;
    responsavel: string | null;
    chuvaMm24h: number;
    classe: string;
    dataUltimoDado: string;
    idadeHoras: number;
    latitude: number | null;
    longitude: number | null;
    requiresAttention: boolean;
}

export interface RiverLevel {
    codigo: number;
    nome: string | null;
    municipio: string | null;
    uf: string | null;
    bacia: string | null;
    subBacia: string | null;
    responsavel: string | null;
    operadora: string | null;
    /** Rede da estacao como vem do SNIRH ("RHN", "CotaOnline", "Setor Elétrico"...). */
    projeto: string | null;
    situacaoEstacao: "ATIVA" | "MANUTENCAO" | null;
    cotaCm: number | null;
    status: "ATENCAO" | "ALERTA" | "INUNDACAO" | "NORMAL" | "ESTIAGEM" | "SEM_DADOS" | "SEM_REFERENCIA" | "DESCONHECIDO";
    statusDescricao: string | null;
    dataUltimoDado: string | null;
    idadeHoras: number | null;
    latitude: number | null;
    longitude: number | null;
    requiresAttention: boolean;
}

export interface HydroStation {
    codigo: number;
    nome: string | null;
    tipo: "FLUVIOMETRICA" | "PLUVIOMETRICA";
    operando: boolean;
    telemetrica: boolean;
    rio: string | null;
    uf: string | null;
    municipio: string | null;
    bacia: string | null;
    subBacia: string | null;
    responsavel: string | null;
    operadora: string | null;
    altitude: number | null;
    areaDrenagem: number | null;
    latitude: number | null;
    longitude: number | null;
}

/** Ponto comum aos tres temas: e o que o mapa e a tabela usam. */
export interface HydroPoint {
    /** Indice no array (id do recurso no mapa). */
    id: number;
    codigo: number;
    nome: string;
    municipio: string;
    /** Nome do estado como vem do SNIRH (ex.: "PARÁ"). */
    estado: string;
    lon: number;
    lat: number;
    /** Classe da legenda (cor). */
    classKey: string;
    /** Ordem de desenho: maior fica por cima. */
    sort: number;
    /** false = estacao desativada (fica apagada). */
    active: boolean;
    /** Texto pronto para a busca (sem acento, minusculo). */
    search: string;
    rain?: RainReading;
    level?: RiverLevel;
    station?: HydroStation;
}

export interface ClassDef {
    key: string;
    color: string;
    /** Chave i18n do rotulo da legenda. */
    label: string;
}

// Cores escolhidas para aparecer bem tanto no satelite quanto nos mapas claros.
export const THEME_CLASSES: Record<ThemeId, ClassDef[]> = {
    rain: [
        { key: "very-strong", color: "#ef4444", label: "hydro.rain.veryStrong" },
        { key: "strong", color: "#f97316", label: "hydro.rain.strong" },
        { key: "moderate", color: "#3b82f6", label: "hydro.rain.moderate" },
        { key: "weak", color: "#7dd3fc", label: "hydro.rain.weak" },
        { key: "none", color: "#a3a3a3", label: "hydro.rain.none" },
    ],
    level: [
        { key: "flood", color: "#991b1b", label: "hydro.level.flood" },
        { key: "alert", color: "#dc2626", label: "hydro.level.alert" },
        { key: "attention", color: "#ef4444", label: "hydro.level.attention" },
        { key: "normal", color: "#22c55e", label: "hydro.level.normal" },
        { key: "drought", color: "#f59e0b", label: "hydro.level.drought" },
        { key: "stale", color: "#a3a3a3", label: "hydro.level.stale" },
        { key: "no-ref", color: "#e5e7eb", label: "hydro.level.noRef" },
    ],
    stations: [
        { key: "fluvio", color: "#22d3ee", label: "hydro.stations.fluvio" },
        { key: "pluvio", color: "#a78bfa", label: "hydro.stations.pluvio" },
    ],
};

const RAIN_KEYS: Record<string, [string, number]> = {
    "chuva muito forte": ["very-strong", 5],
    "chuva forte": ["strong", 4],
    "chuva moderada": ["moderate", 3],
    "chuva fraca": ["weak", 2],
    "sem chuva": ["none", 1],
};

const LEVEL_KEYS: Record<RiverLevel["status"], [string, number]> = {
    INUNDACAO: ["flood", 7],
    ALERTA: ["alert", 6],
    ATENCAO: ["attention", 5],
    ESTIAGEM: ["drought", 4],
    NORMAL: ["normal", 3],
    SEM_DADOS: ["stale", 2],
    SEM_REFERENCIA: ["no-ref", 1],
    DESCONHECIDO: ["stale", 2],
};

const searchKey = (...parts: (string | number | null | undefined)[]) => normalizeText(parts.filter((p) => p != null).join(" "));

const toPoints = <T>(rows: T[], map: (row: T, id: number) => HydroPoint | null) => {
    const points: HydroPoint[] = [];
    for (const row of rows) {
        const point = map(row, points.length);
        if (point) points.push(point);
    }
    return points;
};

const fromRain = (rows: RainReading[]) =>
    toPoints(rows, (r, id) => {
        if (r.latitude == null || r.longitude == null) return null;
        const [classKey, sort] = RAIN_KEYS[normalizeText(r.classe)] ?? ["none", 1];
        return {
            id,
            codigo: r.codigo,
            nome: r.nome ?? String(r.codigo),
            municipio: r.municipio ?? "",
            estado: r.uf ?? "",
            lon: r.longitude,
            lat: r.latitude,
            classKey,
            sort,
            active: true,
            search: searchKey(r.nome, r.municipio, r.codigo, r.bacia),
            rain: r,
        };
    });

const fromLevel = (rows: RiverLevel[]) =>
    toPoints(rows, (r, id) => {
        if (r.latitude == null || r.longitude == null) return null;
        const [classKey, sort] = LEVEL_KEYS[r.status] ?? ["stale", 2];
        return {
            id,
            codigo: r.codigo,
            nome: r.nome ?? String(r.codigo),
            municipio: r.municipio ?? "",
            estado: r.uf ?? "",
            lon: r.longitude,
            lat: r.latitude,
            classKey,
            sort,
            // Estacao em manutencao fica apagada no mapa.
            active: r.situacaoEstacao !== "MANUTENCAO",
            search: searchKey(r.nome, r.municipio, r.codigo, r.bacia, r.subBacia, r.operadora, r.projeto),
            level: r,
        };
    });

const fromStations = (rows: HydroStation[]) =>
    toPoints(rows, (s, id) => {
        if (s.latitude == null || s.longitude == null) return null;
        return {
            id,
            codigo: s.codigo,
            nome: s.nome ?? String(s.codigo),
            municipio: s.municipio ?? "",
            estado: s.uf ?? "",
            lon: s.longitude,
            lat: s.latitude,
            classKey: s.tipo === "FLUVIOMETRICA" ? "fluvio" : "pluvio",
            sort: s.operando ? 2 : 1,
            active: s.operando,
            search: searchKey(s.nome, s.municipio, s.codigo, s.rio, s.responsavel),
            station: s,
        };
    });

// Cache por sessao (o backend tambem guarda em cache). Nivel e chuva expiram em 10 min.
const cache = new Map<ThemeId, { at: number; pending: boolean; promise: Promise<HydroPoint[]> }>();
const TTL: Record<ThemeId, number> = { rain: 10 * 60_000, level: 10 * 60_000, stations: 6 * 60 * 60_000 };

// O prefixo /api do front e removido pelo proxy; estes endpoints ja comecam com /api no backend.
const LOADERS: Record<ThemeId, () => Promise<HydroPoint[]>> = {
    rain: () => api<RainReading[]>("/api/hydro/rain/24h").then(fromRain),
    level: () => api<RiverLevel[]>("/api/hydro/levels").then(fromLevel),
    stations: () => api<HydroStation[]>("/api/hydro/stations").then(fromStations),
};

export const loadTheme = (theme: ThemeId, force = false): Promise<HydroPoint[]> => {
    const hit = cache.get(theme);
    if (hit && (hit.pending || (!force && Date.now() - hit.at < TTL[theme]))) return hit.promise;
    const promise = LOADERS[theme]();
    const entry = { at: Date.now(), pending: true, promise };
    cache.set(theme, entry);
    void promise.then(
        () => {
            entry.pending = false;
            entry.at = Date.now();
        },
        () => {
            if (cache.get(theme) === entry) cache.delete(theme);
        },
    );
    return promise;
};

/** Rios principais da ANA (GeoJSON). Sao alguns MB: carrega uma vez por sessao (o navegador tambem guarda por um dia). */
let riversPromise: Promise<FeatureCollection<MultiLineString, { nome: string | null; km: number }>> | null = null;
export const loadRivers = () => {
    riversPromise ??= api<FeatureCollection<MultiLineString, { nome: string | null; km: number }>>("/api/hydro/rivers");
    riversPromise.catch(() => (riversPromise = null));
    return riversPromise;
};

/** "SALINÓPOLIS" -> "Salinópolis"; "RIO DE JANEIRO" -> "Rio de Janeiro"; siglas (UHE, PCH, II...) ficam em maiusculas. */
const LOWER = new Set(["de", "da", "do", "das", "dos", "e", "d"]);
const ACRONYMS = new Set([
    "uhe",
    "pch",
    "cgh",
    "ute",
    "eta",
    "ete",
    "ana",
    "inmet",
    "cprm",
    "sgb",
    "cemig",
    "copel",
    "chesf",
    "daee",
    "saae",
    "br",
    "ii",
    "iii",
    "iv",
    "vi",
    "vii",
    "viii",
    "ix",
    "xi",
]);
export const titleCase = (text: string | null | undefined) =>
    (text ?? "")
        // "APODI,PIRANHAS" -> "Apodi, Piranhas"
        .replace(/,(?=\S)/g, ", ")
        .toLocaleLowerCase("pt-BR")
        .split(/(\s+|-|\/|\(|\))/)
        .map((word, i) => {
            if (ACRONYMS.has(word)) return word.toLocaleUpperCase("pt-BR");
            if (i > 0 && LOWER.has(word)) return word;
            return word.charAt(0).toLocaleUpperCase("pt-BR") + word.slice(1);
        })
        .join("");
