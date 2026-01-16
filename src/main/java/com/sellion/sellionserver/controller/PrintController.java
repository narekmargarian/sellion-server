package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
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
    private final ProductRepository productRepository; // <-- ДОБАВЛЕНО

    // Вспомогательный класс для шаблона печати (DTO)
    public static class PrintItemDto {
        public String name;
        public Integer quantity;
        public Double price;
        public Double total;
    }

    @GetMapping("/orders/print/{id}")
    public String printOrder(@PathVariable Long id, Model model) {
        Order order = orderRepository.findById(id).orElseThrow();
        model.addAttribute("op", order);
        model.addAttribute("title", "НАКЛАДНАЯ (ЗАКАЗ) №" + id);

        // Преобразуем Map<String, Integer> в List<PrintItemDto>
        List<PrintItemDto> printItems = preparePrintItems(order.getItems());
        model.addAttribute("printItems", printItems); // <-- ПЕРЕДАЕМ НОВЫЕ ДАННЫЕ

        return "print_template";
    }

    @GetMapping("/returns/print/{id}")
    public String printReturn(@PathVariable Long id, Model model) {
        ReturnOrder ret = returnOrderRepository.findById(id).orElseThrow();
        model.addAttribute("op", ret);
        model.addAttribute("title", "АКТ ВОЗВРАТА №" + id);

        // Преобразуем Map<String, Integer> в List<PrintItemDto>
        List<PrintItemDto> printItems = preparePrintItems(ret.getItems());
        model.addAttribute("printItems", printItems); // <-- ПЕРЕДАЕМ НОВЫЕ ДАННЫЕ

        return "print_template";
    }

    // Приватный метод для получения цен товаров
    private List<PrintItemDto> preparePrintItems(Map<String, Integer> items) {
        List<PrintItemDto> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = productRepository.findByName(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));
            PrintItemDto dto = new PrintItemDto();
            dto.name = entry.getKey();
            dto.quantity = entry.getValue();
            dto.price = p.getPrice();
            dto.total = p.getPrice() * entry.getValue();
            list.add(dto);
        }
        return list;
    }


    @GetMapping("/orders/print-all")
    public String printAllOrders(
            @RequestParam(value = "orderManagerId", required = false) String orderManagerId,
            Model model) {

        List<Order> allOrders = orderRepository.findAll();
        List<Order> filteredOrders = (orderManagerId != null && !orderManagerId.isEmpty())
                ? allOrders.stream().filter(o -> orderManagerId.equals(o.getManagerId())).toList()
                : allOrders;

        model.addAttribute("operations", filteredOrders);
        model.addAttribute("title", "РЕЕСТР ЗАКАЗОВ ДЛЯ ДОСТАВКИ");
        model.addAttribute("currentDateTime", LocalDateTime.now()); // <-- ДОБАВЛЕНО
        return "print_list_template";
    }

    @GetMapping("/returns/print-all")
    public String printAllReturns(
            @RequestParam(value = "returnManagerId", required = false) String returnManagerId,
            Model model) {

        List<ReturnOrder> allReturns = returnOrderRepository.findAll();
        List<ReturnOrder> filteredReturns = (returnManagerId != null && !returnManagerId.isEmpty())
                ? allReturns.stream().filter(r -> returnManagerId.equals(r.getManagerId())).toList()
                : allReturns;

        model.addAttribute("operations", filteredReturns);
        model.addAttribute("title", "РЕЕСТР ВОЗВРАТОВ");
        model.addAttribute("currentDateTime", LocalDateTime.now()); // <-- ДОБАВЛЕНО
        return "print_list_template";
    }
}

