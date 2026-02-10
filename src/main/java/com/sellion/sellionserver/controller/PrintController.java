package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.DailySummaryDto;
import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    @GetMapping("/orders/print/{id}")
    @Transactional(readOnly = true)
    public String printOrder(@PathVariable Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        model.addAttribute("op", order);
        model.addAttribute("title", "НАКЛАДНАЯ (ЗАКАЗ) №" + id);
        model.addAttribute("printItems", preparePrintItems(order.getItems()));
        return "print_template";
    }


    @GetMapping("/returns/print/{id}")
    @Transactional(readOnly = true)
    public String printReturn(@PathVariable Long id, Model model) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));

        model.addAttribute("op", ret);
        model.addAttribute("title", "АКТ ВОЗВРАТА №" + id);
        model.addAttribute("printItems", preparePrintItems(ret.getItems()));
        return "print_template";
    }

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

        BigDecimal total = filtered.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("title", "РЕЕСТР ЗАКАЗОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }

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

        BigDecimal total = filtered.stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }


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
        model.addAttribute("routeTotal", routeTotal.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("title", "МАРШРУТНЫЙ ЛИСТ: " + managerId);
        model.addAttribute("clientRepo", clientRepository);
        return "print_route_template";
    }


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
        model.addAttribute("title", "Акт сверки");
        return "print_statement_template";
    }


    @PostMapping("/orders/print-batch")
    @Transactional(readOnly = true)
    public String printOrdersBatch(@RequestParam("ids") List<Long> ids, Model model) {
        List<Order> orders = orderRepository.findAllById(ids);
        BigDecimal total = orders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", orders);
        model.addAttribute("finalTotal", total.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("title", "РЕЕСТР ВЫБРАННЫХ ЗАКАЗОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());
        return "print_list_template";
    }

    @PostMapping("/returns/print-batch")
    @Transactional(readOnly = true)
    public String printReturnsBatch(@RequestParam("ids") List<Long> ids, Model model) {
        List<ReturnOrder> returns = returnOrderRepository.findAllById(ids);
        BigDecimal total = returns.stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", returns);
        model.addAttribute("finalTotal", total.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("title", "РЕЕСТР ВЫБРАННЫХ ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());
        return "print_list_template";
    }


    @GetMapping("/logistic/print-compact")
    @Transactional(readOnly = true)
    public String printCompact(@RequestParam String managerId, @RequestParam String date, @RequestParam String type, Model model) {
        LocalDate deliveryDate = LocalDate.parse(date);

        if ("order".equals(type)) {
            List<Order> orders = orderRepository.findDailyRouteOrders(managerId, deliveryDate);
            model.addAttribute("payload", orders);
            model.addAttribute("title", "РЕЕСТР НАԿԼԱԴՆЫХ (ДОСТАВКА)");
        } else {
            List<ReturnOrder> returns = returnOrderRepository.findReturnsByManagerAndDateRange(managerId, deliveryDate.atStartOfDay(), deliveryDate.atTime(LocalTime.MAX));
            model.addAttribute("payload", returns);
            model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ (ДОСТАВКА)");
        }

        model.addAttribute("productRepo", productRepository);
        return "print_compact_template";
    }


    @GetMapping("/invoices/print-debts")
    @Transactional(readOnly = true)
    public String printManagerDebts(@RequestParam String managerId, Model model) {
        List<Invoice> unpaidInvoices = invoiceRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(inv -> managerId.equals(inv.getManagerId()))
                .filter(inv -> !"PAID".equals(inv.getStatus()))
                .toList();

        BigDecimal totalDebt = unpaidInvoices.stream()
                .map(inv -> inv.getTotalAmount().subtract(Optional.ofNullable(inv.getPaidAmount()).orElse(BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("invoices", unpaidInvoices);
        model.addAttribute("managerId", managerId);
        model.addAttribute("totalDebt", totalDebt.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("title", "ЛИСТ ЗАДОЛЖЕННОСТИ: " + managerId);
        model.addAttribute("currentDate", LocalDateTime.now());

        return "print_debts_template";
    }


    private List<PrintItemDto> preparePrintItems(Map<Long, Integer> items) {
        if (items == null || items.isEmpty()) return new ArrayList<>();

        List<Product> products = productRepository.findAllById(items.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        List<PrintItemDto> dtoList = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Product p = productMap.get(entry.getKey());
            PrintItemDto dto = new PrintItemDto();
            if (p != null) {
                dto.name = p.getName();
                dto.price = p.getPrice();
            } else {
                dto.name = "Товар (ID: " + entry.getKey() + ") удален";
                dto.price = BigDecimal.ZERO;
            }
            dto.quantity = entry.getValue();
            dto.total = (dto.price != null) ? dto.price.multiply(BigDecimal.valueOf(dto.quantity)) : BigDecimal.ZERO;
            dtoList.add(dto);
        }
        return dtoList;
    }



    @PostMapping("/orders/print-daily-summary") // Заменили на PostMapping
    @Transactional(readOnly = true)
    public String printDailySummary(
            @RequestParam("ids") List<Long> ids,
            Model model) {

        List<Order> orders = orderRepository.findAllById(ids);


        Map<Long, Integer> totals = new HashMap<>();
        orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .forEach(o -> o.getItems().forEach((id, qty) -> totals.merge(id, qty, Integer::sum)));

        List<DailySummaryDto> summary = new ArrayList<>();
        if (!totals.isEmpty()) {
            Map<Long, Product> products = productRepository.findAllById(totals.keySet())
                    .stream().collect(Collectors.toMap(Product::getId, p -> p));

            totals.forEach((id, qty) -> {
                Product p = products.get(id);
                DailySummaryDto dto = new DailySummaryDto();
                dto.setProductName(p != null ? p.getName() : "Удален #" + id);
                dto.setCategory(p != null ? p.getCategory() : "Без категории");
                dto.setTotalQuantity(qty);
                summary.add(dto);
            });
        }

        summary.sort(Comparator.comparing(DailySummaryDto::getCategory).thenComparing(DailySummaryDto::getProductName));

        model.addAttribute("summary", summary);
        model.addAttribute("date", "Сводка по выбранным заказам (" + ids.size() + " шт.)");
        model.addAttribute("title", "СВОДНАЯ ВЕДОМОСТЬ");

        return "print_daily_summary_template";
    }





}
