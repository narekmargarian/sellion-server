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
                // 1. Настройки заголовков и CORS
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. ОТКЛЮЧЕНИЕ CSRF (ИСПРАВЛЕНО)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-sellion/**", "/api/**"))

                // 3. Добавляем фильтр API-ключей ПЕРЕД стандартным логином
                .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // --- 1. ПУБЛИЧНЫЕ РЕСУРСЫ ---
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/ws-sellion/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()

                        // --- 2. АДМИНСКИЕ API (КРИТИЧНО: ДОЛЖНЫ БЫТЬ ВЫШЕ ЧЕМ /api/**) ---
                        .requestMatchers("/api/admin/manager-keys/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/returns/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/admin/settings/**", "/api/admin/users/**").hasRole("ADMIN")

                        // --- 3. МОБИЛЬНОЕ API (Android) ---
                        // ИДЕАЛЬНОЕ ИСПРАВЛЕНИЕ: Добавляем ROLE_ADMIN,
                        // чтобы администратор мог использовать ВСЕ API
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN")

                        // --- 4. ВЕБ-ИНТЕРФЕЙС АДМИНКИ (Thymeleaf страницы) ---
                        .requestMatchers("/admin/settings/**", "/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/admin/dashboard-stats/**").hasRole("ADMIN")
                        .requestMatchers("/admin/invoices/**", "/api/payments/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/admin/reports/**", "/api/reports/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")

                        // Все остальные запросы должны быть аутентифицированы
                        .anyRequest().authenticated()
                )

                // 5. НАСТРОЙКИ ЛОГИНА
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID")
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
