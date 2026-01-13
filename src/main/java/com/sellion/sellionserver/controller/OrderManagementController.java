package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderManagementController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;

    @PutMapping("/{id}/edit")
    @Transactional
    public ResponseEntity<?> editOrder(@PathVariable Long id, @RequestBody Map<String, Map<String, Integer>> payload) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        // 1. Запрет редактирования, если выставлен счет
        if (order.getStatus() == OrderStatus.INVOICED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Счёт выставлен. Редактирование запрещено."));
        }

        Map<String, Integer> newItems = payload.get("items");

        try {
            // 2. ЛОГИКА: "Откат и Новое списание"
            // Если заказ уже был PROCESSED (склад списан), сначала возвращаем старое количество на склад
            if (order.getStatus() == OrderStatus.PROCESSED) {
                stockService.returnItemsToStock(order.getItems());
            }

            // 3. Пытаемся списать новое количество
            stockService.deductItemsFromStock(newItems);

            // 4. Пересчитываем общую сумму заказа
            double newTotalAmount = 0.0;
            for (Map.Entry<String, Integer> entry : newItems.entrySet()) {
                Product p = productRepository.findByName(entry.getKey()).orElseThrow();
                newTotalAmount += p.getPrice() * entry.getValue();
            }

            // 5. Обновляем данные заказа
            order.setItems(newItems);
            order.setTotalAmount(newTotalAmount);
            order.setStatus(OrderStatus.PROCESSED); // Гарантируем статус обработанного
            orderRepository.save(order);

            return ResponseEntity.ok(Map.of("message", "Заказ обновлен", "newTotal", newTotalAmount));

        } catch (RuntimeException e) {
            // Если возникла ошибка (например, не хватило товара),
            // благодаря @Transactional все изменения в БД откатятся автоматически.
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}