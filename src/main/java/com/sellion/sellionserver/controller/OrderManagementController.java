package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderManagementController {

    private final OrderRepository orderRepository;

    // Редактирование заказа (Оператор)
    @PutMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('OPERATOR') or hasAuthority('ADMIN')")
    public ResponseEntity<?> editOrder(@PathVariable Long id, @RequestBody Order updatedOrder) {
        Order existingOrder = orderRepository.findById(id).orElseThrow();

        // ПРОВЕРКА: Редактирование запрещено, если статус не NEW или ACCEPTED
        if (existingOrder.getStatus() == OrderStatus.INVOICED) {
            return ResponseEntity.badRequest().body("Счёт уже создан. Редактирование запрещено!");
        }

        existingOrder.setItems(updatedOrder.getItems());
        existingOrder.setShopName(updatedOrder.getShopName());
        existingOrder.setTotalAmount(updatedOrder.getTotalAmount());

        orderRepository.save(existingOrder);
        return ResponseEntity.ok("Заказ обновлен");
    }

    // Отмена заказа
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('OPERATOR') or hasAuthority('ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        if (order.getStatus() == OrderStatus.INVOICED) {
            return ResponseEntity.badRequest().body("Нельзя отменить заказ со счётом!");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return ResponseEntity.ok("Заказ отменен");
    }
}