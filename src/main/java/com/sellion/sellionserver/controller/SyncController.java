package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final StockService stockService; // ДОБАВЛЕНО
    private final ProductRepository productRepository;


    @PostMapping("/orders/sync")
    @Transactional
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders == null || orders.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        int savedCount = 0;
        for (Order order : orders) {
            // Проверка: если такой заказ (по androidId) уже есть — пропускаем
            if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
                continue;
            }

            order.setId(null);
            order.setStatus(OrderStatus.RESERVED);

            if (order.getCreatedAt() == null || order.getCreatedAt().isEmpty()) {
                order.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }

            order.setTotalPurchaseCost(calculatePurchaseCost(order.getItems()));

            try {
                stockService.reserveItemsFromStock(order.getItems(), "Заказ Android: " + order.getShopName());
                orderRepository.save(order);
                savedCount++;
            } catch (Exception e) {
                System.err.println("Ошибка склада: " + e.getMessage());
            }
        }

        if (savedCount > 0) messagingTemplate.convertAndSend("/topic/new-order", "Новых заказов: " + savedCount);
        return ResponseEntity.ok(Map.of("status", "success", "count", savedCount));
    }



    @PostMapping("/returns/sync")
    @Transactional
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns == null || returns.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        returns.forEach(ret -> {
            ret.setId(null);
            ret.setStatus(ReturnStatus.DRAFT);

            // РАСЧЕТ СУММЫ ВОЗВРАТА (чтобы не было 0)
            double total = 0;
            if (ret.getItems() != null) {
                for (Map.Entry<String, Integer> entry : ret.getItems().entrySet()) {
                    double price = productRepository.findByName(entry.getKey())
                            .map(Product::getPrice).orElse(0.0);
                    total += price * entry.getValue();
                }
            }
            ret.setTotalAmount(total);

            if (ret.getCreatedAt() == null || ret.getCreatedAt().isEmpty()) {
                ret.setCreatedAt(LocalDateTime.now().toString());
            }
        });

        returnOrderRepository.saveAll(returns);
        return ResponseEntity.ok(Map.of("status", "success", "count", returns.size()));
    }


    @GetMapping("/orders/manager/{managerId}/current-month")
    public ResponseEntity<List<Order>> getOrdersByManagerCurrentMonth(@PathVariable String managerId) {
        // Вычисляем начало и конец месяца
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).atTime(23, 59, 59);

        // Форматируем в ISO (как в вашей базе)
        String start = startOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = endOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Используем ваш метод из репозитория, добавив фильтр по менеджеру (нужно добавить в репозиторий)
        return ResponseEntity.ok(orderRepository.findOrdersByManagerAndDateRange(managerId, start, end));
    }

    @GetMapping("/returns/manager/{managerId}/current-month")
    public ResponseEntity<List<ReturnOrder>> getReturnsByManagerCurrentMonth(@PathVariable String managerId) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).atTime(23, 59, 59);

        String start = startOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = endOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return ResponseEntity.ok(returnOrderRepository.findReturnsByManagerAndDateRange(managerId, start, end));
    }

    // --- НОВЫЙ ВСПОМОГАТЕЛЬНЫЙ МЕТОД ---
    private double calculatePurchaseCost(Map<String, Integer> items) {
        double cost = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = productRepository.findByNameAndIsDeletedFalse(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));
            // Используем цену закупки (purchasePrice)
            cost += p.getPurchasePrice() != null ? p.getPurchasePrice() * entry.getValue() : 0.0;
        }
        return cost;
    }

}
