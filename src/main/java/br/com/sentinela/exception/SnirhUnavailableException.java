package br.com.sentinela.exception;

/** O SNIRH nao respondeu (rede, timeout, 5xx) mesmo depois das novas tentativas. */
public class SnirhUnavailableException extends RuntimeException {

    public SnirhUnavailableException(String message) {
        super(message);
    }

    public SnirhUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
