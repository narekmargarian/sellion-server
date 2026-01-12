package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainWebController {

    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository; // Добавили возвраты

    public MainWebController(ClientRepository clientRepository,
                             ProductRepository productRepository,
                             OrderRepository orderRepository,
                             ReturnOrderRepository returnOrderRepository) {
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.returnOrderRepository = returnOrderRepository;
    }

    @GetMapping("/admin")
    public String showDashboard(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("orders", orderRepository.findAll());
        model.addAttribute("returns", returnOrderRepository.findAll());
        return "dashboard";
    }

}