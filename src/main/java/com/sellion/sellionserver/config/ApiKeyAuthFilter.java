package com.sellion.sellionserver.config;

import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final ManagerApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(ManagerApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {


        // Оптимизация: проверяем ключ только если это API запрос
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiKey = request.getHeader("X-API-Key");

        if (apiKey != null && !apiKey.isEmpty()) {
            apiKeyRepository.findByApiKeyHash(apiKey).ifPresent(managerKey -> {
                // Важно: Добавляем ROLE_MANAGER, чтобы прошел аутентификацию
                List<SimpleGrantedAuthority> authorities =
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_MANAGER"));

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        managerKey.getManagerId(), null, authorities);

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        filterChain.doFilter(request, response);
    }
}
