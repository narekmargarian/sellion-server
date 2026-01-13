package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderWebController {

    private final OrderRepository orderRepository;

    @PostMapping("/cancel/{id}")
    public String cancelOrder(@PathVariable Long id) {
        orderRepository.findById(id).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.INVOICED) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }
        });
        return "redirect:/admin?activeTab=tab-orders";
    }
}
