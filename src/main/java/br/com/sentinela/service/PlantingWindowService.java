package br.com.sentinela.service;

import br.com.sentinela.dto.response.DataSourceDto;
import br.com.sentinela.dto.response.PlantingWindowResponseDto;
import br.com.sentinela.dto.response.PlantingWindowResponseDto.Option;
import br.com.sentinela.dto.response.PlantingWindowResponseDto.Window;
import br.com.sentinela.entity.Property;
import br.com.sentinela.infra.client.zarc.ZarcCsv;
import br.com.sentinela.util.Util;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Janela de plantio do ZARC no município da propriedade: o que está aberto agora e quando abre a próxima. */
@Service
public class PlantingWindowService {
    private static final String DEFAULT_CROP = "Soja";
    private static final Map<String, String> STATES = Map.ofEntries(
            Map.entry("acre", "AC"), Map.entry("alagoas", "AL"), Map.entry("amapa", "AP"), Map.entry("amazonas", "AM"),
            Map.entry("bahia", "BA"), Map.entry("ceara", "CE"), Map.entry("distrito federal", "DF"), Map.entry("espirito santo", "ES"),
            Map.entry("goias", "GO"), Map.entry("maranhao", "MA"), Map.entry("mato grosso", "MT"), Map.entry("mato grosso do sul", "MS"),
            Map.entry("minas gerais", "MG"), Map.entry("para", "PA"), Map.entry("paraiba", "PB"), Map.entry("parana", "PR"),
            Map.entry("pernambuco", "PE"), Map.entry("piaui", "PI"), Map.entry("rio de janeiro", "RJ"),
            Map.entry("rio grande do norte", "RN"), Map.entry("rio grande do sul", "RS"), Map.entry("rondonia", "RO"),
            Map.entry("roraima", "RR"), Map.entry("santa catarina", "SC"), Map.entry("sao paulo", "SP"), Map.entry("sergipe", "SE"),
            Map.entry("tocantins", "TO"));

