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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    // Добавленный форматтер для дат из Android-приложения
    private static final DateTimeFormatter ANDROID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostMapping("/orders/sync")
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders == null || orders.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        int savedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;
        LocalDate today = LocalDate.now();

        for (Order order : orders) {
            try {
                if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
                    duplicateCount++;
                    continue;
                }

                if (order.getDeliveryDate() != null && order.getDeliveryDate().isBefore(today)) {
                    log.warn("Заказ {} отклонен: дата доставки в прошлом", order.getAndroidId());
                    errorCount++;
                    continue;
                }

                processSingleOrderSync(order);
                savedCount++;

            } catch (Exception e) {
                log.error("Ошибка при синхронизации заказа {}: {}", order.getAndroidId(), e.getMessage());
                errorCount++;
            }
        }

        if (savedCount > 0) {
            messagingTemplate.convertAndSend("/topic/new-order", "Получено новых заказов: " + savedCount);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "saved", savedCount,
                "duplicates", duplicateCount,
                "errors", errorCount
        ));
    }

    @Transactional
    protected void processSingleOrderSync(Order order) {
        try {
            order.setId(null);
            order.setStatus(OrderStatus.RESERVED);

            // Логика обработки даты
            String rawDate = order.getCreatedAt();
            if (rawDate == null || rawDate.isEmpty()) {
                order.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else if (!rawDate.contains("T")) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(rawDate, ANDROID_DATE_FORMAT);
                    order.setCreatedAt(ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } catch (DateTimeParseException e) {
                    log.error("Ошибка парсинга даты Android: {}", rawDate);
                    order.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            }

            // ИСПРАВЛЕНО: Теперь передаем Map<Long, Integer>
            BigDecimal purchaseCost = calculatePurchaseCost(order.getItems());
            order.setTotalPurchaseCost(purchaseCost);

            stockService.reserveItemsFromStock(order.getItems(), "Заказ Android: " + order.getShopName());
            orderRepository.saveAndFlush(order);

        } catch (DataIntegrityViolationException e) {
            log.warn("Дубликат заказа проигнорирован: {}", order.getAndroidId());
        }
    }

    @PostMapping("/returns/sync")
    @Transactional
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns == null || returns.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        for (ReturnOrder ret : returns) {
            ret.setId(null);
            ret.setStatus(ReturnStatus.DRAFT);

            BigDecimal total = BigDecimal.ZERO;
            if (ret.getItems() != null) {
                // ИСПРАВЛЕНО: В ReturnOrder.java ключ тоже должен быть Long
                for (Map.Entry<Long, Integer> entry : ret.getItems().entrySet()) {
                    BigDecimal price = productRepository.findById(entry.getKey())
                            .map(Product::getPrice).orElse(BigDecimal.ZERO);
                    total = total.add(price.multiply(BigDecimal.valueOf(entry.getValue())));
                }
            }
            ret.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

            if (ret.getCreatedAt() == null || ret.getCreatedAt().isEmpty()) {
                ret.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }

        returnOrderRepository.saveAll(returns);
        return ResponseEntity.ok(Map.of("status", "success", "count", returns.size()));
    }

    @GetMapping("/orders/manager/{managerId}/current-month")
    public ResponseEntity<List<Order>> getOrdersByManagerCurrentMonth(@PathVariable String managerId) {
        String start = LocalDate.now().withDayOfMonth(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay().minusSeconds(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ResponseEntity.ok(orderRepository.findOrdersByManagerAndDateRange(managerId, start, end));
    }

    private BigDecimal calculatePurchaseCost(Map<Long, Integer> items) {
        BigDecimal cost = BigDecimal.ZERO;
        if (items == null) return cost;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            // Ищем строго по ID (ключам типа Long)
            Product p = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар с ID " + entry.getKey() + " не найден"));

            BigDecimal purchasePrice = (p.getPurchasePrice() != null) ? p.getPurchasePrice() : BigDecimal.ZERO;
            cost = cost.add(purchasePrice.multiply(BigDecimal.valueOf(entry.getValue())));
        }
        return cost.setScale(2, RoundingMode.HALF_UP);
    }



}