package br.com.sentinela.infra.security;

import br.com.sentinela.entity.User;
import br.com.sentinela.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        TokenService.Credentials credentials =
                tokenService.credentialsFrom(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (credentials != null) {
            userRepository.findById(credentials.userId())
                    .filter(credentials::matches)
                    .ifPresent(user -> authenticate(user, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
