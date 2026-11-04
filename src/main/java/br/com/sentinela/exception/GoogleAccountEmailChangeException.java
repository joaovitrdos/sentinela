package br.com.sentinela.exception;

public class GoogleAccountEmailChangeException extends RuntimeException {

    public GoogleAccountEmailChangeException() {
        super("Contas do Google usam o e-mail da conta Google e não podem trocá-lo aqui");
    }

    public GoogleAccountEmailChangeException(String message) {
        super(message);
    }
}
