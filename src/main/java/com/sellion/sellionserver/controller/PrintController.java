package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PrintController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository; // Добавьте в final поля


    // Вспомогательный класс для шаблона печати (DTO)
    public static class PrintItemDto {
        public String name;
        public Integer quantity;
        public Double price;
        public Double total;
    }

    /**
     * Печать одиночного заказа
     */
    @GetMapping("/orders/print/{id}")
    public String printOrder(@PathVariable Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));

        model.addAttribute("op", order);
        model.addAttribute("title", "НАКЛАДНАЯ (ЗАКАЗ) №" + id);
        model.addAttribute("printItems", preparePrintItems(order.getItems()));

        return "print_template";
    }

    /**
     * Печать одиночного возврата
     */
    @GetMapping("/returns/print/{id}")
    public String printReturn(@PathVariable Long id, Model model) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Возврат не найден: " + id));

        model.addAttribute("op", ret);
        model.addAttribute("title", "АКТ ВОЗВРАТА №" + id);
        model.addAttribute("printItems", preparePrintItems(ret.getItems()));

        return "print_template";
    }

    /**
     * Печать реестра всех заказов за период
     */
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

        // Безопасный расчет суммы (защита от null)
        double total = filtered.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total);
        model.addAttribute("title", "РЕЕСТР ЗАКАЗОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }

    /**
     * Печать реестра всех возвратов за период
     */
    @GetMapping("/returns/print-all")
    public String printAllReturns(
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            @RequestParam(value = "returnStartDate", required = false) String start,
            @RequestParam(value = "returnEndDate", required = false) String end,
            Model model) {

        // Защита от пустых дат
        String s = (start == null || start.isEmpty()) ? LocalDate.now().toString() : start;
        String e = (end == null || end.isEmpty()) ? s : end;

        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(s + "T00:00:00", e + "T23:59:59");

        List<ReturnOrder> filtered = (returnManagerId == null || returnManagerId.isEmpty()) ? returns :
                returns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList();

        // Безопасный расчет суммы (защита от null)
        double total = filtered.stream()
                .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount() : 0.0)
                .sum();

        model.addAttribute("operations", filtered);
        model.addAttribute("finalTotal", total);
        model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now());

        return "print_list_template";
    }

    /**
     * Вспомогательный метод для получения актуальных цен из склада для печатной формы
     */
    private List<PrintItemDto> preparePrintItems(Map<String, Integer> items) {
        List<PrintItemDto> list = new ArrayList<>();
        if (items == null) return list;

        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            PrintItemDto dto = new PrintItemDto();
            dto.name = entry.getKey();
            dto.quantity = entry.getValue();

            // Получаем цену товара из БД, чтобы в накладной была актуальная цена
            productRepository.findByName(entry.getKey()).ifPresentOrElse(
                    p -> {
                        dto.price = p.getPrice();
                        dto.total = p.getPrice() * entry.getValue();
                    },
                    () -> {
                        dto.price = 0.0;
                        dto.total = 0.0;
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

        double routeTotal = orders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0).sum();

        model.addAttribute("orders", orders);
        model.addAttribute("managerId", managerId);
        model.addAttribute("date", date);
        model.addAttribute("routeTotal", routeTotal);
        model.addAttribute("title", "МАРШРУТНЫЙ ЛИСТ: " + managerId);
        // Добавляем репозиторий в модель, чтобы вызвать его из HTML для адресов
        model.addAttribute("clientRepo", clientRepository);

        return "print_route_template";
    }

}

