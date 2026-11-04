import { FormAlert } from "@/components/app/form-alert";
import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Check, Map01, MarkerPin01 } from "@untitledui/icons";
import { geoBounds } from "d3-geo";
import type { FeatureCollection, Geometry } from "geojson";
import * as maplibregl from "maplibre-gl";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import "maplibre-gl/dist/maplibre-gl.css";
import { basemapById } from "@/components/app/brazil-map/basemaps";
import { CitySearch } from "@/components/app/brazil-map/city-search";
import { loadState } from "@/components/app/brazil-map/geo-data";
import { useMapPanelWheel } from "@/components/app/hydro/map-controls";
import { Button } from "@/components/base/buttons/button";
import { Select } from "@/components/base/select/select";
import type { PropertyCoordinate, PropertyImageOverlay, SavedProperty } from "@/lib/property-data";
import { cx } from "@/utils/cx";

maplibregl.setWorkerUrl(workerUrl);

const basemapOptions = [
    { id: "satellite", label: "Satélite" },
    { id: "streets", label: "Ruas" },
];

interface PropertyMapProps {
    vertices: PropertyCoordinate[];
    closed: boolean;
    editable: boolean;
    drawing: boolean;
    fitKey: string;
    overlay: PropertyImageOverlay | null;
    onVerticesChange: (vertices: PropertyCoordinate[]) => void;
    onFinish: () => void;
    onCitySelect: (city: string, state: string) => void;
    visibleProperties?: readonly SavedProperty[];
    toolbarExtra?: ReactNode;
    overlayPanel?: ReactNode;
    onPropertySelect?: (id: string) => void;
}

const propertyData = (properties: readonly SavedProperty[]): FeatureCollection<Geometry> => ({
    type: "FeatureCollection",
    features: properties.flatMap((property) => {
        const geometry: Geometry | null =
            property.geometry ??
            (Number.isFinite(property.longitude) && Number.isFinite(property.latitude)
                ? { type: "Point", coordinates: [property.longitude!, property.latitude!] }
                : null);
        return geometry ? [{ type: "Feature" as const, properties: { propertyId: property.propertyId, name: property.name }, geometry }] : [];
    }),
});

const propertyPoints = (properties: readonly SavedProperty[]): PropertyCoordinate[] =>
    properties.flatMap(
        (property) =>
            property.geometry?.coordinates.flat() ??
            (Number.isFinite(property.longitude) && Number.isFinite(property.latitude)
                ? [[property.longitude!, property.latitude!] as PropertyCoordinate]
                : []),
    );

const geometryData = (vertices: PropertyCoordinate[], closed: boolean): FeatureCollection<Geometry> => {
    const features: FeatureCollection<Geometry>["features"] = [];
    if (vertices.length > 1) {
        features.push({
            type: "Feature",
            properties: { kind: "boundary" },
            geometry: { type: "LineString", coordinates: closed ? [...vertices, vertices[0]] : vertices },
        });
    }
    if (closed && vertices.length > 2) {
        features.push({ type: "Feature", properties: { kind: "area" }, geometry: { type: "Polygon", coordinates: [[...vertices, vertices[0]]] } });
    }
    return { type: "FeatureCollection", features };
};

