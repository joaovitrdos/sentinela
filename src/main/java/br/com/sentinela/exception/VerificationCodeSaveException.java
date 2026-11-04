package br.com.sentinela.exception;

public class VerificationCodeSaveException extends RuntimeException {

    public VerificationCodeSaveException() {
        super("Não foi possível gerar o código de verificação. Tente novamente");
    }

    public VerificationCodeSaveException(String message) {
        super(message);
    }

    public VerificationCodeSaveException(Throwable cause) {
        super("Não foi possível gerar o código de verificação. Tente novamente", cause);
    }
}
