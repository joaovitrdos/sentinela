import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { AlertCircle, CheckCircle, InfoCircle } from "@untitledui/icons";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";

export type NotificationType = "error" | "success" | "info";
type Notification = { id: number; type: NotificationType; message: ReactNode; key?: string };
const eventName = "sentinela:notification";
let nextId = 0;

export function notify(message: ReactNode, type: NotificationType = "error", key?: string) {
    window.dispatchEvent(new CustomEvent(eventName, { detail: { id: ++nextId, type, message, key } }));
}

export function Notifications() {
    const [items, setItems] = useState<Notification[]>([]);
    const timers = useRef(new Set<number>());
    const { t } = useTranslation();
    const reducedMotion = useReducedMotion();
    useEffect(() => {
        const activeTimers = timers.current;
        const receive = (event: Event) => {
            const item = (event as CustomEvent<Notification>).detail;
            setItems((current) => [...current.filter((entry) => item.key ? entry.key !== item.key : !(entry.message === item.message && entry.type === item.type)), item].slice(-5));
            const timer = window.setTimeout(() => {
                activeTimers.delete(timer);
                setItems((current) => current.filter((entry) => entry.id !== item.id));
            }, 7000);
            activeTimers.add(timer);
        };
        window.addEventListener(eventName, receive);
        return () => {
            window.removeEventListener(eventName, receive);
            activeTimers.forEach(window.clearTimeout);
            activeTimers.clear();
        };
    }, []);
    const colors = {
        error: { root: "border-red-500 bg-error-primary text-error-primary", icon: AlertCircle },
        success: { root: "border-green-500 bg-success-primary text-success-primary", icon: CheckCircle },
        info: { root: "border-orange-500 bg-warning-primary text-warning-primary", icon: InfoCircle },
    };
    return createPortal(<div className="pointer-events-none fixed right-4 top-4 z-[10000] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
        <AnimatePresence initial={false}>
            {items.map((item) => {
                const Icon = colors[item.type].icon;
                return <motion.div key={item.id} layout={!reducedMotion} role={item.type === "error" ? "alert" : "status"}
                    initial={{ opacity: 0, x: reducedMotion ? 0 : 24 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: reducedMotion ? 0 : 16 }}
                    transition={{ duration: reducedMotion ? 0.12 : 0.28, ease: [0.22, 1, 0.36, 1] }}
                    className={`pointer-events-auto flex items-start gap-3 rounded-lg border-l-4 p-4 text-sm shadow-lg ${colors[item.type].root}`}>
                    <Icon className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
                    <span className="min-w-0 flex-1">{item.message}</span>
                    <button type="button" aria-label={t("common.close")} onClick={() => setItems((current) => current.filter((entry) => entry.id !== item.id))} className="text-lg leading-none">×</button>
                </motion.div>;
            })}
        </AnimatePresence>
    </div>, document.body);
}
