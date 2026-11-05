import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/base/buttons/button";
import { FormAlert } from "@/components/app/form-alert";
import { notify } from "@/components/app/notifications";
import { listProperties, loadFieldSettings, propertyErrorMessage, saveFieldSettings } from "@/lib/property-data";
import type { FieldSettings, SavedProperty } from "@/lib/property-data";

const field = "min-h-11 w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";

export function FieldSettingsCard() {
    const { t } = useTranslation();
    const [properties, setProperties] = useState<SavedProperty[]>([]);
    const [selectedId, setSelectedId] = useState("");
    const [settings, setSettings] = useState<FieldSettings | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        listProperties(controller.signal).then((rows) => {
            if (controller.signal.aborted) return;
            const editable = rows.filter((row) => row.accessRole === "ADMIN");
            setProperties(editable);
            setSelectedId((current) => editable.some((row) => row.propertyId === current) ? current : editable[0]?.propertyId ?? "");
        }).catch((reason: unknown) => {
            if (!controller.signal.aborted) setError(propertyErrorMessage(reason, t("settings.field.loadPropertiesError")));
        }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [t]);

    useEffect(() => {
        if (!selectedId) { setSettings(null); return; }
        const controller = new AbortController();
        setSettings(null); setError(""); setLoading(true);
        loadFieldSettings(selectedId, controller.signal).then((result) => {
            if (!controller.signal.aborted) setSettings(result);
        }).catch((reason: unknown) => {
            if (!controller.signal.aborted) setError(propertyErrorMessage(reason, t("settings.field.loadError")));
        }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [selectedId, t]);

    const save = async () => {
        if (!selectedId || !settings || saving) return;
        setSaving(true); setError("");
        try {
            setSettings(await saveFieldSettings(selectedId, settings));
            notify(t("settings.field.saved"), "success");
        } catch (reason) {
            const message = propertyErrorMessage(reason, t("settings.field.saveError"));
            setError(message);
            notify(message, "error");
        } finally { setSaving(false); }
    };

    return <section className="w-full min-w-0 overflow-hidden rounded-[18px] bg-primary ring-1 ring-secondary">
        <header className="px-5 pt-5 sm:px-6">
            <h2 className="text-sm font-semibold text-primary">{t("settings.field.title")}</h2>
            <p className="mt-0.5 text-sm text-tertiary">{t("settings.field.description")}</p>
        </header>
        <div className="grid gap-5 px-5 pt-4 pb-5 sm:px-6">
            {error && <FormAlert>{error}</FormAlert>}
            {loading && <p className="text-sm text-tertiary">{t("settings.field.loading")}</p>}
            {!loading && properties.length === 0 && !error && <p className="text-sm text-tertiary">{t("settings.field.empty")}</p>}
            {properties.length > 0 && <label className="grid gap-1.5 text-sm font-medium text-secondary">{t("settings.field.property")}
                <select className={field} value={selectedId} onChange={(event) => setSelectedId(event.target.value)} disabled={saving}>
                    {properties.map((property) => <option key={property.propertyId} value={property.propertyId}>{property.name}</option>)}
                </select>
            </label>}
            {settings && <div className="grid gap-5">
                <div className="rounded-xl border border-secondary p-4">
                    <h3 className="text-sm font-semibold text-primary">{t("settings.field.remindersTitle")}</h3>
                    <p className="mt-1 text-xs text-tertiary">{t("settings.field.remindersDescription")}</p>
                    <label className="mt-4 flex items-center gap-3 text-sm text-secondary"><input type="checkbox" checked={settings.actionReminderEnabled} onChange={(event) => setSettings({ ...settings, actionReminderEnabled: event.target.checked })} />{t("settings.field.emailReminders")}</label>
                    <label className="mt-4 grid gap-1.5 text-sm font-medium text-secondary">{t("settings.field.advance")}
                        <select className={field} value={settings.actionReminderDays} disabled={!settings.actionReminderEnabled} onChange={(event) => setSettings({ ...settings, actionReminderDays: Number(event.target.value) })}>
                            <option value={0}>{t("settings.field.sameDay")}</option><option value={1}>{t("settings.field.daysBefore", { count: 1 })}</option><option value={2}>{t("settings.field.daysBefore", { count: 2 })}</option><option value={3}>{t("settings.field.daysBefore", { count: 3 })}</option><option value={7}>{t("settings.field.daysBefore", { count: 7 })}</option>
                        </select>
                    </label>
                </div>
                <div className="rounded-xl border border-secondary p-4">
                    <h3 className="text-sm font-semibold text-primary">{t("settings.field.notebookTitle")}</h3>
                    <label className="mt-3 flex items-start gap-3 text-sm text-secondary"><input type="checkbox" className="mt-1" checked={settings.requireSeasonForFieldRecords} onChange={(event) => setSettings({ ...settings, requireSeasonForFieldRecords: event.target.checked })} /><span>{t("settings.field.requireSeason")}<span className="mt-1 block text-xs text-tertiary">{t("settings.field.requireSeasonDescription")}</span></span></label>
                    <label className="mt-4 grid gap-1.5 text-sm font-medium text-secondary">{t("settings.field.harvestUnit")}
                        <select className={field} value={settings.defaultHarvestUnit} onChange={(event) => setSettings({ ...settings, defaultHarvestUnit: event.target.value as FieldSettings["defaultHarvestUnit"] })}>
                            <option value="kg">{t("settings.field.kg")}</option><option value="sacas">{t("settings.field.bags")}</option><option value="toneladas">{t("settings.field.tonnes")}</option>
                        </select>
                    </label>
                    <p className="mt-2 text-xs text-tertiary">{t("settings.field.harvestUnitHint")}</p>
                </div>
            </div>}
        </div>
        {settings && <footer className="flex justify-end border-t border-secondary bg-secondary px-5 py-3 sm:px-6"><Button size="sm" isLoading={saving} onClick={() => void save()}>{t("settings.field.save")}</Button></footer>}
    </section>;
}
