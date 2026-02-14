package com.sellion.sellionserver.config;

import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;



@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ManagerApiKeyRepository apiKeyRepository;

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter() {
        // ИДЕАЛЬНО: Передаем PasswordEncoder в фильтр для безопасной проверки хэшей
        return new ApiKeyAuthFilter(apiKeyRepository, passwordEncoder());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Настройки заголовков: Добавлен запрет кэширования для защиты от просмотра страниц после Logout
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .cacheControl(cache -> cache.disable()) // Запрещаем браузеру хранить страницы в кэше
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Отключение CSRF для WebSocket и API
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-sellion/**", "/api/**"))

                // 3. Добавляем фильтр API-ключей ПЕРЕД стандартным логином
                .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // 1. ПУБЛИЧНЫЕ РЕСУРСЫ (Доступны всем)
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/img/**", "/ws-sellion/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()

                        // 2. ДОСТУП ДЛЯ ВСЕХ СОТРУДНИКОВ (И мобилка, и офис)
                        // Эти API нужны всем ролям, включая менеджера
                        .requestMatchers("/api/products/**", "/api/clients/**")
                        .hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")

                        // 3. ОПЕРАЦИОННЫЕ API (Заказы и возвраты)
                        // Менеджеры отправляют, операторы обрабатывают
                        .requestMatchers("/api/orders/**", "/api/returns/**", "/api/admin/orders/**", "/api/admin/returns/**")
                        .hasAnyRole("ADMIN", "OPERATOR", "MANAGER")

                        // 4. СПЕЦИФИЧЕСКИЕ API ОФИСА (Только для бухгалтеров и админов)
                        // Сюда мобильное приложение (MANAGER) не попадет
                        .requestMatchers("/api/payments/**", "/api/reports/**", "/admin/invoices/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")

                        .requestMatchers("/api/admin/settings/**", "/api/admin/users/**", "/api/admin/manager-keys/**")
                        .hasRole("ADMIN")

                        // 5. ГЛАВНЫЙ ЗОНТИК ДЛЯ МОБИЛКИ
                        // Любой другой запрос к /api/, не попавший под правила выше,
                        // разрешен только менеджерам и админам.
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN")

                        // 6. WEB-ИНТЕРФЕЙС (Панель управления)
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")

                        // 7. ВСЕ ОСТАЛЬНОЕ
                        .anyRequest().authenticated()
                )



                // 5. НАСТРОЙКИ ЛОГИНА
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true) // Всегда редиректить на /admin после входа
                        .failureUrl("/login?error")
                        .permitAll()
                )

                // 6. НАСТРОЙКИ ВЫХОДА (Исправлено для полной очистки сессии)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)    // Аннулировать текущую сессию
                        .clearAuthentication(true)      // Стереть данные об аутентификации
                        .deleteCookies("JSESSIONID")    // Удалить куки сессии
                        .permitAll()
                );

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Requested-With", "X-API-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(2592000L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Используем BCrypt для безопасного хэширования паролей в 2026 году
        return new BCryptPasswordEncoder();
    }
}
