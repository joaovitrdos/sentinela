import type { RefAttributes } from "react";
import type { PopoverProps as AriaPopoverProps } from "react-aria-components";
import { Popover as AriaPopover } from "react-aria-components";
import { cx } from "@/utils/cx";

interface PopoverProps extends AriaPopoverProps, RefAttributes<HTMLElement> {
    size: "sm" | "md" | "lg";
}

export const Popover = (props: PopoverProps) => {
    return (
        <AriaPopover
            placement="bottom start"
            containerPadding={12}
            offset={8}
            {...props}
            className={(state) =>
                cx(
                    "z-50 w-(--trigger-width) origin-(--trigger-anchor-point) overflow-x-hidden overflow-y-auto overscroll-contain rounded-xl bg-primary py-1 shadow-lg ring-1 ring-secondary_alt outline-hidden will-change-transform",

                    state.isEntering &&
                        "duration-200 ease-out animate-in fade-in placement-right:slide-in-from-left-1 placement-top:slide-in-from-bottom-1 placement-bottom:slide-in-from-top-1",
                    state.isExiting &&
                        "duration-150 ease-in animate-out fade-out placement-right:slide-out-to-left-1 placement-top:slide-out-to-bottom-1 placement-bottom:slide-out-to-top-1",

                    props.size === "sm" && "max-h-56!",
                    props.size === "md" && "max-h-64!",
                    props.size === "lg" && "max-h-80!",

                    typeof props.className === "function" ? props.className(state) : props.className,
                )
            }
        />
    );
};
