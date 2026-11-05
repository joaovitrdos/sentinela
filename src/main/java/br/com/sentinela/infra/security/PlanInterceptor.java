package br.com.sentinela.infra.security;

import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.service.PlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sem plano vigente, o usuário ainda entra, assina, lê o que já cadastrou e exclui propriedades, mas não altera dados.
 * Rotas de propriedade usam o plano do dono, para que a equipe acompanhe o plano contratado por ele.
 */
@Component @RequiredArgsConstructor
public class PlanInterceptor implements HandlerInterceptor {
    private static final List<String> FREE_PREFIXES = List.of("/auth", "/users", "/api/billing", "/api/plans", "/api/system",
            "/api/connection", "/api/team-invitations");

    private final PlanService plans;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isFree(path, request.getMethod())) return true;

        Feature feature = requiredFeature(method, request.getMethod());
        if (feature != null && !plans.allows(user.getUserId(), propertyId(request), feature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu plano não inclui este recurso");
        }
        return true;
    }

    private boolean isFree(String path, String httpMethod) {
        boolean delete = HttpMethod.DELETE.matches(httpMethod);
        boolean leavingTeam = delete && path.endsWith("/team/me");
        boolean removingProperty = delete && path.matches("/api/properties/[^/]+");
        boolean disablingTeam = HttpMethod.PUT.matches(httpMethod) && path.endsWith("/team/mode");
        return leavingTeam || removingProperty || disablingTeam || FREE_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private Feature requiredFeature(HandlerMethod method, String httpMethod) {
        RequiresFeature required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresFeature.class);
        if (required == null) required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresFeature.class);
        if (required != null) return required.value();
        return HttpMethod.GET.matches(httpMethod) || HttpMethod.HEAD.matches(httpMethod) ? null : Feature.BASE;
    }

    @SuppressWarnings("unchecked")
    private UUID propertyId(HttpServletRequest request) {
        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String value = variables == null ? null : variables.get("propertyId");
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
