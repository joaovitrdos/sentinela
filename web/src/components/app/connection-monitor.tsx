import { useEffect } from "react";
import i18n from "@/i18n";
import { apiUrl } from "@/lib/api";
import { notify } from "@/components/app/notifications";

type ConnectionState = "GOOD" | "UNSTABLE" | "POOR";
type ConnectionStatus = { state: ConnectionState; previousState: ConnectionState | null; latencyMs: number };
type NetworkInformation = EventTarget & { effectiveType?: string };

const CONFIG = {
    intervalMs: 15_000,
    timeoutMs: 5_000,
    goodLatencyMs: 1_000,
    confirmations: 2,
    alertIntervalMs: 30_000,
};

/** Observa a comunicação do navegador com o servidor, inclusive nas telas públicas. */
export function ConnectionMonitor() {
    useEffect(() => {
        let stopped = false;
        let currentState: ConnectionState | null = null;
        let observedState: ConnectionState | null = null;
        let repetitions = 0;
        let lastAlert = 0;
        let timer: number | undefined;
        let probeController: AbortController | null = null;
        let reportController: AbortController | null = null;
        let probeAgain = false;
        let pending: ConnectionStatus | null = null;
        let reporting = false;
        const network = (navigator as Navigator & { connection?: NetworkInformation }).connection;

        const sendPending = async () => {
            if (stopped || reporting || !pending || navigator.onLine === false) return;
            const status = pending;
            reporting = true;
            const controller = new AbortController();
            reportController = controller;
            const timeout = window.setTimeout(() => controller.abort(), CONFIG.timeoutMs);
            try {
                const response = await fetch(apiUrl("/api/connection/status"), {
                    method: "POST", headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(status), cache: "no-store", signal: controller.signal,
                });
                if (response.ok && pending === status) pending = null;
            } catch {
                // A mudança permanece pendente até que o servidor responda novamente.
            } finally {
                window.clearTimeout(timeout);
                if (reportController === controller) reportController = null;
                reporting = false;
                if (!stopped && pending && pending !== status) void sendPending();
            }
        };

        const register = (state: ConnectionState, latencyMs: number, immediate = false) => {
            if (stopped) return;
            repetitions = state === observedState ? repetitions + 1 : 1;
            observedState = state;
            if (!immediate && state !== "GOOD" && repetitions < CONFIG.confirmations) return;
            if (state === currentState) return;
            const previousState = currentState;
            currentState = state;
            if (previousState === null && state === "GOOD") return;

            pending = { state, previousState: pending?.state ?? previousState, latencyMs };
            if (state === "GOOD" || Date.now() - lastAlert >= CONFIG.alertIntervalMs) {
                lastAlert = state === "GOOD" ? 0 : Date.now();
                const key = state === "GOOD" ? "connection.restored" : state === "UNSTABLE" ? "connection.unstable" : "connection.disconnected";
                notify(i18n.t(key), state === "GOOD" ? "success" : state === "UNSTABLE" ? "info" : "error", "connection");
            }
        };

        const probe = async () => {
            if (stopped || document.hidden) return;
            if (probeController) {
                probeAgain = true;
                return;
            }
            if (navigator.onLine === false) {
                register("POOR", -1, true);
                return;
            }
            const controller = new AbortController();
            probeController = controller;
            const start = performance.now();
            let timedOut = false;
            const timeout = window.setTimeout(() => { timedOut = true; controller.abort(); }, CONFIG.timeoutMs);
            try {
                await fetch(apiUrl("/api/connection/probe"), { method: "HEAD", cache: "no-store", signal: controller.signal });
                if (stopped || document.hidden || controller.signal.aborted) return;
                const latencyMs = Math.min(60_000, Math.round(performance.now() - start));
                const slowNetwork = network?.effectiveType === "2g" || network?.effectiveType === "slow-2g";
                register(latencyMs <= CONFIG.goodLatencyMs && !slowNetwork ? "GOOD" : "UNSTABLE", latencyMs);
                void sendPending();
            } catch {
                if (!stopped && !document.hidden && (timedOut || !controller.signal.aborted)) register("POOR", -1);
            } finally {
                window.clearTimeout(timeout);
                if (probeController === controller) probeController = null;
                if (probeAgain && !stopped && !document.hidden) {
                    probeAgain = false;
                    void probe();
                }
            }
        };

        const start = () => {
            if (timer !== undefined) window.clearInterval(timer);
            if (document.hidden) return;
            timer = window.setInterval(() => void probe(), CONFIG.intervalMs);
            void probe();
        };
        const offline = () => {
            probeController?.abort();
            register("POOR", -1, true);
        };
        const online = () => void probe();
        const visibilityChange = () => {
            if (document.hidden) {
                if (timer !== undefined) window.clearInterval(timer);
                timer = undefined;
                probeController?.abort();
            } else start();
        };
        window.addEventListener("offline", offline);
        window.addEventListener("online", online);
        document.addEventListener("visibilitychange", visibilityChange);
        network?.addEventListener("change", online);
        start();
        return () => {
            stopped = true;
            if (timer !== undefined) window.clearInterval(timer);
            probeController?.abort();
            reportController?.abort();
            window.removeEventListener("offline", offline);
            window.removeEventListener("online", online);
            document.removeEventListener("visibilitychange", visibilityChange);
            network?.removeEventListener("change", online);
        };
    }, []);
    return null;
}
