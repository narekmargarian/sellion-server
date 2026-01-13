package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductRepository productRepository;

    public ProductApiController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product updatedProduct) {
        return productRepository.findById(id).map(product -> {
            product.setName(updatedProduct.getName());
            product.setPrice(updatedProduct.getPrice());
            product.setItemsPerBox(updatedProduct.getItemsPerBox());
            product.setBarcode(updatedProduct.getBarcode());
            product.setStockQuantity(updatedProduct.getStockQuantity());
            productRepository.save(product);
            return ResponseEntity.ok("Обновлено");
        }).orElse(ResponseEntity.notFound().build());
    }

}