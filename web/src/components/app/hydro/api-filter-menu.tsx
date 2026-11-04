import type { FC } from "react";
import { ChevronDown } from "@untitledui/icons";
import { Button as AriaButton, Dialog as AriaDialog, DialogTrigger as AriaDialogTrigger, Popover as AriaPopover } from "react-aria-components";
import { useTranslation } from "react-i18next";
import { Checkbox } from "@/components/base/checkbox/checkbox";
import { cx } from "@/utils/cx";
import { useMapPanelWheel } from "./map-controls";

interface ApiFilterMenuProps<Id extends string> {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    options: { id: Id; label: string; icon: FC<{ className?: string }> }[];
    selected: Id[];
    onToggle: (id: Id) => void;
    onSelectAll: () => void;
    hint?: string;
    title?: string;
    selectAllLabel?: string;
    disabled?: boolean;
}

export function ApiFilterMenu<Id extends string>({
    open,
    onOpenChange,
    options,
    selected,
    onToggle,
    onSelectAll,
    hint,
    title,
    selectAllLabel,
    disabled = false,
}: ApiFilterMenuProps<Id>) {
    const { t } = useTranslation();
    const wheelRef = useMapPanelWheel<HTMLElement>();
    const allSelected = options.length > 0 && selected.length === options.length;
    const label = title ?? t("hydro.apiFilters");

    return (
        <div data-map-filters className="shrink-0">
            <AriaDialogTrigger isOpen={open} onOpenChange={onOpenChange}>
                <AriaButton
                    isDisabled={disabled}
                    className="flex h-9 cursor-pointer items-center justify-center gap-2 rounded-lg bg-primary px-3 text-sm font-semibold whitespace-nowrap text-secondary shadow-xs ring-1 ring-primary transition duration-100 ease-linear ring-inset hover:bg-primary_hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {label}
                    <span className="rounded bg-secondary px-1.5 py-0.5 text-xs font-medium text-tertiary tabular-nums">{selected.length}</span>
                    <ChevronDown aria-hidden="true" className={cx("size-4 text-fg-quaternary transition-transform duration-200", open && "rotate-180")} />
                </AriaButton>
                <AriaPopover
                    data-map-filters
                    placement="bottom end"
                    offset={8}
                    containerPadding={12}
                    isNonModal
                    className={({ isEntering, isExiting }) =>
                        cx(
                            "z-50 w-[min(20rem,calc(100vw-2rem))] origin-top-right rounded-xl bg-primary shadow-lg ring-1 ring-secondary_alt outline-hidden",
                            isEntering && "duration-200 ease-out animate-in fade-in slide-in-from-top-1",
                            isExiting && "duration-150 ease-in animate-out fade-out slide-out-to-top-1",
                        )
                    }
                >
                    <AriaDialog
                        ref={wheelRef}
                        data-map-scroll-viewport
                        aria-label={label}
                        className="max-h-[min(28rem,calc(100dvh-10rem),var(--available-height,100dvh))] overflow-y-auto overscroll-contain rounded-xl outline-hidden"
                    >
                        <div className="flex flex-col gap-2 p-3">
                            <Checkbox
                                size="sm"
                                isSelected={allSelected}
                                isDisabled={disabled || options.length === 0}
                                isIndeterminate={selected.length > 0 && !allSelected}
                                onChange={onSelectAll}
                                label={selectAllLabel ?? t("hydro.filters.selectAll")}
                                className="min-h-11 items-center rounded-xl border border-secondary bg-secondary/50 px-3 py-2.5 transition hover:border-brand hover:bg-primary_hover"
                            />
                            {options.map(({ id, label, icon: Icon }) => (
                                <Checkbox
                                    key={id}
                                    size="sm"
                                    isSelected={selected.includes(id)}
                                    isDisabled={disabled}
                                    onChange={() => onToggle(id)}
                                    label={
                                        <span className="flex min-w-0 items-center gap-2">
                                            <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-secondary text-tertiary">
                                                <Icon className="size-4" />
                                            </span>
                                            <span>{label}</span>
                                        </span>
                                    }
                                    className={cx(
                                        "min-h-11 items-center rounded-xl border px-3 py-2.5 transition hover:border-brand hover:bg-primary_hover",
                                        selected.includes(id) ? "bg-brand/5 border-brand" : "border-secondary bg-primary",
                                    )}
                                />
                            ))}
                        </div>
                        {hint && <p className="border-t border-secondary px-3 py-2 text-xs text-tertiary">{hint}</p>}
                    </AriaDialog>
                </AriaPopover>
            </AriaDialogTrigger>
        </div>
    );
}
