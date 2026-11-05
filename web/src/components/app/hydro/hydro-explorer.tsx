import type { ReactNode } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronUp, CloudRaining01, Droplets01, LayersTwo01, Map01, RefreshCw01, Signal01 } from "@untitledui/icons";
import type { FeatureCollection, MultiLineString, Point } from "geojson";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useTranslation } from "react-i18next";
import type { BasemapId } from "@/components/app/brazil-map/basemaps";
import { BASEMAPS, preferredBasemap, rememberBasemap } from "@/components/app/brazil-map/basemaps";
import type { BrazilMapController, PointLayer } from "@/components/app/brazil-map/brazil-map";
import { BrazilMap } from "@/components/app/brazil-map/brazil-map";
import type { StateFeature } from "@/components/app/brazil-map/geo-data";
import { loadCountry } from "@/components/app/brazil-map/geo-data";
import { FormAlert } from "@/components/app/form-alert";
import { Button } from "@/components/base/buttons/button";
import { Select } from "@/components/base/select/select";
import { api } from "@/lib/api";
import { errorKey } from "@/lib/errors";
import { cx } from "@/utils/cx";
import { SENTINELA_TIME_ZONE } from "@/utils/time-zone";
import { ApiFilterMenu } from "./api-filter-menu";
import type { HydroPoint, ThemeId } from "./hydro-data";
import { THEME_CLASSES, loadRivers, loadTheme, titleCase } from "./hydro-data";
import { HydroTable } from "./hydro-table";
import { BasemapGallery, ControlButton, ControlGroup, ControlPanel, LayerToggle, Legend, LegendClasses, LegendTopic, useMapPanelWheel } from "./map-controls";
import { WeatherCredit } from "@/components/app/weather-credit";
import type { CurrentWeather, WeatherForecast, WeatherTimeZone } from "./weather-data";

const THEME_ICONS = { rain: CloudRaining01, level: Droplets01, stations: Signal01 };
type ApiFilterId = ThemeId | "weather-current" | "weather-forecast" | "weather-time-zone";
type WeatherFilterId = Extract<ApiFilterId, `weather-${string}`>;
type WeatherLocation = { latitude: number; longitude: number };
const THEME_FILTERS: ThemeId[] = ["rain", "level", "stations"];
const API_FILTERS: ApiFilterId[] = [...THEME_FILTERS, "weather-current", "weather-forecast", "weather-time-zone"];
const isWeatherFilter = (filter: ApiFilterId): filter is Extract<ApiFilterId, `weather-${string}`> => filter.startsWith("weather-");
const isThemeFilter = (filter: ApiFilterId): filter is ThemeId => THEME_FILTERS.includes(filter as ThemeId);

interface WeatherForecastResponse {
    current: { last_updated: string };
    forecast: WeatherForecast;
}

/**
 * Mapa da pagina inicial: mapa de fundo real, rios, estacoes do tema escolhido (chuva, nivel ou HidroWeb),
 * filtros junto da busca, painel unico de informacoes e legenda, e tabelas hidrologicas abaixo do mapa.
 */
