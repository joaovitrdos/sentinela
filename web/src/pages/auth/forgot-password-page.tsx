import type { FormEvent } from "react";
import { useState } from "react";
import { ArrowLeft, Key01 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { AuthLayout } from "@/components/app/auth-layout";
import { fieldProps } from "@/components/app/form-styles";
import { FormAlert } from "@/components/app/form-alert";
import { notify } from "@/components/app/notifications";
import { Button } from "@/components/base/buttons/button";
import { Form } from "@/components/base/form/form";
import { Input } from "@/components/base/input/input";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import { authApi } from "@/lib/endpoints";
import { errorKey } from "@/lib/errors";
import { validate } from "@/lib/validation";

export const ForgotPasswordPage = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [fieldError, setFieldError] = useState<string>();
    const [error, setError] = useState<string>();
    const [busy, setBusy] = useState(false);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const problem = validate.email(email);
        setFieldError(problem && t(problem));
        setError(undefined);
        if (problem) {
            notify(t(problem), "info");
            return;
        }

        setBusy(true);
        try {
            await authApi.forgotPassword({ email: email.trim() });
            navigate("/reset-password", { state: { email: email.trim() } });
        } catch (err) {
            setError(t(errorKey(err)));
        } finally {
            setBusy(false);
        }
    };

    return (
        <AuthLayout
            title={
                <span className="flex flex-col items-center gap-4">
                    <FeaturedIcon icon={Key01} color="gray" theme="modern" size="lg" />
                    {t("auth.forgot.title")}
                </span>
            }
            subtitle={t("auth.forgot.subtitle")}
            showLegalNotice={false}
        >
            <Form onSubmit={submit} validationBehavior="aria" className="flex flex-col gap-4">
                <Input
                    label={t("common.email")}
                    type="email"
                    autoComplete="email"
                    {...fieldProps}
                    placeholder={t("common.emailPlaceholder")}
                    value={email}
                    onChange={setEmail}
                    isInvalid={!!fieldError}
                    hint={fieldError}
                    autoFocus
                />
                <FormAlert>{error}</FormAlert>
                <Button type="submit" size="md" className="w-full" isLoading={busy} showTextWhileLoading>
                    {t("auth.forgot.submit")}
                </Button>
                <div className="flex justify-center">
                    <Button color="link-gray" size="sm" iconLeading={ArrowLeft} href="/login">
                        {t("auth.forgot.backToLogin")}
                    </Button>
                </div>
            </Form>
        </AuthLayout>
    );
};
