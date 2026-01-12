package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.ProductRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/products")
public class ProductWebController {

    private final ProductRepository productRepository;

    public ProductWebController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 1. Отображение страницы со списком товаров
    @GetMapping
    public String listProducts(Model model) {
        model.addAttribute("products", productRepository.findAll());
        return "products-list"; // Имя твоего HTML файла (без .html)
    }

    // 2. Обработка формы обновления цены
    @PostMapping("/update-price")
    public String updatePrice(@RequestParam("id") Long id,
                              @RequestParam("newPrice") Double newPrice) {

        productRepository.findById(id).ifPresent(product -> {
            product.setPrice(newPrice);
            productRepository.save(product);
        });

        // После обновления возвращаем пользователя обратно на список товаров
        return "redirect:/admin/products";
    }
}
