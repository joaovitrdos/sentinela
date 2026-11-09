package br.com.sentinela.service;

import br.com.sentinela.dto.response.PlantingWindowResponseDto.Window;
import br.com.sentinela.infra.client.zarc.ZarcCsv;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlantingWindowServiceTest {

    /** 36 decêndios a partir de um mapa simples: posição (1..36) -> dígito. */
    private static String risks(int from, int to, char digit) {
        StringBuilder text = new StringBuilder("0".repeat(ZarcCsv.DECENDIOS));
        for (int decendio = from; decendio <= to; decendio++) text.setCharAt(decendio - 1, digit);
        return text.toString();
    }

    @Test
    void decendiosSplitEachMonthInThree() {
        assertThat(PlantingWindowService.decendio(LocalDate.of(2026, 1, 1))).isEqualTo(1);
        assertThat(PlantingWindowService.decendio(LocalDate.of(2026, 1, 11))).isEqualTo(2);
        assertThat(PlantingWindowService.decendio(LocalDate.of(2026, 1, 31))).isEqualTo(3);
        assertThat(PlantingWindowService.decendio(LocalDate.of(2026, 11, 9))).isEqualTo(31);
        assertThat(PlantingWindowService.decendio(LocalDate.of(2026, 12, 31))).isEqualTo(36);
        assertThat(PlantingWindowService.decendioEnd(6, 2028)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void openWindowRunsFromTodayToItsLastDecendio() {
        // Soja típica: 21/set (dec 27) a 31/dez (dec 36), com risco 20% e um trecho final de 30%.
        String soy = risks(27, 33, '2').substring(0, 33) + "333";
        Window window = PlantingWindowService.nextWindow(soy, LocalDate.of(2026, 11, 9));

        assertThat(window.fromDecendio()).isEqualTo(31);
        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(window.toDecendio()).isEqualTo(36);
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(window.bestRiskPercent()).isEqualTo(20);
    }

    @Test
    void closedWindowPointsToTheNextOneEvenAcrossTheYear() {
        // Milho 2ª safra: 1/jan (dec 1) a 20/fev (dec 5).
        Window window = PlantingWindowService.nextWindow(risks(1, 5, '3'), LocalDate.of(2026, 11, 9));

        assertThat(window.fromDecendio()).isEqualTo(1);
        assertThat(window.from()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(window.to()).isEqualTo(LocalDate.of(2027, 2, 20));
        assertThat(window.bestRiskPercent()).isEqualTo(30);
    }

    @Test
    void windowThatCrossesNewYearKeepsGoingIntoJanuary() {
        String risks = risks(1, 2, '4').substring(0, 33) + "222";
        Window window = PlantingWindowService.nextWindow(risks, LocalDate.of(2026, 12, 5));

        assertThat(window.fromDecendio()).isEqualTo(34);
        assertThat(window.toDecendio()).isEqualTo(2);
        assertThat(window.to()).isEqualTo(LocalDate.of(2027, 1, 20));
        assertThat(window.bestRiskPercent()).isEqualTo(20);
    }

    @Test
    void noWindowAtAllReturnsNull() {
        assertThat(PlantingWindowService.nextWindow("0".repeat(36), LocalDate.of(2026, 11, 9))).isNull();
    }

    @Test
    void cropChoiceFollowsRequestThenPropertyThenSoy() {
        List<String> crops = List.of("Feijão", "Milho 1ª Safra", "Milho 2ª Safra", "Soja");
        assertThat(PlantingWindowService.chooseCrop(crops, "milho 2ª safra", "Soja")).isEqualTo("Milho 2ª Safra");
        assertThat(PlantingWindowService.chooseCrop(crops, null, "feijao")).isEqualTo("Feijão");
        assertThat(PlantingWindowService.chooseCrop(crops, null, "Milho")).isEqualTo("Milho 1ª Safra");
        assertThat(PlantingWindowService.chooseCrop(crops, null, "Café")).isEqualTo("Soja");
        assertThat(PlantingWindowService.chooseCrop(List.of("Arroz"), null, null)).isEqualTo("Arroz");
    }

    @Test
    void stateAcceptsAbbreviationOrFullName() {
        assertThat(PlantingWindowService.uf("mt")).isEqualTo("MT");
        assertThat(PlantingWindowService.uf("Mato Grosso do Sul")).isEqualTo("MS");
        assertThat(PlantingWindowService.uf("São Paulo")).isEqualTo("SP");
    }
}
