package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import com.sellion.sellionserver.services.FinanceService;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminManagementController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final StockService stockService;
    private final ClientRepository clientRepository;
    private final AuditLogRepository auditLogRepository;
    private final FinanceService financeService;

    // Форматтер для системных дат (создание записи)
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ФОРМАТТЕР ДЛЯ РУССКИХ ДАТ (например: "17 января 2026")
    private static final DateTimeFormatter RU_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));

    @PutMapping("/orders/{id}/full-edit")
    @Transactional
    public ResponseEntity<?> fullEditOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        // 1. Проверка на наличие счета
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        // 2. Возвращаем старые товары на склад С ЗАПИСЬЮ В ИСТОРИЮ
        stockService.returnItemsToStock(order.getItems(), "Корректировка состава заказа #" + id);

        // 3. Обновляем основные данные
        order.setShopName((String) payload.get("shopName"));

        String deliveryDateString = (String) payload.get("deliveryDate");
        if (deliveryDateString != null && !deliveryDateString.isEmpty()) {
            try {
                order.setDeliveryDate(LocalDate.parse(deliveryDateString, RU_DATE_FORMATTER));
//                order.setDeliveryDate(LocalDate.parse(deliveryDateString));

            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Неверный формат даты: " + deliveryDateString));
            }
        }

        order.setNeedsSeparateInvoice((Boolean) payload.get("needsSeparateInvoice"));
        order.setPaymentMethod(PaymentMethod.fromString((String) payload.get("paymentMethod")));

        // 4. Списываем новые товары С ЗАПИСЬЮ В ИСТОРИЮ
        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        try {
            // Вызываем новый метод с указанием причины
            stockService.reserveItemsFromStock(newItems, "Обновление состава заказа #" + id);
        } catch (RuntimeException e) {
            // Если новых товаров не хватило, возвращаем назад старый состав
            stockService.deductItemsFromStock(order.getItems(), "Откат к старому составу заказа #" + id);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        // 5. Пересчет суммы и себестоимости при редактировании заказа
        Map<String, Double> totals = calculateTotalSaleAndCost(newItems);
        double newTotal = totals.get("totalSale");
        double newPurchaseCost = totals.get("totalCost");

        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        order.setTotalPurchaseCost(newPurchaseCost); // Теперь и при правке пишется себестоимость
        orderRepository.save(order);


        // 6. Запись в общий аудит админки
        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("РЕДАКТИРОВАНИЕ ЗАКАЗА");
        log.setDetails("Заказ #" + id + " изменен. Новая сумма: " + newTotal);
        log.setEntityId(id);
        log.setEntityType("ORDER");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("finalSum", newTotal, "message", "Заказ успешно обновлен"));
    }

    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();

        // ИСПРАВЛЕНО: Убрано условие только для PROCESSED.
        // Возвращаем товар, если заказ в любом активном статусе (кроме уже отмененного)
        if (order.getStatus() != OrderStatus.CANCELLED) {
            stockService.returnItemsToStock(order.getItems(), "Отмена заказа #" + id);
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("ОТМЕНА ЗАКАЗА");
        log.setDetails("Заказ #" + id + " отменен, товары возвращены на склад");
        log.setEntityId(id);
        log.setEntityType("ORDER");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("message", "Заказ отменен, товар возвращен на склад"));
    }




    @PutMapping("/products/{id}/edit")
    @Transactional
    public ResponseEntity<?> editProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return productRepository.findById(id).map(p -> {
            String oldInfo = "Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice();

            p.setName((String) payload.get("name"));
            p.setPrice(((Number) payload.get("price")).doubleValue());
            p.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
            p.setBarcode((String) payload.get("barcode"));
            p.setItemsPerBox(((Number) payload.get("itemsPerBox")).intValue());
            p.setCategory((String) payload.get("category"));
            productRepository.save(p);

            AuditLog log = new AuditLog();
            log.setUsername("ADMIN");
            log.setAction("ИЗМЕНЕНИЕ ТОВАРА");
            log.setDetails("Было [" + oldInfo + "]. Стало [Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice() + "]");
            log.setEntityId(id);
            log.setEntityType("PRODUCT");
            auditLogRepository.save(log);

            return ResponseEntity.ok(Map.of("message", "Данные товара обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/returns/{id}/edit")
    @Transactional
    public ResponseEntity<?> editReturn(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();
        ret.setShopName((String) payload.get("shopName"));

        // ИСПРАВЛЕНО: Прямой парсинг через русский форматтер
        String returnDateString = (String) payload.get("returnDate");
        if (returnDateString != null && !returnDateString.isEmpty()) {
            try {
                ret.setReturnDate(LocalDate.parse(returnDateString, RU_DATE_FORMATTER));
//                ret.setReturnDate(LocalDate.parse(returnDateString));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Неверный формат даты возврата: " + returnDateString));
            }
        }

        ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));

        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");

// Используем наш новый метод и берем только цену продажи
        Map<String, Double> totals = calculateTotalSaleAndCost(newItems);
        double newTotal = totals.get("totalSale");

        ret.setItems(newItems);
        ret.setTotalAmount(newTotal);
        returnOrderRepository.save(ret);

        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("ИЗМЕНЕНИЕ ВОЗВРАТА");
        log.setDetails("Обновлен состав возврата. Новая сумма: " + newTotal);
        log.setEntityId(id);
        log.setEntityType("RETURN");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("newTotal", newTotal, "message", "Возврат изменен"));
    }

    @PutMapping("/clients/{id}/edit")
    @Transactional
    public ResponseEntity<?> editClient(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return clientRepository.findById(id).map(c -> {
            c.setName((String) payload.get("name"));
            c.setAddress((String) payload.get("address"));
            c.setDebt(((Number) payload.get("debt")).doubleValue());
            c.setOwnerName((String) payload.get("ownerName"));
            c.setInn((String) payload.get("inn"));
            c.setPhone((String) payload.get("phone"));
            clientRepository.save(c);

            AuditLog log = new AuditLog();
            log.setUsername("ADMIN");
            log.setAction("ИЗМЕНЕНИЕ КЛИЕНТА");
            log.setDetails("Обновлены реквизиты или долг магазина: " + c.getName());
            log.setEntityId(id);
            log.setEntityType("CLIENT");
            auditLogRepository.save(log);

            return ResponseEntity.ok(Map.of("message", "Данные клиента обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/create-manual")
    @Transactional
    public ResponseEntity<?> createOrderManual(@RequestBody Order order) {
        order.setStatus(OrderStatus.RESERVED);
        order.setCreatedAt(LocalDateTime.now().format(DATETIME_FORMATTER));

        Map<String, Double> totals = calculateTotalSaleAndCost(order.getItems());
        order.setTotalAmount(totals.get("totalSale"));
        order.setTotalPurchaseCost(totals.get("totalCost")); // Устанавливаем себестоимость

        stockService.reserveItemsFromStock(order.getItems(), "Ручной заказ #" + order.getId());
        Order saved = orderRepository.save(order);

        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("СОЗДАНИЕ ЗАКАЗА (РУЧНОЕ)");
        log.setDetails("Оператором создан заказ #" + saved.getId() + " для " + saved.getShopName());
        log.setEntityId(saved.getId());
        log.setEntityType("ORDER");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("message", "Заказ создан", "id", saved.getId(), "totalCost", totals.get("totalCost")));
    }

    @PostMapping("/returns/{id}/confirm")
    @Transactional
    public ResponseEntity<?> confirmReturn(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден"));

        if (ret.getStatus() != ReturnStatus.CONFIRMED) {
            // Находим клиента по имени из возврата
            Client client = clientRepository.findByName(ret.getShopName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            // Вызываем сервис финансов
            financeService.registerOperation(
                    client.getId(),
                    "RETURN",
                    ret.getTotalAmount(),
                    ret.getId(), // Используем геттер getId()
                    "Возврат товара (Акт №" + ret.getId() + ")"
            );

            ret.setStatus(ReturnStatus.CONFIRMED);
            returnOrderRepository.save(ret);

            AuditLog log = new AuditLog();
            log.setUsername("ADMIN");
            log.setAction("ПОДТВЕРЖДЕНИЕ ВОЗВРАТА");
            log.setDetails("Возврат #" + id + " подтвержден. Долг клиента уменьшен на " + ret.getTotalAmount());
            log.setEntityId(id);
            log.setEntityType("RETURN");
            auditLogRepository.save(log);
        }
        return ResponseEntity.ok(Map.of("message", "Возврат подтвержден и записан в историю"));
    }


    @PostMapping("/products/{id}/inventory")
    @Transactional
    public ResponseEntity<?> updateStockManual(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Product p = productRepository.findById(id).orElseThrow();
        int newQty = ((Number) payload.get("newQty")).intValue();
        String reason = (String) payload.get("reason");
        int diff = newQty - p.getStockQuantity();

        p.setStockQuantity(newQty);
        productRepository.save(p);

        // Записываем в историю движения
        stockService.logMovement(p.getName(), diff, "ADJUSTMENT", "Инвентаризация: " + reason, "ADMIN");

        return ResponseEntity.ok(Map.of("message", "Склад обновлен. Разница: " + diff));
    }


    @PostMapping("/returns/{id}/delete")
    @Transactional
    public ResponseEntity<?> deleteReturnOrder(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден"));

        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Подтвержденный возврат удалить нельзя!"));
        }

        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("УДАЛЕНИЕ ВОЗВРАТА");
        log.setDetails("Черновик возврата #" + id + " удален из системы");
        log.setEntityId(id);
        log.setEntityType("RETURN");
        auditLogRepository.save(log);

        returnOrderRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Возврат успешно удален"));
    }

    private Map<String, Double> calculateTotalSaleAndCost(Map<String, Integer> items) {
        double totalSale = 0;
        double totalCost = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = productRepository.findByNameWithLock(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));
            totalSale += p.getPrice() * entry.getValue();
            totalCost += (p.getPurchasePrice() != null ? p.getPurchasePrice() : 0.0) * entry.getValue();
        }
        Map<String, Double> result = new HashMap<>();
        result.put("totalSale", totalSale);
        result.put("totalCost", totalCost);
        return result;
    }
}