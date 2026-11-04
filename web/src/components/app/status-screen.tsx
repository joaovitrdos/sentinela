import type { ReactNode } from "react";
import { LanguageSwitcher } from "./language-switcher";
import { Logo } from "./logo";
import { ThemeToggle } from "./theme-toggle";

export const StatusScreen = ({
    code,
    label,
    title,
    description,
    children,
}: {
    code: "404" | "503";
    label: string;
    title: string;
    description: string;
    children: ReactNode;
}) => (
    <div className="relative flex min-h-dvh flex-col overflow-hidden bg-primary">
        <header className="relative z-10 flex items-center justify-between gap-4 border-b border-secondary px-[15px] py-4 sm:px-8">
            <Logo />
            <div className="flex items-center gap-1">
                <LanguageSwitcher />
                <ThemeToggle />
            </div>
        </header>
        <main className="relative mx-auto grid w-full max-w-6xl flex-1 items-center gap-10 px-5 py-12 sm:px-8 lg:grid-cols-2 lg:gap-16 lg:py-20">
            <div className="relative z-10 max-w-xl">
                <span className="inline-flex rounded-full bg-brand-primary px-3 py-1 text-xs font-semibold text-brand-secondary ring-1 ring-brand_alt">
                    {label}
                </span>
                <h1 className="mt-5 text-display-sm font-semibold tracking-tight text-primary sm:text-display-md">{title}</h1>
                <p className="mt-4 text-md leading-relaxed text-tertiary">{description}</p>
                <div className="mt-7">{children}</div>
            </div>
            <div aria-hidden="true" className="relative mx-auto hidden aspect-square w-full max-w-md items-center justify-center lg:flex">
                <div className="absolute inset-0 rounded-full bg-brand-primary/50 blur-3xl" />
                <svg viewBox="0 0 400 400" fill="none" className="absolute inset-0 size-full text-fg-brand-secondary">
                    <circle cx="200" cy="200" r="182" stroke="currentColor" strokeOpacity=".12" />
                    <circle cx="200" cy="200" r="141" stroke="currentColor" strokeOpacity=".18" strokeDasharray="5 8" />
                    <circle cx="200" cy="200" r="99" stroke="currentColor" strokeOpacity=".15" />
                    <path
                        d="M35 132 108 98 162 134 251 78 364 129M30 285l104-57 65 49 71-32 101 52M108 98l26 130M251 78l19 167"
                        stroke="currentColor"
                        strokeOpacity=".16"
                        strokeWidth="2"
                        strokeLinejoin="round"
                    />
                    <path
                        d="m75 331 61-87 52-18 40-69 46-10 42-71"
                        stroke="currentColor"
                        strokeOpacity=".45"
                        strokeWidth="3"
                        strokeLinecap="round"
                        strokeDasharray="4 9"
                    />
                    <circle cx="108" cy="98" r="6" fill="currentColor" fillOpacity=".65" />
                    <circle cx="270" cy="245" r="7" fill="currentColor" fillOpacity=".7" />
                    <circle cx="75" cy="331" r="5" fill="currentColor" fillOpacity=".65" />
                </svg>
                <div className="relative rounded-3xl border border-secondary bg-primary/90 px-8 py-7 shadow-xl backdrop-blur-sm">
                    <p className="text-[5.5rem] leading-none font-semibold tracking-tighter text-primary sm:text-[7rem]">{code}</p>
                    <div className="mx-auto mt-4 h-1 w-12 rounded-full bg-brand-solid" />
                </div>
            </div>
        </main>
    </div>
);
