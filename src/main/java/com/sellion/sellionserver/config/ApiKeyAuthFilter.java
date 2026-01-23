package com.sellion.sellionserver.config;

import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor // Автоматически создаст конструктор для final полей
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ManagerApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder; // Добавлено для сверки хэшей

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Пропускаем всё, что не относится к API
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Извлекаем ключ и очищаем от лишних пробелов
        String rawApiKey = request.getHeader("X-API-Key");

        if (rawApiKey != null && !rawApiKey.trim().isEmpty()) {
            authenticateManager(rawApiKey.trim(), request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateManager(String rawKey, HttpServletRequest request) {
        try {
            // В идеальном проекте 2026 года мы получаем все активные ключи
            // (в высоконагруженных системах здесь используется Cache)
            List<ManagerApiKey> keys = apiKeyRepository.findAll();

            for (ManagerApiKey keyEntry : keys) {
                // ИДЕАЛЬНО: Безопасное сравнение сырого ключа с хэшем в БД
                if (passwordEncoder.matches(rawKey, keyEntry.getApiKeyHash())) {

                    List<SimpleGrantedAuthority> authorities =
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_MANAGER"));

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            keyEntry.getManagerId(), null, authorities);

                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Устанавливаем аутентификацию в контекст Spring Security
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    log.debug("Успешная API-авторизация: Менеджер ID [{}]", keyEntry.getManagerId());
                    return; // Ключ найден, выходим из цикла
                }
            }
            log.warn("Неудачная попытка доступа к API с неверным ключом от IP: {}", request.getRemoteAddr());

        } catch (Exception e) {
            log.error("Критическая ошибка при проверке API-ключа", e);
        }
    }
}
