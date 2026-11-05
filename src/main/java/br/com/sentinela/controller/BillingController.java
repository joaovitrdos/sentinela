package br.com.sentinela.controller;

import br.com.sentinela.dto.request.SubscribeRequestDto;
import br.com.sentinela.dto.response.BillingStatusResponseDto;
import br.com.sentinela.dto.response.PlanResponseDto;
import br.com.sentinela.dto.response.SubscribeResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.BillingService;
import br.com.sentinela.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.List;

@RestController @RequiredArgsConstructor
public class BillingController {
    private final PlanService plans;
    private final BillingService billing;

    @GetMapping("/api/plans")
    public List<PlanResponseDto> plans() {
        return plans.list();
    }

    @GetMapping("/api/billing/me")
    public BillingStatusResponseDto status(@AuthenticationPrincipal User user) {
        return billing.status(user.getUserId());
    }

    @PostMapping("/api/billing/subscribe")
    public SubscribeResponseDto subscribe(@AuthenticationPrincipal User user, @Valid @RequestBody SubscribeRequestDto request) {
        return billing.subscribe(user, request);
    }

    @PostMapping("/api/billing/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal User user) {
        billing.cancel(user.getUserId());
    }

    @PostMapping("/api/billing/webhooks/asaas")
    public void webhook(@RequestHeader(value = "asaas-access-token", required = false) String token, @RequestBody JsonNode event) {
        billing.handleWebhook(token, event);
    }
}
