package br.com.sentinela.enums;

import br.com.sentinela.util.Util;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public enum ReportFormat {
    PDF("pdf", "application/pdf"),
    HTML("html", "text/html; charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ODT("odt", "application/vnd.oasis.opendocument.text"),
    CSV("csv", "text/csv; charset=UTF-8"),
    RTF("rtf", "application/rtf"),
    TXT("txt", "text/plain; charset=UTF-8");

    private final String extension;
    private final MediaType mediaType;

    ReportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = MediaType.parseMediaType(mediaType);
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public static ReportFormat from(String value) {
        if (Util.isNullOrEmpty(value) || Util.isNullOrBlank(value)) {
            return PDF;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "pdf" -> PDF;
            case "html" -> HTML;
            case "xls", "xlsx" -> XLSX;
            case "odf", "odt" -> ODT;
            case "csv" -> CSV;
            case "rtf" -> RTF;
            case "txt" -> TXT;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de relatório não suportado: " + value);
        };
    }
}
