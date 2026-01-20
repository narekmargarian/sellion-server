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

        // --- 1. ЛОГИКА ДЛЯ ЗАКАЗОВ ---
        String oStart = (orderStartDate != null && !orderStartDate.isEmpty()) ? orderStartDate : today;
        String oEnd = (orderEndDate != null && !orderEndDate.isEmpty()) ? orderEndDate : oStart;

        List<Order> allOrders = Optional.ofNullable(orderRepository.findOrdersBetweenDates(oStart + "T00:00:00", oEnd + "T23:59:59"))
                .orElse(new ArrayList<>());

        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> o != null && orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        BigDecimal totalOrdersSum = filteredOrders.stream()
                .filter(Objects::nonNull)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rawSales = filteredOrders.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rawPurchaseCost = filteredOrders.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalPurchaseCost() != null ? o.getTotalPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 2. ЛОГИКА ДЛЯ ВОЗВРАТОВ ---
        String rStart = (returnStartDate != null && !returnStartDate.isEmpty()) ? returnStartDate : today;
        String rEnd = (returnEndDate != null && !returnEndDate.isEmpty()) ? returnEndDate : rStart;

        List<ReturnOrder> allReturns = Optional.ofNullable(returnOrderRepository.findReturnsBetweenDates(rStart + "T00:00:00", rEnd + "T23:59:59"))
                .orElse(new ArrayList<>());

        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> r != null && returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        BigDecimal totalReturnsSum = filteredReturns.stream()
                .filter(Objects::nonNull)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- РАСЧЕТ ЧИСТОЙ ПРИБЫЛИ (Исправлено: Выручка - Себестоимость - Возвраты) ---
        BigDecimal netProfitBD = rawSales.subtract(rawPurchaseCost)
                .subtract(totalReturnsSum)
                .setScale(0, RoundingMode.HALF_UP);

        // --- 3. ОБЩАЯ СТАТИСТИКА И СЧЕТА ---
        List<Invoice> invoices = Optional.ofNullable(invoiceRepository.findAll()).orElse(new ArrayList<>());

        BigDecimal totalInvoiceDebt = invoices.stream()
                .filter(Objects::nonNull)
                .map(i -> {
                    BigDecimal total = (i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO);
                    BigDecimal paid = (i.getPaidAmount() != null ? i.getPaidAmount() : BigDecimal.ZERO);
                    return total.subtract(paid);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaidSum = invoices.stream()
                .filter(Objects::nonNull)
                .map(i -> i.getPaidAmount() != null ? i.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAllOrdersSum = allOrders.stream()
                .filter(Objects::nonNull)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Исправлено: Гарантированная защита от деления на ноль
        BigDecimal avgCheck = (allOrders.isEmpty()) ? BigDecimal.ZERO :
                totalAllOrdersSum.divide(BigDecimal.valueOf(allOrders.size()), 2, RoundingMode.HALF_UP);

        List<AuditLog> limitedLogs = Optional.ofNullable(auditLogRepository.findAllByOrderByTimestampDesc())
                .orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .limit(15)
                .toList();


        // --- 4. ПЕРЕДАЧА ДАННЫХ В МОДЕЛЬ ---

        // Гарантируем, что в модель не уйдет null даже по ошибке
        model.addAttribute("totalOrdersSum", totalOrdersSum != null ? totalOrdersSum : BigDecimal.ZERO);
        model.addAttribute("totalSales", rawSales != null ? rawSales.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        model.addAttribute("totalPurchaseCost", rawPurchaseCost != null ? rawPurchaseCost.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        model.addAttribute("netProfit", netProfitBD != null ? netProfitBD : BigDecimal.ZERO);
        model.addAttribute("avgCheck", avgCheck != null ? avgCheck : BigDecimal.ZERO);
        model.addAttribute("auditLogs", limitedLogs);

        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders != null ? filteredOrders.size() : 0);
        model.addAttribute("orderStartDate", oStart);
        model.addAttribute("orderEndDate", oEnd);
        model.addAttribute("selectedOrderManager", orderManagerId);

        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns != null ? filteredReturns.size() : 0);
        model.addAttribute("totalReturnsSum", totalReturnsSum != null ? totalReturnsSum : BigDecimal.ZERO);
        model.addAttribute("returnStartDate", rStart);
        model.addAttribute("returnEndDate", rEnd);
        model.addAttribute("selectedReturnManager", returnManagerId);

        // Группировка товаров (Исправлено: добавлена проверка на пустой список)
        List<Product> activeProducts = Optional.ofNullable(productRepository.findAllActive()).orElse(new ArrayList<>());
        Map<String, List<Product>> groupedProducts = activeProducts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        p -> (p.getCategory() == null || p.getCategory().isBlank()) ? "Без категории" : p.getCategory(),
                        TreeMap::new,
                        Collectors.toList()
                ));
        model.addAttribute("groupedProducts", groupedProducts);
        model.addAttribute("products", activeProducts);

        // Клиенты и пользователи (Безопасно)
        List<Client> activeClients = Optional.ofNullable(clientRepository.findAllActive()).orElse(new ArrayList<>());
        model.addAttribute("clients", activeClients);
        model.addAttribute("users", Optional.ofNullable(userRepository.findAll()).orElse(new ArrayList<>()));

        // Список менеджеров (Используем Set для скорости, затем в List)
        List<String> managers = Stream.concat(
                        allOrders.stream().filter(Objects::nonNull).map(Order::getManagerId),
                        allReturns.stream().filter(Objects::nonNull).map(ReturnOrder::getManagerId))
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .sorted()
                .toList();
        model.addAttribute("managers", managers);

        // Просрочка (2026: Учитываем текущую дату сервера)
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        Set<String> overdueClients = invoices.stream()
                .filter(inv -> inv != null && !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(oneMonthAgo))
                .map(Invoice::getShopName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        // Долги клиентов (Используем уже загруженный список activeClients для скорости)
        Map<String, BigDecimal> clientDebts = activeClients.stream()
                .filter(c -> c != null && c.getName() != null)
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
