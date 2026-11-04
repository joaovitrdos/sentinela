package br.com.sentinela.exception;

public class IncorrectPasswordException extends RuntimeException {

    public IncorrectPasswordException() {
        super("A senha informada está incorreta");
    }

    public IncorrectPasswordException(String message) {
        super(message);
    }
}
