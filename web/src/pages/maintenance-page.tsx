import { RefreshCw01 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { StatusScreen } from "@/components/app/status-screen";
import { FormAlert } from "@/components/app/form-alert";
import { Button } from "@/components/base/buttons/button";
import { useSystem } from "@/providers/system-provider";

export const MaintenancePage = () => {
    const { t, i18n } = useTranslation();
    const { status, refresh, checking, failed } = useSystem();
    const date = (value: string) =>
        new Intl.DateTimeFormat(i18n.language, {
            dateStyle: "medium",
            timeStyle: "short",
            timeZone: status?.timeZone ?? "America/Sao_Paulo",
        }).format(new Date(value));

    return (
        <StatusScreen code="503" label={t("maintenance.code")} title={t("maintenance.title")} description={t("maintenance.subtitle")}>
            <FormAlert type="info">{failed ? t("maintenance.checkFailed") : null}</FormAlert>
            <dl className="mb-6 space-y-3 rounded-2xl border border-secondary bg-secondary/50 p-4 text-sm">
                {status?.maintenanceStart && (
                    <div>
                        <dt className="text-xs text-quaternary">{t("maintenance.startedAt")}</dt>
                        <dd className="mt-1 font-medium text-secondary">{date(status.maintenanceStart)}</dd>
                    </div>
                )}
                <div>
                    <dt className="text-xs text-quaternary">{t("maintenance.expectedEnd")}</dt>
                    <dd className="mt-1 font-medium text-secondary">{t("maintenance.noEstimate")}</dd>
                </div>
                {status?.version && (
                    <div>
                        <dt className="text-xs text-quaternary">{t("maintenance.version")}</dt>
                        <dd className="mt-1 font-medium text-secondary">{status.version}</dd>
                    </div>
                )}
            </dl>
            <Button
                size="md"
                iconLeading={RefreshCw01}
                isLoading={checking}
                onPress={() => {
                    void refresh();
                }}
            >
                {t("maintenance.retry")}
            </Button>
            <p aria-live="polite" className="mt-4 text-xs leading-relaxed text-tertiary">
                {failed ? t("maintenance.checkFailed") : t("maintenance.autoRefresh")}
            </p>
        </StatusScreen>
    );
};
