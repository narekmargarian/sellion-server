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
        return new ApiKeyAuthFilter(apiKeyRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Настройки заголовков и CORS
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Отключаем CSRF для API (так как Android использует API-ключи, а не сессии)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-sellion/**", "/api/**"))

                // 3. Добавляем наш фильтр API-ключей перед стандартным логином
                .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // --- МОБИЛЬНОЕ API ---
                        // Разрешаем только получение списка менеджеров (для экрана выбора)
                        .requestMatchers("/api/public/managers").permitAll()
                        // Все остальные API методы ТРЕБУЮТ наличия ROLE_MANAGER (её дает наш фильтр)
                        .requestMatchers("/api/**").hasAuthority("ROLE_MANAGER")

                        // --- ПАНЕЛЬ УПРАВЛЕНИЯ (Web) ---
                        .requestMatchers("/admin/settings/**", "/api/admin/settings/**").hasRole("ADMIN")
                        .requestMatchers("/admin/users/**", "/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/admin/dashboard-stats/**").hasRole("ADMIN")
                        .requestMatchers("/admin/invoices/**", "/api/payments/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/admin/reports/**", "/api/reports/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")

                        // --- ПУБЛИЧНЫЕ РЕСУРСЫ ---
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/ws-sellion/**").permitAll()
                        .anyRequest().authenticated()
                )

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
        return new BCryptPasswordEncoder();
    }
}
