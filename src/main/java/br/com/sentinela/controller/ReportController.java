package br.com.sentinela.controller;

import br.com.sentinela.util.Util;

import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.dto.request.ReportRequestDto;
import br.com.sentinela.dto.response.GeneratedReportResposneDto;
import br.com.sentinela.dto.response.ReportOptionResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@RequiresFeature(Feature.REPORTS)
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public List<ReportOptionResponseDto> listReports() {
        return reportService.getAvailableReports().stream()
                .map(provider -> new ReportOptionResponseDto(provider.code(), provider.title()))
                .toList();
    }

    @PostMapping("/{reportCode}")
    public ResponseEntity<byte[]> generate(
            @AuthenticationPrincipal User user,
            @PathVariable String reportCode,
            @RequestBody ReportRequestDto request) {
        if (Util.isNull(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os parâmetros do relatório");
        }
        GeneratedReportResposneDto report = reportService.generate(
                reportCode, request.format(), user.getUserId(), request.parameters());
        return ResponseEntity.ok()
                .contentType(report.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(report.filename(), StandardCharsets.UTF_8).build().toString())
                .body(report.content());
    }
}
