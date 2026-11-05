package br.com.sentinela.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PlotSeasonRequestDto(@NotBlank @Size(max = 120) String cropName,
                                   @Size(max = 120) String variety,
                                   @NotNull @PastOrPresent LocalDate plantingDate) {}
