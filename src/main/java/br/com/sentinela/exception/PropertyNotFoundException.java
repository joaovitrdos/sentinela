package br.com.sentinela.exception;

public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException() {
        super("Propriedade não encontrada");
    }
}
