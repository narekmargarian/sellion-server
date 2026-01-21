package com.sellion.sellionserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping({"/", "/login", "/{path:[^\\.]*}"})
    public String index() {
        return "login";
    }
}