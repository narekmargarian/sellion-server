package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.ManagerKpiDTO;
import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


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
    private final ManagerTargetRepository managerTargetRepository;

    @GetMapping
    public String showDashboard(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "orderStartDate", required = false) String orderStartDate,
            @RequestParam(value = "orderEndDate", required = false) String orderEndDate,
            @RequestParam(value = "returnStartDate", required = false) String returnStartDate,
            @RequestParam(value = "returnEndDate", required = false) String returnEndDate,
            @RequestParam(value = "activeTab", required = false, defaultValue = "tab-orders") String activeTab,
            Model model) {

        // --- 1. ЛОГИКА ДАТ (2026 СТАНДАРТ) ---
        LocalDate startD = (orderStartDate != null && !orderStartDate.isEmpty()) ? LocalDate.parse(orderStartDate) : LocalDate.now();
        LocalDate endD = (orderEndDate != null && !orderEndDate.isEmpty()) ? LocalDate.parse(orderEndDate) : startD;
        LocalDateTime oStartDT = startD.atStartOfDay();
        LocalDateTime oEndDT = endD.atTime(LocalTime.MAX);

        LocalDate startR = (returnStartDate != null && !returnStartDate.isEmpty()) ? LocalDate.parse(returnStartDate) : LocalDate.now();
        LocalDate endR = (returnEndDate != null && !returnEndDate.isEmpty()) ? LocalDate.parse(returnEndDate) : startR;
        LocalDateTime rStartDT = startR.atStartOfDay();
        LocalDateTime rEndDT = endR.atTime(LocalTime.MAX);

        // --- 2. ЛОГИКА ДЛЯ ЗАКАЗОВ (Полная статистика + Пагинация) ---
        // ИДЕАЛЬНО: Используем Pageable прямо в запросе репозитория для производительности 2026 года
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Order> ordersPage;
        if (orderManagerId != null && !orderManagerId.isEmpty()) {
            ordersPage = orderRepository.findOrdersByManagerAndDateRangePaged(orderManagerId, oStartDT, oEndDT, pageable);
        } else {
            ordersPage = orderRepository.findOrdersBetweenDatesPaged(oStartDT, oEndDT, pageable);
        }

        // Для расчета статистики за период все равно нужны агрегированные данные (сохраняем вашу логику фильтрации)
        List<Order> allOrdersForPeriod = (orderManagerId != null && !orderManagerId.isEmpty())
                ? orderRepository.findOrdersByManagerAndDateRange(orderManagerId, oStartDT, oEndDT)
                : orderRepository.findOrdersBetweenDates(oStartDT, oEndDT);

        // Расчет сумм по вашей логике
        BigDecimal totalOrdersSum = allOrdersForPeriod.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED && o.getType() != OrderType.WRITE_OFF)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rawSales = totalOrdersSum;

        BigDecimal rawPurchaseCost = allOrdersForPeriod.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalPurchaseCost() != null ? o.getTotalPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 3. ЛОГИКА ДЛЯ ВОЗВРАТОВ (Полная) ---
        List<ReturnOrder> allReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? returnOrderRepository.findReturnsByManagerAndDateRange(returnManagerId, rStartDT, rEndDT)
                : returnOrderRepository.findReturnsBetweenDates(rStartDT, rEndDT);

        BigDecimal totalReturnsSum = allReturns.stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Чистая прибыль
        BigDecimal netProfitBD = rawSales.subtract(rawPurchaseCost)
                .subtract(totalReturnsSum)
                .setScale(0, RoundingMode.HALF_UP);

        // --- 4. ОБЩАЯ СТАТИСТИКА И СЧЕТА ---
        List<Invoice> invoices = Optional.ofNullable(invoiceRepository.findAllByOrderByCreatedAtDesc()).orElse(new ArrayList<>());

        BigDecimal totalInvoiceDebt = invoices.stream()
                .filter(Objects::nonNull)
                .map(i -> {
                    BigDecimal total = (i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO);
                    BigDecimal paid = (i.getPaidAmount() != null ? i.getPaidAmount() : BigDecimal.ZERO);
                    return total.subtract(paid);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaidSum = invoices.stream()
                .filter(i -> i != null && i.getPaidAmount() != null)
                .map(Invoice::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Средний чек
        long activeOrdersCount = allOrdersForPeriod.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).count();
        BigDecimal avgCheck = (activeOrdersCount == 0) ? BigDecimal.ZERO :
                rawSales.divide(BigDecimal.valueOf(activeOrdersCount), 2, RoundingMode.HALF_UP);

        List<AuditLog> limitedLogs = auditLogRepository.findAllByOrderByTimestampDesc().stream().limit(15).toList();

        // --- 5. ЛОГИКА KPI МЕНЕДЖЕРОВ (Полная) ---
        List<String> managersForUI = ManagerId.getAllDisplayNames();
        Map<String, ManagerKpiDTO> managerStats = new HashMap<>();
        LocalDate now = LocalDate.now();
        Month currentMonth = now.getMonth();
        Year currentYear = Year.of(now.getYear());

        for (String mName : managersForUI) {
            BigDecimal mSales = orderRepository.findByManagerId(mName).stream()
                    .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal mReturns = returnOrderRepository.findByManagerId(mName).stream()
                    .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                    .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ManagerKpiDTO dto = new ManagerKpiDTO(mSales, mReturns);
            ManagerTarget target = managerTargetRepository.findByManagerIdAndMonthAndYear(mName, currentMonth, currentYear);
            dto.setTargetAmount(target != null ? target.getTargetAmount() : BigDecimal.ZERO);
            managerStats.put(mName, dto);
        }

        // --- 6. ПЕРЕДАЧА В МОДЕЛЬ  ---
        addModel(page, orderManagerId, returnManagerId, model, ordersPage, totalOrdersSum, rawSales, rawPurchaseCost, netProfitBD, avgCheck, limitedLogs, invoices, totalInvoiceDebt, totalPaidSum, startD, endD, allReturns, totalReturnsSum, startR, endR);

        // Склад и Группировка
        groupAndWarehouse(activeTab, model, managersForUI, managerStats, invoices);

        return "dashboard";
    }

    private static void addModel(int page, String orderManagerId, String returnManagerId, Model model, Page<Order> ordersPage, BigDecimal totalOrdersSum, BigDecimal rawSales, BigDecimal rawPurchaseCost, BigDecimal netProfitBD, BigDecimal avgCheck, List<AuditLog> limitedLogs, List<Invoice> invoices, BigDecimal totalInvoiceDebt, BigDecimal totalPaidSum, LocalDate startD, LocalDate endD, List<ReturnOrder> allReturns, BigDecimal totalReturnsSum, LocalDate startR, LocalDate endR) {
        model.addAttribute("orders", ordersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        model.addAttribute("totalOrdersCount", ordersPage.getTotalElements());

        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("totalSales", rawSales.setScale(0, RoundingMode.HALF_UP));
        model.addAttribute("totalPurchaseCost", rawPurchaseCost.setScale(0, RoundingMode.HALF_UP));
        model.addAttribute("netProfit", netProfitBD);
        model.addAttribute("avgCheck", avgCheck);
        model.addAttribute("auditLogs", limitedLogs);

        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);
        model.addAttribute("totalPaidSum", totalPaidSum);

        model.addAttribute("orderStartDate", startD.toString());
        model.addAttribute("orderEndDate", endD.toString());
        model.addAttribute("selectedOrderManager", orderManagerId);

        model.addAttribute("returns", allReturns);
        model.addAttribute("totalReturnsCount", allReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("returnStartDate", startR.toString());
        model.addAttribute("returnEndDate", endR.toString());
        model.addAttribute("selectedReturnManager", returnManagerId);
    }

    private void groupAndWarehouse(String activeTab, Model model, List<String> managersForUI, Map<String, ManagerKpiDTO> managerStats, List<Invoice> invoices) {
        List<Product> activeProducts = Optional.ofNullable(productRepository.findAllActive()).orElse(new ArrayList<>());
        Map<String, List<Product>> groupedProducts = activeProducts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        p -> (p.getCategory() == null || p.getCategory().isBlank()) ? "Без категории" : p.getCategory(),
                        TreeMap::new, Collectors.toList()));

        model.addAttribute("groupedProducts", groupedProducts);
        model.addAttribute("products", activeProducts);

        List<Client> activeClients = Optional.ofNullable(clientRepository.findAllActive()).orElse(new ArrayList<>());
        model.addAttribute("clients", activeClients);
        model.addAttribute("users", Optional.ofNullable(userRepository.findAll()).orElse(new ArrayList<>()));
        model.addAttribute("managers", managersForUI);
        model.addAttribute("managersKPI", managersForUI);
        model.addAttribute("managerStats", managerStats);

        // Логика просрочки
        LocalDateTime limitDate = LocalDateTime.now().minusDays(30);
        Set<String> overdueClients = invoices.stream()
                .filter(inv -> inv != null && !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(limitDate))
                .map(Invoice::getShopName).filter(Objects::nonNull).collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        // Карта долгов для JS
        Map<String, BigDecimal> clientDebts = activeClients.stream()
                .filter(c -> c != null && c.getName() != null)
                .collect(Collectors.toMap(Client::getName, c -> c.getDebt() != null ? c.getDebt() : BigDecimal.ZERO, (ex, rep) -> ex));
        model.addAttribute("clientDebts", clientDebts);

        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);
    }
}
