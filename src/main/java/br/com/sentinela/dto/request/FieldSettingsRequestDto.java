package br.com.sentinela.dto.request;

import jakarta.validation.constraints.*;

public record FieldSettingsRequestDto(@NotNull Boolean actionReminderEnabled,
                                      @NotNull @Min(0) @Max(7) Integer actionReminderDays,
                                      @NotNull Boolean requireSeasonForFieldRecords,
                                      @NotNull @Pattern(regexp = "kg|sacas|toneladas") String defaultHarvestUnit) {}
