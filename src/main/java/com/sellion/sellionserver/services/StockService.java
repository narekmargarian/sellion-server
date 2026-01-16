package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;
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
    private final StockMovementRepository movementRepository;

    // --- Существующий метод (теперь без логирования, для обратной совместимости, если нужно) ---
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

    // --- Существующий метод (теперь без логирования) ---
    @Transactional
    public void returnItemsToStock(Map<String, Integer> items) {
        if (items == null) return;
        items.forEach(productRepository::addStock);
    }

//    // --- ОБНОВЛЕННЫЙ: Авто-списание теперь логирует движения товара ---
//    @Scheduled(cron = "0 */3 * * * *") // Авто-списание новых заказов
//    @Transactional
//    public void processNewOrders() {
//        orderRepository.findAllByStatus(OrderStatus.NEW).forEach(order -> {
//            try {
//                // Используем перегруженный метод с причиной
//                deductItemsFromStock(order.getItems(), "Авто-списание заказа #" + order.getId());
//                order.setStatus(OrderStatus.PROCESSED);
//                orderRepository.save(order);
//            } catch (Exception e) {
//                System.err.println("Ошибка списания заказа #" + order.getId() + ": " + e.getMessage());
//            }
//        });
//    }

    @Transactional
    public void reserveItemsFromStock(Map<String, Integer> items, String reason) {
        if (items == null) return;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String name = entry.getKey();
            Integer qty = entry.getValue();

            // 1. Атомарное списание (используем наш безопасный метод)
            int updated = productRepository.deductStock(name, qty);
            if (updated == 0) {
                throw new RuntimeException("Недостаточно товара для резервирования: " + name);
            }

            // 2. Логируем движение как SALE, но с причиной "Резерв"
            logMovement(name, -qty, "SALE", reason, "SYSTEM/MANAGER");
        }
    }


    @Transactional
    public void logMovement(String name, Integer qty, String type, String reason, String operator) {
        StockMovement m = new StockMovement();
        m.setProductName(name);
        m.setQuantityChange(qty);
        m.setType(type);
        m.setReason(reason);
        m.setOperator(operator);
        movementRepository.save(m);
    }

    // --- ПЕРЕГРУЖЕННЫЙ: Списание с логированием (для ручных операций) ---
    @Transactional
    public void deductItemsFromStock(Map<String, Integer> items, String reason) {
        if (items == null) return;
        items.forEach((name, qty) -> {
            int updated = productRepository.deductStock(name, qty);
            if (updated == 0) {
                throw new RuntimeException("Недостаточно товара на складе: " + name);
            }
            logMovement(name, -qty, "SALE", reason, "ADMIN"); // Логируем
        });
    }

    // --- ДОБАВЛЕННЫЙ ПЕРЕГРУЖЕННЫЙ МЕТОД: Возврат с логированием (для ручных операций) ---
    @Transactional
    public void returnItemsToStock(Map<String, Integer> items, String reason) {
        if (items == null) return;
        items.forEach((name, qty) -> {
            productRepository.addStock(name, qty);
            logMovement(name, qty, "RETURN", reason, "ADMIN"); // Логируем возврат
        });
    }
}
