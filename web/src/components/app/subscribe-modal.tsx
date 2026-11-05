import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { fieldProps } from "@/components/app/form-styles";
import { notify } from "@/components/app/notifications";
import { Dialog, Modal, ModalOverlay } from "@/components/application/modals/modal";
import { Button } from "@/components/base/buttons/button";
import { Input } from "@/components/base/input/input";
import type { BillingCycle, Plan } from "@/lib/billing";
import { billingApi, formatDate } from "@/lib/billing";
import { errorKey } from "@/lib/errors";
import { useBilling } from "@/providers/billing-provider";

const onlyDigits = (value: string) => value.replace(/\D/g, "");

/** A fatura abre em outra aba: ao voltar para o Sentinela, o foco na janela atualiza o plano. */
export const useSubscribe = () => {
    const { t, i18n } = useTranslation();
    const { refresh } = useBilling();
    const [busy, setBusy] = useState<string | null>(null);
    const [error, setError] = useState<string>();

    const subscribe = async (plan: Plan, cycle: BillingCycle, cpfCnpj?: string) => {
        setBusy(plan.code);
        setError(undefined);
        // Aberta antes da chamada para o navegador não tratar como pop-up.
        const invoiceTab = window.open("about:blank", "_blank");
        try {
            const result = await billingApi.subscribe({ planCode: plan.code, cycle, cpfCnpj });
            await refresh();
            if (result.invoiceUrl && invoiceTab) invoiceTab.location.href = result.invoiceUrl;
            else if (result.invoiceUrl) window.location.assign(result.invoiceUrl);
            else {
                invoiceTab?.close();
                notify(t("plans.modal.reactivated", { date: formatDate(result.nextDueDate, i18n.language) }), "success");
            }
            return true;
        } catch (reason) {
            invoiceTab?.close();
            setError(t(errorKey(reason)));
            return false;
        } finally {
            setBusy(null);
        }
    };

    return { subscribe, busy, error };
};

/** Só aparece quando há algo a dizer antes da fatura: o CPF ou CNPJ da primeira vez ou o que se perde no downgrade. */
export const SubscribeModal = ({ plan, plans, cycle, onClose }: { plan: Plan | null; plans: Plan[]; cycle: BillingCycle; onClose: () => void }) => {
    const { t } = useTranslation();
    const { status } = useBilling();
    const { subscribe, busy, error } = useSubscribe();
    const [document, setDocument] = useState("");

    useEffect(() => setDocument(""), [plan]);

    const needsDocument = !status?.hasCustomer;
    const digits = onlyDigits(document);
    const valid = !needsDocument || digits.length === 11 || digits.length === 14;
    const contracted = plans.find((item) => item.code === status?.contractedPlan);
    const lostFeatures = plan && contracted ? contracted.features.filter((feature) => !plan.features.includes(feature)) : [];

    const submit = async () => {
        if (plan && (await subscribe(plan, cycle, needsDocument ? digits : undefined))) onClose();
    };

    return (
        <ModalOverlay isOpen={plan !== null} onOpenChange={(open) => { if (!open && !busy) onClose(); }} isDismissable={!busy}>
            <Modal className="max-w-md">
                <Dialog aria-label={t("plans.modal.title")}>
                    <div className="flex w-full flex-col gap-4 p-6">
                        <h3 className="text-md font-semibold text-primary">
                            {t("plans.modal.title")}: {plan?.name}
                        </h3>
                        {needsDocument ? (
                            <Input
                                label={t("plans.modal.document")}
                                hint={t("plans.modal.documentHint")}
                                inputMode="numeric"
                                autoComplete="off"
                                {...fieldProps}
                                value={document}
                                onChange={setDocument}
                            />
                        ) : null}
                        {lostFeatures.length > 0 ? (
                            <p className="text-sm leading-6 text-warning-primary">
                                {t("plans.modal.downgradeNote", { features: lostFeatures.map((feature) => t(`plans.features.${feature}`)).join(", ") })}
                            </p>
                        ) : null}
                        <FormAlert>{error}</FormAlert>
                        <div className="mt-1 grid grid-cols-2 gap-3">
                            <Button size="sm" color="secondary" onClick={onClose} isDisabled={busy !== null}>
                                {t("common.cancel")}
                            </Button>
                            <Button size="sm" onClick={() => void submit()} isDisabled={!valid} isLoading={busy !== null} showTextWhileLoading>
                                {t("plans.modal.confirm")}
                            </Button>
                        </div>
                    </div>
                </Dialog>
            </Modal>
        </ModalOverlay>
    );
};
