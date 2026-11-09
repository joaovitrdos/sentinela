package br.com.sentinela.exception;

public class ClimateHistoryUnavailableException extends RuntimeException {

    public ClimateHistoryUnavailableException() {
        super("Histórico de clima indisponível. Tente novamente em instantes");
    }
}
