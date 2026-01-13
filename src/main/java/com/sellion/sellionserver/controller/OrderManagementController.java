package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.PaymentMethod;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderManagementController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;

    // Этот метод /edit оставлен без изменений, так как он не используется в текущем UI
    @PutMapping("/{id}/edit")
    @Transactional
    public ResponseEntity<?> editOrder(@PathVariable Long id, @RequestBody Map<String, Map<String, Integer>> payload) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        if (order.getStatus() == OrderStatus.INVOICED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Счёт выставлен. Редактирование запрещено."));
        }

        Map<String, Integer> newItems = payload.get("items");

        try {
            if (order.getStatus() == OrderStatus.PROCESSED) {
                stockService.returnItemsToStock(order.getItems());
            }

            stockService.deductItemsFromStock(newItems);

            double newTotalAmount = 0.0;
            for (Map.Entry<String, Integer> entry : newItems.entrySet()) {
                Product p = productRepository.findByName(entry.getKey()).orElseThrow();
                newTotalAmount += p.getPrice() * entry.getValue();
            }

            order.setItems(newItems);
            order.setTotalAmount(newTotalAmount);
            order.setStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);

            return ResponseEntity.ok(Map.of("message", "Заказ обновлен", "newTotal", newTotalAmount));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ИСПРАВЛЕННЫЙ МЕТОД full-edit
    @PutMapping("/{id}/full-edit")
    @Transactional
    public ResponseEntity<?> fullEditOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id).orElseThrow();

        if (order.getStatus() == OrderStatus.INVOICED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя редактировать заказ со счетом!"));
        }

        // 1. Возвращаем старый товар на склад
        stockService.returnItemsToStock(order.getItems());

        // 2. Обновляем простые поля
        order.setShopName((String) payload.get("shopName"));
        order.setDeliveryDate((String) payload.get("deliveryDate"));
        order.setNeedsSeparateInvoice((Boolean) payload.get("needsSeparateInvoice"));

        // ИЗМЕНЕНО: Конвертируем строку оплаты в Enum PaymentMethod
        String paymentMethodString = (String) payload.get("paymentMethod");
        order.setPaymentMethod(PaymentMethod.valueOf(paymentMethodString));

        // 3. Получаем новый состав товаров и считаем сумму
        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");
        stockService.deductItemsFromStock(newItems); // Списываем новый товар

        double newTotalAmount = 0;
        for (Map.Entry<String, Integer> entry : newItems.entrySet()) {
            Product p = productRepository.findByName(entry.getKey()).orElseThrow();
            newTotalAmount += p.getPrice() * entry.getValue();
        }

        order.setItems(newItems);
        order.setTotalAmount(newTotalAmount);
        orderRepository.save(order);

        // ИСПРАВЛЕНО: Возвращаем JSON-объект, который ожидает frontend (с finalSum)
        return ResponseEntity.ok(Map.of("message", "Заказ полностью обновлен", "finalSum", newTotalAmount));
    }
}
