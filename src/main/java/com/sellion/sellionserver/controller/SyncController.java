package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SyncController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StockService stockService;
    private final ProductRepository productRepository;
    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    @PostMapping("/orders/sync")
    @Transactional
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders == null || orders.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        int savedCount = 0;
        LocalDate today = LocalDate.now();
        for (Order order : orders) {
            // 1. Валидация даты доставки
            if (order.getDeliveryDate() != null && order.getDeliveryDate().isBefore(today)) {
                log.error("Заказ отклонен: дата доставки {} уже прошла", order.getDeliveryDate());
                continue;
            }

            // 2. Проверка на дубликаты
            if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
                savedCount++;
                continue;
            }

            order.setId(null);
            order.setStatus(OrderStatus.RESERVED);

            if (order.getCreatedAt() == null || order.getCreatedAt().isEmpty()) {
                order.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }

            // ИСПРАВЛЕНО: Расчет себестоимости через BigDecimal
            order.setTotalPurchaseCost(calculatePurchaseCost(order.getItems()));

            try {
                stockService.reserveItemsFromStock(order.getItems(), "Заказ Android: " + order.getShopName());
                orderRepository.save(order);
                savedCount++;
            } catch (Exception e) {
                log.error("Ошибка склада при синхронизации заказа {}: {}", order.getAndroidId(), e.getMessage());
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

            // ИСПРАВЛЕНО: Расчет суммы возврата через BigDecimal
            BigDecimal total = BigDecimal.ZERO;
            if (ret.getItems() != null) {
                for (Map.Entry<String, Integer> entry : ret.getItems().entrySet()) {
                    BigDecimal price = productRepository.findByName(entry.getKey())
                            .map(Product::getPrice).orElse(BigDecimal.ZERO);
                    BigDecimal qty = BigDecimal.valueOf(entry.getValue());
                    total = total.add(price.multiply(qty));
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
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).atTime(23, 59, 59);

        String start = startOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = endOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

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

    // ИСПРАВЛЕНО: Вспомогательный метод теперь возвращает BigDecimal
    private BigDecimal calculatePurchaseCost(Map<String, Integer> items) {
        BigDecimal cost = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = productRepository.findByNameAndIsDeletedFalse(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));

            BigDecimal purchasePrice = (p.getPurchasePrice() != null) ? p.getPurchasePrice() : BigDecimal.ZERO;
            BigDecimal qty = BigDecimal.valueOf(entry.getValue());

            cost = cost.add(purchasePrice.multiply(qty));
        }
        return cost;
    }
}
