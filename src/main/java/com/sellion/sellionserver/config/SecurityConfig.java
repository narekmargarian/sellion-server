package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
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
                        // Игнорируем запросы от Chrome DevTools
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/ws-sellion/**", "/login", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/api/**", "/admin/**").permitAll()
                        // Все остальные запросы тоже разрешены
                        .anyRequest().permitAll()
                )

                // 5. Настройка логина
                .formLogin(form -> form
                        .loginPage("/login")         // Указываем вашу страницу
                        .defaultSuccessUrl("/admin") // Куда идти после логина
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
        // В 2026 году для allowCredentials(true) нельзя использовать "*",
        // нужно использовать setAllowedOriginPatterns
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization")); // Полезно для API

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Оставляем NoOp только если это учебный проект/тесты
        return NoOpPasswordEncoder.getInstance();
    }
}
