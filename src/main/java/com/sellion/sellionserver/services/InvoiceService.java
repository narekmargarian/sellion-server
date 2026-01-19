package com.sellion.sellionserver.services;


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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final ClientRepository clientRepository;

    @Transactional
    public void createInvoiceFromOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        if (order.getInvoiceId() != null) {
            throw new RuntimeException("Счет уже выставлен");
        }

        // 1. Валидация остатков
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            Product product = productRepository.findByName(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));

            // ИСПОЛЬЗУЕМ ГЕТТЕР getStockQuantity() вместо прямого обращения
            if (product.getStockQuantity() < entry.getValue()) {
                throw new RuntimeException("Дефицит товара [" + product.getName() +
                        "]. На складе: " + product.getStockQuantity());
            }
        }
        // 2. Создание Invoice
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName());
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
        invoice.setStatus("UNPAID");
        invoice.setCreatedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        // 3. Списание со склада (КРИТИЧЕСКИ ВАЖНО - ДОБАВЛЕНО)
        stockService.deductItemsFromStock(order.getItems(), "Счет №" + invoice.getInvoiceNumber());

        // 4. Запись долга (FinanceService)
        financeService.registerOperation(
                null, // ID будет найден внутри сервиса по имени магазина
                "ORDER",
                order.getTotalAmount(), // Просто передаем поле, так как оно уже BigDecimal
                invoice.getId(),
                "Выставлен счет № " + invoice.getInvoiceNumber(),
                order.getShopName()
        );

        // 5. Обновление заказа
        order.setStatus(OrderStatus.INVOICED);
        order.setInvoiceId(invoice.getId());
        orderRepository.save(order);
    }
}

