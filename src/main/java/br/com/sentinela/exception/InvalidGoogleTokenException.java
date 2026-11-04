package br.com.sentinela.exception;

public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException() {
        super("Token do Google inválido ou expirado");
    }

    public InvalidGoogleTokenException(String message) {
        super(message);
    }
}
