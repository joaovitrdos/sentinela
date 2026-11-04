import type { ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { useLocation, useOutlet } from "react-router";

// Animacoes feitas com Motion (JavaScript) e CSS: funcionam igual em Chrome, Edge, Firefox e Safari.
// Nao usa a View Transitions API, que ainda nao e uniforme entre navegadores.
const EASE = [0.22, 1, 0.36, 1] as const;

/** Entrada/saida de uma pagina: fade com deslocamento curto. */
export const PageFade = ({ children }: { children: ReactNode }) => (
    <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0, transition: { duration: 0.28, ease: EASE } }}
        exit={{ opacity: 0, y: -4, transition: { duration: 0.14, ease: "easeIn" } }}
    >
        {children}
    </motion.div>
);

/**
 * Conteudo das telas que compartilham o cabecalho (inicio, configuracoes, paginas legais...).
 * O cabecalho fica montado; so o conteudo troca, com animacao.
 */
export const AnimatedOutlet = () => {
    const location = useLocation();
    const outlet = useOutlet();

    return (
        <AnimatePresence mode="wait" initial={false} onExitComplete={() => window.scrollTo({ top: 0, behavior: "instant" })}>
            <PageFade key={location.pathname}>{outlet}</PageFade>
        </AnimatePresence>
    );
};

/** Agrupa rotas que compartilham a mesma moldura (nao refazem a animacao de pagina inteira entre si). */
export const routeGroup = (pathname: string) => {
    if (pathname === "/login" || pathname === "/register") return "auth";
    if (/^\/(home|properties|dashboard|reports|settings|privacy|terms)(\/|$)/.test(pathname)) return "shell";
    return pathname;
};
