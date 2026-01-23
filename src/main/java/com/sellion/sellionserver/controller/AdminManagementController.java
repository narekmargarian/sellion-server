package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import com.sellion.sellionserver.services.FinanceService;
import com.sellion.sellionserver.services.StockService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminManagementController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final AuditLogRepository auditLogRepository;
    private final ClientRepository clientRepository;
    private final FinanceService financeService;
    private final ReturnOrderRepository returnOrderRepository;

    private static final Logger log = LoggerFactory.getLogger(AdminManagementController.class);

    @PutMapping("/orders/{id}/full-edit")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> fullEditOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        // 1. Возврат старых товаров
        stockService.returnItemsToStock(order.getItems(), "Корректировка состава заказа #" + id, "ADMIN");

        // 2. Обновление основных полей
        order.setShopName((String) payload.get("shopName"));
        String deliveryDateString = (String) payload.get("deliveryDate");
        if (deliveryDateString != null && !deliveryDateString.isEmpty()) {
            try {
                order.setDeliveryDate(LocalDate.parse(deliveryDateString));
            } catch (DateTimeParseException e) {
                throw new RuntimeException("Неверный формат даты: " + deliveryDateString);
            }
        }

        order.setNeedsSeparateInvoice(Boolean.TRUE.equals(payload.get("needsSeparateInvoice")));
        order.setPaymentMethod(PaymentMethod.fromString((String) payload.get("paymentMethod")));
        order.setCarNumber((String) payload.get("carNumber"));

        // 3. Конвертация полученных данных в Map<Long, Integer>
        Map<Long, Integer> newItems = new HashMap<>();
        Object itemsObj = payload.get("items");
        if (itemsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> rawItems = (Map<Object, Object>) itemsObj;
            rawItems.forEach((key, value) -> {
                // ИСПРАВЛЕНО: Безопасное преобразование ключа (ID) и значения (Кол-во)
                try {
                    Long productId = Long.valueOf(key.toString());
                    Integer qty = (value instanceof Number) ? ((Number) value).intValue() : Integer.parseInt(value.toString());
                    if (qty > 0) {
                        newItems.put(productId, qty);
                    }
                } catch (Exception e) {
                    log.error("Ошибка парсинга товара в заказе {}: key={}, value={}", id, key, value);
                }
            });
        }

        // 4. Резерв новых товаров
        stockService.reserveItemsFromStock(newItems, "Обновление состава заказа #" + id);

        // 5. Расчет сумм
        Map<String, BigDecimal> totals = calculateTotalSaleAndCost(newItems);
        order.setItems(newItems);
        order.setTotalAmount(totals.get("totalSale"));
        order.setTotalPurchaseCost(totals.get("totalCost"));
        orderRepository.save(order);
        recordAudit(id, "ORDER", "РЕДАКТИРОВАНИЕ ЗАКАЗА", "Заказ изменен. Новая сумма: " + order.getTotalAmount() + " ֏");
        return ResponseEntity.ok(Map.of(
                "finalSum", order.getTotalAmount(),
                "message", "Заказ успешно обновлен"
        ));
    }

    @PostMapping("/orders/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));
        // Если счет уже выставлен, запрещаем удаление (защита бухгалтерии)
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя удалить заказ со счетом!"));
        }
        // 1. Возвращаем товар на склад (отменяем резерв)
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            stockService.returnItemsToStock(order.getItems(), "Отмена заказа #" + id, "ADMIN");
        }
        // 2. Удаляем запись
        orderRepository.delete(order);

        return ResponseEntity.ok(Map.of("message", "Заказ полностью удален, товар вернулся на склад"));
    }

    @PutMapping("/products/{id}/edit")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> editProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return productRepository.findById(id).map(p -> {
            String oldInfo = "Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice();
            p.setName((String) payload.get("name"));

            Object priceVal = payload.get("price");
            p.setPrice(priceVal != null ? new BigDecimal(priceVal.toString()) : BigDecimal.ZERO);
            p.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
            p.setBarcode((String) payload.get("barcode"));
            p.setItemsPerBox(((Number) payload.get("itemsPerBox")).intValue());
            p.setCategory((String) payload.get("category"));
            p.setHsnCode((String) payload.get("hsnCode"));
            p.setUnit((String) payload.get("unit"));
            productRepository.save(p);

            recordAudit(id, "PRODUCT", "ИЗМЕНЕНИЕ ТОВАРА", "Было [" + oldInfo + "]. Стало [Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice() + "]");
            return ResponseEntity.ok(Map.of("message", "Данные товара обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/returns/{id}/edit")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> editReturn(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();

        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя менять подтвержденный возврат"));
        }

        ret.setShopName((String) payload.get("shopName"));
        if (payload.get("returnDate") != null) {
            ret.setReturnDate(LocalDate.parse((String) payload.get("returnDate")));
        }
        ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));

        // 1. ИСПРАВЛЕНО: Конвертация Map<String, Object> -> Map<Long, Integer>
        Map<Long, Integer> newItems = new HashMap<>();
        Object itemsObj = payload.get("items");

        if (itemsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawItems = (Map<String, Object>) itemsObj;
            rawItems.forEach((key, value) -> {
                // Преобразуем строковый ключ ID из JSON в Long
                newItems.put(Long.valueOf(key), ((Number) value).intValue());
            });
        }

        Map<String, BigDecimal> totals = calculateTotalSaleAndCost(newItems);
        ret.setItems(newItems);
        ret.setTotalAmount(totals.get("totalSale"));

        returnOrderRepository.save(ret);

        recordAudit(id, "RETURN", "ИЗМЕНЕНИЕ ВОЗВРАТА", "Обновлен состав. Сумма: " + ret.getTotalAmount() + " ֏");

        return ResponseEntity.ok(Map.of(
                "newTotal", ret.getTotalAmount(),
                "message", "Возврат изменен"
        ));
    }


    @PostMapping("/returns/{id}/delete")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> deleteReturn(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));

        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя удалить уже подтвержденный возврат!"));
        }

        returnOrderRepository.deleteById(id);
        recordAudit(id, "RETURN", "ПОЛНОЕ УДАЛЕНИЕ", "Возврат #" + id + " полностью удален");
        return ResponseEntity.ok(Map.of("message", "Возврат полностью удален"));
    }


    @PutMapping("/clients/{id}/edit")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> editClient(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return clientRepository.findById(id).map(c -> {
            c.setName((String) payload.get("name"));
            c.setAddress((String) payload.get("address"));

            Object debtVal = payload.get("debt");
            c.setDebt(debtVal != null ? new BigDecimal(debtVal.toString()) : BigDecimal.ZERO);

            c.setOwnerName((String) payload.get("ownerName"));
            c.setInn((String) payload.get("inn"));
            c.setPhone((String) payload.get("phone"));
            c.setBankAccount((String) payload.get("bankAccount"));
            clientRepository.save(c);

            recordAudit(id, "CLIENT", "ИЗМЕНЕНИЕ КЛИЕНТА", "Обновлены реквизиты: " + c.getName());
            return ResponseEntity.ok(Map.of("message", "Данные клиента обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/create-manual")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> createOrderManual(@RequestBody Order order) {
        try {
            // 1. Установка базовых параметров
            order.setStatus(OrderStatus.RESERVED);

            // ИСПРАВЛЕНО: Устанавливаем объект LocalDateTime напрямую, без конвертации в String
            order.setCreatedAt(LocalDateTime.now());

            // 2. Валидация товаров
            if (order.getItems() == null || order.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Заказ не может быть пустым"));
            }

            // 3. Расчет сумм (используем существующий метод calculateTotalSaleAndCost)
            Map<String, BigDecimal> totals = calculateTotalSaleAndCost(order.getItems());
            order.setTotalAmount(totals.get("totalSale"));
            order.setTotalPurchaseCost(totals.get("totalCost"));

            // 4. Резервирование товара на складе
            // Если товара не хватит, stockService выбросит исключение и транзакция откатится
            stockService.reserveItemsFromStock(order.getItems(), "Ручной заказ через админ-панель");

            // 5. Сохранение
            Order saved = orderRepository.save(order);

            // 6. Аудит
            recordAudit(saved.getId(), "ORDER", "СОЗДАНИЕ ЗАКАЗА",
                    "Ручной заказ создан. Сумма: " + saved.getTotalAmount() + " ֏");

            return ResponseEntity.ok(Map.of(
                    "message", "Заказ успешно создан",
                    "id", saved.getId(),
                    "total", saved.getTotalAmount()
            ));

        } catch (Exception e) {
            log.error("Ошибка при ручном создании заказа: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Не удалось создать заказ: " + e.getMessage()));
        }
    }

    @PostMapping("/returns/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> confirmReturn(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));

        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Этот возврат уже был подтвержден ранее."));
        }

        // 2. ФИНАНСЫ (Выполняется ВСЕГДА)
        // Раз фактура (инвойс) уже есть, мы обязаны уменьшить долг клиента в любом случае
        financeService.registerOperation(
                null,
                "RETURN",
                ret.getTotalAmount(),
                id,
                "Возврат (" + ret.getReturnReason().getDisplayName() + ")",
                ret.getShopName()
        );

        // 3. СКЛАД (Выполняется ТОЛЬКО для определенных причин)
        // Если товар пригоден для перепродажи или это ошибка склада/заказа — возвращаем в остатки
        ReasonsReturn reason = ret.getReturnReason();
        if (reason == ReasonsReturn.WAREHOUSE ||
                reason == ReasonsReturn.CORRECTION_ORDER ||
                reason == ReasonsReturn.CORRECTION_RETURN) {

            // Метод addStockById внутри returnItemsToStock прибавит количество к товарам
            stockService.returnItemsToStock(ret.getItems(), "Корректировка/Возврат на склад #" + id, "ADMIN");
        }

        // 4. Обновляем статус
        ret.setStatus(ReturnStatus.CONFIRMED);
        returnOrderRepository.save(ret);
        // Логируем в аудит
        recordAudit(id, "RETURN", "ПОДТВЕРЖДЕНИЕ", "Возврат подтвержден. Долг уменьшен на " + ret.getTotalAmount());

        return ResponseEntity.ok(Map.of(
                "message", "Возврат успешно подтвержден. Долг клиента уменьшен.",
                "stockUpdated", (reason == ReasonsReturn.WAREHOUSE || reason == ReasonsReturn.CORRECTION_ORDER || reason == ReasonsReturn.CORRECTION_RETURN)
        ));
    }

    @PostMapping("/products/{id}/inventory")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> updateStockManual(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Product p = productRepository.findById(id).orElseThrow();
        int newQty = ((Number) payload.get("newQty")).intValue();
        int diff = newQty - p.getStockQuantity();
        p.setStockQuantity(newQty);
        productRepository.save(p);

        stockService.logMovement(p.getName(), diff, "ADJUSTMENT", "Инвентаризация: " + payload.get("reason"), "ADMIN");
        recordAudit(id, "PRODUCT", "ИНВЕНТАРИЗАЦИЯ", "Остаток изменен на " + newQty);
        return ResponseEntity.ok(Map.of("message", "Склад обновлен"));
    }

    private Map<String, BigDecimal> calculateTotalSaleAndCost(Map<Long, Integer> items) {
        if (items == null || items.isEmpty()) {
            return Map.of("totalSale", BigDecimal.ZERO, "totalCost", BigDecimal.ZERO);
        }

        // ИСПРАВЛЕНО (Оптимизация): Один запрос к БД вместо цикла
        List<Product> products = productRepository.findAllById(items.keySet());

        BigDecimal totalSale = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Product p : products) {
            Integer qtyInt = items.get(p.getId());
            if (qtyInt == null) continue;

            BigDecimal qty = BigDecimal.valueOf(qtyInt);

            BigDecimal price = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);
            BigDecimal pPrice = Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO);

            totalSale = totalSale.add(price.multiply(qty));
            totalCost = totalCost.add(pPrice.multiply(qty));
        }

        return Map.of(
                "totalSale", totalSale.setScale(2, RoundingMode.HALF_UP),
                "totalCost", totalCost.setScale(2, RoundingMode.HALF_UP)
        );
    }



    private void recordAudit(Long entityId, String type, String action, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername("ADMIN");
        auditLog.setEntityId(entityId);
        auditLog.setEntityType(type);
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLog.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }
}