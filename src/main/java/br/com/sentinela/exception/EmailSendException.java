package br.com.sentinela.exception;

public class EmailSendException extends RuntimeException {

    public EmailSendException() {
        super("Falha ao enviar o e-mail");
    }

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(Throwable cause) {
        super("Falha ao enviar o e-mail", cause);
    }
}
