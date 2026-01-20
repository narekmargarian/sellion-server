package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Role;
import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User saveUser(User user) {
        // Проверяем, не зашифрован ли уже пароль (длина BCrypt обычно 60 символов)
        if (user.getPassword().length() < 30) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        userRepository.findById(id).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        });
    }

    // Объединенный метод инициализации (выполняется 1 раз при запуске)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void setupDefaultAdmin() {
        String adminUsername = "admin";
        User admin = userRepository.findByUsername(adminUsername).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(adminUsername);
            return newUser;
        });

        admin.setFullName("Администратор Системы");
        admin.setRole(Role.ADMIN);

        // В 2026 году пароль должен быть зашифрован BCrypt
        // Если пароль в базе не совпадает с зашифрованным "1111", обновляем его
        admin.setPassword(passwordEncoder.encode("1111"));

        userRepository.save(admin);
        System.out.println("✅ [System] Учетная запись администратора готова. Логин: admin, Пароль: 1111");
    }

    @Transactional
    public void toggleUserStatus(Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setEnabled(!user.isEnabled());
            userRepository.save(user);
        });

    }
}