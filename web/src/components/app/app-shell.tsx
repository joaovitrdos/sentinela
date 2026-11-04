import type { ReactNode } from "react";
import { BarChartSquare02, FileDownload01, Home03, LogOut01, Map01, Settings01 } from "@untitledui/icons";
import { motion } from "motion/react";
import { Button as AriaButton } from "react-aria-components";
import { useTranslation } from "react-i18next";
import { Link, NavLink, useNavigate } from "react-router";
import { Avatar } from "@/components/base/avatar/avatar";
import { AvatarLabelGroup } from "@/components/base/avatar/avatar-label-group";
import { Dropdown } from "@/components/base/dropdown/dropdown";
import { useAuth } from "@/providers/auth-provider";
import { cx } from "@/utils/cx";

export const initialsOf = (name: string) =>
    name
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((p) => p[0]!.toUpperCase())
        .join("");

const COMPANY = import.meta.env.VITE_LEGAL_COMPANY || "Sentinela";

const UserMenu = () => {
    const { t } = useTranslation();
    const { user, signOut } = useAuth();
    const navigate = useNavigate();
    if (!user) return null;

    return (
        <Dropdown.Root>
            <AriaButton
                aria-label={`${t("home.greeting", { name: user.name.split(/\s+/)[0] ?? user.name })}. ${t("nav.account")}`}
                className={({ isPressed, isFocusVisible }) =>
                    cx(
                        "relative inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-full outline-offset-2 outline-focus-ring",
                        (isPressed || isFocusVisible) && "outline-2",
                    )
                }
            >
                <Avatar size="sm" src={user.picture || undefined} alt={user.name} initials={initialsOf(user.name)} />
            </AriaButton>
            <Dropdown.Popover className="w-64">
                <div className="flex gap-3 border-b border-secondary p-3">
                    <AvatarLabelGroup size="md" src={user.picture || undefined} initials={initialsOf(user.name)} title={user.name} subtitle={user.email} />
                </div>
                <Dropdown.Menu
                    aria-label={t("nav.account")}
                    onAction={(key) => {
                        if (key === "logout") signOut().then(() => navigate("/login", { replace: true }));
                        else navigate(String(key));
                    }}
                >
                    <Dropdown.Item id="/home" icon={Home03}>
                        {t("nav.home")}
                    </Dropdown.Item>
                    <Dropdown.Item id="/properties" icon={Map01}>
                        {t("nav.properties")}
                    </Dropdown.Item>
                    <Dropdown.Item id="/dashboard" icon={BarChartSquare02}>
                        {t("nav.dashboard")}
                    </Dropdown.Item>
                    <Dropdown.Item id="/reports" icon={FileDownload01}>
                        {t("nav.reports")}
                    </Dropdown.Item>
                    <Dropdown.Item id="/settings" icon={Settings01}>
                        {t("nav.settings")}
                    </Dropdown.Item>
                    <Dropdown.Separator />
                    <Dropdown.Item id="logout" icon={LogOut01}>
                        {t("nav.logout")}
                    </Dropdown.Item>
                </Dropdown.Menu>
            </Dropdown.Popover>
        </Dropdown.Root>
    );
};

export const AppFooter = () => {
    const { t } = useTranslation();
    return (
        <footer className="border-t border-secondary bg-secondary">
            <div className="container-page flex flex-col gap-3 py-6 text-sm text-quaternary sm:flex-row sm:items-center sm:justify-between">
                <p>{t("footer.rights", { year: new Date().getFullYear(), company: COMPANY })}</p>
                <nav className="flex gap-5">
                    <Link to="/privacy" className="link-underline hover:text-secondary">
                        {t("legal.privacy")}
                    </Link>
                    <Link to="/terms" className="link-underline hover:text-secondary">
                        {t("legal.terms")}
                    </Link>
                </nav>
            </div>
        </footer>
    );
};

/** Moldura das telas logadas (e das paginas legais): cabecalho, conteudo e rodape. */
export const AppShell = ({ children }: { children: ReactNode }) => {
    const { t } = useTranslation();
    const { isAuthenticated } = useAuth();

    const navLink = ({ isActive }: { isActive: boolean }) =>
        cx(
            "relative isolate inline-flex min-h-11 items-center rounded-lg px-3 py-2 text-[13px] font-normal transition duration-150 ease-out active:scale-[0.95]",
            isActive ? "text-white" : "text-white/70 hover:text-white",
        );

    // Fundo do item ativo desliza entre os links (o cabecalho continua montado ao trocar de pagina).
    const navLabel =
        (label: string) =>
        ({ isActive }: { isActive: boolean }) => (
            <>
                {isActive && (
                    <motion.span
                        layoutId="nav-active"
                        aria-hidden="true"
                        className="absolute inset-0 -z-10 rounded-lg bg-white/10"
                        transition={{ type: "spring", bounce: 0.15, duration: 0.4 }}
                    />
                )}
                {label}
            </>
        );

    return (
        <div className="flex min-h-dvh flex-col bg-secondary">
            <header className="site-nav sticky top-0 z-40 border-b border-white/10 bg-black">
                <div className="mx-auto flex h-14 w-full max-w-[1440px] items-center gap-4 px-4 sm:px-6 lg:px-8">
                    {isAuthenticated && (
                        <nav className="hidden items-center gap-1 lg:flex">
                            <NavLink to="/home" className={navLink}>
                                {navLabel(t("nav.home"))}
                            </NavLink>
                            <NavLink to="/properties" className={navLink}>
                                {navLabel(t("nav.properties"))}
                            </NavLink>
                            <NavLink to="/dashboard" className={navLink}>
                                {navLabel(t("nav.dashboard"))}
                            </NavLink>
                            <NavLink to="/reports" className={navLink}>
                                {navLabel(t("nav.reports"))}
                            </NavLink>
                            <NavLink to="/settings" className={navLink}>
                                {navLabel(t("nav.settings"))}
                            </NavLink>
                        </nav>
                    )}
                    <div className="ml-auto flex items-center gap-1">
                        {isAuthenticated && (
                            <div className="ml-2">
                                <UserMenu />
                            </div>
                        )}
                    </div>
                </div>
            </header>
            <main className="flex-1">{children}</main>
            <AppFooter />
        </div>
    );
};
