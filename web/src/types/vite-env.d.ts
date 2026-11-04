/// <reference types="vite/client" />

interface ViteTypeOptions {
    strictImportMetaEnv: unknown;
}

interface ImportMetaEnv {
    readonly VITE_API_URL?: string;
    readonly VITE_GOOGLE_CLIENT_ID?: string;
    readonly VITE_LEGAL_COMPANY?: string;
    readonly VITE_LEGAL_CONTACT_EMAIL?: string;
    readonly VITE_TIME_ZONE?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
