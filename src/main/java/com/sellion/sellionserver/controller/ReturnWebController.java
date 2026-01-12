package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.ReturnOrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/returns")
public class ReturnWebController {
    private final ReturnOrderRepository returnOrderRepository;

    public ReturnWebController(ReturnOrderRepository returnOrderRepository) {
        this.returnOrderRepository = returnOrderRepository;
    }

    @GetMapping
    public String listReturns(Model model) {
        model.addAttribute("returns", returnOrderRepository.findAll());
        return "returns-list";
    }
}
