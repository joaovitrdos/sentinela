package br.com.sentinela.report;

import java.util.List;
import java.util.Map;

/** Dados já autorizados e validados que o ReportService entrega ao Jasper. */
public record PreparedReport(Map<String, Object> parameters, List<Map<String, ?>> rows) {

    /** Use rows nulo quando o JRXML contém a consulta SQL e deve usar JDBC. */
    public static PreparedReport jdbc(Map<String, Object> parameters) {
        return new PreparedReport(parameters, null);
    }
}
