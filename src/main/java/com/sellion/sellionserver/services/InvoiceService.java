package com.sellion.sellionserver.services;


import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final FinanceService financeService;
    private final ProductRepository productRepository;

    @Transactional(rollbackFor = Exception.class)
    public void createInvoiceFromOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));

        if (order.getInvoiceId() != null) {
            throw new RuntimeException("Счет уже выставлен для этого заказа");
        }

        // 1. Проверка остатков с использованием ID (теперь Map.Entry<Long, Integer>)
        for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
            // Используем поиск по ID. Рекомендуется метод findByIdWithLock
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар ID " + entry.getKey() + " не найден"));

            if (product.getStockQuantity() < entry.getValue()) {
                throw new RuntimeException("Дефицит товара [" + product.getName() +
                        "]. Требуется: " + entry.getValue() + ", в наличии: " + product.getStockQuantity());
            }
        }

        // 2. Создание инвойса
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName());
        invoice.setManagerId(order.getManagerId());
        invoice.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
        invoice.setStatus("UNPAID");
        invoice.setCreatedAt(LocalDateTime.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 3. Списание со склада.
        // ВАЖНО: Метод deductItemsFromStock в StockService должен теперь принимать Map<Long, Integer>
        stockService.deductItemsFromStock(
                order.getItems(),
                "Выставление счета № " + savedInvoice.getInvoiceNumber(),
                "ADMIN"
        );

        // 4. Финансовый учет
        financeService.registerOperation(
                null,
                "ORDER",
                invoice.getTotalAmount(),
                savedInvoice.getId(),
                "Выставлен счет № " + savedInvoice.getInvoiceNumber(),
                order.getShopName()
        );

        // 5. Финализация заказа
        order.setStatus(OrderStatus.INVOICED);
        order.setInvoiceId(savedInvoice.getId());
        orderRepository.save(order);
    }
}