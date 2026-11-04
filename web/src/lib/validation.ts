// Mesmas regras dos DTOs do backend (@Email, @Size, @Pattern), validadas antes de enviar.

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE = /^\d{8}$/;
const URL = /^https?:\/\/\S+$/;

/** Retorna a chave i18n do erro ou undefined se estiver ok. */
export const validate = {
    required: (v: string) => (v.trim() ? undefined : "validation.required"),
    email: (v: string) => (!v.trim() ? "validation.required" : EMAIL.test(v.trim()) ? undefined : "validation.email"),
    password: (v: string) => (!v ? "validation.required" : v.length < 8 || v.length > 72 ? "validation.passwordLength" : undefined),
    name: (v: string) => (!v.trim() ? "validation.required" : v.trim().length > 120 ? "validation.nameMax" : undefined),
    code: (v: string) => (CODE.test(v) ? undefined : "validation.code"),
    pictureUrl: (v: string) => (!v.trim() ? undefined : v.trim().length > 512 ? "validation.urlMax" : URL.test(v.trim()) ? undefined : "validation.url"),
};

export type FieldErrors<K extends string> = Partial<Record<K, string>>;

/** Roda as regras e devolve so os campos com erro. */
export const check = <K extends string>(rules: Record<K, string | undefined>): FieldErrors<K> =>
    Object.fromEntries(Object.entries(rules).filter(([, v]) => v)) as FieldErrors<K>;
