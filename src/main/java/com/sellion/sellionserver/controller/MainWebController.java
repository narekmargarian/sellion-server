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

        // --- 1. ЛОГИКА ДАТ (ЗАКАЗЫ И ВОЗВРАТЫ - БЕЗ ИЗМЕНЕНИЙ) ---

        LocalDateTime oStartDT = parseSafeDateTime(orderStartDate, LocalDateTime.now().with(LocalTime.MIN));
        LocalDateTime oEndDT = parseSafeDateTime(orderEndDate, LocalDateTime.now().with(LocalTime.MAX));


        LocalDateTime rStartDT = parseSafeDateTime(returnStartDate, LocalDateTime.now().with(LocalTime.MIN));
        LocalDateTime rEndDT = parseSafeDateTime(returnEndDate, LocalDateTime.now().with(LocalTime.MAX));

        // Используем тот же метод parseSafeDateTime
        LocalDateTime invStartDT = parseSafeDateTime(invoiceStart, LocalDateTime.now().withDayOfMonth(1).with(LocalTime.MIN));
        LocalDateTime invEndDT = parseSafeDateTime(invoiceEnd, LocalDateTime.now().with(LocalTime.MAX));

// Передаем отформатированные строки обратно в модель для инпутов
        java.time.format.DateTimeFormatter html5PickerFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

// Передаем именно в этом формате!
        model.addAttribute("orderStartDate", oStartDT.format(html5PickerFormat));
        model.addAttribute("orderEndDate", oEndDT.format(html5PickerFormat));

        model.addAttribute("returnStartDate", rStartDT.format(html5PickerFormat));
        model.addAttribute("returnEndDate", rEndDT.format(html5PickerFormat));

