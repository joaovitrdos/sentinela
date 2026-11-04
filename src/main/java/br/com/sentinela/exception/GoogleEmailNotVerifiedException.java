package br.com.sentinela.exception;

public class GoogleEmailNotVerifiedException extends RuntimeException {

    public GoogleEmailNotVerifiedException() {
        super("O e-mail da conta Google não foi verificado pelo Google");
    }

    public GoogleEmailNotVerifiedException(String message) {
        super(message);
    }
}
