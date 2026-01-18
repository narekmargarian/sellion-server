package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public String showDashboard(
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
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
        if (allOrders == null) allOrders = new ArrayList<>();

        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        double totalOrdersSum = filteredOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();


        // 1. Выручка
        double rawSales = filteredOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

// 2. Себестоимость
        double rawPurchaseCost = filteredOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalPurchaseCost() != null ? o.getTotalPurchaseCost() : 0.0)
                .sum();

// 3. Чистая прибыль - округляем до ближайшего целого числа
        long netProfit = Math.round(rawSales - rawPurchaseCost);

        model.addAttribute("totalSales", Math.round(rawSales));
        model.addAttribute("totalPurchaseCost", Math.round(rawPurchaseCost));
        model.addAttribute("netProfit", netProfit);



        // 2. ЛОГИКА ДЛЯ ВОЗВРАТОВ
        String rStart = (returnStartDate != null && !returnStartDate.isEmpty()) ? returnStartDate : today;
        String rEnd = (returnEndDate != null && !returnEndDate.isEmpty()) ? returnEndDate : rStart;

        List<ReturnOrder> allReturns = returnOrderRepository.findReturnsBetweenDates(rStart + "T00:00:00", rEnd + "T23:59:59");
        if (allReturns == null) allReturns = new ArrayList<>();

        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        double totalReturnsSum = filteredReturns.stream()
                .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount() : 0.0)
                .sum();

        // 3. ОБЩАЯ СТАТИСТИКА И СЧЕТА
        List<Invoice> invoices = invoiceRepository.findAll();
        if (invoices == null) invoices = new ArrayList<>();

        double totalInvoiceDebt = invoices.stream()
                .mapToDouble(i -> (i.getTotalAmount() != null ? i.getTotalAmount() : 0)
                        - (i.getPaidAmount() != null ? i.getPaidAmount() : 0))
                .sum();

        double totalPaidSum = invoices.stream()
                .mapToDouble(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0.0)
                .sum();

        double totalAllOrdersSum = allOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();
        double avgCheck = allOrders.isEmpty() ? 0.0 : totalAllOrdersSum / allOrders.size();

        // Получаем последние логи для таблицы импорта
        List<AuditLog> auditLogs = auditLogRepository.findAllByOrderByTimestampDesc();

        // Ограничим список последними 15 записями, чтобы не перегружать страницу
        List<AuditLog> limitedLogs = auditLogs.stream().limit(15).toList();

        model.addAttribute("auditLogs", limitedLogs);


        // 4. ПЕРЕДАЧА ДАННЫХ В МОДЕЛЬ
        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders.size());
        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("orderStartDate", oStart);
        model.addAttribute("orderEndDate", oEnd);
        model.addAttribute("selectedOrderManager", orderManagerId);

        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("returnStartDate", rStart);
        model.addAttribute("returnEndDate", rEnd);
        model.addAttribute("selectedReturnManager", returnManagerId);

        model.addAttribute("totalPaidSum", totalPaidSum);
        model.addAttribute("avgCheck", avgCheck);
        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);
        // Группировка с сортировкой ключей (TreeMap) для красивого порядка
        Map<String, List<Product>> groupedProducts = productRepository.findAllActive().stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getCategory() == null || p.getCategory().isBlank()) ? "Без категории" : p.getCategory(),
                        TreeMap::new, // Это автоматически отсортирует категории А-Я
                        Collectors.toList()
                ));

        model.addAttribute("groupedProducts", groupedProducts);

        // Оставляем обычный список для поиска (если он используется в JS)
        model.addAttribute("products", productRepository.findAllActive());

        model.addAttribute("clients", clientRepository.findAllActive() != null ? clientRepository.findAllActive() : new ArrayList<>());
        model.addAttribute("users", userRepository.findAll() != null ? userRepository.findAll() : new ArrayList<>());

        List<String> managers = Stream.concat(allOrders.stream().map(Order::getManagerId),
                        allReturns.stream().map(ReturnOrder::getManagerId))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        model.addAttribute("managers", managers);

        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        Set<String> overdueClients = invoices.stream()
                .filter(inv -> !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(oneMonthAgo))
                .map(Invoice::getShopName)
                .collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        Map<String, Double> clientDebts = clientRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Client::getName,
                        c -> c.getDebt() != null ? c.getDebt() : 0.0,
                        (v1, v2) -> v1
                ));
        model.addAttribute("clientDebts", clientDebts);

        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);

        return "dashboard";
    }
}
