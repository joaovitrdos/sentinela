import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { PlanLocked } from "@/components/app/plan-gate";
import type { PlantingWindow, PlantingWindowOption } from "@/lib/advisory";
import { advisoryApi, isPlanBlocked } from "@/lib/advisory";
import type { SavedProperty } from "@/lib/property-data";
import { propertyErrorMessage } from "@/lib/property-data";
import { cx } from "@/utils/cx";

const field = "w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";
const RISK_CLASS: Record<number, string> = {
    0: "bg-tertiary",
    20: "bg-success-solid",
    30: "bg-warning-solid",
    40: "bg-error-solid",
};
const MONTHS = Array.from({ length: 12 }, (_, index) => index);

const unique = (values: number[]) => [...new Set(values)];

export const PropertyPlantingWindow = ({ property }: { property: SavedProperty }) => {
    const { t, i18n } = useTranslation();
    const [crop, setCrop] = useState<string | null>(null);
    const [data, setData] = useState<PlantingWindow | null>(null);
    const [loading, setLoading] = useState(true);
    const [locked, setLocked] = useState(false);
    const [error, setError] = useState("");
    const [management, setManagement] = useState<number | null>(null);
    const [cycle, setCycle] = useState<number | null>(null);
    const [soil, setSoil] = useState<number | null>(null);

    useEffect(() => setCrop(null), [property.propertyId]);

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setLocked(false);
        setError("");
        advisoryApi
            .plantingWindow(property.propertyId, crop, controller.signal)
            .then((result) => {
                setData(result);
                setManagement(null);
                setCycle(null);
                setSoil(null);
            })
            .catch((reason: unknown) => {
                if (controller.signal.aborted) return;
                setData(null);
                if (isPlanBlocked(reason)) setLocked(true);
                else setError(propertyErrorMessage(reason, t("advisor.planting.loadError")));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [property.propertyId, crop, t]);

    // Cada filtro só mostra o que existe dentro dos filtros anteriores, para nunca cair numa combinação vazia.
    const options = data?.options ?? [];
    const managements = unique(options.map((option) => option.managementCode));
    const selectedManagement = management ?? managements[0];
    const byManagement = options.filter((option) => option.managementCode === selectedManagement);
    const cycles = unique(byManagement.map((option) => option.cycleCode)).sort((a, b) => a - b);
    const selectedCycle = cycle !== null && cycles.includes(cycle) ? cycle : cycles[0];
    const byCycle = byManagement.filter((option) => option.cycleCode === selectedCycle);
    const soils = unique(byCycle.map((option) => option.soilCode)).sort((a, b) => a - b);
    const selectedSoil = soil !== null && soils.includes(soil) ? soil : soils[0];
    const selected: PlantingWindowOption | undefined = byCycle.find((option) => option.soilCode === selectedSoil);

    const date = useMemo(() => new Intl.DateTimeFormat(i18n.language, { day: "2-digit", month: "2-digit", year: "numeric" }), [i18n.language]);
    const month = useMemo(() => new Intl.DateTimeFormat(i18n.language, { month: "short" }), [i18n.language]);
    const formatDate = (value: string) => date.format(new Date(`${value}T12:00:00`));
    const managementName = (code: number) => options.find((option) => option.managementCode === code)?.managementName ?? String(code);
    const label = (group: "cycles" | "soils", code: number) => t(`advisor.planting.${group}.${code}`, { defaultValue: String(code) });

    return (
        <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-planting-title">
            <h2 id="property-planting-title" className="text-lg font-semibold text-primary">
                {t("advisor.planting.title")}
            </h2>
            <p className="mt-1 text-sm text-tertiary">{t("advisor.planting.subtitle")}</p>

            {locked ? <PlanLocked feature="PLANTING_WINDOW" /> : null}
            {loading && !data ? <p className="mt-4 text-sm text-tertiary">{t("common.loading")}</p> : null}
            {error ? (
                <div className="mt-4">
                    <FormAlert>{error}</FormAlert>
                </div>
            ) : null}
            {data?.status === "NOT_IMPORTED" ? <p className="mt-4 text-sm text-tertiary">{t("advisor.planting.notImported")}</p> : null}
            {data?.status === "MUNICIPALITY_NOT_FOUND" ? (
                <p className="mt-4 text-sm text-tertiary">{t("advisor.planting.notFound", { city: property.city, uf: data.uf ?? property.state })}</p>
            ) : null}

            {data?.status === "OK" ? (
                <>
                    <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                        <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-secondary">
                            {t("advisor.planting.crop")}
                            <select className={field} value={data.crop ?? ""} onChange={(event) => setCrop(event.target.value)} disabled={loading}>
                                {data.crops.map((name) => (
                                    <option key={name} value={name}>
                                        {name}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-secondary">
                            {t("advisor.planting.management")}
                            <select className={field} value={selectedManagement} onChange={(event) => setManagement(Number(event.target.value))}>
                                {managements.map((code) => (
                                    <option key={code} value={code}>
                                        {managementName(code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-secondary">
                            {t("advisor.planting.cycle")}
                            <select className={field} value={selectedCycle} onChange={(event) => setCycle(Number(event.target.value))}>
                                {cycles.map((code) => (
                                    <option key={code} value={code}>
                                        {label("cycles", code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-secondary">
                            {t("advisor.planting.soil")}
                            <select className={field} value={selectedSoil} onChange={(event) => setSoil(Number(event.target.value))}>
                                {soils.map((code) => (
                                    <option key={code} value={code}>
                                        {label("soils", code)}
                                    </option>
                                ))}
                            </select>
                        </label>
                    </div>

                    {selected ? (
                        <>
                            <p
                                className={cx(
                                    "mt-4 rounded-xl px-4 py-3 text-sm font-medium",
                                    selected.openNow ? "bg-success-primary text-success-primary" : "bg-secondary text-secondary",
                                )}
                            >
                                {selected.window
                                    ? t(selected.openNow ? "advisor.planting.openNow" : "advisor.planting.opensOn", {
                                          from: formatDate(selected.window.from),
                                          to: formatDate(selected.window.to),
                                          risk: selected.window.bestRiskPercent,
                                      })
                                    : t("advisor.planting.noWindow")}
                            </p>

                            <div className="mt-4 overflow-x-auto">
                                <div className="grid min-w-[36rem] grid-cols-12 gap-1" role="img" aria-label={t("advisor.planting.riskLegend")}>
                                    {MONTHS.map((monthIndex) => (
                                        <div key={monthIndex} className="flex flex-col gap-1">
                                            <div className="flex gap-px">
                                                {[0, 1, 2].map((part) => {
                                                    const decendio = monthIndex * 3 + part + 1;
                                                    const risk = selected.risks[decendio - 1] ?? 0;
                                                    const today = decendio === data.currentDecendio;
                                                    return (
                                                        <span
                                                            key={part}
                                                            title={risk ? `${risk}%` : t("advisor.planting.outside")}
                                                            className={cx(
                                                                "h-7 min-w-0 flex-1 rounded-[3px]",
                                                                RISK_CLASS[risk] ?? "bg-tertiary",
                                                                today && "ring-2 ring-fg-primary ring-offset-1 ring-offset-bg-primary",
                                                            )}
                                                        />
                                                    );
                                                })}
                                            </div>
                                            <span className="text-center text-[11px] text-tertiary capitalize">
                                                {month.format(new Date(2026, monthIndex, 15))}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-tertiary">
                                <span className="font-medium text-secondary">{t("advisor.planting.riskLegend")}:</span>
                                {[20, 30, 40].map((risk) => (
                                    <span key={risk} className="inline-flex items-center gap-1.5">
                                        <span aria-hidden="true" className={cx("size-2.5 rounded-sm", RISK_CLASS[risk])} />
                                        {risk}%
                                    </span>
                                ))}
                                <span className="inline-flex items-center gap-1.5">
                                    <span aria-hidden="true" className={cx("size-2.5 rounded-sm ring-1 ring-primary", RISK_CLASS[0])} />
                                    {t("advisor.planting.outside")}
                                </span>
                                <span className="inline-flex items-center gap-1.5">
                                    <span aria-hidden="true" className="size-2.5 rounded-sm ring-2 ring-fg-primary" />
                                    {t("advisor.planting.today")}
                                </span>
                            </div>
                        </>
                    ) : null}

                    <p className="mt-3 text-[11px] leading-4 text-quaternary">
                        {t("advisor.planting.disclaimer")} {selected?.ordinance ? t("advisor.planting.ordinance", { value: selected.ordinance }) : null}{" "}
                        {data.sources.map((source) => (
                            <span key={source.url}>
                                {t("advisor.planting.source")}{" "}
                                <a href={source.url} target="_blank" rel="noreferrer" className="underline">
                                    {source.name}
                                </a>{" "}
                                ({source.license}), {data.municipality}/{data.uf}.
                            </span>
                        ))}
                    </p>
                </>
            ) : null}
        </section>
    );
};
