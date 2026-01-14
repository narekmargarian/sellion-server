package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Публичные ресурсы
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/api/**").permitAll()

                        // ADMIN - Полный контроль
                        .requestMatchers("/admin/users/**", "/admin/audit/**", "/admin/settings/**").hasAuthority("ADMIN")
                        .requestMatchers("/admin/products/manage/**").hasAuthority("ADMIN")

                        // ACCOUNTANT - Счета, Оплаты, Отчеты
                        .requestMatchers("/admin/invoices/**", "/admin/payments/**", "/admin/reports/**").hasAnyAuthority("ACCOUNTANT", "ADMIN")
                        .requestMatchers("/admin/adjustments/**").hasAnyAuthority("ACCOUNTANT", "ADMIN")

                        // OPERATOR - Заказы, Возвраты, Клиенты (просмотр/создание)
                        .requestMatchers("/admin/orders/**", "/admin/returns/**", "/admin/clients/**").hasAnyAuthority("OPERATOR", "ACCOUNTANT", "ADMIN")

                        // Главная панель - для всех авторизованных
                        .requestMatchers("/admin", "/").authenticated()
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
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}

