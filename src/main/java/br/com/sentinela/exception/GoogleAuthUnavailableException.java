package br.com.sentinela.exception;

public class GoogleAuthUnavailableException extends RuntimeException {

    public GoogleAuthUnavailableException() {
        super("Não foi possível validar o token do Google. Tente novamente em instantes");
    }

    public GoogleAuthUnavailableException(String message) {
        super(message);
    }

    public GoogleAuthUnavailableException(Throwable cause) {
        super("Não foi possível validar o token do Google. Tente novamente em instantes", cause);
    }
}
