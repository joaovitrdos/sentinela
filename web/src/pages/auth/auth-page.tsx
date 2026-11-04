import type { FormEvent, ReactNode } from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router";
import { Tabs } from "@/components/application/tabs/tabs";
import { AuthLayout, OrDivider } from "@/components/app/auth-layout";
import { fieldProps } from "@/components/app/form-styles";
import { FormAlert } from "@/components/app/form-alert";
import { notify } from "@/components/app/notifications";
import { GoogleButton } from "@/components/app/google-button";
import { Button } from "@/components/base/buttons/button";
import { Form } from "@/components/base/form/form";
import { Input } from "@/components/base/input/input";
import { authApi } from "@/lib/endpoints";
import { errorKey, isEmailNotVerified } from "@/lib/errors";
import type { FieldErrors } from "@/lib/validation";
import { check, validate } from "@/lib/validation";
import { useAuth } from "@/providers/auth-provider";
import { cx } from "@/utils/cx";

type Mode = "login" | "register";

export interface AuthLocationState {
    from?: string;
    email?: string;
    notice?: "passwordReset" | "loggedOutAll" | "accountDeleted";
}

/**
 * Entrar e Criar conta na mesma tela (rotas /login e /register).
 * Fica montada ao trocar de aba: so o formulario visivel muda, sem recarregar nada.
 */
export const AuthPage = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const location = useLocation();
    const mode: Mode = location.pathname.startsWith("/register") ? "register" : "login";
    const state = (location.state ?? {}) as AuthLocationState;
    const { signIn, sessionExpired } = useAuth();
    const [googleError, setGoogleError] = useState<string>();
    const [googleBusy, setGoogleBusy] = useState(false);

    const afterSignIn = () => navigate(state.from && state.from !== "/login" ? state.from : "/home", { replace: true });

    const handleGoogle = async (idToken: string) => {
        setGoogleError(undefined);
        setGoogleBusy(true);
        try {
            signIn(await authApi.loginWithGoogle({ idToken }));
            afterSignIn();
        } catch (err) {
            setGoogleError(t(errorKey(err)));
        } finally {
            setGoogleBusy(false);
        }
    };

    const notice = state.notice ? t(`auth.notices.${state.notice}`) : sessionExpired ? t("errors.sessionExpired") : undefined;

    return (
        <AuthLayout
            title={t("auth.card.title")}
            subtitle={t("auth.card.subtitle")}
            header={
                <Tabs
                    selectedKey={mode}
                    onSelectionChange={(key) => navigate(key === "register" ? "/register" : "/login", { replace: true, state: location.state })}
                >
                    <Tabs.List type="button-border" size="sm" fullWidth aria-label={t("auth.card.title")}>
                        <Tabs.Item id="login" label={t("auth.tabs.login")} />
                        <Tabs.Item id="register" label={t("auth.tabs.register")} />
                    </Tabs.List>
                </Tabs>
            }
        >
            <div className="flex flex-col gap-4">
                <FormAlert type={state.notice ? "success" : "info"}>{notice}</FormAlert>

                <GoogleButton onCredential={handleGoogle} isDisabled={googleBusy} />
                <FormAlert>{googleError}</FormAlert>

                <OrDivider label={t("common.or")} />

                {/* Os dois formularios ocupam a mesma celula: a altura e sempre a do maior, entao nada se mexe ao trocar. */}
                <div className="grid">
                    <FormSlot active={mode === "login"}>
                        <LoginForm initialEmail={state.email} returnTo={state.from} onSignedIn={afterSignIn} />
                    </FormSlot>
                    <FormSlot active={mode === "register"}>
                        <RegisterForm returnTo={state.from} />
                    </FormSlot>
                </div>
            </div>
        </AuthLayout>
    );
};

const FormSlot = ({ active, children }: { active: boolean; children: ReactNode }) => (
    <div
        inert={!active}
        aria-hidden={!active}
        className={cx(
            "col-start-1 row-start-1 transition-[opacity,visibility] duration-150 ease-out",
            active ? "visible opacity-100" : "invisible opacity-0",
        )}
    >
        {children}
    </div>
);

