import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FormAlert } from "@/components/app/form-alert";
import { fieldProps } from "@/components/app/form-styles";
import { notify } from "@/components/app/notifications";
import { Dialog, Modal, ModalOverlay } from "@/components/application/modals/modal";
import { Button } from "@/components/base/buttons/button";
import { Input } from "@/components/base/input/input";
import type { BillingCycle, PaymentMethod, Plan } from "@/lib/billing";
import { billingApi, formatDate, formatMoney } from "@/lib/billing";
import { errorKey } from "@/lib/errors";
import { useBilling } from "@/providers/billing-provider";
import { cx } from "@/utils/cx";

const onlyDigits = (value: string) => value.replace(/\D/g, "");
const METHODS: PaymentMethod[] = ["CARD", "PIX", "BOLETO"];

/** Os dados de pagamento ficam nas telas do Asaas; aqui só vai o CPF ou CNPJ de quem paga com Pix ou boleto. */
export const SubscribeModal = ({ plan, plans, cycle, onClose }: { plan: Plan | null; plans: Plan[]; cycle: BillingCycle; onClose: () => void }) => {
    const { t, i18n } = useTranslation();
    const { status, refresh } = useBilling();
    const [method, setMethod] = useState<PaymentMethod>("CARD");
    const [document, setDocument] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string>();

    useEffect(() => {
        setDocument("");
        setError(undefined);
    }, [plan]);

    const needsDocument = method !== "CARD" && !status?.hasCustomer;
    const digits = onlyDigits(document);
    const documentValid = !needsDocument || digits.length === 11 || digits.length === 14;
    const price = plan ? (cycle === "YEARLY" ? plan.yearlyPrice : plan.monthlyPrice) : 0;
    const changingPaidPlan = status?.status === "ACTIVE" && status.contractedPlan !== null;
    const contracted = plans.find((item) => item.code === status?.contractedPlan);
    const lostFeatures = plan && contracted ? contracted.features.filter((feature) => !plan.features.includes(feature)) : [];

    const submit = async () => {
        if (!plan) return;
        setBusy(true);
        setError(undefined);
        try {
            const result = await billingApi.subscribe({ planCode: plan.code, cycle, paymentMethod: method, cpfCnpj: needsDocument ? digits : undefined });
            await refresh();
            if (result.invoiceUrl) {
                window.location.assign(result.invoiceUrl);
                return;
            }
            notify(t("plans.modal.noInvoice", { date: formatDate(result.nextDueDate, i18n.language) }), "success");
            onClose();
        } catch (reason) {
            setError(t(errorKey(reason)));
        } finally {
            setBusy(false);
        }
    };

    return (
        <ModalOverlay isOpen={plan !== null} onOpenChange={(open) => { if (!open && !busy) onClose(); }} isDismissable={!busy}>
            <Modal className="max-w-md">
                <Dialog aria-label={t("plans.modal.title")}>
                    <div className="flex w-full flex-col gap-4 p-6">
                        <div>
                            <h3 className="text-md font-semibold text-primary">
                                {t("plans.modal.title")}: {plan?.name}
                            </h3>
                            <p className="mt-1 text-sm leading-6 text-tertiary">
                                {formatMoney(price, i18n.language)} {t(cycle === "YEARLY" ? "plans.perYear" : "plans.perMonth")}
                            </p>
                        </div>
                        <div role="radiogroup" aria-label={t("plans.modal.method")} className="grid grid-cols-3 gap-2">
                            {METHODS.map((item) => (
                                <button
                                    key={item}
                                    type="button"
                                    role="radio"
                                    aria-checked={method === item}
                                    disabled={busy}
                                    onClick={() => setMethod(item)}
                                    className={cx(
                                        "cursor-pointer rounded-lg px-3 py-2 text-sm font-medium ring-1 transition duration-100 ease-linear ring-inset disabled:cursor-not-allowed disabled:opacity-50",
                                        method === item ? "bg-brand-secondary text-primary ring-brand" : "text-secondary ring-primary",
                                    )}
                                >
                                    {t(`plans.modal.methods.${item}`)}
                                </button>
                            ))}
                        </div>
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
                        <p className="text-sm leading-6 text-tertiary">
                            {t(`plans.modal.notes.${method}`)}{changingPaidPlan ? ` ${t("plans.modal.changeNote")}` : ""}
                        </p>
                        {lostFeatures.length > 0 ? (
                            <p className="text-sm leading-6 text-warning-primary">
                                {t("plans.modal.downgradeNote", { features: lostFeatures.map((feature) => t(`plans.features.${feature}`)).join(", ") })}
                            </p>
                        ) : null}
                        <FormAlert>{error}</FormAlert>
                        <div className="mt-1 grid grid-cols-2 gap-3">
                            <Button size="sm" color="secondary" onClick={onClose} isDisabled={busy}>
                                {t("common.cancel")}
                            </Button>
                            <Button size="sm" onClick={() => void submit()} isDisabled={!documentValid} isLoading={busy} showTextWhileLoading>
                                {t("plans.modal.confirm")}
                            </Button>
                        </div>
                    </div>
                </Dialog>
            </Modal>
        </ModalOverlay>
    );
};
