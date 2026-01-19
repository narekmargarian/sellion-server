package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Role;
import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    // Мы будем использовать PasswordEncoder, который определен в SecurityConfig
    private final PasswordEncoder passwordEncoder;

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User saveUser(User user) {
        // Всегда кодируем пароль перед сохранением
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    public Optional<User> toggleUserStatus(Long id) {
        return userRepository.findById(id).map(user -> {
            // Вам нужно добавить поле 'enabled' (boolean) в вашу сущность User.java
            // user.setEnabled(!user.isEnabled());
            userRepository.save(user);
            return user;
        });
    }

    @Transactional
    public Optional<User> resetPassword(Long id, String defaultPassword) {
        return userRepository.findById(id).map(user -> {
            // Кодируем стандартный пароль "qwerty" или тот, что передали
            user.setPassword(passwordEncoder.encode(defaultPassword));
            userRepository.save(user);
            return user;
        });
    }


    @EventListener(ApplicationReadyEvent.class)
    public void initAdminPassword() {
        userRepository.findByUsername("admin").ifPresent(user -> {
            // Устанавливаем зашифрованный пароль 'qwerty' или '1111'
            user.setPassword(passwordEncoder.encode("1111"));
            userRepository.save(user);
            System.out.println("✅ Пароль администратора обновлен и зашифрован.");
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void setupDefaultAdmin() {
        User admin = userRepository.findByUsername("admin").orElse(new User());
        admin.setUsername("admin");
        admin.setFullName("Администратор Системы");
        admin.setRole(Role.ADMIN);
        // Кодируем стандартный пароль "1111"
        admin.setPassword(passwordEncoder.encode("1111"));
        userRepository.save(admin);
        System.out.println("--- СИСТЕМА: Пароль admin обновлен/создан на 1111 ---");
    }

}