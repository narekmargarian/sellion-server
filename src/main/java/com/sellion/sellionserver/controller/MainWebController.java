package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*; // Импорт всех сущностей и Enum
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
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
            @RequestParam(value = "activeTab", required = false, defaultValue = "tab-orders") String activeTab,
            Model model) {

        // 1. ЗАКАЗЫ
        List<Order> allOrders = orderRepository.findAll();
        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        double totalOrdersSum = filteredOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        // 2. ВОЗВРАТЫ
        List<ReturnOrder> allReturns = returnOrderRepository.findAll();
        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        double totalReturnsSum = filteredReturns.stream()
                .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount() : 0).sum();

        // 3. СЧЕТА И РАСЧЕТ ОБЩЕГО ДОЛГА (ИСПРАВЛЕНИЕ ОШИБКИ 2026)
        List<Invoice> invoices = invoiceRepository.findAll();

        double totalInvoiceDebt = invoices.stream()
                .mapToDouble(i -> (i.getTotalAmount() != null ? i.getTotalAmount() : 0)
                        - (i.getPaidAmount() != null ? i.getPaidAmount() : 0))
                .sum();

        // НОВАЯ ПЕРЕМЕННАЯ: Рассчитываем общую сумму оплаченных средств
        double totalPaidSum = invoices.stream()
                .mapToDouble(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0.0)
                .sum();

        double totalSum = allOrders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0).sum();
        double avgCheck = allOrders.isEmpty() ? 0 : totalSum / allOrders.size();
        model.addAttribute("totalPaidSum", totalPaidSum);
        model.addAttribute("avgCheck", avgCheck);

        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);

        model.addAttribute("users", userRepository.findAll());
        // 4. ДАННЫЕ КЛИЕНТОВ И ПРОДУКТОВ
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("users", userRepository.findAll());

        // 5. ПЕРЕДАЧА ДАННЫХ ЗАКАЗОВ
        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders.size());
        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("selectedOrderManager", orderManagerId);

        // 6. ПЕРЕДАЧА ДАННЫХ ВОЗВРАТОВ
        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("selectedReturnManager", returnManagerId);

        List<String> orderManagers = allOrders.stream().map(Order::getManagerId).filter(Objects::nonNull).toList();
        List<String> returnManagers = allReturns.stream().map(ReturnOrder::getManagerId).filter(Objects::nonNull).toList();

        List<String> managers = Stream.concat(orderManagers.stream(), returnManagers.stream())
                .distinct()
                .sorted()
                .toList();
        model.addAttribute("managers", managers);

        // 8. ПРОВЕРКА ПРОСРОЧКИ (1 МЕСЯЦ)
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        Set<String> overdueClients = invoices.stream()
                .filter(inv -> !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(oneMonthAgo))
                .map(Invoice::getShopName)
                .collect(Collectors.toSet());

        model.addAttribute("overdueClients", overdueClients);

        // 9. ДОЛГИ КЛИЕНТОВ ДЛЯ ТАБЛИЦЫ
        Map<String, Double> clientDebts = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, c -> c.getDebt() != null ? c.getDebt() : 0.0, (v1, v2) -> v1));
        model.addAttribute("clientDebts", clientDebts);

        // 10. ВСПОМОГАТЕЛЬНЫЕ ДАННЫЕ
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);

        return "dashboard";
    }
}
