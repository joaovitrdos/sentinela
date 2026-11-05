import { api } from "./api";

export type Feature = "BASE" | "REPORTS" | "SPRAY_WINDOW" | "NDVI" | "ALERTS" | "TEAM" | "IRRIGATION" | "EARLY_ACCESS";
export type BillingCycle = "MONTHLY" | "YEARLY";
export type PaymentMethod = "CARD" | "PIX" | "BOLETO";
export type SubscriptionStatus = "TRIAL" | "PENDING" | "ACTIVE" | "OVERDUE" | "CANCELED";

export interface Plan {
    code: string;
    name: string;
    monthlyPrice: number;
    yearlyPrice: number;
    maxProperties: number | null;
    features: Feature[];
}

/** {@code effectivePlan} nulo indica acesso somente leitura aos dados já cadastrados. */
export interface BillingStatus {
    status: SubscriptionStatus;
    effectivePlan: string | null;
    contractedPlan: string | null;
    pendingPlan: string | null;
    cycle: BillingCycle | null;
    trialEndsAt: string;
    paidUntil: string | null;
    maxProperties: number | null;
    features: Feature[];
    hasCustomer: boolean;
}

export interface SubscribeResult {
    planCode: string;
    cycle: BillingCycle;
    value: number;
    nextDueDate: string;
    invoiceUrl: string | null;
}

/** Ordem de exibição dos recursos nos cartões de plano. */
export const FEATURE_ORDER: Feature[] = ["BASE", "REPORTS", "SPRAY_WINDOW", "NDVI", "ALERTS", "TEAM", "IRRIGATION", "EARLY_ACCESS"];

export const billingApi = {
    plans: (signal?: AbortSignal) => api<Plan[]>("/api/plans", { signal }),
    status: (signal?: AbortSignal) => api<BillingStatus>("/api/billing/me", { signal }),
    subscribe: (body: { planCode: string; cycle: BillingCycle; paymentMethod: PaymentMethod; cpfCnpj?: string }) =>
        api<SubscribeResult>("/api/billing/subscribe", { method: "POST", body }),
    cancel: () => api("/api/billing/cancel", { method: "POST" }),
};

export const trialDaysLeft = (status: BillingStatus, now = Date.now()) =>
    Math.max(0, Math.ceil((new Date(status.trialEndsAt).getTime() - now) / 86_400_000));

export const isInTrial = (status: BillingStatus, now = Date.now()) => new Date(status.trialEndsAt).getTime() > now;

export const formatMoney = (value: number, language: string) =>
    new Intl.NumberFormat(language, { style: "currency", currency: "BRL" }).format(value);

export const formatDate = (value: string, language: string) => {
    // Data sem horário: evita voltar um dia por causa do fuso.
    const date = value.length === 10 ? new Date(`${value}T12:00:00`) : new Date(value);
    return new Intl.DateTimeFormat(language, { dateStyle: "short" }).format(date);
};
