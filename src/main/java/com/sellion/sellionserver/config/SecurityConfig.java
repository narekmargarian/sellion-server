package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // --- ДОБАВЛЕНО ДЛЯ ПЕЧАТИ ЧЕРЕЗ IFRAME ---
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                // -----------------------------------------
                .authorizeHttpRequests(auth -> auth
                        // Игнорируем запросы от Chrome DevTools и публичные API
                        .requestMatchers("/.well-known/**", "/ws-sellion/**", "/login", "/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers("/api/public/**", "/api/products/catalog", "/api/orders/sync", "/api/returns/sync").permitAll()

                        // Все авторизованные пользователи могут зайти на главную страницу
                        .requestMatchers("/admin/**").authenticated()

                        // API по умолчанию закрыто, если нет @PreAuthorize в самом контроллере
                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().authenticated()
                )

                // 5. Настройка логина
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true) // Принудительный редирект
                        .permitAll()
                )

                // 6. Настройка выхода
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Используем надежный BCryptPasswordEncoder
        return new BCryptPasswordEncoder();
    }
}