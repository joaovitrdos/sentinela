package br.com.sentinela.exception;

public class InvalidRegistrationDataException extends RuntimeException {

    public InvalidRegistrationDataException() {
        super("Nome, e-mail e senha são obrigatórios");
    }

    public InvalidRegistrationDataException(String message) {
        super(message);
    }
}
