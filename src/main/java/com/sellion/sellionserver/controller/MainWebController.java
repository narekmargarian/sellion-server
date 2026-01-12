package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class MainWebController {

    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;

    public MainWebController(ClientRepository clientRepository,
                             ProductRepository productRepository,
                             OrderRepository orderRepository,
                             ReturnOrderRepository returnOrderRepository) {
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.returnOrderRepository = returnOrderRepository;
    }

    @GetMapping
    public String showDashboard(Model model) {
        // Используем empty list если в базе пусто, чтобы Thymeleaf не падал
        model.addAttribute("orders", orderRepository.findAll());
        model.addAttribute("returns", returnOrderRepository.findAll());
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("products", productRepository.findAll());

        // Добавьте это, чтобы избежать ошибок в шапке (если th:text="${ordersCount}")
        model.addAttribute("ordersCount", orderRepository.count());
        model.addAttribute("returnsCount", returnOrderRepository.count());
        model.addAttribute("clientsCount", clientRepository.count());
        return "dashboard"; // Откроет твой dashboard.html
    }

    @GetMapping("/summary")
    public String showDailySummary(Model model) {
        List<Order> allOrders = orderRepository.findAll();
        Map<String, Integer> totalToCollect = new HashMap<>();

        for (Order order : allOrders) {
            order.getItems().forEach((name, qty) ->
                    totalToCollect.merge(name, qty, Integer::sum));
        }

        model.addAttribute("summary", totalToCollect);
        return "daily-summary";
    }
}