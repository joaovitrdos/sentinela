package br.com.sentinela.exception;

public class UserSaveException extends RuntimeException {

    public UserSaveException() {
        super("Não foi possível salvar os dados do usuário. Tente novamente");
    }

    public UserSaveException(String message) {
        super(message);
    }

    public UserSaveException(Throwable cause) {
        super("Não foi possível salvar os dados do usuário. Tente novamente", cause);
    }
}
