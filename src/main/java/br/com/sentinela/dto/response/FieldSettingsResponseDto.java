package br.com.sentinela.dto.response;

public record FieldSettingsResponseDto(boolean actionReminderEnabled, int actionReminderDays,
                                       boolean requireSeasonForFieldRecords, String defaultHarvestUnit) {}
