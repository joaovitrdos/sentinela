import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { PlanLocked } from "@/components/app/plan-gate";
import type { IrrigationRecommendation } from "@/lib/advisory";
import { advisoryApi, isPlanBlocked } from "@/lib/advisory";
import type { Plot, SavedProperty } from "@/lib/property-data";
import { listPlots, propertyErrorMessage } from "@/lib/property-data";

const field = "w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";

export const PropertyIrrigation = ({ property }: { property: SavedProperty }) => {
    const { t, i18n } = useTranslation();
    const [plots, setPlots] = useState<Plot[] | null>(null);
    const [plotId, setPlotId] = useState("");
    const [data, setData] = useState<IrrigationRecommendation | null>(null);
    const [loading, setLoading] = useState(false);
    const [locked, setLocked] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        setPlots(null);
        setPlotId("");
        setData(null);
        setLocked(false);
        setError("");
        listPlots(property.propertyId, controller.signal)
            .then((rows) => {
                setPlots(rows);
                setPlotId(rows[0]?.plotId ?? "");
            })
            .catch((reason: unknown) => {
                if (!controller.signal.aborted) setError(propertyErrorMessage(reason, t("advisor.irrigation.loadError")));
            });
        return () => controller.abort();
    }, [property.propertyId, t]);

    useEffect(() => {
        if (!plotId) return;
        const controller = new AbortController();
        setLoading(true);
        setData(null);
        setError("");
        advisoryApi.irrigation(property.propertyId, plotId, controller.signal)
            .then(setData)
            .catch((reason: unknown) => {
                if (controller.signal.aborted) return;
                if (isPlanBlocked(reason)) setLocked(true);
                else setError(propertyErrorMessage(reason, t("advisor.irrigation.loadError")));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [property.propertyId, plotId, t]);

    const number = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 });
    const date = (value: string) => new Intl.DateTimeFormat(i18n.language, { day: "2-digit", month: "2-digit" }).format(new Date(`${value}T12:00:00`));

    return (
        <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-irrigation-title">
            <h2 id="property-irrigation-title" className="text-lg font-semibold text-primary">{t("advisor.irrigation.title")}</h2>
            <p className="mt-1 text-sm text-tertiary">{t("advisor.irrigation.subtitle")}</p>

            {locked ? <PlanLocked feature="IRRIGATION" /> : null}
            {error ? <div className="mt-4"><FormAlert>{error}</FormAlert></div> : null}
            {plots?.length === 0 ? <p className="mt-4 text-sm text-tertiary">{t("advisor.irrigation.noPlots")}</p> : null}

            {!locked && plots && plots.length > 0 ? (
                <label className="mt-4 flex max-w-xs flex-col gap-1.5 text-xs font-medium text-secondary">
                    {t("advisor.irrigation.plot")}
                    <select className={field} value={plotId} onChange={(event) => setPlotId(event.target.value)}>
                        {plots.map((plot) => (
                            <option key={plot.plotId} value={plot.plotId}>{plot.name}</option>
                        ))}
                    </select>
                </label>
            ) : null}

            {loading ? <p className="mt-4 text-sm text-tertiary">{t("common.loading")}</p> : null}

            {data ? (
                <>
                    <div className="mt-4 grid gap-3 sm:grid-cols-2">
                        <div className="rounded-xl border border-secondary p-4">
                            <p className="text-xs font-medium text-tertiary">{t("advisor.irrigation.recommended")}</p>
                            <p className="mt-2 text-2xl font-semibold text-primary tabular-nums">{number.format(data.recommendedMm)} mm</p>
                            <p className="mt-1 text-xs text-tertiary">{t("advisor.irrigation.recommendedHint")}</p>
                        </div>
                        <div className="rounded-xl border border-secondary p-4">
                            <p className="text-xs font-medium text-tertiary">{t("advisor.irrigation.crop")}</p>
                            <p className="mt-2 text-2xl font-semibold text-primary">{data.cropName ?? t("advisor.irrigation.noCrop")}</p>
                            <p className="mt-1 text-xs text-tertiary">Kc {number.format(data.cropCoefficient)}</p>
                        </div>
                    </div>

                    {data.warnings.length > 0 ? (
                        <ul className="mt-3 flex flex-col gap-1 text-sm text-warning-primary">
                            {data.warnings.map((warning) => <li key={warning}>{warning}</li>)}
                        </ul>
                    ) : null}

                    <div className="mt-4 overflow-x-auto">
                        <table className="w-full min-w-[520px] text-left text-sm">
                            <thead>
                                <tr className="border-b border-secondary text-xs text-tertiary">
                                    <th className="py-2 pr-3 font-medium">{t("advisor.irrigation.day")}</th>
                                    <th className="py-2 pr-3 font-medium">{t("advisor.irrigation.demand")}</th>
                                    <th className="py-2 pr-3 font-medium">{t("advisor.irrigation.rain")}</th>
                                    <th className="py-2 pr-3 font-medium">{t("advisor.irrigation.applied")}</th>
                                    <th className="py-2 font-medium">{t("advisor.irrigation.deficit")}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {data.days.map((day) => (
                                    <tr key={day.date} className="border-b border-secondary last:border-0">
                                        <td className="py-2 pr-3 text-secondary">
                                            {date(day.date)} {day.forecast ? <span className="text-xs text-tertiary">({t("advisor.irrigation.forecast")})</span> : null}
                                        </td>
                                        <td className="py-2 pr-3 text-secondary tabular-nums">{number.format(day.cropDemandMm)} mm</td>
                                        <td className="py-2 pr-3 text-secondary tabular-nums">{number.format(day.rainMm)} mm</td>
                                        <td className="py-2 pr-3 text-secondary tabular-nums">{number.format(day.irrigationMm)} mm</td>
                                        <td className="py-2 text-secondary tabular-nums">{number.format(day.deficitMm)} mm</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <p className="mt-3 text-[11px] leading-4 text-quaternary">
                        <span className="font-medium">{t("advisor.source", { source: data.source })}</span> {data.method}. {data.disclaimer}
                    </p>
                </>
            ) : null}
        </section>
    );
};
