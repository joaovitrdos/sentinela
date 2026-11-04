import type { FC, ReactNode, RefCallback } from "react";
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { Check, ChevronDown, ChevronUp, XClose } from "@untitledui/icons";
import { AnimatePresence, motion, useAnimationControls, useReducedMotion } from "motion/react";
import { useTranslation } from "react-i18next";
import type { BasemapId } from "@/components/app/brazil-map/basemaps";
import { BASEMAPS } from "@/components/app/brazil-map/basemaps";
import { Toggle } from "@/components/base/toggle/toggle";
import { cx } from "@/utils/cx";
import type { ClassDef } from "./hydro-data";

/** Scrolls the panel under the pointer without moving keyboard focus. */
export const useMapPanelWheel = <T extends HTMLElement = HTMLDivElement>(): RefCallback<T> =>
    useCallback((panel: T | null) => {
        if (!panel) return;

        const onWheel = (event: WheelEvent) => {
            if (event.ctrlKey || event.metaKey || event.defaultPrevented) return;
            const target = event.target instanceof Element ? event.target : null;
            if (
                target?.closest(
                    "input[type='number'], input[type='range'], select, textarea, [role='spinbutton'], [contenteditable='true'], [data-map-wheel-native]",
                )
            )
                return;

            const horizontal = event.shiftKey || Math.abs(event.deltaX) > Math.abs(event.deltaY);
            const hasOverflow = (element: HTMLElement) =>
                element.clientHeight > 0 &&
                element.clientWidth > 0 &&
                !element.closest("[inert]") &&
                (horizontal ? element.scrollWidth > element.clientWidth + 1 : element.scrollHeight > element.clientHeight + 1);

            const hovered = target?.closest<HTMLElement>("[data-map-scroll-viewport]");
            const viewport =
                hovered && panel.contains(hovered) && hasOverflow(hovered)
                    ? hovered
                    : [panel, ...panel.querySelectorAll<HTMLElement>("[data-map-scroll-viewport]")].find(
                          (element) => element.matches("[data-map-scroll-viewport]") && hasOverflow(element),
                      );
            if (!viewport) return;

            const lineHeight = Number.parseFloat(getComputedStyle(viewport).lineHeight) || 16;
            const unit = event.deltaMode === 1 ? lineHeight : event.deltaMode === 2 ? (horizontal ? viewport.clientWidth : viewport.clientHeight) : 1;
            const deltaX = (event.shiftKey && !event.deltaX ? event.deltaY : event.deltaX) * unit;
            const deltaY = (event.shiftKey && !event.deltaX ? 0 : event.deltaY) * unit;
            const delta = horizontal ? deltaX : deltaY;
            if (!delta) return;

            event.stopPropagation();
            const position = horizontal ? viewport.scrollLeft : viewport.scrollTop;
            const limit = horizontal ? viewport.scrollWidth - viewport.clientWidth : viewport.scrollHeight - viewport.clientHeight;
            const canScroll = delta < 0 ? position > 0 : position < limit - 1;

            // Keep native scrolling when the pointer is already over the viewport.
            if (canScroll && target && viewport.contains(target)) return;
            event.preventDefault();
            if (canScroll) viewport.scrollBy({ left: deltaX, top: deltaY, behavior: "instant" });
        };

        panel.addEventListener("wheel", onWheel, { passive: false });
        return () => panel.removeEventListener("wheel", onWheel);
    }, []);

// ---------- Coluna de botoes (canto direito do mapa) ----------

export const ControlGroup = ({ children }: { children: ReactNode }) => (
    <div className="pointer-events-auto flex flex-col overflow-hidden rounded-lg bg-primary shadow-xs ring-1 ring-secondary [&>*+*]:border-t [&>*+*]:border-secondary">
        {children}
    </div>
);

export const ControlButton = ({
    icon: Icon,
    label,
    active,
    onClick,
}: {
    icon: FC<{ className?: string }>;
    label: string;
    active?: boolean;
    onClick: () => void;
}) => (
    <button
        type="button"
        data-control-trigger
        aria-label={label}
        aria-pressed={active}
        title={label}
        onClick={onClick}
        className={cx(
            "flex size-8 cursor-pointer items-center justify-center transition duration-100 ease-linear focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus-ring active:scale-95",
            active ? "bg-primary_hover text-fg-primary" : "text-fg-quaternary hover:bg-primary_hover hover:text-fg-secondary",
        )}
    >
        <Icon className="size-4" />
    </button>
);

