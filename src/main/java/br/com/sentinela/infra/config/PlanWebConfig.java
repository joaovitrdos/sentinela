package br.com.sentinela.infra.config;

import br.com.sentinela.infra.security.PlanInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration @RequiredArgsConstructor
public class PlanWebConfig implements WebMvcConfigurer {
    private final PlanInterceptor planInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planInterceptor);
    }
}
