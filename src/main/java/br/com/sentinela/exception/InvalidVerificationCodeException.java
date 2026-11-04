package br.com.sentinela.exception;

public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException() {
        super("Código de verificação inválido");
    }

    public InvalidVerificationCodeException(String message) {
        super(message);
    }
}