/** Painel que abre ao lado da coluna de botoes. Fecha com Esc ou clicando fora. */
export const ControlPanel = ({
    open,
    title,
    onClose,
    children,
    className,
}: {
    open: boolean;
    title: string;
    onClose: () => void;
    children: ReactNode;
    className?: string;
}) => {
    const { t } = useTranslation();
    const ref = useRef<HTMLDivElement>(null);
    const wheelRef = useMapPanelWheel();
    const panelRef = useCallback(
        (node: HTMLDivElement | null) => {
            ref.current = node;
            return wheelRef(node);
        },
        [wheelRef],
    );

    useEffect(() => {
        if (!open) return;
        const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
        const onDown = (e: PointerEvent) => {
            const target = e.target as Node;
            // Clique no proprio botao que abriu o painel e tratado por ele.
            if (ref.current && !ref.current.contains(target) && !(target as Element).closest?.("[data-control-trigger]")) onClose();
        };
        document.addEventListener("keydown", onKey);
        document.addEventListener("pointerdown", onDown);
        return () => {
            document.removeEventListener("keydown", onKey);
            document.removeEventListener("pointerdown", onDown);
        };
    }, [open, onClose]);

    return (
        <AnimatePresence>
            {open && (
                <motion.div
                    ref={panelRef}
                    data-map-scroll-viewport
                    role="dialog"
                    aria-label={title}
                    initial={{ opacity: 0, x: 8, scale: 0.98 }}
                    animate={{ opacity: 1, x: 0, scale: 1 }}
                    exit={{ opacity: 0, x: 6, scale: 0.98, transition: { duration: 0.12 } }}
                    transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
                    className={cx(
                        "pointer-events-auto absolute top-0 right-11 z-30 max-h-80 origin-top-right overflow-y-auto overscroll-contain rounded-xl bg-primary p-3 shadow-lg ring-1 ring-secondary_alt",
                        className,
                    )}
                >
                    <div className="mb-2.5 flex items-center justify-between gap-3">
                        <p className="text-xs font-semibold text-primary">{title}</p>
                        <button
                            type="button"
                            aria-label={t("hydro.popup.close")}
                            onClick={onClose}
                            className="cursor-pointer rounded-md p-0.5 text-fg-quaternary hover:bg-primary_hover hover:text-fg-secondary"
                        >
                            <XClose className="size-4" />
                        </button>
                    </div>
                    {children}
                </motion.div>
            )}
        </AnimatePresence>
    );
};

// ---------- Galeria de mapas base ----------

export const BasemapGallery = ({ value, onChange }: { value: BasemapId; onChange: (id: BasemapId) => void }) => {
    const { t } = useTranslation();
    return (
        <ul className="grid w-full grid-cols-2 gap-2">
            {BASEMAPS.map((b) => {
                const selected = b.id === value;
                return (
                    <li key={b.id}>
                        <button
                            type="button"
                            onClick={() => onChange(b.id)}
                            aria-pressed={selected}
                            className={cx(
                                "group relative block w-full cursor-pointer overflow-hidden rounded-lg text-left ring-1 transition duration-150 ease-out active:scale-[0.98]",
                                selected ? "ring-2 ring-brand" : "ring-secondary hover:ring-primary",
                            )}
                        >
                            <span className="block aspect-[16/9] overflow-hidden bg-secondary">
                                {b.thumb ? (
                                    <img src={b.thumb} alt="" loading="lazy" className="size-full object-cover transition duration-300 group-hover:scale-105" />
                                ) : (
                                    <span className="block size-full bg-dots" />
                                )}
                            </span>
                            <span className="flex items-center justify-between gap-1 px-2 py-1.5 text-xs font-medium text-secondary">
                                {t(b.label)}
                                {selected && <Check className="size-3.5 text-fg-brand-primary" aria-hidden="true" />}
                            </span>
                        </button>
                    </li>
                );
            })}
        </ul>
    );
};

