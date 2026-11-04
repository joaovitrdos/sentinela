import { FormAlert } from "@/components/app/form-alert";
import type { KeyboardEvent, ReactNode, Ref } from "react";
import { useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ArrowLeft, ChevronRight, Home03, MarkerPin01, Minus, Plus, RefreshCw01 } from "@untitledui/icons";
import { geoCentroid } from "d3-geo";
import type { Feature, FeatureCollection, MultiLineString, MultiPolygon, Point, Polygon } from "geojson";
import type { ExpressionSpecification, GeoJSONSource, MapMouseEvent, StyleSpecification } from "maplibre-gl";
import { AttributionControl, Map as MapLibre, Marker, Popup, ScaleControl, setWorkerUrl } from "maplibre-gl";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import "maplibre-gl/dist/maplibre-gl.css";
import { useTranslation } from "react-i18next";
import { LoadingIndicator } from "@/components/application/loading-indicator/loading-indicator";
import { Button } from "@/components/base/buttons/button";
import { cx } from "@/utils/cx";
import type { BasemapId } from "./basemaps";
import { ANA_RIVERS_ATTRIBUTION, MAP_GLYPHS, RIVERS_SOURCE, VECTOR_ATTRIBUTION, VECTOR_SOURCE_ID, basemapById } from "./basemaps";
import { CitySearch } from "./city-search";
import type { CityFeature, CountryData, StateData, StateFeature } from "./geo-data";
import { loadCountry, loadState } from "./geo-data";
import { monitoringLayers } from "./monitoring-basemap";

// O bundler empacota o MapLibre em outro arquivo; o worker precisa do endereco explicito.
setWorkerUrl(workerUrl);

type View = { level: "country" } | { level: "state"; uf: string } | { level: "city"; uf: string; cityId: number };
type Hover = { kind: "state"; uf: string } | { kind: "city"; id: number } | { kind: "point"; id: number } | null;

/** Camada de pontos (estacoes): cada recurso tem properties { id, k (classe), s (ordem), a (1 = ativa) }. */
export interface PointLayer {
    data: FeatureCollection<Point>;
    /** Cor por classe (k). */
    colors: Record<string, string>;
    /** Classes escondidas pela legenda. */
    hidden?: string[];
}

/** Balao preso a um ponto do mapa (detalhes da estacao clicada). */
export interface MapPopup {
    key: string;
    lngLat: [number, number];
    content: ReactNode;
    onClose: () => void;
}

/** Acoes que a pagina pode pedir ao mapa (ex.: clicar numa linha da tabela). */
export interface BrazilMapController {
    flyTo: (lngLat: [number, number], zoom?: number) => void;
    openState: (uf: string) => void;
}
type BBox = [[number, number], [number, number]];
type Shape = Polygon | MultiPolygon;

// Paleta neutra com um unico tom de destaque (o azul da marca).
const PALETTE = {
    light: {
        land: "#eef0f3",
        landHover: "#e1e4e9",
        border: "rgba(10,13,18,0.16)",
        outline: "rgba(10,13,18,0.45)",
        cityLine: "rgba(10,13,18,0.08)",
        highlight: "rgba(10,13,18,0.8)",
        accent: "#1570ef",
        capital: "#0a0d12",
        capitalRing: "#ffffff",
    },
    dark: {
        land: "#18181b",
        landHover: "#232327",
        border: "rgba(255,255,255,0.13)",
        outline: "rgba(255,255,255,0.4)",
        cityLine: "rgba(255,255,255,0.07)",
        highlight: "rgba(255,255,255,0.85)",
        accent: "#53b1fd",
        capital: "#f5f5f5",
        capitalRing: "#0a0a0a",
    },
};
type Palette = (typeof PALETTE)["light"];

// Por cima de um mapa de fundo real o preenchimento some (o fundo aparece) e as linhas acompanham o tom do mapa.
const OVER_BASEMAP: Record<"light" | "dark", Palette & { dim: string; pointRing: string }> = {
    dark: {
        land: "rgba(0,0,0,0)",
        landHover: "rgba(255,255,255,0.14)",
        border: "rgba(255,255,255,0.45)",
        outline: "rgba(255,255,255,0.85)",
        cityLine: "rgba(255,255,255,0.28)",
        highlight: "#ffffff",
        accent: "#7dd3fc",
        capital: "#ffffff",
        capitalRing: "#0a0a0a",
        dim: "rgba(0,0,0,0.45)",
        pointRing: "rgba(10,10,10,0.85)",
    },
    light: {
        land: "rgba(0,0,0,0)",
        landHover: "rgba(10,13,18,0.08)",
        border: "rgba(10,13,18,0.4)",
        outline: "rgba(10,13,18,0.65)",
        cityLine: "rgba(10,13,18,0.2)",
        highlight: "#0a0d12",
        accent: "#1570ef",
        capital: "#0a0d12",
        capitalRing: "#ffffff",
        dim: "rgba(255,255,255,0.55)",
        pointRing: "rgba(255,255,255,0.95)",
    },
};

const EMPTY: FeatureCollection = { type: "FeatureCollection", features: [] };
/** Cor da agua (rios e lagos), a mesma em todos os mapas de fundo. */
const RIVER_COLOR = "#22d3ee";

const isDark = () => document.documentElement.classList.contains("dark-mode");

/** Rotulo de marcador: o MapLibre controla o elemento externo, o estilo fica no interno. */
const labelElement = (className: string, text: string) => {
    const wrapper = document.createElement("div");
    const label = document.createElement("span");
    label.className = className;
    label.textContent = text;
    wrapper.appendChild(label);
    return wrapper;
};
const prefersReducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const bboxOf = (geometries: Shape[]): BBox => {
    let x0 = Infinity,
        y0 = Infinity,
        x1 = -Infinity,
        y1 = -Infinity;
    for (const g of geometries) {
        const polygons = g.type === "Polygon" ? [g.coordinates] : g.coordinates;
        for (const polygon of polygons)
            for (const ring of polygon)
                for (const [x, y] of ring) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
    }
    return [
        [x0, y0],
        [x1, y1],
    ];
};

const useNumberFormat = () => {
    const { i18n } = useTranslation();
    return useMemo(() => {
        const plain = new Intl.NumberFormat(i18n.language);
        const compact = new Intl.NumberFormat(i18n.language, { notation: "compact", maximumFractionDigits: 1 });
        return { plain: (n: number) => plain.format(n), compact: (n: number) => compact.format(n) };
    }, [i18n.language]);
};

