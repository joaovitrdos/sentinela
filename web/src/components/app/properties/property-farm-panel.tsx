import { FormAlert } from "@/components/app/form-alert";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/base/buttons/button";
import { api } from "@/lib/api";
import { createPropertyActivity, deletePropertyActivity, listPropertyActivities, propertyErrorMessage } from "@/lib/property-data";
import type { PropertyActivity, PropertyActivityType, SavedProperty } from "@/lib/property-data";
import type { RainReading } from "@/components/app/hydro/hydro-data";
import type { ForecastDay } from "@/components/app/hydro/weather-data";
import { PropertyOperations } from "./property-operations";
import { PropertySeasonNotebook } from "./property-season-notebook";

type ForecastResponse = { forecast: { forecastday: ForecastDay[] } };
const today = () => {
    const date = new Date();
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 10);
};
const fieldClass = "w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2 focus-visible:outline-offset-2";
const formatDate = (value: string) => new Date(`${value.slice(0, 10)}T12:00:00Z`).toLocaleDateString("pt-BR", { timeZone: "UTC" });
const formatKm = (value: number) => `${value.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} km`;
const activityLabels: Record<PropertyActivityType, string> = {
    IRRIGATION: "Irrigação",
    RAINFALL: "Chuva medida",
    INSPECTION: "Vistoria",
    INCIDENT: "Ocorrência",
    PLANTING: "Plantio",
    FERTILIZATION: "Adubação",
    SPRAYING: "Aplicação",
    HARVEST: "Colheita",
    OTHER: "Outra operação",
};

const distanceKm = (aLat: number, aLon: number, bLat: number, bLon: number) => {
    const rad = (degrees: number) => degrees * Math.PI / 180;
    const dLat = rad(bLat - aLat);
    const dLon = rad(bLon - aLon);
    const value = Math.sin(dLat / 2) ** 2 + Math.cos(rad(aLat)) * Math.cos(rad(bLat)) * Math.sin(dLon / 2) ** 2;
    return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
};