const LoginForm = ({ initialEmail = "", returnTo, onSignedIn }: { initialEmail?: string; returnTo?: string; onSignedIn: () => void }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { signIn } = useAuth();
    const [email, setEmail] = useState(initialEmail);
    const [password, setPassword] = useState("");
    const [errors, setErrors] = useState<FieldErrors<"email" | "password">>({});
    const [serverError, setServerError] = useState<string>();
    const [busy, setBusy] = useState(false);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const found = check({ email: validate.email(email), password: validate.required(password) });
        setErrors(found);
        setServerError(undefined);
        if (Object.keys(found).length) {
            const firstError = Object.values(found).find(Boolean);
            if (firstError) notify(t(firstError), "info");
            return;
        }

        setBusy(true);
        try {
            signIn(await authApi.login({ email: email.trim(), password }));
            onSignedIn();
        } catch (err) {
            if (isEmailNotVerified(err)) {
                navigate("/verify-email", { state: { email: email.trim(), from: returnTo } });
                return;
            }
            setServerError(t(errorKey(err)));
        } finally {
            setBusy(false);
        }
    };

    return (
        <Form onSubmit={submit} validationBehavior="aria" className="flex flex-col gap-3.5">
            <Input
                {...fieldProps}
                label={t("common.email")}
                type="email"
                autoComplete="email"
                placeholder={t("common.emailPlaceholder")}
                value={email}
                onChange={setEmail}
                isInvalid={!!errors.email}
                hint={errors.email && t(errors.email)}
            />
            <div className="flex flex-col gap-1">
                <Input
                    {...fieldProps}
                    label={t("common.password")}
                    type="password"
                    autoComplete="current-password"
                    placeholder={t("common.passwordPlaceholder")}
                    value={password}
                    onChange={setPassword}
                    isInvalid={!!errors.password}
                    hint={errors.password && t(errors.password)}
                />
                <div className="flex justify-end">
                    <Button color="link-gray" size="xs" href="/forgot-password">
                        {t("auth.login.forgot")}
                    </Button>
                </div>
            </div>

            <FormAlert>{serverError}</FormAlert>

            <Button type="submit" size="md" className="w-full" isLoading={busy} showTextWhileLoading>
                {t("auth.login.submit")}
            </Button>
        </Form>
    );
};

const RegisterForm = ({ returnTo }: { returnTo?: string }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [errors, setErrors] = useState<FieldErrors<"name" | "email" | "password">>({});
    const [serverError, setServerError] = useState<string>();
    const [busy, setBusy] = useState(false);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const found = check({ name: validate.name(name), email: validate.email(email), password: validate.password(password) });
        setErrors(found);
        setServerError(undefined);
        if (Object.keys(found).length) {
            const firstError = Object.values(found).find(Boolean);
            if (firstError) notify(t(firstError), "info");
            return;
        }

        setBusy(true);
        try {
            await authApi.register({ name: name.trim(), email: email.trim(), password });
            navigate("/verify-email", { state: { email: email.trim(), justRegistered: true, from: returnTo } });
        } catch (err) {
            setServerError(t(errorKey(err)));
        } finally {
            setBusy(false);
        }
    };

    return (
        <Form onSubmit={submit} validationBehavior="aria" className="flex flex-col gap-3.5">
            <Input
                {...fieldProps}
                label={t("common.name")}
                autoComplete="name"
                placeholder={t("common.namePlaceholder")}
                value={name}
                onChange={setName}
                isInvalid={!!errors.name}
                hint={errors.name && t(errors.name)}
            />
            <Input
                {...fieldProps}
                label={t("common.email")}
                type="email"
                autoComplete="email"
                placeholder={t("common.emailPlaceholder")}
                value={email}
                onChange={setEmail}
                isInvalid={!!errors.email}
                hint={errors.email && t(errors.email)}
            />
            <Input
                {...fieldProps}
                label={t("common.password")}
                type="password"
                autoComplete="new-password"
                placeholder={t("common.passwordPlaceholder")}
                value={password}
                onChange={setPassword}
                isInvalid={!!errors.password}
                hint={errors.password ? t(errors.password) : t("auth.register.passwordHint")}
            />

            <FormAlert>{serverError}</FormAlert>

            <Button type="submit" size="md" className="w-full" isLoading={busy} showTextWhileLoading>
                {t("auth.register.submit")}
            </Button>
        </Form>
    );
};
