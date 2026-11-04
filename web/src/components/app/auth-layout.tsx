import type { ReactNode } from "react";
import { lazy, Suspense } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link } from "react-router";
import { LanguageSwitcher } from "./language-switcher";
import { Logo } from "./logo";
import { ThemeToggle } from "./theme-toggle";

// O mapa (MapLibre/WebGL) vem em um pacote separado: o formulario aparece na hora.
const BrazilMap = lazy(() => import("./brazil-map/brazil-map").then((m) => ({ default: m.BrazilMap })));

interface AuthLayoutProps {
    title: ReactNode;
    subtitle?: ReactNode;
    /** Conteudo fixo logo abaixo do titulo (ex.: abas Entrar/Criar conta). */
    header?: ReactNode;
    children: ReactNode;
    /** Mostra "Ao continuar, voce aceita os Termos e a Politica" no rodape do card. */
    showLegalNotice?: boolean;
}

export const LegalNotice = () => (
    <p className="text-center text-xs text-tertiary">
        <Trans
            i18nKey="auth.legalNotice"
            components={{
                terms: <Link to="/terms" className="font-medium text-secondary underline underline-offset-3 hover:text-primary" />,
                privacy: <Link to="/privacy" className="font-medium text-secondary underline underline-offset-3 hover:text-primary" />,
            }}
        />
    </p>
);

/**
 * Login, cadastro e recuperacao de senha: mapa a esquerda, card a direita.
 * O conteudo do card fica ancorado no topo (nao centralizado) para nada "pular" quando o formulario muda de altura.
 */
export const AuthLayout = ({ title, subtitle, header, children, showLegalNotice = true }: AuthLayoutProps) => {
    const { t } = useTranslation();

    return (
        <div className="min-h-dvh bg-secondary">
            {/*
              Grade 2x2 no desktop: [titulo | idioma+tema] em cima, [mapa | card] embaixo,
              assim o topo do mapa e o topo do card ficam alinhados. Gutters do site: 16/24/32px.
              No celular vira uma coluna: marca, card, titulo, mapa.
            */}
            <div className="mx-auto grid w-full max-w-[1920px] gap-x-8 gap-y-5 px-4 py-4 sm:px-6 lg:h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(400px,480px)] lg:grid-rows-[auto_minmax(0,1fr)] lg:gap-y-6 lg:px-8 lg:py-6 xl:grid-cols-[minmax(0,1fr)_minmax(440px,520px)]">
                <header className="order-1 flex items-center justify-between lg:col-start-2 lg:row-start-1 lg:items-start lg:justify-end">
                    <Logo className="lg:hidden" />
                    <div className="flex items-center gap-1">
                        <LanguageSwitcher />
                        <ThemeToggle />
                    </div>
                </header>

                <div className="order-3 lg:col-start-1 lg:row-start-1">
                    <h1 className="text-3xl font-semibold tracking-tight text-primary sm:text-4xl">{t("auth.hero.title")}</h1>
                    <p className="mt-2 max-w-2xl text-base leading-relaxed text-tertiary">{t("auth.hero.subtitle")}</p>
                </div>

                <section className="order-4 flex h-[560px] flex-col sm:h-[620px] lg:col-start-1 lg:row-start-2 lg:h-auto lg:min-h-0">
                    <Suspense fallback={<div className="flex-1 rounded-2xl ring-1 ring-secondary" />}>
                        <BrazilMap variant="hero" className="min-h-0 flex-1" />
                    </Suspense>
                </section>

                <section className="order-2 flex flex-col lg:col-start-2 lg:row-start-2 lg:min-h-0">
                    <div className="flex flex-1 flex-col rounded-[18px] bg-primary px-6 py-8 ring-1 ring-secondary sm:px-10 lg:overflow-y-auto lg:pt-[max(2.5rem,8vh)]">
                        <div className="mx-auto w-full max-w-sm">
                            <div className="text-center">
                                <h2 className="text-2xl font-semibold tracking-tight text-primary">{title}</h2>
                                {subtitle && <p className="mx-auto mt-2 max-w-xs text-sm leading-relaxed text-tertiary">{subtitle}</p>}
                            </div>
                            {header && <div className="mt-5">{header}</div>}
                            <div className="mt-5">{children}</div>
                        </div>

                        {showLegalNotice && (
                            <div className="mx-auto mt-8 w-full max-w-sm lg:mt-auto lg:pt-8">
                                <LegalNotice />
                            </div>
                        )}
                    </div>
                </section>
            </div>
        </div>
    );
};

/** Linha "ou" entre o botao do Google e o formulario. */
export const OrDivider = ({ label }: { label: string }) => (
    <div className="flex items-center gap-3" role="separator">
        <span className="h-px flex-1 bg-border-secondary" />
        <span className="text-xs text-quaternary">{label}</span>
        <span className="h-px flex-1 bg-border-secondary" />
    </div>
);