// Для инвойсов (если там тоже input type="date")
        model.addAttribute("invoiceStart", invStartDT.format(html5PickerFormat));
        model.addAttribute("invoiceEnd", invEndDT.format(html5PickerFormat));

        // --- 2. ЛОГИКА ДЛЯ ЗАКАЗОВ ---
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> ordersPage;
        if (orderManagerId != null && !orderManagerId.isEmpty()) {
            ordersPage = orderRepository.findOrdersByManagerAndDateRangePaged(orderManagerId, oStartDT, oEndDT, pageable);
        } else {
            ordersPage = orderRepository.findOrdersBetweenDatesPaged(oStartDT, oEndDT, pageable);
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
        List<ReturnOrder> allReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? returnOrderRepository.findReturnsByManagerAndDateRange(returnManagerId, rStartDT, rEndDT)
                : returnOrderRepository.findReturnsBetweenDates(rStartDT, rEndDT);

        BigDecimal totalReturnsSum = allReturns.stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitBD = rawSales.subtract(rawPurchaseCost).subtract(totalReturnsSum)
                .setScale(1, RoundingMode.HALF_UP);

        // --- 4. ОБЩАЯ СТАТИСТИКА И СЧЕТА (ПАГИНАЦИЯ + ФИЛЬТРЫ) ---
        BigDecimal totalInvoiceDebt = Optional.ofNullable(invoiceRepository.calculateTotalDebt()).orElse(BigDecimal.ZERO);
        BigDecimal totalPaidSum = Optional.ofNullable(invoiceRepository.calculateTotalPaid()).orElse(BigDecimal.ZERO);

        // Подготовка параметров для фильтра
        String managerParam = (invoiceManager != null && !invoiceManager.isEmpty()) ? invoiceManager : null;
        String statusParam = (invoiceStatus != null && !invoiceStatus.isEmpty()) ? invoiceStatus : null;

        Pageable invPageable = PageRequest.of(invoicePage, 15, Sort.by("createdAt").descending());

        // ВАЖНО: Загружаем только один раз с учетом ВСЕХ фильтров
        Page<Invoice> invoicesPage = invoiceRepository.findFilteredInvoices(
                invStartDT, invEndDT, managerParam, statusParam, invPageable);

        List<Invoice> invoicesList = invoicesPage.getContent();

        long activeOrdersCount = allOrdersForPeriod.size();
        BigDecimal avgCheck = (activeOrdersCount == 0) ? BigDecimal.ZERO : rawSales.divide(BigDecimal.valueOf(activeOrdersCount), 2, RoundingMode.HALF_UP);
        List<AuditLog> limitedLogs = auditLogRepository.findTop50ByOrderByTimestampDesc();

        // --- 5. ЛОГИКА KPI МЕНЕДЖЕРОВ (ОПТИМИЗИРОВАНО) ---
        List<String> managersForUI = ManagerId.getAllDisplayNames();
        Map<String, ManagerKpiDTO> managerStats = new HashMap<>();
        LocalDate now = LocalDate.now();

        for (String mName : managersForUI) {
            // Используем уже существующий список allOrdersForPeriod вместо запроса к репозиторию
            BigDecimal mSales = allOrdersForPeriod.stream()
                    .filter(o -> mName.equals(o.getManagerId()) && o.getStatus() != OrderStatus.CANCELLED)
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Используем уже существующий список allReturns
            BigDecimal mReturns = allReturns.stream()
                    .filter(r -> mName.equals(r.getManagerId()) && r.getStatus() == ReturnStatus.CONFIRMED)
                    .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ManagerKpiDTO dto = new ManagerKpiDTO(mSales, mReturns);

            // Таргет оставляем из БД, так как это точечный запрос на конкретный месяц
            ManagerTarget target = managerTargetRepository.findByManagerIdAndMonthAndYear(
                    mName, now.getMonth(), Year.of(now.getYear()));

            dto.setTargetAmount(target != null ? target.getTargetAmount() : BigDecimal.ZERO);
            managerStats.put(mName, dto);
        }


        // Если даты не пришли в URL, ставим текущий месяц
        LocalDate pStart = parseSafeDateTime(promoStart, LocalDateTime.now().withDayOfMonth(1).with(LocalTime.MIN)).toLocalDate();
        LocalDate pEnd = parseSafeDateTime(promoEnd, LocalDateTime.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).with(LocalTime.MAX)).toLocalDate();

        // Отдаем акции именно за этот период
        model.addAttribute("promos", promoRepository.findByPeriod(pStart, pEnd));

        // ПРИНЦИПИАЛЬНО: Отдаем даты обратно, чтобы инпуты их не теряли
        model.addAttribute("promoStartDefault", pStart.toString());
        model.addAttribute("promoEndDefault", pEnd.toString());




        addModel(page, orderManagerId, returnManagerId, model, ordersPage, totalOrdersSum, rawSales, rawPurchaseCost, netProfitBD, avgCheck, limitedLogs, invoicesList, totalInvoiceDebt, totalPaidSum, oStartDT.toLocalDate(), oEndDT.toLocalDate(), allReturns, totalReturnsSum, rStartDT.toLocalDate(), rEndDT.toLocalDate());

        addInvModel(invoicePage, invoiceManager, invoiceStatus, model, invoicesList, invoicesPage, invStartDT.toLocalDate(), invEndDT.toLocalDate());
        groupAndWarehouse(activeTab, clientPage, clientCategory, clientSearch, model, managersForUI, managerStats, invoicesList);
        return "dashboard";

    }

    private static void addInvModel(int invoicePage, String invoiceManager, String invoiceStatus, Model model, List<Invoice> invoicesList, Page<Invoice> invoicesPage, LocalDate invStartD, LocalDate invEndD) {
        model.addAttribute("invoices", invoicesList);
        model.addAttribute("invCurrentPage", invoicePage);
        model.addAttribute("invTotalPages", invoicesPage.getTotalPages());

        // ИСПРАВЛЕНО: Только yyyy-MM-dd для корректной работы input type="date"
        java.time.format.DateTimeFormatter isoDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        model.addAttribute("invoiceStart", invStartD.format(isoDate));
        model.addAttribute("invoiceEnd", invEndD.format(isoDate));

        model.addAttribute("selectedInvManager", invoiceManager);
        model.addAttribute("selectedInvStatus", invoiceStatus);
    }



    private static void addModel(int page, String orderManagerId, String returnManagerId, Model model, Page<Order> ordersPage, BigDecimal totalOrdersSum, BigDecimal rawSales, BigDecimal rawPurchaseCost, BigDecimal netProfitBD, BigDecimal avgCheck, List<AuditLog> limitedLogs, List<Invoice> invoices, BigDecimal totalInvoiceDebt, BigDecimal totalPaidSum, LocalDate startD, LocalDate endD, List<ReturnOrder> allReturns, BigDecimal totalReturnsSum, LocalDate startR, LocalDate endR) {
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

        model.addAttribute("returns", allReturns);
        model.addAttribute("totalReturnsCount", allReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("selectedReturnManager", returnManagerId);

        java.time.format.DateTimeFormatter isoDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Передаем ЧИСТУЮ дату без времени и точек
        model.addAttribute("orderStartDate", startD.format(isoDate));
        model.addAttribute("orderEndDate", endD.format(isoDate));

        model.addAttribute("returnStartDate", startR.format(isoDate));
        model.addAttribute("returnEndDate", endR.format(isoDate));
    }



    private void groupAndWarehouse(String activeTab, int clientPage, String clientCategory,
                                   String clientSearch, Model model, List<String> managersForUI,
                                   Map<String, ManagerKpiDTO> managerStats, List<Invoice> invoices) {

        // 1. СКЛАД: Оптимизируем загрузку (дизайн и логику не меняем)
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

        // 2. КЛИЕНТЫ: Исправляем фильтр категорий и поиск
        int pageSize = 50;
        Pageable pageable = PageRequest.of(clientPage, pageSize, Sort.by("name").ascending());

        // Очищаем параметры (убираем лишние пробелы)
        String searchKeyword = (clientSearch != null && !clientSearch.trim().isEmpty()) ? clientSearch.trim() : null;
        String categoryFilter = (clientCategory != null && !clientCategory.trim().isEmpty()) ? clientCategory.trim() : null;

        Page<Client> clientsPage;
        // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: поиск вызывается если есть ХОТЯ БЫ один фильтр
        if (searchKeyword != null || categoryFilter != null) {
            clientsPage = clientRepository.searchClients(searchKeyword, categoryFilter, pageable);
        } else {
            clientsPage = clientRepository.findAllByIsDeletedFalse(pageable);
        }

        model.addAttribute("clients", clientsPage.getContent());
        model.addAttribute("clientCurrentPage", clientPage);
        model.addAttribute("clientTotalPages", clientsPage.getTotalPages());
        model.addAttribute("clientTotalElements", clientsPage.getTotalElements());

        // Передаем параметры обратно в форму поиска, чтобы они не сбрасывались
        model.addAttribute("selectedCategory", clientCategory);
        model.addAttribute("clientSearch", clientSearch);

        // ИСПРАВЛЕНИЕ: Вызываем список категорий клиентов, чтобы фильтр всегда был заполнен
        model.addAttribute("clientCategories", clientRepository.findUniqueCategories());

        // 3. ПЕРСОНАЛ И KPI
        model.addAttribute("users", Optional.ofNullable(userRepository.findAll()).orElse(new ArrayList<>()));
        model.addAttribute("managers", managersForUI);
        model.addAttribute("managersKPI", managersForUI);
        model.addAttribute("managerStats", managerStats);

        // 4. ЛОГИКА ПРОСРОЧКИ (БЕЗ ИЗМЕНЕНИЙ)
        LocalDateTime limitDate = LocalDateTime.now().minusDays(30);
        List<Invoice> allUnpaid = invoiceRepository.findAllByStatusNot("PAID");
        Set<String> overdueClients = allUnpaid.stream()
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(limitDate))
                .map(Invoice::getShopName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        // 5. КАРТА ДОЛГОВ ДЛЯ JS (Исправлено balance -> debt)
        // Теперь JS не будет выдавать ошибки при поиске долга клиента
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


    private LocalDateTime parseSafeDateTime(String d, LocalDateTime def) {
        if (d == null || d.trim().isEmpty()) return def;
        try {
            // 1. ПОДДЕРЖКА ФОРМАТА БРАУЗЕРА (2026-04-08)
            // Если есть дефисы и нет точек — это стандартный HTML5 input date
            if (d.contains("-") && !d.contains(".")) {
                // Если в строке есть время (содержит T или пробел)
                if (d.contains("T") || d.contains(" ")) {
                    return LocalDateTime.parse(d.replace(" ", "T"));
                }
                // Если это просто дата 2026-04-08
                return LocalDate.parse(d).atStartOfDay();
            }

            // 2. ПОДДЕРЖКА ФОРМАТА С ТОЧКАМИ (08.04.2026 21:09)
            if (d.contains(".")) {
                java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy[ HH:mm[:ss]]");

                // Если в строке нет времени, parse выбросит ошибку, поймаем её ниже
                if (!d.contains(" ")) {
                    return LocalDate.parse(d, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay();
                }
                return LocalDateTime.parse(d, formatter);
            }

            // 3. УНИВЕРСАЛЬНЫЙ ISO ПАРСИНГ
            return LocalDateTime.parse(d.replace(" ", "T"));

        } catch (Exception e) {
            // Если ничего не подошло, возвращаем значение по умолчанию
            return def;
        }
    }

}
