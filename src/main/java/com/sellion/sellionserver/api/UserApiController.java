package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Role;
import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.UserRepository;
import com.sellion.sellionserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserApiController {

    private final UserService userService;
    private final UserRepository userRepository; // Добавлено
    private final PasswordEncoder passwordEncoder; // Добавлено

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAllUsers();
    }

    @PostMapping("/create")
    public ResponseEntity<?> createUser(@RequestBody User newUser) {
        try {
            userService.saveUser(newUser);
            return ResponseEntity.ok(Map.of("message", "Пользователь создан"));
        } catch (RuntimeException e) {
            // Отправляем текст ошибки (например: "Пользователь уже существует")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @PostMapping("/reset-password/{id}")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id, "qwerty");
        return ResponseEntity.ok(Map.of("message", "Пароль сброшен на qwerty"));
    }

    @PostMapping("/toggle-status/{id}")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        userService.toggleUserStatus(id);
        return ResponseEntity.ok(Map.of("message", "Статус пользователя изменен"));
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editUser(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            // Находим пользователя
            User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("Не найден"));

            user.setUsername((String) payload.get("username"));
            user.setFullName((String) payload.get("fullName"));
            user.setRole(Role.valueOf((String) payload.get("role")));

            String newPassword = (String) payload.get("password");
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(newPassword));
            }

            userService.saveUser(user); // Используем сервис для проверки логина
            return ResponseEntity.ok(Map.of("message", "Данные обновлены"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Сотрудник удален"));
    }
}
