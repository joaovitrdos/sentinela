package br.com.sentinela.service;

import br.com.sentinela.dto.response.GeneratedReportResposneDto;
import br.com.sentinela.enums.ReportFormat;
import br.com.sentinela.exception.ReportGenerationException;
import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import br.com.sentinela.util.LogUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.engine.export.JRTextExporter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.export.SimpleTextReportConfiguration;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final List<ReportProvider> reportProviders;
    private final ResourceLoader resourceLoader;
    private final DataSource dataSource;
    private Map<String, ReportProvider> providersByCode;
    private final Map<String, JasperReport> compiledReports = new ConcurrentHashMap<>();

    @PostConstruct
    void initializeProviders() {
        providersByCode = reportProviders.stream().collect(Collectors.toUnmodifiableMap(
                ReportProvider::code, Function.identity()));
    }

    public List<ReportProvider> getAvailableReports() {
        return reportProviders.stream()
                .sorted((left, right) -> left.code().compareTo(right.code()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GeneratedReportResposneDto generate(String reportCode, String formatValue,
            UUID userId, Map<String, Object> requestedParameters) {
        ReportProvider provider = providersByCode.get(reportCode);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatório não encontrado");
        }

        ReportFormat format = ReportFormat.from(formatValue);
        PreparedReport prepared = provider.prepare(userId,
                requestedParameters == null ? Map.of() : requestedParameters);
        try {
            JasperReport template = compiledReports.computeIfAbsent(provider.code(), ignored -> compile(provider));
            JasperPrint filledReport;
            if (prepared.rows() != null) {
                JRDataSource reportDataSource = new JRMapCollectionDataSource(prepared.rows());
                filledReport = JasperFillManager.fillReport(template, prepared.parameters(), reportDataSource);
            } else {
                Connection connection = DataSourceUtils.getConnection(dataSource);
                try {
                    filledReport = JasperFillManager.fillReport(template, prepared.parameters(), connection);
                } finally {
                    DataSourceUtils.releaseConnection(connection, dataSource);
                }
            }
            byte[] content = export(filledReport, format);
            return new GeneratedReportResposneDto(content,
                    provider.code() + "." + format.extension(), format.mediaType());
        } catch (JRException exception) {
            LogUtil.error("Falha ao gerar relatório Jasper " + reportCode, exception);
            throw new ReportGenerationException("Não foi possível gerar o relatório", exception);
        }
    }

    private JasperReport compile(ReportProvider provider) {
        String resourcePath = "classpath:" + provider.templatePath();
        Resource resource = resourceLoader.getResource(resourcePath);
        try (InputStream input = resource.getInputStream()) {
            return JasperCompileManager.compileReport(input);
        } catch (Exception exception) {
            LogUtil.error("Falha ao compilar template Jasper " + resourcePath, exception);
            throw new ReportGenerationException("Template de relatório inválido: " + provider.code(), exception);
        }
    }

    private byte[] export(JasperPrint report, ReportFormat format) throws JRException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var input = new SimpleExporterInput(report);
        switch (format) {
            case PDF -> {
                JRPdfExporter exporter = new JRPdfExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
                exporter.exportReport();
            }
            case HTML -> {
                HtmlExporter exporter = new HtmlExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleHtmlExporterOutput(output));
                exporter.exportReport();
            }
            case XLSX -> {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
                exporter.exportReport();
            }
            case ODT -> {
                JROdtExporter exporter = new JROdtExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
                exporter.exportReport();
            }
            case CSV -> {
                JRCsvExporter exporter = new JRCsvExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleWriterExporterOutput(output));
                exporter.exportReport();
            }
            case RTF -> {
                JRRtfExporter exporter = new JRRtfExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleWriterExporterOutput(output));
                exporter.exportReport();
            }
            case TXT -> {
                JRTextExporter exporter = new JRTextExporter();
                exporter.setExporterInput(input);
                exporter.setExporterOutput(new SimpleWriterExporterOutput(output));
                SimpleTextReportConfiguration configuration = new SimpleTextReportConfiguration();
                configuration.setCharWidth(6f);
                configuration.setCharHeight(10f);
                exporter.setConfiguration(configuration);
                exporter.exportReport();
            }
        }
        return output.toByteArray();
    }
}
