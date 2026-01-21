package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserApiController {

    private final UserService userService;

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAllUsers();
    }

    @PostMapping("/create")
    public ResponseEntity<?> createUser(@RequestBody User newUser) {
        userService.saveUser(newUser);
        return ResponseEntity.ok(Map.of("message", "Пользователь создан"));
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
    public ResponseEntity<?> editUser(@PathVariable Long id, @RequestBody User updatedUser) {
        userService.saveUser(updatedUser);
        return ResponseEntity.ok(Map.of("message", "Данные обновлены"));
    }
}