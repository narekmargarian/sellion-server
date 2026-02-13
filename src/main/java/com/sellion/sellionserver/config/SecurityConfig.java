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
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/ws-sellion/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()

                        // Доступ к API управления ключами и пользователями — только ADMIN
                        .requestMatchers("/api/admin/manager-keys/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/settings/**", "/api/admin/users/**").hasRole("ADMIN")

                        // Настройки и статистика — только ADMIN
                        .requestMatchers("/admin/settings/**", "/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/admin/dashboard-stats/**").hasRole("ADMIN")

                        // Доступ к операционным API
                        .requestMatchers("/api/admin/returns/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/admin/orders/**").hasAnyRole("ADMIN", "OPERATOR")

                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "OPERATOR" , "ACCOUNTANT")
                        .requestMatchers("/api/products/**").hasAnyRole("ADMIN", "OPERATOR" , "ACCOUNTANT")
                        .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "OPERATOR" , "ACCOUNTANT")
                        .requestMatchers("/api/clients/**").hasAnyRole("ADMIN", "OPERATOR" , "ACCOUNTANT")
                        .requestMatchers("/api/register/**").hasAnyRole("ADMIN", "OPERATOR" , "ACCOUNTANT")

                        // API для мобильных менеджеров
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN")

                        // Доступ к Web-разделам по ролям
                        .requestMatchers("/admin/invoices/**", "/api/payments/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/admin/reports/**", "/api/reports/**").hasAnyRole("ADMIN", "ACCOUNTANT", "OPERATOR")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")

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
