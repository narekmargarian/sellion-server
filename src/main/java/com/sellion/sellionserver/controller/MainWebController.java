package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*; // Импорт всех сущностей и Enum
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            // 4 отдельных параметра даты для независимой фильтрации
            @RequestParam(value = "orderStartDate", required = false) String orderStartDate,
            @RequestParam(value = "orderEndDate", required = false) String orderEndDate,
            @RequestParam(value = "returnStartDate", required = false) String returnStartDate,
            @RequestParam(value = "returnEndDate", required = false) String returnEndDate,
            @RequestParam(value = "activeTab", required = false, defaultValue = "tab-orders") String activeTab,
            Model model) {

        String today = LocalDate.now().toString();

        // 1. ЛОГИКА ДЛЯ ЗАКАЗОВ
        String oStart = (orderStartDate != null && !orderStartDate.isEmpty()) ? orderStartDate : today;
        String oEnd = (orderEndDate != null && !orderEndDate.isEmpty()) ? orderEndDate : oStart;

        List<Order> allOrders = orderRepository.findOrdersBetweenDates(oStart + "T00:00:00", oEnd + "T23:59:59");
        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        double totalOrdersSum = filteredOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        // 2. ЛОГИКА ДЛЯ ВОЗВРАТОВ
        String rStart = (returnStartDate != null && !returnStartDate.isEmpty()) ? returnStartDate : today;
        String rEnd = (returnEndDate != null && !returnEndDate.isEmpty()) ? returnEndDate : rStart;

        List<ReturnOrder> allReturns = returnOrderRepository.findReturnsBetweenDates(rStart + "T00:00:00", rEnd + "T23:59:59");
        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        double totalReturnsSum = filteredReturns.stream()
                .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount() : 0.0)
                .sum();

        // 3. ОБЩАЯ СТАТИСТИКА И СЧЕТА
        List<Invoice> invoices = invoiceRepository.findAll();
        double totalInvoiceDebt = invoices.stream()
                .mapToDouble(i -> (i.getTotalAmount() != null ? i.getTotalAmount() : 0)
                        - (i.getPaidAmount() != null ? i.getPaidAmount() : 0))
                .sum();

        double totalPaidSum = invoices.stream()
                .mapToDouble(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0.0)
                .sum();

        double totalAllOrdersSum = allOrders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0).sum();
        double avgCheck = allOrders.isEmpty() ? 0 : totalAllOrdersSum / allOrders.size();

        // 4. ПЕРЕДАЧА ДАННЫХ ЗАКАЗОВ В МОДЕЛЬ
        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders.size());
        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("orderStartDate", oStart);
        model.addAttribute("orderEndDate", oEnd);
        model.addAttribute("selectedOrderManager", orderManagerId);

        // 5. ПЕРЕДАЧА ДАННЫХ ВОЗВРАТОВ В МОДЕЛЬ
        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("returnStartDate", rStart);
        model.addAttribute("returnEndDate", rEnd);
        model.addAttribute("selectedReturnManager", returnManagerId);

        // 6. ОСТАЛЬНЫЕ ДАННЫЕ
        model.addAttribute("totalPaidSum", totalPaidSum);
        model.addAttribute("avgCheck", avgCheck);
        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("users", userRepository.findAll());

        // Список менеджеров для фильтров (из текущих выборок)
        List<String> managers = Stream.concat(allOrders.stream().map(Order::getManagerId),
                        allReturns.stream().map(ReturnOrder::getManagerId))
                .filter(Objects::nonNull).distinct().sorted().toList();
        model.addAttribute("managers", managers);

        // Проверка просрочки
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        Set<String> overdueClients = invoices.stream()
                .filter(inv -> !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(oneMonthAgo))
                .map(Invoice::getShopName).collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        // Долги клиентов
        Map<String, Double> clientDebts = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, c -> c.getDebt() != null ? c.getDebt() : 0.0, (v1, v2) -> v1));
        model.addAttribute("clientDebts", clientDebts);

        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);

        return "dashboard";
    }
}

