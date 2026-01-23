package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.ApiResponse;
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
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SyncController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProductRepository productRepository;
    private final OrderSyncService orderSyncService;

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);
    private static final DateTimeFormatter ANDROID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostMapping("/orders/sync")
    // УДАЛЕНО: @Transactional убран отсюда, так как каждый заказ должен быть отдельной транзакцией
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncOrders(@RequestBody List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("Список пуст", Map.of("status", "empty")));
        }

        int savedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;
        List<String> errorMessages = new ArrayList<>();

        // ОПТИМИЗАЦИЯ: Предварительная загрузка цен (оставляем, это отлично для скорости)
        Set<Long> allProductIds = orders.stream()
                .filter(o -> o.getItems() != null)
                .flatMap(o -> o.getItems().keySet().stream())
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> purchasePrices = productRepository.findAllById(allProductIds).stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        p -> p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO
                ));

        for (Order order : orders) {
            try {
                // 1. Валидация входных данных (Базовая)
                if (order.getShopName() == null || order.getItems() == null || order.getItems().isEmpty()) {
                    errorCount++;
                    errorMessages.add("Заказ без данных магазина или товаров (AndroidID: " + order.getAndroidId() + ")");
                    continue;
                }

                // 2. Проверка на дубликат (Безопасно выносим за транзакцию записи)
                if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
                    duplicateCount++;
                    continue;
                }

                // 3. Подготовка данных (Даты и Себестоимость)
                order.setCreatedAt(parseAndroidDate(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null));

                BigDecimal totalOrderPurchaseCost = BigDecimal.ZERO;
                for (Map.Entry<Long, Integer> item : order.getItems().entrySet()) {
                    BigDecimal price = purchasePrices.getOrDefault(item.getKey(), BigDecimal.ZERO);
                    totalOrderPurchaseCost = totalOrderPurchaseCost.add(price.multiply(BigDecimal.valueOf(item.getValue())));
                }
                // ИСПРАВЛЕНО: Убедитесь, что в Order.java тип BigDecimal
                order.setPurchaseCost(totalOrderPurchaseCost.setScale(2, RoundingMode.HALF_UP));

                // 4. ГЛАВНОЕ: Вызов сервиса.
                // Метод processOrderFromAndroid внутри помечен как @Transactional,
                // поэтому каждый заказ зафиксируется или откатится ИНДИВИДУАЛЬНО.
                orderSyncService.processOrderFromAndroid(order);
                savedCount++;

            } catch (Exception e) {
                errorCount++;
                String androidId = (order.getAndroidId() != null) ? order.getAndroidId() : "unknown";
                log.error("Ошибка синхронизации заказа [{}]: {}", androidId, e.getMessage());
                errorMessages.add("Заказ " + androidId + ": " + e.getMessage());
            }
        }

        // 5. Уведомление через WebSocket (Используем ApiResponse стиль)
        if (savedCount > 0) {
            Map<String, Object> wsPayload = Map.of(
                    "message", "Новые заказы: " + savedCount,
                    "count", savedCount,
                    "timestamp", LocalDateTime.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/new-order", (Object) wsPayload);
        }

        // 6. Итоговый ответ в едином стандарте ApiResponse
        Map<String, Object> resultData = Map.of(
                "saved", savedCount,
                "duplicates", duplicateCount,
                "errors", errorCount,
                "errorDetails", errorMessages
        );

        String finalStatus = errorCount == 0 ? "success" : (savedCount > 0 ? "partial_success" : "failed");

        return ResponseEntity.ok(ApiResponse.ok("Обработка завершена. Статус: " + finalStatus, resultData));
    }


    @PostMapping("/returns/sync")
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns == null || returns.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        int saved = 0;
        for (ReturnOrder ret : returns) {
            try {
                // Проверка на дубликат возврата
                if (ret.getAndroidId() != null && returnOrderRepository.existsByAndroidId(ret.getAndroidId())) {
                    continue;
                }

                ret.setId(null);
                ret.setStatus(ReturnStatus.DRAFT); // Ставим SENT, так как пришло с телефона

                if (ret.getCreatedAt() == null) {
                    ret.setCreatedAt(LocalDateTime.now());
                }

                // Расчет суммы возврата по прайс-листу (безопасность)
                BigDecimal total = BigDecimal.ZERO;
                if (ret.getItems() != null) {
                    for (Map.Entry<Long, Integer> entry : ret.getItems().entrySet()) {
                        BigDecimal price = productRepository.findById(entry.getKey())
                                .map(Product::getPrice).orElse(BigDecimal.ZERO);
                        total = total.add(price.multiply(BigDecimal.valueOf(entry.getValue())));
                    }
                }
                ret.setTotalAmount(total.setScale(0, RoundingMode.HALF_UP));

                returnOrderRepository.save(ret);
                saved++;
            } catch (Exception e) {
                log.error("Ошибка синхронизации возврата: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("status", "success", "count", saved));
    }

    @GetMapping("/orders/manager/{managerId}/current-month")
    public ResponseEntity<List<Order>> getOrdersByManagerCurrentMonth(@PathVariable String managerId) {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return ResponseEntity.ok(orderRepository.findByManagerIdAndCreatedAtBetween(managerId, start, end));
    }

    @GetMapping("/returns/manager/{managerId}/current-month")
    public ResponseEntity<List<ReturnOrder>> getReturnsByManagerCurrentMonth(@PathVariable String managerId) {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return ResponseEntity.ok(returnOrderRepository.findByManagerIdAndCreatedAtBetween(managerId, start, end));
    }

    // ИСПРАВЛЕНО: Возвращаем BigDecimal вместо double
    private BigDecimal calculatePurchaseCost(Map<Long, Integer> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;

        List<Product> products = productRepository.findAllById(items.keySet());
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Product p : products) {
            Integer qty = items.get(p.getId());
            if (qty != null) {
                BigDecimal pPrice = Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO);
                totalCost = totalCost.add(pPrice.multiply(BigDecimal.valueOf(qty)));
            }
        }
        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }


    private LocalDateTime parseAndroidDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            if (dateStr.contains("T")) return LocalDateTime.parse(dateStr);
            return LocalDateTime.parse(dateStr, ANDROID_DATE_FORMAT);
        } catch (Exception e) {
            log.warn("Ошибка даты {}, ставим сейчас", dateStr);
            return LocalDateTime.now();
        }
    }
}
