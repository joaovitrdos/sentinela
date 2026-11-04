import { api } from "./api";
import type { AuthResponse, MessageResponse, User } from "./types";

// AuthController (/auth)
export const authApi = {
    register: (body: { name: string; email: string; password: string }) => api<MessageResponse>("/auth/register", { method: "POST", body }),
    confirmEmail: (body: { email: string; code: string }) => api<AuthResponse>("/auth/email/confirm", { method: "POST", body }),
    resendVerification: (body: { email: string }) => api<MessageResponse>("/auth/email/resend", { method: "POST", body }),
    login: (body: { email: string; password: string }) => api<AuthResponse>("/auth/login", { method: "POST", body }),
    loginWithGoogle: (body: { idToken: string }) => api<AuthResponse>("/auth/google", { method: "POST", body }),
    logout: () => api("/auth/logout", { method: "POST" }),
    logoutEverywhere: () => api("/auth/logout-all", { method: "POST" }),
    forgotPassword: (body: { email: string }) => api<MessageResponse>("/auth/password/forgot", { method: "POST", body }),
    validateRecoveryCode: (body: { email: string; code: string }) => api("/auth/password/validate", { method: "POST", body }),
    resetPassword: (body: { email: string; code: string; newPassword: string }) => api<MessageResponse>("/auth/password/reset", { method: "POST", body }),
};

// UserController (/users)
export const userApi = {
    me: (signal?: AbortSignal) => api<User>("/users/me", { signal }),
    update: (body: { name: string; picture: string }) => api<User>("/users/me", { method: "PUT", body }),
    delete: () => api("/users/me", { method: "DELETE" }),
    requestEmailChange: (body: { newEmail: string; currentPassword: string }) => api<MessageResponse>("/users/me/email", { method: "POST", body }),
    confirmEmailChange: (body: { code: string }) => api<AuthResponse>("/users/me/email/confirm", { method: "POST", body }),
    changePassword: (body: { currentPassword: string; newPassword: string }) => api<AuthResponse>("/users/change-password", { method: "POST", body }),
    count: (signal?: AbortSignal) => api<number>("/users/count", { signal }),
};
