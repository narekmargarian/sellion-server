package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .headers(headers -> headers
//                        .frameOptions(frame -> frame.sameOrigin()) // Разрешено для печати в iframe
//                )
//                .authorizeHttpRequests(auth -> auth
//                        // Открытые ресурсы
//                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/ws-sellion/**").permitAll()
//
//                        // API для Android должны быть защищены (обычно через Basic Auth или Token)
//                        // Но пока оставим доступ для разработки, но ограничим админку:
//                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
//                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
//                        .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "OPERATOR", "ACCOUNTANT")
//
//                        // Все остальные запросы требуют логина
//                        .anyRequest().authenticated()
//                )
//                .formLogin(form -> form
//                        .loginPage("/login")
//                        .defaultSuccessUrl("/admin", true)
//                        .permitAll()
//                )
//                .logout(logout -> logout
//                        .logoutUrl("/logout")
//                        .logoutSuccessUrl("/login?logout")
//                        .permitAll()
//                );
//
//        return http.build();
//    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Активируем CORS с вашими настройками
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. CSRF ВКЛЮЧЕН (для безопасности форм)
                // Игнорируем только для WebSocket, если они не поддерживают CSRF
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-sellion/**","/api/**"))

//                .headers(headers -> headers
//                        .frameOptions(f -> f.sameOrigin())
//                        .contentSecurityPolicy(csp -> csp.policyDirectives("upgrade-insecure-requests;")) // Форсируем HTTPS в 2026
//                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OPERATOR","ACCOUNTANT")
                        .requestMatchers("/", "/login", "/css/**", "/js/**", "/ws-sellion/**").permitAll()


                        .anyRequest().permitAll()

                )

                // 3. SESSION MANAGEMENT (Защита от параллельных входов)
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .expiredUrl("/login?expired")
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID") // Удаляем куки при выходе
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Разрешаем доступ для Android-приложения и веб-интерфейса
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        // УСТАНАВЛИВАЕМ МАКСИМАЛЬНОЕ ВРЕМЯ (30 дней)
        // 2592000 секунд = 30 суток
        configuration.setMaxAge(2592000L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt - золотой стандарт 2026 года
        return new BCryptPasswordEncoder();
    }
}