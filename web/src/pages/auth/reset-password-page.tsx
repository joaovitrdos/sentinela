import type { FormEvent } from "react";
import { useState } from "react";
import { ArrowLeft, Lock01 } from "@untitledui/icons";
import { AnimatePresence, motion } from "motion/react";
import { Trans, useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router";
import { AuthLayout } from "@/components/app/auth-layout";
import { CodeInput } from "@/components/app/code-input";
import { FormAlert } from "@/components/app/form-alert";
import { notify } from "@/components/app/notifications";
import { fieldProps } from "@/components/app/form-styles";
import { Redirect } from "@/components/app/route-guards";
import { Button } from "@/components/base/buttons/button";
import { Form } from "@/components/base/form/form";
import { Input } from "@/components/base/input/input";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import { authApi } from "@/lib/endpoints";
import { errorKey } from "@/lib/errors";
import type { FieldErrors } from "@/lib/validation";
import { check, validate } from "@/lib/validation";
import { useCooldown } from "./verify-email-page";

export const ResetPasswordPage = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const email = ((useLocation().state ?? {}) as { email?: string }).email;

    const [step, setStep] = useState<"code" | "password">("code");
    const [code, setCode] = useState("");
    const [password, setPassword] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [errors, setErrors] = useState<FieldErrors<"password" | "confirmation">>({});
    const [error, setError] = useState<string>();
    const [info, setInfo] = useState<string>();
    const [busy, setBusy] = useState(false);
    const [cooldown, startCooldown] = useCooldown(60);

    if (!email) return <Redirect to="/forgot-password" />;

    const validateCode = async (value = code) => {
        if (validate.code(value)) {
            setError(t("validation.code"));
            notify(t("validation.code"));
            return;
        }
        setBusy(true);
        setError(undefined);
        try {
            await authApi.validateRecoveryCode({ email, code: value });
            setStep("password");
        } catch (err) {
            setError(t(errorKey(err)));
            setCode("");
        } finally {
            setBusy(false);
        }
    };

    const resend = async () => {
        setError(undefined);
        try {
            await authApi.forgotPassword({ email });
            setInfo(t("auth.verify.resent"));
            startCooldown();
        } catch (err) {
            setError(t(errorKey(err)));
        }
    };

    const reset = async (event: FormEvent) => {
        event.preventDefault();
        const found = check({
            password: validate.password(password),
            confirmation: confirmation !== password ? "validation.passwordMismatch" : undefined,
        });
        setErrors(found);
        setError(undefined);
        if (Object.keys(found).length) {
            const firstError = Object.values(found).find(Boolean);
            if (firstError) notify(t(firstError), "info");
            return;
        }

        setBusy(true);
        try {
            await authApi.resetPassword({ email, code, newPassword: password });
            navigate("/login", { replace: true, state: { email, notice: "passwordReset" } });
        } catch (err) {
            setError(t(errorKey(err)));
            // Codigo vencido ou usado: volta para pedir outro.
            if (errorKey(err).includes("Code")) {
                setStep("code");
                setCode("");
            }
        } finally {
            setBusy(false);
        }
    };

    return (
        <AuthLayout
            title={
                <span className="flex flex-col items-center gap-4">
                    <FeaturedIcon icon={Lock01} color="gray" theme="modern" size="lg" />
                    {t("auth.reset.title")}
                </span>
            }
            subtitle={
                step === "code" ? (
                    <Trans i18nKey="auth.reset.subtitleCode" values={{ email }} components={{ strong: <strong className="font-semibold text-secondary" /> }} />
                ) : (
                    t("auth.reset.subtitlePassword")
                )
            }
            showLegalNotice={false}
        >
            <AnimatePresence mode="wait" initial={false}>
                {step === "code" ? (
                    <motion.div key="code" initial={{ opacity: 0, x: -12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -12 }}>
                        <Form
                            onSubmit={(e) => {
                                e.preventDefault();
                                validateCode();
                            }}
                            validationBehavior="aria"
                            className="flex flex-col gap-5"
                        >
                            <CodeInput
                                label={t("auth.verify.codeLabel")}
                                value={code}
                                onChange={setCode}
                                onComplete={validateCode}
                                isDisabled={busy}
                                autoFocus
                            />
                            <FormAlert>{error}</FormAlert>
                            <FormAlert type="success">{info}</FormAlert>
                            <Button type="submit" size="md" className="w-full" isLoading={busy} showTextWhileLoading>
                                {t("auth.reset.validate")}
                            </Button>
                            <p className="flex items-center justify-center gap-1 text-sm text-tertiary">
                                {t("auth.verify.noCode")}
                                <Button color="link-color" size="sm" onClick={resend} isDisabled={cooldown > 0}>
                                    {cooldown > 0 ? t("auth.verify.resendIn", { seconds: cooldown }) : t("auth.verify.resend")}
                                </Button>
                            </p>
                        </Form>
                    </motion.div>
                ) : (
                    <motion.div key="password" initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 12 }}>
                        <Form onSubmit={reset} validationBehavior="aria" className="flex flex-col gap-4">
                            <Input
                                label={t("auth.reset.newPassword")}
                                type="password"
                                autoComplete="new-password"
                                {...fieldProps}
                                value={password}
                                onChange={setPassword}
                                isInvalid={!!errors.password}
                                hint={errors.password ? t(errors.password) : t("auth.register.passwordHint")}
                                autoFocus
                            />
                            <Input
                                label={t("auth.reset.confirmPassword")}
                                type="password"
                                autoComplete="new-password"
                                {...fieldProps}
                                value={confirmation}
                                onChange={setConfirmation}
                                isInvalid={!!errors.confirmation}
                                hint={errors.confirmation && t(errors.confirmation)}
                            />
                            <FormAlert>{error}</FormAlert>
                            <Button type="submit" size="md" className="w-full" isLoading={busy} showTextWhileLoading>
                                {t("auth.reset.submit")}
                            </Button>
                        </Form>
                    </motion.div>
                )}
            </AnimatePresence>

            <div className="mt-4 flex justify-center">
                <Button color="link-gray" size="sm" iconLeading={ArrowLeft} href="/login">
                    {t("auth.forgot.backToLogin")}
                </Button>
            </div>
        </AuthLayout>
    );
};
