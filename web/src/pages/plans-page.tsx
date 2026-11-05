import { useEffect, useState } from "react";
import { Check } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { PageLoader } from "@/components/app/route-guards";
import { SubscribeModal } from "@/components/app/subscribe-modal";
import { Tabs } from "@/components/application/tabs/tabs";
import { Badge } from "@/components/base/badges/badges";
import { Button } from "@/components/base/buttons/button";
import type { BillingCycle, Plan } from "@/lib/billing";
import { FEATURE_ORDER, billingApi, formatMoney } from "@/lib/billing";
import { errorKey } from "@/lib/errors";
import { useAuth } from "@/providers/auth-provider";
import { useBilling } from "@/providers/billing-provider";
import { cx } from "@/utils/cx";

const HIGHLIGHTED_PLAN = "PREMIUM";

/** Pública: visitantes veem os planos antes de criar a conta. Os valores vêm sempre do backend. */
export const PlansPage = () => {
    const { t } = useTranslation();
    const { isAuthenticated } = useAuth();
    const [plans, setPlans] = useState<Plan[]>();
    const [error, setError] = useState<string>();
    const [cycle, setCycle] = useState<BillingCycle>("MONTHLY");
    const [selected, setSelected] = useState<Plan | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        billingApi.plans(controller.signal).then(setPlans).catch((reason: unknown) => {
            if (!controller.signal.aborted) setError(t(errorKey(reason)));
        });
        return () => controller.abort();
    }, [t]);

    if (!plans && !error) return <PageLoader />;

    return (
        <div className="container-page py-8 lg:py-12">
            <header className="mx-auto max-w-2xl text-center">
                <h1 className="text-3xl font-semibold tracking-tight text-primary sm:text-4xl">{t("plans.title")}</h1>
                <p className="mt-3 text-base leading-relaxed text-tertiary">{t("plans.subtitle")}</p>
            </header>

            <div className="mx-auto mt-6 max-w-xs">
                <Tabs selectedKey={cycle} onSelectionChange={(key) => setCycle(key as BillingCycle)}>
                    <Tabs.List type="button-border" size="sm" fullWidth aria-label={t("plans.cycle.label")}>
                        <Tabs.Item id="MONTHLY" label={t("plans.cycle.monthly")} />
                        <Tabs.Item id="YEARLY" label={t("plans.cycle.yearly")} />
                    </Tabs.List>
                </Tabs>
            </div>

            <div className="mt-4">
                <FormAlert>{error}</FormAlert>
            </div>

            <div className="mt-6 grid gap-5 lg:grid-cols-3">
                {plans?.map((plan) => (
                    <PlanCard key={plan.code} plan={plan} plans={plans} cycle={cycle} onSelect={() => setSelected(plan)} />
                ))}
            </div>

            {!isAuthenticated ? (
                <p className="mt-8 text-center text-sm text-tertiary">
                    {t("plans.hasAccount")}{" "}
                    <Button color="link-color" size="sm" href="/login">
                        {t("plans.login")}
                    </Button>
                </p>
            ) : null}

            <SubscribeModal plan={selected} plans={plans ?? []} cycle={cycle} onClose={() => setSelected(null)} />
        </div>
    );
};

const PlanCard = ({ plan, plans, cycle, onSelect }: { plan: Plan; plans: Plan[]; cycle: BillingCycle; onSelect: () => void }) => {
    const { t, i18n } = useTranslation();
    const { isAuthenticated } = useAuth();
    const { status } = useBilling();
    const yearly = cycle === "YEARLY";
    const highlighted = plan.code === HIGHLIGHTED_PLAN;
    const current = status?.contractedPlan === plan.code && status.cycle === cycle && status.status !== "CANCELED" && !status.pendingPlan;
    const pending = status?.pendingPlan === plan.code;
    const contracted = plans.find((item) => item.code === status?.contractedPlan);
    const action = !contracted
        ? "plans.subscribe"
        : status?.status === "CANCELED"
          ? "plans.reactivate"
          : plan.monthlyPrice > contracted.monthlyPrice
            ? "plans.upgrade"
            : plan.monthlyPrice < contracted.monthlyPrice
              ? "plans.downgrade"
              : "plans.change";

    return (
        <section className={cx("flex flex-col rounded-[18px] bg-primary p-6 ring-1", highlighted ? "ring-2 ring-brand" : "ring-secondary")}>
            <div className="flex items-center justify-between gap-2">
                <h2 className="text-lg font-semibold text-primary">{plan.name}</h2>
                {highlighted ? (
                    <Badge size="sm" type="pill-color" color="brand">
                        {t("plans.complete")}
                    </Badge>
                ) : null}
            </div>

            <p className="mt-4">
                <span className="text-3xl font-semibold tracking-tight text-primary">
                    {formatMoney(yearly ? plan.yearlyPrice : plan.monthlyPrice, i18n.language)}
                </span>
                <span className="ml-1 text-sm text-tertiary">{t(yearly ? "plans.perYear" : "plans.perMonth")}</span>
            </p>
            <p className="mt-1 min-h-5 text-xs text-tertiary">
                {yearly ? t("plans.yearlyEquivalent", { value: formatMoney(plan.yearlyPrice / 12, i18n.language) }) : ""}
            </p>

            <div className="mt-5">
                {!isAuthenticated ? (
                    <Button size="sm" className="w-full" color={highlighted ? "primary" : "secondary"} href="/register">
                        {t("plans.startTrial")}
                    </Button>
                ) : current || pending ? (
                    <Button size="sm" className="w-full" color="secondary" isDisabled>
                        {t(pending ? "plans.pendingPlan" : "plans.currentPlan")}
                    </Button>
                ) : (
                    <Button size="sm" className="w-full" color={highlighted ? "primary" : "secondary"} onClick={onSelect}>
                        {t(action)}
                    </Button>
                )}
            </div>

            <ul className="mt-6 flex flex-col gap-2.5 text-sm text-secondary">
                <li className="flex items-start gap-2">
                    <Check className="mt-0.5 size-4 shrink-0 text-fg-brand-primary" aria-hidden />
                    {plan.maxProperties === null ? t("plans.unlimitedProperties") : t("plans.properties", { count: plan.maxProperties })}
                </li>
                {FEATURE_ORDER.filter((feature) => plan.features.includes(feature)).map((feature) => (
                    <li key={feature} className="flex items-start gap-2">
                        <Check className="mt-0.5 size-4 shrink-0 text-fg-brand-primary" aria-hidden />
                        {t(`plans.features.${feature}`)}
                    </li>
                ))}
            </ul>
        </section>
    );
};
