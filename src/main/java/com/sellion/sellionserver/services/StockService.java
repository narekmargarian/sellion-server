package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void deductItemsFromStock(Map<String, Integer> items) {
        if (items == null) return;
        items.forEach((name, qty) -> {
            int updated = productRepository.deductStock(name, qty);
            if (updated == 0) {
                throw new RuntimeException("Недостаточно товара на складе: " + name);
            }
        });
    }

    @Transactional
    public void returnItemsToStock(Map<String, Integer> items) {
        if (items == null) return;
        items.forEach(productRepository::addStock);
    }

    @Scheduled(cron = "0 */3 * * * *") // Авто-списание новых заказов
    @Transactional
    public void processNewOrders() {
        orderRepository.findAllByStatus(OrderStatus.NEW).forEach(order -> {
            try {
                deductItemsFromStock(order.getItems());
                order.setStatus(OrderStatus.PROCESSED);
                orderRepository.save(order);
            } catch (Exception e) {
                System.err.println("Ошибка списания заказа #" + order.getId() + ": " + e.getMessage());
            }
        });
    }
}