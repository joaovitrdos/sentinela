import { useEffect, useState } from "react";
import { Moon01, Sun } from "@untitledui/icons";
import { useTranslation } from "react-i18next";
import { ButtonUtility } from "@/components/base/buttons/button-utility";
import { useTheme } from "@/providers/theme-provider";

const isDarkNow = () => document.documentElement.classList.contains("dark-mode");

export const ThemeToggle = ({ className }: { className?: string }) => {
    const { t } = useTranslation();
    const { theme, setTheme } = useTheme();
    const [dark, setDark] = useState(isDarkNow);

    // "system" so e resolvido no DOM; acompanha a classe para mostrar o icone certo.
    useEffect(() => {
        const observer = new MutationObserver(() => setDark(isDarkNow()));
        observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
        return () => observer.disconnect();
    }, [theme]);

    const label = dark ? t("theme.toLight") : t("theme.toDark");

    return (
        <ButtonUtility
            className={className}
            color="tertiary"
            size="sm"
            tooltip={label}
            aria-label={label}
            icon={dark ? Sun : Moon01}
            onClick={() => setTheme(dark ? "light" : "dark")}
        />
    );
};
