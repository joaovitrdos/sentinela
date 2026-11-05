import type { ReactNode } from "react";
import { Lock01 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/base/buttons/button";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import type { Feature } from "@/lib/billing";
import { formatDate, isInTrial, trialDaysLeft } from "@/lib/billing";
import { useBilling } from "@/providers/billing-provider";

/** Troca a tela por um convite de upgrade quando o plano do usuário não inclui o recurso. */
export const PlanGate = ({ feature, children }: { feature: Feature; children: ReactNode }) => {
    const { t } = useTranslation();
    const { can } = useBilling();
    if (can(feature)) return <>{children}</>;

    return (
        <div className="container-page flex flex-col items-center gap-4 py-20 text-center">
            <FeaturedIcon icon={Lock01} color="brand" theme="light" size="lg" />
            <div className="max-w-md">
                <h1 className="text-xl font-semibold text-primary">{t("plans.gate.title")}</h1>
                <p className="mt-2 text-sm leading-6 text-tertiary">
                    {t("plans.gate.description", { feature: t(`plans.features.${feature}`) })}
                </p>
            </div>
            <Button size="sm" href="/plans">
                {t("plans.gate.action")}
            </Button>
        </div>
    );
};

/** Versão compacta para cartões: aparece quando o backend recusa o recurso pelo plano do dono da propriedade. */
export const PlanLocked = ({ feature }: { feature: Feature }) => {
    const { t } = useTranslation();
    return (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-secondary p-4">
            <p className="text-sm text-tertiary">{t("plans.gate.description", { feature: t(`plans.features.${feature}`) })}</p>
            <Button size="sm" href="/plans">
                {t("plans.gate.action")}
            </Button>
        </div>
    );
};

/** Faixa no topo do sistema durante o teste, com pagamento em atraso ou sem plano vigente. */
export const PlanBanner = () => {
    const { t, i18n } = useTranslation();
    const { status } = useBilling();
    if (!status) return null;

    const trial = isInTrial(status) && status.contractedPlan === null;
    const readOnly = status.effectivePlan === null;
    const overdue = status.status === "OVERDUE" && !readOnly;
    const ending = status.status === "CANCELED" && !readOnly && status.paidUntil !== null;
    if (!trial && !readOnly && !overdue && !ending) return null;

    const days = trialDaysLeft(status);
    const message = readOnly
        ? t("plans.banner.readOnly")
        : overdue
          ? t("plans.banner.overdue")
          : ending
            ? t("plans.banner.ending", { date: formatDate(status.paidUntil!, i18n.language) })
            : t("plans.banner.trial", { count: days });

    return (
        <div className={readOnly || overdue ? "bg-error-secondary" : "bg-brand-secondary"}>
            <div className="mx-auto flex w-full max-w-[1440px] flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-2 sm:px-6 lg:px-8">
                <p className="text-sm text-primary">{message}</p>
                <Button size="xs" color="link-color" href="/plans">
                    {t("plans.banner.action")}
                </Button>
            </div>
        </div>
    );
};
