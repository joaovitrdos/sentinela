import type { ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { BillingStatus, Feature } from "@/lib/billing";
import { billingApi } from "@/lib/billing";
import { useAuth } from "@/providers/auth-provider";

interface BillingContextType {
    status: BillingStatus | null;
    refresh: () => Promise<void>;
    /** Só para exibição: quem decide de verdade é o backend, em cada requisição. */
    can: (feature: Feature) => boolean;
}

const BillingContext = createContext<BillingContextType | null>(null);

export const BillingProvider = ({ children }: { children: ReactNode }) => {
    const { isAuthenticated } = useAuth();
    const [status, setStatus] = useState<BillingStatus | null>(null);

    const refresh = useCallback(async () => {
        if (!isAuthenticated) {
            setStatus(null);
            return;
        }
        try {
            setStatus(await billingApi.status());
        } catch {
            // Sem o status, a tela não bloqueia nada e o backend continua decidindo.
        }
    }, [isAuthenticated]);

    useEffect(() => {
        void refresh();
        // O pagamento acontece na fatura do Asaas: ao voltar para a aba, o plano pode ter mudado.
        const onFocus = () => void refresh();
        window.addEventListener("focus", onFocus);
        return () => window.removeEventListener("focus", onFocus);
    }, [refresh]);

    const value = useMemo<BillingContextType>(
        () => ({ status, refresh, can: (feature) => status === null || status.features.includes(feature) }),
        [status, refresh],
    );

    return <BillingContext.Provider value={value}>{children}</BillingContext.Provider>;
};

export const useBilling = (): BillingContextType => {
    const context = useContext(BillingContext);
    if (!context) throw new Error("useBilling must be used within a BillingProvider");
    return context;
};
