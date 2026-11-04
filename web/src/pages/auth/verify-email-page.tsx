import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import { ArrowLeft, Mail01 } from "@untitledui/icons";
import { Trans, useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router";
import { AuthLayout } from "@/components/app/auth-layout";
import { fieldProps } from "@/components/app/form-styles";
import { CodeInput } from "@/components/app/code-input";
import { FormAlert } from "@/components/app/form-alert";
import { notify } from "@/components/app/notifications";
import { Button } from "@/components/base/buttons/button";
import { Form } from "@/components/base/form/form";
import { Input } from "@/components/base/input/input";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import { authApi } from "@/lib/endpoints";
import { errorKey } from "@/lib/errors";
import { validate } from "@/lib/validation";
import { useAuth } from "@/providers/auth-provider";

const RESEND_COOLDOWN = 60;

/** Contagem regressiva para liberar o "reenviar codigo". */
export const useCooldown = (initial = 0) => {
    const [seconds, setSeconds] = useState(initial);
    useEffect(() => {
        if (seconds <= 0) return;
        const timer = setTimeout(() => setSeconds((s) => s - 1), 1000);
        return () => clearTimeout(timer);
    }, [seconds]);
    return [seconds, () => setSeconds(RESEND_COOLDOWN)] as const;
};

export const VerifyEmailPage = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const location = useLocation();
    const state = (location.state ?? {}) as { email?: string; justRegistered?: boolean; from?: string };
    const { signIn } = useAuth();

    const [email, setEmail] = useState(state.email ?? "");
    const [emailConfirmed, setEmailConfirmed] = useState(!!state.email);
    const [code, setCode] = useState("");
    const [error, setError] = useState<string>();
    const [info, setInfo] = useState<string | undefined>(state.justRegistered ? t("auth.register.success") : undefined);
    const [busy, setBusy] = useState(false);
    const [cooldown, startCooldown] = useCooldown(state.justRegistered ? RESEND_COOLDOWN : 0);

    const confirm = async (value = code) => {
        if (validate.code(value)) {
            setError(t("validation.code"));
            notify(t("validation.code"));
            return;
        }
        setBusy(true);
        setError(undefined);
        try {
            signIn(await authApi.confirmEmail({ email: email.trim(), code: value }));
            navigate(state.from?.startsWith("/") && !state.from.startsWith("//") ? state.from : "/home", { replace: true });
        } catch (err) {
            setError(t(errorKey(err)));
            setCode("");
        } finally {
            setBusy(false);
        }
    };

    const resend = async () => {
        setError(undefined);
        setInfo(undefined);
        try {
            await authApi.resendVerification({ email: email.trim() });
            setInfo(t("auth.verify.resent"));
            startCooldown();
        } catch (err) {
            setError(t(errorKey(err)));
        }
    };

    // Chegou sem e-mail (link direto): pergunta qual e-mail confirmar.
    if (!emailConfirmed) {
        const submitEmail = (event: FormEvent) => {
            event.preventDefault();
            const problem = validate.email(email);
            if (problem) {
                setError(t(problem));
                notify(t(problem));
                return;
            }
            setError(undefined);
            setEmailConfirmed(true);
        };
        return (
            <AuthLayout title={t("auth.verify.title")} showLegalNotice={false}>
                <Form onSubmit={submitEmail} validationBehavior="aria" className="flex flex-col gap-4">
                    <Input
                        label={t("auth.verify.emailLabel")}
                        type="email"
                        autoComplete="email"
                        {...fieldProps}
                        placeholder={t("common.emailPlaceholder")}
                        value={email}
                        onChange={setEmail}
                        isInvalid={!!error}
                        hint={error}
                    />
                    <FormAlert>{error}</FormAlert>
                    <Button type="submit" size="md" className="w-full">
                        {t("common.continue")}
                    </Button>
                </Form>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout
            title={
                <span className="flex flex-col items-center gap-4">
                    <FeaturedIcon icon={Mail01} color="gray" theme="modern" size="lg" />
                    {t("auth.verify.title")}
                </span>
            }
            subtitle={<Trans i18nKey="auth.verify.subtitle" values={{ email }} components={{ strong: <strong className="font-semibold text-secondary" /> }} />}
            showLegalNotice={false}
        >
            <Form
                onSubmit={(e) => {
                    e.preventDefault();
                    confirm();
                }}
                validationBehavior="aria"
                className="flex flex-col gap-5"
            >
                <CodeInput label={t("auth.verify.codeLabel")} value={code} onChange={setCode} onComplete={confirm} isDisabled={busy} autoFocus />

                <FormAlert>{error}</FormAlert>
                <FormAlert type="success">{info}</FormAlert>

                <Button type="submit" size="md" className="w-full" isLoading={busy} showTextWhileLoading>
                    {t("auth.verify.submit")}
                </Button>

                <p className="flex items-center justify-center gap-1 text-sm text-tertiary">
                    {t("auth.verify.noCode")}
                    <Button color="link-color" size="sm" onClick={resend} isDisabled={cooldown > 0}>
                        {cooldown > 0 ? t("auth.verify.resendIn", { seconds: cooldown }) : t("auth.verify.resend")}
                    </Button>
                </p>

                <div className="flex justify-center">
                    <Button color="link-gray" size="sm" iconLeading={ArrowLeft} onClick={() => navigate("/login", { state: { email } })}>
                        {t("auth.forgot.backToLogin")}
                    </Button>
                </div>
            </Form>
        </AuthLayout>
    );
};
