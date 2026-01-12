package com.sellion.sellionserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Отключаем защиту CSRF, чтобы Android-приложение могло отправлять POST-запросы (заказы)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Настраиваем права доступа
                .authorizeHttpRequests(auth -> auth
                        // Разрешаем Android-приложению (API) работать без логина
                        .requestMatchers("/api/**").permitAll()

                        // Статические файлы (картинки, стили) разрешаем всем
                        .requestMatchers("/css/**", "/js/**").permitAll()

                        // Доступ к Товарам: только Операторы и Директор
                        .requestMatchers("/admin/products/**").hasAnyAuthority("OPERATOR", "ADMIN")

                        // Доступ к Возвратам и Долгам: только Бухгалтеры и Директор
                        .requestMatchers("/admin/returns/**", "/admin/debts/**").hasAnyAuthority("ACCOUNTANT", "ADMIN")

                        // Все остальные страницы в /admin доступны всем сотрудникам
                        .requestMatchers("/admin/**").authenticated()

                        .anyRequest().permitAll()
                )

                // 3. Настраиваем форму входа (Login)
                .formLogin(form -> form
                        .loginPage("/login")           // Наш кастомный HTML-файл
                        .defaultSuccessUrl("/admin", true) // Куда идти после успешного входа
                        .permitAll()
                )

                // 4. Настраиваем выход из системы
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    /**
     * В 2026 году для безопасности нужно шифровать пароли (BCrypt).
     * Но для начала, чтобы твои пароли "1111", "2222" из базы сработали,
     * используем временный "пустой" кодировщик.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}