package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Role;
import com.sellion.sellionserver.entity.User;
import com.sellion.sellionserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserWebController {

    private final UserRepository userRepository;

    // 1. Просмотр списка всех пользователей
    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "users-list";
    }

    // 2. Форма создания нового пользователя
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", Role.values()); // Передаем список ролей из Enum
        return "user-form";
    }

    // 3. Сохранение пользователя
    @PostMapping("/save")
    public String saveUser(@ModelAttribute User user) {
        // В 2026 году здесь должна быть проверка на уникальность логина
        userRepository.save(user);
        return "redirect:/admin/users";
    }

    // 4. Блокировка / Активация пользователя
    @PostMapping("/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            // Если в вашей сущности User нет поля enabled, добавьте его:
            // boolean isEnabled = user.isEnabled();
            // user.setEnabled(!isEnabled);
            userRepository.save(user);
        });
        return "redirect:/admin/users";
    }

    // 5. Сброс пароля (установка стандартного "1111")
    @PostMapping("/reset-password/{id}")
    public String resetPassword(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setPassword("1111");
            userRepository.save(user);
        });
        return "redirect:/admin/users";
    }
}
