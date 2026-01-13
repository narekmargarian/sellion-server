package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    // Метод для возврата товаров из заказа обратно на склад
    @Transactional
    public void returnItemsToStock(Map<String, Integer> items) {
        if (items == null) return;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            productRepository.findByName(entry.getKey()).ifPresent(product -> {
                product.setStockQuantity(product.getStockQuantity() + entry.getValue());
                productRepository.save(product);
            });
        }
    }

    // Метод для списания товаров со склада
    @Transactional
    public void deductItemsFromStock(Map<String, Integer> items) {
        if (items == null) return;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product product = productRepository.findByName(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));

            if (product.getStockQuantity() < entry.getValue()) {
                throw new RuntimeException("Недостаточно товара: " + entry.getKey());
            }

            product.setStockQuantity(product.getStockQuantity() - entry.getValue());
            productRepository.save(product);
        }
    }

    // Ваш существующий планировщик (обновленный)
    @Scheduled(cron = "0 */3 * * * *")
    @Transactional
    public void updateStockFromOrders() {
        // Берем только NEW заказы. После списания ставим PROCESSED
        orderRepository.findAllByStatus(OrderStatus.NEW).forEach(order -> {
            try {
                deductItemsFromStock(order.getItems());
                order.setStatus(OrderStatus.PROCESSED);
                orderRepository.save(order);
            } catch (Exception e) {
                System.err.println("Ошибка авто-списания заказа #" + order.getId() + ": " + e.getMessage());
                // Можно поставить статус ERROR или оставить NEW до исправления остатков
            }
        });
    }
}
