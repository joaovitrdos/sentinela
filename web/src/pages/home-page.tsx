import { Suspense, lazy } from "react";
import { AlertCircle } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/base/buttons/button";
import { FormAlert } from "@/components/app/form-alert";
import { useAuth } from "@/providers/auth-provider";

// Mapa (MapLibre/WebGL + dados do SNIRH) em pacote separado, carregado so quando a pagina abre.
const HydroExplorer = lazy(() => import("@/components/app/hydro/hydro-explorer").then((m) => ({ default: m.HydroExplorer })));

export const HomePage = () => {
    const { t } = useTranslation();
    const { user } = useAuth();

    return (
        <div className="flex min-h-[calc(100dvh-4rem)] w-full flex-col gap-3 px-[15px] py-3">
            {user && !user.emailVerified && (
                <div className="flex flex-wrap items-center gap-3 rounded-xl bg-warning-primary px-4 py-3 text-sm text-warning-primary ring-1 ring-utility-yellow-200 ring-inset">
                    <FormAlert type="info">{t("home.verifyBanner")}</FormAlert>
                    <AlertCircle className="size-5 shrink-0" aria-hidden="true" />
                    <span className="flex-1">{t("home.verifyBanner")}</span>
                    <Button size="sm" color="secondary" href="/verify-email" routerOptions={{ state: { email: user.email } }}>
                        {t("auth.verify.submit")}
                    </Button>
                </div>
            )}

            <section className="flex flex-1 flex-col gap-3">
                <div className="sr-only">
                    <h2>{t("home.mapTitle")}</h2>
                    <p>{t("home.mapSubtitle")}</p>
                </div>
                <Suspense fallback={<div className="min-h-[520px] flex-1 rounded-2xl ring-1 ring-secondary" />}>
                    <HydroExplorer className="flex-1" mapClassName="h-[calc(100dvh-10rem)] min-h-[520px]" />
                </Suspense>
            </section>
        </div>
    );
};
