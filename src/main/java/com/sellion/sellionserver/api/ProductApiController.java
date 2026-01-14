package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products") // Именно этот путь ищет Android
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductApiController {

    private final ProductRepository productRepository;

    @GetMapping
    public List<Product> getAllProducts() {
        // Отдаем все товары для загрузки в телефон
        return productRepository.findAll();
    }
}