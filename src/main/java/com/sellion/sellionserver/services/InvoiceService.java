package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final FinanceService financeService;

    /**
     * Создание инвойса из заказа.
     * ИСПРАВЛЕНО: Добавлена атомарная связка заказа с инвойсом и защита от тихих ошибок.
     */

    @Transactional(rollbackFor = Exception.class)
    public Invoice createInvoiceFromOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        if (order.getInvoiceId() != null) return null;

        // КРИТИЧНО: УДАЛЯЕМ строку stockService.deductItemsFromStock(...),
        // так как товар уже был вычтен методом reserveItemsFromStock при создании заказа.

        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + order.getId();

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName().trim());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setStatus("UNPAID");

        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        order.setInvoiceId(savedInvoice.getId());
        order.setStatus(OrderStatus.INVOICED);
        orderRepository.saveAndFlush(order);

        try {
            financeService.registerOperation(null, "ORDER", order.getTotalAmount(),
                    savedInvoice.getId(), "Счет " + invoiceNumber, order.getShopName());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка финансов: " + e.getMessage());
        }

        return savedInvoice;
    }

}
