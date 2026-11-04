import type { ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { SYSTEM_MAINTENANCE_EVENT, api } from "@/lib/api";

export interface SystemStatus {
    maintenance: boolean;
    version: string | null;
    maintenanceStart: string | null;
    maintenanceEnd: string | null;
    timeZone: string;
}

interface SystemContextValue {
    status: SystemStatus | null;
    ready: boolean;
    checking: boolean;
    failed: boolean;
    refresh: () => Promise<void>;
}

const SystemContext = createContext<SystemContextValue | undefined>(undefined);

export const SystemProvider = ({ children }: { children: ReactNode }) => {
    const [status, setStatus] = useState<SystemStatus | null>(null);
    const [ready, setReady] = useState(false);
    const [checking, setChecking] = useState(false);
    const [failed, setFailed] = useState(false);
    const request = useRef<AbortController | null>(null);

    const refresh = useCallback(async () => {
        request.current?.abort();
        const controller = new AbortController();
        request.current = controller;
        const timeout = window.setTimeout(() => controller.abort(), 10_000);
        setChecking(true);
        try {
            const next = await api<SystemStatus>("/api/system/status", { signal: controller.signal });
            if (request.current !== controller) return;
            if (typeof next.maintenance !== "boolean") throw new Error("Invalid system status response");
            setStatus(next);
            setFailed(false);
        } catch {
            // A disconnected weather/SNIRH/backend API must not masquerade as scheduled maintenance.
            if (request.current === controller) setFailed(true);
        } finally {
            window.clearTimeout(timeout);
            if (request.current === controller) {
                request.current = null;
                setChecking(false);
                setReady(true);
            }
        }
    }, []);

    useEffect(() => {
        const check = () => {
            void refresh();
        };
        const maintenance = () => {
            setStatus((previous) => ({
                maintenance: true,
                version: previous?.version ?? null,
                maintenanceStart: previous?.maintenanceStart ?? null,
                maintenanceEnd: previous?.maintenanceEnd ?? null,
                timeZone: previous?.timeZone ?? "America/Sao_Paulo",
            }));
            check();
        };
        check();
        const timer = window.setInterval(check, 30_000);
        window.addEventListener("online", check);
        window.addEventListener("focus", check);
        window.addEventListener(SYSTEM_MAINTENANCE_EVENT, maintenance);
        return () => {
            window.clearInterval(timer);
            window.removeEventListener("online", check);
            window.removeEventListener("focus", check);
            window.removeEventListener(SYSTEM_MAINTENANCE_EVENT, maintenance);
            const pending = request.current;
            request.current = null;
            pending?.abort();
        };
    }, [refresh]);

    return <SystemContext.Provider value={{ status, ready, checking, failed, refresh }}>{children}</SystemContext.Provider>;
};

export const useSystem = () => {
    const context = useContext(SystemContext);
    if (!context) throw new Error("useSystem must be used inside SystemProvider");
    return context;
};
