import { ChevronDown, Globe01 } from "@untitledui/icons";
import { Button as AriaButton } from "react-aria-components";
import { useTranslation } from "react-i18next";
import { Dropdown } from "@/components/base/dropdown/dropdown";
import { LANGUAGES, currentLanguage } from "@/i18n";
import { cx } from "@/utils/cx";

export const LanguageSwitcher = ({ className }: { className?: string }) => {
    const { t, i18n } = useTranslation();
    const active = currentLanguage();

    return (
        <Dropdown.Root>
            <AriaButton
                aria-label={t("language.label")}
                className={({ isFocusVisible, isHovered, isPressed }) =>
                    cx(
                        "flex cursor-pointer items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm font-medium text-tertiary outline-focus-ring transition duration-100 ease-linear",
                        (isHovered || isPressed) && "bg-primary_hover text-secondary",
                        isFocusVisible && "outline-2 outline-offset-2",
                        className,
                    )
                }
            >
                <Globe01 className="size-4 text-fg-quaternary" aria-hidden="true" />
                <span>{t(`language.${active}`)}</span>
                <ChevronDown className="size-4 text-fg-quaternary" aria-hidden="true" />
            </AriaButton>

            <Dropdown.Popover className="w-44">
                <Dropdown.Menu
                    aria-label={t("language.label")}
                    selectionMode="single"
                    disallowEmptySelection
                    selectedKeys={new Set([active])}
                    onSelectionChange={(keys) => {
                        const [next] = Array.from(keys as Set<string>);
                        if (next) i18n.changeLanguage(next);
                    }}
                >
                    {LANGUAGES.map((lng) => (
                        <Dropdown.Item key={lng} id={lng} lang={lng}>
                            {t(`language.${lng}`)}
                        </Dropdown.Item>
                    ))}
                </Dropdown.Menu>
            </Dropdown.Popover>
        </Dropdown.Root>
    );
};
