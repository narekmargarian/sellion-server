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
import java.util.ArrayList;
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
    private final ProductRepository productRepository;
    private final OrderSyncService orderSyncService;

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);
    private static final DateTimeFormatter ANDROID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostMapping("/orders/sync")
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders == null || orders.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        int savedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;
        List<String> errorMessages = new ArrayList<>();

        for (Order order : orders) {
            try {
                // 1. Проверка на дубликат по Android ID
                if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
                    duplicateCount++;
                    continue;
                }

                // 2. Обработка даты (Конвертация строки в LocalDateTime)
                order.setCreatedAt(parseAndroidDate(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null));

                // 3. Обработка заказа через сервис (Резерв товара + сохранение)
                orderSyncService.processOrderFromAndroid(order);
                savedCount++;

            } catch (Exception e) {
                errorCount++;
                String msg = "Заказ " + order.getAndroidId() + ": " + e.getMessage();
                log.error(msg);
                errorMessages.add(msg);
            }
        }

        // Уведомление через WebSocket, если есть новые заказы
        if (savedCount > 0) {
            messagingTemplate.convertAndSend("/topic/new-order", "Синхронизировано заказов: " + savedCount);
        }

        return ResponseEntity.ok(Map.of(
                "status", errorCount == 0 ? "success" : "partial_success",
                "saved", savedCount,
                "duplicates", duplicateCount,
                "errors", errorCount,
                "details", errorMessages
        ));
    }

    @PostMapping("/returns/sync")
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns == null || returns.isEmpty()) return ResponseEntity.ok(Map.of("status", "empty"));

        int saved = 0;
        for (ReturnOrder ret : returns) {
            try {
                ret.setId(null);
                ret.setStatus(ReturnStatus.DRAFT);

                // ИСПРАВЛЕНО: Парсинг даты для возврата
                if (ret.getCreatedAt() == null) {
                    ret.setCreatedAt(LocalDateTime.now());
                }

                // Расчет суммы возврата на стороне сервера (безопасность)
                BigDecimal total = BigDecimal.ZERO;
                if (ret.getItems() != null) {
                    for (Map.Entry<Long, Integer> entry : ret.getItems().entrySet()) {
                        BigDecimal price = productRepository.findById(entry.getKey())
                                .map(Product::getPrice).orElse(BigDecimal.ZERO);
                        total = total.add(price.multiply(BigDecimal.valueOf(entry.getValue())));
                    }
                }
                ret.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

                returnOrderRepository.save(ret);
                saved++;
            } catch (Exception e) {
                log.error("Ошибка синхронизации возврата: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("status", "success", "count", saved));
    }

    // Вспомогательный метод парсинга даты (2026 стандарт)
    private LocalDateTime parseAndroidDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            // Если Android прислал ISO формат (с T)
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            // Если прислал пробельный формат (наш кастомный)
            return LocalDateTime.parse(dateStr, ANDROID_DATE_FORMAT);
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату {}, ставим текущую", dateStr);
            return LocalDateTime.now();
        }
    }
}