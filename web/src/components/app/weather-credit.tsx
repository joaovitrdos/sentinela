import { useTranslation } from "react-i18next";
import { cx } from "@/utils/cx";

/** Crédito e aviso exigidos pelos termos da WeatherAPI onde os dados dela aparecem. */
export const WeatherCredit = ({ className }: { className?: string }) => {
    const { t } = useTranslation();
    return (
        <p className={cx("text-[11px] leading-4 text-quaternary", className)}>
            <span className="font-medium">{t("advisor.weatherSource")}</span> {t("advisor.weatherDisclaimer")}
        </p>
    );
};
