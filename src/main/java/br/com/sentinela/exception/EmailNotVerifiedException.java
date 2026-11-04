package br.com.sentinela.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Confirme seu e-mail antes de entrar. Verifique sua caixa de entrada");
    }

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