export const PropertyFarmPanel = ({ property, inspectionSuggestion }: { property: SavedProperty | null; inspectionSuggestion?: { propertyId: string; note: string; key: number } | null }) => {
    const [forecast, setForecast] = useState<ForecastDay[]>([]);
    const [rain, setRain] = useState<RainReading | null>(null);
    const [weatherLoading, setWeatherLoading] = useState(false);
    const [weatherError, setWeatherError] = useState("");
    const [activities, setActivities] = useState<PropertyActivity[]>([]);
    const [activityLoading, setActivityLoading] = useState(false);
    const [activityError, setActivityError] = useState("");
    const [activityType, setActivityType] = useState<PropertyActivityType>("INSPECTION");
    const [activityDate, setActivityDate] = useState(today());
    const [activityTime, setActivityTime] = useState("");
    const [activityNotes, setActivityNotes] = useState("");
    const [amountMm, setAmountMm] = useState("");
    const [savingActivity, setSavingActivity] = useState(false);
    const propertyId = property?.propertyId;
    const currentPropertyId = useRef(propertyId);
    currentPropertyId.current = propertyId;

    useEffect(() => {
        setForecast([]);
        setRain(null);
        setWeatherError("");
        if (!property?.latitude || !property.longitude) return;
        const controller = new AbortController();
        const query = `latitude=${encodeURIComponent(property.latitude)}&longitude=${encodeURIComponent(property.longitude)}`;
        setWeatherLoading(true);
        Promise.allSettled([
            api<ForecastResponse>(`/api/weather/forecast?${query}&days=7`, { signal: controller.signal }),
            api<RainReading[]>("/api/hydro/rain/24h", { signal: controller.signal }),
        ]).then(([weatherResult, rainResult]) => {
            if (controller.signal.aborted) return;
            if (weatherResult.status === "rejected") {
                setWeatherError(propertyErrorMessage(weatherResult.reason, "Não foi possível consultar a previsão desta propriedade."));
                return;
            }
            setForecast(weatherResult.value.forecast.forecastday ?? []);
            if (rainResult.status === "rejected") return;
            const nearest = rainResult.value
                .filter((item) => item.latitude != null && item.longitude != null)
                .map((item) => ({ item, distance: distanceKm(property.latitude!, property.longitude!, item.latitude!, item.longitude!) }))
                .filter(({ distance }) => distance <= 50)
                .sort((a, b) => a.distance - b.distance)[0];
            setRain(nearest ? { ...nearest.item, nome: `${nearest.item.nome} · ${formatKm(nearest.distance)}` } : null);
        }).finally(() => {
            if (!controller.signal.aborted) setWeatherLoading(false);
        });
        return () => controller.abort();
    }, [property?.propertyId, property?.latitude, property?.longitude]);

    const loadActivities = useCallback((signal?: AbortSignal) => {
        if (!propertyId) return Promise.resolve();
        const requestedPropertyId = propertyId;
        setActivityLoading(true);
        setActivityError("");
        return listPropertyActivities(propertyId, signal)
            .then((result) => { if (!signal?.aborted && currentPropertyId.current === requestedPropertyId) setActivities(result); })
            .catch((reason: unknown) => { if (!signal?.aborted && currentPropertyId.current === requestedPropertyId) setActivityError(propertyErrorMessage(reason, "Não foi possível carregar o caderno de campo.")); })
            .finally(() => { if (!signal?.aborted && currentPropertyId.current === requestedPropertyId) setActivityLoading(false); });
    }, [propertyId]);

    useEffect(() => {
        setActivities([]);
        if (!propertyId) return;
        const controller = new AbortController();
        void loadActivities(controller.signal);
        const refresh = (event: Event) => { if ((event as CustomEvent<string>).detail === propertyId) void loadActivities(controller.signal); };
        window.addEventListener("sentinela:property-activities-changed", refresh);
        return () => { controller.abort(); window.removeEventListener("sentinela:property-activities-changed", refresh); };
    }, [propertyId, loadActivities]);

    const forecastRainTotal = useMemo(() => forecast.reduce((sum, day) => sum + (day.day?.totalprecip_mm ?? 0), 0), [forecast]);

    const submitActivity = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!propertyId || !activityNotes.trim()) return;
        const submittedPropertyId = propertyId;
        setSavingActivity(true);
        setActivityError("");
        try {
            await createPropertyActivity(propertyId, {
                type: activityType,
                date: activityDate,
                time: activityTime || null,
                notes: activityNotes.trim(),
                amountMm: activityType === "IRRIGATION" && amountMm !== "" ? Number(amountMm) : null,
                plotId: null,
            });
            if (currentPropertyId.current === submittedPropertyId) {
                setActivityNotes("");
                setActivityTime("");
                setAmountMm("");
                await loadActivities();
            }
        } catch (reason) {
            if (currentPropertyId.current === submittedPropertyId) setActivityError(propertyErrorMessage(reason, "Não foi possível salvar o registro."));
        } finally {
            if (currentPropertyId.current === submittedPropertyId) setSavingActivity(false);
        }
    };

    const removeActivity = async (activityId: string) => {
        if (!propertyId) return;
        const submittedPropertyId = propertyId;
        try {
            await deletePropertyActivity(propertyId, activityId);
            if (currentPropertyId.current === submittedPropertyId) setActivities((rows) => rows.filter((row) => row.activityId !== activityId));
        } catch (reason) {
            if (currentPropertyId.current === submittedPropertyId) setActivityError(propertyErrorMessage(reason, "Não foi possível excluir o registro."));
        }
    };

    if (!property) return null;

    return (
        <div className="grid gap-4 lg:grid-cols-2">
            <PropertyOperations property={property} observedRain={rain} forecast={forecast} inspectionSuggestion={inspectionSuggestion} />
            <PropertySeasonNotebook property={property} />
            <section className="rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5 lg:col-span-2" aria-label="Resumo da propriedade">
                <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                        <h2 className="text-lg font-semibold text-primary">{property.name}{property.plotName ? ` · ${property.plotName}` : ""}</h2>
                        <p className="mt-1 text-sm text-tertiary">{property.city} · {property.state} · {property.areaHectares?.toLocaleString("pt-BR", { maximumFractionDigits: 2 }) ?? "Área não informada"} ha</p>
                    </div>
                    <span className="hidden text-xs text-tertiary print:block">Relatório gerado em {new Date().toLocaleDateString("pt-BR")}</span>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                    <div><dt className="text-xs text-tertiary">Cultura</dt><dd className="mt-1 font-medium text-primary">{property.cropName || "Não informada"}</dd></div>
                    <div><dt className="text-xs text-tertiary">Talhão</dt><dd className="mt-1 font-medium text-primary">{property.plotName || "Não informado"}</dd></div>
                    <div><dt className="text-xs text-tertiary">Plantio</dt><dd className="mt-1 font-medium text-primary">{property.plantingDate ? formatDate(property.plantingDate) : "Não informado"}</dd></div>
                    <div><dt className="text-xs text-tertiary">Fase</dt><dd className="mt-1 font-medium text-primary">{property.cropStage || "Não informada"}</dd></div>
                </dl>
            </section>
            <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-weather-title">
                <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                        <h2 id="property-weather-title" className="text-lg font-semibold text-primary">Clima da propriedade</h2>
                        <p className="mt-1 text-sm text-tertiary">Previsão para o centro da área e chuva observada em estação próxima.</p>
                    </div>
                    <Button size="sm" color="secondary" onClick={() => window.print()} className="print:hidden">Imprimir resumo</Button>
                </div>
                {!property.geometry ? <p className="mt-4 text-sm text-tertiary">Delimite a propriedade para consultar o clima da área.</p> : weatherLoading ?
                    <p className="mt-4 text-sm text-tertiary">Consultando clima e estações próximas…</p> : weatherError ?
                    <FormAlert>{weatherError}</FormAlert> : (
                        <>
                            <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                <div className="rounded-xl border border-secondary p-4">
                                    <p className="text-xs font-medium text-tertiary">Chuva observada nas últimas 24 h</p>
                                    <p className="mt-2 text-2xl font-semibold text-primary tabular-nums">{rain ? `${rain.chuvaMm24h.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mm` : "Sem estação próxima"}</p>
                                    <p className="mt-1 text-xs text-tertiary">{rain ? `${rain.nome} · leitura ${formatDate(rain.dataUltimoDado)}` : "Não há estação pluviométrica com leitura em até 50 km."}</p>
                                </div>
                                <div className="rounded-xl border border-secondary p-4">
                                    <p className="text-xs font-medium text-tertiary">Chuva prevista · próximos 7 dias</p>
                                    <p className="mt-2 text-2xl font-semibold text-primary tabular-nums">{forecastRainTotal.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mm</p>
                                    <p className="mt-1 text-xs text-tertiary">Estimativa agregada da previsão diária.</p>
                                </div>
                            </div>
                            {forecast.length > 0 && <div className="mt-4 overflow-x-auto">
                                <table className="w-full min-w-[420px] text-left text-sm">
                                    <thead><tr className="border-b border-secondary text-xs text-tertiary"><th className="py-2 pr-3 font-medium">Dia</th><th className="py-2 pr-3 font-medium">Mín. / Máx.</th><th className="py-2 pr-3 font-medium">Chuva</th><th className="py-2 font-medium">Chance</th></tr></thead>
                                    <tbody>{forecast.map((day) => <tr className="border-b border-secondary last:border-0" key={day.date}>
                                        <td className="py-2.5 pr-3 text-secondary">{formatDate(day.date)}</td>
                                        <td className="py-2.5 pr-3 text-secondary">{Math.round(day.day.mintemp_c)}° / {Math.round(day.day.maxtemp_c)}°C</td>
                                        <td className="py-2.5 pr-3 text-secondary">{day.day.totalprecip_mm.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mm</td>
                                        <td className="py-2.5 text-secondary">{day.day.daily_chance_of_rain}%</td>
                                    </tr>)}</tbody>
                                </table>
                            </div>}
                            <p className="mt-3 text-xs leading-5 text-tertiary">A chuva observada vem da estação ANA/SNIRH mais próxima, se estiver até 50 km. Ela pode não representar toda a propriedade. A previsão é fornecida pela WeatherAPI.</p>
                        </>
                    )}
            </section>

            <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-log-title">
                <h2 id="property-log-title" className="text-lg font-semibold text-primary">Caderno de campo</h2>
                <p className="mt-1 text-sm text-tertiary">Registre irrigação, vistorias e ocorrências desta área.</p>
                {property.accessRole !== "VIEWER" && <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submitActivity(event)}>
                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">Tipo
                        <select className={fieldClass} value={activityType} onChange={(event) => setActivityType(event.target.value as PropertyActivityType)}>
                            <option value="IRRIGATION">Irrigação</option><option value="INSPECTION">Vistoria</option><option value="INCIDENT">Ocorrência</option>
                        </select>
                    </label>
                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">Data
                        <input required type="date" max={today()} className={fieldClass} value={activityDate} onChange={(event) => setActivityDate(event.target.value)} />
                    </label>
                    <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">Horário (opcional)
                        <input type="time" className={fieldClass} value={activityTime} onChange={(event) => setActivityTime(event.target.value)} />
                    </label>
                    {activityType === "IRRIGATION" && <label className="flex flex-col gap-1.5 text-xs font-medium text-secondary">Lâmina aplicada (mm)
                        <input type="number" min="0" step="0.1" className={fieldClass} value={amountMm} onChange={(event) => setAmountMm(event.target.value)} placeholder="Opcional" />
                    </label>}
                    <label className={`flex flex-col gap-1.5 text-xs font-medium text-secondary ${activityType === "IRRIGATION" ? "sm:col-span-2" : "sm:col-span-2"}`}>Descrição
                        <textarea required maxLength={1000} rows={2} className={fieldClass} value={activityNotes} onChange={(event) => setActivityNotes(event.target.value)} placeholder="O que foi feito ou observado?" />
                    </label>
                    <div className="sm:col-span-2"><Button type="submit" size="sm" isLoading={savingActivity}>Salvar registro</Button></div>
                </form>}
                {activityError && <FormAlert>{activityError}</FormAlert>}
                <div className="mt-4 border-t border-secondary pt-3">
                    <h3 className="text-sm font-semibold text-primary">Registros recentes</h3>
                    {activityLoading ? <p className="mt-3 text-sm text-tertiary">Carregando registros…</p> : activities.length === 0 ? <p className="mt-3 text-sm text-tertiary">Nenhum registro para esta propriedade.</p> :
                        <ul className="mt-2 divide-y divide-secondary">{activities.map((activity) => <li key={activity.activityId} className="flex items-start justify-between gap-3 py-3">
                            <div className="min-w-0"><p className="text-xs font-medium text-tertiary">{activityLabels[activity.type]} · {formatDate(activity.date)}{activity.time ? ` · ${activity.time.slice(0, 5)}` : ""}{activity.amountMm != null ? ` · ${activity.amountMm} mm` : ""}</p><p className="mt-1 whitespace-pre-wrap text-sm text-secondary">{activity.notes}</p></div>
                            {property.accessRole === "ADMIN" && <button type="button" className="shrink-0 text-xs text-tertiary hover:text-error-primary print:hidden" onClick={() => void removeActivity(activity.activityId)} aria-label="Excluir registro">Excluir</button>}
                        </li>)}</ul>}
                </div>
            </section>
        </div>
    );
};