    private final PropertyService propertyService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PlantingWindowService(PropertyService propertyService, JdbcTemplate jdbc, @Qualifier("sentinelaClock") Clock clock) {
        this.propertyService = propertyService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlantingWindowResponseDto window(UUID propertyId, UUID userId, String requestedCrop) {
        Property property = propertyService.requireView(propertyId, userId);
        LocalDate today = LocalDate.now(clock);
        int current = decendio(today);
        List<DataSourceDto> sources = List.of(DataSourceDto.ZARC);

        Boolean imported = jdbc.query("SELECT 1 FROM tb_zarc_risk LIMIT 1", (ResultSetExtractor<Boolean>) ResultSet::next);
        if (!Boolean.TRUE.equals(imported)) {
            return new PlantingWindowResponseDto("NOT_IMPORTED", null, null, null, List.of(), null, current, List.of(), sources);
        }

        String uf = uf(property.getState());
        String key = ZarcCsv.normalize(property.getCity());
        List<String> crops = jdbc.queryForList(
                "SELECT DISTINCT crop_name FROM tb_zarc_risk WHERE uf = ? AND municipality_key = ? ORDER BY crop_name", String.class, uf, key);
        if (Util.isNullOrEmpty(crops)) {
            return new PlantingWindowResponseDto("MUNICIPALITY_NOT_FOUND", property.getCity(), uf, null, List.of(), null, current, List.of(),
                    sources);
        }

        String crop = chooseCrop(crops, requestedCrop, property.getCropName());
        List<Row> rows = jdbc.query("""
                SELECT harvest, cycle_code, soil_code, climate_name, management_code, management_name, productivity, management_level,
                       ordinance, risks, ibge_code, municipality
                  FROM tb_zarc_risk
                 WHERE uf = ? AND municipality_key = ? AND crop_name = ?
                 ORDER BY management_code, cycle_code, soil_code
                """, (rs, index) -> new Row(rs.getString("harvest"), rs.getInt("cycle_code"), rs.getInt("soil_code"),
                rs.getString("climate_name"), rs.getInt("management_code"), rs.getString("management_name"), rs.getString("productivity"),
                rs.getObject("management_level", Integer.class), rs.getString("ordinance"), rs.getString("risks"),
                rs.getInt("ibge_code"), rs.getString("municipality")), uf, key, crop);

        List<Option> options = rows.stream().map(row -> option(row, today)).toList();
        Row first = rows.getFirst();
        return new PlantingWindowResponseDto("OK", first.municipality(), uf, first.ibgeCode(), crops, crop, current, options, sources);
    }

    private record Row(String harvest, int cycleCode, int soilCode, String climateName, int managementCode, String managementName,
                       String productivity, Integer managementLevel, String ordinance, String risks, int ibgeCode, String municipality) {}

    private static Option option(Row row, LocalDate today) {
        List<Integer> risks = row.risks().chars().mapToObj(digit -> (digit - '0') * 10).toList();
        Window window = nextWindow(row.risks(), today);
        boolean openNow = risks.get(decendio(today) - 1) > 0;
        return new Option(row.harvest(), row.cycleCode(), row.soilCode(), row.climateName(), row.managementCode(), row.managementName(),
                row.productivity(), row.managementLevel(), row.ordinance(), risks, openNow, window);
    }

    /** A cultura pedida, senão a cadastrada na propriedade, senão soja, senão a primeira da lista. */
    static String chooseCrop(List<String> crops, String requested, String propertyCrop) {
        for (String candidate : new String[] {requested, propertyCrop, DEFAULT_CROP}) {
            if (Util.isNullOrBlank(candidate)) continue;
            String wanted = ZarcCsv.normalize(candidate);
            for (String crop : crops) if (ZarcCsv.normalize(crop).equals(wanted)) return crop;
            for (String crop : crops) if (ZarcCsv.normalize(crop).startsWith(wanted)) return crop;
        }
        return crops.getFirst();
    }

    static String uf(String state) {
        if (Util.isNullOrBlank(state)) return "";
        String trimmed = state.trim();
        if (trimmed.length() == 2) return trimmed.toUpperCase(Locale.ROOT);
        return STATES.getOrDefault(ZarcCsv.normalize(trimmed), trimmed.toUpperCase(Locale.ROOT));
    }

    /** Decêndio do ano de 1 a 36: dias 1-10, 11-20 e 21 até o fim de cada mês. */
    static int decendio(LocalDate date) {
        return (date.getMonthValue() - 1) * 3 + Math.min(3, (date.getDayOfMonth() - 1) / 10 + 1);
    }

    static LocalDate decendioStart(int decendio, int year) {
        int month = (decendio - 1) / 3 + 1;
        return LocalDate.of(year, month, ((decendio - 1) % 3) * 10 + 1);
    }

    static LocalDate decendioEnd(int decendio, int year) {
        LocalDate start = decendioStart(decendio, year);
        return (decendio - 1) % 3 == 2 ? YearMonth.from(start).atEndOfMonth() : start.plusDays(9);
    }

    /**
     * Janela aberta hoje (até onde ela vai) ou a próxima a abrir, olhando no máximo um ano à frente. A janela pode
     * virar o ano (dezembro a janeiro): por isso a busca anda em volta dos 36 decêndios.
     */
    static Window nextWindow(String risks, LocalDate today) {
        int current = decendio(today);
        int startOffset = -1;
        for (int offset = 0; offset < ZarcCsv.DECENDIOS; offset++) {
            if (riskAt(risks, current + offset) > 0) {
                startOffset = offset;
                break;
            }
        }
        if (startOffset < 0) return null;

        int endOffset = startOffset;
        int best = riskAt(risks, current + startOffset);
        while (endOffset + 1 < ZarcCsv.DECENDIOS && riskAt(risks, current + endOffset + 1) > 0) {
            endOffset++;
            best = Math.min(best, riskAt(risks, current + endOffset));
        }

        int from = wrap(current + startOffset);
        int to = wrap(current + endOffset);
        int fromYear = today.getYear() + (current + startOffset > ZarcCsv.DECENDIOS ? 1 : 0);
        int toYear = today.getYear() + (current + endOffset > ZarcCsv.DECENDIOS ? 1 : 0);
        LocalDate start = startOffset == 0 ? today : decendioStart(from, fromYear);
        return new Window(from, to, start, decendioEnd(to, toYear), best);
    }

    private static int riskAt(String risks, int decendio) {
        return (risks.charAt(wrap(decendio) - 1) - '0') * 10;
    }

    private static int wrap(int decendio) {
        return (decendio - 1) % ZarcCsv.DECENDIOS + 1;
    }

}
