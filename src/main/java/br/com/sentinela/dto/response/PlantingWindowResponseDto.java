package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Janelas de plantio do ZARC para o município da propriedade. "status" diz por que pode não haver dado:
 * OK, MUNICIPALITY_NOT_FOUND (cidade/UF não bate com o ZARC) ou NOT_IMPORTED (a tábua ainda não foi importada).
 */
public record PlantingWindowResponseDto(
        String status,
        String municipality,
        String uf,
        Integer ibgeCode,
        List<String> crops,
        String crop,
        int currentDecendio,
        List<Option> options,
        List<DataSourceDto> sources
) {
    /** Uma combinação de ciclo, solo e manejo. "risks" tem os 36 decêndios do ano: 0 fora da janela, ou 20/30/40 (%). */
    public record Option(String harvest, int cycleCode, int soilCode, String climateName, int managementCode, String managementName,
                         String productivity, Integer managementLevel, String ordinance, List<Integer> risks, boolean openNow,
                         Window window) {}

    /** Janela atual (se aberta) ou a próxima, com o menor risco dentro dela. */
    public record Window(int fromDecendio, int toDecendio, LocalDate from, LocalDate to, int bestRiskPercent) {}
}
