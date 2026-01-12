package com.sellion.sellionserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login"; // Открывает login.html
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/admin"; // Если пользователь не залогинен, Spring Security сам кинет его на /login
    }
}