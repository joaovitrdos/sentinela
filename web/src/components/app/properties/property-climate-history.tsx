import { useEffect, useMemo, useState } from "react";
import type { TFunction } from "i18next";
import { useTranslation } from "react-i18next";
import type { TooltipContentProps } from "recharts";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { FormAlert } from "@/components/app/form-alert";
import { PlanLocked } from "@/components/app/plan-gate";
import type { ClimateHistory, ClimateMonth, ClimatePeriod } from "@/lib/advisory";
import { advisoryApi, isPlanBlocked } from "@/lib/advisory";
import type { SavedProperty } from "@/lib/property-data";
import { propertyErrorMessage } from "@/lib/property-data";
import { cx } from "@/utils/cx";

const AXIS_TICK = { fill: "var(--color-text-tertiary)", fontSize: 11 };
const COLORS = { rain: "var(--color-utility-blue-500)", normal: "var(--color-utility-neutral-400)" };
/** Diferença pequena demais para chamar de "acima" ou "abaixo" da média. */
const EVEN_PERCENT = 5;

interface ChartRow {
    label: string;
    rain: number;
    normal: number;
    complete: boolean;
    driestRank: number;
    yearsCompared: number;
}

/** Mostra o lado mais extremo da série: "3º mais seco" ou, se estiver na metade chuvosa, "2º mais chuvoso". */
const rankingText = (t: TFunction, period: Pick<ClimatePeriod, "driestRank" | "yearsCompared">) => {
    const wettestRank = period.yearsCompared - period.driestRank + 1;
    return period.driestRank <= wettestRank
        ? t("advisor.climate.driest", { rank: period.driestRank, years: period.yearsCompared })
        : t("advisor.climate.wettest", { rank: wettestRank, years: period.yearsCompared });
};

const Anomaly = ({ value }: { value: number | null }) => {
    const { t, i18n } = useTranslation();
    if (value === null) return null;
    const number = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 0 }).format(Math.abs(value));
    if (Math.abs(value) < EVEN_PERCENT) return <span className="text-tertiary">{t("advisor.climate.even")}</span>;
    return (
        <span className={cx("font-semibold", value < 0 ? "text-warning-primary" : "text-brand-secondary")}>
            {t(value < 0 ? "advisor.climate.below" : "advisor.climate.above", { value: number })}
        </span>
    );
};

function MonthTooltip({ active, payload }: TooltipContentProps) {
    const { t, i18n } = useTranslation();
    if (!active || !payload.length) return null;
    const row = payload[0].payload as ChartRow;
    const number = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 });
    return (
        <div className="rounded-xl border border-secondary bg-primary p-3 text-xs text-secondary shadow-lg">
            <p className="font-semibold text-primary">
                {row.label}
                {row.complete ? "" : ` (${t("advisor.climate.partial")})`}
            </p>
            <p className="mt-1">{t("advisor.climate.rain", { value: number.format(row.rain) })}</p>
            <p className="text-tertiary">{t("advisor.climate.normal", { value: number.format(row.normal) })}</p>
            <p className="mt-1 font-medium text-secondary">{rankingText(t, row)}</p>
        </div>
    );
}

