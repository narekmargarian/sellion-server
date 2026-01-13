package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductWebController {
    private final ProductRepository productRepository;

    // ... listProducts метод не нужен ...

    @PostMapping("/update-price")
    public String updatePrice(@RequestParam("id") Long id, @RequestParam("newPrice") Double newPrice) {
        productRepository.findById(id).ifPresent(p -> {
            p.setPrice(newPrice);
            productRepository.save(p);
        });
        return "redirect:/admin?activeTab=tab-products";
    }
}