package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.ManagerKpiDTO;
import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
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
    private final AuditLogRepository auditLogRepository;
    private final ManagerTargetRepository managerTargetRepository;
    private final PromoActionRepository promoRepository;

    @GetMapping
    public String showDashboard(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "clientPage", defaultValue = "0") int clientPage,
            @RequestParam(value = "clientCategory", required = false) String clientCategory,
            @RequestParam(value = "clientSearch", required = false) String clientSearch,

            // ПАРАМЕТРЫ ДЛЯ ПОИСКА ЗАКАЗОВ И ВОЗВРАТОВ
            @RequestParam(value = "orderSearch", required = false) String orderSearch,
            @RequestParam(value = "returnSearch", required = false) String returnSearch,

            // НОВЫЕ ПАРАМЕТРЫ ДЛЯ ИНВОЙСОВ
            @RequestParam(value = "invoicePage", defaultValue = "0") int invoicePage,
            @RequestParam(value = "invoiceStart", required = false) String invoiceStart,
            @RequestParam(value = "invoiceEnd", required = false) String invoiceEnd,
            @RequestParam(value = "invoiceManager", required = false) String invoiceManager,
            @RequestParam(value = "invoiceStatus", required = false) String invoiceStatus,

            @RequestParam(value = "promoStart", required = false) String promoStart,
            @RequestParam(value = "promoEnd", required = false) String promoEnd,

            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "orderStartDate", required = false) String orderStartDate,
            @RequestParam(value = "orderEndDate", required = false) String orderEndDate,
            @RequestParam(value = "returnStartDate", required = false) String returnStartDate,
            @RequestParam(value = "returnEndDate", required = false) String returnEndDate,
            @RequestParam(value = "activeTab", required = false, defaultValue = "tab-orders") String activeTab,
            Model model) {

        // --- 1. ЛОГИКА ДАТ (ЗАКАЗЫ И ВОЗВРАТЫ) ---

        // Заказы
        LocalDateTime oStartDT = parseSafeDateTime(orderStartDate, LocalDateTime.now().with(LocalTime.MIN), false);
        LocalDateTime oEndDT = parseSafeDateTime(orderEndDate, LocalDateTime.now().with(LocalTime.MAX), true);

        // Возвраты
        LocalDateTime rStartDT = parseSafeDateTime(returnStartDate, LocalDateTime.now().with(LocalTime.MIN), false);
        LocalDateTime rEndDT = parseSafeDateTime(returnEndDate, LocalDateTime.now().with(LocalTime.MAX), true);

        // Инвойсы
        LocalDateTime invStartDT = parseSafeDateTime(invoiceStart, LocalDateTime.now().withDayOfMonth(1).with(LocalTime.MIN), false);
        LocalDateTime invEndDT = parseSafeDateTime(invoiceEnd, LocalDateTime.now().with(LocalTime.MAX), true);

        java.time.format.DateTimeFormatter html5PickerFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        model.addAttribute("orderStartDate", oStartDT.format(html5PickerFormat));
        model.addAttribute("orderEndDate", oEndDT.format(html5PickerFormat));

        model.addAttribute("returnStartDate", rStartDT.format(html5PickerFormat));
        model.addAttribute("returnEndDate", rEndDT.format(html5PickerFormat));

        model.addAttribute("invoiceStart", invStartDT.format(html5PickerFormat));
        model.addAttribute("invoiceEnd", invEndDT.format(html5PickerFormat));

        // --- 2. ЛОГИКА ДЛЯ ЗАКАЗОВ ---
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> ordersPage;
        if (orderManagerId != null && !orderManagerId.isEmpty()) {
            ordersPage = orderRepository.findOrdersByManagerWithSearchPaged(orderManagerId, oStartDT, oEndDT, orderSearch, pageable);
        } else {
            ordersPage = orderRepository.findOrdersWithSearchPaged(oStartDT, oEndDT, orderSearch, pageable);
        }

        List<Order> allOrdersForPeriod = (orderManagerId != null && !orderManagerId.isEmpty())
                ? orderRepository.findOrdersByManagerAndDateRange(orderManagerId, oStartDT, oEndDT)
                : orderRepository.findOrdersBetweenDates(oStartDT, oEndDT);

        BigDecimal totalOrdersSum = allOrdersForPeriod.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);

        BigDecimal rawSales = totalOrdersSum;
        BigDecimal rawPurchaseCost = allOrdersForPeriod.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalPurchaseCost() != null ? o.getTotalPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 3. ЛОГИКА ДЛЯ ВОЗВРАТОВ ---
        Page<ReturnOrder> returnsPage;
        if (returnManagerId != null && !returnManagerId.isEmpty()) {
            returnsPage = returnOrderRepository.findReturnsByManagerWithSearchPaged(returnManagerId, rStartDT, rEndDT, returnSearch, pageable);
        } else {
            returnsPage = returnOrderRepository.findReturnsBetweenDatesWithSearchPaged(rStartDT, rEndDT, returnSearch, pageable);
        }

        List<ReturnOrder> allReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? returnOrderRepository.findReturnsByManagerAndDateRange(returnManagerId, rStartDT, rEndDT)
                : returnOrderRepository.findReturnsBetweenDates(rStartDT, rEndDT);

        BigDecimal totalReturnsSum = allReturns.stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitBD = rawSales.subtract(rawPurchaseCost).subtract(totalReturnsSum)
                .setScale(1, RoundingMode.HALF_UP);

        // --- 4. ОБЩАЯ СТАТИСТИКА И СЧЕТА ---
        BigDecimal totalPaidSum = Optional.ofNullable(invoiceRepository.calculateTotalPaid()).orElse(BigDecimal.ZERO);

        // Считаем ГЛОБАЛЬНЫЙ общий долг всей системы (абсолютно по всем инвойсам и возвратам без фильтров дат)
        BigDecimal globalSystemTotalAmount = invoiceRepository.findAll().stream()
                .map(inv -> inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal globalSystemPaidAmount = invoiceRepository.findAll().stream()
                .map(inv -> inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal globalSystemReturnsSum = returnOrderRepository.findAll().stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Итоговый общий долг всей системы с учетом возвратов
        BigDecimal totalSystemDebt = globalSystemTotalAmount
                .subtract(globalSystemPaidAmount)
                .subtract(globalSystemReturnsSum)
                .setScale(2, RoundingMode.HALF_UP);

        // Подготовка параметров для фильтра инвойсов
        String managerParam = (invoiceManager != null && !invoiceManager.isEmpty()) ? invoiceManager : null;
        String statusParam = (invoiceStatus != null && !invoiceStatus.isEmpty()) ? invoiceStatus : null;

        Pageable invPageable = PageRequest.of(invoicePage, 100, Sort.by("createdAt").descending());

        Page<Invoice> invoicesPage = invoiceRepository.findFilteredInvoices(
                invStartDT, invEndDT, managerParam, statusParam, invPageable);

        List<Invoice> invoicesList = invoicesPage.getContent();

        List<ReturnOrder> allConfirmedReturns = returnOrderRepository.findAll().stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .collect(Collectors.toList());

        Map<String, BigDecimal> returnsByShop = allConfirmedReturns.stream()
                .filter(r -> r.getShopName() != null)
                .collect(Collectors.groupingBy(
                        ReturnOrder::getShopName,
                        Collectors.mapping(
                                r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));

        for (Invoice inv : invoicesList) {
            if (inv.getShopName() != null && returnsByShop.containsKey(inv.getShopName())) {
                BigDecimal shopReturns = returnsByShop.get(inv.getShopName());
                if (shopReturns.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal currentTotal = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal newTotal = currentTotal.subtract(shopReturns);
                    if (newTotal.compareTo(BigDecimal.ZERO) < 0) newTotal = BigDecimal.ZERO;
                    inv.setTotalAmount(newTotal);
                }
            }
        }

        List<Invoice> allInvoicesForPeriod = invoiceRepository.findAllFilteredInvoicesNoPage(
                invStartDT, invEndDT, managerParam, statusParam);

        BigDecimal periodTotalAmount = allInvoicesForPeriod.stream()
                .map(inv -> inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal periodPaidAmount = allInvoicesForPeriod.stream()
                .map(inv -> inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal periodReturnsSum = returnOrderRepository.findReturnsBetweenDates(invStartDT, invEndDT).stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Чистый долг выбранного периода для карточки периода
        BigDecimal periodInvoiceDebt = periodTotalAmount.subtract(periodPaidAmount).subtract(periodReturnsSum).setScale(2, RoundingMode.HALF_UP);

        long activeOrdersCount = allOrdersForPeriod.size();
        BigDecimal avgCheck = (activeOrdersCount == 0) ? BigDecimal.ZERO : rawSales.divide(BigDecimal.valueOf(activeOrdersCount), 2, RoundingMode.HALF_UP);
        List<AuditLog> limitedLogs = auditLogRepository.findTop50ByOrderByTimestampDesc();

        // --- 5. ЛОГИКА KPI МЕНЕДЖЕРОВ ---
        List<String> managersForUI = ManagerId.getAllDisplayNames();
        Map<String, ManagerKpiDTO> managerStats = new HashMap<>();
        LocalDate now = LocalDate.now();

        for (String mName : managersForUI) {
            BigDecimal mSales = allOrdersForPeriod.stream()
                    .filter(o -> mName.equals(o.getManagerId()) && o.getStatus() != OrderStatus.CANCELLED)
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal mReturns = allReturns.stream()
                    .filter(r -> mName.equals(r.getManagerId()) && r.getStatus() == ReturnStatus.CONFIRMED)
                    .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ManagerKpiDTO dto = new ManagerKpiDTO(mSales, mReturns);

            ManagerTarget target = managerTargetRepository.findByManagerIdAndMonthAndYear(
                    mName, now.getMonth(), Year.of(now.getYear()));

            dto.setTargetAmount(target != null ? target.getTargetAmount() : BigDecimal.ZERO);
            managerStats.put(mName, dto);
        }

        LocalDate defaultStart = LocalDate.now().withDayOfMonth(1);
        LocalDate defaultEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        LocalDate pStart = (promoStart != null && !promoStart.isEmpty())
                ? parseSafeDateTime(promoStart, defaultStart.atStartOfDay(), false).toLocalDate()
                : defaultStart;

        LocalDate pEnd = (promoEnd != null && !promoEnd.isEmpty())
                ? parseSafeDateTime(promoEnd, defaultEnd.atTime(LocalTime.MAX), true).toLocalDate()
                : defaultEnd;

        model.addAttribute("promos", promoRepository.findByPeriod(pStart, pEnd));
        model.addAttribute("promoStart", pStart.toString());
        model.addAttribute("promoEnd", pEnd.toString());
        model.addAttribute("promoStartDefault", pStart.toString());
        model.addAttribute("promoEndDefault", pEnd.toString());

        List<Long> allOrderIds = allOrdersForPeriod.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Long> allReturnIds = allReturns.stream()
                .map(ReturnOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        model.addAttribute("allOrderIds", allOrderIds);
        model.addAttribute("allReturnIds", allReturnIds);

        addModel(page, orderManagerId, returnManagerId, model,
                ordersPage, returnsPage, totalOrdersSum,
                rawSales, rawPurchaseCost, netProfitBD, avgCheck, limitedLogs, invoicesList,
                totalSystemDebt,
                totalPaidSum,
                oStartDT.toLocalDate(), oEndDT.toLocalDate(), allReturns, totalReturnsSum, rStartDT.toLocalDate(), rEndDT.toLocalDate(),
                orderSearch, returnSearch);

        addInvModel(invoicePage, invoiceManager, invoiceStatus, model, invoicesList, invoicesPage, invStartDT.toLocalDate(), invEndDT.toLocalDate(), periodInvoiceDebt);
        groupAndWarehouse(activeTab, clientPage, clientCategory, clientSearch, model, managersForUI, managerStats, invoicesList);
        return "dashboard";
    }

    private static void addInvModel(int invoicePage, String invoiceManager, String invoiceStatus, Model model,
                                    List<Invoice> invoicesList, Page<Invoice> invoicesPage,
                                    LocalDate invStartD, LocalDate invEndD, BigDecimal periodInvoiceDebt) {
        model.addAttribute("invoices", invoicesList);
        model.addAttribute("invCurrentPage", invoicePage);
        model.addAttribute("invTotalPages", invoicesPage.getTotalPages());

        model.addAttribute("periodInvoiceDebt", periodInvoiceDebt);

        java.time.format.DateTimeFormatter isoDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        model.addAttribute("invoiceStart", invStartD.format(isoDate));
        model.addAttribute("invoiceEnd", invEndD.format(isoDate));

        model.addAttribute("selectedInvManager", invoiceManager);
        model.addAttribute("selectedInvStatus", invoiceStatus);
    }

    private static void addModel(int page, String orderManagerId, String returnManagerId, Model model,
                                 Page<Order> ordersPage, Page<ReturnOrder> returnsPage,
                                 BigDecimal totalOrdersSum, BigDecimal rawSales, BigDecimal rawPurchaseCost,
                                 BigDecimal netProfitBD, BigDecimal avgCheck, List<AuditLog> limitedLogs,
                                 List<Invoice> invoices, BigDecimal totalInvoiceDebt, BigDecimal totalPaidSum,
                                 LocalDate startD, LocalDate endD, List<ReturnOrder> allReturnsForCalculation,
                                 BigDecimal totalReturnsSum, LocalDate startR, LocalDate endR,
                                 String orderSearch, String returnSearch) {

        model.addAttribute("orders", ordersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        model.addAttribute("totalOrdersCount", ordersPage.getTotalElements());

        model.addAttribute("totalOrdersSum", totalOrdersSum);
        model.addAttribute("totalSales", rawSales.setScale(1, RoundingMode.HALF_UP));
        model.addAttribute("totalPurchaseCost", rawPurchaseCost.setScale(1, RoundingMode.HALF_UP));
        model.addAttribute("netProfit", netProfitBD);
        model.addAttribute("avgCheck", avgCheck.setScale(1, RoundingMode.HALF_UP));
        model.addAttribute("auditLogs", limitedLogs);

        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);
        model.addAttribute("totalPaidSum", totalPaidSum);

        model.addAttribute("selectedOrderManager", orderManagerId);

        model.addAttribute("returns", returnsPage.getContent());
        model.addAttribute("totalReturnsCount", allReturnsForCalculation.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("selectedReturnManager", returnManagerId);

        model.addAttribute("orderSearch", orderSearch);
        model.addAttribute("returnSearch", returnSearch);

        java.time.format.DateTimeFormatter isoDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        model.addAttribute("orderStartDate", startD.format(isoDate));
        model.addAttribute("orderEndDate", endD.format(isoDate));

        model.addAttribute("returnStartDate", startR.format(isoDate));
        model.addAttribute("returnEndDate", endR.format(isoDate));
    }

    private void groupAndWarehouse(String activeTab, int clientPage, String clientCategory,
                                   String clientSearch, Model model, List<String> managersForUI,
                                   Map<String, ManagerKpiDTO> managerStats, List<Invoice> invoices) {

        List<Product> activeProducts = Optional.ofNullable(productRepository.findAllByIsDeletedFalse()).orElse(new ArrayList<>());
        Map<String, List<Product>> groupedProducts = activeProducts.stream()
                .filter(Objects::nonNull)
                .peek(p -> {
                    if (p.getCategory() == null || p.getCategory().isBlank()) p.setCategory("Без категории");
                })
                .collect(Collectors.groupingBy(
                        Product::getCategory,
                        TreeMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            list.sort(Comparator.comparing(Product::getName));
                            return list;
                        })
                ));

        model.addAttribute("groupedProducts", groupedProducts);
        model.addAttribute("products", activeProducts);

        int pageSize = 100;
        Pageable pageable = PageRequest.of(clientPage, pageSize, Sort.by("name").ascending());

        String searchKeyword = (clientSearch != null && !clientSearch.trim().isEmpty()) ? clientSearch.trim() : null;
        String categoryFilter = (clientCategory != null && !clientCategory.trim().isEmpty()) ? clientCategory.trim() : null;

        Page<Client> clientsPage;
        if (searchKeyword != null || categoryFilter != null) {
            clientsPage = clientRepository.searchClients(searchKeyword, categoryFilter, pageable);
        } else {
            clientsPage = clientRepository.findAllByIsDeletedFalse(pageable);
        }

        model.addAttribute("clients", clientsPage.getContent());
        model.addAttribute("clientCurrentPage", clientPage);
        model.addAttribute("clientTotalPages", clientsPage.getTotalPages());
        model.addAttribute("clientTotalElements", clientsPage.getTotalElements());

        model.addAttribute("selectedCategory", clientCategory);
        model.addAttribute("clientSearch", clientSearch);

        model.addAttribute("clientCategories", clientRepository.findUniqueCategories());

        model.addAttribute("users", Optional.ofNullable(userRepository.findAll()).orElse(new ArrayList<>()));
        model.addAttribute("managers", managersForUI);
        model.addAttribute("managersKPI", managersForUI);
        model.addAttribute("managerStats", managerStats);

        LocalDateTime limitDate = LocalDateTime.now().minusDays(30);
        List<Invoice> allUnpaid = invoiceRepository.findAllByStatusNot("PAID");
        Set<String> overdueClients = allUnpaid.stream()
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(limitDate))
                .map(Invoice::getShopName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        List<Client> allActiveForMap = clientRepository.findAllByIsDeletedFalse();
        Map<String, BigDecimal> clientDebts = allActiveForMap.stream()
                .filter(c -> c != null && c.getName() != null)
                .collect(Collectors.toMap(
                        Client::getName,
                        c -> c.getDebt() != null ? c.getDebt() : BigDecimal.ZERO,
                        (existing, replacement) -> existing
                ));

        model.addAttribute("clientDebts", clientDebts);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);
    }

    private LocalDateTime parseSafeDateTime(String d, LocalDateTime def, boolean isEnd) {
        if (d == null || d.trim().isEmpty()) return def;
        try {
            LocalDateTime dt;
            if (d.contains("-") && !d.contains(".")) {
                if (d.contains("T") || d.contains(" ")) {
                    dt = LocalDateTime.parse(d.replace(" ", "T"));
                } else {
                    dt = LocalDate.parse(d).atStartOfDay();
                }
            } else if (d.contains(".")) {
                if (d.contains(":")) {
                    dt = LocalDateTime.parse(d, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm[:ss]"));
                } else {
                    dt = LocalDate.parse(d, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay();
                }
            } else {
                dt = LocalDateTime.parse(d.replace(" ", "T"));
            }

            if (isEnd && dt.toLocalTime().equals(LocalTime.MIN)) {
                return dt.with(LocalTime.MAX);
            }
            return dt;
        } catch (Exception e) {
            return def;
        }
    }
}