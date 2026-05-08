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
    // stockService здесь больше не нужен для списания, так как списание на Этапе 1
    private final FinanceService financeService;

    /**
     * ЭТАП 2: Создание инвойса из зарезервированного заказа.
     * Склад НЕ трогаем, так как товар уже списан при сохранении заказа (статус RESERVED).
     */
    @Transactional(rollbackFor = Exception.class)
    public Invoice createInvoiceFromOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        // 1. Проверка на дубликат инвойса
        if (order.getInvoiceId() != null) {
            log.warn("Для заказа #{} уже существует инвойс ID: {}", orderId, order.getInvoiceId());
            return null;
        }

        // 2. КРИТИЧЕСКАЯ ПРОВЕРКА: Инвойс можно создать только если товар уже зарезервирован
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new RuntimeException("Ошибка: Нельзя выставить счет. Заказ должен иметь статус RESERVED (товар списан). " +
                    "Текущий статус: " + order.getStatus());
        }

        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + order.getId();

        // 3. Создание записи Инвойса
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setShopName(order.getShopName().trim());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setStatus("UNPAID");

        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        // 4. Обновление заказа: привязываем инвойс и переводим в финальный статус PROCESSED
        order.setInvoiceId(savedInvoice.getId());
        order.setStatus(OrderStatus.PROCESSED); // <--- ЭТАП 2: Завершение сделки
        orderRepository.saveAndFlush(order);

        // 5. ФИНАНСЫ: Увеличиваем долг клиента
        try {
            financeService.registerOperation(
                    null,
                    "ORDER",
                    order.getTotalAmount(),
                    savedInvoice.getId(),
                    "Счет " + invoiceNumber,
                    order.getShopName()
            );
        } catch (Exception e) {
            log.error("Критическая ошибка при регистрации финансов для инвойса {}", invoiceNumber);
            throw new RuntimeException("Ошибка финансов: " + e.getMessage());
        }

        log.info("Инвойс {} успешно создан для магазина {}. Статус заказа изменен на PROCESSED",
                invoiceNumber, order.getShopName());

        return savedInvoice;
    }
}