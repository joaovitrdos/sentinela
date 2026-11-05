import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { notify } from "@/components/app/notifications";
import { PlanLocked } from "@/components/app/plan-gate";
import { WeatherCredit } from "@/components/app/weather-credit";
import { Badge } from "@/components/base/badges/badges";
import { Button } from "@/components/base/buttons/button";
import { Toggle } from "@/components/base/toggle/toggle";
import type { AlertSettings, PropertyAlert } from "@/lib/advisory";
import { advisoryApi, isPlanBlocked } from "@/lib/advisory";
import type { SavedProperty } from "@/lib/property-data";
import { propertyErrorMessage } from "@/lib/property-data";

const field = "w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2 disabled:opacity-50";

type NumericSetting = Exclude<keyof AlertSettings, "enabled">;
const LIMITS: { key: NumericSetting; min: number; max: number; step: number }[] = [
    { key: "frostTemperatureC", min: -5, max: 10, step: 0.5 },
    { key: "heavyRainMm", min: 10, max: 300, step: 5 },
    { key: "ndviDrop", min: 0.05, max: 0.8, step: 0.05 },
    { key: "riverRadiusKm", min: 1, max: 200, step: 1 },
];

export const PropertyAlerts = ({ property }: { property: SavedProperty }) => {
    const { t, i18n } = useTranslation();
    const [alerts, setAlerts] = useState<PropertyAlert[]>([]);
    const [settings, setSettings] = useState<AlertSettings | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [locked, setLocked] = useState(false);
    const [error, setError] = useState("");
    const canEdit = property.accessRole === "ADMIN";

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setLocked(false);
        setError("");
        setSettings(null);
        Promise.all([advisoryApi.alerts(property.propertyId, controller.signal), advisoryApi.alertSettings(property.propertyId, controller.signal)])
            .then(([items, current]) => {
                setAlerts(items);
                setSettings(current);
            })
            .catch((reason: unknown) => {
                if (controller.signal.aborted) return;
                if (isPlanBlocked(reason)) setLocked(true);
                else setError(propertyErrorMessage(reason, t("advisor.alerts.loadError")));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [property.propertyId, t]);

    const save = async () => {
        if (!settings) return;
        setSaving(true);
        setError("");
        try {
            setSettings(await advisoryApi.saveAlertSettings(property.propertyId, settings));
            notify(t("advisor.alerts.saved"), "success");
        } catch (reason) {
            setError(propertyErrorMessage(reason, t("advisor.alerts.saveError")));
        } finally {
            setSaving(false);
        }
    };

    const hasWeatherAlert = alerts.some((alert) => alert.disclaimer);

    return (
        <section className="min-w-0 rounded-2xl bg-primary p-4 ring-1 ring-secondary sm:p-5" aria-labelledby="property-alerts-title">
            <h2 id="property-alerts-title" className="text-lg font-semibold text-primary">{t("advisor.alerts.title")}</h2>
            <p className="mt-1 text-sm text-tertiary">{t("advisor.alerts.subtitle")}</p>

            {locked ? <PlanLocked feature="ALERTS" /> : null}
            {loading ? <p className="mt-4 text-sm text-tertiary">{t("common.loading")}</p> : null}
            {error ? <div className="mt-4"><FormAlert>{error}</FormAlert></div> : null}

            {settings ? (
                <div className="mt-4 grid gap-5 lg:grid-cols-[minmax(0,20rem)_minmax(0,1fr)]">
                    <div className="flex flex-col gap-3 rounded-xl border border-secondary p-4">
                        <Toggle
                            size="sm"
                            label={t("advisor.alerts.enabled")}
                            hint={t("advisor.alerts.enabledHint")}
                            isSelected={settings.enabled}
                            isDisabled={!canEdit || saving}
                            onChange={(enabled) => setSettings({ ...settings, enabled })}
                        />
                        {LIMITS.map(({ key, min, max, step }) => (
                            <label key={key} className="flex flex-col gap-1.5 text-xs font-medium text-secondary">
                                {t(`advisor.alerts.${key}`)}
                                <input
                                    type="number"
                                    className={field}
                                    min={min}
                                    max={max}
                                    step={step}
                                    value={settings[key]}
                                    disabled={!canEdit || saving}
                                    onChange={(event) => setSettings({ ...settings, [key]: Number(event.target.value) })}
                                />
                            </label>
                        ))}
                        {canEdit ? (
                            <Button size="sm" onClick={() => void save()} isLoading={saving} showTextWhileLoading>
                                {t("advisor.alerts.save")}
                            </Button>
                        ) : (
                            <p className="text-xs text-tertiary">{t("advisor.alerts.adminOnly")}</p>
                        )}
                    </div>

                    <div className="min-w-0">
                        <h3 className="text-sm font-semibold text-primary">{t("advisor.alerts.history")}</h3>
                        {alerts.length === 0 ? (
                            <p className="mt-3 text-sm text-tertiary">{t(settings.enabled ? "advisor.alerts.empty" : "advisor.alerts.emptyDisabled")}</p>
                        ) : (
                            <ul className="mt-2 divide-y divide-secondary">
                                {alerts.map((alert) => (
                                    <li key={alert.alertId} className="py-3">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <Badge size="sm" type="pill-color" color={alert.severity === "PERIGO" ? "error" : "warning"}>
                                                {t(`advisor.alerts.severity.${alert.severity}`)}
                                            </Badge>
                                            <p className="text-sm font-medium text-primary">{alert.title}</p>
                                        </div>
                                        <p className="mt-1 text-sm leading-6 text-tertiary">{alert.message}</p>
                                        <p className="mt-0.5 text-xs text-quaternary">
                                            {new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "short" }).format(new Date(alert.createdAt))}
                                        </p>
                                    </li>
                                ))}
                            </ul>
                        )}
                        {hasWeatherAlert ? <WeatherCredit className="mt-3" /> : null}
                    </div>
                </div>
            ) : null}
        </section>
    );
};