// ---------- Camadas ----------

export const LayerToggle = ({ label, hint, isSelected, onChange }: { label: string; hint?: string; isSelected: boolean; onChange: (v: boolean) => void }) => (
    <div className="flex items-start justify-between gap-4 py-1.5">
        <div className="min-w-0">
            <p className="text-sm font-medium text-secondary">{label}</p>
            {hint && <p className="text-xs text-tertiary">{hint}</p>}
        </div>
        <Toggle size="sm" isSelected={isSelected} onChange={onChange} aria-label={label} />
    </div>
);

// ---------- Legenda (canto inferior esquerdo, recolhivel) ----------

const COLLAPSE_TRANSITION = { duration: 0.24, ease: [0.22, 1, 0.36, 1] as const };

/** Keeps nested accordions mounted while animating the visible, constrained height. */
export const CollapsiblePanel = ({
    id,
    open,
    children,
    className,
    scrollable = true,
}: {
    id: string;
    open: boolean;
    children: ReactNode;
    className?: string;
    scrollable?: boolean;
}) => {
    const ref = useRef<HTMLDivElement>(null);
    const mounted = useRef(false);
    const controls = useAnimationControls();
    const reducedMotion = useReducedMotion();

    useLayoutEffect(() => {
        const panel = ref.current;
        if (!panel) return;

        controls.stop();
        if (!mounted.current || reducedMotion) {
            mounted.current = true;
            controls.set({ height: open ? "auto" : 0, opacity: open ? 1 : 0 });
            return;
        }

        const currentHeight = panel.getBoundingClientRect().height;
        const currentOpacity = Number.parseFloat(getComputedStyle(panel).opacity);
        let targetHeight = 0;

        if (open) {
            // Measure the final flex-clamped size, rather than the full scroll height.
            const previousHeight = panel.style.height;
            panel.style.height = "auto";
            targetHeight = panel.getBoundingClientRect().height;
            panel.style.height = previousHeight;
        }

        let cancelled = false;
        controls.set({ height: currentHeight, opacity: currentOpacity });
        void controls.start({ height: targetHeight, opacity: open ? 1 : 0, transition: COLLAPSE_TRANSITION }).then(() => {
            // Natural height lets nested topic animations and new API data resize the panel.
            if (!cancelled && open) controls.set({ height: "auto" });
        });

        return () => {
            cancelled = true;
            controls.stop();
        };
    }, [controls, open, reducedMotion]);

    return (
        <motion.div ref={ref} id={id} initial={false} animate={controls} inert={!open} aria-hidden={!open} className="flex min-h-0 flex-col overflow-hidden">
            <div
                data-map-scroll-viewport={scrollable ? "" : undefined}
                className={cx("min-h-0", scrollable ? "overflow-y-auto overscroll-contain" : "overflow-visible", className)}
            >
                {children}
            </div>
        </motion.div>
    );
};

export const Legend = ({
    title,
    open,
    onOpenChange,
    footer,
    headerLabel,
    inline = false,
    scrollable = true,
    className,
    children,
}: {
    title: string;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    footer?: ReactNode;
    headerLabel?: string;
    inline?: boolean;
    scrollable?: boolean;
    className?: string;
    children: ReactNode;
}) => {
    const { t } = useTranslation();
    const panelId = useId();
    const triggerRef = useRef<HTMLButtonElement>(null);
    const wheelRef = useMapPanelWheel();

    return (
        <div
            ref={wheelRef}
            role="region"
            aria-label={headerLabel ?? t("hydro.legend.title")}
            onKeyDown={(event) => {
                event.stopPropagation();
                if (event.key === "Escape" && open) {
                    event.preventDefault();
                    onOpenChange(false);
                    triggerRef.current?.focus();
                }
            }}
            className={cx(
                "pointer-events-auto flex min-h-0 flex-col overflow-hidden rounded-xl bg-primary ring-1 ring-secondary",
                inline
                    ? "w-full"
                    : "absolute bottom-3 left-3 z-20 max-h-[calc(100%-1.5rem)] w-60 max-w-[calc(100%-1.5rem)] bg-primary/95 shadow-lg ring-secondary_alt",
                className,
            )}
        >
            <button
                ref={triggerRef}
                type="button"
                onClick={() => onOpenChange(!open)}
                aria-expanded={open}
                aria-controls={panelId}
                className="flex w-full shrink-0 cursor-pointer items-center justify-between gap-2 px-3 py-2 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus-ring"
            >
                <span className="min-w-0">
                    <span className="block text-[11px] font-medium tracking-wider text-quaternary uppercase">{headerLabel ?? t("hydro.legend.title")}</span>
                    <span className="block truncate text-xs font-semibold text-primary" title={title}>
                        {title}
                    </span>
                </span>
                {open ? (
                    <ChevronDown aria-hidden="true" className="size-4 shrink-0 text-fg-quaternary" />
                ) : (
                    <ChevronUp aria-hidden="true" className="size-4 shrink-0 text-fg-quaternary" />
                )}
            </button>
            <CollapsiblePanel id={panelId} open={open} scrollable={scrollable} className="border-t border-secondary">
                {children}
                {footer && <div className="border-t border-secondary px-3 py-2 text-[11px] text-quaternary">{footer}</div>}
            </CollapsiblePanel>
        </div>
    );
};

