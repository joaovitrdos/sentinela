package br.com.sentinela.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record PropertyAlertSettingsDto(
        boolean enabled,
        @DecimalMin(value = "-5", message = "A temperatura de geada deve ser de pelo menos -5 °C")
        @DecimalMax(value = "10", message = "A temperatura de geada deve ser de até 10 °C")
        double frostTemperatureC,
        @DecimalMin(value = "10", message = "A chuva forte deve ser de pelo menos 10 mm")
        @DecimalMax(value = "300", message = "A chuva forte deve ser de até 300 mm")
        double heavyRainMm,
        @DecimalMin(value = "0.05", message = "A queda de NDVI deve ser de pelo menos 0,05")
        @DecimalMax(value = "0.8", message = "A queda de NDVI deve ser de até 0,8")
        double ndviDrop,
        @DecimalMin(value = "1", message = "O raio dos rios deve ser de pelo menos 1 km")
        @DecimalMax(value = "200", message = "O raio dos rios deve ser de até 200 km")
        double riverRadiusKm) {}
