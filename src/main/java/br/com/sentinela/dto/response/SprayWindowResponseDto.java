package br.com.sentinela.dto.response;

import java.util.List;

public record SprayWindowResponseDto(List<Hour> hours, List<Window> windows, String source, String disclaimer) {

    public record Hour(String time, boolean suitable, double temperatureC, int humidity, double windKph,
                       int rainChance, double precipitationMm, List<String> reasons) {}

    /** Horas seguidas favoráveis; {@code to} é o início da última hora favorável. */
    public record Window(String from, String to, int hours) {}
}
