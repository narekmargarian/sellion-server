package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.time.LocalDateTime;
import java.util.Map;
@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    @PostMapping("/create-from-order/{orderId}")
    @Transactional
    // ИСПРАВЛЕНО: Добавлен параметр RedirectAttributes в скобки метода
    public String createFromOrder(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        // Валидация остатков (как в 1С)
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            String productName = entry.getKey();

            Product product = productRepository.findByName(productName)
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + productName));

            // Проверка на дефицит
            if (product.getStockQuantity() < 0) {
                // Теперь redirectAttributes доступен!
                redirectAttributes.addFlashAttribute("error", "Ошибка: Товар [" + productName + "] в дефиците (" + product.getStockQuantity() + "). Счет не выписан!");
                return "redirect:/admin?activeTab=tab-orders";
            }
        }

        // Создание Invoice
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
        invoice.setStatus("UNPAID");
        invoiceRepository.save(invoice);

        // Увеличиваем долг клиента
        clientRepository.findByName(order.getShopName()).ifPresent(client -> {
            double currentDebt = (client.getDebt() != null) ? client.getDebt() : 0.0;
            client.setDebt(currentDebt + order.getTotalAmount());
            clientRepository.save(client);
        });

        order.setStatus(OrderStatus.INVOICED);
        order.setInvoiceId(invoice.getId());
        orderRepository.save(order);

        // Теперь и здесь успех будет работать!
        redirectAttributes.addFlashAttribute("success", "Счет успешно выставлен, долг клиента обновлен.");
        return "redirect:/admin?activeTab=tab-invoices";
    }

    @GetMapping("/print/{id}")
    public String printInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Счет не найден"));

        Order order = invoice.getOrder();
        model.addAttribute("invoice", invoice);
        model.addAttribute("order", order);
        model.addAttribute("items", order.getItems());
        model.addAttribute("now", LocalDateTime.now());

        return "invoice-print";
    }
}