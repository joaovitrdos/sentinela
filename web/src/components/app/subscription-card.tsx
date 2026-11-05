import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ConfirmationModal } from "@/components/app/confirmation-modal";
import { notify } from "@/components/app/notifications";
import { Badge } from "@/components/base/badges/badges";
import { Button } from "@/components/base/buttons/button";
import { FEATURE_ORDER, billingApi, formatDate, isInTrial, trialDaysLeft } from "@/lib/billing";
import { errorKey } from "@/lib/errors";
import { useBilling } from "@/providers/billing-provider";

const planName = (code: string | null, fallback: string) => (code ? code.charAt(0) + code.slice(1).toLowerCase() : fallback);

/** Mostra o plano em uso e leva para a troca; os preços ficam na página de planos. */
export const SubscriptionCard = () => {
    const { t, i18n } = useTranslation();
    const { status, refresh } = useBilling();
    const [confirming, setConfirming] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string>();

    if (!status) return <p className="text-sm text-tertiary">{t("common.loading")}</p>;

    const trial = isInTrial(status) && status.contractedPlan === null;
    const canCancel = status.contractedPlan !== null && status.status !== "CANCELED";
    const canceled = status.status === "CANCELED";
    // O teste continua valendo depois de cancelar: o acesso termina na data mais distante.
    const trialEnd = trial ? status.trialEndsAt.slice(0, 10) : null;
    const accessEnd = [status.paidUntil, trialEnd].filter((date): date is string => date !== null).sort().at(-1) ?? null;
    const accessEndLabel = accessEnd ? formatDate(accessEnd, i18n.language) : null;

    const cancel = async () => {
        setBusy(true);
        setError(undefined);
        try {
            await billingApi.cancel();
            await refresh();
            setConfirming(false);
            notify(accessEndLabel ? t("plans.settings.canceledUntil", { date: accessEndLabel }) : t("plans.settings.canceled"), "success");
        } catch (reason) {
            setError(t(errorKey(reason)));
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="w-full min-w-0 overflow-hidden rounded-[18px] bg-primary ring-1 ring-secondary">
            <header className="flex flex-wrap items-start justify-between gap-3 px-5 pt-5 sm:px-6">
                <div>
                    <h2 className="text-sm font-semibold text-primary">{t("plans.settings.title")}</h2>
                    <p className="mt-1 text-2xl font-semibold tracking-tight text-primary">
                        {planName(status.effectivePlan, t("plans.settings.noPlan"))}
                    </p>
                </div>
                <Badge size="sm" type="pill-color" color={status.status === "ACTIVE" || trial ? "success" : status.status === "PENDING" ? "warning" : "error"}>
                    {t(`plans.status.${trial && status.status === "TRIAL" ? "TRIAL" : status.status}`)}
                </Badge>
            </header>

            <div className="flex flex-col gap-1.5 px-5 pt-3 pb-5 text-sm text-tertiary sm:px-6">
                {trial ? <p>{t("plans.settings.trial", { count: trialDaysLeft(status), date: formatDate(status.trialEndsAt, i18n.language) })}</p> : null}
                {status.contractedPlan ? (
                    <p>
                        {t("plans.settings.contracted", { plan: planName(status.contractedPlan, ""), cycle: t(status.cycle === "YEARLY" ? "plans.cycle.yearly" : "plans.cycle.monthly") })}
                    </p>
                ) : null}
                {status.paidUntil ? <p>{t(status.status === "CANCELED" ? "plans.settings.endsAt" : "plans.settings.paidUntil", { date: formatDate(status.paidUntil, i18n.language) })}</p> : null}
                {status.pendingPlan ? <p>{t("plans.settings.pending", { plan: planName(status.pendingPlan, "") })}</p> : null}
                {status.effectivePlan === null ? <p>{t("plans.banner.readOnly")}</p> : null}

                {status.features.length > 0 ? (
                    <ul className="mt-3 grid gap-1.5 text-secondary sm:grid-cols-2">
                        <li>{status.maxProperties === null ? t("plans.unlimitedProperties") : t("plans.properties", { count: status.maxProperties })}</li>
                        {FEATURE_ORDER.filter((feature) => status.features.includes(feature)).map((feature) => (
                            <li key={feature}>{t(`plans.features.${feature}`)}</li>
                        ))}
                    </ul>
                ) : null}
            </div>

            <footer className="flex flex-wrap items-center justify-end gap-3 border-t border-secondary bg-secondary px-5 py-3 sm:px-6">
                {canCancel ? (
                    <Button size="sm" color="link-destructive" onClick={() => setConfirming(true)}>
                        {t("plans.settings.cancel")}
                    </Button>
                ) : null}
                <Button size="sm" href="/plans">
                    {t(canceled ? "plans.settings.reactivate" : status.contractedPlan ? "plans.settings.change" : "plans.settings.choose")}
                </Button>
            </footer>

            <ConfirmationModal
                open={confirming}
                title={t("plans.settings.cancelTitle")}
                description={accessEndLabel ? t("plans.settings.cancelDescriptionUntil", { date: accessEndLabel }) : t("plans.settings.cancelDescription")}
                confirmLabel={t("plans.settings.cancelConfirm")}
                cancelLabel={t("plans.settings.keep")}
                onConfirm={() => void cancel()}
                onCancel={() => setConfirming(false)}
                busy={busy}
                error={error}
            />
        </section>
    );
};
