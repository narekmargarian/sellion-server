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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + id));

        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        // 1. Возврат старых товаров на склад
        stockService.returnItemsToStock(order.getItems(), "Корректировка состава заказа #" + id, "ADMIN");

        // 2. Обновление полей
        order.setShopName((String) payload.get("shopName"));

        if (payload.containsKey("discountPercent")) {
            order.setDiscountPercent(new BigDecimal(payload.get("discountPercent").toString()));
        }

        String deliveryDateString = (String) payload.get("deliveryDate");
        if (deliveryDateString != null && !deliveryDateString.isEmpty()) {
            try {
                order.setDeliveryDate(LocalDate.parse(deliveryDateString));
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный формат даты");
            }
        }

        order.setNeedsSeparateInvoice(Boolean.TRUE.equals(payload.get("needsSeparateInvoice")));
        order.setPaymentMethod(PaymentMethod.fromString((String) payload.get("paymentMethod")));
        order.setCarNumber((String) payload.get("carNumber"));
        order.setComment((String) payload.get("comment"));

        // 3. Конвертация товаров
        Map<Long, Integer> newItems = new HashMap<>();
        Object itemsObj = payload.get("items");
        if (itemsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> rawItems = (Map<Object, Object>) itemsObj;
            rawItems.forEach((key, value) -> {
                try {
                    Long productId = Long.valueOf(key.toString());
                    Integer qty = (value instanceof Number) ? ((Number) value).intValue() : Integer.parseInt(value.toString());
                    if (qty > 0) newItems.put(productId, qty);
                } catch (Exception e) {
                    log.error("Ошибка парсинга товара: " + key);
                }
            });
        }

        // 4. РЕЗЕРВ НОВЫХ ТОВАРОВ С ЖЕСТКИМ ПРЕРЫВАНИЕМ ПРИ ОШИБКЕ
        try {
            stockService.reserveItemsFromStock(newItems, "Обновление состава заказа #" + id);
        } catch (RuntimeException e) {
            // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Бросаем ResponseStatusException вместо возврата ResponseEntity.
            // Это предотвращает UnexpectedRollbackException, так как Spring сразу прекращает транзакцию.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // 5. Расчет сумм
        Map<String, BigDecimal> totals = calculateTotalSaleAndCostWithDiscount(newItems, order.getDiscountPercent());

        order.setItems(newItems);
        order.setTotalAmount(totals.get("totalSale"));
        order.setTotalPurchaseCost(totals.get("totalCost"));

        orderRepository.save(order);

        recordAudit(id, "ORDER", "РЕДАКТИРОВАНИЕ ЗАКАЗА",
                "Заказ изменен. Скидка: " + order.getDiscountPercent() + "%. Итог: " + order.getTotalAmount() + " ֏");

        return ResponseEntity.ok(Map.of(
                "finalSum", order.getTotalAmount(),
                "message", "Заказ успешно обновлен"
        ));
    }


    // В AdminManagementController.java
    @PostMapping("/orders/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        // 1. Поиск заказа с проверкой на существование
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        // 2. Блокировка отмены, если уже выставлен счет (Инвойс)
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя отменить заказ с выставленным счетом!"));
        }

        // Защита от повторной отмены (если статус уже CANCELLED)
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ уже был отменен ранее."));
        }

        // 3. СКЛАД: Возвращаем товары в свободный остаток
        // Метод увеличит p.stockQuantity на указанное в заказе количество
        stockService.returnItemsToStock(order.getItems(), "Отмена заказа #" + id, "ADMIN");

        // 4. ФИНАНСЫ: Обнуляем суммы, чтобы заказ не портил статистику продаж
        order.setTotalAmount(BigDecimal.ZERO);
        order.setTotalPurchaseCost(BigDecimal.ZERO);
        order.setPurchaseCost(BigDecimal.ZERO);

        // 5. Статус и сохранение
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // 6. Аудит
        recordAudit(id, "ORDER", "ОТМЕНА", "Заказ отменен. Товары вернулись на склад. Суммы обнулены.");

        return ResponseEntity.ok(Map.of(
                "message", "Заказ успешно отменен, товар вернулся на склад",
                "id", id
        ));
    }


    @PutMapping("/products/{id}/edit")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> editProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return productRepository.findById(id).map(p -> {
            String oldInfo = "Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice();
            p.setName((String) payload.get("name"));

            // ЦЕНА ПРОДАЖИ
            Object priceVal = payload.get("price");
            p.setPrice(priceVal != null ? new BigDecimal(priceVal.toString()) : BigDecimal.ZERO);

            // СЕБЕСТОИМОСТЬ (Важно для отчетов прибыли)
            if (payload.containsKey("purchasePrice")) {
                Object purVal = payload.get("purchasePrice");
                p.setPurchasePrice(purVal != null ? new BigDecimal(purVal.toString()) : BigDecimal.ZERO);
            }

            p.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
            p.setBarcode((String) payload.get("barcode"));
            p.setItemsPerBox(((Number) payload.get("itemsPerBox")).intValue());
            p.setCategory((String) payload.get("category"));
            p.setHsnCode((String) payload.get("hsnCode"));
            p.setUnit((String) payload.get("unit"));

            productRepository.save(p);

            recordAudit(id, "PRODUCT", "ИЗМЕНЕНИЕ ТОВАРА",
                    "Было [" + oldInfo + "]. Стало [Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice() + "]");

            return ResponseEntity.ok(Map.of("message", "Данные товара обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/returns/{id}/edit")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> editReturn(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден"));

        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя менять подтвержденный возврат"));
        }

        // Сохранение доп. полей
        if (payload.containsKey("carNumber")) {
            ret.setCarNumber((String) payload.get("carNumber"));
        }
        if (payload.containsKey("comment")) {
            ret.setComment((String) payload.get("comment"));
        }

        ret.setShopName((String) payload.get("shopName"));

        if (payload.get("returnDate") != null) {
            ret.setReturnDate(LocalDate.parse((String) payload.get("returnDate")));
        }

        if (payload.get("returnReason") != null) {
            ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));
        }

        // Конвертация товаров
        Map<Long, Integer> newItems = new HashMap<>();
        Object itemsObj = payload.get("items");

        if (itemsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> rawItems = (Map<Object, Object>) itemsObj;
            rawItems.forEach((key, value) -> {
                try {
                    Long productId = Long.valueOf(key.toString());
                    Integer qty = (value instanceof Number) ? ((Number) value).intValue() : Integer.parseInt(value.toString());
                    if (qty > 0) newItems.put(productId, qty);
                } catch (Exception e) {
                    log.error("Error parsing return item: " + key);
                }
            });
        }

        // РАСЧЕТ СУММЫ ВОЗВРАТА (Для возвратов скидка всегда 0%)
        // Используем модифицированный метод с передачей BigDecimal.ZERO
        Map<String, BigDecimal> totals = calculateTotalSaleAndCostWithDiscount(newItems, BigDecimal.ZERO);

        ret.setItems(newItems);
        // Округляем до целых для драммов
        ret.setTotalAmount(totals.get("totalSale").setScale(0, RoundingMode.HALF_UP));

        returnOrderRepository.save(ret);

        recordAudit(id, "RETURN", "ИЗМЕНЕНИЕ ВОЗВРАТА",
                "Обновлен состав, Авто: " + ret.getCarNumber() + ". Сумма: " + ret.getTotalAmount() + " ֏");

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


    @PutMapping("clients/{id}/edit")
    @Transactional
    public ResponseEntity<?> editClient(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        // 1. Поиск клиента в базе
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Клиент не найден: " + id));

        // 2. Обновление основных полей
        client.setName((String) payload.get("name"));
        client.setCategory((String) payload.get("category"));
        client.setOwnerName((String) payload.get("ownerName"));
        client.setInn((String) payload.get("inn"));
        client.setPhone((String) payload.get("phone"));
        client.setAddress((String) payload.get("address"));
        client.setBankName((String) payload.get("bankName"));
        client.setBankAccount((String) payload.get("bankAccount"));
        client.setManagerId((String) payload.get("managerId"));
        client.setRouteDay((String) payload.get("routeDay"));

        // 3. Обновление долга (защита от ошибок приведения типов)
        if (payload.get("debt") != null) {
            client.setDebt(new BigDecimal(payload.get("debt").toString()));
        }

        // --- НОВОЕ: СОХРАНЕНИЕ ПРОЦЕНТА МАГАЗИНА ---
        if (payload.containsKey("defaultPercent")) {
            Object percentVal = payload.get("defaultPercent");
            BigDecimal percent = (percentVal != null) ? new BigDecimal(percentVal.toString()) : BigDecimal.ZERO;
            client.setDefaultPercent(percent);
        }
        // ------------------------------------------

        // 4. Сохранение
        clientRepository.save(client);

        // 5. Логирование (Аудит)
        log.info("Данные клиента {} обновлены. Процент: {}%", client.getName(), client.getDefaultPercent());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Данные клиента обновлены"
        ));
    }


    @PostMapping("/orders/create-manual")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> createOrderManual(@RequestBody Order order) {
        try {
            // 1. Базовые параметры
            order.setStatus(OrderStatus.RESERVED);
            order.setCreatedAt(LocalDateTime.now());

            // 2. Валидация
            if (order.getItems() == null || order.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Заказ не может быть пустым"));
            }

            // --- ЛОГИКА СКИДКИ ---
            if (order.getDiscountPercent() == null || order.getDiscountPercent().compareTo(BigDecimal.ZERO) == 0) {
                clientRepository.findByName(order.getShopName()).ifPresent(client -> {
                    order.setDiscountPercent(client.getDefaultPercent());
                });
            }

            BigDecimal percent = Optional.ofNullable(order.getDiscountPercent()).orElse(BigDecimal.ZERO);
            BigDecimal modifier = BigDecimal.ONE.subtract(
                    percent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
            );

            // 3. Расчет сумм с учетом СКИДКИ
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            List<Product> products = productRepository.findAllByIdWithLock(order.getItems().keySet());
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
                Product p = productMap.get(entry.getKey());
                if (p == null) throw new RuntimeException("Товар ID " + entry.getKey() + " не найден");

                BigDecimal qty = BigDecimal.valueOf(entry.getValue());
                BigDecimal basePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);
                BigDecimal discountedPrice = basePrice.multiply(modifier).setScale(0, RoundingMode.HALF_UP);

                totalAmount = totalAmount.add(discountedPrice.multiply(qty));
                totalCost = totalCost.add(Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO).multiply(qty));
            }

            order.setTotalAmount(totalAmount);
            order.setTotalPurchaseCost(totalCost);

            // --- КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: ТОЧЕЧНЫЙ ПЕРЕХВАТ ОШИБКИ СКЛАДА ---
            try {
                // 4. Резервирование
                stockService.reserveItemsFromStock(order.getItems(), "Ручной заказ: " + order.getShopName());
            } catch (RuntimeException stockEx) {
                // Если тут вылетит "Недостаточно товара", мы вернем 400 ошибку и транзакция не "умрет" с UnexpectedRollback
                return ResponseEntity.badRequest().body(Map.of("error", stockEx.getMessage()));
            }

            // 5. Сохранение
            Order saved = orderRepository.save(order);

            // 6. Аудит
            recordAudit(saved.getId(), "ORDER", "СОЗДАНИЕ ЗАКАЗА",
                    "Создан заказ со скидкой " + percent + "%. Итого: " + saved.getTotalAmount() + " ֏");

            return ResponseEntity.ok(Map.of(
                    "message", "Заказ успешно создан",
                    "id", saved.getId(),
                    "total", saved.getTotalAmount()
            ));

        } catch (Exception e) {
            log.error("Ошибка при ручном создании заказа: {}", e.getMessage());
            // Если ошибка не связана со складом, возвращаем общую ошибку
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Критическая ошибка: " + e.getMessage()));
        }
    }


    @PostMapping("/returns/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> confirmReturn(@PathVariable Long id) {
        // 1. Поиск возврата
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));

        // Защита от повторного подтверждения
        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Этот возврат уже был подтвержден ранее."));
        }


        // 2. ФИНАНСЫ (Выполняется ВСЕГДА)
        // Регистрируем операцию в финансовом модуле. Долг клиента уменьшается ровно на ret.getTotalAmount()
        financeService.registerOperation(
                null,
                "RETURN",
                ret.getTotalAmount(),
                id,
                "Подтвержденный возврат #" + id + " (" + ret.getReturnReason().getDisplayName() + ")",
                ret.getShopName()
        );

        // 3. СКЛАД (Выполняется ТОЛЬКО для определенных причин)
        // Если товар пригоден (склад, ошибка заказа/возврата) — возвращаем в остатки
        ReasonsReturn reason = ret.getReturnReason();
        boolean isStockUpdated = false;

        if (reason == ReasonsReturn.WAREHOUSE ||
                reason == ReasonsReturn.CORRECTION_ORDER ||
                reason == ReasonsReturn.CORRECTION_RETURN) {

            // Увеличиваем физическое количество товара на складе
            stockService.returnItemsToStock(ret.getItems(), "Пополнение склада: возврат #" + id, "ADMIN");
            isStockUpdated = true;
        }

        // 4. Обновляем статус и сохраняем
        ret.setStatus(ReturnStatus.CONFIRMED);
        returnOrderRepository.save(ret);

        // 5. Аудит (Логируем финальную сумму без скидок)
        recordAudit(id, "RETURN", "ПОДТВЕРЖДЕНИЕ",
                String.format("Возврат подтвержден. Сумма: %s ֏. Склад обновлен: %s",
                        ret.getTotalAmount(), isStockUpdated));

        return ResponseEntity.ok(Map.of(
                "message", "Возврат успешно подтвержден. Долг клиента уменьшен на " + ret.getTotalAmount() + " ֏",
                "stockUpdated", isStockUpdated,
                "finalAmount", ret.getTotalAmount()
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

    // Обновленный вспомогательный метод с учетом скидки
    private Map<String, BigDecimal> calculateTotalSaleAndCostWithDiscount(Map<Long, Integer> items, BigDecimal discountPercent) {
        if (items == null || items.isEmpty()) {
            return Map.of("totalSale", BigDecimal.ZERO, "totalCost", BigDecimal.ZERO);
        }

        List<Product> products = productRepository.findAllById(items.keySet());
        BigDecimal modifier = BigDecimal.ONE.subtract(
                Optional.ofNullable(discountPercent).orElse(BigDecimal.ZERO)
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
        );

        BigDecimal totalSale = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Product p : products) {
            Integer qtyInt = items.get(p.getId());
            if (qtyInt == null) continue;
            BigDecimal qty = BigDecimal.valueOf(qtyInt);

            // Цена продажи со скидкой, округленная до целых
            BigDecimal discountedPrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO)
                    .multiply(modifier).setScale(0, RoundingMode.HALF_UP);

            totalSale = totalSale.add(discountedPrice.multiply(qty));
            totalCost = totalCost.add(Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO).multiply(qty));
        }

        return Map.of(
                "totalSale", totalSale.setScale(0, RoundingMode.HALF_UP),
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

    @PostMapping("/orders/write-off")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> createWriteOff(@RequestBody Order order) {
        // 1. Предварительная настройка
        order.setType(OrderType.WRITE_OFF);
        order.setStatus(OrderStatus.PROCESSED);
        order.setManagerId("Офис");
        order.setCreatedAt(LocalDateTime.now());
        order.setDiscountPercent(BigDecimal.ZERO);

        if (order.getNeedsSeparateInvoice() == null) {
            order.setNeedsSeparateInvoice(false);
        }

        // 2. Списание со склада с ручной обработкой ошибки нехватки
        try {
            stockService.deductItemsFromStock(order.getItems(),
                    "Списание: " + (order.getComment() != null ? order.getComment() : "Без описания"), "ADMIN");
        } catch (RuntimeException e) {
            // Если товара не хватило, возвращаем 400 и текст ошибки.
            // Транзакция откатится автоматически из-за выброса исключения в StockService.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Списание отклонено: " + e.getMessage()));
        }

        // 3. Расчет себестоимости через оптимизированный метод
        Map<String, BigDecimal> totals = calculateTotalSaleAndCostWithDiscount(order.getItems(), BigDecimal.ZERO);

        // 4. Установка финансовых показателей
        order.setTotalAmount(BigDecimal.ZERO);
        order.setTotalPurchaseCost(totals.get("totalCost"));
        order.setPurchaseCost(totals.get("totalCost"));

        // 5. Сохранение и лог
        Order saved = orderRepository.save(order);
        recordAudit(saved.getId(), "ORDER", "СПИСАНИЕ",
                "Проведено списание. Себестоимость: " + order.getTotalPurchaseCost() + " ֏");

        return ResponseEntity.ok(Map.of("message", "Списание успешно проведено"));
    }


}