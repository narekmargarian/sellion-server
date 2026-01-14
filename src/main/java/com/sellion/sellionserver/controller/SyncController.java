package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<?> syncOrders(@RequestBody List<Order> orders) {
        if (orders != null) {
            orders.forEach(order -> {
                order.setId(null);
                order.setStatus(OrderStatus.NEW);
            });
            orderRepository.saveAll(orders);
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PostMapping("/returns")
    @Transactional
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns != null) {
            returns.forEach(ret -> {
                ret.setId(null);
                ret.setStatus(ReturnStatus.DRAFT);
            });
            returnOrderRepository.saveAll(returns);
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}
