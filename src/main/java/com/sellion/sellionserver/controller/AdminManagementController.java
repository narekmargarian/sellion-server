package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import com.sellion.sellionserver.services.FinanceService;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

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
    @Transactional
    public ResponseEntity<?> fullEditOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        // 1. Проверка на наличие счета
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        // 2. Возвращаем старые товары на склад (с логированием)
        stockService.returnItemsToStock(order.getItems(), "Корректировка состава заказа #" + id);

        // 3. Обновляем данные заказа
        order.setShopName((String) payload.get("shopName"));
        String deliveryDateString = (String) payload.get("deliveryDate");
        if (deliveryDateString != null && !deliveryDateString.isEmpty()) {
            try {
                order.setDeliveryDate(LocalDate.parse(deliveryDateString));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Неверный формат даты: " + deliveryDateString));
            }
        }

        order.setNeedsSeparateInvoice((Boolean) payload.get("needsSeparateInvoice"));
        order.setPaymentMethod(PaymentMethod.fromString((String) payload.get("paymentMethod")));
        order.setCarNumber((String) payload.get("carNumber"));

        // 4. Резервируем новые товары
        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        try {
            stockService.reserveItemsFromStock(newItems, "Обновление состава заказа #" + id);
        } catch (RuntimeException e) {
            // Откат: если новых товаров нет, возвращаем старые
            stockService.deductItemsFromStock(order.getItems(), "Откат к старому составу заказа #" + id);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        // 5. Пересчет суммы через BigDecimal
        Map<String, BigDecimal> totals = calculateTotalSaleAndCost(newItems);
        BigDecimal newTotal = totals.get("totalSale");
        BigDecimal newPurchaseCost = totals.get("totalCost");

        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        order.setTotalPurchaseCost(newPurchaseCost);
        orderRepository.save(order);

        // 6. Аудит
        recordAudit(id, "ORDER", "РЕДАКТИРОВАНИЕ ЗАКАЗА", "Заказ изменен. Новая сумма: " + newTotal + " ֏");

        return ResponseEntity.ok(Map.of("finalSum", newTotal, "message", "Заказ успешно обновлен"));
    }

    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ уже отменен"));
        }
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя отменить заказ с выставленным счетом!"));
        }

        stockService.returnItemsToStock(order.getItems(), "Отмена заказа #" + id);
        order.setStatus(OrderStatus.CANCELLED);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setTotalPurchaseCost(BigDecimal.ZERO);
        orderRepository.save(order);

        recordAudit(id, "ORDER", "ОТМЕНА ЗАКАЗА", "Заказ отменен, товар возвращен на склад");
        return ResponseEntity.ok(Map.of("message", "Заказ отменен, товар возвращен на склад"));
    }

    @PutMapping("/products/{id}/edit")
    @Transactional
    public ResponseEntity<?> editProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return productRepository.findById(id).map(p -> {
            String oldInfo = "Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice();
            p.setName((String) payload.get("name"));
            p.setPrice(new BigDecimal(payload.get("price").toString()));
            p.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
            p.setBarcode((String) payload.get("barcode"));
            p.setItemsPerBox(((Number) payload.get("itemsPerBox")).intValue());
            p.setCategory((String) payload.get("category"));
            p.setHsnCode((String) payload.get("hsnCode"));
            p.setUnit((String) payload.get("unit"));
            productRepository.save(p);

            String newInfo = "Остаток: " + p.getStockQuantity() + ", Цена: " + p.getPrice();
            recordAudit(id, "PRODUCT", "ИЗМЕНЕНИЕ ТОВАРА", "Было [" + oldInfo + "]. Стало [" + newInfo + "]");
            return ResponseEntity.ok(Map.of("message", "Данные товара обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/returns/{id}/edit")
    @Transactional
    public ResponseEntity<?> editReturn(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();
        ret.setShopName((String) payload.get("shopName"));

        if (payload.get("returnDate") != null) {
            ret.setReturnDate(LocalDate.parse((String) payload.get("returnDate")));
        }
        ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));

        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        Map<String, BigDecimal> totals = calculateTotalSaleAndCost(newItems);
        BigDecimal newTotal = totals.get("totalSale");

        ret.setItems(newItems);
        ret.setTotalAmount(newTotal);
        returnOrderRepository.save(ret);

        recordAudit(id, "RETURN", "ИЗМЕНЕНИЕ ВОЗВРАТА", "Обновлен состав. Сумма: " + newTotal + " ֏");
        return ResponseEntity.ok(Map.of("newTotal", newTotal, "message", "Возврат изменен"));
    }

    @PutMapping("/clients/{id}/edit")
    @Transactional
    public ResponseEntity<?> editClient(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return clientRepository.findById(id).map(c -> {
            c.setName((String) payload.get("name"));
            c.setAddress((String) payload.get("address"));
            c.setDebt(new BigDecimal(payload.get("debt").toString()));
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
    @Transactional
    public ResponseEntity<?> createOrderManual(@RequestBody Order order) {
        order.setStatus(OrderStatus.RESERVED);
        order.setCreatedAt(LocalDateTime.now().toString());

        Map<String, BigDecimal> totals = calculateTotalSaleAndCost(order.getItems());
        order.setTotalAmount(totals.get("totalSale"));
        order.setTotalPurchaseCost(totals.get("totalCost"));

        stockService.reserveItemsFromStock(order.getItems(), "Ручной заказ");
        Order saved = orderRepository.save(order);

        recordAudit(saved.getId(), "ORDER", "СОЗДАНИЕ ЗАКАЗА", "Создан заказ #" + saved.getId());
        return ResponseEntity.ok(Map.of("message", "Заказ создан", "id", saved.getId()));
    }

    @PostMapping("/returns/{id}/confirm")
    @Transactional
    public ResponseEntity<?> confirmReturn(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();
        if (ret.getStatus() != ReturnStatus.CONFIRMED) {
            financeService.registerOperation(null, "RETURN", ret.getTotalAmount(), id, "Возврат товара", ret.getShopName());
            ret.setStatus(ReturnStatus.CONFIRMED);
            returnOrderRepository.save(ret);
            recordAudit(id, "RETURN", "ПОДТВЕРЖДЕНИЕ ВОЗВРАТА", "Долг уменьшен на " + ret.getTotalAmount() + " ֏");
        }
        return ResponseEntity.ok(Map.of("message", "Возврат подтвержден"));
    }

    @PostMapping("/products/{id}/inventory")
    @Transactional
    public ResponseEntity<?> updateStockManual(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Product p = productRepository.findById(id).orElseThrow();
        int newQty = ((Number) payload.get("newQty")).intValue();
        int diff = newQty - p.getStockQuantity();
        p.setStockQuantity(newQty);
        productRepository.save(p);

        stockService.logMovement(p.getName(), diff, "ADJUSTMENT", "Инвентаризация: " + payload.get("reason"), "ADMIN");
        return ResponseEntity.ok(Map.of("message", "Склад обновлен"));
    }

    private Map<String, BigDecimal> calculateTotalSaleAndCost(Map<String, Integer> items) {
        BigDecimal totalSale = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = productRepository.findByNameWithLock(entry.getKey()).orElseThrow();
            BigDecimal qty = BigDecimal.valueOf(entry.getValue());
            totalSale = totalSale.add(p.getPrice().multiply(qty));
            totalCost = totalCost.add(p.getPurchasePrice().multiply(qty));
        }
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("totalSale", totalSale);
        result.put("totalCost", totalCost);
        return result;
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