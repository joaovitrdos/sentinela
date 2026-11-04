import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { SocialButton } from "@/components/base/buttons/social-button";
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
 * Botao "Continuar com o Google" no visual do Untitled UI.
 * O botao oficial do Google (que gera o ID token) fica por cima, transparente, e recebe o clique.
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
            <SocialButton social="google" theme="brand" size="md" className="w-full" isDisabled={unavailable || isDisabled || !ready} aria-hidden={ready}>
                {t("auth.google")}
            </SocialButton>
            {!unavailable && (
                <div
                    ref={overlayRef}
                    className={cx(
                        "absolute inset-0 flex items-center justify-center overflow-hidden opacity-[0.011]",
                        (isDisabled || !ready) && "pointer-events-none",
                    )}
                />
            )}
        </div>
    );
};
