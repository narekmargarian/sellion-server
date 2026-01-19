package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // ИСПРАВЛЕНО: Суммирование BigDecimal
        BigDecimal totalOrdersSum = filteredOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        // 1. Выручка (ИСПРАВЛЕНО: BigDecimal)
        BigDecimal rawSales = filteredOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Себестоимость (ИСПРАВЛЕНО: BigDecimal)
        BigDecimal rawPurchaseCost = filteredOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalPurchaseCost() != null ? o.getTotalPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Чистая прибыль - округляем до ближайшего целого числа
        BigDecimal netProfitBD = rawSales.subtract(rawPurchaseCost).setScale(0, RoundingMode.HALF_UP);
        long netProfit = netProfitBD.longValue();

        model.addAttribute("totalOrdersSum", totalOrdersSum); // Добавьте это в модель
        model.addAttribute("totalSales", rawSales.longValue());
        model.addAttribute("totalPurchaseCost", rawPurchaseCost.longValue());
        model.addAttribute("netProfit", netProfit);
        // 2. ЛОГИКА ДЛЯ ВОЗВРАТОВ
        String rStart = (returnStartDate != null && !returnStartDate.isEmpty()) ? returnStartDate : today;
        String rEnd = (returnEndDate != null && !returnEndDate.isEmpty()) ? returnEndDate : rStart;

        List<ReturnOrder> allReturns = returnOrderRepository.findReturnsBetweenDates(rStart + "T00:00:00", rEnd + "T23:59:59");
        if (allReturns == null) allReturns = new ArrayList<>();

        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        // ИСПРАВЛЕНО: Суммирование BigDecimal
        BigDecimal totalReturnsSum = filteredReturns.stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. ОБЩАЯ СТАТИСТИКА И СЧЕТА
        List<Invoice> invoices = invoiceRepository.findAll();
        if (invoices == null) invoices = new ArrayList<>();

        // ИСПРАВЛЕНО: Суммирование BigDecimal (Долг)
        BigDecimal totalInvoiceDebt = invoices.stream()
                .map(i -> {
                    BigDecimal total = (i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO);
                    BigDecimal paid = (i.getPaidAmount() != null ? i.getPaidAmount() : BigDecimal.ZERO);
                    return total.subtract(paid);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ИСПРАВЛЕНО: Суммирование BigDecimal (Оплачено)
        BigDecimal totalPaidSum = invoices.stream()
                .map(i -> i.getPaidAmount() != null ? i.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ИСПРАВЛЕНО: Суммирование BigDecimal (Общая сумма заказов)
        BigDecimal totalAllOrdersSum = allOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ИСПРАВЛЕНО: Расчет среднего чека через BigDecimal
        BigDecimal avgCheck = allOrders.isEmpty() ? BigDecimal.ZERO :
                totalAllOrdersSum.divide(BigDecimal.valueOf(allOrders.size()), 2, RoundingMode.HALF_UP);

        // Получаем последние логи для таблицы импорта
        List<AuditLog> auditLogs = auditLogRepository.findAllByOrderByTimestampDesc();

        // Ограничим список последними 15 записями, чтобы не перегружать страницу
        List<AuditLog> limitedLogs = auditLogs.stream().limit(15).toList();

        model.addAttribute("auditLogs", limitedLogs);
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);
        model.addAttribute("totalPaidSum", totalPaidSum);
        model.addAttribute("avgCheck", avgCheck);
        // 4. ПЕРЕДАЧА ДАННЫХ В МОДЕЛЬ
        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders.size());
        // Значения totalOrdersSum, rawSales и т.д. уже рассчитаны в Части 1 как BigDecimal
        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("orderStartDate", oStart);
        model.addAttribute("orderEndDate", oEnd);
        model.addAttribute("selectedOrderManager", orderManagerId);

        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum); // Рассчитано в Части 2 как BigDecimal
        model.addAttribute("returnStartDate", rStart);
        model.addAttribute("returnEndDate", rEnd);
        model.addAttribute("selectedReturnManager", returnManagerId);

        model.addAttribute("totalPaidSum", totalPaidSum);
        model.addAttribute("avgCheck", avgCheck);
        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);

        // Группировка товаров по категориям (Сортировка А-Я)
        Map<String, List<Product>> groupedProducts = productRepository.findAllActive().stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getCategory() == null || p.getCategory().isBlank()) ? "Без категории" : p.getCategory(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        model.addAttribute("groupedProducts", groupedProducts);
        model.addAttribute("products", productRepository.findAllActive());

        model.addAttribute("clients", clientRepository.findAllActive() != null ? clientRepository.findAllActive() : new ArrayList<>());
        model.addAttribute("users", userRepository.findAll() != null ? userRepository.findAll() : new ArrayList<>());

        // Список менеджеров для фильтров
        List<String> managers = Stream.concat(allOrders.stream().map(Order::getManagerId),
                        allReturns.stream().map(ReturnOrder::getManagerId))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        model.addAttribute("managers", managers);

        // Клиенты с просрочкой более месяца
        java.time.LocalDateTime oneMonthAgo = java.time.LocalDateTime.now().minusMonths(1);
        java.util.Set<String> overdueClients = invoices.stream()
                .filter(inv -> !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(oneMonthAgo))
                .map(Invoice::getShopName)
                .collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        // Карта долгов клиентов (ИСПРАВЛЕНО: использование BigDecimal)
        Map<String, BigDecimal> clientDebts = clientRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Client::getName,
                        c -> c.getDebt() != null ? c.getDebt() : BigDecimal.ZERO,
                        (v1, v2) -> v1
                ));
        model.addAttribute("clientDebts", clientDebts);

        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);

        return "dashboard";
    }
}
