package br.com.sentinela.exception;

public class WeatherApiUnavailableException extends RuntimeException {

    public WeatherApiUnavailableException() {
        super("Serviço de clima indisponível. Tente novamente em instantes");
    }
}
