package br.com.sentinela.exception;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Muitas tentativas de login. Tente novamente mais tarde");
    }

    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
