package br.com.sentinela.exception;

public class GoogleAccountWithoutPasswordException extends RuntimeException {

    public GoogleAccountWithoutPasswordException() {
        super("Contas do Google não possuem senha para alterar");
    }

    public GoogleAccountWithoutPasswordException(String message) {
        super(message);
    }
}
