import type { ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { apiSession } from "@/lib/api";
import { authApi, userApi } from "@/lib/endpoints";
import type { AuthResponse, User } from "@/lib/types";

const STORAGE_KEY = "sentinela-session";

interface Session {
    token: string;
    user: User;
}

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    /** true enquanto confere no backend se o token salvo ainda vale. */
    isRestoring: boolean;
    /** true quando a sessao caiu sozinha (token expirado ou revogado). */
    sessionExpired: boolean;
    signIn: (auth: AuthResponse) => void;
    signOut: () => Promise<void>;
    /** Limpa a sessao local sem chamar o backend (ex.: depois de logout-all ou exclusao da conta). */
    clearSession: () => void;
    setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const readSession = (): Session | null => {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? (JSON.parse(raw) as Session) : null;
    } catch {
        return null;
    }
};

const writeSession = (session: Session | null) => {
    try {
        if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        else localStorage.removeItem(STORAGE_KEY);
    } catch {
        // Modo privado ou storage bloqueado: a sessao vale so enquanto a aba estiver aberta.
    }
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [session, setSession] = useState<Session | null>(readSession);
    const [isRestoring, setIsRestoring] = useState(() => session !== null);
    const [sessionExpired, setSessionExpired] = useState(false);

    const update = useCallback((next: Session | null) => {
        writeSession(next);
        setSession(next);
    }, []);

    const clearSession = useCallback(() => update(null), [update]);

    // O cliente HTTP le o token daqui e avisa quando o backend recusa a sessao.
    apiSession.getToken = () => session?.token ?? null;
    apiSession.onSessionRejected = () => {
        setSessionExpired(true);
        update(null);
    };

    // Ao abrir o app com um token salvo, confirma no backend e atualiza os dados do usuario.
    useEffect(() => {
        if (!isRestoring) return;
        const controller = new AbortController();
        userApi
            .me(controller.signal)
            .then((user) => setSession((s) => (s ? { ...s, user } : s)))
            .catch(() => undefined)
            .finally(() => setIsRestoring(false));
        return () => controller.abort();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const signIn = useCallback(
        (auth: AuthResponse) => {
            setSessionExpired(false);
            update({ token: auth.accessToken, user: auth.user });
        },
        [update],
    );

    const signOut = useCallback(async () => {
        // O backend incrementa tokenVersion antes de limpar o token local.
        await authApi.logout().catch(() => undefined);
        update(null);
    }, [update]);

    const setUser = useCallback((user: User) => setSession((s) => (s ? (writeSession({ ...s, user }), { ...s, user }) : s)), []);

    const value = useMemo<AuthContextType>(
        () => ({
            user: session?.user ?? null,
            isAuthenticated: session !== null,
            isRestoring,
            sessionExpired,
            signIn,
            signOut,
            clearSession,
            setUser,
        }),
        [session, isRestoring, sessionExpired, signIn, signOut, clearSession, setUser],
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
    const context = useContext(AuthContext);
    if (!context) throw new Error("useAuth must be used within an AuthProvider");
    return context;
};
