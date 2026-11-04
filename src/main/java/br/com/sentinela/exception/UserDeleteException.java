package br.com.sentinela.exception;

public class UserDeleteException extends RuntimeException {

    public UserDeleteException() {
        super("Não foi possível excluir a conta. Tente novamente");
    }

    public UserDeleteException(String message) {
        super(message);
    }

    public UserDeleteException(Throwable cause) {
        super("Não foi possível excluir a conta. Tente novamente", cause);
    }
}