export interface BrazilMapProps {
    className?: string;
    /** hero: lateral do login. full: pagina inicial. */
    variant?: "hero" | "full";
    /** Mapa de fundo real (satelite, ruas...). "minimal" = so o desenho do app. */
    basemap?: BasemapId;
    /** Destaca os rios: os principais da ANA (riverLines) na visao ampla e os do OpenStreetMap ao aproximar. */
    rivers?: boolean;
    /** Rios principais da ANA (GeoJSON do backend). Sem eles, os rios so aparecem a partir da escala de estado. */
    riverLines?: FeatureCollection<MultiLineString> | null;
    /** Pontos por cima do mapa (estacoes do tema escolhido). */
    points?: PointLayer | null;
    renderPointTooltip?: (id: number) => ReactNode;
    onPointClick?: (id: number) => void;
    /** Coordinates clicked anywhere on the map, including on a city or station. */
    onMapClick?: (coordinates: [number, number]) => void;
    /** Selected coordinate shown as a marker on the map. */
    selectedLocation?: [number, number] | null;
    popup?: MapPopup | null;
    /** Avisado quando o estado aberto muda (null = Brasil inteiro). */
    onScopeChange?: (uf: string | null) => void;
    /** Controles extras na barra de cima (ex.: seletor de tema). */
    toolbarExtra?: ReactNode;
    /** Botoes extras na coluna de controles do canto direito (mapa base, camadas, legenda). */
    controls?: ReactNode;
    /** Elementos por cima do mapa, posicionados pela pagina (legenda, alca da tabela). */
    overlay?: ReactNode;
    /** Conteudo logo abaixo do mapa (ex.: tabela recolhivel). */
    below?: ReactNode;
    /** Altura da area do mapa quando o painel cresce com o conteudo de baixo (padrao: ocupa o espaco livre). */
    mapClassName?: string;
    controllerRef?: Ref<BrazilMapController>;
}

/**
 * Painel do mapa em tres faixas, sem nada por cima do desenho:
 * barra de ferramentas (trilha + busca), area do mapa e barra de informacoes do que esta selecionado.
 */
