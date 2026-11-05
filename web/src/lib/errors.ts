import { ApiError } from "./api";

// Mensagens fixas das excecoes do backend -> chave de traducao.
// Assim o erro aparece no idioma escolhido, nao so em portugues.
const SERVER_MESSAGES: Record<string, string> = {
    "E-mail ou senha inválidos": "errors.invalidCredentials",
    "Confirme seu e-mail antes de entrar. Verifique sua caixa de entrada": "errors.emailNotVerified",
    "Este e-mail já está em uso. Tente outro e-mail": "errors.emailTaken",
    "Contas do Google não possuem senha para alterar": "errors.googleNoPassword",
    "Contas do Google usam o e-mail da conta Google e não podem trocá-lo aqui": "errors.googleEmailChange",
    "Código de verificação inválido": "errors.invalidCode",
    "Código de verificação expirado. Solicite um novo código": "errors.expiredCode",
    "Muitas tentativas com o código. Solicite um novo código mais tarde": "errors.tooManyCodeAttempts",
    "Muitos códigos solicitados. Tente novamente mais tarde": "errors.tooManyCodes",
    "Muitas tentativas de login. Tente novamente mais tarde": "errors.tooManyLogins",
    "A senha informada está incorreta": "errors.incorrectPassword",
    "Token do Google inválido ou expirado": "errors.googleInvalid",
    "O e-mail da conta Google não foi verificado pelo Google": "errors.googleEmailNotVerified",
    "Não foi possível validar o token do Google. Tente novamente em instantes": "errors.googleUnavailable",
    "Usuário não encontrado": "errors.userNotFound",
    "Falha ao enviar o e-mail": "errors.emailSend",
    "Nome, e-mail e senha são obrigatórios": "errors.invalidData",
    "Não foi possível excluir a conta. Tente novamente": "errors.deleteFailed",
    "Não foi possível salvar os dados do usuário. Tente novamente": "errors.saveFailed",
    "Não foi possível gerar o código de verificação. Tente novamente": "errors.saveFailed",
    "Autenticação necessária para acessar este recurso": "errors.sessionExpired",
    "Serviço de clima indisponível. Tente novamente em instantes": "weather.loadError",
    "Seu plano não inclui este recurso": "errors.planRequired",
    "Informe um CPF ou CNPJ válido": "errors.invalidDocument",
    "Pagamentos ainda não configurados": "errors.paymentsUnavailable",
    "Pagamentos indisponíveis. Tente novamente em instantes": "errors.paymentsUnavailable",
    "Nenhuma assinatura ativa para cancelar": "errors.noSubscription",
};

const BY_STATUS: Record<number, string> = {
    0: "errors.network",
    400: "errors.invalidData",
    401: "errors.sessionExpired",
    403: "errors.emailNotVerified",
    // Só erros com a mensagem de usuário desconhecido devem ser rotulados assim.
    404: "errors.generic",
    409: "errors.emailTaken",
    429: "errors.rateLimited",
    // Sem mensagem conhecida, 502/503/504 vem do proxy/gateway: o backend esta fora do ar.
    // (Os 502/503 do proprio backend tem mensagem e sao resolvidos em SERVER_MESSAGES.)
    502: "errors.network",
    503: "errors.network",
    504: "errors.network",
};

/** Chave i18n da mensagem de erro para mostrar ao usuario. */
export const errorKey = (error: unknown): string => {
    if (!(error instanceof ApiError)) return "errors.generic";
    const message = error.serverMessage.trim();
    if (message.startsWith("Seu plano permite até")) return "errors.planPropertyLimit";
    return SERVER_MESSAGES[message] ?? BY_STATUS[error.status] ?? "errors.generic";
};

export const isEmailNotVerified = (error: unknown) => error instanceof ApiError && error.status === 403;
