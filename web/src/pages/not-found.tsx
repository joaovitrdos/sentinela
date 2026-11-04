import { ArrowLeft, Home03 } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { StatusScreen } from "@/components/app/status-screen";
import { Button } from "@/components/base/buttons/button";

export function NotFound() {
    const { t } = useTranslation();
    const navigate = useNavigate();

    return (
        <StatusScreen code="404" label={t("notFound.code")} title={t("notFound.title")} description={t("notFound.subtitle")}>
            <div className="flex flex-col gap-3 sm:flex-row">
                <Button
                    color="secondary"
                    size="md"
                    iconLeading={ArrowLeft}
                    onPress={() => (window.history.state?.idx > 0 ? navigate(-1) : navigate("/", { replace: true }))}
                >
                    {t("notFound.back")}
                </Button>
                <Button size="md" iconLeading={Home03} onPress={() => navigate("/", { replace: true })}>
                    {t("notFound.home")}
                </Button>
            </div>
        </StatusScreen>
    );
}
