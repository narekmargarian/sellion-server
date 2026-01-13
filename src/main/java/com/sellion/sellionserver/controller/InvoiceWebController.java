package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;

    @PostMapping("/create-from-order/{orderId}")
    public String createFromOrder(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setManagerId(order.getManagerId());
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
        invoice.setStatus("UNPAID");

        Invoice saved = invoiceRepository.save(invoice);

        order.setStatus(OrderStatus.INVOICED);
        order.setInvoiceId(saved.getId());
        orderRepository.save(order);

        // Возвращаемся на главную, открывая вкладку счетов
        return "redirect:/admin?activeTab=tab-invoices";
    }
}
