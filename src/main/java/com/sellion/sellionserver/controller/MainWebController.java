package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;


@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class MainWebController {
    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;

    @GetMapping
    public String showDashboard(
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "activeTab", required = false, defaultValue = "tab-orders") String activeTab,
            Model model) {

        // 1. ЗАКАЗЫ (фильтруем только по orderManagerId)
        List<Order> allOrders = orderRepository.findAll();
        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        double totalOrdersSum = filteredOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();

        // 2. ВОЗВРАТЫ (фильтруем только по returnManagerId)
        List<ReturnOrder> allReturns = returnOrderRepository.findAll();
        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        double totalReturnsSum = filteredReturns.stream()
                .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount() : 0).sum();

        // 3. ОБЩИЕ ДАННЫЕ (Клиенты, Продукты и т.д.)
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("invoices", invoiceRepository.findAll());

        // 4. ПЕРЕДАЧА ДАННЫХ ЗАКАЗОВ
        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders.size());
        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("selectedOrderManager", orderManagerId); // Для подсветки в select

        // 5. ПЕРЕДАЧА ДАННЫХ ВОЗВРАТОВ
        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("selectedReturnManager", returnManagerId); // Для подсветки в select

        // 6. СПИСОК МЕНЕДЖЕРОВ (берем уникальных из всех заказов)
        List<String> managers = allOrders.stream()
                .map(Order::getManagerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        model.addAttribute("managers", managers);

        // Вкладка
        model.addAttribute("activeTab", activeTab);

        return "dashboard";
    }
}