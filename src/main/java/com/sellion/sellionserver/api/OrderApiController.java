package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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
    @PostMapping("/sync")
    @Transactional
    public ResponseEntity<Map<String, String>> syncOrders(@RequestBody List<Order> orders) {
        if (orders != null && !orders.isEmpty()) {
            for (Order order : orders) {
                order.setId(null); // Сбрасываем Android ID, чтобы MySQL создал свой
            }
            orderRepository.saveAll(orders);
        }
        return ResponseEntity.ok(Collections.singletonMap("status", "success"));
    }

    /**
     * Дополнительный метод: получить все заказы (для тестов в браузере)
     */
    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}