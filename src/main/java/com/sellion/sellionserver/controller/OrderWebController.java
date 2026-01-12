package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.OrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/orders")
public class OrderWebController {

    private final OrderRepository orderRepository;

    public OrderWebController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public String listOrders(Model model) {
        // Берем все заказы из базы данных и передаем их в HTML
        model.addAttribute("orders", orderRepository.findAll());
        return "orders-list"; // Имя HTML-файла в папке templates
    }
}
