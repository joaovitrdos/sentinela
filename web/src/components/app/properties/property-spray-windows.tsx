import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { PlanLocked } from "@/components/app/plan-gate";
import type { SprayHour, SprayWindows } from "@/lib/advisory";
import { advisoryApi, isPlanBlocked } from "@/lib/advisory";
import type { SavedProperty } from "@/lib/property-data";
import { propertyErrorMessage } from "@/lib/property-data";
import { cx } from "@/utils/cx";

const dayOf = (time: string) => time.slice(0, 10);
const hourOf = (time: string) => time.slice(11, 16);

export const PropertySprayWindows = ({ property }: { property: SavedProperty }) => {
    const { t, i18n } = useTranslation();
    const [data, setData] = useState<SprayWindows | null>(null);
    const [loading, setLoading] = useState(true);
    const [locked, setLocked] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setLocked(false);
        setError("");
        setData(null);
        advisoryApi.sprayWindows(property.propertyId, controller.signal)
            .then(setData)
            .catch((reason: unknown) => {
                if (controller.signal.aborted) return;
                if (isPlanBlocked(reason)) setLocked(true);
                else setError(propertyErrorMessage(reason, t("advisor.spray.loadError")));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [property.propertyId, t]);

    const days = useMemo(() => {
        const grouped = new Map<string, SprayHour[]>();
        for (const hour of data?.hours ?? []) grouped.set(dayOf(hour.time), [...(grouped.get(dayOf(hour.time)) ?? []), hour]);
        return [...grouped.entries()];
    }, [data]);

    const dayLabel = (day: string) =>
        new Intl.DateTimeFormat(i18n.language, { weekday: "short", day: "2-digit", month: "2-digit" }).format(new Date(`${day}T12:00:00`));

    return (
        <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-spray-title">
            <h2 id="property-spray-title" className="text-lg font-semibold text-primary">{t("advisor.spray.title")}</h2>
            <p className="mt-1 text-sm text-tertiary">{t("advisor.spray.subtitle")}</p>

            {locked ? <PlanLocked feature="SPRAY_WINDOW" /> : null}
            {loading ? <p className="mt-4 text-sm text-tertiary">{t("common.loading")}</p> : null}
            {error ? <div className="mt-4"><FormAlert>{error}</FormAlert></div> : null}

            {data ? (
                <>
                    <h3 className="mt-4 text-sm font-semibold text-primary">{t("advisor.spray.best")}</h3>
                    {data.windows.length === 0 ? (
                        <p className="mt-2 text-sm text-tertiary">{t("advisor.spray.none")}</p>
                    ) : (
                        <ul className="mt-2 flex flex-wrap gap-2">
                            {[...data.windows].sort((a, b) => b.hours - a.hours).slice(0, 6).sort((a, b) => a.from.localeCompare(b.from)).map((window) => (
                                <li key={window.from} className="rounded-lg bg-success-primary px-3 py-1.5 text-sm text-success-primary">
                                    {dayLabel(dayOf(window.from))}, {hourOf(window.from)} {t("advisor.spray.hours", { count: window.hours })}
                                </li>
                            ))}
                        </ul>
                    )}

                    <div className="mt-4 flex flex-col gap-2">
                        {days.map(([day, hours]) => (
                            <div key={day} className="flex items-center gap-3">
                                <span className="w-20 shrink-0 text-xs text-tertiary">{dayLabel(day)}</span>
                                <div className="flex min-w-0 flex-1 gap-px">
                                    {hours.map((hour) => (
                                        <span
                                            key={hour.time}
                                            title={`${hourOf(hour.time)} · ${hour.temperatureC}°C · ${hour.humidity}% · ${hour.windKph} km/h · ${hour.rainChance}%${hour.reasons.length ? ` — ${hour.reasons.join("; ")}` : ""}`}
                                            className={cx("h-6 min-w-0 flex-1 rounded-[3px]", hour.suitable ? "bg-success-solid" : "bg-tertiary")}
                                        />
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                    <p className="mt-2 text-xs text-tertiary">{t("advisor.spray.legend")}</p>
                    <p className="mt-3 text-[11px] leading-4 text-quaternary">
                        <span className="font-medium">{t("advisor.source", { source: data.source })}</span> {data.disclaimer}
                    </p>
                </>
            ) : null}
        </section>
    );
};
