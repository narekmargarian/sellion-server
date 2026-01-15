package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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

    // --- ОБЩАЯ ЦЕПОЧКА ДЛЯ API и ФРОНТЕНДА (Без CSRF для Android/JS) ---
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))

                // Этот фильтр обрабатывает ТОЛЬКО /api/** запросы
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        // ВРЕМЕННО: Разрешаем полный доступ к аудиту для отладки
                        .requestMatchers("/api/admin/audit/**").permitAll()

                        // Все остальные API открыты (permitAll)
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    // --- ЦЕПОЧКА ДЛЯ HTML-СТРАНИЦ (Требует логина через форму) ---
    @Bean
    public SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(Customizer.withDefaults()) // CSRF включен для веб-форм
                .cors(Customizer.withDefaults())

                // ВАЖНО: Добавляем "/logout" в список matchers
                .securityMatcher("/admin/**", "/login", "/css/**", "/js/**", "/error", "/", "/logout")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").authenticated() // Админка требует авторизации
                        .requestMatchers("/login", "/css/**", "/js/**", "/error", "/", "/favicon.ico", "/logout").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
