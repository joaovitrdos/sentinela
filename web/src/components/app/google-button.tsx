import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { notify } from "@/components/app/notifications";
import { cx } from "@/utils/cx";

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();
const SCRIPT_SRC = "https://accounts.google.com/gsi/client";

interface GoogleIdApi {
    initialize: (config: { client_id: string; callback: (response: { credential?: string }) => void; ux_mode?: "popup" }) => void;
    renderButton: (parent: HTMLElement, options: Record<string, string | number>) => void;
}

declare global {
    interface Window {
        google?: { accounts: { id: GoogleIdApi } };
    }
}

let scriptPromise: Promise<void> | null = null;
const loadGoogleScript = () => {
    scriptPromise ??= new Promise<void>((resolve, reject) => {
        const script = document.createElement("script");
        script.src = SCRIPT_SRC;
        script.async = true;
        script.defer = true;
        script.onload = () => resolve();
        script.onerror = () => {
            scriptPromise = null;
            reject(new Error("Google Identity Services indisponivel"));
        };
        document.head.appendChild(script);
    });
    return scriptPromise;
};

// O callback muda a cada render; o Google so aceita um por initialize.
let currentCallback: (idToken: string) => void = () => {};

interface GoogleButtonProps {
    onCredential: (idToken: string) => void;
    isDisabled?: boolean;
    className?: string;
}

/**
 * Usa o botao oficial do Google para manter o mesmo controle acessível que recebe o clique.
 */
export const GoogleButton = ({ onCredential, isDisabled, className }: GoogleButtonProps) => {
    const { t, i18n } = useTranslation();
    const wrapperRef = useRef<HTMLDivElement>(null);
    const overlayRef = useRef<HTMLDivElement>(null);
    const [ready, setReady] = useState(false);

    currentCallback = onCredential;

    useEffect(() => {
        if (!CLIENT_ID) return;
        let cancelled = false;

        const render = () => {
            const google = window.google?.accounts.id;
            const overlay = overlayRef.current;
            const wrapper = wrapperRef.current;
            if (!google || !overlay || !wrapper || cancelled) return;
            overlay.innerHTML = "";
            google.renderButton(overlay, {
                type: "standard",
                size: "large",
                text: "continue_with",
                shape: "rectangular",
                width: Math.min(400, Math.max(200, Math.round(wrapper.offsetWidth))),
                locale: i18n.language,
            });
            setReady(true);
        };

        loadGoogleScript()
            .then(() => {
                window.google?.accounts.id.initialize({
                    client_id: CLIENT_ID,
                    ux_mode: "popup",
                    callback: ({ credential }) => credential && currentCallback(credential),
                });
                render();
            })
            .catch(() => {
                if (!cancelled) {
                    setReady(false);
                    notify(t("errors.googleUnavailable"));
                }
            });

        const observer = new ResizeObserver(() => render());
        if (wrapperRef.current) observer.observe(wrapperRef.current);

        return () => {
            cancelled = true;
            observer.disconnect();
        };
    }, [i18n.language, t]);

    const unavailable = !CLIENT_ID;

    return (
        <div ref={wrapperRef} className={cx("relative", className)} title={unavailable ? t("auth.googleNotConfigured") : undefined}>
            {!unavailable && (
                <div
                    ref={overlayRef}
                    className={cx(
                        "flex min-h-11 w-full items-center justify-center overflow-hidden",
                        (isDisabled || !ready) && "pointer-events-none",
                    )}
                    aria-hidden={!ready}
                    inert={isDisabled || !ready}
                />
            )}
            {unavailable && <div aria-disabled="true" className="flex min-h-11 w-full items-center justify-center rounded-lg border border-secondary text-sm font-semibold text-tertiary">{t("auth.google")}</div>}
        </div>
    );
};
