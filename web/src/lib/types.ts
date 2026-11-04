// Espelho dos DTOs do backend (br.com.sentinela.dto).

export type AuthProvider = "LOCAL" | "GOOGLE";

export interface User {
    userId: string;
    name: string;
    email: string;
    picture: string | null;
    authProvider: AuthProvider;
    emailVerified: boolean;
}

export interface AuthResponse {
    accessToken: string;
    tokenType: string;
    user: User;
}

export interface MessageResponse {
    message: string;
}
