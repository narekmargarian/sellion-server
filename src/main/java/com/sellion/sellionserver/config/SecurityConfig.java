package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-sellion/**", "/api/**"))
                .authorizeHttpRequests(auth -> auth
                        // 1. Только ADMIN
                        .requestMatchers("/admin/settings/**", "/api/admin/settings/**").hasRole("ADMIN")
                        .requestMatchers("/admin/users/**", "/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/admin/dashboard-stats/**").hasRole("ADMIN") // Обзор

                        // 2. Доступ для ADMIN и ACCOUNTANT (Бухгалтер видит счета и отчеты)
                        .requestMatchers("/admin/invoices/**", "/api/payments/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/admin/reports/**", "/api/reports/**").hasAnyRole("ADMIN", "ACCOUNTANT")

                        // 3. Общие разделы (Заказы, Возвраты, Клиенты, Склад)
                        .requestMatchers("/admin", "/admin/orders/**", "/admin/returns/**", "/admin/clients/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
                        .requestMatchers("/api/products/**", "/api/clients/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")

                        // 4. Публичные ресурсы
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/ws-sellion/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll()
                )
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        // 2592000 секунд = 30 суток
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