package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // Важно: RestController возвращает JSON
@RequestMapping("/api/admin/users") // Все API-пути для админки начинаются здесь
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Все методы здесь требуют роль ADMIN
public class UserApiController {

    private final UserService userService;

    // Этот метод не используется JS, но может пригодиться для отладки
    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAllUsers();
    }

    // Обрабатывает запрос из JS submitCreateUser()
    @PostMapping("/create")
    public ResponseEntity<?> createUser(@RequestBody User newUser) {
        userService.saveUser(newUser);
        return ResponseEntity.ok().build();
    }

    // Обрабатывает запрос из JS resetPassword()
    @PostMapping("/reset-password/{id}")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        // Мы передаем "qwerty" как стандартный пароль
        userService.resetPassword(id, "qwerty");
        return ResponseEntity.ok().build();
    }

    // Если вам нужен метод для редактирования (мы его обсуждали):
    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editUser(@PathVariable Long id, @RequestBody User updatedUser) {
        // Здесь будет логика обновления данных пользователя без смены пароля
        // ...
        return ResponseEntity.ok().build();
    }

    // Обрабатывает запрос из JS toggleUserStatus() (если решите добавить в UI)
    @PostMapping("/toggle-status/{id}")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        userService.toggleUserStatus(id);
        return ResponseEntity.ok().build();
    }
}