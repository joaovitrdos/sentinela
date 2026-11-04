// Fundos cartograficos em vetor ou raster, compativeis com o WebGL do MapLibre.
// Nenhum exige chave. Atencao a licenca: Esri e OpenStreetMap pedem atribuicao e tem limites/politicas de uso; em producao, revise os termos.

export type BasemapId = "monitoring" | "minimal" | "satellite" | "hybrid" | "streets" | "topo" | "ocean" | "light" | "dark";

export interface Basemap {
    id: BasemapId;
    /** Chave i18n do nome. */
    label: string;
    /** Camadas raster, de baixo para cima. Monitoring usa seu estilo vetorial; minimal usa o desenho do app. */
    layers: { tiles: string[]; maxzoom?: number }[];
    attribution: string;
    /** Tom do mapa: define a cor das divisas e dos pontos por cima (claras no escuro, escuras no claro). */
    tone: "light" | "dark" | "app" | "theme";
    /** Miniatura da galeria. */
    thumb?: string;
}

const esri = (service: string) => `https://server.arcgisonline.com/ArcGIS/rest/services/${service}/MapServer/tile/{z}/{y}/{x}`;
const osm = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const thumbOf = (template: string) => template.replace("{z}", "3").replace("{x}", "2").replace("{y}", "4");

export const BASEMAPS: Basemap[] = [
    {
        id: "monitoring",
        label: "hydro.basemaps.monitoring",
        layers: [],
        attribution: "OpenFreeMap · © OpenMapTiles · © OpenStreetMap",
        tone: "theme",
        thumb: "/maps/monitoring.svg",
    },
    {
        id: "satellite",
        label: "hydro.basemaps.satellite",
        layers: [{ tiles: [esri("World_Imagery")], maxzoom: 19 }],
        attribution: "© Esri, Maxar, Earthstar Geographics",
        tone: "dark",
        thumb: thumbOf(esri("World_Imagery")),
    },
    {
        id: "hybrid",
        label: "hydro.basemaps.hybrid",
        layers: [
            { tiles: [esri("World_Imagery")], maxzoom: 19 },
            { tiles: [esri("Reference/World_Boundaries_and_Places")], maxzoom: 19 },
        ],
        attribution: "© Esri, Maxar, Earthstar Geographics",
        tone: "dark",
        thumb: thumbOf(esri("World_Imagery")),
    },
    {
        id: "streets",
        label: "hydro.basemaps.streets",
        layers: [{ tiles: [osm], maxzoom: 19 }],
        attribution: "© OpenStreetMap",
        tone: "light",
        thumb: thumbOf(osm),
    },
    {
        id: "topo",
        label: "hydro.basemaps.topo",
        layers: [{ tiles: [esri("World_Topo_Map")], maxzoom: 19 }],
        attribution: "© Esri, HERE, Garmin, OpenStreetMap",
        tone: "light",
        thumb: thumbOf(esri("World_Topo_Map")),
    },
    {
        id: "ocean",
        label: "hydro.basemaps.ocean",
        layers: [
            { tiles: [esri("Ocean/World_Ocean_Base")], maxzoom: 13 },
            { tiles: [esri("Ocean/World_Ocean_Reference")], maxzoom: 13 },
        ],
        attribution: "© Esri, GEBCO, NOAA, National Geographic",
        tone: "light",
        thumb: thumbOf(esri("Ocean/World_Ocean_Base")),
    },
    {
        id: "light",
        label: "hydro.basemaps.light",
        layers: [
            { tiles: [esri("Canvas/World_Light_Gray_Base")], maxzoom: 16 },
            { tiles: [esri("Canvas/World_Light_Gray_Reference")], maxzoom: 16 },
        ],
        attribution: "© Esri, HERE, Garmin, OpenStreetMap",
        tone: "light",
        thumb: thumbOf(esri("Canvas/World_Light_Gray_Base")),
    },
    {
        id: "dark",
        label: "hydro.basemaps.dark",
        layers: [
            { tiles: [esri("Canvas/World_Dark_Gray_Base")], maxzoom: 16 },
            { tiles: [esri("Canvas/World_Dark_Gray_Reference")], maxzoom: 16 },
        ],
        attribution: "© Esri, HERE, Garmin, OpenStreetMap",
        tone: "dark",
        thumb: thumbOf(esri("Canvas/World_Dark_Gray_Base")),
    },
    {
        id: "minimal",
        label: "hydro.basemaps.minimal",
        layers: [],
        attribution: "",
        tone: "app",
    },
];

export const basemapById = (id: BasemapId) => BASEMAPS.find((b) => b.id === id) ?? BASEMAPS[0];

/** Rios em vetor (OpenFreeMap / OpenMapTiles, dados do OpenStreetMap): gratuito e sem chave. */
export const RIVERS_SOURCE = "https://tiles.openfreemap.org/planet";
export const VECTOR_SOURCE_ID = "openmaptiles";
export const MAP_GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf";
export const VECTOR_ATTRIBUTION =
    '<a href="https://openfreemap.org/" target="_blank" rel="noopener">OpenFreeMap</a> · <a href="https://www.openmaptiles.org/" target="_blank" rel="noopener">© OpenMapTiles</a> · <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">© OpenStreetMap</a>';
/** Rios principais da ANA (camada RiosPrincipais do SNIRH), servidos pelo backend. */
export const ANA_RIVERS_ATTRIBUTION = "Rios principais: ANA/SNIRH";

const BASEMAP_STORAGE_KEY = "sentinela-basemap";

export const preferredBasemap = (fallback: BasemapId = "monitoring"): BasemapId => {
    try {
        const saved = localStorage.getItem(BASEMAP_STORAGE_KEY);
        return BASEMAPS.find((basemap) => basemap.id === saved)?.id ?? fallback;
    } catch {
        return fallback;
    }
};

export const rememberBasemap = (basemap: BasemapId) => {
    try {
        localStorage.setItem(BASEMAP_STORAGE_KEY, basemap);
    } catch {
        // Storage may be unavailable; the current map selection still works.
    }
};
