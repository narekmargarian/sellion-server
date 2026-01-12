package com.sellion.sellionserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainWebController {

    @GetMapping("/admin")
    public String showDashboard() {
        return "dashboard"; // Откроет dashboard.html
    }

    // Сделаем так, чтобы пустой адрес тоже вел в админку
    @GetMapping("/")
    public String index() {
        return "redirect:/admin";
    }
}