export const PropertyClimateHistory = ({ property }: { property: SavedProperty }) => {
    const { t, i18n } = useTranslation();
    const [data, setData] = useState<ClimateHistory | null>(null);
    const [loading, setLoading] = useState(true);
    const [locked, setLocked] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setLocked(false);
        setError("");
        setData(null);
        advisoryApi
            .climateHistory(property.propertyId, controller.signal)
            .then(setData)
            .catch((reason: unknown) => {
                if (controller.signal.aborted) return;
                if (isPlanBlocked(reason)) setLocked(true);
                else setError(propertyErrorMessage(reason, t("advisor.climate.loadError")));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [property.propertyId, t]);

    const number = useMemo(() => new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }), [i18n.language]);
    const monthLabel = useMemo(() => new Intl.DateTimeFormat(i18n.language, { month: "short", year: "2-digit" }), [i18n.language]);

    const rows = useMemo<ChartRow[]>(
        () =>
            (data?.recentMonths ?? []).map((month: ClimateMonth) => ({
                label: monthLabel.format(new Date(`${month.month}-15T12:00:00`)),
                rain: month.rainMm,
                normal: month.normalRainMm,
                complete: month.complete,
                driestRank: month.driestRank,
                yearsCompared: month.yearsCompared,
            })),
        [data, monthLabel],
    );

    const firstYear = data ? Number(data.firstDate.slice(0, 4)) : 1981;
    const lastDate = data ? new Intl.DateTimeFormat(i18n.language, { dateStyle: "short" }).format(new Date(`${data.lastDate}T12:00:00`)) : "";
    const lastMonth = data?.recentMonths.at(-1);
    const lastMonthNormals = lastMonth ? data?.normals.find((normal) => normal.month === Number(lastMonth.month.slice(5, 7))) : undefined;

    return (
        <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-climate-title">
            <h2 id="property-climate-title" className="text-lg font-semibold text-primary">
                {t("advisor.climate.title", { year: firstYear })}
            </h2>
            <p className="mt-1 text-sm text-tertiary">{t("advisor.climate.subtitle", { year: firstYear })}</p>

            {locked ? <PlanLocked feature="BASE" /> : null}
            {loading ? <p className="mt-4 text-sm text-tertiary">{t("common.loading")}</p> : null}
            {error ? (
                <div className="mt-4">
                    <FormAlert>{error}</FormAlert>
                </div>
            ) : null}

            {data ? (
                <div className="mt-4 grid min-w-0 gap-4 lg:grid-cols-[minmax(0,18rem)_minmax(0,1fr)]">
                    {data.last30Days ? (
                        <div className="rounded-xl bg-secondary p-4">
                            <h3 className="text-sm font-semibold text-secondary">{t("advisor.climate.last30")}</h3>
                            <p className="mt-2 text-display-xs font-semibold text-primary tabular-nums">
                                {t("advisor.climate.rain", { value: number.format(data.last30Days.rainMm) })}
                            </p>
                            <p className="mt-1 text-sm">
                                <Anomaly value={data.last30Days.anomalyPercent} />
                            </p>
                            <p className="mt-1 text-sm text-tertiary">
                                {t("advisor.climate.normal", { value: number.format(data.last30Days.normalRainMm) })}
                            </p>
                            <p className="mt-3 text-sm font-medium text-secondary">{rankingText(t, data.last30Days)}</p>
                        </div>
                    ) : null}

                    <div className="min-w-0">
                        <h3 className="text-sm font-semibold text-secondary">{t("advisor.climate.monthsTitle")}</h3>
                        <div className="mt-2 h-64 min-w-0" role="group" aria-label={t("advisor.climate.monthsTitle")}>
                            <ResponsiveContainer width="100%" height="100%" minWidth={0} debounce={100}>
                                <BarChart data={rows} margin={{ top: 8, right: 4, bottom: 0, left: 0 }} barGap={2}>
                                    <CartesianGrid stroke="var(--color-border-secondary)" strokeDasharray="3 3" vertical={false} />
                                    <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={false} minTickGap={8} tickMargin={8} />
                                    <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={40} tickFormatter={(value) => number.format(Number(value))} />
                                    <Tooltip content={MonthTooltip} cursor={{ fill: "var(--color-bg-secondary)" }} />
                                    <Bar dataKey="normal" name={t("advisor.climate.seriesNormal")} fill={COLORS.normal} radius={[3, 3, 0, 0]} />
                                    <Bar dataKey="rain" name={t("advisor.climate.seriesRain")} fill={COLORS.rain} radius={[3, 3, 0, 0]} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-tertiary">
                            <span className="inline-flex items-center gap-1.5">
                                <span aria-hidden="true" className="size-2 rounded-full" style={{ backgroundColor: COLORS.rain }} />
                                {t("advisor.climate.seriesRain")}
                            </span>
                            <span className="inline-flex items-center gap-1.5">
                                <span aria-hidden="true" className="size-2 rounded-full" style={{ backgroundColor: COLORS.normal }} />
                                {t("advisor.climate.seriesNormal")}
                            </span>
                            {lastMonthNormals ? (
                                <span>
                                    {lastMonth ? `${monthLabel.format(new Date(`${lastMonth.month}-15T12:00:00`))}: ` : ""}
                                    {t("advisor.climate.extremes", {
                                        min: number.format(lastMonthNormals.minRainMm),
                                        minYear: lastMonthNormals.minYear,
                                        max: number.format(lastMonthNormals.maxRainMm),
                                        maxYear: lastMonthNormals.maxYear,
                                    })}
                                </span>
                            ) : null}
                        </div>
                    </div>

                    <p className="text-[11px] leading-4 text-quaternary lg:col-span-2">
                        {t("advisor.climate.lastDate", { date: lastDate })}{" "}
                        {data.sources.map((source) => (
                            <span key={source.url}>
                                {t("advisor.climate.source")}{" "}
                                <a href={source.url} target="_blank" rel="noreferrer" className="underline">
                                    {source.name}
                                </a>{" "}
                                ({source.license}).
                            </span>
                        ))}
                    </p>
                </div>
            ) : null}
        </section>
    );
};
