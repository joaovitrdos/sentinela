import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertCircle, Building07, CloudRaining01, Droplets01, Map01, RefreshCw01, Signal01 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { loadCountry, normalizeText } from "@/components/app/brazil-map/geo-data";
import type { StateFeature } from "@/components/app/brazil-map/geo-data";
import { HourlyWeatherChart, RiverLevelChart } from "@/components/app/dashboard/dashboard-charts";
import { FormAlert } from "@/components/app/form-alert";
import { WeatherCredit } from "@/components/app/weather-credit";
import type { LevelChartRow } from "@/components/app/dashboard/dashboard-charts";
import { DashboardCityFilter } from "@/components/app/dashboard/dashboard-city-filter";
import { THEME_CLASSES, loadTheme, titleCase } from "@/components/app/hydro/hydro-data";
import type { HydroPoint } from "@/components/app/hydro/hydro-data";
import type { CurrentWeather, ForecastDay, ForecastHour, WeatherForecast, WeatherTimeZone } from "@/components/app/hydro/weather-data";
import { Button } from "@/components/base/buttons/button";
import { Select } from "@/components/base/select/select";
import { api } from "@/lib/api";
import { errorKey } from "@/lib/errors";
import { SENTINELA_TIME_ZONE } from "@/utils/time-zone";

type RegionId = "all" | "N" | "NE" | "CO" | "SE" | "S";
type PeriodHours = 6 | 12 | 24;

interface CityOption {
    id: string;
    name: string;
    uf: string;
    region: string | null;
    latitude: number;
    longitude: number;
    stationCount: number;
}

interface WeatherForecastResponse {
    current: { last_updated: string };
    forecast: WeatherForecast;
}

interface MetricCardProps {
    title: string;
    value: string;
    detail: string;
    icon: typeof CloudRaining01;
    tone?: "blue" | "green" | "amber" | "red";
}

const toneStyles = {
    blue: "bg-utility-blue-50 text-utility-blue-700 ring-utility-blue-200",
    green: "bg-utility-green-50 text-utility-green-700 ring-utility-green-200",
    amber: "bg-utility-yellow-50 text-utility-yellow-700 ring-utility-yellow-200",
    red: "bg-utility-red-50 text-utility-red-700 ring-utility-red-200",
};

const MetricCard = ({ title, value, detail, icon: Icon, tone = "blue" }: MetricCardProps) => (
    <article className="min-w-0 rounded-[18px] bg-primary p-4 ring-1 ring-secondary sm:p-5">
        <div className="flex items-start justify-between gap-3">
            <p className="text-sm font-medium text-tertiary">{title}</p>
            <span className={`inline-flex size-9 shrink-0 items-center justify-center rounded-xl ring-1 ring-inset ${toneStyles[tone]}`}>
                <Icon className="size-4" aria-hidden="true" />
            </span>
        </div>
        <p className="mt-3 truncate text-2xl font-semibold tracking-tight text-primary tabular-nums">{value}</p>
        <p className="mt-1 text-xs leading-5 text-tertiary">{detail}</p>
    </article>
);

const imageUrl = (icon: string | null | undefined) => (icon?.startsWith("//") ? `https:${icon}` : icon || "");

