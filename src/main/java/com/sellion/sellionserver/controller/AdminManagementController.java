package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // --- ЗАКАЗЫ ---

    @PutMapping("/orders/{id}/full-edit")
    @Transactional
    public ResponseEntity<?> fullEditOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id).orElseThrow();

        // Запрет редактирования, если есть счет
        if (order.getInvoiceId() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Заказ со счетом нельзя менять!"));
        }

        // 1. Возврат старого товара на склад ПЕРЕД изменением состава
        stockService.returnItemsToStock(order.getItems());

        // 2. Обновление полей
        order.setShopName((String) payload.get("shopName"));
        order.setDeliveryDate((String) payload.get("deliveryDate"));
        order.setNeedsSeparateInvoice((Boolean) payload.get("needsSeparateInvoice"));

        // ИСПОЛЬЗУЕМ БЕЗОПАСНЫЙ ENUM
        order.setPaymentMethod(PaymentMethod.fromString((String) payload.get("paymentMethod")));

        // 3. Списание нового состава
        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        try {
            stockService.deductItemsFromStock(newItems);
        } catch (RuntimeException e) {
            // Если на складе нет места для НОВОГО состава — возвращаем старый назад и выдаем ошибку
            stockService.deductItemsFromStock(order.getItems());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        // 4. Пересчет и сохранение
        double newTotal = calculateTotal(newItems);
        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        orderRepository.save(order);

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
        return ResponseEntity.ok(Map.of("message", "Заказ отменен, товар вернут на склад"));
    }

    // --- ТОВАРЫ ---

    @PutMapping("/products/{id}")
    @Transactional
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product updated) {
        return productRepository.findById(id).map(p -> {
            p.setName(updated.getName());
            p.setPrice(updated.getPrice());
            p.setStockQuantity(updated.getStockQuantity());
            p.setBarcode(updated.getBarcode());
            p.setItemsPerBox(updated.getItemsPerBox());
            productRepository.save(p);
            return ResponseEntity.ok(Map.of("message", "Данные товара обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- НОВОЕ API: ОБНОВЛЕНИЕ ТОВАРА (для новой JS-логики) ---
    @PutMapping("/products/{id}/edit")
    @Transactional
    public ResponseEntity<?> editProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return productRepository.findById(id).map(p -> {
            p.setName((String) payload.get("name"));
            // Обработка чисел из payload, так как они могут прийти как Integer
            p.setPrice(((Number) payload.get("price")).doubleValue());
            p.setStockQuantity(((Number) payload.get("stockQuantity")).intValue());
            p.setBarcode((String) payload.get("barcode"));
            p.setItemsPerBox(((Number) payload.get("itemsPerBox")).intValue());
            p.setCategory((String) payload.get("category"));

            productRepository.save(p);
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

        // ИСПРАВЛЕНО: Используем безопасный метод и правильный Enum
        ret.setReturnReason(ReasonsReturn.fromString((String) payload.get("returnReason")));

        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        double newTotal = calculateTotal(newItems);

        ret.setItems(newItems);
        ret.setTotalAmount(newTotal);
        returnOrderRepository.save(ret);

        return ResponseEntity.ok(Map.of("newTotal", newTotal, "message", "Возврат изменен"));
    }

    // --- НОВОЕ API: ОБНОВЛЕНИЕ КЛИЕНТА ---
    @PutMapping("/clients/{id}/edit")
    @Transactional
    public ResponseEntity<?> editClient(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return clientRepository.findById(id).map(c -> {
            c.setName((String) payload.get("name"));
            c.setAddress((String) payload.get("address"));
            // Обработка числа из payload
            c.setDebt(((Number) payload.get("debt")).doubleValue());
            clientRepository.save(c);
            return ResponseEntity.ok(Map.of("message", "Данные клиента обновлены"));
        }).orElse(ResponseEntity.notFound().build());
    }


    // --- УТИЛИТЫ ---

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
