package com.sellion.sellionserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login"; // Вернет login.html из папки templates
    }

    @GetMapping("/")
    public String index() {
        // Перенаправляем в админку. Если юзер не залогинен, Security сам кинет на /login
        return "redirect:/admin";
    }
}