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
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User saveUser(User user) {
        // 1. Проверка на дубликат логина
        if (user.getId() == null) {
            if (userRepository.existsByUsername(user.getUsername())) {
                throw new RuntimeException("Пользователь с логином '" + user.getUsername() + "' уже существует!");
            }
        } else {
            userRepository.findByUsername(user.getUsername()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(user.getId())) {
                    throw new RuntimeException("Логин '" + user.getUsername() + "' уже занят другим сотрудником!");
                }
            });
        }

        // 2. Шифрование пароля (ОДНОКРАТНОЕ)
        // ВАЖНО: В UserApiController.editUser мы теперь передаем сырой пароль,
        // и здесь он надежно шифруется перед сохранением в БД.
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
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

    @Transactional
    public void toggleUserStatus(Long id) {
        userRepository.findById(id).ifPresent(user -> {
            // ИСПРАВЛЕНО: Для объекта Boolean геттер называется getEnabled()
            // Также добавлена проверка на null для безопасности
            boolean currentStatus = (user.getEnabled() != null) ? user.getEnabled() : true;
            user.setEnabled(!currentStatus);
            userRepository.save(user);
            log.info("Статус пользователя {} изменен на {}", user.getUsername(), user.getEnabled());
        });
    }
}
