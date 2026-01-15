package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
        // Всегда кодируем пароль перед сохранением!
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
}