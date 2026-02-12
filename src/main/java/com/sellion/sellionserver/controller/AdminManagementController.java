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
        // 1. Поиск заказа
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + id));

        // Запрет редактирования, если уже есть счет
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        // 2. ВОЗВРАТ СТАРЫХ ТОВАРОВ НА СКЛАД
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            stockService.returnItemsToStock(order.getItems(), "Корректировка (начало) состава заказа #" + id, "ADMIN");
        }

        // 3. Обновление базовых полей заказа
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

        // 4. ПОЛУЧЕНИЕ АКЦИЙ
        Map<Long, BigDecimal> promoItemsMap = new HashMap<>();
        if (payload.get("appliedPromoItems") instanceof Map) {
            Map<?, ?> rawPromo = (Map<?, ?>) payload.get("appliedPromoItems");
            rawPromo.forEach((k, v) -> promoItemsMap.put(Long.valueOf(k.toString()), new BigDecimal(v.toString())));
        }

        // 5. КОНВЕРТАЦИЯ НОВОГО СОСТАВА
        Map<Long, Integer> newItemsMap = new HashMap<>();
        Object itemsObj = payload.get("items");
        if (itemsObj instanceof Map) {
            Map<?, ?> rawItems = (Map<?, ?>) itemsObj;
            rawItems.forEach((key, value) -> {
                try {
                    Long productId = Long.valueOf(key.toString());
                    Integer qty = (value instanceof Number) ? ((Number) value).intValue() : Integer.parseInt(value.toString());
                    if (qty > 0) newItemsMap.put(productId, qty);
                } catch (Exception e) {
                    log.error("Ошибка парсинга товара при редактировании: " + key);
                }
            });
        }

        if (newItemsMap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заказ не может быть пустым");
        }

        // 6. РЕЗЕРВ ТОВАРОВ
        try {
            stockService.reserveItemsFromStock(newItemsMap, "Корректировка (финал) состава заказа #" + id);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // 7. РАСЧЕТ СУММ С УЧЕТОМ ПРИОРИТЕТА АКЦИЙ
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal currentShopPercent = Optional.ofNullable(order.getDiscountPercent()).orElse(BigDecimal.ZERO);

        List<Product> products = productRepository.findAllByIdWithLock(newItemsMap.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : newItemsMap.entrySet()) {
            Long pId = entry.getKey();
            Product p = productMap.get(pId);
            if (p == null) continue;

            BigDecimal qty = BigDecimal.valueOf(entry.getValue());
            BigDecimal basePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);

            BigDecimal itemPercent = promoItemsMap.containsKey(pId) ? promoItemsMap.get(pId) : currentShopPercent;

            BigDecimal modifier = BigDecimal.ONE.subtract(
                    itemPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
            );

            // ИСПРАВЛЕНО: Установлен масштаб 1 вместо 0 для сохранения точности (напр. 1751.2)
            BigDecimal discountedPrice = basePrice.multiply(modifier).setScale(1, RoundingMode.HALF_UP);

            totalAmount = totalAmount.add(discountedPrice.multiply(qty));
            totalCost = totalCost.add(Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO).multiply(qty));
        }

        // 8. СОХРАНЕНИЕ
        order.setItems(newItemsMap);
        order.setAppliedPromoItems(promoItemsMap);

        // ИСПРАВЛЕНО: Устанавливаем итоговую сумму с точностью до 1 знака
        order.setTotalAmount(totalAmount.setScale(1, RoundingMode.HALF_UP));
        order.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        order.setPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));

        orderRepository.save(order);

        // 9. АУДИТ
        int promoCount = promoItemsMap.size();
        String promoNote = promoCount > 0 ? " (Акционных позиций: " + promoCount + ")" : "";
        recordAudit(id, "ORDER", "РЕДАКТИРОВАНИЕ ЗАКАЗА",
                String.format("Заказ изменен. Маг. скидка: %s%%%s. Новый итог: %s ֏",
                        currentShopPercent, promoNote, order.getTotalAmount()));

        return ResponseEntity.ok(Map.of(
                "finalSum", order.getTotalAmount(),
                "message", "Заказ #" + id + " успешно обновлен"
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

        // 1. Обновление базовых полей
        if (payload.containsKey("carNumber")) ret.setCarNumber((String) payload.get("carNumber"));
        if (payload.containsKey("comment")) ret.setComment((String) payload.get("comment"));
        if (payload.containsKey("shopName")) ret.setShopName((String) payload.get("shopName"));

        if (payload.get("returnDate") != null) {
            ret.setReturnDate(LocalDate.parse((String) payload.get("returnDate")));
        }
        if (payload.get("returnReason") != null) {
            ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));
        }

        // 2. Обработка товаров (items) и индивидуальных цен (itemPrices)
        Map<Long, Integer> newItems = new HashMap<>();
        Map<Long, BigDecimal> newItemPrices = new HashMap<>();
        BigDecimal totalSum = BigDecimal.ZERO;

        Object itemsObj = payload.get("items");
        Object pricesObj = payload.get("itemPrices"); // Получаем кастомные цены из JS

        if (itemsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawItems = (Map<String, Object>) itemsObj;
            @SuppressWarnings("unchecked")
            Map<String, Object> rawPrices = (pricesObj instanceof Map) ? (Map<String, Object>) pricesObj : new HashMap<>();

            for (Map.Entry<String, Object> entry : rawItems.entrySet()) {
                try {
                    Long productId = Long.valueOf(entry.getKey());
                    Integer qty = (entry.getValue() instanceof Number) ?
                            ((Number) entry.getValue()).intValue() : Integer.parseInt(entry.getValue().toString());

                    if (qty > 0) {
                        newItems.put(productId, qty);

                        // Берем кастомную цену из payload, если её нет - ищем в товарах (fallback)
                        BigDecimal price;
                        if (rawPrices.containsKey(entry.getKey())) {
                            price = new BigDecimal(rawPrices.get(entry.getKey()).toString());
                        } else {
                            // Если цена не пришла, пытаемся оставить ту, что была или взять текущую из БД
                            price = productRepository.findById(productId)
                                    .map(Product::getPrice)
                                    .orElse(BigDecimal.ZERO);
                        }

                        newItemPrices.put(productId, price);

                        // Расчет суммы: Цена из документа * Количество
                        BigDecimal rowSum = price.multiply(BigDecimal.valueOf(qty));
                        totalSum = totalSum.add(rowSum);
                    }
                } catch (Exception e) {
                    log.error("Ошибка парсинга товара/цены для ID: " + entry.getKey(), e);
                }
            }
        }

        // 3. Сохранение данных
        ret.setItems(newItems);
        ret.setItemPrices(newItemPrices); // Сохраняем кастомные цены в ReturnOrder

        // Округляем итог до целых (актуально для 2026 года)
        ret.setTotalAmount(totalSum.setScale(0, RoundingMode.HALF_UP));

        returnOrderRepository.save(ret);

        // 4. Аудит
        recordAudit(id, "RETURN", "ИЗМЕНЕНИЕ ВОЗВРАТА",
                String.format("Обновлен состав и цены. Авто: %s. Итого: %s ֏",
                        ret.getCarNumber(), ret.getTotalAmount()));

        return ResponseEntity.ok(Map.of(
                "newTotal", ret.getTotalAmount(),
                "message", "Возврат изменен (цены зафиксированы в документе)"
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

            // --- УЛУЧШЕННАЯ ЛОГИКА ОПРЕДЕЛЕНИЯ СКИДКИ ---
            if (order.getDiscountPercent() == null || order.getDiscountPercent().compareTo(BigDecimal.ZERO) == 0) {
                clientRepository.findByName(order.getShopName()).ifPresent(client -> {
                    order.setDiscountPercent(client.getDefaultPercent());
                });
            }

            BigDecimal shopDefaultPercent = Optional.ofNullable(order.getDiscountPercent()).orElse(BigDecimal.ZERO);

            // 3. Расчет сумм с учетом ПРИОРИТЕТА АКЦИЙ
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            int promoItemsCount = 0;

            List<Product> products = productRepository.findAllByIdWithLock(order.getItems().keySet());
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

            for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
                Long productId = entry.getKey();
                Product p = productMap.get(productId);
                if (p == null) throw new RuntimeException("Товар ID " + productId + " не найден");

                BigDecimal qty = BigDecimal.valueOf(entry.getValue());
                BigDecimal basePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);

                BigDecimal currentItemPercent;
                if (order.getAppliedPromoItems() != null && order.getAppliedPromoItems().containsKey(productId)) {
                    currentItemPercent = order.getAppliedPromoItems().get(productId);
                    promoItemsCount++;
                } else {
                    currentItemPercent = shopDefaultPercent;
                }

                BigDecimal itemModifier = BigDecimal.ONE.subtract(
                        currentItemPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                );

                // ИСПРАВЛЕНО: Установлен масштаб 1 вместо 0 для сохранения точности (напр. 1751.2)
                BigDecimal discountedPrice = basePrice.multiply(itemModifier).setScale(1, RoundingMode.HALF_UP);

                totalAmount = totalAmount.add(discountedPrice.multiply(qty));
                totalCost = totalCost.add(Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO).multiply(qty));
            }

            // ИСПРАВЛЕНО: Устанавливаем итоговую сумму с точностью до 1 знака
            order.setTotalAmount(totalAmount.setScale(1, RoundingMode.HALF_UP));
            order.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
            order.setPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));

            // 4. Резервирование товара
            try {
                stockService.reserveItemsFromStock(order.getItems(), "Ручной заказ: " + order.getShopName());
            } catch (RuntimeException stockEx) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, stockEx.getMessage());
            }

            // 5. Сохранение заказа
            Order saved = orderRepository.save(order);

            // 6. Аудит
            String auditDetails = String.format("Создан заказ. Скидка магазина: %s%%. Товаров по акции: %d. Итого: %s ֏",
                    shopDefaultPercent, promoItemsCount, saved.getTotalAmount());

            recordAudit(saved.getId(), "ORDER", "СОЗДАНИЕ ЗАКАЗА", auditDetails);

            return ResponseEntity.ok(Map.of(
                    "message", "Заказ успешно создан",
                    "id", saved.getId(),
                    "total", saved.getTotalAmount()
            ));

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            log.error("Критическая ошибка при создании заказа: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка сервера: " + e.getMessage()));
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

            // ИСПРАВЛЕНО: Установлен scale(1) вместо 0
            BigDecimal discountedPrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO)
                    .multiply(modifier).setScale(1, RoundingMode.HALF_UP);

            totalSale = totalSale.add(discountedPrice.multiply(qty));
            totalCost = totalCost.add(Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO).multiply(qty));
        }

        return Map.of(
                // ИСПРАВЛЕНО: Установлен scale(1) вместо 0
                "totalSale", totalSale.setScale(1, RoundingMode.HALF_UP),
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


    @PostMapping("/returns/create-manual")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> createReturnManual(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Получен запрос на создание возврата: {}", payload);

            ReturnOrder ret = new ReturnOrder();
            ret.setStatus(ReturnStatus.DRAFT);
            ret.setCreatedAt(LocalDateTime.now());

            // Магазин и Менеджер
            ret.setShopName((String) payload.getOrDefault("shopName", "Неизвестно"));
            ret.setManagerId((String) payload.getOrDefault("managerId", "OFFICE"));
            ret.setCarNumber((String) payload.get("carNumber"));
            ret.setComment((String) payload.get("comment"));

            // Дата и Причина
            if (payload.get("returnDate") != null) {
                ret.setReturnDate(LocalDate.parse((String) payload.get("returnDate")));
            }
            if (payload.get("returnReason") != null) {
                ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));
            }

            // КРИТИЧЕСКИЙ УЗЕЛ: Парсинг товаров (items)
            Map<Long, Integer> items = new HashMap<>();
            Map<Long, BigDecimal> itemPrices = new HashMap<>();
            BigDecimal totalAmount = BigDecimal.ZERO;

            // Пытаемся достать карту товаров
            Object rawItemsObj = payload.get("items");
            if (rawItemsObj instanceof Map) {
                Map<?, ?> rawItemsMap = (Map<?, ?>) rawItemsObj;
                Map<?, ?> rawPricesMap = (payload.get("itemPrices") instanceof Map) ? (Map<?, ?>) payload.get("itemPrices") : new HashMap<>();

                for (Map.Entry<?, ?> entry : rawItemsMap.entrySet()) {
                    String key = entry.getKey().toString();
                    Long pId = Long.valueOf(key);
                    Integer qty = Integer.valueOf(entry.getValue().toString());

                    if (qty <= 0) continue;

                    items.put(pId, qty);

                    // Определяем цену: из фронта или из базы
                    BigDecimal price;
                    if (rawPricesMap.containsKey(key)) {
                        price = new BigDecimal(rawPricesMap.get(key).toString());
                    } else {
                        price = productRepository.findById(pId).map(Product::getPrice).orElse(BigDecimal.ZERO);
                    }

                    itemPrices.put(pId, price);
                    totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(qty)));
                }
            }

            if (items.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Список товаров пуст!"));
            }

            ret.setItems(items);
            ret.setItemPrices(itemPrices);
            ret.setTotalAmount(totalAmount.setScale(1, RoundingMode.HALF_UP));

            ReturnOrder saved = returnOrderRepository.save(ret);

            return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "Возврат успешно создан"));

        } catch (Exception e) {
            log.error("Ошибка при ручном создании возврата: ", e);
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка сервера: " + e.getMessage()));
        }
    }



}