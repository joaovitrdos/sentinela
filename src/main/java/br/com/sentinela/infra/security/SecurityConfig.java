package br.com.sentinela.infra.security;

import br.com.sentinela.infra.config.SentinelaSecurityProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SentinelaSecurityProperties.class)
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final ErrorResponseWriter errorResponseWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, MaintenanceFilter maintenanceFilter,
                                                   RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rotas protegidas que casariam com as regras publicas abaixo: precisam vir antes.
                        .requestMatchers("/auth/logout", "/auth/logout-all", "/users/me", "/users/me/**").authenticated()
                        .requestMatchers("/auth/**", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/system/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/connection/probe").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/connection/probe").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/connection/status").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> errorResponseWriter
                                .write(response, HttpStatus.UNAUTHORIZED,
                                        "Autenticação necessária para acessar este recurso"))
                        .accessDeniedHandler((request, response, ex) -> errorResponseWriter
                                .write(response, HttpStatus.FORBIDDEN,
                                        "Você não tem permissão para acessar este recurso")))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(maintenanceFilter, SecurityFilter.class)
                .addFilterBefore(rateLimitFilter, MaintenanceFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public static Cache<String, Bucket> rateLimitBuckets() {
        return Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(1)).maximumSize(100_000).build();
    }

    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration(SecurityFilter filter) {
        return securityOnly(filter);
    }

    @Bean
    public FilterRegistrationBean<MaintenanceFilter> maintenanceFilterRegistration(MaintenanceFilter filter) {
        return securityOnly(filter);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        return securityOnly(filter);
    }

    private static <T extends Filter> FilterRegistrationBean<T> securityOnly(T filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
