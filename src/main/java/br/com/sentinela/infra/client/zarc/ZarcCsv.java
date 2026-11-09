package br.com.sentinela.infra.client.zarc;

import br.com.sentinela.util.Util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Leitura da Tábua de risco do ZARC: CSV com ";", UTF-8 com BOM e uma coluna por decêndio (dec1 a dec36). */
public final class ZarcCsv {
    public static final int DECENDIOS = 36;
    private static final List<String> REQUIRED = List.of("Nome_cultura", "Cod_Cultura", "Cod_Ciclo", "Cod_Solo", "geocodigo", "UF",
            "municipio", "Cod_Outros_Manejos", "Nome_Outros_Manejos", "dec1", "dec36");

    private final Map<String, Integer> columns = new HashMap<>();

    public record Row(String cropCode, String cropName, int cycleCode, int soilCode, int ibgeCode, String uf, String municipality,
                      String municipalityKey, String climateName, int managementCode, String managementName, String productivity,
                      Integer managementLevel, String ordinance, String risks) {}

    public ZarcCsv(String headerLine) {
        String[] names = stripBom(headerLine).split(";", -1);
        for (int index = 0; index < names.length; index++) columns.put(names[index].trim(), index);
        for (String name : REQUIRED) {
            if (!columns.containsKey(name)) throw new IllegalArgumentException("Coluna ausente na Tábua de risco: " + name);
        }
    }

    /** Linha fora do formato devolve null: o arquivo tem 1 milhão de linhas e uma ruim não pode parar a importação. */
    public Row parse(String line) {
        if (Util.isNullOrBlank(line)) return null;
        String[] values = line.split(";", -1);
        if (values.length < columns.size()) return null;
        try {
            StringBuilder risks = new StringBuilder(DECENDIOS);
            for (int decendio = 1; decendio <= DECENDIOS; decendio++) risks.append(riskDigit(value(values, "dec" + decendio)));
            String municipality = value(values, "municipio");
            return new Row(value(values, "Cod_Cultura"), value(values, "Nome_cultura"), Integer.parseInt(value(values, "Cod_Ciclo")),
                    Integer.parseInt(value(values, "Cod_Solo")), Integer.parseInt(value(values, "geocodigo")),
                    value(values, "UF").toUpperCase(Locale.ROOT), municipality, normalize(municipality),
                    Util.trimToNull(value(values, "Nome_Clima")), Integer.parseInt(value(values, "Cod_Outros_Manejos")),
                    value(values, "Nome_Outros_Manejos"), Util.trimToNull(value(values, "Produtividade")),
                    integerOrNull(value(values, "Cod_NM")), Util.trimToNull(value(values, "Portaria")), risks.toString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** O arquivo traz o risco em % (20, 30, 40); guardamos só o dígito das dezenas. */
    static char riskDigit(String value) {
        if (Util.isNullOrBlank(value)) return '0';
        return switch (value.trim()) {
            case "0" -> '0';
            case "20" -> '2';
            case "30" -> '3';
            case "40" -> '4';
            default -> throw new IllegalArgumentException("Risco inesperado: " + value);
        };
    }

    /** Sem acento, minúsculo e sem pontuação: "São João d'Aliança" e "SAO JOAO D ALIANCA" viram a mesma chave. */
    public static String normalize(String text) {
        if (Util.isNull(text)) return "";
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String value(String[] values, String column) {
        Integer index = columns.get(column);
        return Util.isNull(index) || index >= values.length ? "" : values[index].trim();
    }

    private static Integer integerOrNull(String value) {
        return Util.isNullOrBlank(value) ? null : Integer.valueOf(value.trim());
    }

    private static String stripBom(String line) {
        return !Util.isNullOrEmpty(line) && line.charAt(0) == '﻿' ? line.substring(1) : line;
    }
}