export const PropertyMap = (props: PropertyMapProps) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const mapRef = useRef<maplibregl.Map | null>(null);
    const latest = useRef(props);
    latest.current = props;
    const [ready, setReady] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [locationBusy, setLocationBusy] = useState(false);
    const [base, setBase] = useState<"satellite" | "streets">("satellite");
    const [showNdvi, setShowNdvi] = useState(true);
    const panelWheelRef = useMapPanelWheel<HTMLDivElement>();

    useEffect(() => {
        if (!containerRef.current) return;
        let map: maplibregl.Map;
        try {
            const satellite = basemapById("satellite");
            map = new maplibregl.Map({
                container: containerRef.current,
                center: [-51.9, -15.8],
                zoom: 3.5,
                minZoom: 2,
                maxZoom: 19,
                renderWorldCopies: false,
                attributionControl: false,
                dragRotate: false,
                touchPitch: false,
                pitchWithRotate: false,
                style: {
                    version: 8,
                    sources: {
                        "property-basemap": {
                            type: "raster",
                            tiles: satellite.layers[0].tiles,
                            tileSize: 256,
                            maxzoom: 19,
                            attribution: satellite.attribution,
                        },
                        "property-outline": { type: "geojson", data: geometryData(latest.current.vertices, latest.current.closed) },
                        properties: { type: "geojson", data: propertyData(latest.current.visibleProperties ?? []) },
                    },
                    layers: [
                        { id: "property-basemap", type: "raster", source: "property-basemap" },
                        {
                            id: "properties-fill",
                            type: "fill",
                            source: "properties",
                            filter: ["==", ["geometry-type"], "Polygon"],
                            paint: { "fill-color": "#2dd4bf", "fill-opacity": 0.15 },
                        },
                        {
                            id: "properties-line-shadow",
                            type: "line",
                            source: "properties",
                            filter: ["==", ["geometry-type"], "Polygon"],
                            paint: { "line-color": "#0f172a", "line-width": 5, "line-opacity": 0.7 },
                        },
                        {
                            id: "properties-line",
                            type: "line",
                            source: "properties",
                            filter: ["==", ["geometry-type"], "Polygon"],
                            paint: { "line-color": "#5eead4", "line-width": 2.5 },
                        },
                        {
                            id: "properties-point",
                            type: "circle",
                            source: "properties",
                            filter: ["==", ["geometry-type"], "Point"],
                            paint: { "circle-color": "#2dd4bf", "circle-radius": 6, "circle-stroke-color": "#fff", "circle-stroke-width": 2 },
                        },
                        {
                            id: "property-fill",
                            type: "fill",
                            source: "property-outline",
                            filter: ["==", ["get", "kind"], "area"],
                            paint: { "fill-color": "#38bdf8", "fill-opacity": 0.12 },
                        },
                        {
                            id: "property-line-shadow",
                            type: "line",
                            source: "property-outline",
                            filter: ["==", ["get", "kind"], "boundary"],
                            paint: { "line-color": "#0f172a", "line-width": 5, "line-opacity": 0.7 },
                        },
                        {
                            id: "property-line",
                            type: "line",
                            source: "property-outline",
                            filter: ["==", ["get", "kind"], "boundary"],
                            paint: { "line-color": "#e0f2fe", "line-width": 2.5 },
                        },
                    ],
                },
            });
        } catch {
            setError("O mapa não pôde ser iniciado. Verifique se a aceleração gráfica está disponível no navegador.");
            return;
        }
        mapRef.current = map;
        map.doubleClickZoom.disable();
        map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "bottom-right");
        map.addControl(new maplibregl.AttributionControl({ compact: true }), "bottom-right");
        map.addControl(new maplibregl.ScaleControl({ unit: "metric" }), "bottom-left");
        map.on("load", () => setReady(true));
        map.on("click", (event) => {
            const current = latest.current;
            if (!current.editable) {
                if (!map.getLayer("properties-fill")) return;
                const feature = map.queryRenderedFeatures(event.point, { layers: ["properties-fill", "properties-line", "properties-point"] })[0];
                const id = feature?.properties?.propertyId;
                if (typeof id === "string") current.onPropertySelect?.(id);
                return;
            }
            if (!current.editable || !current.drawing || current.vertices.length >= 200 || event.originalEvent.detail > 1) return;
            current.onVerticesChange([...current.vertices, [event.lngLat.lng, event.lngLat.lat]]);
        });
        map.on("mousemove", (event) => {
            if (latest.current.editable || !map.getLayer("properties-fill")) return;
            const hovered = map.queryRenderedFeatures(event.point, { layers: ["properties-fill", "properties-line", "properties-point"] }).length > 0;
            map.getCanvas().style.cursor = hovered ? "pointer" : "";
        });
        const resize = new ResizeObserver(() => map.resize());
        resize.observe(containerRef.current);
        return () => {
            resize.disconnect();
            mapRef.current = null;
            map.remove();
        };
    }, []);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready) return;
        (map.getSource("property-outline") as maplibregl.GeoJSONSource).setData(geometryData(props.vertices, props.closed));
        map.getCanvas().style.cursor = props.editable && props.drawing ? "crosshair" : "";
    }, [props.vertices, props.closed, props.editable, props.drawing, ready]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready) return;
        (map.getSource("properties") as maplibregl.GeoJSONSource).setData(propertyData(props.visibleProperties ?? []));
    }, [props.visibleProperties, ready]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready) return;
        const current = latest.current;
        const points = current.vertices.length >= 2 ? current.vertices : propertyPoints(current.visibleProperties ?? []);
        if (!points.length) return;
        const bounds = new maplibregl.LngLatBounds(points[0], points[0]);
        points.slice(1).forEach((point) => bounds.extend(point));
        const desktopPanel = !!current.overlayPanel && map.getContainer().clientWidth >= 900;
        map.fitBounds(bounds, { padding: { top: 60, right: 60, bottom: 80, left: desktopPanel ? 360 : 60 }, maxZoom: 17, duration: 450 });
    }, [props.fitKey, ready]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready || !props.editable) return;
        const markers = props.vertices.map((point, index) => {
            const element = document.createElement("button");
            element.type = "button";
            element.ariaLabel = index === 0 && props.drawing ? "Concluir área neste ponto" : `Vértice ${index + 1}; arraste para ajustar`;
            element.style.cssText = "width:18px;height:18px;border:3px solid white;border-radius:50%;background:#0284c7;box-shadow:0 1px 5px #0009;cursor:grab";
            element.addEventListener("click", (event) => {
                event.stopPropagation();
                if (index === 0 && latest.current.drawing && latest.current.vertices.length >= 3) latest.current.onFinish();
            });
            const marker = new maplibregl.Marker({ element, draggable: !props.drawing }).setLngLat(point).addTo(map);
            marker.on("dragend", () => {
                const position = marker.getLngLat();
                const next = [...latest.current.vertices];
                next[index] = [position.lng, position.lat];
                latest.current.onVerticesChange(next);
            });
            return marker;
        });
        return () => markers.forEach((marker) => marker.remove());
    }, [props.vertices, props.editable, props.drawing, ready]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready) return;
        const basemap = basemapById(base);
        map.removeLayer("property-basemap");
        map.removeSource("property-basemap");
        map.addSource("property-basemap", { type: "raster", tiles: basemap.layers[0].tiles, tileSize: 256, maxzoom: 19, attribution: basemap.attribution });
        map.addLayer({ id: "property-basemap", type: "raster", source: "property-basemap" }, "properties-fill");
    }, [base, ready]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !ready) return;
        if (map.getLayer("property-ndvi")) map.removeLayer("property-ndvi");
        if (map.getSource("property-ndvi")) map.removeSource("property-ndvi");
        if (props.overlay && showNdvi) {
            const [west, south, east, north] = props.overlay.bounds;
            map.addSource("property-ndvi", {
                type: "image",
                url: props.overlay.url,
                coordinates: [
                    [west, north],
                    [east, north],
                    [east, south],
                    [west, south],
                ],
            });
            map.addLayer(
                { id: "property-ndvi", type: "raster", source: "property-ndvi", paint: { "raster-opacity": 0.85, "raster-fade-duration": 0 } },
                "property-fill",
            );
            map.setPaintProperty("property-fill", "fill-opacity", 0);
        } else {
            map.setPaintProperty("property-fill", "fill-opacity", 0.12);
        }
    }, [props.overlay, ready, showNdvi]);

    const locate = () => {
        if (!navigator.geolocation) {
            setError("Este navegador não oferece localização. Busque uma cidade para encontrar sua propriedade.");
            return;
        }
        setLocationBusy(true);
        setError(null);
        navigator.geolocation.getCurrentPosition(
            ({ coords }) => {
                mapRef.current?.flyTo({ center: [coords.longitude, coords.latitude], zoom: 16, duration: 700 });
                setLocationBusy(false);
            },
            () => {
                setLocationBusy(false);
                setError("Não foi possível obter sua localização. Permita o acesso no navegador ou busque uma cidade.");
            },
            { enableHighAccuracy: true, timeout: 12_000, maximumAge: 60_000 },
        );
    };

    const findCity = async (id: number, state: string) => {
        setError(null);
        try {
            const data = await loadState(state);
            const city = data.cities.find((item) => item.properties.id === id);
            if (!city) throw new Error("City not found");
            const bounds = geoBounds(city);
            mapRef.current?.fitBounds(bounds, { padding: 45, maxZoom: 13, duration: 650 });
            latest.current.onCitySelect(city.properties.nome, state);
        } catch {
            setError("Não foi possível localizar essa cidade no mapa. Tente novamente ou navegue pelo mapa.");
        }
    };

    return (
        <section
            className="sentinela-map flex min-h-0 w-full flex-col overflow-hidden rounded-2xl bg-primary ring-1 ring-secondary"
            aria-label="Mapa das propriedades"
        >
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-secondary px-3 py-2.5 sm:px-4">
                <h2 className="text-sm font-semibold text-primary">Propriedades</h2>
                <div className="flex w-full min-w-0 flex-wrap items-center gap-2 sm:ml-auto sm:w-auto">
                    {props.toolbarExtra}
                    <CitySearch className="min-w-0 flex-1 sm:w-60 sm:flex-none" onSelect={(id, uf) => void findCity(id, uf)} />
                    <Button
                        size="sm"
                        color="secondary"
                        iconLeading={MarkerPin01}
                        onClick={locate}
                        isLoading={locationBusy}
                        aria-label="Ir para minha localização atual"
                    >
                        Localização atual
                    </Button>
                    {props.overlay && !props.editable && (
                        <Button size="sm" color="secondary" aria-pressed={showNdvi} onClick={() => setShowNdvi((visible) => !visible)}>
                            {showNdvi ? "Ocultar NDVI" : "Mostrar NDVI"}
                        </Button>
                    )}
                    <Select
                        size="sm"
                        aria-label="Mapa base"
                        icon={Map01}
                        items={basemapOptions}
                        selectedKey={base}
                        onSelectionChange={(key) => {
                            if (key === "satellite" || key === "streets") setBase(key);
                        }}
                        className="w-36 shrink-0"
                    >
                        {(item) => <Select.Item id={item.id} label={item.label} icon={Map01} />}
                    </Select>
                </div>
            </div>
            <div className="relative isolate h-[calc(100dvh-10rem)] min-h-[520px] overflow-hidden bg-secondary">
                <div className="absolute inset-0">
                    <div ref={containerRef} className="size-full" />
                </div>
                {props.overlayPanel && (
                    <div
                        className={cx(
                            "pointer-events-none absolute top-3 left-3 z-20 flex min-h-0 w-[min(20rem,calc(100%-4.5rem))] flex-col items-start",
                            props.editable || (props.overlay && showNdvi) ? "bottom-44 sm:bottom-28" : "bottom-12",
                        )}
                    >
                        <div
                            ref={panelWheelRef}
                            data-map-scroll-viewport
                            className="pointer-events-auto max-h-full min-h-0 w-full overflow-y-auto overscroll-contain rounded-xl shadow-lg"
                        >
                            {props.overlayPanel}
                        </div>
                    </div>
                )}
                {props.editable && (
                    <div
                        className={cx(
                            "absolute inset-x-3 bottom-12 z-10 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-secondary bg-primary/95 px-3 py-2 text-xs text-secondary shadow-md sm:inset-x-auto sm:max-w-lg",
                            props.overlayPanel ? "sm:right-14" : "sm:left-3",
                        )}
                    >
                        <p>
                            {props.drawing
                                ? "Clique nos limites da propriedade. Ao terminar, conclua a área."
                                : "Arraste os pontos para ajustar o contorno da propriedade."}{" "}
                            <strong className="whitespace-nowrap">{props.vertices.length}/200 pontos</strong>
                        </p>
                        {props.drawing && (
                            <Button size="xs" color="secondary" iconLeading={Check} isDisabled={props.vertices.length < 3} onClick={props.onFinish}>
                                Concluir área
                            </Button>
                        )}
                    </div>
                )}
                {props.overlay && !props.editable && showNdvi && (
                    <div
                        className={cx(
                            "absolute bottom-12 z-10 w-56 max-w-[calc(100%-5rem)] rounded-xl border border-secondary bg-primary/95 p-3 text-xs text-secondary shadow-md",
                            props.overlayPanel ? "right-14" : "left-3",
                        )}
                    >
                        <p className="font-semibold text-primary">
                            NDVI · {new Date(`${props.overlay.date}T12:00:00Z`).toLocaleDateString("pt-BR", { timeZone: "UTC" })}
                        </p>
                        <div
                            className="mt-2 h-2 rounded-full"
                            style={{
                                background: "linear-gradient(to right,#8c2929 0%,#c74229 50%,#f08a2e 60%,#f7d445 70%,#66b542 80%,#14783b 90%,#054024 100%)",
                            }}
                        />
                        <div className="mt-1 flex justify-between tabular-nums">
                            <span>−1</span>
                            <span>0</span>
                            <span>1</span>
                        </div>
                        <p className="mt-1 leading-4 text-tertiary">
                            Valores maiores tendem a indicar vegetação mais vigorosa. Pixels excluídos ficam transparentes.
                        </p>
                    </div>
                )}
                {error && (
                    <FormAlert>
                        {error}
                    </FormAlert>
                )}
            </div>
        </section>
    );
};
