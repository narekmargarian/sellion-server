package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final OrderRepository orderRepository;

    public OrderApiController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Эндпоинт для синхронизации заказов.
     * Принимает список заказов из Android и сохраняет их в MySQL.
     */
    @Transactional
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncOrders(@RequestBody List<Order> orders) {
        System.out.println(">>> ПОЛУЧЕНО ЗАКАЗОВ: " + (orders != null ? orders.size() : 0));

        if (orders != null && !orders.isEmpty()) {
            for (Order order : orders) {
                // КЛЮЧЕВОЙ МОМЕНТ: обнуляем ID перед сохранением.
                // Теперь MySQL сама присвоит новый уникальный ID (Auto Increment).
                order.setId(null);
            }
            orderRepository.saveAll(orders);
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    /**
     * Дополнительный метод: получить все заказы (для тестов в браузере)
     */
    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}