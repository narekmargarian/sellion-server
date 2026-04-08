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

        if (order.getInvoiceId() != null) return null; // Уже есть счет

        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + order.getId();

        // 1. Списание (Сначала склад)
        stockService.deductItemsFromStock(order.getItems(), "Счет " + invoiceNumber, "ADMIN");

        // 2. Создание инвойса
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName().trim()); // ЧИСТИМ ИМЯ ОТ ПРОБЕЛОВ
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setStatus("UNPAID");

        // СОХРАНЯЕМ ИНВОЙС СРАЗУ
        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        // 3. !!! СВЯЗЫВАЕМ С ЗАКАЗОМ СРАЗУ (Чтобы даже если финансы упадут, мы видели связь)
        order.setInvoiceId(savedInvoice.getId());
        order.setStatus(OrderStatus.INVOICED);
        orderRepository.saveAndFlush(order); // ФИКСИРУЕМ В БАЗЕ МГНОВЕННО

        // 4. ФИНАНСЫ (Делаем в самом конце)
        try {
            financeService.registerOperation(null, "ORDER", order.getTotalAmount(),
                    savedInvoice.getId(), "Счет " + invoiceNumber, order.getShopName());
        } catch (Exception e) {
            log.error("ОШИБКА ФИНАНСОВ для заказа {}: {}", orderId, e.getMessage());
            // Мы не гасим ошибку! Пусть транзакция откатится, если финансы критичны.
            throw new RuntimeException("Ошибка финансов: " + e.getMessage());
        }

        return savedInvoice;
    }

}
