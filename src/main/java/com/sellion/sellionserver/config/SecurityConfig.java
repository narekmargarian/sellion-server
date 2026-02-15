package com.sellion.sellionserver.config;

import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .cacheControl(cache -> cache.disable())
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-sellion/**", "/api/**"))
                .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // 1. ПУБЛИЧНЫЕ РЕСУРСЫ
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/img/**", "/ws-sellion/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()

                        // 2. СКЛАД (ПРОСМОТР И ИСТОРИЯ)
                        // Разрешаем просмотр (GET) всем ролям офиса и мобилки
                        .requestMatchers(HttpMethod.GET, "/api/products/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")

                        // ИНВЕНТАРИЗАЦИЯ, СОЗДАНИЕ, УДАЛЕНИЕ, ИМПОРТ — ТОЛЬКО АДМИН
                        .requestMatchers("/api/admin/products/*/inventory", "/api/products/create", "/api/products/import").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")

                        // 3. АКЦИИ (ПРОСМОТР ДЛЯ ВСЕХ, УДАЛЕНИЕ ДЛЯ АДМИНА И ОПЕРАТОРА)
                        .requestMatchers("/api/admin/promos/filter", "/api/admin/promos/check-active-for-items").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/promos/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/admin/promos/**").hasAnyRole("ADMIN", "MANAGER", "OPERATOR")

                        // 4. КЛИЕНТЫ (ПРОСМОТР И РЕДАКТИРОВАНИЕ)
                        .requestMatchers(HttpMethod.GET, "/api/clients/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/clients/*/edit").hasAnyRole("ADMIN", "OPERATOR")

                        // 5. ОПЕРАЦИОННЫЕ API (ЗАКАЗЫ И ВОЗВРАТЫ)
                        // СПИСАНИЕ ТОВАРА (Write-off) — ТОЛЬКО АДМИН
                        .requestMatchers("/api/admin/orders/write-off").hasRole("ADMIN")
                        // Остальные операции заказов/возвратов
                        .requestMatchers("/api/orders/**", "/api/returns/**", "/api/admin/orders/**", "/api/admin/returns/**").hasAnyRole("ADMIN", "OPERATOR", "MANAGER")

                        // 6. ПЕЧАТЬ И ИНВОЙСЫ
                        .requestMatchers("/admin/invoices/print/**", "/admin/orders/print/**", "/admin/returns/print/**", "/admin/logistic/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        .requestMatchers("/api/payments/**", "/api/reports/**", "/admin/invoices/**").hasAnyRole("ADMIN", "ACCOUNTANT", "OPERATOR")

                        // 7. АДМИНИСТРИРОВАНИЕ (ТОЛЬКО АДМИН)
                        .requestMatchers("/api/admin/settings/**", "/api/admin/users/**", "/api/admin/manager-keys/**").hasRole("ADMIN")

                        // 8. WEB-ИНТЕРФЕЙС (ПАНЕЛЬ УПРАВЛЕНИЯ)
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
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
