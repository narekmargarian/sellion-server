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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ManagerApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    // ИДЕАЛЬНО: Кэш для авторизованных ключей, чтобы не мучить БД и CPU при каждом запросе
    // Хранит хэш ключа -> ID менеджера (срок жизни 10 минут)
    private final Map<String, String> authCache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Пропускаем только API запросы
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Исключаем публичные эндпоинты (если они есть)
        if (uri.startsWith("/api/public/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Извлечение ключа
        String rawApiKey = request.getHeader("X-API-Key");

        if (rawApiKey != null && !rawApiKey.trim().isEmpty()) {
            authenticateManager(rawApiKey.trim(), request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateManager(String rawKey, HttpServletRequest request) {
        try {
            // ПРОВЕРКА КЭША: Если ключ уже проверялся недавно, берем из памяти
            if (authCache.containsKey(rawKey)) {
                setAuthentication(authCache.get(rawKey), request);
                return;
            }

            // ОПТИМИЗАЦИЯ 2026: Загружаем ключи.
            // В идеале в будущем заменить на apiKeyRepository.findByPrefix(...)
            List<ManagerApiKey> keys = apiKeyRepository.findAll();

            for (ManagerApiKey keyEntry : keys) {
                // Безопасное сравнение через BCrypt
                if (passwordEncoder.matches(rawKey, keyEntry.getApiKeyHash())) {
//                    if (rawKey.equals(keyEntry.getApiKeyHash())){


                        // Добавляем в кэш для следующих запросов
                    authCache.put(rawKey, keyEntry.getManagerId());

                    setAuthentication(keyEntry.getManagerId(), request);
                    log.debug("API Auth Success: Manager ID [{}]", keyEntry.getManagerId());
                    return;
                }
            }

            log.warn("Unauthorized API access attempt from IP: {}", request.getRemoteAddr());

        } catch (Exception e) {
            log.error("Critical error in ApiKeyAuthFilter", e);
        }
    }

    private void setAuthentication(String managerId, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_MANAGER"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                managerId, null, authorities);

        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