export const BrazilMap = ({
    className,
    variant = "hero",
    basemap = "minimal",
    rivers = false,
    riverLines = null,
    points = null,
    renderPointTooltip,
    onPointClick,
    onMapClick,
    selectedLocation = null,
    popup = null,
    onScopeChange,
    toolbarExtra,
    controls,
    overlay,
    below,
    mapClassName,
    controllerRef,
}: BrazilMapProps) => {
    const { t, i18n } = useTranslation();
    const fmt = useNumberFormat();
    // Callbacks da pagina lidos pelos eventos do mapa (criados uma vez so).
    const pointClickRef = useRef(onPointClick);
    pointClickRef.current = onPointClick;
    const mapClickRef = useRef(onMapClick);
    mapClickRef.current = onMapClick;
    const hasBasemap = basemap !== "minimal";

    const [country, setCountry] = useState<CountryData | null>(null);
    const [failed, setFailed] = useState(false);
    const [ready, setReady] = useState(false);
    const [view, setView] = useState<View>({ level: "country" });
    const [stateData, setStateData] = useState<{ uf: string; data: StateData } | null>(null);
    const [hover, setHover] = useState<Hover>(null);
    const [locationBusy, setLocationBusy] = useState(false);
    const [locationError, setLocationError] = useState<string | null>(null);
    const locationRequestRef = useRef(0);
    const cancelLocation = useCallback(() => {
        locationRequestRef.current += 1;
        setLocationBusy(false);
        setLocationError(null);
    }, []);

    const containerRef = useRef<HTMLDivElement>(null);
    const mapRef = useRef<MapLibre | null>(null);
    const tooltipRef = useRef<HTMLDivElement>(null);
    const stateLabelsRef = useRef<Marker[]>([]);
    const cityLabelsRef = useRef<Marker[]>([]);
    const selectedMarkerRef = useRef<Marker | null>(null);
    const hoverRef = useRef<Hover>(null);
    const viewRef = useRef(view);
    viewRef.current = view;

    const activeUf = view.level === "country" ? null : view.uf;

    // ---------- Dados ----------

    const fetchCountry = useCallback(() => {
        setFailed(false);
        loadCountry()
            .then(setCountry)
            .catch(() => setFailed(true));
    }, []);
    useEffect(fetchCountry, [fetchCountry]);

    useEffect(() => {
        if (!activeUf) return;
        let alive = true;
        loadState(activeUf)
            .then((data) => alive && setStateData({ uf: activeUf, data }))
            .catch(() => undefined);
        return () => {
            alive = false;
        };
    }, [activeUf]);

    const states = useMemo(() => {
        if (!country) return null;
        const byUf = new Map<string, { feature: StateFeature; bbox: BBox; center: [number, number] }>();
        for (const feature of country.states) {
            byUf.set(feature.properties.sigla, { feature, bbox: bboxOf([feature.geometry]), center: geoCentroid(feature) as [number, number] });
        }
        return { byUf, bbox: bboxOf(country.states.map((s) => s.geometry)) };
    }, [country]);

    // Municipios do estado aberto, com peso 0..1 pela populacao (escala log) para o preenchimento.
    const cities = useMemo(() => {
        if (!stateData || stateData.uf !== activeUf) return null;
        const list = stateData.data.cities;
        const logs = list.map((c) => Math.log10(Math.max(c.properties.populacao ?? 1, 1)));
        const min = Math.min(...logs);
        const max = Math.max(...logs);
        const byId = new Map<number, { feature: CityFeature; bbox: BBox; center: [number, number] }>();
        const features = list.map((c, i) => {
            byId.set(c.properties.id, { feature: c, bbox: bboxOf([c.geometry]), center: geoCentroid(c) as [number, number] });
            return { ...c, properties: { ...c.properties, w: max > min ? (logs[i] - min) / (max - min) : 0.5 } };
        });
        return { byId, collection: { type: "FeatureCollection", features } as FeatureCollection, borders: stateData.data.borders };
    }, [stateData, activeUf]);

    const activeState = activeUf ? states?.byUf.get(activeUf) : undefined;
    const activeCity = view.level === "city" ? cities?.byId.get(view.cityId) : undefined;

    // ---------- Criacao do mapa (uma vez) ----------

    useEffect(() => {
        const container = containerRef.current;
        if (!country || !states || !container || mapRef.current) return;

        const palette = isDark() ? PALETTE.dark : PALETTE.light;
        const style: StyleSpecification = { version: 8, glyphs: MAP_GLYPHS, sources: {}, layers: [] };

        let map: MapLibre;
        try {
            map = new MapLibre({
                container,
                style,
                bounds: states.bbox,
                fitBoundsOptions: { padding: 24 },
                renderWorldCopies: false,
                attributionControl: false,
                dragRotate: false,
                pitchWithRotate: false,
                touchPitch: false,
                // Com mapa de fundo real da para aproximar ate o nivel da rua.
                maxZoom: variant === "full" ? 19 : 11,
                // Full maps use the same direct gestures as the property map.
                cooperativeGestures: variant === "hero" && window.matchMedia("(max-width: 1023px)").matches,
                locale: {
                    "CooperativeGesturesHandler.WindowsHelpText": t("map.gestures.windows"),
                    "CooperativeGesturesHandler.MacHelpText": t("map.gestures.mac"),
                    "CooperativeGesturesHandler.MobileHelpText": t("map.gestures.mobile"),
                },
                canvasContextAttributes: { antialias: true },
            });
        } catch {
            setFailed(true);
            return;
        }
        mapRef.current = map;
        map.touchZoomRotate.disableRotation();
        map.addControl(new AttributionControl({ compact: true }), "bottom-right");
        if (variant === "full") map.addControl(new ScaleControl({ maxWidth: 100, unit: "metric" }), "bottom-left");
        const resize = new ResizeObserver(() => map.resize());
        resize.observe(container);

        map.on("load", () => {
            const capitals: FeatureCollection<Point> = {
                type: "FeatureCollection",
                features: country.states
                    .filter((s) => s.properties.capitalCoord)
                    .map((s) => ({
                        type: "Feature",
                        properties: { sigla: s.properties.sigla },
                        geometry: { type: "Point", coordinates: s.properties.capitalCoord! },
                    })),
            };
            const statesFc: FeatureCollection = { type: "FeatureCollection", features: country.states as Feature[] };
            const geo = { type: "geojson" as const, tolerance: 0.45, maxzoom: 12 };

            map.addSource("states", { ...geo, data: statesFc, promoteId: "id", attribution: "Limites: IBGE" });
            map.addSource("state-borders", { ...geo, data: { type: "Feature", properties: {}, geometry: country.borders } });
            map.addSource("outline", { ...geo, data: { type: "Feature", properties: {}, geometry: country.outline } });
            map.addSource("cities", { ...geo, data: EMPTY, promoteId: "id" });
            map.addSource("city-borders", { ...geo, data: EMPTY });
            map.addSource("capitals", { type: "geojson", data: capitals });

            const hovered = ["boolean", ["feature-state", "hover"], false];
            const selected = ["boolean", ["feature-state", "selected"], false];

            map.addLayer({
                id: "states-fill",
                type: "fill",
                source: "states",
                paint: {
                    "fill-color": ["case", hovered, palette.landHover, palette.land] as never,
                    "fill-opacity-transition": { duration: 300 },
                },
            });
            map.addLayer({
                id: "cities-fill",
                type: "fill",
                source: "cities",
                paint: {
                    "fill-color": palette.accent,
                    "fill-opacity": ["case", selected, 0.85, hovered, ["+", 0.22, ["*", 0.4, ["get", "w"]]], ["+", 0.03, ["*", 0.34, ["get", "w"]]]] as never,
                },
            });
            map.addLayer({ id: "city-borders", type: "line", source: "city-borders", paint: { "line-color": palette.cityLine, "line-width": 0.6 } });
            map.addLayer({
                id: "state-borders",
                type: "line",
                source: "state-borders",
                paint: { "line-color": palette.border, "line-width": ["interpolate", ["linear"], ["zoom"], 3, 0.6, 7, 1.1] as never },
            });
            map.addLayer({
                id: "outline",
                type: "line",
                source: "outline",
                paint: { "line-color": palette.outline, "line-width": ["interpolate", ["linear"], ["zoom"], 3, 0.9, 7, 1.6] as never },
            });
            map.addLayer({
                id: "state-active",
                type: "line",
                source: "states",
                filter: ["==", ["get", "sigla"], ""],
                paint: { "line-color": palette.accent, "line-width": 1.6 },
            });
            map.addLayer({
                id: "state-hover",
                type: "line",
                source: "states",
                paint: { "line-color": palette.highlight, "line-width": 1.4, "line-opacity": ["case", hovered, 1, 0] as never },
            });
            map.addLayer({
                id: "city-hover",
                type: "line",
                source: "cities",
                paint: {
                    "line-color": palette.highlight,
                    "line-width": ["case", selected, 2, 1.2] as never,
                    "line-opacity": ["case", ["any", hovered, selected], 1, 0] as never,
                },
            });
            map.addLayer({
                id: "capitals",
                type: "circle",
                source: "capitals",
                paint: {
                    "circle-radius": ["interpolate", ["linear"], ["zoom"], 3, 2.5, 8, 4] as never,
                    "circle-color": palette.capital,
                    "circle-stroke-color": palette.capitalRing,
                    "circle-stroke-width": 1.5,
                },
            });

            // Siglas dos estados (HTML leve, so 27 elementos).
            stateLabelsRef.current = country.states.map((s) =>
                new Marker({ element: labelElement("map-label-state", s.properties.sigla) }).setLngLat(states.byUf.get(s.properties.sigla)!.center).addTo(map),
            );

            setReady(true);
        });

        // Hover e clique: uma consulta por evento, sem re-renderizar o React a cada movimento.
        const visible = (id: string) => !!map.getLayer(id) && map.getLayoutProperty(id, "visibility") !== "none";
        const pick = (e: MapMouseEvent): Hover => {
            // Pontos sao pequenos: procura numa area de 10px em volta do cursor.
            if (visible("points")) {
                const { x, y } = e.point;
                const [dot] = map.queryRenderedFeatures(
                    [
                        [x - 5, y - 5],
                        [x + 5, y + 5],
                    ],
                    { layers: ["points"] },
                );
                if (dot) return { kind: "point", id: Number(dot.id) };
            }
            const layers = ["cities-fill", "states-fill"].filter(visible);
            const [hit] = map.queryRenderedFeatures(e.point, { layers });
            if (!hit) return null;
            if (hit.layer.id === "cities-fill") return { kind: "city", id: Number(hit.id) };
            return { kind: "state", uf: String(hit.properties?.sigla) };
        };

        const setFeatureHover = (target: Hover, value: boolean) => {
            if (!target) return;
            if (target.kind === "point") {
                if (map.getSource("points")) map.setFeatureState({ source: "points", id: target.id }, { hover: value });
            } else if (target.kind === "city") map.setFeatureState({ source: "cities", id: target.id }, { hover: value });
            else {
                const id = states.byUf.get(target.uf)?.feature.properties.id;
                if (id != null) map.setFeatureState({ source: "states", id }, { hover: value });
            }
        };

        const keyOf = (h: Hover) => (h ? `${h.kind}:${h.kind === "state" ? h.uf : h.id}` : "");
        const applyHover = (target: Hover) => {
            const prev = hoverRef.current;
            const same = keyOf(prev) === keyOf(target);
            if (same) return;
            setFeatureHover(prev, false);
            setFeatureHover(target, true);
            hoverRef.current = target;
            setHover(target);
            map.getCanvas().style.cursor = target ? "pointer" : "";
        };

        map.on("mousemove", (e) => {
            const v = viewRef.current;
            let target = pick(e);
            // Com um estado aberto, o proprio estado nao recebe hover (os municipios recebem).
            if (target?.kind === "state" && v.level !== "country" && target.uf === v.uf) target = null;
            applyHover(target);
            const tip = tooltipRef.current;
            if (tip) {
                const flip = e.point.x > container.clientWidth - 220;
                tip.style.transform = `translate(${e.point.x + (flip ? -14 : 14)}px, ${e.point.y + 14}px) translateX(${flip ? "-100%" : "0"})`;
            }
        });
        map.on("mouseout", () => applyHover(null));

        map.on("click", (e) => {
            cancelLocation();
            mapClickRef.current?.([e.lngLat.lng, e.lngLat.lat]);
            const target = pick(e);
            const v = viewRef.current;
            if (!target) return;
            if (target.kind === "point") {
                pointClickRef.current?.(target.id);
                return;
            }
            if (target.kind === "city" && v.level !== "country") setView({ level: "city", uf: v.uf, cityId: target.id });
            else if (target.kind === "state" && (v.level === "country" || target.uf !== v.uf)) setView({ level: "state", uf: target.uf });
        });

        return () => {
            locationRequestRef.current += 1;
            resize.disconnect();
            stateLabelsRef.current.forEach((m) => m.remove());
            cityLabelsRef.current.forEach((m) => m.remove());
            map.remove();
            mapRef.current = null;
            setReady(false);
        };
        // A criacao depende so dos dados do pais; textos e tema sao aplicados nos efeitos abaixo.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [country, states, variant]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready || !selectedLocation) {
            selectedMarkerRef.current?.remove();
            selectedMarkerRef.current = null;
            return;
        }

        const element = document.createElement("div");
        element.className = "size-4 rounded-full border-2 border-white bg-brand-solid shadow-lg ring-4 ring-brand-solid/30";
        element.setAttribute("role", "img");
        element.setAttribute("aria-label", t("weather.selectedMarker"));

        const marker = new Marker({ element, anchor: "center" }).setLngLat(selectedLocation).addTo(map);
        selectedMarkerRef.current = marker;
        return () => {
            marker.remove();
            if (selectedMarkerRef.current === marker) selectedMarkerRef.current = null;
        };
    }, [ready, selectedLocation?.[0], selectedLocation?.[1], t]);

    // ---------- Cores: tema do app (claro/escuro) + mapa de fundo + estado aberto ----------

    const [dark, setDark] = useState(isDark);
    useEffect(() => {
        const observer = new MutationObserver(() => setDark(isDark()));
        observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
        return () => observer.disconnect();
    }, []);

    const basemapDefinition = basemapById(basemap);
    const tone = basemapDefinition.tone === "theme" ? (dark ? "dark" : "light") : basemapDefinition.tone;
    const monitoringDark = basemap === "monitoring" && dark;
    const naturalBasemap = variant === "full" && hasBasemap && basemap !== "monitoring";
    const palette =
        tone === "app"
            ? { ...(dark ? PALETTE.dark : PALETTE.light), dim: "", pointRing: dark ? "#0a0a0a" : "#ffffff" }
            : basemap === "monitoring"
              ? {
                    ...OVER_BASEMAP[tone],
                    border: dark ? "rgba(180,202,216,0.28)" : "rgba(53,76,90,0.25)",
                    outline: dark ? "rgba(180,202,216,0.5)" : "rgba(53,76,90,0.45)",
                    dim: dark ? "rgba(15,23,30,0.28)" : "rgba(245,247,242,0.4)",
                }
              : OVER_BASEMAP[tone];

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map) return;
        const p = naturalBasemap
            ? {
                  ...palette,
                  landHover: tone === "dark" ? "rgba(255,255,255,0.06)" : "rgba(10,13,18,0.04)",
                  border: tone === "dark" ? "rgba(255,255,255,0.28)" : "rgba(10,13,18,0.24)",
                  outline: tone === "dark" ? "rgba(255,255,255,0.55)" : "rgba(10,13,18,0.45)",
                  cityLine: tone === "dark" ? "rgba(255,255,255,0.16)" : "rgba(10,13,18,0.12)",
              }
            : palette;
        const hovered: ExpressionSpecification = ["boolean", ["feature-state", "hover"], false];
        const selected: ExpressionSpecification = ["boolean", ["feature-state", "selected"], false];
        const isActive: ExpressionSpecification = ["==", ["get", "sigla"], activeUf ?? ""];

        // Keep administrative geometry clickable while preserving the base imagery.
        const stateColor: ExpressionSpecification = ["case", hovered, p.landHover, p.land];
        map.setPaintProperty("states-fill", "fill-color", hasBasemap && activeUf && !naturalBasemap ? ["case", isActive, stateColor, p.dim] : stateColor);
        map.setPaintProperty("states-fill", "fill-opacity", !hasBasemap && activeUf ? ["case", isActive, 1, 0.4] : 1);
        map.setPaintProperty("cities-fill", "fill-color", p.accent);
        // Por cima do satelite a "mancha" por populacao atrapalha: so o municipio sob o cursor/selecionado.
        const weight: ExpressionSpecification = ["get", "w"];
        map.setPaintProperty(
            "cities-fill",
            "fill-opacity",
            hasBasemap
                ? [
                      "case",
                      selected,
                      naturalBasemap ? 0.12 : basemap === "monitoring" ? 0.14 : 0.45,
                      hovered,
                      naturalBasemap ? 0.05 : basemap === "monitoring" ? 0.08 : 0.22,
                      0,
                  ]
                : ["case", selected, 0.85, hovered, ["+", 0.22, ["*", 0.4, weight]], ["+", 0.03, ["*", 0.34, weight]]],
        );
        map.setPaintProperty("city-borders", "line-color", p.cityLine);
        map.setPaintProperty("state-borders", "line-color", p.border);
        map.setPaintProperty("outline", "line-color", p.outline);
        map.setPaintProperty("state-active", "line-color", p.accent);
        map.setPaintProperty("state-hover", "line-color", p.highlight);
        map.setPaintProperty("city-hover", "line-color", p.highlight);
        map.setPaintProperty("capitals", "circle-color", p.capital);
        map.setPaintProperty("capitals", "circle-stroke-color", p.capitalRing);
        if (map.getLayer("points")) map.setPaintProperty("points", "circle-stroke-color", p.pointRing);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ready, dark, basemap, activeUf, variant]);

    // ---------- Mapa de fundo: troca apenas as camadas de base e preserva os dados ----------

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map) return;
        for (const layer of map.getStyle().layers) {
            if (layer.id.startsWith("monitoring-")) map.removeLayer(layer.id);
        }
        for (let i = 0; i < 4; i++) {
            if (map.getLayer(`basemap-${i}`)) map.removeLayer(`basemap-${i}`);
            if (map.getSource(`basemap-${i}`)) map.removeSource(`basemap-${i}`);
        }
        if (basemap === "monitoring") {
            if (!map.getSource(VECTOR_SOURCE_ID)) {
                map.addSource(VECTOR_SOURCE_ID, { type: "vector", url: RIVERS_SOURCE, attribution: VECTOR_ATTRIBUTION });
            }
            monitoringLayers(monitoringDark).forEach((layer) => map.addLayer(layer, "states-fill"));
            return;
        }
        basemapById(basemap).layers.forEach((layer, i) => {
            map.addSource(`basemap-${i}`, {
                type: "raster",
                tiles: layer.tiles,
                tileSize: 256,
                maxzoom: layer.maxzoom ?? 19,
                attribution: basemapById(basemap).attribution,
            });
            map.addLayer({ id: `basemap-${i}`, type: "raster", source: `basemap-${i}`, paint: { "raster-fade-duration": 150 } }, "states-fill");
        });
    }, [ready, basemap, monitoringDark]);

    // ---------- Rios (vetores do OpenStreetMap via OpenFreeMap) ----------

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map) return;
        if (rivers && !map.getSource(VECTOR_SOURCE_ID)) {
            map.addSource(VECTOR_SOURCE_ID, { type: "vector", url: RIVERS_SOURCE, attribution: VECTOR_ATTRIBUTION });
        }
        if (rivers && !map.getLayer("rivers-water")) {
            const water = RIVER_COLOR;
            map.addLayer(
                {
                    id: "rivers-water",
                    type: "fill",
                    source: VECTOR_SOURCE_ID,
                    "source-layer": "water",
                    minzoom: 4,
                    filter: ["match", ["get", "class"], ["lake", "river", "pond"], true, false],
                    paint: { "fill-color": water, "fill-opacity": 0.35 },
                },
                "city-borders",
            );
            map.addLayer(
                {
                    id: "rivers-major",
                    type: "line",
                    source: VECTOR_SOURCE_ID,
                    "source-layer": "waterway",
                    filter: ["==", ["get", "class"], "river"],
                    layout: { "line-join": "round", "line-cap": "round" },
                    paint: { "line-color": water, "line-opacity": 0.9, "line-width": ["interpolate", ["linear"], ["zoom"], 3, 0.5, 6, 1, 9, 1.8, 13, 3] },
                },
                "city-borders",
            );
            map.addLayer(
                {
                    id: "rivers-minor",
                    type: "line",
                    source: VECTOR_SOURCE_ID,
                    "source-layer": "waterway",
                    minzoom: 8,
                    filter: ["match", ["get", "class"], ["canal", "stream"], true, false],
                    paint: { "line-color": water, "line-opacity": 0.6, "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.4, 13, 1.4] },
                },
                "city-borders",
            );
        }
        for (const id of ["rivers-water", "rivers-major", "rivers-minor"]) {
            if (map.getLayer(id)) map.setLayoutProperty(id, "visibility", rivers ? "visible" : "none");
        }
    }, [ready, rivers]);

    // ---------- Rios principais da ANA (GeoJSON do backend) ----------
    // Aparecem desde a visao do pais; ao aproximar somem aos poucos e ficam os do OpenStreetMap, mais detalhados.

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map || !riverLines) return;
        const source = map.getSource("ana-rivers") as GeoJSONSource | undefined;
        if (source) source.setData(riverLines);
        else {
            map.addSource("ana-rivers", { type: "geojson", data: riverLines, tolerance: 0.5, attribution: ANA_RIVERS_ATTRIBUTION });
            map.addLayer(
                {
                    id: "ana-rivers",
                    type: "line",
                    source: "ana-rivers",
                    maxzoom: 9,
                    layout: { "line-join": "round", "line-cap": "round" },
                    // Na visao do pais so os rios longos; os menores entram aos poucos ao aproximar (km vem do backend).
                    paint: {
                        "line-color": RIVER_COLOR,
                        "line-opacity": [
                            "interpolate",
                            ["linear"],
                            ["zoom"],
                            3,
                            ["case", [">=", ["get", "km"], 400], 0.9, 0],
                            4.5,
                            ["case", [">=", ["get", "km"], 150], 0.85, 0],
                            6,
                            ["case", [">=", ["get", "km"], 50], 0.8, 0.35],
                            7.5,
                            0.75,
                            9,
                            0,
                        ],
                        "line-width": [
                            "interpolate",
                            ["linear"],
                            ["zoom"],
                            3,
                            ["case", [">=", ["get", "km"], 1000], 1.3, 0.7],
                            6,
                            ["case", [">=", ["get", "km"], 1000], 2.2, 1.1],
                            9,
                            ["case", [">=", ["get", "km"], 1000], 3, 1.6],
                        ],
                    },
                },
                "city-borders",
            );
        }
        map.setLayoutProperty("ana-rivers", "visibility", rivers ? "visible" : "none");
    }, [ready, riverLines, rivers]);

    // ---------- Pontos (estacoes do tema escolhido) ----------

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map) return;
        const source = map.getSource("points") as GeoJSONSource | undefined;
        if (!points) {
            if (map.getLayer("points")) map.setLayoutProperty("points", "visibility", "none");
            return;
        }
        if (!source) {
            map.addSource("points", { type: "geojson", data: points.data, promoteId: "id" });
            const hovered: ExpressionSpecification = ["boolean", ["feature-state", "hover"], false];
            map.addLayer(
                {
                    id: "points",
                    type: "circle",
                    source: "points",
                    layout: { "circle-sort-key": ["get", "s"] },
                    paint: {
                        "circle-radius": [
                            "interpolate",
                            ["linear"],
                            ["zoom"],
                            3,
                            ["case", hovered, 6, 2.6],
                            7,
                            ["case", hovered, 8, 4.5],
                            12,
                            ["case", hovered, 10, 7],
                        ],
                        "circle-opacity": ["case", ["==", ["get", "a"], 0], 0.35, 0.95],
                        "circle-stroke-width": ["interpolate", ["linear"], ["zoom"], 3, 0.6, 10, 1.2],
                        "circle-stroke-color": palette.pointRing,
                        "circle-stroke-opacity": ["case", ["==", ["get", "a"], 0], 0.4, 1],
                    },
                },
                "capitals",
            );
        } else {
            source.setData(points.data);
        }
        // Com dezenas de milhares de pontos (HidroWeb) eles ficam menores na visao do pais para nao virar uma mancha.
        const dense = points.data.features.length > 8000;
        const hover: ExpressionSpecification = ["boolean", ["feature-state", "hover"], false];
        map.setPaintProperty("points", "circle-radius", [
            "interpolate",
            ["linear"],
            ["zoom"],
            3,
            ["case", hover, 6, dense ? 1.6 : 2.6],
            7,
            ["case", hover, 8, dense ? 3.2 : 4.5],
            12,
            ["case", hover, 10, 7],
        ]);
        map.setPaintProperty("points", "circle-stroke-width", ["interpolate", ["linear"], ["zoom"], 3, dense ? 0.3 : 0.6, 10, 1.2]);
        const pairs = Object.entries(points.colors).flat();
        map.setPaintProperty(
            "points",
            "circle-color",
            pairs.length ? (["match", ["get", "k"], ...pairs, "#a3a3a3"] as unknown as ExpressionSpecification) : "#a3a3a3",
        );
        map.setFilter("points", points.hidden?.length ? ["!", ["in", ["get", "k"], ["literal", points.hidden]]] : null);
        map.setLayoutProperty("points", "visibility", "visible");
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ready, points]);

    // ---------- Balao de detalhes ----------

    const [popupNode, setPopupNode] = useState<HTMLElement | null>(null);
    const popupKey = popup?.key;
    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map || !popup) return;
        const node = document.createElement("div");
        const instance = new Popup({ closeButton: false, maxWidth: "320px", offset: 12, className: "sentinela-popup", focusAfterOpen: false })
            .setLngLat(popup.lngLat)
            .setDOMContent(node)
            .addTo(map);
        const onClose = popup.onClose;
        instance.on("close", onClose);
        setPopupNode(node);
        return () => {
            instance.off("close", onClose);
            instance.remove();
            setPopupNode(null);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ready, popupKey]);

    useImperativeHandle(
        controllerRef,
        () => ({
            flyTo: (lngLat, zoom = 10) => {
                cancelLocation();
                mapRef.current?.flyTo({ center: lngLat, zoom, duration: prefersReducedMotion() ? 0 : variant === "full" ? 700 : 1100, essential: true });
            },
            openState: (uf) => {
                cancelLocation();
                setView({ level: "state", uf });
            },
        }),
        [variant, cancelLocation],
    );

    // ---------- Estado aberto: filtros, municipios e rotulos ----------

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map) return;

        map.setFilter("state-active", ["==", ["get", "sigla"], activeUf ?? ""]);
        map.setFilter("capitals", activeUf ? ["==", ["get", "sigla"], activeUf] : null);
        // O MapLibre grava opacity inline no elemento do marcador; por isso o "esconder" vai no filho.
        stateLabelsRef.current.forEach((m) => m.getElement().firstElementChild?.classList.toggle("is-hidden", !!activeUf));

        const showCities = !!cities && !!activeUf;
        (map.getSource("cities") as GeoJSONSource).setData(showCities ? cities.collection : EMPTY);
        (map.getSource("city-borders") as GeoJSONSource).setData(showCities ? { type: "Feature", properties: {}, geometry: cities.borders } : EMPTY);
    }, [ready, activeUf, cities]);

    // Rotulos de municipios: capital + mais populosos + o selecionado.
    useEffect(() => {
        const map = mapRef.current;
        cityLabelsRef.current.forEach((m) => m.remove());
        cityLabelsRef.current = [];
        if (!ready || !map || !cities || !activeUf || basemap === "monitoring") return;

        const all = [...cities.byId.values()];
        const top = all.sort((a, b) => (b.feature.properties.populacao ?? 0) - (a.feature.properties.populacao ?? 0)).slice(0, variant === "full" ? 8 : 5);
        const capital = all.find((c) => c.feature.properties.capital);
        const labels = new Set([...(capital ? [capital] : []), ...top, ...(activeCity ? [activeCity] : [])]);

        cityLabelsRef.current = [...labels].map((c) =>
            new Marker({
                element: labelElement(cx("map-label-city", activeCity === c && "is-active"), c.feature.properties.nome),
                offset: [0, c.feature.properties.capital ? -12 : 0],
            })
                .setLngLat(c.center)
                .addTo(map),
        );
    }, [ready, cities, activeUf, activeCity, variant, basemap]);

    // Municipio selecionado (contorno forte).
    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map || !activeCity) return;
        const id = activeCity.feature.properties.id;
        map.setFeatureState({ source: "cities", id }, { selected: true });
        return () => {
            if (map.getSource("cities")) map.setFeatureState({ source: "cities", id }, { selected: false });
        };
    }, [ready, activeCity]);

    // ---------- Camera ----------

    // Leave room for the shared information/legend panel when fitting an area.
    const padding = useCallback(() => {
        const el = containerRef.current;
        const side = Math.min(32, Math.min(el?.clientWidth ?? 600, el?.clientHeight ?? 600) * 0.06);
        return { top: side, bottom: side, left: variant === "full" && (el?.clientWidth ?? 0) >= 900 ? 360 : side, right: side };
    }, [variant]);

    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map || !states) return;
        const duration = prefersReducedMotion() ? 0 : variant === "full" ? 450 : 1100;
        const target = activeCity ? activeCity.bbox : activeState ? activeState.bbox : states.bbox;
        map.fitBounds(target, { padding: padding(), duration, maxZoom: activeCity ? 10 : 9, essential: true });
    }, [ready, states, activeState, activeCity, padding, variant]);

    // Zoom minimo = o que mostra o Brasil inteiro, e o arrasto fica preso a essa area.
    // Recalcula quando o painel muda de tamanho (o enquadramento depende da proporcao).
    useEffect(() => {
        const map = mapRef.current;
        if (!ready || !map || !states) return;
        let boundsInitialized = false;
        const fitLimits = () => {
            const camera = map.cameraForBounds(states.bbox, { padding: padding() });
            if (!camera || camera.zoom == null) return;
            const atCountry = viewRef.current.level === "country";
            const retainCamera = variant === "full" && boundsInitialized;
            const saved = { center: map.getCenter(), zoom: map.getZoom() };

            map.setMaxBounds(null);
            map.setMinZoom(camera.zoom);
            map.jumpTo(camera);
            const visible = map.getBounds();
            const dx = (visible.getEast() - visible.getWest()) * 0.01;
            const dy = (visible.getNorth() - visible.getSouth()) * 0.01;
            map.setMaxBounds([
                [visible.getWest() - dx, visible.getSouth() - dy],
                [visible.getEast() + dx, visible.getNorth() + dy],
            ]);
            // Preserve manual navigation when the full map changes size.
            if (retainCamera || !atCountry) map.jumpTo({ center: saved.center, zoom: Math.max(saved.zoom, camera.zoom) });
            boundsInitialized = true;
        };
        fitLimits();
        map.on("resize", fitLimits);
        return () => {
            map.off("resize", fitLimits);
        };
    }, [ready, states, padding, variant]);

    // ---------- Navegacao ----------

    const goTo = useCallback(
        (next: View) => {
            cancelLocation();
            setView(next);
        },
        [cancelLocation],
    );
    const goUp = useCallback(() => {
        cancelLocation();
        setView((v) => (v.level === "city" ? { level: "state", uf: v.uf } : { level: "country" }));
    }, [cancelLocation]);

    const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === "Escape" && view.level !== "country") {
            event.preventDefault();
            goUp();
        }
    };

    const zoomBy = (delta: number) => mapRef.current?.easeTo({ zoom: mapRef.current.getZoom() + delta, duration: 300 });

    // Volta para o Brasil inteiro (tambem quando ja esta na visao do pais, mas com zoom/arrasto manual).
    const resetView = () => {
        cancelLocation();
        setView({ level: "country" });
        if (states) mapRef.current?.fitBounds(states.bbox, { padding: padding(), duration: prefersReducedMotion() ? 0 : variant === "full" ? 450 : 900 });
    };

    const locate = () => {
        setLocationError(null);
        const requestId = ++locationRequestRef.current;
        if (!navigator.geolocation) {
            setLocationError("weather.geolocationUnsupported");
            return;
        }
        setLocationBusy(true);
        navigator.geolocation.getCurrentPosition(
            ({ coords }) => {
                const map = mapRef.current;
                if (!map || requestId !== locationRequestRef.current) return;
                const coordinates: [number, number] = [coords.longitude, coords.latitude];
                mapClickRef.current?.(coordinates);
                map.flyTo({ center: coordinates, zoom: 16, duration: prefersReducedMotion() ? 0 : 700, essential: true });
                setLocationBusy(false);
            },
            (error) => {
                if (!mapRef.current || requestId !== locationRequestRef.current) return;
                setLocationBusy(false);
                setLocationError(error.code === 1 ? "weather.locationDenied" : error.code === 3 ? "weather.locationTimeout" : "weather.locationUnavailable");
            },
            { enableHighAccuracy: true, timeout: 12_000, maximumAge: 60_000 },
        );
    };

    useEffect(() => {
        onScopeChange?.(activeUf);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeUf]);

    const hoveredState = hover?.kind === "state" ? states?.byUf.get(hover.uf) : undefined;
    const hoveredCity = hover?.kind === "city" ? cities?.byId.get(hover.id) : undefined;

    const totals = useMemo(() => {
        if (!country) return null;
        return country.states.reduce((acc, s) => ({ cities: acc.cities + s.properties.municipios, people: acc.people + (s.properties.populacao ?? 0) }), {
            cities: 0,
            people: 0,
        });
    }, [country]);

    const loadingCities = !!activeUf && !cities;

    // Barra de informacoes: os numeros acompanham o que esta selecionado (Brasil, estado ou municipio).
    const facts: FactItem[] = (() => {
        if (activeCity) {
            const p = activeCity.feature.properties;
            return [
                { label: t("map.population"), value: p.populacao != null ? fmt.plain(p.populacao) : "—" },
                { label: t("map.meso"), value: p.meso ?? "—", text: true },
                { label: t("map.micro"), value: p.micro ?? "—", text: true },
                { label: t("map.ibgeCode"), value: String(p.id) },
            ];
        }
        if (activeState) {
            const p = activeState.feature.properties;
            return [
                { label: t("map.capital"), value: p.capital ?? "—", text: true },
                { label: t("map.region"), value: t(`map.regions.${p.regiao}`), text: true },
                { label: t("map.statsCities"), value: fmt.plain(p.municipios) },
                { label: t("map.population"), value: p.populacao != null ? fmt.plain(p.populacao) : "—" },
            ];
        }
        if (!country || !totals) return [];
        return [
            { label: t("map.statsStates"), value: fmt.plain(country.states.length) },
            { label: t("map.statsCities"), value: fmt.plain(totals.cities) },
            { label: t("map.statsPopulation"), value: fmt.compact(totals.people) },
        ];
    })();

    const hint = loadingCities ? t("common.loading") : view.level === "country" ? t("map.hintCountry") : view.level === "state" ? t("map.hintState") : null;

    return (
        <div lang={i18n.language} className={cx("sentinela-map flex min-h-0 flex-col overflow-hidden rounded-2xl bg-primary ring-1 ring-secondary", className)}>
            {/* 1. Barra de ferramentas: fora da area do mapa, nao cobre o desenho */}
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-secondary px-3 py-2.5 sm:px-4">
                <Breadcrumb
                    country={t("map.brazil")}
                    state={activeState?.feature.properties.nome}
                    city={activeCity?.feature.properties.nome}
                    badge={activeCity?.feature.properties.capital ? t("map.capital") : undefined}
                    onCountry={() => goTo({ level: "country" })}
                    onState={() => activeUf && goTo({ level: "state", uf: activeUf })}
                    onBack={view.level !== "country" ? goUp : undefined}
                />
                {hint && <p className="hidden truncate text-xs text-quaternary md:block">{hint}</p>}
                <div className="flex w-full min-w-0 flex-wrap items-center gap-2 sm:ml-auto sm:w-auto">
                    {toolbarExtra}
                    {ready && <CitySearch className="w-full min-w-0 sm:w-60" onSelect={(id, uf) => goTo({ level: "city", uf, cityId: id })} />}
                    {variant === "full" && (
                        <Button
                            size="sm"
                            color="secondary"
                            iconLeading={MarkerPin01}
                            onClick={locate}
                            isLoading={locationBusy}
                            isDisabled={!ready || loadingCities}
                        >
                            {t("weather.currentLocation")}
                        </Button>
                    )}
                </div>
                {locationError && (
                    <FormAlert>
                        {t(locationError)}
                    </FormAlert>
                )}
            </div>

            {/* 2. Area do mapa: so o desenho e os botoes de zoom no canto */}
            <div
                onKeyDown={onKeyDown}
                className={cx("relative isolate overflow-hidden", mapClassName ?? "min-h-0 flex-1", tone !== "app" && `map-tone-${tone}`)}
            >
                <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 bg-dots mask-radial-from-40% mask-radial-to-90%" />

                {/* Canvas WebGL. A classe do container e fixa: o MapLibre adiciona as dele e o React nao pode sobrescrever. */}
                <div className={cx("absolute inset-0 transition-opacity duration-700", ready ? "opacity-100" : "opacity-0")}>
                    <div ref={containerRef} className="size-full" />
                </div>

                {failed && !ready ? (
                    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-center">
                        <FormAlert>{t("map.error")}</FormAlert>
                        <p className="text-sm text-tertiary">{t("map.error")}</p>
                        <Button size="sm" color="secondary" iconLeading={RefreshCw01} onClick={fetchCountry}>
                            {t("map.retry")}
                        </Button>
                    </div>
                ) : (
                    !ready && (
                        <div className="absolute inset-0 flex items-center justify-center">
                            <LoadingIndicator type="line-spinner" size="sm" label={t("map.loading")} />
                        </div>
                    )
                )}

                <div
                    ref={tooltipRef}
                    aria-hidden="true"
                    className={cx("pointer-events-none absolute top-0 left-0 z-30 transition-opacity duration-100", hover ? "opacity-100" : "opacity-0")}
                >
                    {hoveredState && (
                        <TooltipCard
                            title={hoveredState.feature.properties.nome}
                            lines={[
                                t(`map.regions.${hoveredState.feature.properties.regiao}`),
                                t("map.municipalities", { count: hoveredState.feature.properties.municipios }),
                                hoveredState.feature.properties.populacao != null
                                    ? t("map.inhabitants", { value: fmt.compact(hoveredState.feature.properties.populacao) })
                                    : null,
                            ]}
                        />
                    )}
                    {hoveredCity && (
                        <TooltipCard
                            title={hoveredCity.feature.properties.nome}
                            lines={[
                                hoveredCity.feature.properties.capital ? t("map.capital") : hoveredCity.feature.properties.meso,
                                hoveredCity.feature.properties.populacao != null
                                    ? t("map.inhabitants", { value: fmt.plain(hoveredCity.feature.properties.populacao) })
                                    : null,
                            ]}
                        />
                    )}
                    {hover?.kind === "point" && renderPointTooltip?.(hover.id)}
                </div>

                {/* Coluna de controles no canto direito: fina, fora do centro do mapa */}
                {ready && (
                    <div className="absolute top-3 right-3 z-20 flex flex-col items-end gap-2">
                        <div className="flex flex-col overflow-hidden rounded-lg bg-primary shadow-xs ring-1 ring-secondary">
                            <MapButton label={t("map.zoomIn")} onClick={() => zoomBy(1)} icon={Plus} />
                            <span className="h-px bg-border-secondary" />
                            <MapButton label={t("map.zoomOut")} onClick={() => zoomBy(-1)} icon={Minus} />
                            <span className="h-px bg-border-secondary" />
                            <MapButton label={t("map.resetView")} onClick={resetView} icon={Home03} />
                        </div>
                        {controls}
                    </div>
                )}

                {ready && overlay}

                {popupNode && popup && createPortal(popup.content, popupNode)}

                {/* Acesso por teclado/leitor de tela aos estados */}
                {states && (
                    <ul className="sr-only">
                        {[...states.byUf.values()].map((s) => (
                            <li key={s.feature.properties.sigla}>
                                <button type="button" onClick={() => goTo({ level: "state", uf: s.feature.properties.sigla })}>
                                    {s.feature.properties.nome}
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            {below}

            {/* 3. Barra de informacoes do que esta selecionado */}
            <div className="flex flex-wrap items-end gap-x-8 gap-y-3 border-t border-secondary px-4 py-3 sm:px-5">
                {facts.map((f) => (
                    <Fact key={f.label} {...f} />
                ))}
                {country?.populationYear && (
                    <p className="w-full text-[11px] text-quaternary sm:ml-auto sm:w-auto sm:pb-0.5">{t("map.source", { year: country.populationYear })}</p>
                )}
            </div>
        </div>
    );
};

// ---------- Subcomponentes ----------

interface FactItem {
    label: string;
    value: string;
    /** Texto (nome) em vez de numero: fonte menor e corte com reticencias. */
    text?: boolean;
}

const Fact = ({ label, value, text }: FactItem) => (
    <div className="min-w-0">
        <p className="text-[11px] font-medium tracking-wider text-quaternary uppercase">{label}</p>
        <p className={cx("mt-0.5 truncate font-semibold text-primary", text ? "max-w-48 text-sm leading-7" : "text-lg leading-7 tabular-nums")} title={value}>
            {value}
        </p>
    </div>
);

const MapButton = ({ label, onClick, icon: Icon }: { label: string; onClick: () => void; icon: typeof Plus }) => (
    <button
        type="button"
        aria-label={label}
        title={label}
        onClick={onClick}
        className="flex size-8 cursor-pointer items-center justify-center text-fg-quaternary transition duration-100 ease-linear hover:bg-primary_hover hover:text-fg-secondary focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus-ring"
    >
        <Icon className="size-4" />
    </button>
);

const Breadcrumb = ({
    country,
    state,
    city,
    badge,
    onCountry,
    onState,
    onBack,
}: {
    country: string;
    state?: string;
    city?: string;
    badge?: string;
    onCountry: () => void;
    onState: () => void;
    onBack?: () => void;
}) => {
    const { t } = useTranslation();
    const crumb = "cursor-pointer truncate rounded-md px-1.5 py-1 transition duration-100 ease-linear hover:bg-primary_hover hover:text-secondary";
    return (
        <nav aria-label="breadcrumb" className="flex min-w-0 items-center gap-0.5 text-sm font-medium text-tertiary">
            {onBack && (
                <button
                    type="button"
                    aria-label={t("map.back")}
                    title={t("map.back")}
                    onClick={onBack}
                    className="mr-1 flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-fg-quaternary ring-1 ring-secondary transition duration-100 ease-linear ring-inset hover:bg-primary_hover hover:text-fg-secondary"
                >
                    <ArrowLeft className="size-4" />
                </button>
            )}
            <button type="button" onClick={onCountry} className={cx(crumb, "shrink-0", !state && "text-primary")}>
                {country}
            </button>
            {state && (
                <>
                    <ChevronRight className="size-3.5 shrink-0 text-fg-quaternary" aria-hidden="true" />
                    <button type="button" onClick={onState} className={cx(crumb, "max-w-40", !city && "text-primary")}>
                        {state}
                    </button>
                </>
            )}
            {city && (
                <>
                    <ChevronRight className="size-3.5 shrink-0 text-fg-quaternary" aria-hidden="true" />
                    <span className="max-w-40 truncate px-1.5 text-primary">{city}</span>
                </>
            )}
            {badge && (
                <span className="ml-1 shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium text-secondary ring-1 ring-secondary ring-inset">{badge}</span>
            )}
        </nav>
    );
};

const TooltipCard = ({ title, lines }: { title: string; lines: (string | null)[] }) => (
    <div className="min-w-36 rounded-lg bg-primary px-3 py-2 shadow-lg ring-1 ring-secondary_alt">
        <p className="text-sm font-semibold text-primary">{title}</p>
        {lines.filter(Boolean).map((line) => (
            <p key={line} className="text-xs text-tertiary">
                {line}
            </p>
        ))}
    </div>
);
