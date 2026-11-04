package br.com.sentinela.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Este e-mail já está em uso. Tente outro e-mail");
    }

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
