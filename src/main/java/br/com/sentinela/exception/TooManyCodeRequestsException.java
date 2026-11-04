package br.com.sentinela.exception;

public class TooManyCodeRequestsException extends RuntimeException {

    public TooManyCodeRequestsException() {
        super("Muitos códigos solicitados. Tente novamente mais tarde");
    }

    public TooManyCodeRequestsException(String message) {
        super(message);
    }
}
