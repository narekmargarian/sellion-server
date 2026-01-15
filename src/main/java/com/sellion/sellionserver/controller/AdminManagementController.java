package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final AuditLogRepository auditLogRepository; // Добавлено для логов

    @PutMapping("/orders/{id}/full-edit")
    @Transactional
    public ResponseEntity<?> fullEditOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id).orElseThrow();

        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        stockService.returnItemsToStock(order.getItems());

        order.setShopName((String) payload.get("shopName"));
        order.setDeliveryDate((String) payload.get("deliveryDate"));
        order.setNeedsSeparateInvoice((Boolean) payload.get("needsSeparateInvoice"));
        order.setPaymentMethod(PaymentMethod.fromString((String) payload.get("paymentMethod")));

        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        try {
            stockService.deductItemsFromStock(newItems);
        } catch (RuntimeException e) {
            stockService.deductItemsFromStock(order.getItems());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        double newTotal = calculateTotal(newItems);
        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        orderRepository.save(order);

        // ЛОГИРОВАНИЕ ИЗМЕНЕНИЯ ЗАКАЗА
        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("РЕДАКТИРОВАНИЕ ЗАКАЗА");
        log.setDetails("Изменен состав или параметры заказа. Новая сумма: " + newTotal);
        log.setEntityId(id);
        log.setEntityType("ORDER");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("finalSum", newTotal, "message", "Заказ обновлен"));
    }

    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        if (order.getStatus() == OrderStatus.PROCESSED) {
            stockService.returnItemsToStock(order.getItems());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // ЛОГИРОВАНИЕ ОТМЕНЫ ЗАКАЗА
        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("ОТМЕНА ЗАКАЗА");
        log.setDetails("Заказ отменен, товары возвращены на склад");
        log.setEntityId(id);
        log.setEntityType("ORDER");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("message", "Заказ отменен, товар вернут на склад"));
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

            // ЛОГИРОВАНИЕ ИЗМЕНЕНИЯ ТОВАРА
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

    // --- ВОЗВРАТЫ ---
    @PutMapping("/returns/{id}/edit")
    @Transactional
    public ResponseEntity<?> editReturn(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();
        ret.setShopName((String) payload.get("shopName"));
        ret.setReturnDate((String) payload.get("returnDate"));
        ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));

        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        double newTotal = calculateTotal(newItems);
        ret.setItems(newItems);
        ret.setTotalAmount(newTotal);
        returnOrderRepository.save(ret);

        // АУДИТ: Изменение возврата
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

            // АУДИТ: Изменение данных клиента
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
        order.setStatus(OrderStatus.ACCEPTED);
        order.setCreatedAt(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        stockService.deductItemsFromStock(order.getItems());
        Order saved = orderRepository.save(order);

        // АУДИТ: Ручное создание заказа
        AuditLog log = new AuditLog();
        log.setUsername("ADMIN");
        log.setAction("СОЗДАНИЕ ЗАКАЗА (РУЧНОЕ)");
        log.setDetails("Оператором создан заказ #" + saved.getId() + " для " + saved.getShopName());
        log.setEntityId(saved.getId());
        log.setEntityType("ORDER");
        auditLogRepository.save(log);

        return ResponseEntity.ok(Map.of("message", "Заказ создан и списан со склада", "id", saved.getId()));
    }

    @PostMapping("/returns/{id}/confirm")
    @Transactional
    public ResponseEntity<?> confirmReturn(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();
        if (ret.getStatus() != ReturnStatus.CONFIRMED) {
            clientRepository.findByName(ret.getShopName()).ifPresent(client -> {
                double currentDebt = client.getDebt() != null ? client.getDebt() : 0.0;
                client.setDebt(Math.max(0, currentDebt - ret.getTotalAmount()));
                clientRepository.save(client);
            });

            ret.setStatus(ReturnStatus.CONFIRMED);
            returnOrderRepository.save(ret);

            // АУДИТ: Подтверждение возврата
            AuditLog log = new AuditLog();
            log.setUsername("ADMIN");
            log.setAction("ПОДТВЕРЖДЕНИЕ ВОЗВРАТА");
            log.setDetails("Возврат #" + id + " подтвержден. Долг клиента уменьшен на " + ret.getTotalAmount());
            log.setEntityId(id);
            log.setEntityType("RETURN");
            auditLogRepository.save(log);
        }
        return ResponseEntity.ok(Map.of("message", "Возврат подтвержден, долг клиента уменьшен"));
    }

    @PostMapping("/returns/{id}/delete")
    @Transactional
    public ResponseEntity<?> deleteReturnOrder(@PathVariable Long id) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден"));

        if (ret.getStatus() == ReturnStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Подтвержденный возврат удалить нельзя!"));
        }

        // АУДИТ: Перед удалением записываем лог
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

    private double calculateTotal(Map<String, Integer> items) {
        double total = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = productRepository.findByNameWithLock(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));
            total += p.getPrice() * entry.getValue();
        }
        return total;
    }
}
