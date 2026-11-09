package br.com.sentinela.service;

import br.com.sentinela.infra.client.zarc.ZarcCsv;
import br.com.sentinela.infra.config.ZarcProperties;
import br.com.sentinela.util.LogUtil;
import br.com.sentinela.util.Util;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Importa a Tábua de risco do ZARC uma vez por dia. O portal informa a data de modificação de cada arquivo: só baixa
 * de novo (cerca de 200 MB por safra) quando ela muda. A safra antiga só some depois que a nova termina de gravar.
 */
@Service
public class ZarcImportService {
    private static final int BATCH = 2_000;
    private static final String INSERT = """
            INSERT INTO tb_zarc_risk (harvest, crop_code, crop_name, cycle_code, soil_code, ibge_code, uf, municipality,
                municipality_key, climate_name, management_code, management_name, productivity, management_level, ordinance, risks)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final ZarcProperties properties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final HttpClient http;
    private final AtomicBoolean running = new AtomicBoolean();

    public ZarcImportService(ZarcProperties properties, JdbcTemplate jdbc, TransactionTemplate transaction,
                             @Qualifier("sentinelaClock") Clock clock) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 86_400_000)
    public void importAll() {
        if (!properties.enabled() || Util.isNullOrEmpty(properties.files())) return;
        if (!running.compareAndSet(false, true)) return;
        try {
            for (ZarcProperties.SourceFile file : properties.files()) importFile(file);
        } finally {
            running.set(false);
        }
    }

    private void importFile(ZarcProperties.SourceFile file) {
        try {
            String modified = lastModified(file.url());
            String imported = jdbc.query("SELECT source_modified FROM tb_zarc_import WHERE harvest = ?",
                    rs -> rs.next() ? rs.getString(1) : null, file.harvest());
            if (Util.nonNull(modified) && modified.equals(imported)) return;
            LogUtil.info("Importando ZARC da safra {0}", file.harvest());
            int rows = download(file, modified);
            LogUtil.info("ZARC da safra {0} importado: {1} linhas", file.harvest(), rows);
        } catch (IOException | RuntimeException failure) {
            LogUtil.warn("Falha ao importar o ZARC da safra " + file.harvest(), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LogUtil.warn("Importação do ZARC interrompida na safra {0}", file.harvest());
        }
    }

    private String lastModified(String url) throws IOException, InterruptedException {
        HttpResponse<Void> response = http.send(request(url).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) throw new IOException("Portal do ZARC respondeu HTTP " + response.statusCode());
        return response.headers().firstValue("Last-Modified").orElse(null);
    }

    private int download(ZarcProperties.SourceFile file, String modified) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request(file.url()).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Portal do ZARC respondeu HTTP " + response.statusCode());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            Integer rows = transaction.execute(status -> {
                jdbc.update("DELETE FROM tb_zarc_risk WHERE harvest = ?", file.harvest());
                int total = store(reader, file.harvest());
                jdbc.update("DELETE FROM tb_zarc_import WHERE harvest = ?", file.harvest());
                jdbc.update("INSERT INTO tb_zarc_import (harvest, source_url, source_modified, row_count, imported_at) VALUES (?, ?, ?, ?, ?)",
                        file.harvest(), file.url(), modified, total, Timestamp.valueOf(LocalDateTime.now(clock)));
                return total;
            });
            return Util.isNull(rows) ? 0 : rows;
        }
    }

    /** Uma linha ruim é contada e ignorada; um arquivo sem nenhuma linha válida desfaz a importação inteira. */
    private int store(BufferedReader reader, String harvest) {
        try {
            String header = reader.readLine();
            if (Util.isNullOrBlank(header)) throw new IllegalStateException("Arquivo do ZARC vazio");
            ZarcCsv csv = new ZarcCsv(header);
            List<ZarcCsv.Row> batch = new ArrayList<>(BATCH);
            int total = 0;
            int skipped = 0;
            for (String line = reader.readLine(); Util.nonNull(line); line = reader.readLine()) {
                ZarcCsv.Row row = csv.parse(line);
                if (Util.isNull(row)) {
                    skipped++;
                    continue;
                }
                batch.add(row);
                if (batch.size() == BATCH) {
                    insert(harvest, batch);
                    total += batch.size();
                    batch.clear();
                }
            }
            insert(harvest, batch);
            total += batch.size();
            if (skipped > 0) LogUtil.warn("ZARC da safra {0}: {1} linhas fora do formato ignoradas", harvest, skipped);
            if (total == 0) throw new IllegalStateException("Nenhuma linha válida no arquivo do ZARC");
            return total;
        } catch (IOException failure) {
            throw new IllegalStateException("Falha ao ler o arquivo do ZARC", failure);
        }
    }

    private void insert(String harvest, List<ZarcCsv.Row> rows) {
        if (Util.isNullOrEmpty(rows)) return;
        jdbc.batchUpdate(INSERT, rows, rows.size(), (statement, row) -> {
            statement.setString(1, harvest);
            statement.setString(2, row.cropCode());
            statement.setString(3, row.cropName());
            statement.setInt(4, row.cycleCode());
            statement.setInt(5, row.soilCode());
            statement.setInt(6, row.ibgeCode());
            statement.setString(7, row.uf());
            statement.setString(8, row.municipality());
            statement.setString(9, row.municipalityKey());
            statement.setString(10, row.climateName());
            statement.setInt(11, row.managementCode());
            statement.setString(12, row.managementName());
            statement.setString(13, row.productivity());
            if (Util.isNull(row.managementLevel())) statement.setNull(14, Types.SMALLINT);
            else statement.setInt(14, row.managementLevel());
            statement.setString(15, row.ordinance());
            statement.setString(16, row.risks());
        });
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(properties.timeout()).header("User-Agent", properties.userAgent());
    }
}
