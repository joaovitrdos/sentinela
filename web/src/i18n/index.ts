import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import legalEn from "./legal/en.json";
import legalEs from "./legal/es.json";
import legalPtBR from "./legal/pt-BR.json";
import en from "./locales/en.json";
import es from "./locales/es.json";
import ptBR from "./locales/pt-BR.json";

export const LANGUAGES = ["pt-BR", "en", "es"] as const;
export type Language = (typeof LANGUAGES)[number];

/** Converte o que o navegador informa (pt, pt-PT, es-AR, en-US...) para um dos idiomas suportados. */
const normalizeLanguage = (lng: string): Language => {
    const base = lng.toLowerCase().split(/[-_]/)[0];
    if (base === "en") return "en";
    if (base === "es") return "es";
    return "pt-BR";
};

i18n.use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources: {
            "pt-BR": { translation: ptBR, legal: legalPtBR },
            en: { translation: en, legal: legalEn },
            es: { translation: es, legal: legalEs },
        },
        supportedLngs: LANGUAGES,
        fallbackLng: "pt-BR",
        load: "currentOnly",
        interpolation: { escapeValue: false },
        detection: {
            order: ["localStorage", "navigator", "htmlTag"],
            lookupLocalStorage: "sentinela-lang",
            caches: ["localStorage"],
            convertDetectedLanguage: normalizeLanguage,
        },
    });

const syncHtmlLang = (lng: string) => document.documentElement.setAttribute("lang", lng);
syncHtmlLang(i18n.resolvedLanguage ?? "pt-BR");
i18n.on("languageChanged", syncHtmlLang);

export const currentLanguage = (): Language => normalizeLanguage(i18n.resolvedLanguage ?? i18n.language ?? "pt-BR");

export default i18n;
