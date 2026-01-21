package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.ManagerKpiDTO;
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
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "orderStartDate", required = false) String orderStartDate,
            @RequestParam(value = "orderEndDate", required = false) String orderEndDate,
            @RequestParam(value = "returnStartDate", required = false) String returnStartDate,
            @RequestParam(value = "returnEndDate", required = false) String returnEndDate,
            @RequestParam(value = "activeTab", required = false, defaultValue = "tab-orders") String activeTab,
            Model model) {

        String today = LocalDate.now().toString();

        // --- 1. ЛОГИКА ДЛЯ ЗАКАЗОВ (Исправлено для LocalDateTime) ---
        LocalDate startD = (orderStartDate != null && !orderStartDate.isEmpty()) ? LocalDate.parse(orderStartDate) : LocalDate.now();
        LocalDate endD = (orderEndDate != null && !orderEndDate.isEmpty()) ? LocalDate.parse(orderEndDate) : startD;

        LocalDateTime oStartDT = startD.atStartOfDay();
        LocalDateTime oEndDT = endD.atTime(LocalTime.MAX);

// Теперь передаем объекты LocalDateTime, а не строки
        List<Order> allOrders = Optional.ofNullable(orderRepository.findOrdersBetweenDates(oStartDT, oEndDT))
                .orElse(new ArrayList<>());

        // Фильтруем по менеджеру, если выбран
        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> o != null && orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        // Расчет общей суммы (включая все кроме отмененных)
        BigDecimal totalOrdersSum = filteredOrders.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Расчет выручки и себестоимости (одним проходом)
        BigDecimal rawSales = totalOrdersSum;
        BigDecimal rawPurchaseCost = filteredOrders.stream()
                .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalPurchaseCost() != null ? o.getTotalPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate startR = (returnStartDate != null && !returnStartDate.isEmpty()) ? LocalDate.parse(returnStartDate) : LocalDate.now();
        LocalDate endR = (returnEndDate != null && !returnEndDate.isEmpty()) ? LocalDate.parse(returnEndDate) : startR;

        LocalDateTime rStartDT = startR.atStartOfDay();
        LocalDateTime rEndDT = endR.atTime(LocalTime.MAX);

        List<ReturnOrder> allReturns = Optional.ofNullable(returnOrderRepository.findReturnsBetweenDates(rStartDT, rEndDT))
                .orElse(new ArrayList<>());

        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> r != null && returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        // Сумма подтвержденных возвратов (влияет на прибыль)
        BigDecimal totalReturnsSum = filteredReturns.stream()
                .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- РАСЧЕТ ЧИСТОЙ ПРИБЫЛИ (2026: Выручка - Себестоимость - Возвраты) ---
        BigDecimal netProfitBD = rawSales.subtract(rawPurchaseCost)
                .subtract(totalReturnsSum)
                .setScale(0, RoundingMode.HALF_UP);

        // --- 3. ОБЩАЯ СТАТИСТИКА И СЧЕТА ---
        List<Invoice> invoices = Optional.ofNullable(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .orElse(new ArrayList<>());

        // Расчет дебиторской задолженности (Сумма всех счетов - Оплачено)
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

        model.addAttribute("invoices", invoices);
        model.addAttribute("totalInvoiceDebt", totalInvoiceDebt);
        model.addAttribute("totalPaidSum", totalPaidSum);

        // Средний чек по активным заказам (защита от /0)
        long activeOrdersCount = allOrders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).count();
        BigDecimal avgCheck = (activeOrdersCount == 0) ? BigDecimal.ZERO :
                rawSales.divide(BigDecimal.valueOf(activeOrdersCount), 2, RoundingMode.HALF_UP);

        // Логи аудита (лимит 15 записей)
        List<AuditLog> limitedLogs = Optional.ofNullable(auditLogRepository.findAllByOrderByTimestampDesc())
                .orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .limit(15)
                .toList();


        // --- 4. ЛОГИКА KPI МЕНЕДЖЕРОВ ---
        List<String> managersForUI = ManagerId.getAllDisplayNames();
        Map<String, ManagerKpiDTO> managerStats = new HashMap<>();

        // Определяем текущий период для планов (Январь 2026)
        LocalDate now = LocalDate.now();
        Month currentMonth = now.getMonth();
        Year currentYear = Year.of(now.getYear());

        for (String mName : managersForUI) {
            // 1. Считаем продажи (Берем только подтвержденные или созданные счета)
            BigDecimal salesSum = orderRepository.findByManagerId(mName).stream()
                    .filter(o -> o != null && o.getStatus() != OrderStatus.CANCELLED)
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 2. Считаем возвраты (Только фактически проведенные)
            BigDecimal returnsSum = returnOrderRepository.findByManagerId(mName).stream()
                    .filter(r -> r != null && r.getStatus() == ReturnStatus.CONFIRMED)
                    .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 3. Создаем DTO (Логика КПД внутри конструктора DTO)
            ManagerKpiDTO dto = new ManagerKpiDTO(salesSum, returnsSum);

            // 4. Установка цели (плана) из БД
            ManagerTarget target = managerTargetRepository.findByManagerIdAndMonthAndYear(mName, currentMonth, currentYear);
            if (target != null) {
                dto.setTargetAmount(target.getTargetAmount());
            } else {
                dto.setTargetAmount(BigDecimal.ZERO);
            }

            managerStats.put(mName, dto);
        }

        model.addAttribute("managersKPI", managersForUI);
        model.addAttribute("managerStats", managerStats);

        // --- 5. ПЕРЕДАЧА СТАТИСТИКИ В МОДЕЛЬ (UI 2026) ---
        model.addAttribute("totalOrdersSum", totalOrdersSum != null ? totalOrdersSum : BigDecimal.ZERO);
        model.addAttribute("totalSales", rawSales != null ? rawSales.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        model.addAttribute("totalPurchaseCost", rawPurchaseCost != null ? rawPurchaseCost.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        model.addAttribute("netProfit", netProfitBD != null ? netProfitBD : BigDecimal.ZERO);
        model.addAttribute("avgCheck", avgCheck != null ? avgCheck : BigDecimal.ZERO);
        model.addAttribute("auditLogs", limitedLogs);

        // Данные таблиц
        model.addAttribute("orders", filteredOrders);
        model.addAttribute("totalOrdersCount", filteredOrders.size());
        model.addAttribute("orderStartDate", startD.toString());
        model.addAttribute("orderEndDate", endD.toString());
        model.addAttribute("selectedOrderManager", orderManagerId);

        model.addAttribute("returns", filteredReturns);
        model.addAttribute("totalReturnsCount", filteredReturns.size());
        model.addAttribute("totalReturnsSum", totalReturnsSum);
        model.addAttribute("returnStartDate", startR.toString());
        model.addAttribute("returnEndDate", endR.toString());
        model.addAttribute("selectedReturnManager", returnManagerId);

        // --- 6. СКЛАД И КЛИЕНТЫ ---
        List<Product> activeProducts = Optional.ofNullable(productRepository.findAllActive()).orElse(new ArrayList<>());
        Map<String, List<Product>> groupedProducts = activeProducts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        p -> (p.getCategory() == null || p.getCategory().isBlank()) ? "Без категории" : p.getCategory(),
                        TreeMap::new, // Сортировка категорий А-Я
                        Collectors.toList()
                ));
        model.addAttribute("groupedProducts", groupedProducts);
        model.addAttribute("products", activeProducts);

        List<Client> activeClients = Optional.ofNullable(clientRepository.findAllActive()).orElse(new ArrayList<>());
        model.addAttribute("clients", activeClients);
        model.addAttribute("users", Optional.ofNullable(userRepository.findAll()).orElse(new ArrayList<>()));
        model.addAttribute("managers", managersForUI);

        // Логика просрочки (Счета старше 30 дней)
        LocalDateTime limitDate = LocalDateTime.now().minusDays(30);
        Set<String> overdueClients = invoices.stream()
                .filter(inv -> inv != null && !"PAID".equals(inv.getStatus()))
                .filter(inv -> inv.getCreatedAt() != null && inv.getCreatedAt().isBefore(limitDate))
                .map(Invoice::getShopName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        model.addAttribute("overdueClients", overdueClients);

        // Быстрая карта долгов для JS
        Map<String, BigDecimal> clientDebts = activeClients.stream()
                .filter(c -> c != null && c.getName() != null)
                .collect(Collectors.toMap(
                        Client::getName,
                        c -> c.getDebt() != null ? c.getDebt() : BigDecimal.ZERO,
                        (existing, replacement) -> existing // Защита от дублей имен
                ));
        model.addAttribute("clientDebts", clientDebts);

        // Константы для выпадающих списков
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("returnReasons", ReasonsReturn.values());
        model.addAttribute("activeTab", activeTab);

        return "dashboard";
    }
}
