package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PlotSeasonResponseDto(UUID seasonId, UUID plotId, String cropName, String variety,
                                    LocalDate plantingDate, LocalDate harvestDate, String status) {}
