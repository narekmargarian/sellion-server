package com.sellion.sellionserver.config;

import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import jakarta.servlet.http.HttpServletResponse;
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
                .addFilterBefore(new PlatformBlockerFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)


                // КРИТИЧНО: Чтобы API не выдавало HTML-страницу логина при ошибках
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/") // Простая проверка пути без матчера
                        )
                )


                .authorizeHttpRequests(auth -> auth
                        // 1. ПУБЛИЧНЫЕ РЕСУРСЫ
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/img/**", "/ws-sellion/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/public/**").permitAll()

                        // 2. АДМИНИСТРИРОВАНИЕ (ТОЛЬКО АДМИН)
                        // Вкладки Персонал, Менеджеры и Настройки защищены здесь
                        .requestMatchers("/api/admin/settings/**", "/api/admin/users/**", "/api/admin/manager-keys/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/orders/write-off").hasAnyRole("ADMIN","OPERATOR")

                        // 3. СКЛАД / ПРОДУКТЫ
                        // Инвентаризация, создание и удаление — даем доступ ADMIN, OPERATOR, ACCOUNTANT по вашему запросу
                        .requestMatchers("/api/products/create", "/api/products/import", "/api/admin/products/*/inventory").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        // Просмотр доступен всем, включая MANAGER (мобилка)
                        .requestMatchers("/api/products/**", "/api/admin/products/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")

                        // 4. ЗАКАЗЫ И ВОЗВРАТЫ
                        .requestMatchers("/api/orders/**", "/api/returns/**", "/api/admin/orders/**", "/api/admin/returns/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")

                        // 5. КЛИЕНТЫ И АКЦИИ
                        .requestMatchers("/api/clients/**", "/api/admin/clients/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")
                        .requestMatchers("/api/admin/promos/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT", "MANAGER")

                        // 6. ПЕЧАТЬ И ОТЧЕТЫ
                        .requestMatchers("/admin/invoices/print/**", "/admin/orders/print/**", "/admin/returns/print/**", "/admin/logistic/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        .requestMatchers("/api/payments/**", "/api/reports/**", "/admin/invoices/**").hasAnyRole("ADMIN", "ACCOUNTANT", "OPERATOR")

                        // 7. WEB-ИНТЕРФЕЙС
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
        return new BCryptPasswordEncoder();
    }
}
