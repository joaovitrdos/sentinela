package br.com.sentinela.exception;

/** O SNIRH respondeu, mas com erro do ArcGIS ou corpo vazio: repetir a chamada nao resolve. */
public class SnirhApiException extends RuntimeException {

    public SnirhApiException(String message) {
        super(message);
    }
}