export const LegendTopic = ({ title, children, defaultOpen = true }: { title: string; children: ReactNode; defaultOpen?: boolean }) => {
    const [open, setOpen] = useState(defaultOpen);
    const panelId = useId();
    const headingId = useId();

    return (
        <section aria-labelledby={headingId} className="border-b border-secondary last:border-b-0">
            <h3>
                <button
                    id={headingId}
                    type="button"
                    aria-expanded={open}
                    aria-controls={panelId}
                    onClick={() => setOpen((current) => !current)}
                    className="flex min-h-10 w-full cursor-pointer items-center justify-between gap-2 px-3 py-2 text-left text-xs font-semibold text-secondary transition hover:bg-primary_hover focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus-ring"
                >
                    <span className="min-w-0 truncate">{title}</span>
                    {open ? (
                        <ChevronUp aria-hidden="true" className="size-3.5 shrink-0 text-fg-quaternary" />
                    ) : (
                        <ChevronDown aria-hidden="true" className="size-3.5 shrink-0 text-fg-quaternary" />
                    )}
                </button>
            </h3>
            <CollapsiblePanel id={panelId} open={open} scrollable={false}>
                {children}
            </CollapsiblePanel>
        </section>
    );
};

export const LegendClasses = ({
    classes,
    counts,
    hidden,
    onToggleClass,
}: {
    classes: ClassDef[];
    counts: Record<string, number>;
    hidden: string[];
    onToggleClass: (key: string) => void;
}) => {
    const { t, i18n } = useTranslation();
    const fmt = new Intl.NumberFormat(i18n.language);

    return (
        <ul className="px-1.5 pb-1.5" title={t("hydro.legend.toggleHint")}>
            {classes.map((item) => {
                const off = hidden.includes(item.key);
                return (
                    <li key={item.key}>
                        <button
                            type="button"
                            aria-pressed={!off}
                            onClick={() => onToggleClass(item.key)}
                            className={cx(
                                "flex min-h-8 w-full cursor-pointer items-center gap-2 rounded-md px-1.5 py-1 text-left text-xs transition duration-100 ease-linear hover:bg-primary_hover focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus-ring",
                                off && "opacity-50",
                            )}
                        >
                            <span
                                aria-hidden="true"
                                className={cx(
                                    "flex size-3.5 shrink-0 items-center justify-center rounded border",
                                    off ? "border-secondary bg-primary" : "border-brand-solid bg-brand-solid",
                                )}
                            >
                                {!off && <Check className="size-2.5 text-fg-white" />}
                            </span>
                            <span aria-hidden="true" className="size-2.5 shrink-0 rounded-full ring-1 ring-black/10" style={{ background: item.color }} />
                            <span className="min-w-0 flex-1 truncate text-secondary">{t(item.label)}</span>
                            <span className="text-quaternary tabular-nums">{fmt.format(counts[item.key] ?? 0)}</span>
                        </button>
                    </li>
                );
            })}
        </ul>
    );
};
