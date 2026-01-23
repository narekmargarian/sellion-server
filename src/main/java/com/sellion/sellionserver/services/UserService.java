package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Role;
import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j // Используем Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User saveUser(User user) {
        // Проверяем, не зашифрован ли уже пароль (длина BCrypt обычно 60 символов)
        if (user.getPassword() != null && user.getPassword().length() < 30) {
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

    // ИДЕАЛЬНО: Метод инициализации полностью удален по запросу пользователя.
    // Теперь вы должны создать первого администратора вручную в вашей БД.

    @Transactional
    public void toggleUserStatus(Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setEnabled(!user.isEnabled());
            userRepository.save(user);
        });
    }
}
