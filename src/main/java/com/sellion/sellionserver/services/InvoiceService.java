package com.sellion.sellionserver.services;


import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final FinanceService financeService;
    private final ProductRepository productRepository;

    /**
     * Создание инвойса из заказа с гарантированной проверкой остатков.
     * Весь процесс защищен единой транзакцией и блокировкой строк.
     */
    @Transactional(rollbackFor = Exception.class)
    public void createInvoiceFromOrder(Long orderId) {
        // 1. Поиск заказа
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));

        // 2. Защита от дубликатов
        if (order.getInvoiceId() != null) {
            throw new RuntimeException("Счет уже выставлен для этого заказа (ID: " + order.getInvoiceId() + ")");
        }

        // 3. АТОМАРНАЯ ПРОВЕРКА И СПИСАНИЕ
        // Мы используем метод deductItemsFromStock, который (в нашей исправленной версии)
        // сначала блокирует товары в БД, затем проверяет остаток и только потом списывает.
        // Это исключает ситуацию, когда остаток уходит в минус.
        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + String.format("%06d", System.currentTimeMillis() % 1000000);

        try {
            stockService.deductItemsFromStock(
                    order.getItems(),
                    "Выставление счета № " + invoiceNumber,
                    "ADMIN"
            );
        } catch (Exception e) {
            log.error("Ошибка списания при создании счета для заказа {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Не удалось выставить счет: " + e.getMessage());
        }

        // 4. Создание записи инвойса
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName());
        invoice.setManagerId(order.getManagerId());
        invoice.setTotalAmount(Optional.ofNullable(order.getTotalAmount()).orElse(BigDecimal.ZERO));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setStatus("UNPAID");
        invoice.setCreatedAt(LocalDateTime.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 5. Финансовый учет (регистрация дебиторской задолженности)
        financeService.registerOperation(
                null,
                "ORDER",
                invoice.getTotalAmount(),
                savedInvoice.getId(),
                "Выставлен счет № " + invoice.getInvoiceNumber(),
                order.getShopName()
        );

        // 6. Финализация заказа
        order.setStatus(OrderStatus.INVOICED);
        order.setInvoiceId(savedInvoice.getId());
        orderRepository.save(order);

        log.info("Счет успешно создан: {} для магазина {}", invoiceNumber, order.getShopName());
    }
}
