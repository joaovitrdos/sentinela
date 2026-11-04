package br.com.sentinela.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PlotRequestDto(@NotBlank @Size(max = 120) String name,
                             @Size(max = 120) String cropName,
                             @PastOrPresent LocalDate plantingDate,
                             @Size(max = 80) String cropStage,
                             @Positive Double areaHectares) {}
