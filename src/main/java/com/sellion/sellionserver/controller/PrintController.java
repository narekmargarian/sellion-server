package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.DailySummaryDto;
import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class PrintController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;

    public static class PrintItemDto {
        public String name;
        public Integer quantity;
        public BigDecimal price;
        public BigDecimal total;
    }

    // 1. Одиночная печать ЗАКАЗА
    @GetMapping("/orders/print/{id}")
    @Transactional(readOnly = true)
    public String printOrder(@PathVariable Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        model.addAttribute("productRepo", productRepository);
        model.addAttribute("op", order);
        model.addAttribute("title", "НАКЛАДНАЯ (ЗАКАЗ) №" + id);
        // ИСПРАВЛЕНО: используем специфичный метод для заказов
        model.addAttribute("printItems", prepareOrderPrintItems(order));
        return "print_template";
    }

    // 2. Одиночная печать ВОЗВРАТА
    @GetMapping("/returns/print/{id}")
    @Transactional(readOnly = true)
    public String printReturn(@PathVariable Long id, Model model) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));
        model.addAttribute("productRepo", productRepository);
        model.addAttribute("op", ret);
        model.addAttribute("title", "АКТ ВОЗВРАТА №" + id);
        // ИСПРАВЛЕНО: используем специфичный метод для возвратов (подтянет ручные цены)
        model.addAttribute("printItems", prepareReturnPrintItems(ret));
        return "print_template";
    }

    // 3. Реестр ЗАКАЗОВ
    @GetMapping("/orders/print-all")
    @Transactional(readOnly = true)
    public String printAllOrders(
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "orderStartDate", required = false) String start,
            @RequestParam(value = "orderEndDate", required = false) String end,
            Model model) {

        LocalDate startD = (start == null || start.isEmpty()) ? LocalDate.now() : LocalDate.parse(start);
        LocalDate endD = (end == null || end.isEmpty()) ? startD : LocalDate.parse(end);

        LocalDateTime from = startD.atStartOfDay();
        LocalDateTime to = endD.atTime(LocalTime.MAX);

        List<Order> orders = orderRepository.findOrdersBetweenDates(from, to);
        List<Order> filtered = (orderManagerId == null || orderManagerId.isEmpty()) ? orders :
                orders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList();

        // Итоговая сумма берется напрямую из сохраненных полей totalAmount в БД
        BigDecimal total = filtered.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total.setScale(0, RoundingMode.HALF_UP)); // Округление до целых драмов
        model.addAttribute("title", "РЕЕСТР ЗАКАЗОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }

    // 4. Реестр ВОЗВРАТОВ
    @GetMapping("/returns/print-all")
    @Transactional(readOnly = true)
    public String printAllReturns(
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "returnStartDate", required = false) String start,
            @RequestParam(value = "returnEndDate", required = false) String end,
            Model model) {

        LocalDate startD = (start == null || start.isEmpty()) ? LocalDate.now() : LocalDate.parse(start);
        LocalDate endD = (end == null || end.isEmpty()) ? startD : LocalDate.parse(end);

        LocalDateTime from = startD.atStartOfDay();
        LocalDateTime to = endD.atTime(LocalTime.MAX);

        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(from, to);
        List<ReturnOrder> filtered = (returnManagerId == null || returnManagerId.isEmpty()) ? returns :
                returns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList();

        // Итоговая сумма берется из сохраненных в БД итогов возвратов (учитывает ручные цены)
        BigDecimal total = filtered.stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total.setScale(0, RoundingMode.HALF_UP)); // Округление до целых драмов
        model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }


    // 1. Маршрутный лист (Логистика)
    @GetMapping("/logistic/route-list")
    @Transactional(readOnly = true)
    public String printRouteList(@RequestParam String managerId, @RequestParam String date, Model model) {
        LocalDate deliveryDate = LocalDate.parse(date);
        List<Order> orders = orderRepository.findDailyRouteOrders(managerId, deliveryDate);

        BigDecimal routeTotal = orders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("orders", orders);
        model.addAttribute("managerId", managerId);
        model.addAttribute("date", date);
        // ИСПРАВЛЕНО: Округление до целых для драммов
        model.addAttribute("routeTotal", routeTotal.setScale(0, RoundingMode.HALF_UP));
        model.addAttribute("title", "МАРШРУТНЫЙ ЛИСТ: " + managerId);
        model.addAttribute("clientRepo", clientRepository);
        return "print_route_template";
    }

    // 2. Акт сверки клиента
    @GetMapping("/clients/print-statement/{id}")
    @Transactional(readOnly = true)
    public String printClientStatement(@PathVariable Long id, @RequestParam String start, @RequestParam String end, Model model) {
        Client client = clientRepository.findById(id).orElseThrow();
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        List<Transaction> transactions = transactionRepository.findAllByClientIdOrderByTimestampAsc(id)
                .stream()
                .filter(tx -> tx.getTimestamp() != null &&
                        !tx.getTimestamp().toLocalDate().isBefore(startDate) &&
                        !tx.getTimestamp().toLocalDate().isAfter(endDate))
                .toList();

        model.addAttribute("client", client);
        model.addAttribute("transactions", transactions);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("title", "Акт сверки: " + client.getName());
        return "print_statement_template";
    }

    // 3. Массовая печать ВЫБРАННЫХ ЗАКАЗОВ
    @PostMapping("/orders/print-batch")
    @Transactional(readOnly = true)
    public String printOrdersBatch(@RequestParam("ids") List<Long> ids, Model model) {
        List<Order> orders = orderRepository.findAllById(ids);
        BigDecimal total = orders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", orders);
        model.addAttribute("finalTotal", total.setScale(0, RoundingMode.HALF_UP));
        model.addAttribute("title", "РЕЕСТР ВЫБРАННЫХ ЗАКАЗОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());
        return "print_list_template";
    }

    // 4. Массовая печать ВЫБРАННЫХ ВОЗВРАТОВ (Учитывает ручные цены)
    @PostMapping("/returns/print-batch")
    @Transactional(readOnly = true)
    public String printReturnsBatch(@RequestParam("ids") List<Long> ids, Model model) {
        List<ReturnOrder> returns = returnOrderRepository.findAllById(ids);
        BigDecimal total = returns.stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", returns);
        model.addAttribute("finalTotal", total.setScale(0, RoundingMode.HALF_UP));
        model.addAttribute("title", "РЕЕСТР ВЫБРАННЫХ ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());
        return "print_list_template";
    }



    @GetMapping("/logistic/print-compact")
    @Transactional(readOnly = true)
    public String printCompact(
            @RequestParam(required = false) String managerId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) List<Long> ids,
            @RequestParam String type,
            Model model) {

        String effectiveManagerId = managerId;

        if ("order".equals(type)) {
            List<Order> orders;
            if (ids != null && !ids.isEmpty()) {
                orders = orderRepository.findAllById(ids);
                // Если менеджер не пришел в параметрах, берем из первого заказа
                if (effectiveManagerId == null && !orders.isEmpty()) {
                    effectiveManagerId = orders.get(0).getManagerId();
                }
            } else if (managerId != null && date != null) {
                LocalDate deliveryDate = LocalDate.parse(date);
                orders = orderRepository.findDailyRouteOrders(managerId, deliveryDate);
            } else {
                orders = new ArrayList<>();
            }
            model.addAttribute("payload", orders);
            model.addAttribute("managerId", effectiveManagerId);
            model.addAttribute("title", "РЕЕСТР НАКЛАДНЫХ");
            model.addAttribute("productRepo", productRepository);
            return "print_compact_template"; // Шаблон для заказов (% и Прайс)
        } else {
            List<ReturnOrder> returns;
            if (ids != null && !ids.isEmpty()) {
                returns = returnOrderRepository.findAllById(ids);
                if (effectiveManagerId == null && !returns.isEmpty()) {
                    effectiveManagerId = returns.get(0).getManagerId();
                }
            } else if (managerId != null && date != null) {
                LocalDate deliveryDate = LocalDate.parse(date);
                returns = returnOrderRepository.findReturnsByManagerAndDateRange(
                        managerId, deliveryDate.atStartOfDay(), deliveryDate.atTime(LocalTime.MAX));
            } else {
                returns = new ArrayList<>();
            }
            model.addAttribute("payload", returns);
            model.addAttribute("managerId", effectiveManagerId);
            model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ");
            model.addAttribute("productRepo", productRepository);

            // ИСПРАВЛЕНО: Теперь возвраты используют свой отдельный шаблон (№, Товар, Кол-во, Цена, Сумма)
            return "print_compact_return_template";
        }
    }


    // 6. Лист задолженности (Дебиторка)
    @GetMapping("/invoices/print-debts")
    @Transactional(readOnly = true)
    public String printManagerDebts(
            @RequestParam String managerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Model model) {

        List<Invoice> unpaidInvoices = invoiceRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(inv -> managerId.equals(inv.getManagerId()))
                .filter(inv -> !"PAID".equals(inv.getStatus()))
                .filter(inv -> {
                    if (inv.getCreatedAt() == null) return false;
                    LocalDate invDate = inv.getCreatedAt().toLocalDate();
                    boolean matches = true;
                    if (start != null && invDate.isBefore(start)) matches = false;
                    if (end != null && invDate.isAfter(end)) matches = false;
                    return matches;
                })
                .toList();

        BigDecimal totalDebt = unpaidInvoices.stream()
                .map(inv -> inv.getTotalAmount().subtract(Optional.ofNullable(inv.getPaidAmount()).orElse(BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("invoices", unpaidInvoices);
        model.addAttribute("managerId", managerId);
        model.addAttribute("totalDebt", totalDebt.setScale(0, RoundingMode.HALF_UP)); // Округление до целых
        model.addAttribute("title", "ЛИСТ ЗАДОЛЖЕННОСТИ: " + managerId);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("currentDate", LocalDateTime.now());

        return "print_debts_template";
    }



    // ДЛЯ ЗАКАЗОВ: учитывает акции и скидки, сохраненные в заказе
    private List<PrintItemDto> prepareOrderPrintItems(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) return new ArrayList<>();

        Map<Long, Product> productMap = productRepository.findAllById(order.getItems().keySet())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        List<PrintItemDto> dtoList = new ArrayList<>();
        order.getItems().forEach((pId, qty) -> {
            Product p = productMap.get(pId);
            PrintItemDto dto = new PrintItemDto();
            dto.name = (p != null) ? p.getName() : "Товар (ID: " + pId + ") удален";
            dto.quantity = qty;

            // ПРИОРИТЕТ: Акция на товар > Скидка магазина (с защитой от null)
            BigDecimal discount = order.getAppliedPromoItems().get(pId);
            if (discount == null) {
                discount = Optional.ofNullable(order.getDiscountPercent()).orElse(BigDecimal.ZERO);
            }

            BigDecimal basePrice = (p != null) ? p.getPrice() : BigDecimal.ZERO;

            // Считаем цену со скидкой
            BigDecimal modifier = BigDecimal.ONE.subtract(discount.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            dto.price = basePrice.multiply(modifier).setScale(2, RoundingMode.HALF_UP);
            dto.total = dto.price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

            dtoList.add(dto);
        });
        return dtoList;
    }

    // ДЛЯ ВОЗВРАТОВ: берет ручные цены из документа (itemPrices)
    private List<PrintItemDto> prepareReturnPrintItems(ReturnOrder ret) {
        if (ret.getItems() == null || ret.getItems().isEmpty()) return new ArrayList<>();

        Map<Long, Product> productMap = productRepository.findAllById(ret.getItems().keySet())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        List<PrintItemDto> dtoList = new ArrayList<>();
        ret.getItems().forEach((pId, qty) -> {
            Product p = productMap.get(pId);
            PrintItemDto dto = new PrintItemDto();
            dto.name = (p != null) ? p.getName() : "Товар (ID: " + pId + ") удален";
            dto.quantity = qty;

            // КЛЮЧЕВОЕ: Берем цену, которую вы ввели вручную в инпут (сохранена в itemPrices)
            BigDecimal manualPrice = ret.getItemPrices().getOrDefault(pId, (p != null ? p.getPrice() : BigDecimal.ZERO));

            dto.price = manualPrice.setScale(0, RoundingMode.HALF_UP);
            dto.total = dto.price.multiply(BigDecimal.valueOf(qty)).setScale(0, RoundingMode.HALF_UP);

            dtoList.add(dto);
        });
        return dtoList;
    }



    @PostMapping("/orders/print-daily-summary")
    @Transactional(readOnly = true)
    public String printDailySummary(@RequestParam("ids") List<Long> ids, Model model) {
        // Загружаем выбранные заказы
        List<Order> orders = orderRepository.findAllById(ids);

        // Считаем общее кол-во каждого товара (только для активных заказов)
        Map<Long, Integer> totals = new HashMap<>();
        orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED) // Не считаем отмененные
                .forEach(o -> o.getItems().forEach((pId, qty) -> totals.merge(pId, qty, Integer::sum)));

        List<DailySummaryDto> summary = new ArrayList<>();
        if (!totals.isEmpty()) {
            Map<Long, Product> products = productRepository.findAllById(totals.keySet())
                    .stream().collect(Collectors.toMap(Product::getId, p -> p));

            totals.forEach((pId, qty) -> {
                Product p = products.get(pId);
                DailySummaryDto dto = new DailySummaryDto();
                dto.setProductName(p != null ? p.getName() : "Товар #" + pId + " (Удален)");
                dto.setCategory(p != null ? p.getCategory() : "Без категории");
                dto.setTotalQuantity(qty);
                summary.add(dto);
            });
        }

        // Сортировка по категориям, затем по имени
        summary.sort(Comparator.comparing(DailySummaryDto::getCategory)
                .thenComparing(DailySummaryDto::getProductName));

        // --- ДОБАВЛЕНО ТУТ: Считаем общую сумму всех количеств ---
        int totalItemsCount = summary.stream()
                .mapToInt(DailySummaryDto::getTotalQuantity)
                .sum();

        // --- ЛОГИКА ОПРЕДЕЛЕНИЯ МЕНЕДЖЕРА ---
        // Собираем всех уникальных менеджеров из выбранных заказов
        Set<String> managers = orders.stream()
                .map(Order::getManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Если менеджер один — пишем его ID, если разные или пусто — пишем "ВСЕ"
        String displayManager = (managers.size() == 1) ? managers.iterator().next() : "ВСЕ";
        model.addAttribute("selectedManager", displayManager);

        model.addAttribute("summary", summary);
        model.addAttribute("totalItemsCount", totalItemsCount); // Передаем готовую сумму
        model.addAttribute("date", "Сводка по " + ids.size() + " заказам");
        model.addAttribute("title", "СВОДНАЯ ВЕДОМОСТЬ (ПОГРУЗКА)");

        return "print_daily_summary_template";
    }



}
