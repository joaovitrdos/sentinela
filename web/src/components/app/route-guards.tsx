import type { ReactNode } from "react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router";
import { LoadingIndicator } from "@/components/application/loading-indicator/loading-indicator";
import { useAuth } from "@/providers/auth-provider";

export const PageLoader = () => {
    const { t } = useTranslation();
    return (
        <div className="flex min-h-dvh items-center justify-center bg-primary">
            <LoadingIndicator type="line-spinner" size="md" label={t("common.loading")} />
        </div>
    );
};

/**
 * Redireciona uma unica vez (com replace).
 * O <Navigate> do react-router navega a cada render; durante a animacao de saida a tela antiga continua montada
 * e re-renderiza a cada navegacao, o que virava um loop ("Maximum update depth exceeded") no login e no logout.
 */
export const Redirect = ({ to, state }: { to: string; state?: unknown }) => {
    const navigate = useNavigate();
    const done = useRef(false);

    useEffect(() => {
        if (done.current) return;
        done.current = true;
        navigate(to, { replace: true, state });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return null;
};

/** So entra logado; senao vai para o login e volta depois. */
export const RequireAuth = ({ children }: { children: ReactNode }) => {
    const { isAuthenticated, isRestoring } = useAuth();
    const location = useLocation();

    if (isRestoring) return <PageLoader />;
    if (!isAuthenticated) return <Redirect to="/login" state={{ from: location.pathname + location.search }} />;
    return children;
};

/** Telas de login/cadastro: quem ja esta logado vai direto para o inicio. */
export const GuestOnly = ({ children }: { children: ReactNode }) => {
    const { isAuthenticated, isRestoring } = useAuth();
    const location = useLocation();
    if (isRestoring) return <PageLoader />;
    if (isAuthenticated) {
        const from = (location.state as { from?: unknown } | null)?.from;
        const returnTo = typeof from === "string" && from.startsWith("/") && !from.startsWith("//")
            ? from
            : "/home";
        return <Redirect to={returnTo} />;
    }
    return children;
};
