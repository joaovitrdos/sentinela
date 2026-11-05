package br.com.sentinela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import br.com.sentinela.dto.response.IrrigationRecommendationResponseDto.Day;
import br.com.sentinela.dto.response.PropertyMonitoringDto;
import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.dto.response.SprayWindowResponseDto.Hour;
import br.com.sentinela.entity.Property;
import br.com.sentinela.service.InmetService.InmetWarning;
import br.com.sentinela.service.IrrigationService.ClimateDay;
import br.com.sentinela.service.IrrigationService.Crop;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FieldAdvisoryTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Property property = Property.builder().name("Fazenda").latitude(-16.68).longitude(-49.25).build();

    @Test
    void radiationMatchesFaoWorkedExample() {
        assertThat(IrrigationService.extraterrestrialRadiation(-20, LocalDate.of(2026, 9, 3))).isCloseTo(32.2, within(0.1));
    }

    @Test
    void hargreavesGivesPlausibleSpringDemandInGoias() {
        assertThat(IrrigationService.hargreaves(-16.68, LocalDate.of(2026, 11, 1), 35, 22)).isBetween(5.0, 7.5);
    }

    @Test
    void cropsAreMatchedIgnoringAccentsAndCase() {
        assertThat(IrrigationService.crop("Feijão carioca").key()).isEqualTo("feijao");
        assertThat(IrrigationService.crop("Milho safrinha").key()).isEqualTo("milho");
        assertThat(IrrigationService.crop("Cana-de-açúcar").key()).isEqualTo("cana");
        assertThat(IrrigationService.crop("Pitaya")).isNull();
        assertThat(IrrigationService.crop(null)).isNull();
    }

    @Test
    void cropCoefficientFollowsTheCycle() {
        Crop soy = IrrigationService.crop("soja");
        LocalDate planting = LocalDate.of(2026, 10, 1);
        assertThat(IrrigationService.coefficient(soy, planting, planting.minusDays(1))).isZero();
        assertThat(IrrigationService.coefficient(soy, planting, planting.plusDays(5))).isEqualTo(0.40);
        assertThat(IrrigationService.coefficient(soy, planting, planting.plusDays(70))).isEqualTo(1.15);
        assertThat(IrrigationService.coefficient(soy, planting, planting.plusDays(200))).isEqualTo(0.50);
    }

    @Test
    void balanceAccumulatesDemandAndHeavyRainClearsDeficit() {
        Crop reference = new Crop("ref", 1, 1, 1, 1);
        LocalDate date = LocalDate.of(2026, 11, 1);
        List<Day> days = IrrigationService.balance(-16.68, List.of(
                new ClimateDay(date, 35, 22, 0, false),
                new ClimateDay(date.plusDays(1), 35, 22, 0, false),
                new ClimateDay(date.plusDays(2), 30, 22, 80, true)), Map.of(), Map.of(), reference, null);

        assertThat(days.get(1).deficitMm()).isGreaterThan(days.get(0).deficitMm());
        assertThat(days.get(2).deficitMm()).isZero();
    }

    @Test
    void recordedIrrigationAndRainOverrideTheBalance() {
        Crop reference = new Crop("ref", 1, 1, 1, 1);
        LocalDate date = LocalDate.of(2026, 11, 1);
        List<ClimateDay> climate = List.of(new ClimateDay(date, 35, 22, 50, false));

        assertThat(IrrigationService.balance(-16.68, climate, Map.of(), Map.of(date, 0.0), reference, null).getFirst().rainMm()).isZero();
        assertThat(IrrigationService.balance(-16.68, climate, Map.of(date, 100.0), Map.of(date, 0.0), reference, null).getFirst().deficitMm()).isZero();
    }

    @Test
    void nasaMissingValuesAreDiscarded() {
        JsonNode response = mapper.readTree("""
                {"properties":{"parameter":{
                  "T2M_MAX":{"20261101":34.98,"20261102":-999},
                  "T2M_MIN":{"20261101":22.06,"20261102":-999},
                  "PRECTOTCORR":{"20261101":5.97,"20261102":-999}}}}""");

        assertThat(NasaPowerService.parse(response)).containsExactly(
                new NasaPowerService.DailyClimate(LocalDate.of(2026, 11, 1), 34.98, 22.06, 5.97));
    }

    @Test
    void sprayHourExplainsEachFailedCondition() {
        List<Hour> hours = SprayWindowService.hours(forecast("""
                {"time_epoch":1000,"time":"a","temp_c":25,"humidity":70,"wind_kph":6,"chance_of_rain":0,"precip_mm":0},
                {"time_epoch":10000,"time":"b","temp_c":25,"humidity":70,"wind_kph":6,"chance_of_rain":0,"precip_mm":0},
                {"time_epoch":13600,"time":"c","temp_c":33,"humidity":40,"wind_kph":15,"chance_of_rain":80,"precip_mm":1}"""), 9_000);

        assertThat(hours).extracting(Hour::time).containsExactly("b", "c");
        assertThat(hours.getFirst().suitable()).isTrue();
        assertThat(hours.getLast().reasons()).hasSize(4);
    }

    @Test
    void sprayWindowsGroupConsecutiveSuitableHours() {
        List<Hour> hours = new ArrayList<>();
        for (String[] item : new String[][]{{"a", "s"}, {"b", "s"}, {"c", "n"}, {"d", "s"}}) {
            hours.add(new Hour(item[0], "s".equals(item[1]), 0, 0, 0, 0, 0, List.of()));
        }

        assertThat(SprayWindowService.windows(hours)).extracting("from", "to", "hours")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("a", "b", 2), org.assertj.core.groups.Tuple.tuple("d", "d", 1));
    }

    @Test
    void forecastCreatesFrostAndRainAlertsFromConfiguredLimits() {
        property.setAlertFrostTemperatureC(2.0);
        property.setAlertHeavyRainMm(50.0);
        JsonNode forecast = mapper.readTree("""
                {"forecastday":[{"date":"2026-11-05","day":{"mintemp_c":1.5,"totalprecip_mm":0}},
                                {"date":"2026-11-06","day":{"mintemp_c":15,"totalprecip_mm":60}},
                                {"date":"2026-11-07","day":{"mintemp_c":15,"totalprecip_mm":10}}]}""");

        assertThat(PropertyAlertService.forecastAlerts(property, forecast)).extracting(PropertyAlertService.Candidate::key)
                .containsExactly("FROST:2026-11-05", "HEAVY_RAIN:2026-11-06");
    }

    @Test
    void inmetKeepsOnlyDangerousWarningsAndChecksThePolygon() {
        JsonNode response = mapper.readTree("""
                {"hoje":[
                  {"id_aviso":1,"descricao":"Geada","severidade":"Perigo","inicio":"i","fim":"f","riscos":["r"],
                   "poligono":"{\\"type\\":\\"Polygon\\",\\"coordinates\\":[[[-50,-17],[-49,-17],[-49,-16],[-50,-16],[-50,-17]]]}"},
                  {"id_aviso":2,"descricao":"Chuva","severidade":"Perigo Potencial","poligono":"{}"}],
                 "futuro":[
                  {"id_aviso":3,"descricao":"Vento","severidade":"Grande Perigo","inicio":"i","fim":"f","riscos":[],
                   "poligono":"{\\"type\\":\\"MultiPolygon\\",\\"coordinates\\":[[[[10,10],[11,10],[11,11],[10,10]]]]}"},
                  {"id_aviso":4,"descricao":"Fim","severidade":"Perigo","encerrado":true,"poligono":"{}"}]}""");

        List<InmetWarning> warnings = InmetService.parse(response, mapper);

        assertThat(warnings).extracting(InmetWarning::id).containsExactly(1L, 3L);
        assertThat(PropertyAlertService.inmetAlerts(property, warnings)).extracting(PropertyAlertService.Candidate::key)
                .containsExactly("INMET:1");
    }

    @Test
    void riverAlertUsesOnlyNearbyRecentStationsInAttention() {
        property.setAlertRiverRadiusKm(30.0);
        List<RiverLevelResponseDto> levels = List.of(
                river(1, "INUNDACAO", true, -16.70, -49.26, 2.0),
                river(2, "NORMAL", false, -16.70, -49.26, 2.0),
                river(3, "ALERTA", true, -10.00, -49.26, 2.0),
                river(4, "ALERTA", true, -16.70, -49.26, 72.0));

        var alerts = PropertyAlertService.riverAlerts(property, levels);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().severity()).isEqualTo("PERIGO");
        assertThat(alerts.getFirst().key()).startsWith("RIVER:1:INUNDACAO:");
    }

    @Test
    void ndviAlertRespectsConfiguredDrop() {
        property.setAlertNdviDrop(0.15);

        assertThat(PropertyAlertService.ndviAlerts(property, monitoring(-0.20))).hasSize(1);
        assertThat(PropertyAlertService.ndviAlerts(property, monitoring(-0.05))).isEmpty();
        assertThat(PropertyAlertService.ndviAlerts(property, monitoring(null))).isEmpty();
    }

    @Test
    void carCodeAcceptsDotsAndLowerCaseButRejectsInjection() {
        assertThat(CarService.normalize(" go-5207808-0530.a0b5.7a18.4968.987f.8fe2.dba3.13ed "))
                .isEqualTo("GO-5207808-0530A0B57A184968987F8FE2DBA313ED");
        for (String invalid : List.of("", "XX-5207808-0530A0B57A184968987F8FE2DBA313ED",
                "GO-5207808-0530A0B57A184968987F8FE2DBA313ED' OR '1'='1")) {
            assertThatThrownBy(() -> CarService.normalize(invalid)).isInstanceOfSatisfying(ResponseStatusException.class,
                    exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void carKeepsLargestPartAndFitsThePointLimit() {
        StringBuilder circle = new StringBuilder();
        for (int index = 0; index <= 600; index++) {
            double angle = 2 * Math.PI * (index % 600) / 600;
            circle.append(index == 0 ? "" : ",").append('[').append(-50 + 0.02 * Math.cos(angle)).append(',')
                    .append(-16 + 0.02 * Math.sin(angle)).append(']');
        }
        JsonNode feature = mapper.readTree("""
                {"geometry":{"type":"MultiPolygon","coordinates":[[[[-49,-15],[-48.999,-15],[-48.999,-14.999],[-49,-15]]],[[%s]]]},
                 "properties":{"cod_imovel":"X","area":120.5,"municipio":"Firminópolis","uf":"GO"}}""".formatted(circle));

        var response = CarService.response(feature);

        assertThat(response.simplified()).isTrue();
        assertThat(response.ignoredParts()).isEqualTo(1);
        assertThat(response.geometry().coordinates().getFirst().size()).isLessThanOrEqualTo(201);
        assertThat(response.geometry().coordinates().getFirst().getFirst().getFirst()).isBetween(-50.03, -49.97);
    }

    private JsonNode forecast(String hours) {
        return mapper.readTree("{\"forecastday\":[{\"hour\":[" + hours + "]}]}");
    }

    private RiverLevelResponseDto river(long code, String status, boolean attention, double latitude, double longitude, double age) {
        return new RiverLevelResponseDto(code, "Estação " + code, "Cidade", "GO", null, null, null, null, null, null, 100.0,
                status, "Situação " + status, OffsetDateTime.parse("2026-11-05T10:00:00-03:00"), age, latitude, longitude, attention);
    }

    private PropertyMonitoringDto monitoring(Double change) {
        return new PropertyMonitoringDto(true, "OK", null, null, null, null, null, List.of(),
                LocalDate.of(2026, 11, 1), null, null, change, null);
    }
}
