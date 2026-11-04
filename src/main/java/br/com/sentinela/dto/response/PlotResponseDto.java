package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PlotResponseDto(UUID plotId, String name, String cropName, LocalDate plantingDate,
                              String cropStage, Double areaHectares) {}
