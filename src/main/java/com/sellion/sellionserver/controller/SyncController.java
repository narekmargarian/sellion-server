package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api") // Базовый путь для всех синхронизаций
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Обязательно для работы с внешними устройствами
public class SyncController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;

    // Путь будет: /api/orders/sync (согласно логам вашего Android)
    @PostMapping("/orders/sync")
    @Transactional
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders != null && !orders.isEmpty()) {
            orders.forEach(order -> {
                order.setId(null);
                order.setStatus(OrderStatus.NEW);
                // ДОБАВЛЕНО: Устанавливаем текущую дату, если Android не прислал
                if (order.getCreatedAt() == null || order.getCreatedAt().isEmpty()) {
                    // В SyncController.java вместо toString()
                    order.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                }
            });
            orderRepository.saveAll(orders);
            return ResponseEntity.ok(Map.of("status", "success", "count", orders.size()));
        }
        return ResponseEntity.ok(Map.of("status", "empty"));
    }

    // Путь будет: /api/returns/sync
    @PostMapping("/returns/sync")
    @Transactional
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns != null && !returns.isEmpty()) {
            returns.forEach(ret -> {
                ret.setId(null);
                ret.setStatus(ReturnStatus.DRAFT);
                // ДОБАВЛЕНО: Устанавливаем текущую дату, если Android не прислал
                if (ret.getCreatedAt() == null || ret.getCreatedAt().isEmpty()) {
                    ret.setCreatedAt(LocalDateTime.now().toString());
                }
            });
            returnOrderRepository.saveAll(returns);
            return ResponseEntity.ok(Map.of("status", "success", "count", returns.size()));
        }
        return ResponseEntity.ok(Map.of("status", "empty"));
    }

    @GetMapping("/orders/manager/{managerId}")
    public ResponseEntity<List<Order>> getOrdersByManager(@PathVariable String managerId) {
        // Используем ваш существующий метод в репозитории
        return ResponseEntity.ok(orderRepository.findAllByManagerId(managerId));
    }

    @GetMapping("/returns/manager/{managerId}")
    public ResponseEntity<List<ReturnOrder>> getReturnsByManager(@PathVariable String managerId) {
        // Используем ваш существующий метод в репозитории
        return ResponseEntity.ok(returnOrderRepository.findAllByManagerId(managerId));
    }

}
