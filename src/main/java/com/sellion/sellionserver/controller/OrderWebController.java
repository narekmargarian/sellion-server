package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.services.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class OrderWebController {

    private final OrderRepository orderRepository;


    private final StockService stockService;

    @PostMapping("/cancel/{id}")
    public String cancelOrder(@PathVariable Long id) {
        orderRepository.findById(id).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PROCESSED) {
                stockService.returnItemsToStock(order.getItems()); // Возвращаем товар при отмене
            }
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        });
        return "redirect:/admin?activeTab=tab-orders";
    }
}
