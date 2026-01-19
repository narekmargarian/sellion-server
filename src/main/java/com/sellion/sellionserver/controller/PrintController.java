package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PrintController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;

    // Вспомогательный класс обновлен на BigDecimal для точности
    public static class PrintItemDto {
        public String name;
        public Integer quantity;
        public BigDecimal price;
        public BigDecimal total;
    }

    @GetMapping("/orders/print/{id}")
    public String printOrder(@PathVariable Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        model.addAttribute("op", order);
        model.addAttribute("title", "НАКЛАДНАЯ (ЗАКАЗ) №" + id);
        model.addAttribute("printItems", preparePrintItems(order.getItems()));

        return "print_template";
    }

    @GetMapping("/returns/print/{id}")
    public String printReturn(@PathVariable Long id, Model model) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));

        model.addAttribute("op", ret);
        model.addAttribute("title", "АКТ ВОЗВРАТА №" + id);
        model.addAttribute("printItems", preparePrintItems(ret.getItems()));

        return "print_template";
    }

    @GetMapping("/orders/print-all")
    public String printAllOrders(
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            @RequestParam(value = "orderStartDate", required = false) String start,
            @RequestParam(value = "orderEndDate", required = false) String end,
            Model model) {

        String s = (start == null || start.isEmpty()) ? LocalDate.now().toString() : start;
        String e = (end == null || end.isEmpty()) ? s : end;

        List<Order> orders = orderRepository.findOrdersBetweenDates(s + "T00:00:00", e + "T23:59:59");
        List<Order> filtered = (orderManagerId == null || orderManagerId.isEmpty()) ? orders :
                orders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList();

        // ИСПРАВЛЕНО: Суммирование BigDecimal
        BigDecimal total = filtered.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total);
        model.addAttribute("title", "РЕЕСТР ЗАКАЗОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }

    @GetMapping("/returns/print-all")
    public String printAllReturns(
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "returnStartDate", required = false) String start,
            @RequestParam(value = "returnEndDate", required = false) String end,
            Model model) {

        String s = (start == null || start.isEmpty()) ? LocalDate.now().toString() : start;
        String e = (end == null || end.isEmpty()) ? s : end;

        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(s + "T00:00:00", e + "T23:59:59");
        List<ReturnOrder> filtered = (returnManagerId == null || returnManagerId.isEmpty()) ? returns :
                returns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList();

        // ИСПРАВЛЕНО: Суммирование BigDecimal
        BigDecimal total = filtered.stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total);
        model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }

    private List<PrintItemDto> preparePrintItems(Map<String, Integer> items) {
        List<PrintItemDto> list = new ArrayList<>();
        if (items == null) return list;

        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            PrintItemDto dto = new PrintItemDto();
            dto.name = entry.getKey();
            dto.quantity = entry.getValue();

            productRepository.findByName(entry.getKey()).ifPresentOrElse(
                    p -> {
                        dto.price = p.getPrice();
                        // ИСПРАВЛЕНО: Умножение BigDecimal
                        dto.total = p.getPrice().multiply(BigDecimal.valueOf(entry.getValue()));
                    },
                    () -> {
                        dto.price = BigDecimal.ZERO;
                        dto.total = BigDecimal.ZERO;
                    }
            );
            list.add(dto);
        }
        return list;
    }

    @GetMapping("/logistic/route-list")
    public String printRouteList(
            @RequestParam String managerId,
            @RequestParam String date,
            Model model) {

        LocalDate deliveryDate = LocalDate.parse(date);
        List<Order> orders = orderRepository.findDailyRouteOrders(managerId, deliveryDate);

        // ИСПРАВЛЕНО: Суммирование BigDecimal
        BigDecimal routeTotal = orders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("orders", orders);
        model.addAttribute("managerId", managerId);
        model.addAttribute("date", date);
        model.addAttribute("routeTotal", routeTotal);
        model.addAttribute("title", "МАРШРУТНЫЙ ЛИСТ: " + managerId);
        model.addAttribute("clientRepo", clientRepository);

        return "print_route_template";
    }

    @GetMapping("/clients/print-statement/{id}")
    public String printClientStatement(
            @PathVariable Long id,
            @RequestParam String start,
            @RequestParam String end,
            Model model) {

        Client client = clientRepository.findById(id).orElseThrow();
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        List<Transaction> transactions = transactionRepository.findAllByClientIdOrderByTimestampAsc(id)
                .stream()
                .filter(tx -> !tx.getTimestamp().toLocalDate().isBefore(startDate) &&
                        !tx.getTimestamp().toLocalDate().isAfter(endDate))
                .collect(Collectors.toList());

        model.addAttribute("client", client);
        model.addAttribute("transactions", transactions);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("title", "Акт сверки");

        return "print_statement_template";
    }
}
