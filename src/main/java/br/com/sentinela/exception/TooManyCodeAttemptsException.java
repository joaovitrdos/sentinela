package br.com.sentinela.exception;

public class TooManyCodeAttemptsException extends RuntimeException {

    public TooManyCodeAttemptsException() {
        super("Muitas tentativas com o código. Solicite um novo código mais tarde");
    }

    public TooManyCodeAttemptsException(String message) {
        super(message);
    }
}