export const DashboardPage = () => {
    const { t, i18n } = useTranslation();
    const [rain, setRain] = useState<HydroPoint[]>([]);
    const [levels, setLevels] = useState<HydroPoint[]>([]);
    const [stations, setStations] = useState<HydroPoint[]>([]);
    const [states, setStates] = useState<StateFeature[]>([]);
    const [hydroLoading, setHydroLoading] = useState(true);
    const [hydroError, setHydroError] = useState(false);
    const [region, setRegion] = useState<RegionId>("all");
    const [cityId, setCityId] = useState("all");
    const [period, setPeriod] = useState<PeriodHours>(24);
    const [weather, setWeather] = useState<CurrentWeather | null>(null);
    const [forecast, setForecast] = useState<ForecastDay[]>([]);
    const [weatherTimeZone, setWeatherTimeZone] = useState<WeatherTimeZone | null>(null);
    const [forecastUpdatedAt, setForecastUpdatedAt] = useState("");
    const [weatherLoading, setWeatherLoading] = useState(false);
    const [weatherError, setWeatherError] = useState<string | null>(null);
    const [forecastError, setForecastError] = useState<string | null>(null);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [refreshCount, setRefreshCount] = useState(0);
    const mountedRef = useRef(false);
    const hydroRequestRef = useRef<Promise<void> | null>(null);

    const refreshHydro = useCallback((force = false) => {
        if (hydroRequestRef.current) return hydroRequestRef.current;
        setHydroLoading(true);
        const request = Promise.allSettled([loadTheme("rain", force), loadTheme("level", force), loadTheme("stations", force)])
            .then(([rainResult, levelResult, stationResult]) => {
                if (!mountedRef.current) return;
                const failed = [rainResult, levelResult, stationResult].some((result) => result.status === "rejected");
                if (rainResult.status === "fulfilled") setRain(rainResult.value);
                if (levelResult.status === "fulfilled") setLevels(levelResult.value);
                if (stationResult.status === "fulfilled") setStations(stationResult.value);
                setHydroError(failed);
                setLastUpdated(new Date());
                setHydroLoading(false);
            })
            .finally(() => {
                hydroRequestRef.current = null;
            });
        hydroRequestRef.current = request;
        return request;
    }, []);

    useEffect(() => {
        mountedRef.current = true;
        void refreshHydro();
        loadCountry()
            .then((data) => {
                if (mountedRef.current) setStates(data.states);
            })
            .catch(() => undefined);
        const interval = window.setInterval(() => {
            if (document.visibilityState !== "visible") return;
            void refreshHydro();
            setRefreshCount((count) => count + 1);
        }, 10 * 60_000);
        return () => {
            mountedRef.current = false;
            window.clearInterval(interval);
        };
    }, [refreshHydro]);

    const stateByName = useMemo(
        () =>
            new Map(
                states.flatMap((state) => [[normalizeText(state.properties.nome), state] as const, [normalizeText(state.properties.sigla), state] as const]),
            ),
        [states],
    );

    const { cities, pointMetadata } = useMemo(() => {
        const pointMetadata = new WeakMap<HydroPoint, { cityId: string; region: string | null }>();
        const grouped = new Map<string, { name: string; uf: string; lat: number; lon: number; coords: Set<string>; codes: Set<number> }>();
        for (const points of [rain, levels, stations])
            for (const point of points) {
                const state = stateByName.get(normalizeText(point.estado));
                const key = `${normalizeText(point.municipio)}|${normalizeText(state?.properties.sigla ?? point.estado)}`;
                pointMetadata.set(point, { cityId: key, region: state?.properties.regiao ?? null });
                if (!point.municipio || !point.estado || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) continue;
                const row = grouped.get(key) ?? {
                    name: point.municipio,
                    uf: point.estado,
                    lat: 0,
                    lon: 0,
                    coords: new Set<string>(),
                    codes: new Set<number>(),
                };
                const coordKey = `${point.lat.toFixed(4)},${point.lon.toFixed(4)}`;
                if (!row.coords.has(coordKey)) {
                    row.coords.add(coordKey);
                    row.lat += point.lat;
                    row.lon += point.lon;
                }
                row.codes.add(point.codigo);
                grouped.set(key, row);
            }
        const collator = new Intl.Collator(i18n.language);
        const cities: CityOption[] = [...grouped.entries()]
            .map(([id, row]) => {
                const state = stateByName.get(normalizeText(row.uf));
                const size = Math.max(1, row.coords.size);
                return {
                    id,
                    name: titleCase(row.name),
                    uf: state?.properties.sigla ?? row.uf,
                    region: state?.properties.regiao ?? null,
                    latitude: row.lat / size,
                    longitude: row.lon / size,
                    stationCount: row.codes.size,
                };
            })
            .sort((a, b) => b.stationCount - a.stationCount || collator.compare(a.name, b.name));
        return { cities, pointMetadata };
    }, [rain, levels, stations, stateByName, i18n.language]);

    const cityOptions = useMemo(() => cities.filter((city) => region === "all" || city.region === region), [cities, region]);
    const selectedCity = useMemo(() => cityOptions.find((city) => city.id === cityId) ?? null, [cityOptions, cityId]);
    const weatherCity = selectedCity ?? cityOptions[0] ?? null;
    const weatherLatitude = weatherCity?.latitude;
    const weatherLongitude = weatherCity?.longitude;

    useEffect(() => {
        if (cityId !== "all" && !cityOptions.some((city) => city.id === cityId)) setCityId("all");
    }, [cityId, cityOptions]);

    useEffect(() => {
        if (weatherLatitude == null || weatherLongitude == null) {
            setWeatherLoading(false);
            setWeather(null);
            setForecast([]);
            setWeatherTimeZone(null);
            setWeatherError(null);
            setForecastError(null);
            setForecastUpdatedAt("");
            return;
        }

        const controller = new AbortController();
        const query = `latitude=${encodeURIComponent(weatherLatitude)}&longitude=${encodeURIComponent(weatherLongitude)}`;
        const queryWithDays = `${query}&days=3`;
        setWeatherTimeZone(null);
        setWeather(null);
        setForecast([]);
        setWeatherLoading(true);
        setWeatherError(null);
        setForecastError(null);

        const currentRequest = api<CurrentWeather>(`/api/weather/current?${query}`, { signal: controller.signal })
            .then((response) => {
                if (!controller.signal.aborted) setWeather(response);
            })
            .catch((error) => {
                if (!controller.signal.aborted) setWeatherError(t(errorKey(error)));
            });

        const forecastRequest = api<WeatherForecastResponse>(`/api/weather/forecast?${queryWithDays}`, { signal: controller.signal })
            .then((response) => {
                if (controller.signal.aborted) return;
                setForecast(response.forecast.forecastday ?? []);
                setForecastUpdatedAt(response.current?.last_updated ?? "");
            })
            .catch((error) => {
                if (!controller.signal.aborted) setForecastError(t(errorKey(error)));
            });

        const timeZoneRequest = api<WeatherTimeZone>(`/api/weather/time-zone?${query}`, { signal: controller.signal })
            .then((response) => {
                if (!controller.signal.aborted) setWeatherTimeZone(response);
            })
            .catch(() => {
                if (!controller.signal.aborted) setWeatherTimeZone(null);
            });

        void Promise.allSettled([currentRequest, forecastRequest, timeZoneRequest]).then(() => {
            if (!controller.signal.aborted) setWeatherLoading(false);
        });

        return () => controller.abort();
    }, [weatherLatitude, weatherLongitude, refreshCount, t]);

    const matchesFilters = useCallback(
        (point: HydroPoint) => {
            const metadata = pointMetadata.get(point);
            return (region === "all" || metadata?.region === region) && (!selectedCity || metadata?.cityId === selectedCity.id);
        },
        [region, selectedCity, pointMetadata],
    );

    const filteredRain = useMemo(() => rain.filter(matchesFilters), [rain, matchesFilters]);
    const filteredLevels = useMemo(() => levels.filter(matchesFilters), [levels, matchesFilters]);
    const filteredStations = useMemo(() => stations.filter(matchesFilters), [stations, matchesFilters]);
    const number = useMemo(() => new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }), [i18n.language]);
    const integer = useMemo(() => new Intl.NumberFormat(i18n.language), [i18n.language]);
    const dateTime = useMemo(
        () => new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "short", timeZone: SENTINELA_TIME_ZONE }),
        [i18n.language],
    );

    const { avgRain, readingsWithLevel, activeStations, totalStations, riskStations, riverRisk } = useMemo(() => {
        let sumRain = 0;
        let readingsWithLevel = 0;
        let riverRisk = 0;
        const alerts = new Set<number>();
        const active = new Set<number>();
        const monitored = new Set<number>();
        for (const point of filteredRain) {
            sumRain += point.rain?.chuvaMm24h ?? 0;
            if (point.rain?.requiresAttention) alerts.add(point.codigo);
        }
        for (const point of filteredLevels) {
            if (point.level?.cotaCm != null && Number.isFinite(point.level.cotaCm)) readingsWithLevel++;
            if (point.level?.requiresAttention) {
                alerts.add(point.codigo);
                riverRisk++;
            }
        }
        for (const point of filteredStations) {
            monitored.add(point.codigo);
            if (point.active) active.add(point.codigo);
        }
        return {
            avgRain: filteredRain.length ? sumRain / filteredRain.length : null,
            readingsWithLevel,
            activeStations: active.size,
            totalStations: monitored.size,
            riskStations: alerts.size,
            riverRisk,
        };
    }, [filteredRain, filteredLevels, filteredStations]);

    const levelChart = useMemo<LevelChartRow[]>(() => {
        const latest = filteredLevels
            .filter((point) => point.level?.cotaCm != null && Number.isFinite(point.level.cotaCm))
            .sort((a, b) => (a.level?.idadeHoras ?? Infinity) - (b.level?.idadeHoras ?? Infinity))
            .slice(0, 10);
        return latest.map((point) => {
            const classification = THEME_CLASSES.level.find((item) => item.key === point.classKey);
            return {
                id: `${point.codigo}:${point.id}`,
                name: titleCase(point.nome),
                city: titleCase(point.municipio),
                value: point.level?.cotaCm ?? 0,
                statusLabel: classification?.label ?? "hydro.level.stale",
                color:
                    point.classKey === "stale" || point.classKey === "no-ref"
                        ? "var(--color-fg-quaternary)"
                        : (classification?.color ?? "var(--color-fg-quaternary)"),
                updatedAt: point.level?.dataUltimoDado,
            };
        });
    }, [filteredLevels]);

    const hourlyChart = useMemo(() => {
        const currentHour = forecastUpdatedAt.slice(0, 13);
        const hours = forecast
            .flatMap((day) => day.hour ?? [])
            .filter((hour: ForecastHour) => !currentHour || hour.time.slice(0, 13) >= currentHour)
            .slice(0, period);
        return hours.map((hour) => ({
            label: `${hour.time.slice(5, 10)} ${hour.time.slice(11, 16)}`,
            temperature: hour.temp_c,
            rain: hour.precip_mm,
            condition: hour.condition?.text,
            icon: hour.condition?.icon,
            chanceOfRain: hour.chance_of_rain,
        }));
    }, [forecast, forecastUpdatedAt, period]);

    const regionItems = useMemo(
        () => [
            { id: "all", label: t("dashboard.filters.allRegions") },
            ...(["N", "NE", "CO", "SE", "S"] as const).map((id) => ({ id, label: t(`map.regions.${id}`) })),
        ],
        [t],
    );
    const cityItems = useMemo(
        () => cityOptions.map((city) => ({ id: city.id, label: `${city.name} · ${city.uf}`, searchKey: normalizeText(`${city.name} ${city.uf}`) })),
        [cityOptions],
    );
    const periodItems = useMemo(
        () => ([6, 12, 24] as const).map((hours) => ({ id: String(hours), label: t("dashboard.filters.lastHours", { count: hours }) })),
        [t],
    );

    const refreshAll = () => {
        setRefreshCount((value) => value + 1);
        void refreshHydro(true);
    };

    return (
        <div className="container-page flex min-w-0 flex-col gap-5 py-8 lg:py-12">
            <header className="flex flex-col gap-4 pb-2 md:flex-row md:items-end md:justify-between">
                <div>
                    <p className="text-xs font-semibold tracking-[0.16em] text-fg-brand-primary uppercase">Sentinela</p>
                    <h1 className="mt-2 text-3xl font-semibold tracking-tight text-primary sm:text-4xl">{t("dashboard.title")}</h1>
                    <p className="mt-3 max-w-2xl text-base leading-relaxed text-tertiary">{t("dashboard.subtitle")}</p>
                </div>
                <div className="flex items-center gap-3">
                    {lastUpdated && (
                        <span className="hidden text-xs text-tertiary sm:inline">{t("dashboard.updatedAt", { time: dateTime.format(lastUpdated) })}</span>
                    )}
                    <Button size="sm" color="secondary" iconLeading={RefreshCw01} isLoading={hydroLoading || weatherLoading} onPress={refreshAll}>
                        {t("dashboard.refresh")}
                    </Button>
                </div>
            </header>

            <section
                aria-label={t("dashboard.filters.title")}
                className="grid items-start gap-4 rounded-[18px] bg-primary p-5 ring-1 ring-secondary md:grid-cols-3"
            >
                <div>
                    <Select
                        size="sm"
                        label={t("dashboard.filters.region")}
                        items={regionItems}
                        selectedKey={region}
                        onSelectionChange={(key) => {
                            setRegion(String(key ?? "all") as RegionId);
                            setCityId("all");
                        }}
                        className="w-full"
                    >
                        {(item) => <Select.Item id={item.id} label={item.label} />}
                    </Select>
                </div>
                <div>
                    <DashboardCityFilter items={cityItems} selectedKey={cityId} onSelectionChange={setCityId} isDisabled={!cityItems.length} />
                </div>
                <div>
                    <Select
                        size="sm"
                        label={t("dashboard.filters.period")}
                        items={periodItems}
                        selectedKey={String(period)}
                        onSelectionChange={(key) => setPeriod(Number(key ?? 24) as PeriodHours)}
                        className="w-full"
                    >
                        {(item) => <Select.Item id={item.id} label={item.label} />}
                    </Select>
                </div>
            </section>

            <FormAlert type="info">{hydroError ? t("dashboard.partialData") : null}</FormAlert>

            <section aria-label={t("dashboard.metricsTitle")} className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
                <MetricCard
                    title={t("dashboard.cards.rain")}
                    value={avgRain == null ? "—" : `${number.format(avgRain)} mm`}
                    detail={t("dashboard.cards.rainDetail", { count: integer.format(filteredRain.length) })}
                    icon={CloudRaining01}
                    tone={avgRain != null && avgRain >= 50 ? "red" : "blue"}
                />
                <MetricCard
                    title={t("dashboard.cards.temperature")}
                    value={weather ? `${number.format(weather.temperature)}°C` : "—"}
                    detail={weather?.city ?? weatherCity?.name ?? t("dashboard.noWeatherLocation")}
                    icon={CloudRaining01}
                    tone="amber"
                />
                <MetricCard
                    title={t("dashboard.cards.levels")}
                    value={integer.format(readingsWithLevel)}
                    detail={t("dashboard.cards.levelDetail", { count: integer.format(filteredLevels.length) })}
                    icon={Droplets01}
                    tone={riskStations > 0 ? "red" : "green"}
                />
                <MetricCard
                    title={t("dashboard.cards.alerts")}
                    value={integer.format(riskStations)}
                    detail={t("dashboard.cards.alertDetail")}
                    icon={AlertCircle}
                    tone={riskStations > 0 ? "red" : "green"}
                />
                <MetricCard
                    title={t("dashboard.cards.stations")}
                    value={integer.format(activeStations)}
                    detail={t("dashboard.cards.stationDetail", { total: integer.format(totalStations) })}
                    icon={Building07}
                    tone="green"
                />
            </section>

            <section className="grid min-w-0 items-stretch gap-4 xl:grid-cols-2">
                <article className="flex min-w-0 flex-col rounded-[18px] bg-primary p-4 ring-1 ring-secondary sm:p-5">
                    <header className="mb-4 flex min-h-11 flex-wrap items-start justify-between gap-3">
                        <div>
                            <h2 className="text-base font-semibold text-primary">{t("dashboard.charts.weatherTitle")}</h2>
                            <p className="mt-1 text-xs text-tertiary">
                                {t("dashboard.charts.weatherSubtitle", { city: weather?.city ?? weatherCity?.name ?? "—", count: period })}
                            </p>
                        </div>
                        <span className="rounded-full bg-secondary px-2.5 py-1 text-[11px] font-medium text-secondary">
                            {t("dashboard.charts.forecastBadge")}
                        </span>
                    </header>
                    {hourlyChart.length === 0 ? (
                        <div className="flex h-80 items-center justify-center rounded-xl bg-secondary/50 text-sm text-tertiary sm:h-[396px]">
                            {weatherLoading
                                ? t("dashboard.loadingWeather")
                                : forecastError
                                  ? t("dashboard.weather.forecastUnavailable")
                                  : t("dashboard.charts.noForecast")}
                        </div>
                    ) : (
                        <HourlyWeatherChart data={hourlyChart} />
                    )}
                    <p className="mt-auto pt-3 text-xs text-tertiary">{t("dashboard.charts.forecastSourceNote")}</p>
                </article>

                <article className="flex min-w-0 flex-col rounded-[18px] bg-primary p-4 ring-1 ring-secondary sm:p-5">
                    <header className="mb-4 flex min-h-11 items-start justify-between gap-3">
                        <div>
                            <h2 className="text-base font-semibold text-primary">{t("dashboard.charts.levelTitle")}</h2>
                            <p className="mt-1 text-xs text-tertiary">{t("dashboard.charts.levelSubtitle", { count: integer.format(levelChart.length) })}</p>
                        </div>
                        <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-xl bg-utility-blue-50 text-utility-blue-700 ring-1 ring-utility-blue-200">
                            <Signal01 className="size-4" aria-hidden="true" />
                        </span>
                    </header>
                    {levelChart.length === 0 ? (
                        <div className="flex h-80 items-center justify-center rounded-xl bg-secondary/50 px-5 text-center text-sm text-tertiary sm:h-[396px]">
                            {hydroLoading ? t("dashboard.loadingHydro") : t("dashboard.charts.noLevels")}
                        </div>
                    ) : (
                        <RiverLevelChart data={levelChart} />
                    )}
                </article>
            </section>

            <section className="grid min-w-0 items-start gap-4 xl:grid-cols-2">
                <article className="min-w-0 rounded-[18px] bg-primary p-4 ring-1 ring-secondary sm:p-5">
                    <header className="mb-4 flex items-center gap-3">
                        <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-xl bg-utility-blue-50 text-utility-blue-700 ring-1 ring-utility-blue-200">
                            <Map01 className="size-4" aria-hidden="true" />
                        </span>
                        <div>
                            <h2 className="text-base font-semibold text-primary">{t("dashboard.summary.title")}</h2>
                            <p className="text-xs text-tertiary">
                                {t("dashboard.summary.subtitle", {
                                    location: selectedCity
                                        ? `${selectedCity.name} · ${selectedCity.uf}`
                                        : region === "all"
                                          ? t("dashboard.filters.allRegions")
                                          : t(`map.regions.${region}`),
                                })}
                            </p>
                        </div>
                    </header>
                    <div className="grid gap-3 sm:grid-cols-2">
                        <div className="rounded-xl bg-secondary/55 p-3.5">
                            <p className="text-xs font-medium text-tertiary">{t("dashboard.summary.rain")}</p>
                            <p className="mt-1 text-sm font-semibold text-primary">
                                {avgRain == null ? "—" : t("dashboard.summary.rainValue", { value: number.format(avgRain) })}
                            </p>
                            <p className="mt-1 text-xs text-tertiary">{t("dashboard.summary.rainReadings", { count: integer.format(filteredRain.length) })}</p>
                        </div>
                        <div className="rounded-xl bg-secondary/55 p-3.5">
                            <p className="text-xs font-medium text-tertiary">{t("dashboard.summary.river")}</p>
                            <p className="mt-1 text-sm font-semibold text-primary">
                                {t("dashboard.summary.levelsValue", { count: integer.format(readingsWithLevel) })}
                            </p>
                            <p className="mt-1 text-xs text-tertiary">{t("dashboard.summary.riverRisk", { count: integer.format(riverRisk) })}</p>
                        </div>
                    </div>
                </article>

                <article className="min-w-0 rounded-[18px] bg-primary p-4 ring-1 ring-secondary sm:p-5">
                    <header className="mb-3 flex items-center justify-between gap-3">
                        <div>
                            <h2 className="text-base font-semibold text-primary">{t("dashboard.weather.title")}</h2>
                            <p className="mt-1 text-xs text-tertiary">
                                {weather?.city ?? weatherCity?.name ?? t("dashboard.noWeatherLocation")}
                                {weather?.region ? ` · ${weather.region}` : ""}
                            </p>
                        </div>
                        {weather?.icon && <img src={imageUrl(weather.icon)} alt={weather.condition} className="size-12" />}
                    </header>
                    {weatherError ? (
                        <FormAlert>{weatherError}</FormAlert>
                    ) : weather ? (
                        <>
                            <div className="flex items-end gap-2">
                                <p className="text-4xl font-semibold tracking-tight text-primary tabular-nums">{number.format(weather.temperature)}°</p>
                                <p className="pb-1 text-sm text-secondary">{weather.condition}</p>
                            </div>
                            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 border-t border-secondary pt-3 text-xs">
                                <div className="flex justify-between gap-2">
                                    <dt className="text-tertiary">{t("weather.feelsLike")}</dt>
                                    <dd className="font-medium text-secondary">{number.format(weather.feelsLike)}°C</dd>
                                </div>
                                <div className="flex justify-between gap-2">
                                    <dt className="text-tertiary">{t("weather.humidity")}</dt>
                                    <dd className="font-medium text-secondary">{integer.format(weather.humidity)}%</dd>
                                </div>
                                <div className="flex justify-between gap-2">
                                    <dt className="text-tertiary">{t("weather.precipitation")}</dt>
                                    <dd className="font-medium text-secondary">{number.format(weather.precipitation)} mm</dd>
                                </div>
                                <div className="flex justify-between gap-2">
                                    <dt className="text-tertiary">{t("weather.wind")}</dt>
                                    <dd className="font-medium text-secondary">{number.format(weather.windSpeed)} km/h</dd>
                                </div>
                            </dl>
                            {weatherTimeZone && (
                                <p className="mt-3 text-[11px] text-tertiary">
                                    {t("dashboard.weather.localTime", { time: weatherTimeZone.localTime, zone: weatherTimeZone.timeZone })}
                                </p>
                            )}
                            {forecast.length > 0 && (
                                <div className="mt-4 border-t border-secondary pt-3">
                                    <h3 className="mb-2 text-xs font-semibold text-secondary">{t("dashboard.weather.forecastTitle")}</h3>
                                    <ul className="space-y-2">
                                        {forecast.map(({ date, day }) => (
                                            <li key={date} className="flex items-center gap-2 text-xs">
                                                <span className="w-10 shrink-0 text-tertiary">
                                                    {new Date(`${date}T12:00:00`).toLocaleDateString(i18n.language, {
                                                        weekday: "short",
                                                        timeZone: SENTINELA_TIME_ZONE,
                                                    })}
                                                </span>
                                                <img src={imageUrl(day.condition.icon)} alt={day.condition.text} className="size-7 shrink-0" />
                                                <span className="min-w-0 flex-1 truncate text-secondary">{day.condition.text}</span>
                                                <span className="shrink-0 font-medium text-primary tabular-nums">
                                                    {number.format(day.maxtemp_c)}° / {number.format(day.mintemp_c)}°
                                                </span>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}
                            <FormAlert type="info">{forecastError ? t("dashboard.weather.forecastUnavailable") : null}</FormAlert>
                            <p className="mt-3 text-[11px] text-quaternary">{t("dashboard.weather.observedAt", { time: weather.updatedAt })} · WeatherAPI</p>
                        </>
                    ) : (
                        <p className="rounded-lg bg-secondary/55 p-3 text-sm text-tertiary">
                            {weatherLoading ? t("dashboard.loadingWeather") : t("dashboard.noWeatherLocation")}
                        </p>
                    )}
                </article>
            </section>

            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-quaternary">
                <span className="inline-flex items-center gap-1.5">
                    <span className="size-2 rounded-full bg-utility-green-500" />
                    {t("dashboard.liveNote")}
                </span>
                <span>·</span>
                <span>{t("dashboard.sources")}</span>
                {weather?.updatedAt && <span className="ml-auto">WeatherAPI: {weather.updatedAt}</span>}
            </p>
            <WeatherCredit />
        </div>
    );
};
