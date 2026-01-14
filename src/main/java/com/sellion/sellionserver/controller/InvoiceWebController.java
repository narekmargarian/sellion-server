package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;

    @PostMapping("/create-from-order/{orderId}")
    @Transactional // Добавь эту аннотацию
    public String createFromOrder(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        // ... твой существующий код создания Invoice ...
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
        invoice.setStatus("UNPAID");
        invoiceRepository.save(invoice);

        // НОВАЯ ЛОГИКА: Увеличиваем долг клиента
        clientRepository.findByName(order.getShopName()).ifPresent(client -> {
            double currentDebt = (client.getDebt() != null) ? client.getDebt() : 0.0;
            client.setDebt(currentDebt + order.getTotalAmount()); // Прибавили сумму счета к долгу
            clientRepository.save(client);
        });

        order.setStatus(OrderStatus.INVOICED);
        order.setInvoiceId(invoice.getId());
        orderRepository.save(order);

        return "redirect:/admin?activeTab=tab-invoices";
    }


    @GetMapping("/print/{id}")
    public String printInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Счет не найден"));

        // Получаем оригинальный заказ, чтобы вытащить список товаров
        Order order = invoice.getOrder();

        model.addAttribute("invoice", invoice);
        model.addAttribute("order", order);
        model.addAttribute("items", order.getItems());
        model.addAttribute("now", LocalDateTime.now());

        return "invoice-print"; // Создадим этот шаблон ниже
    }
}
