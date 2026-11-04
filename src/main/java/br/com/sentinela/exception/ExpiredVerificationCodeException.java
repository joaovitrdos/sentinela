package br.com.sentinela.exception;

public class ExpiredVerificationCodeException extends RuntimeException {

    public ExpiredVerificationCodeException() {
        super("Código de verificação expirado. Solicite um novo código");
    }

    public ExpiredVerificationCodeException(String message) {
        super(message);
    }
}