export const HydroExplorer = ({ className, mapClassName }: { className?: string; mapClassName?: string }) => {
    const { t, i18n } = useTranslation();
    const mapRef = useRef<BrazilMapController>(null);
    const tableRef = useRef<HTMLDivElement>(null);
    const rootRef = useRef<HTMLDivElement>(null);
    const informationPanelRef = useRef<HTMLDivElement>(null);
    const informationWheelRef = useMapPanelWheel();
    const sharedPanelRef = useCallback(
        (node: HTMLDivElement | null) => {
            informationPanelRef.current = node;
            return informationWheelRef(node);
        },
        [informationWheelRef],
    );
    const reducedMotion = useReducedMotion();

    // Escolhas do usuario
    const [basemap, setBasemap] = useState<BasemapId>(() => preferredBasemap("satellite"));
    const [rivers, setRivers] = useState(true);
    const [showInactive, setShowInactive] = useState(true);
    const [hidden, setHidden] = useState<string[]>([]);
    const [tableOpen, setTableOpen] = useState(true);
    const [selectedFilters, setSelectedFilters] = useState<ApiFilterId[]>(["rain"]);
    const [filtersOpen, setFiltersOpen] = useState(false);
    const [legendOpen, setLegendOpen] = useState(true);
    const [mapControlsOpen, setMapControlsOpen] = useState(false);
    const [selected, setSelected] = useState<{ point: HydroPoint; theme: ThemeId } | null>(null);
    const [detailsOpen, setDetailsOpen] = useState(true);
    const [weatherLocation, setWeatherLocation] = useState<WeatherLocation | null>(null);
    const [currentWeather, setCurrentWeather] = useState<CurrentWeather | null>(null);
    const [weatherForecast, setWeatherForecast] = useState<WeatherForecast | null>(null);
    const [weatherTimeZone, setWeatherTimeZone] = useState<WeatherTimeZone | null>(null);
    const [weatherLoading, setWeatherLoading] = useState<Record<WeatherFilterId, boolean>>({
        "weather-current": false,
        "weather-forecast": false,
        "weather-time-zone": false,
    });
    const [weatherErrors, setWeatherErrors] = useState<Partial<Record<WeatherFilterId, string>>>({});
    const [weatherRefresh, setWeatherRefresh] = useState(0);
    const [scopeUf, setScopeUf] = useState<string | null>(null);

    // Dados
    const [pointsByTheme, setPointsByTheme] = useState<Record<ThemeId, HydroPoint[]>>({ rain: [], level: [], stations: [] });
    const [themeLoading, setThemeLoading] = useState<Record<ThemeId, boolean>>({ rain: false, level: false, stations: false });
    const [themeErrors, setThemeErrors] = useState<Partial<Record<ThemeId, string>>>({});
    const [states, setStates] = useState<StateFeature[]>([]);
    const [riverLines, setRiverLines] = useState<FeatureCollection<MultiLineString> | null>(null);
    const selectedThemes = useMemo(() => THEME_FILTERS.filter((theme) => selectedFilters.includes(theme)), [selectedFilters]);
    const selectedWeatherFilters = useMemo(
        () => API_FILTERS.filter((filter): filter is WeatherFilterId => isWeatherFilter(filter) && selectedFilters.includes(filter)),
        [selectedFilters],
    );
    const selectedThemesKey = selectedThemes.join("|");
    const selectedWeatherKey = selectedWeatherFilters.join("|");
    const allFiltersSelected = selectedFilters.length === API_FILTERS.length;
    const basemapOptions = useMemo(() => BASEMAPS.map((item) => ({ id: item.id, label: t(item.label) })), [t]);

    useEffect(() => rememberBasemap(basemap), [basemap]);

    useEffect(() => {
        loadCountry()
            .then((c) => setStates(c.states))
            .catch(() => undefined);
    }, []);

    // Rios principais da ANA: so carrega quando a camada de rios esta ligada.
    // Se falhar, o mapa segue com os rios do OpenStreetMap (que aparecem ao aproximar).
    useEffect(() => {
        if (!rivers || riverLines) return;
        let alive = true;
        loadRivers()
            .then((data) => alive && setRiverLines(data))
            .catch(() => undefined);
        return () => {
            alive = false;
        };
    }, [rivers, riverLines]);

    // Carrega somente os temas hidrológicos marcados; cada fonte mantém sua tabela própria.
    useEffect(() => {
        let alive = true;
        const themes = selectedThemesKey ? (selectedThemesKey.split("|") as ThemeId[]) : [];
        setThemeLoading({ rain: false, level: false, stations: false });
        setThemeErrors({});
        for (const theme of themes) {
            setThemeLoading((current) => ({ ...current, [theme]: true }));
            loadTheme(theme)
                .then((rows) => alive && setPointsByTheme((current) => ({ ...current, [theme]: rows })))
                .catch((err) => {
                    if (!alive) return;
                    setPointsByTheme((current) => ({ ...current, [theme]: [] }));
                    setThemeErrors((current) => ({ ...current, [theme]: t(errorKey(err)) }));
                })
                .finally(() => alive && setThemeLoading((current) => ({ ...current, [theme]: false })));
        }
        return () => {
            alive = false;
        };
    }, [selectedThemesKey, t]);

    useEffect(() => {
        if (selected && !selectedThemes.includes(selected.theme)) setSelected(null);
    }, [selected, selectedThemes]);

    useEffect(() => {
        if (!selectedWeatherFilters.length || !weatherLocation) {
            setCurrentWeather(null);
            setWeatherForecast(null);
            setWeatherTimeZone(null);
            setWeatherErrors({});
            setWeatherLoading({ "weather-current": false, "weather-forecast": false, "weather-time-zone": false });
            return;
        }

        const controller = new AbortController();
        const query = `latitude=${encodeURIComponent(weatherLocation.latitude)}&longitude=${encodeURIComponent(weatherLocation.longitude)}`;
        setWeatherLoading({ "weather-current": false, "weather-forecast": false, "weather-time-zone": false });
        setWeatherErrors({});
        if (selectedWeatherFilters.includes("weather-current")) setCurrentWeather(null);
        if (selectedWeatherFilters.includes("weather-forecast")) setWeatherForecast(null);
        if (selectedWeatherFilters.includes("weather-time-zone")) setWeatherTimeZone(null);

        const requests = selectedWeatherFilters.map((filter) => {
            setWeatherLoading((current) => ({ ...current, [filter]: true }));
            const request =
                filter === "weather-current"
                    ? api<CurrentWeather>(`/api/weather/current?${query}`, { signal: controller.signal }).then((response) => {
                          if (!controller.signal.aborted) setCurrentWeather(response);
                      })
                    : filter === "weather-forecast"
                      ? api<WeatherForecastResponse>(`/api/weather/forecast?${query}&days=3`, { signal: controller.signal }).then(
                            (response) => !controller.signal.aborted && setWeatherForecast(response.forecast),
                        )
                      : api<WeatherTimeZone>(`/api/weather/time-zone?${query}`, { signal: controller.signal }).then((response) => {
                            if (!controller.signal.aborted) setWeatherTimeZone(response);
                        });
            return request
                .catch((error) => {
                    if (!controller.signal.aborted) setWeatherErrors((current) => ({ ...current, [filter]: errorKey(error) }));
                })
                .finally(() => {
                    if (!controller.signal.aborted) setWeatherLoading((current) => ({ ...current, [filter]: false }));
                });
        });
        void Promise.all(requests);

        return () => controller.abort();
    }, [selectedWeatherKey, weatherLocation, weatherRefresh]);

    const classes = useMemo(
        () => selectedThemes.flatMap((theme) => THEME_CLASSES[theme].map((item) => ({ ...item, key: `${theme}:${item.key}` }))),
        [selectedThemes],
    );
    const classByKey = useMemo(() => Object.fromEntries(classes.map((item) => [item.key, item])), [classes]);

    const pointEntries = useMemo(() => {
        let id = 0;
        return selectedThemes.flatMap((theme) =>
            (pointsByTheme[theme] ?? [])
                .filter((point) => theme !== "stations" || showInactive || point.active)
                .map((point) => ({ id: id++, theme, point, classKey: `${theme}:${point.classKey}` })),
        );
    }, [selectedThemes, pointsByTheme, showInactive]);

    const counts = useMemo(() => {
        const out: Record<string, number> = {};
        for (const entry of pointEntries) out[entry.classKey] = (out[entry.classKey] ?? 0) + 1;
        return out;
    }, [pointEntries]);

    const pointLayer = useMemo<PointLayer>(() => {
        const data: FeatureCollection<Point> = {
            type: "FeatureCollection",
            features: pointEntries.map(({ id, point, classKey }) => ({
                type: "Feature",
                properties: { id, k: classKey, s: point.sort, a: point.active ? 1 : 0 },
                geometry: { type: "Point", coordinates: [point.lon, point.lat] },
            })),
        };
        return { data, colors: Object.fromEntries(classes.map((c) => [c.key, c.color])), hidden };
    }, [pointEntries, classes, hidden]);

    const byId = useMemo(() => new Map(pointEntries.map((entry) => [entry.id, entry])), [pointEntries]);

    const select = useCallback((point: HydroPoint, theme: ThemeId, fly: boolean) => {
        setSelected({ point, theme });
        setDetailsOpen(true);
        informationPanelRef.current?.scrollTo({ top: 0, behavior: "instant" });
        setFiltersOpen(false);
        setWeatherLocation({ latitude: point.lat, longitude: point.lon });
        if (fly) {
            // Clique na tabela: traz o mapa de volta para a tela e voa ate a estacao.
            rootRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
            mapRef.current?.flyTo([point.lon, point.lat], 10);
        }
    }, []);

    const apiFilterItems = useMemo(
        () =>
            API_FILTERS.map((id) => ({
                id,
                label: isThemeFilter(id)
                    ? t(`hydro.themes.${id}`)
                    : id === "weather-current"
                      ? t("weather.current")
                      : id === "weather-forecast"
                        ? t("weather.forecast")
                        : t("weather.timeZone"),
                icon: isThemeFilter(id) ? THEME_ICONS[id] : id === "weather-time-zone" ? Signal01 : CloudRaining01,
            })),
        [t],
    );

    const toggleFilter = (filter: ApiFilterId) => {
        setSelectedFilters((current) => API_FILTERS.filter((item) => (item === filter ? !current.includes(item) : current.includes(item))));
    };

    const retryTheme = (theme: ThemeId) => {
        setThemeLoading((current) => ({ ...current, [theme]: true }));
        setThemeErrors((current) => ({ ...current, [theme]: undefined }));
        loadTheme(theme, true)
            .then((rows) => setPointsByTheme((current) => ({ ...current, [theme]: rows })))
            .catch((err) => {
                setPointsByTheme((current) => ({ ...current, [theme]: [] }));
                setThemeErrors((current) => ({ ...current, [theme]: t(errorKey(err)) }));
            })
            .finally(() => setThemeLoading((current) => ({ ...current, [theme]: false })));
    };

    const toggleClass = (key: string) => setHidden((h) => (h.includes(key) ? h.filter((k) => k !== key) : [...h, key]));

    const toggleTable = () => {
        setTableOpen((open) => {
            const next = !open;
            // Rola ate a tabela depois que ela abrir.
            if (next) setTimeout(() => tableRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" }), 250);
            return next;
        });
    };

    const fmt = new Intl.NumberFormat(i18n.language);

    return (
        <div ref={rootRef} className="scroll-mt-20">
            <div
                className="min-w-0"
                onClickCapture={(event) => {
                    const target = event.target;
                    if (!(target instanceof Element) || !target.closest("[data-map-filters]")) setFiltersOpen(false);
                }}
            >
                <BrazilMap
                    variant="full"
                    className={className}
                    mapClassName={mapClassName}
                    basemap={basemap}
                    rivers={rivers}
                    riverLines={riverLines}
                    points={pointLayer}
                    selectedLocation={selectedWeatherFilters.length && weatherLocation ? [weatherLocation.longitude, weatherLocation.latitude] : null}
                    controllerRef={mapRef}
                    toolbarExtra={
                        <>
                            <ApiFilterMenu
                                open={filtersOpen}
                                onOpenChange={(open) => {
                                    setFiltersOpen(open);
                                    if (open) setMapControlsOpen(false);
                                }}
                                options={apiFilterItems}
                                selected={selectedFilters}
                                onToggle={toggleFilter}
                                onSelectAll={() => setSelectedFilters(allFiltersSelected ? [] : [...API_FILTERS])}
                                hint={selectedWeatherFilters.length > 0 ? t("weather.selectHint") : undefined}
                            />
                            <Select
                                size="sm"
                                aria-label={t("hydro.basemaps.title")}
                                icon={Map01}
                                items={basemapOptions}
                                selectedKey={basemap}
                                onSelectionChange={(key) => {
                                    const next = BASEMAPS.find((item) => item.id === key);
                                    if (next) setBasemap(next.id);
                                }}
                                onOpenChange={(open) => {
                                    if (open) {
                                        setFiltersOpen(false);
                                        setMapControlsOpen(false);
                                    }
                                }}
                                className="w-48 shrink-0"
                            >
                                {(item) => <Select.Item id={item.id} label={item.label} icon={Map01} />}
                            </Select>
                        </>
                    }
                    onScopeChange={setScopeUf}
                    onMapClick={([longitude, latitude]) => {
                        setWeatherLocation({ latitude, longitude });
                        setSelected(null);
                        setFiltersOpen(false);
                    }}
                    controls={
                        <>
                            <ControlGroup>
                                <ControlButton
                                    icon={LayersTwo01}
                                    label={t("hydro.mapControls.title")}
                                    active={mapControlsOpen}
                                    onClick={() => {
                                        setMapControlsOpen((open) => !open);
                                        setFiltersOpen(false);
                                    }}
                                />
                            </ControlGroup>
                            <ControlPanel
                                open={mapControlsOpen}
                                title={t("hydro.mapControls.title")}
                                onClose={() => setMapControlsOpen(false)}
                                className="w-[min(18rem,calc(100vw-5rem))]"
                            >
                                <h3 className="mb-2 text-xs font-semibold text-secondary">{t("hydro.basemaps.title")}</h3>
                                <BasemapGallery value={basemap} onChange={setBasemap} />
                                <div className="mt-3 border-t border-secondary pt-2">
                                    <h3 className="mb-1 text-xs font-semibold text-secondary">{t("hydro.layers.title")}</h3>
                                    <LayerToggle
                                        label={t("hydro.layers.rivers")}
                                        hint={t("hydro.layers.riversHint")}
                                        isSelected={rivers}
                                        onChange={setRivers}
                                    />
                                    {selectedThemes.includes("stations") && (
                                        <LayerToggle
                                            label={t("hydro.layers.inactive")}
                                            hint={fmt.format(pointsByTheme.stations.filter((point) => !point.active).length)}
                                            isSelected={showInactive}
                                            onChange={setShowInactive}
                                        />
                                    )}
                                </div>
                            </ControlPanel>
                        </>
                    }
                    overlay={
                        <>
                            <div className="pointer-events-none absolute top-3 bottom-12 left-3 z-20 flex min-h-0 w-[min(20rem,calc(100%-4.5rem))] flex-col items-start">
                                <div
                                    ref={sharedPanelRef}
                                    data-map-scroll-viewport
                                    className="pointer-events-auto max-h-full min-h-0 w-full overflow-y-auto overscroll-contain rounded-xl bg-primary/95 shadow-lg ring-1 ring-secondary_alt backdrop-blur-sm"
                                >
                                    <AnimatePresence initial={false}>
                                        {selected && (
                                            <motion.section
                                                key="selected-information"
                                                initial={{ height: 0, opacity: 0 }}
                                                animate={{ height: "auto", opacity: 1 }}
                                                exit={{ height: 0, opacity: 0 }}
                                                transition={{ duration: reducedMotion ? 0 : 0.24, ease: [0.22, 1, 0.36, 1] }}
                                                className="overflow-hidden border-b border-secondary"
                                            >
                                                <Legend
                                                    inline
                                                    scrollable={false}
                                                    headerLabel={t("hydro.popup.title")}
                                                    title={titleCase(selected.point.nome)}
                                                    open={detailsOpen}
                                                    onOpenChange={setDetailsOpen}
                                                    className="rounded-none bg-transparent ring-0"
                                                >
                                                    <PointDetails point={selected.point} theme={selected.theme} />
                                                </Legend>
                                            </motion.section>
                                        )}
                                    </AnimatePresence>
                                    <Legend
                                        inline
                                        scrollable={false}
                                        className="rounded-none bg-transparent ring-0"
                                        title={
                                            selectedFilters.length
                                                ? t("hydro.filters.selectedCount", { count: selectedFilters.length })
                                                : t("hydro.filters.noneSelected")
                                        }
                                        open={legendOpen}
                                        onOpenChange={setLegendOpen}
                                        footer={
                                            <>
                                                {selectedThemes.length > 0 && <p>{t("hydro.popup.source")}</p>}
                                                {selectedWeatherFilters.length > 0 && <WeatherCredit className={selectedThemes.length > 0 ? "mt-1" : undefined} />}
                                            </>
                                        }
                                    >
                                        {!selectedFilters.length && <p className="px-3 py-3 text-xs text-tertiary">{t("hydro.legend.empty")}</p>}
                                        {selectedThemes.map((theme) => (
                                            <LegendTopic key={theme} title={t(`hydro.themes.${theme}`)}>
                                                {themeLoading[theme] ? (
                                                    <p className="px-3 pb-3 text-xs text-tertiary">{t("hydro.table.loading")}</p>
                                                ) : themeErrors[theme] ? (
                                                    <div className="space-y-2 px-3 pb-3">
                                                        <FormAlert>{themeErrors[theme]}</FormAlert>
                                                        <Button size="sm" color="secondary" onPress={() => retryTheme(theme)}>
                                                            {t("hydro.table.retry")}
                                                        </Button>
                                                    </div>
                                                ) : (
                                                    <LegendClasses
                                                        classes={classes.filter((item) => item.key.startsWith(`${theme}:`))}
                                                        counts={counts}
                                                        hidden={hidden}
                                                        onToggleClass={toggleClass}
                                                    />
                                                )}
                                            </LegendTopic>
                                        ))}
                                        <WeatherDetails
                                            modes={selectedWeatherFilters}
                                            location={weatherLocation}
                                            current={currentWeather}
                                            forecast={weatherForecast}
                                            timeZone={weatherTimeZone}
                                            loading={weatherLoading}
                                            errors={weatherErrors}
                                            onRetry={() => setWeatherRefresh((version) => version + 1)}
                                        />
                                    </Legend>
                                </div>
                            </div>
                        </>
                    }
                    onPointClick={(id) => {
                        const entry = byId.get(id);
                        if (entry) select(entry.point, entry.theme, false);
                    }}
                    renderPointTooltip={(id) => {
                        const entry = byId.get(id);
                        const c = entry && classByKey[entry.classKey];
                        if (!entry) return null;
                        const { point, theme } = entry;
                        return (
                            <div className="min-w-40 rounded-lg bg-primary px-3 py-2 shadow-lg ring-1 ring-secondary_alt">
                                <p className="text-sm font-semibold text-primary">{titleCase(point.nome)}</p>
                                <p className="text-xs text-tertiary">
                                    {[titleCase(point.municipio), point.estado && titleCase(point.estado)].filter(Boolean).join(" · ")}
                                </p>
                                {c && (
                                    <p className="mt-1 flex items-center gap-1.5 text-xs text-secondary">
                                        <span className="size-2 rounded-full" style={{ background: c.color }} />
                                        {pointValue(point, theme) ?? t(c.label)}
                                    </p>
                                )}
                            </div>
                        );
                    }}
                />
            </div>

            <section ref={tableRef} className="mt-4 overflow-hidden rounded-2xl bg-primary shadow-xs ring-1 ring-secondary">
                <header className="flex items-center justify-between gap-3 border-b border-secondary px-4 py-3 sm:px-5">
                    <div>
                        <h2 className="text-sm font-semibold text-primary">{t("hydro.table.title")}</h2>
                        <p className="mt-0.5 text-xs text-tertiary">
                            {selectedThemes.length ? t("hydro.filters.activeTables", { count: selectedThemes.length }) : t("hydro.filters.noHydroSelected")}
                        </p>
                    </div>
                    <Button size="sm" color="secondary" iconLeading={tableOpen ? ChevronUp : ChevronDown} onPress={toggleTable} aria-expanded={tableOpen}>
                        {tableOpen ? t("hydro.table.hide") : t("hydro.table.show")}
                    </Button>
                </header>
                <AnimatePresence initial={false}>
                    {tableOpen && (
                        <motion.div
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: "auto", opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                            className="overflow-hidden"
                        >
                            {selectedThemes.map((theme) => (
                                <section key={theme} className="border-b border-secondary last:border-0">
                                    <header className="flex items-center justify-between gap-3 bg-secondary/50 px-4 py-2.5 sm:px-5">
                                        <h3 className="text-xs font-semibold text-secondary">{t(`hydro.themes.${theme}`)}</h3>
                                        <p className="text-xs text-tertiary">
                                            {themeLoading[theme]
                                                ? t("hydro.table.loading")
                                                : t("hydro.table.range", {
                                                      from: 1,
                                                      to: fmt.format(pointsByTheme[theme].length),
                                                      total: fmt.format(pointsByTheme[theme].length),
                                                  })}
                                        </p>
                                    </header>
                                    <HydroTable
                                        theme={theme}
                                        points={pointsByTheme[theme]}
                                        loading={themeLoading[theme]}
                                        error={themeErrors[theme] ?? null}
                                        onRetry={() => retryTheme(theme)}
                                        states={states}
                                        scopeUf={scopeUf}
                                        onPointClick={(point) => select(point, theme, true)}
                                        onStateClick={(uf) => {
                                            mapRef.current?.openState(uf);
                                            setScopeUf(uf);
                                        }}
                                    />
                                </section>
                            ))}
                        </motion.div>
                    )}
                </AnimatePresence>
            </section>
        </div>
    );
};

/** Valor principal da estacao para o tooltip (mm de chuva, cota em cm...). */
const pointValue = (p: HydroPoint, theme: ThemeId): string | null => {
    if (theme === "rain" && p.rain) return `${p.rain.chuvaMm24h.toLocaleString(undefined, { maximumFractionDigits: 1 })} mm`;
    if (theme === "level" && p.level?.cotaCm != null) return `${p.level.cotaCm.toLocaleString(undefined, { maximumFractionDigits: 0 })} cm`;
    return null;
};

const WeatherDetails = ({
    modes,
    location,
    current,
    forecast,
    timeZone,
    loading,
    errors,
    onRetry,
}: {
    modes: WeatherFilterId[];
    location: WeatherLocation | null;
    current: CurrentWeather | null;
    forecast: WeatherForecast | null;
    timeZone: WeatherTimeZone | null;
    loading: Record<WeatherFilterId, boolean>;
    errors: Partial<Record<WeatherFilterId, string>>;
    onRetry: () => void;
}) => {
    const { t, i18n } = useTranslation();
    const iconUrl = current?.icon.startsWith("//") ? `https:${current.icon}` : current?.icon;
    const titleFor = (mode: WeatherFilterId) =>
        mode === "weather-current" ? t("weather.current") : mode === "weather-forecast" ? t("weather.forecast") : t("weather.timeZone");

    return (
        <>
            {location && modes.length > 0 && (
                <p className="border-b border-secondary px-3 py-2 text-[11px] text-quaternary tabular-nums">
                    {location.latitude.toFixed(3)}, {location.longitude.toFixed(3)}
                </p>
            )}
            {modes.map((mode) => {
                const title = titleFor(mode);
                return (
                    <LegendTopic key={mode} title={title}>
                        <div aria-live="polite" aria-busy={loading[mode]} className="px-3 pb-3">
                            {!location ? (
                                <p className="text-sm text-tertiary">{t("weather.empty")}</p>
                            ) : loading[mode] ? (
                                <p className="text-sm text-tertiary">{t("weather.loading")}</p>
                            ) : errors[mode] ? (
                                <div className="space-y-2">
                                    <FormAlert>{t(errors[mode])}</FormAlert>
                                    <Button size="sm" color="secondary" iconLeading={RefreshCw01} onPress={onRetry}>
                                        {t("weather.retry")}
                                    </Button>
                                </div>
                            ) : mode === "weather-current" && current ? (
                                <>
                                    <div className="flex items-center gap-3">
                                        {iconUrl && <img src={iconUrl} alt={current.condition} className="size-12" />}
                                        <div>
                                            <p className="text-2xl font-semibold text-primary tabular-nums">
                                                {current.temperature.toLocaleString(i18n.language)}°C
                                            </p>
                                            <p className="text-xs text-tertiary">
                                                {current.condition} · {current.city}
                                                {current.region ? `, ${current.region}` : ""}
                                            </p>
                                        </div>
                                    </div>
                                    <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2">
                                        <WeatherValue label={t("weather.feelsLike")} value={`${current.feelsLike.toLocaleString(i18n.language)}°C`} />
                                        <WeatherValue label={t("weather.humidity")} value={`${current.humidity}%`} />
                                        <WeatherValue label={t("weather.precipitation")} value={`${current.precipitation.toLocaleString(i18n.language)} mm`} />
                                        <WeatherValue label={t("weather.wind")} value={`${current.windSpeed.toLocaleString(i18n.language)} km/h`} />
                                    </dl>
                                </>
                            ) : mode === "weather-forecast" && forecast ? (
                                <ul className="space-y-2">
                                    {forecast.forecastday.slice(0, 3).map((day) => (
                                        <li key={day.date} className="flex items-center gap-2 border-b border-secondary pb-2 last:border-0 last:pb-0">
                                            <img
                                                src={day.day.condition.icon.startsWith("//") ? `https:${day.day.condition.icon}` : day.day.condition.icon}
                                                alt={day.day.condition.text}
                                                className="size-9 shrink-0"
                                            />
                                            <div className="min-w-0 flex-1">
                                                <p className="text-xs font-medium text-secondary">
                                                    {new Date(`${day.date}T12:00:00`).toLocaleDateString(i18n.language, {
                                                        weekday: "long",
                                                        day: "numeric",
                                                        month: "short",
                                                        timeZone: SENTINELA_TIME_ZONE,
                                                    })}
                                                </p>
                                                <p className="truncate text-[11px] text-tertiary">
                                                    {day.day.condition.text} · {day.day.totalprecip_mm.toLocaleString(i18n.language)} mm
                                                </p>
                                            </div>
                                            <p className="shrink-0 text-xs font-semibold text-primary tabular-nums">
                                                {Math.round(day.day.maxtemp_c)}° / {Math.round(day.day.mintemp_c)}°
                                            </p>
                                        </li>
                                    ))}
                                </ul>
                            ) : mode === "weather-time-zone" && timeZone ? (
                                <div>
                                    <p className="text-lg font-semibold text-primary tabular-nums">{timeZone.localTime}</p>
                                    <p className="mt-1 text-sm break-words text-secondary">{timeZone.timeZone}</p>
                                    <p className="mt-1 text-xs text-tertiary">
                                        {timeZone.city}
                                        {timeZone.region ? `, ${timeZone.region}` : ""} · {timeZone.country}
                                    </p>
                                </div>
                            ) : (
                                <p className="text-sm text-tertiary">{t("weather.empty")}</p>
                            )}
                        </div>
                    </LegendTopic>
                );
            })}
        </>
    );
};

const WeatherValue = ({ label, value }: { label: string; value: string }) => (
    <div className="min-w-0">
        <dt className="text-[11px] text-quaternary">{label}</dt>
        <dd className="truncate text-xs font-medium text-secondary">{value}</dd>
    </div>
);

/** Conteudo do balao ao clicar numa estacao. */
const PointDetails = ({ point, theme }: { point: HydroPoint; theme: ThemeId }) => {
    const { t, i18n } = useTranslation();
    const c = THEME_CLASSES[theme].find((x) => x.key === point.classKey);
    const date = (iso: string | null | undefined) =>
        iso ? new Date(iso).toLocaleString(i18n.language, { dateStyle: "short", timeStyle: "short", timeZone: SENTINELA_TIME_ZONE }) : "—";

    const rows: [string, ReactNode][] = [];
    if (point.rain) {
        rows.push([t("hydro.table.cols.rain"), `${point.rain.chuvaMm24h.toLocaleString(i18n.language, { maximumFractionDigits: 1 })} mm`]);
        rows.push([t("hydro.table.cols.basin"), titleCase(point.rain.bacia) || "—"]);
        rows.push([t("hydro.table.cols.responsible"), point.rain.responsavel ?? "—"]);
        rows.push([t("hydro.popup.lastData"), date(point.rain.dataUltimoDado)]);
    }
    if (point.level) {
        rows.push([t("hydro.table.cols.level"), point.level.cotaCm != null ? `${point.level.cotaCm.toLocaleString(i18n.language)} cm` : "—"]);
        rows.push([t("hydro.table.cols.basin"), titleCase(point.level.subBacia ?? point.level.bacia) || "—"]);
        rows.push([
            t("hydro.table.cols.responsible"),
            [point.level.responsavel, point.level.operadora]
                .filter(Boolean)
                .filter((v, i, a) => a.indexOf(v) === i)
                .join(" / ") || "—",
        ]);
        rows.push([t("hydro.table.cols.project"), point.level.projeto ?? "—"]);
        rows.push([t("hydro.popup.lastData"), date(point.level.dataUltimoDado)]);
    }
    if (point.station) {
        const s = point.station;
        rows.push([t("hydro.table.cols.river"), titleCase(s.rio) || "—"]);
        rows.push([t("hydro.table.cols.basin"), titleCase(s.subBacia ?? s.bacia) || "—"]);
        rows.push([
            t("hydro.table.cols.responsible"),
            [s.responsavel, s.operadora]
                .filter(Boolean)
                .filter((v, i, a) => a.indexOf(v) === i)
                .join(" / ") || "—",
        ]);
        rows.push([t("hydro.table.cols.telemetry"), s.telemetrica ? t("hydro.stations.telemetry") : t("hydro.stations.conventional")]);
        if (s.altitude != null) rows.push([t("hydro.popup.altitude"), `${s.altitude.toLocaleString(i18n.language)} m`]);
        if (s.areaDrenagem != null) rows.push([t("hydro.popup.drainage"), `${s.areaDrenagem.toLocaleString(i18n.language)} km²`]);
    }

    return (
        <div className="w-full p-3">
            <p className="text-xs text-tertiary">
                {[titleCase(point.municipio), titleCase(point.estado)].filter(Boolean).join(" · ")} · {point.codigo}
            </p>
            {c && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                    <p
                        className={cx(
                            "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium text-secondary ring-1 ring-secondary ring-inset",
                            !point.active && "opacity-60",
                        )}
                    >
                        <span className="size-2 rounded-full" style={{ background: c.color }} />
                        {t(c.label)}
                        {point.station && ` · ${point.active ? t("hydro.stations.operating") : t("hydro.stations.inactive")}`}
                    </p>
                    {point.level?.situacaoEstacao === "MANUTENCAO" && (
                        <p className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium text-warning-primary ring-1 ring-secondary ring-inset">
                            {t("hydro.stations.maintenance")}
                        </p>
                    )}
                </div>
            )}
            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2">
                {rows.map(([label, value]) => (
                    <div key={label} className="min-w-0">
                        <dt className="text-[11px] text-quaternary">{label}</dt>
                        <dd className="text-xs font-medium break-words text-secondary">{value}</dd>
                    </div>
                ))}
            </dl>
            <p className="mt-3 border-t border-secondary pt-2 text-[11px] text-quaternary">{t("hydro.popup.source")}</p>
        </div>
    );
};
