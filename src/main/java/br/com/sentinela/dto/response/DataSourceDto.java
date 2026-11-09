package br.com.sentinela.dto.response;

/** Fonte de um dado exibido: a licença das fontes públicas exige citar nome e origem junto do resultado. */
public record DataSourceDto(String name, String url, String license) {
    public static final DataSourceDto NASA_POWER = new DataSourceDto(
            "NASA POWER (Langley Research Center)", "https://power.larc.nasa.gov", "Uso livre com citação");
    public static final DataSourceDto ZARC = new DataSourceDto(
            "ZARC – Tábua de risco (MAPA)",
            "https://dados.agricultura.gov.br/dataset/tabua-de-risco-zoneamento-agricola-de-risco-climatico", "CC-BY");
}
