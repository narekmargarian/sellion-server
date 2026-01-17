package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
import com.sellion.sellionserver.repository.OrderRepository;
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

    @PostMapping("/orders/sync")
    @Transactional
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders != null && !orders.isEmpty()) {
            orders.forEach(order -> {
                order.setId(null);
                order.setStatus(OrderStatus.RESERVED);

                // Установка даты создания
                if (order.getCreatedAt() == null || order.getCreatedAt().isEmpty()) {
                    order.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                // КРИТИЧЕСКИЙ МОМЕНТ: Списываем товары со склада при получении заказа
                try {
                    stockService.reserveItemsFromStock(order.getItems(), "Заказ с Android (" + order.getShopName() + ")");
                } catch (Exception e) {
                    // Если товара не хватило, логируем ошибку, но заказ сохраняем (или можно выкинуть ошибку)
                    System.err.println("Ошибка списания при синхронизации: " + e.getMessage());
                }
            });

            orderRepository.saveAll(orders);
            messagingTemplate.convertAndSend("/topic/new-order", "Новый заказ получен!");

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


}
