import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig, loadEnv } from "vite";

const PUBLIC_ENV_NAMES = ["VITE_API_URL", "VITE_GOOGLE_CLIENT_ID", "VITE_LEGAL_COMPANY", "VITE_LEGAL_CONTACT_EMAIL", "VITE_TIME_ZONE"] as const;

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, import.meta.dirname, "");

    return {
        plugins: [react(), tailwindcss()],
        // Only these public settings may enter browser code. Provider credentials stay on the server.
        envPrefix: [],
        define: Object.fromEntries(
            PUBLIC_ENV_NAMES.map((name) => [`import.meta.env.${name}`, env[name] === undefined ? "undefined" : JSON.stringify(env[name])]),
        ),
        build: {
            sourcemap: false,
        },
        resolve: {
            alias: {
                "@": path.resolve(import.meta.dirname, "./src"),
            },
        },
        server: {
            // Em desenvolvimento o front chama /api e o Vite repassa para o Spring Boot (sem CORS).
            proxy: {
                "/api": {
                    target: env.VITE_API_PROXY_TARGET || "http://localhost:8080",
                    changeOrigin: true,
                    rewrite: (p) => p.replace(/^\/api/, ""),
                },
            },
        },
    };
});
