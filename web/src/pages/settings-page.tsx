import type { FormEvent, ReactNode } from "react";
import { useEffect, useRef, useState } from "react";
import { ChevronDown, Edit03, Lock01, LogOut01, Map01, Sun, Trash01, User01 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router";
import { initialsOf } from "@/components/app/app-shell";
import { CodeInput } from "@/components/app/code-input";
import { ConfirmationModal } from "@/components/app/confirmation-modal";
import { FormAlert } from "@/components/app/form-alert";
import { FieldSettingsCard } from "@/components/app/field-settings-card";
import { GoogleButton } from "@/components/app/google-button";
import { notify } from "@/components/app/notifications";
import { fieldProps } from "@/components/app/form-styles";
import { Dialog, Modal, ModalOverlay } from "@/components/application/modals/modal";
import { Tabs } from "@/components/application/tabs/tabs";
import { Avatar } from "@/components/base/avatar/avatar";
import { Badge } from "@/components/base/badges/badges";
import { Button } from "@/components/base/buttons/button";
import { Form } from "@/components/base/form/form";
import { Input } from "@/components/base/input/input";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import { useBreakpoint } from "@/hooks/use-breakpoint";
import { LANGUAGES, currentLanguage } from "@/i18n";
import { authApi, userApi } from "@/lib/endpoints";
import { errorKey } from "@/lib/errors";
import { acceptTeamInvitation, cancelTeamInvitation, changeTeamRole, deleteProperty, inviteTeamMember, leaveTeam, listProperties, loadTeam, propertyErrorMessage, removeTeamMember, setTeamMode, setTeamTitle } from "@/lib/property-data";
import type { TeamOverview, TeamRole } from "@/lib/property-data";
import type { SavedProperty } from "@/lib/property-data";
import type { FieldErrors } from "@/lib/validation";
import { check, validate } from "@/lib/validation";
import { useAuth } from "@/providers/auth-provider";
import { useTheme } from "@/providers/theme-provider";
import { cx } from "@/utils/cx";

// ---------- Blocos de layout ----------

/** Card de configuracao: titulo e descricao em cima, campos no meio e barra de acao embaixo. */
const SettingsCard = ({ title, description, children, footer }: { title: string; description?: string; children: ReactNode; footer?: ReactNode }) => (
    <section className="w-full min-w-0 overflow-hidden rounded-[18px] bg-primary ring-1 ring-secondary">
        <header className="px-5 pt-5 sm:px-6">
            <h2 className="text-sm font-semibold text-primary">{title}</h2>
            {description && <p className="mt-0.5 text-sm text-tertiary">{description}</p>}
        </header>
        <div className="px-5 pt-4 pb-5 sm:px-6">{children}</div>
        {footer && (
            <footer className="flex flex-wrap items-center justify-end gap-x-4 gap-y-2 rounded-b-xl border-t border-secondary bg-secondary px-5 py-3 sm:px-6">
                {footer}
            </footer>
        )}
    </section>
);

/** Card com uma acao so: texto a esquerda e botao a direita (empilha no celular). */
const ActionCard = ({
    title,
    description,
    action,
    danger,
    children,
}: {
    title: string;
    description: string;
    action: ReactNode;
    danger?: boolean;
    children?: ReactNode;
}) => (
    <section className={cx("w-full min-w-0 rounded-[18px] bg-primary px-5 py-5 ring-1 sm:px-6", danger ? "ring-error_subtle" : "ring-secondary")}>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
                <h2 className={cx("text-sm font-semibold", danger ? "text-error-primary" : "text-primary")}>{title}</h2>
                <p className="mt-0.5 max-w-lg text-sm text-tertiary">{description}</p>
            </div>
            <div className="shrink-0">{action}</div>
        </div>
        {children}
    </section>
);

/** Dica curta na barra de acao (fica a esquerda do botao). */
const FooterHint = ({ children }: { children: ReactNode }) => <p className="mr-auto text-xs text-tertiary">{children}</p>;

// ---------- Pagina ----------

export const SettingsPage = () => {
    const { t } = useTranslation();
    const wide = useBreakpoint("lg");
    const location = useLocation();
    const navigate = useNavigate();
    const [tab, setTab] = useState<"profile" | "properties" | "team" | "field" | "security" | "appearance">(() =>
        new URLSearchParams(location.search).get("tab") === "team" ? "team" : "profile");
    const [teamReload, setTeamReload] = useState(0);
    const invitationStarted = useRef(false);
    const pageMounted = useRef(true);

    useEffect(() => { pageMounted.current = true; return () => { pageMounted.current = false; }; }, []);

    useEffect(() => {
        const token = new URLSearchParams(location.search).get("invite");
        if (!token || invitationStarted.current) return;
        invitationStarted.current = true;
        setTab("team");
        acceptTeamInvitation(token)
            .then(() => { if (pageMounted.current) { notify(t("settings.team.accepted"), "success"); setTeamReload((n) => n + 1); } })
            .catch((error: unknown) => { if (pageMounted.current) notify(propertyErrorMessage(error, t("settings.team.acceptError"))); })
            .finally(() => { if (pageMounted.current) navigate("/settings?tab=team", { replace: true }); });
    }, [location.search, navigate, t]);

    return (
        <div data-settings-page className="container-page py-8 lg:py-12">
            <header className="max-w-3xl pb-2">
                <h1 className="text-3xl font-semibold tracking-tight text-primary sm:text-4xl">{t("settings.title")}</h1>
                <p className="mt-3 text-base leading-relaxed text-tertiary">{t("settings.subtitle")}</p>
            </header>

            {/* Desktop: menu lateral. Celular: abas no topo. */}
            <Tabs
                orientation={wide ? "vertical" : "horizontal"}
                selectedKey={tab}
                onSelectionChange={(k) => setTab(k as typeof tab)}
                className="mt-8 gap-6 lg:grid lg:grid-cols-[13rem_minmax(0,1fr)] lg:items-start lg:gap-x-12"
            >
                <Tabs.List
                    type={wide ? "button-gray" : "underline"}
                    size="sm"
                    fullWidth={wide}
                    aria-label={t("settings.title")}
                    className="max-w-full overflow-x-auto lg:sticky lg:top-24 lg:w-52 lg:shrink-0 lg:overflow-visible"
                >
                    <Tabs.Item id="profile" icon={User01} label={t("settings.tabs.profile")} />
                    <Tabs.Item id="properties" icon={Map01} label={t("settings.tabs.properties")} />
                    <Tabs.Item id="team" icon={User01} label={t("settings.tabs.team")} />
                    <Tabs.Item id="field" icon={Map01} label={t("settings.tabs.field")} />
                    <Tabs.Item id="security" icon={Lock01} label={t("settings.tabs.security")} />
                    <Tabs.Item id="appearance" icon={Sun} label={t("settings.tabs.appearance")} />
                </Tabs.List>

                <div className="w-full max-w-3xl min-w-0">
                    <Tabs.Panel id="profile" animation="fade" className="flex w-full min-w-0 flex-col gap-5">
                        <ProfileCard />
                    </Tabs.Panel>
                    <Tabs.Panel id="properties" animation="fade" className="flex w-full min-w-0 flex-col gap-5">
                        <PropertiesCard />
                    </Tabs.Panel>
                    <Tabs.Panel id="team" animation="fade" className="flex w-full min-w-0 flex-col gap-5">
                        <TeamCard reloadKey={teamReload} />
                    </Tabs.Panel>
                    <Tabs.Panel id="field" animation="fade" className="flex w-full min-w-0 flex-col gap-5">
                        <FieldSettingsCard />
                    </Tabs.Panel>
                    <Tabs.Panel id="security" animation="fade" className="flex w-full min-w-0 flex-col gap-5">
                        <PasswordCard />
                        <EmailCard />
                        <SessionsCard />
                        <p className="mt-3 text-xs font-semibold tracking-wider text-error-primary uppercase">{t("settings.dangerZone")}</p>
                        <DeleteCard />
                    </Tabs.Panel>
                    <Tabs.Panel id="appearance" animation="fade" className="flex w-full min-w-0 flex-col gap-5">
                        <AppearanceCard />
                    </Tabs.Panel>
                </div>
            </Tabs>
        </div>
    );
};

const AppearanceCard = () => {
    const { t, i18n } = useTranslation();
    const { theme, setTheme } = useTheme();
    const selectClass = "min-h-11 w-full rounded-lg border border-primary bg-primary px-4 py-2 text-sm text-primary outline-brand focus-visible:outline-2 focus-visible:outline-offset-2 sm:max-w-xs";

    return (
        <SettingsCard title={t("settings.appearance.title")} description={t("settings.appearance.description")}>
            <div className="grid gap-6">
                <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                    {t("settings.appearance.theme")}
                    <select className={selectClass} value={theme} onChange={(event) => setTheme(event.target.value as "light" | "dark" | "system")}>
                        <option value="system">{t("settings.appearance.system")}</option>
                        <option value="light">{t("settings.appearance.light")}</option>
                        <option value="dark">{t("settings.appearance.dark")}</option>
                    </select>
                </label>
                <label className="flex flex-col gap-1.5 text-sm font-medium text-secondary">
                    {t("language.label")}
                    <select className={selectClass} value={currentLanguage()} onChange={(event) => void i18n.changeLanguage(event.target.value)}>
                        {LANGUAGES.map((language) => <option key={language} value={language}>{t(`language.${language}`)}</option>)}
                    </select>
                </label>
                <p className="text-xs text-tertiary">{t("settings.appearance.saved")}</p>
            </div>
        </SettingsCard>
    );
};

const teamRoleKey: Record<TeamRole, string> = { VIEWER: "viewer", EDITOR: "editor", ADMIN: "admin" };
const teamInputClass = "min-h-11 w-full rounded-lg border border-primary bg-primary px-3 py-2 text-sm text-primary outline-brand focus-visible:outline-2";

const TeamGroup = ({ label, empty, properties, selectedId, open, onToggle, onSelect }: {
    label: string; empty: string; properties: SavedProperty[]; selectedId: string;
    open: boolean; onToggle: () => void; onSelect: (id: string) => void;
}) => <section className="overflow-hidden rounded-xl border border-secondary">
    <button type="button" className="flex min-h-12 w-full items-center justify-between gap-3 px-4 text-left text-sm font-semibold text-primary transition-colors hover:bg-secondary"
        aria-expanded={open} onClick={onToggle}>
        <span>{label} <span className="ml-1 text-tertiary">{properties.length}</span></span>
        <ChevronDown aria-hidden="true" className={cx("size-4 text-tertiary transition-transform duration-200", open && "rotate-180")} />
    </button>
    <div className={cx("grid transition-[grid-template-rows,opacity] duration-200 ease-out", open ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0")}>
        <div className="min-h-0 overflow-hidden">
            <div className="border-t border-secondary p-2">
                {properties.length === 0 ? <p className="px-2 py-2 text-sm text-tertiary">{empty}</p> : properties.map((property) =>
                    <button key={property.propertyId} type="button" onClick={() => onSelect(property.propertyId)}
                        aria-current={selectedId === property.propertyId ? "true" : undefined}
                        className={cx("flex min-h-12 w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left transition-colors", selectedId === property.propertyId ? "bg-brand-secondary text-brand-secondary" : "text-primary hover:bg-secondary")}>
                        <span className="min-w-0"><span className="block truncate text-sm font-semibold">{property.teamTitle?.trim() || property.name}</span>
                            {property.teamTitle?.trim() && property.teamTitle.trim() !== property.name && <span className="block truncate text-xs text-tertiary">{property.name}</span>}
                        </span>
                    </button>)}
            </div>
        </div>
    </div>
</section>;

const TeamCard = ({ reloadKey }: { reloadKey: number }) => {
    const { t, i18n } = useTranslation();
    const roleLabel = (role: TeamRole) => t(`settings.team.${teamRoleKey[role]}`);
    const [properties, setProperties] = useState<SavedProperty[]>([]);
    const [selectedId, setSelectedId] = useState("");
    const [team, setTeam] = useState<TeamOverview | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [email, setEmail] = useState("");
    const [role, setRole] = useState<TeamRole>("VIEWER");
    const [title, setTitle] = useState("");
    const [organizerOpen, setOrganizerOpen] = useState(true);
    const [participatingOpen, setParticipatingOpen] = useState(true);
    const [pending, setPending] = useState<{ kind: "leave" } | { kind: "solo" } | { kind: "remove"; memberId: string; name: string } | null>(null);
    const organized = properties.filter((property) => property.teamOwner);
    const participating = properties.filter((property) => !property.teamOwner);
    const selectedProperty = properties.find((property) => property.propertyId === selectedId);
    const selectedGroupOpen = selectedProperty?.teamOwner ? organizerOpen : participatingOpen;

    useEffect(() => {
        const controller = new AbortController();
        listProperties(controller.signal)
            .then((rows) => { if (!controller.signal.aborted) { setProperties(rows); setSelectedId((current) => rows.some((row) => row.propertyId === current) ? current : rows[0]?.propertyId ?? ""); } })
            .catch((reason: unknown) => { if (!controller.signal.aborted) setError(propertyErrorMessage(reason, t("settings.properties.loadError"))); });
        return () => controller.abort();
    }, [reloadKey]);

    useEffect(() => {
        setTeam(null);
        setError("");
        if (!selectedId) return;
        const controller = new AbortController();
        loadTeam(selectedId, controller.signal)
            .then((result) => { if (!controller.signal.aborted) { setTeam(result); setTitle(result.title); } })
            .catch((reason: unknown) => { if (!controller.signal.aborted) setError(propertyErrorMessage(reason, t("settings.team.loadError"))); });
        return () => controller.abort();
    }, [selectedId, reloadKey]);

    const act = async (operation: () => Promise<TeamOverview>, success: string) => {
        if (busy) return false;
        setBusy(true); setError("");
        try { const result = await operation(); setTeam(result); setTitle(result.title); notify(success, "success"); return true; }
        catch (reason) { setError(propertyErrorMessage(reason, t("settings.team.saveError"))); return false; }
        finally { setBusy(false); }
    };

    const submitInvite = (event: FormEvent) => {
        event.preventDefault();
        if (!team?.enabled || !team.owner || !email.trim()) return;
        void act(() => inviteTeamMember(selectedId, email.trim(), role), t("settings.team.invited"))
            .then((saved) => { if (saved) setEmail(""); });
    };

    const leave = async (): Promise<boolean> => {
        if (!team || team.owner || busy) return false;
        const leavingId = selectedId;
        setBusy(true); setError("");
        try {
            await leaveTeam(leavingId);
            const remaining = properties.filter((property) => property.propertyId !== leavingId);
            setProperties(remaining);
            setSelectedId(remaining[0]?.propertyId ?? "");
            setTeam(null);
            notify(t("settings.team.left"), "success");
            return true;
        } catch (reason) { setError(propertyErrorMessage(reason, t("settings.team.saveError"))); return false; }
        finally { setBusy(false); }
    };

    const confirmPending = async () => {
        if (!pending || busy) return;
        const saved = pending.kind === "leave" ? await leave()
            : pending.kind === "solo" ? await act(() => setTeamMode(selectedId, false), t("settings.team.solo"))
            : await act(() => removeTeamMember(selectedId, pending.memberId), t("settings.team.remove"));
        if (saved) setPending(null);
    };

    return <SettingsCard title={t("settings.tabs.team")} description={t("settings.team.description")}>
        {error && <FormAlert>{error}</FormAlert>}
        {properties.length === 0 ? <p className="text-sm text-tertiary">{t("settings.team.empty")}</p> : <>
            <div className="grid gap-3">
                <TeamGroup label={t("settings.team.organizer")} empty={t("settings.team.noOrganized")} properties={organized} selectedId={selectedId}
                    open={organizerOpen} onToggle={() => setOrganizerOpen((open) => !open)} onSelect={(id) => { if (!busy) setSelectedId(id); }} />
                <TeamGroup label={t("settings.team.participating")} empty={t("settings.team.noParticipating")} properties={participating} selectedId={selectedId}
                    open={participatingOpen} onToggle={() => setParticipatingOpen((open) => !open)} onSelect={(id) => { if (!busy) setSelectedId(id); }} />
            </div>
            {selectedGroupOpen && (!team ? <p className="mt-4 text-sm text-tertiary">{t("settings.team.loading")}</p> : <>
                <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
                    <div><h3 className="text-base font-semibold text-primary">{team.title}</h3>
                        <p className="text-sm text-tertiary">{t("settings.team.property")}: {team.propertyName} · {t("settings.team.access", { role: roleLabel(team.myRole) })}</p></div>
                    {!team.owner && <Button size="sm" color="secondary" isDisabled={busy} onClick={() => { setError(""); setPending({ kind: "leave" }); }}>{t("settings.team.leave")}</Button>}
                </div>
                {team.owner && <form className="mt-4 flex flex-wrap items-end gap-3" onSubmit={(event) => {
                    event.preventDefault();
                    if (title.trim() && title.trim() !== team.title) void act(() => setTeamTitle(selectedId, title.trim()), t("settings.team.titleSaved"))
                        .then((saved) => { if (saved) setProperties((rows) => rows.map((row) => row.propertyId === selectedId ? { ...row, teamTitle: title.trim() } : row)); });
                }}>
                    <label className="min-w-0 flex-1 text-sm font-medium text-secondary">{t("settings.team.titleLabel")}
                        <input className={`${teamInputClass} mt-1`} value={title} maxLength={120} required disabled={busy} onChange={(event) => setTitle(event.target.value)} />
                    </label>
                    <Button type="submit" size="sm" color="secondary" isDisabled={busy || !title.trim() || title.trim() === team.title}>{t("settings.team.saveTitle")}</Button>
                </form>}
                <div className="mt-5 rounded-xl border border-secondary p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div><h3 className="text-sm font-semibold text-primary">{t("settings.team.mode")}</h3><p className="mt-1 text-sm text-tertiary">{t(team.enabled ? "settings.team.active" : "settings.team.solo")} · {t("settings.team.access", { role: roleLabel(team.myRole) })}</p></div>
                        {team.owner && <Button size="sm" color="secondary" isDisabled={busy} onClick={() => {
                            if (team.enabled) { setError(""); setPending({ kind: "solo" }); return; }
                            void act(() => setTeamMode(selectedId, !team.enabled), t(team.enabled ? "settings.team.solo" : "settings.team.active"));
                        }}>{t(team.enabled ? "settings.team.useSolo" : "settings.team.activate")}</Button>}
                    </div>
                </div>
                {team.enabled && team.owner && <>
                    <form onSubmit={submitInvite} className="mt-5 grid gap-3 rounded-xl border border-secondary p-4 sm:grid-cols-[minmax(0,1fr)_10rem_auto] sm:items-end">
                        <label className="text-sm font-medium text-secondary">{t("settings.team.inviteEmail")}<input className={`${teamInputClass} mt-1`} type="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="pessoa@exemplo.com" /></label>
                        <label className="text-sm font-medium text-secondary">{t("settings.team.role")}<select className={`${teamInputClass} mt-1`} value={role} onChange={(event) => setRole(event.target.value as TeamRole)}><option value="VIEWER">{roleLabel("VIEWER")}</option><option value="EDITOR">{roleLabel("EDITOR")}</option><option value="ADMIN">{roleLabel("ADMIN")}</option></select></label>
                        <Button type="submit" size="sm" isLoading={busy}>{t("settings.team.sendInvite")}</Button>
                    </form>
                    <p className="mt-2 text-xs text-tertiary">{t("settings.team.permissions")}</p>
                    <h3 className="mt-6 text-sm font-semibold text-primary">{t("settings.team.members")}</h3>
                    <ul className="mt-2 divide-y divide-secondary">{team.members.map((member) => <li key={member.userId} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                        <div className="min-w-0"><p className="font-semibold text-primary">{member.name}{member.owner ? ` · ${t("settings.team.owner")}` : ""}</p><p className="truncate text-tertiary">{member.email}</p></div>
                        {member.owner ? <span className="text-tertiary">{roleLabel("ADMIN")}</span> : <div className="flex items-center gap-2"><select aria-label={`${t("settings.team.role")}: ${member.name}`} className={teamInputClass} value={member.role} disabled={busy} onChange={(event) => void act(() => changeTeamRole(selectedId, member.userId, event.target.value as TeamRole), t("settings.team.role"))}><option value="VIEWER">{roleLabel("VIEWER")}</option><option value="EDITOR">{roleLabel("EDITOR")}</option><option value="ADMIN">{roleLabel("ADMIN")}</option></select><button type="button" disabled={busy} className="text-error-primary" onClick={() => { setError(""); setPending({ kind: "remove", memberId: member.userId, name: member.name }); }}>{t("settings.team.remove")}</button></div>}
                    </li>)}</ul>
                    <h3 className="mt-6 text-sm font-semibold text-primary">{t("settings.team.pending")}</h3>
                    {team.invitations.length === 0 ? <p className="mt-2 text-sm text-tertiary">{t("settings.team.noPending")}</p> : <ul className="mt-2 divide-y divide-secondary">{team.invitations.map((invite) => <li key={invite.invitationId} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm"><div><p className="text-primary">{invite.email}</p><p className="text-tertiary">{roleLabel(invite.role)} · {t("settings.team.expires", { date: new Date(invite.expiresAt).toLocaleDateString(i18n.language) })}</p></div><button type="button" disabled={busy} className="text-error-primary" onClick={() => void act(() => cancelTeamInvitation(selectedId, invite.invitationId), t("settings.team.cancel"))}>{t("settings.team.cancel")}</button></li>)}</ul>}
                </>}
                {team.enabled && team.myRole !== "ADMIN" && <p className="mt-5 text-sm text-tertiary">{t("settings.team.adminOnly")}</p>}
            </>)}
        </>}
        <ConfirmationModal open={pending !== null}
            title={pending?.kind === "remove" ? t("settings.team.remove") : pending?.kind === "leave" ? t("settings.team.leave") : t("settings.team.useSolo")}
            description={pending?.kind === "remove" ? t("settings.team.confirmRemove", { name: pending.name })
                : pending?.kind === "leave" ? t("settings.team.confirmLeave", { title: team?.title ?? "" }) : t("settings.team.confirmSolo")}
            confirmLabel={pending?.kind === "remove" ? t("settings.team.remove") : pending?.kind === "leave" ? t("settings.team.leave") : t("settings.team.useSolo")}
            cancelLabel={t("common.cancel")} onConfirm={() => void confirmPending()} onCancel={() => { setPending(null); setError(""); }}
            busy={busy} error={error} />
    </SettingsCard>;
};

// ---------- Perfil ----------

const ProfileCard = () => {
    const { t } = useTranslation();
    const { user, setUser } = useAuth();
    const [name, setName] = useState(user?.name ?? "");
    const [picture, setPicture] = useState(user?.picture ?? "");
    const [errors, setErrors] = useState<FieldErrors<"name" | "picture">>({});
    const [result, setResult] = useState<{ ok: boolean; text: string }>();
    const [busy, setBusy] = useState(false);
    if (!user) return null;

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const found = check({ name: validate.name(name), picture: validate.pictureUrl(picture) });
        setErrors(found);
        setResult(undefined);
        if (Object.keys(found).length) {
            const firstError = Object.values(found).find(Boolean);
            if (firstError) notify(t(firstError), "info");
            return;
        }
        setBusy(true);
        try {
            setUser(await userApi.update({ name: name.trim(), picture: picture.trim() }));
            setResult({ ok: true, text: t("settings.profile.saved") });
        } catch (err) {
            setResult({ ok: false, text: t(errorKey(err)) });
        } finally {
            setBusy(false);
        }
    };

    return (
        <Form onSubmit={submit} validationBehavior="aria" className="w-full min-w-0">
            <SettingsCard
                title={t("settings.profile.title")}
                description={t("settings.profile.subtitle")}
                footer={
                    <Button type="submit" size="sm" isLoading={busy} showTextWhileLoading>
                        {t("common.save")}
                    </Button>
                }
            >
                <div className="flex items-center gap-4">
                    <Avatar size="lg" src={picture.trim() || undefined} alt={name} initials={initialsOf(name || user.name)} />
                    <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-primary">{name || user.name}</p>
                        <p className="truncate text-sm text-tertiary">{user.email}</p>
                    </div>
                    <div className="ml-auto hidden flex-wrap justify-end gap-1.5 sm:flex">
                        <Badge size="sm" type="pill-color" color={user.emailVerified ? "success" : "warning"}>
                            {user.emailVerified ? t("settings.profile.emailVerified") : t("settings.profile.emailNotVerified")}
                        </Badge>
                        <Badge size="sm" type="pill-color" color="gray">
                            {t(`settings.profile.providers.${user.authProvider}`)}
                        </Badge>
                    </div>
                </div>

                <div className="mt-5 grid gap-4 sm:grid-cols-2">
                    <Input
                        {...fieldProps}
                        label={t("common.name")}
                        value={name}
                        onChange={setName}
                        autoComplete="name"
                        isInvalid={!!errors.name}
                        hint={errors.name && t(errors.name)}
                    />
                    <Input
                        {...fieldProps}
                        label={t("settings.profile.picture")}
                        type="url"
                        placeholder="https://"
                        value={picture}
                        onChange={setPicture}
                        isInvalid={!!errors.picture}
                        hint={errors.picture ? t(errors.picture) : t("settings.profile.pictureHint")}
                    />
                </div>

                <FormAlert type={result?.ok ? "success" : "error"} className="mt-4">
                    {result?.text}
                </FormAlert>
            </SettingsCard>
        </Form>
    );
};

// ---------- Propriedades ----------

const PropertiesCard = () => {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const [properties, setProperties] = useState<SavedProperty[]>([]);
    const [selected, setSelected] = useState<SavedProperty | null>(null);
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<string>();
    const [deleteError, setDeleteError] = useState<string>();
    const [notice, setNotice] = useState<string>();
    const [busy, setBusy] = useState(false);
    const [reload, setReload] = useState(0);

    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        setLoadError(undefined);
        listProperties(controller.signal)
            .then((result) => {
                if (!controller.signal.aborted) setProperties(result.filter((property) => property.accessRole !== "VIEWER"));
            })
            .catch((error: unknown) => {
                if (!controller.signal.aborted) setLoadError(propertyErrorMessage(error, t("settings.properties.loadError")));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [reload, t]);

    const select = (property: SavedProperty) => {
        if (busy) return;
        setDeleteError(undefined);
        setNotice(undefined);
        setSelected(property);
        setOpen(true);
    };

    const remove = async () => {
        if (!selected || busy || !selected.teamOwner) return;
        const property = selected;
        setBusy(true);
        setDeleteError(undefined);
        try {
            await deleteProperty(property.propertyId);
            setProperties((current) => current.filter((item) => item.propertyId !== property.propertyId));
            setOpen(false);
            setNotice(t("settings.properties.deleted", { name: property.name }));
        } catch (error) {
            setDeleteError(propertyErrorMessage(error, t("settings.properties.deleteError")));
        } finally {
            setBusy(false);
        }
    };

    const formatArea = (property: SavedProperty) =>
        property.areaHectares != null && Number.isFinite(property.areaHectares)
            ? `${property.areaHectares.toLocaleString(i18n.language, { maximumFractionDigits: 2 })} ha`
            : t("settings.properties.areaUnknown");

    return (
        <SettingsCard title={t("settings.properties.title")} description={t("settings.properties.subtitle")}>
            <FormAlert type="success" className="mb-4">
                {notice}
            </FormAlert>
            {loading ? (
                <p role="status" className="text-sm text-tertiary">
                    {t("settings.properties.loading")}
                </p>
            ) : loadError ? (
                <div>
                    <FormAlert>{loadError}</FormAlert>
                    <Button color="secondary" size="sm" className="mt-3" onClick={() => setReload((value) => value + 1)}>
                        {t("settings.properties.retry")}
                    </Button>
                </div>
            ) : properties.length === 0 ? (
                <p className="rounded-lg border border-dashed border-secondary px-4 py-5 text-sm text-tertiary">{t("settings.properties.empty")}</p>
            ) : (
                <ul className="space-y-2" aria-label={t("settings.properties.title")}>
                    {properties.map((property) => (
                        <li key={property.propertyId}>
                            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-secondary p-4">
                                <Map01 className="size-5 shrink-0 text-tertiary" aria-hidden="true" />
                                <span className="min-w-0 flex-1">
                                    <span className="block truncate text-sm font-semibold text-primary">{property.name}</span>
                                    <span className="mt-1 block truncate text-xs text-tertiary">
                                        {property.city} · {property.state}
                                    </span>
                                    <span className="mt-1 block text-xs text-secondary tabular-nums">{formatArea(property)}</span>
                                </span>
                                <div className="flex flex-wrap gap-2">
                                    <Button type="button" size="sm" color="secondary" iconLeading={Edit03}
                                        onClick={() => navigate(`/properties?edit=${encodeURIComponent(property.propertyId)}`)}
                                        isDisabled={busy}>
                                        {t("settings.properties.edit")}
                                    </Button>
                                    {property.teamOwner && <Button type="button" size="sm" color="primary-destructive" iconLeading={Trash01}
                                        onClick={() => select(property)} isDisabled={busy}>
                                        {t("settings.properties.delete")}
                                    </Button>}
                                </div>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <ModalOverlay
                isOpen={open}
                onOpenChange={(value) => {
                    if (!value && !busy) {
                        setOpen(false);
                        setDeleteError(undefined);
                    }
                }}
                isDismissable={!busy}
                isKeyboardDismissDisabled={busy}
            >
                <Modal className="max-w-md">
                    <Dialog aria-label={t("settings.properties.confirmTitle")}>
                        <div className="flex w-full flex-col gap-4 p-6">
                            <FeaturedIcon icon={Trash01} color="error" theme="light" size="md" />
                            <div>
                                <h3 className="text-md font-semibold text-primary">{t("settings.properties.confirmTitle")}</h3>
                                <p className="mt-1 text-sm break-words text-tertiary">{t("settings.properties.confirmText", { name: selected?.name ?? "" })}</p>
                            </div>
                            <FormAlert>{deleteError}</FormAlert>
                            <div className="mt-1 grid grid-cols-2 gap-3">
                                <Button size="sm" color="secondary" autoFocus onClick={() => setOpen(false)} isDisabled={busy}>
                                    {t("common.cancel")}
                                </Button>
                                <Button
                                    size="sm"
                                    color="primary-destructive"
                                    onClick={() => void remove()}
                                    isDisabled={busy || !selected}
                                    isLoading={busy}
                                    showTextWhileLoading
                                >
                                    {t("settings.properties.confirm")}
                                </Button>
                            </div>
                        </div>
                    </Dialog>
                </Modal>
            </ModalOverlay>
        </SettingsCard>
    );
};

// ---------- Senha ----------

const PasswordCard = () => {
    const { t } = useTranslation();
    const { user, signIn } = useAuth();
    const [current, setCurrent] = useState("");
    const [next, setNext] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [errors, setErrors] = useState<FieldErrors<"current" | "next" | "confirmation">>({});
    const [result, setResult] = useState<{ ok: boolean; text: string }>();
    const [busy, setBusy] = useState(false);

    if (user?.authProvider === "GOOGLE") {
        return (
            <SettingsCard title={t("settings.password.title")} description={t("settings.password.subtitle")}>
                <FormAlert type="info">{t("settings.password.googleOnly")}</FormAlert>
            </SettingsCard>
        );
    }

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const found = check({
            current: validate.required(current),
            next: validate.password(next),
            confirmation: confirmation !== next ? "validation.passwordMismatch" : undefined,
        });
        setErrors(found);
        setResult(undefined);
        if (Object.keys(found).length) {
            const firstError = Object.values(found).find(Boolean);
            if (firstError) notify(t(firstError), "info");
            return;
        }
        setBusy(true);
        try {
            // O backend revoga os tokens antigos (outros dispositivos saem) e devolve um novo para esta sessao.
            signIn(await userApi.changePassword({ currentPassword: current, newPassword: next }));
            setCurrent("");
            setNext("");
            setConfirmation("");
            setResult({ ok: true, text: t("settings.password.saved") });
        } catch (err) {
            setResult({ ok: false, text: t(errorKey(err)) });
        } finally {
            setBusy(false);
        }
    };

    return (
        <Form onSubmit={submit} validationBehavior="aria" className="w-full min-w-0">
            <SettingsCard
                title={t("settings.password.title")}
                description={t("settings.password.subtitle")}
                footer={
                    <>
                        <FooterHint>{t("auth.register.passwordHint")}</FooterHint>
                        <Button type="submit" size="sm" isLoading={busy} showTextWhileLoading>
                            {t("settings.password.submit")}
                        </Button>
                    </>
                }
            >
                <div className="grid gap-4 sm:grid-cols-2">
                    <Input
                        {...fieldProps}
                        label={t("settings.password.current")}
                        type="password"
                        autoComplete="current-password"
                        value={current}
                        onChange={setCurrent}
                        isInvalid={!!errors.current}
                        hint={errors.current && t(errors.current)}
                    />
                    <div aria-hidden="true" className="hidden sm:block" />
                    <Input
                        {...fieldProps}
                        label={t("settings.password.new")}
                        type="password"
                        autoComplete="new-password"
                        value={next}
                        onChange={setNext}
                        isInvalid={!!errors.next}
                        hint={errors.next && t(errors.next)}
                    />
                    <Input
                        {...fieldProps}
                        label={t("settings.password.confirm")}
                        type="password"
                        autoComplete="new-password"
                        value={confirmation}
                        onChange={setConfirmation}
                        isInvalid={!!errors.confirmation}
                        hint={errors.confirmation && t(errors.confirmation)}
                    />
                </div>
                <FormAlert type={result?.ok ? "success" : "error"} className="mt-4">
                    {result?.text}
                </FormAlert>
            </SettingsCard>
        </Form>
    );
};

// ---------- E-mail ----------

const EmailCard = () => {
    const { t } = useTranslation();
    const { user, signIn } = useAuth();
    const [newEmail, setNewEmail] = useState("");
    const [password, setPassword] = useState("");
    const [code, setCode] = useState("");
    const [step, setStep] = useState<"request" | "confirm">("request");
    const [errors, setErrors] = useState<FieldErrors<"newEmail" | "password">>({});
    const [error, setError] = useState<string>();
    const [success, setSuccess] = useState<string>();
    const [busy, setBusy] = useState(false);

    const currentEmail = (
        <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-tertiary">{t("settings.email.current")}:</span>
            <span className="font-medium text-primary">{user?.email}</span>
            {user?.emailVerified && (
                <Badge size="sm" type="pill-color" color="success">
                    {t("settings.profile.emailVerified")}
                </Badge>
            )}
        </div>
    );

    if (user?.authProvider === "GOOGLE") {
        return (
            <SettingsCard title={t("settings.email.title")} description={t("settings.email.subtitle")}>
                {currentEmail}
                <FormAlert type="info" className="mt-4">
                    {t("settings.email.googleOnly")}
                </FormAlert>
            </SettingsCard>
        );
    }

    const request = async (event: FormEvent) => {
        event.preventDefault();
        const found = check({ newEmail: validate.email(newEmail), password: validate.required(password) });
        setErrors(found);
        setError(undefined);
        setSuccess(undefined);
        if (Object.keys(found).length) {
            const firstError = Object.values(found).find(Boolean);
            if (firstError) notify(t(firstError), "info");
            return;
        }
        setBusy(true);
        try {
            await userApi.requestEmailChange({ newEmail: newEmail.trim(), currentPassword: password });
            setStep("confirm");
            setPassword("");
        } catch (err) {
            setError(t(errorKey(err)));
        } finally {
            setBusy(false);
        }
    };

    const confirm = async (value = code) => {
        if (validate.code(value)) {
            setError(t("validation.code"));
            notify(t("validation.code"));
            return;
        }
        setBusy(true);
        setError(undefined);
        try {
            // O token antigo carrega o e-mail antigo e foi revogado; o backend devolve um novo para esta sessao.
            const auth = await userApi.confirmEmailChange({ code: value });
            signIn(auth);
            setStep("request");
            setNewEmail("");
            setCode("");
            setSuccess(t("settings.email.saved", { email: auth.user.email }));
        } catch (err) {
            setError(t(errorKey(err)));
            setCode("");
        } finally {
            setBusy(false);
        }
    };

    if (step === "confirm") {
        return (
            <Form
                className="w-full min-w-0"
                onSubmit={(e) => {
                    e.preventDefault();
                    confirm();
                }}
                validationBehavior="aria"
            >
                <SettingsCard
                    title={t("settings.email.title")}
                    description={t("settings.email.codeTitle", { email: newEmail.trim() })}
                    footer={
                        <>
                            <Button size="sm" color="secondary" onClick={() => setStep("request")} isDisabled={busy}>
                                {t("common.back")}
                            </Button>
                            <Button type="submit" size="sm" isLoading={busy} showTextWhileLoading>
                                {t("settings.email.confirm")}
                            </Button>
                        </>
                    }
                >
                    <div className="flex justify-start">
                        <CodeInput label={t("auth.verify.codeLabel")} value={code} onChange={setCode} onComplete={confirm} isDisabled={busy} autoFocus />
                    </div>
                    <FormAlert className="mt-4">{error}</FormAlert>
                </SettingsCard>
            </Form>
        );
    }

    return (
        <Form onSubmit={request} validationBehavior="aria" className="w-full min-w-0">
            <SettingsCard
                title={t("settings.email.title")}
                description={t("settings.email.subtitle")}
                footer={
                    <Button type="submit" size="sm" color="secondary" isLoading={busy} showTextWhileLoading>
                        {t("settings.email.submit")}
                    </Button>
                }
            >
                {currentEmail}
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <Input
                        {...fieldProps}
                        label={t("settings.email.new")}
                        type="email"
                        autoComplete="email"
                        placeholder={t("common.emailPlaceholder")}
                        value={newEmail}
                        onChange={setNewEmail}
                        isInvalid={!!errors.newEmail}
                        hint={errors.newEmail && t(errors.newEmail)}
                    />
                    <Input
                        {...fieldProps}
                        label={t("settings.email.password")}
                        type="password"
                        autoComplete="current-password"
                        value={password}
                        onChange={setPassword}
                        isInvalid={!!errors.password}
                        hint={errors.password && t(errors.password)}
                    />
                </div>
                <FormAlert className="mt-4">{error}</FormAlert>
                <FormAlert type="success" className="mt-4">
                    {success}
                </FormAlert>
            </SettingsCard>
        </Form>
    );
};

// ---------- Sessoes ----------

const SessionsCard = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { user, clearSession } = useAuth();
    const [error, setError] = useState<string>();
    const [busy, setBusy] = useState(false);

    const logoutAll = async () => {
        setBusy(true);
        setError(undefined);
        try {
            await authApi.logoutEverywhere();
            const email = user?.email;
            clearSession();
            navigate("/login", { replace: true, state: { email, notice: "loggedOutAll" } });
        } catch (err) {
            setError(t(errorKey(err)));
            setBusy(false);
        }
    };

    return (
        <ActionCard
            title={t("settings.sessions.title")}
            description={t("settings.sessions.subtitle")}
            action={
                <Button size="sm" color="secondary" iconLeading={LogOut01} onClick={logoutAll} isLoading={busy} showTextWhileLoading>
                    {t("settings.sessions.submit")}
                </Button>
            }
        >
            <FormAlert className="mt-4">{error}</FormAlert>
        </ActionCard>
    );
};

// ---------- Excluir conta ----------

const DeleteCard = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { clearSession, user } = useAuth();
    const [open, setOpen] = useState(false);
    const [typed, setTyped] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string>();
    const [busy, setBusy] = useState(false);
    const keyword = t("settings.delete.keyword");

    const finishRemoval = async (body: { currentPassword?: string; googleIdToken?: string }) => {
        setBusy(true);
        setError(undefined);
        try {
            await userApi.delete(body);
            clearSession();
            navigate("/login", { replace: true, state: { notice: "accountDeleted" } });
        } catch (err) {
            setError(t(errorKey(err)));
            setBusy(false);
        }
    };

    const remove = () => finishRemoval({ currentPassword: password });

    return (
        <ActionCard
            danger
            title={t("settings.delete.title")}
            description={t("settings.delete.subtitle")}
            action={
                <Button size="sm" color="primary-destructive" iconLeading={Trash01} onClick={() => setOpen(true)}>
                    {t("settings.delete.submit")}
                </Button>
            }
        >
            <ModalOverlay
                isOpen={open}
                onOpenChange={(value) => {
                    if (busy) return;
                    setOpen(value);
                    if (!value) {
                        setTyped("");
                        setPassword("");
                        setError(undefined);
                    }
                }}
                isDismissable={!busy}
            >
                <Modal className="max-w-md">
                    <Dialog aria-label={t("settings.delete.modalTitle")}>
                        <div className="flex w-full flex-col gap-4 p-6">
                            <FeaturedIcon icon={Trash01} color="error" theme="light" size="md" />
                            <div>
                                <h3 className="text-md font-semibold text-primary">{t("settings.delete.modalTitle")}</h3>
                                <p className="mt-1 text-sm text-tertiary">{t("settings.delete.modalText", { word: keyword })}</p>
                            </div>
                            <Input {...fieldProps} aria-label={keyword} placeholder={keyword} value={typed} onChange={setTyped} autoFocus />
                            {user?.authProvider === "LOCAL" ? (
                                <Input
                                    label={t("settings.email.password")}
                                    type="password"
                                    autoComplete="current-password"
                                    {...fieldProps}
                                    value={password}
                                    onChange={setPassword}
                                />
                            ) : null}
                            <FormAlert>{error}</FormAlert>
                            <div className="mt-1 grid grid-cols-2 gap-3">
                                <Button size="sm" color="secondary" onClick={() => setOpen(false)} isDisabled={busy}>
                                    {t("common.cancel")}
                                </Button>
                                <Button
                                    size="sm"
                                    color="primary-destructive"
                                    onClick={remove}
                                    isDisabled={typed.trim() !== keyword || user?.authProvider !== "LOCAL" || !password}
                                    isLoading={busy}
                                    showTextWhileLoading
                                >
                                    {t("settings.delete.confirm")}
                                </Button>
                            </div>
                            {user?.authProvider === "GOOGLE" && typed.trim() === keyword ? (
                                <GoogleButton
                                    isDisabled={busy}
                                    onCredential={(googleIdToken) => void finishRemoval({ googleIdToken })}
                                />
                            ) : null}
                        </div>
                    </Dialog>
                </Modal>
            </ModalOverlay>
        </ActionCard>
    );
};
