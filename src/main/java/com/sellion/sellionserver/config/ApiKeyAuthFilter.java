package com.sellion.sellionserver.config;

import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ManagerApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(ManagerApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Проверяем только API запросы
        if (request.getRequestURI().startsWith("/api/")) {
            String apiKey = request.getHeader("X-API-Key");

            if (apiKey != null && !apiKey.isEmpty()) {
                // Ищем менеджера по этому ключу в БД
                apiKeyRepository.findByApiKeyHash(apiKey).ifPresent(managerKey -> {
                    // Если нашли, создаем "призрачную" аутентификацию для Spring Security
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            managerKey.getManagerId(), null, Collections.emptyList());

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Теперь запросы из Android будут считаться аутентифицированными!
                });
            }
        }

        filterChain.doFilter(request, response);
    }
}