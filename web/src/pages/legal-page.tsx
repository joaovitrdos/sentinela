import type { MouseEvent } from "react";
import { useEffect, useState } from "react";
import { ArrowRight, FileShield02, File06 } from "@untitledui/icons";
import { motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import { cx } from "@/utils/cx";

type Doc = "privacy" | "terms";

interface LegalSection {
    id: string;
    title: string;
    paragraphs?: string[];
    items?: string[];
}

const COMPANY = import.meta.env.VITE_LEGAL_COMPANY || "Sentinela";
const CONTACT = import.meta.env.VITE_LEGAL_CONTACT_EMAIL || "privacidade@sentinela.com.br";

/** Indice "Nesta pagina": acompanha a secao que esta na tela e rola suave ao clicar. */
const TableOfContents = ({ label, sections, doc }: { label: string; sections: LegalSection[]; doc: Doc }) => {
    const [active, setActive] = useState(sections[0]?.id);
    const ids = sections.map((s) => s.id).join();

    useEffect(() => {
        const observer = new IntersectionObserver(
            (entries) => {
                const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
                if (visible[0]) setActive(visible[0].target.id);
            },
            // Conta como "atual" a secao que esta na faixa logo abaixo do cabecalho fixo.
            { rootMargin: "-96px 0px -60% 0px" },
        );
        ids.split(",").forEach((id) => {
            const el = document.getElementById(id);
            if (el) observer.observe(el);
        });
        return () => observer.disconnect();
    }, [ids]);

    const goTo = (event: MouseEvent<HTMLAnchorElement>, id: string) => {
        event.preventDefault();
        setActive(id);
        document.getElementById(id)?.scrollIntoView({ block: "start" });
        window.history.replaceState(window.history.state, "", `#${id}`);
    };

    return (
        <nav aria-label={label} className="hidden lg:block">
            <div className="sticky top-24">
                <p className="text-xs font-semibold tracking-wide text-quaternary uppercase">{label}</p>
                <ul className="mt-3 flex flex-col border-l border-secondary">
                    {sections.map((s) => (
                        <li key={s.id} className="relative">
                            {active === s.id && (
                                <motion.span
                                    layoutId={`toc-${doc}`}
                                    aria-hidden="true"
                                    className="absolute inset-y-1 -left-px w-0.5 rounded-full bg-fg-brand-primary"
                                    transition={{ type: "spring", bounce: 0.15, duration: 0.4 }}
                                />
                            )}
                            <a
                                href={`#${s.id}`}
                                onClick={(e) => goTo(e, s.id)}
                                aria-current={active === s.id ? "location" : undefined}
                                className={cx(
                                    "block py-1.5 pl-3 text-sm transition-colors duration-150",
                                    active === s.id ? "font-medium text-primary" : "text-tertiary hover:text-secondary",
                                )}
                            >
                                {s.title.replace(/^\d+\.\s*/, "")}
                            </a>
                        </li>
                    ))}
                </ul>
            </div>
        </nav>
    );
};

export const LegalPage = ({ doc }: { doc: Doc }) => {
    const { t, i18n } = useTranslation("legal");
    const { t: tc } = useTranslation();
    const vars = { company: COMPANY, email: CONTACT };

    const title = t(`${doc}.title`);
    const sections = t(`${doc}.sections`, { returnObjects: true, ...vars }) as LegalSection[];
    const updated = new Intl.DateTimeFormat(i18n.language, { dateStyle: "long" }).format(new Date(`${t(`${doc}.updated`)}T12:00:00`));
    const other: Doc = doc === "privacy" ? "terms" : "privacy";

    useEffect(() => {
        document.title = `${title} · Sentinela`;
        window.scrollTo({ top: 0, behavior: "instant" });
        return () => {
            document.title = "Sentinela";
        };
    }, [title]);

    return (
        <>
            <div className="relative overflow-hidden border-b border-secondary">
                <div aria-hidden="true" className="absolute inset-0 bg-dots mask-b-from-20%" />
                <motion.div
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.5 }}
                    className="relative container-page py-8 lg:py-12"
                >
                    <FeaturedIcon icon={doc === "privacy" ? FileShield02 : File06} color="gray" theme="modern" size="lg" />
                    <h1 className="mt-5 text-display-xs font-semibold text-primary md:text-display-sm">{title}</h1>
                    <p className="mt-3 text-sm font-medium text-quaternary">{tc("legal.updatedAt", { date: updated })}</p>
                    <p className="mt-4 max-w-3xl text-md text-tertiary">{t(`${doc}.intro`, vars)}</p>
                </motion.div>
            </div>

            <div className="container-page grid gap-8 py-8 lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-16 lg:py-12">
                <TableOfContents label={tc("legal.contents")} sections={sections} doc={doc} />

                <article className="max-w-3xl">
                    {sections.map((s) => (
                        <section key={s.id} id={s.id} className="scroll-mt-24 border-b border-secondary py-8 first:pt-0 last:border-b-0">
                            <h2 className="text-lg font-semibold text-primary">{s.title}</h2>
                            {s.paragraphs?.map((p) => (
                                <p key={p} className="mt-3 text-sm leading-6 text-tertiary">
                                    {p}
                                </p>
                            ))}
                            {s.items && (
                                <ul className="mt-4 flex flex-col gap-2.5">
                                    {s.items.map((item) => (
                                        <li key={item} className="flex gap-3 text-sm leading-6 text-tertiary">
                                            <span aria-hidden="true" className="mt-2.5 size-1.5 shrink-0 rounded-full bg-fg-quaternary" />
                                            <span>{item}</span>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </section>
                    ))}

                    <Link
                        to={`/${other}`}
                        className="group mt-6 flex items-center justify-between gap-4 rounded-xl bg-secondary p-5 ring-1 ring-secondary transition duration-150 ease-out hover:-translate-y-0.5 hover:ring-primary active:translate-y-0"
                    >
                        <span>
                            <span className="block text-sm text-quaternary">{tc("legal.otherDoc")}</span>
                            <span className="text-md font-semibold text-primary">{t(`${other}.title`)}</span>
                        </span>
                        <ArrowRight className="size-5 text-fg-quaternary transition duration-100 ease-linear group-hover:translate-x-0.5" />
                    </Link>
                </article>
            </div>
        </>
    );
};
