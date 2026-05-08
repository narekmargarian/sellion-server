package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;



    /**
     * Атомарное списание товара с блокировкой строк БД.
     * Используется при выставлении счета.
     */

    /**
     * Атомарное списание товара с блокировкой строк БД.
     * Исправлено: добавлена защита от повторного списания (Double Deduction)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductItemsFromStock(Order order, String operator) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) return;

        // ГИГАНТСКАЯ ОШИБКА БЫЛА ТУТ: Если у заказа уже есть invoiceId,
        // значит товар уже был списан ранее. Выходим, чтобы не списать второй раз.
        if (order.getInvoiceId() != null) {
            log.warn("Попытка повторного списания для заказа №{}. Операция отменена.", order.getId());
            return;
        }

        Map<Long, Integer> items = order.getItems();
        String reason = "Заказ #" + (order.getId() != null ? order.getId() : "NEW");

        // 1. Блокируем товары для предотвращения Race Condition
        List<Product> products = productRepository.findAllByIdWithLock(items.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qtyToDeduct = entry.getValue();

            if (qtyToDeduct == null || qtyToDeduct <= 0) continue;

            Product p = productMap.get(productId);
            if (p == null) {
                throw new RuntimeException("Критическая ошибка: Товар ID " + productId + " не найден!");
            }

            // Если заказ был в статусе RESERVED, значит остаток уже был уменьшен
            // методом reserveItemsFromStock. В этом случае ПОВТОРНО списывать не нужно.
            if (order.getStatus() == OrderStatus.RESERVED) {
                log.info("Товар {} уже был зарезервирован для заказа {}, пропускаем списание остатка.", p.getName(), order.getId());
                continue;
            }

            // 2. Проверка остатка
            int currentStock = (p.getStockQuantity() != null) ? p.getStockQuantity() : 0;
            if (currentStock < qtyToDeduct) {
                throw new RuntimeException("Недостаточно товара на складе: " + p.getName() +
                        " (Требуется: " + qtyToDeduct + ", В наличии: " + currentStock + ")");
            }

            // 3. Списание
            p.setStockQuantity(currentStock - qtyToDeduct);
            productRepository.save(p);

            logMovement(p.getName(), -qtyToDeduct, "SALE", reason, operator);
        }
    }

    /**
     * Резервирование товара (для заказов с Android) с блокировкой.
     */
// МЕТОД ДЛЯ ЭТАПА 1 (СОЗДАНИЕ/РЕЗЕРВ)
    @Transactional(rollbackFor = Exception.class)
    public void reserveItemsFromStock(Map<Long, Integer> items, String reason) {
        if (items == null || items.isEmpty()) return;

        // Блокируем товары, чтобы никто другой не изменил остаток в эту секунду
        List<Product> products = productRepository.findAllByIdWithLock(items.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Product p = productMap.get(entry.getKey());
            if (p == null) throw new RuntimeException("Товар не найден: " + entry.getKey());

            int qty = entry.getValue();
            if (p.getStockQuantity() < qty) {
                throw new RuntimeException("Недостаточно товара: " + p.getName());
            }

            p.setStockQuantity(p.getStockQuantity() - qty);
            productRepository.save(p);
            logMovement(p.getName(), -qty, "RESERVE", reason);
        }
    }

    /**
     * Возврат товара на склад (отмена заказа или возврат от клиента).
     */
// В файле StockService.java исправь заголовок метода:
    @Transactional(rollbackFor = Exception.class)
    public void returnItemsToStock(Map<Long, Integer> items, String reason, String operator) { // Добавили 3-й параметр
        if (items == null || items.isEmpty()) return;

        List<Product> products = productRepository.findAllByIdWithLock(items.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        items.forEach((productId, qty) -> {
            if (qty != null && qty > 0) {
                Product p = productMap.get(productId);
                if (p != null) {
                    int currentStock = (p.getStockQuantity() != null) ? p.getStockQuantity() : 0;
                    p.setStockQuantity(currentStock + qty);
                    productRepository.save(p);
                    // Передаем оператора в лог
                    logMovement(p.getName(), qty, "RETURN", reason, operator);
                }
            }
        });
    }

    private void logMovement(String name, int qty, String type, String reason) {
        StockMovement m = new StockMovement();
        m.setProductName(name);
        m.setQuantityChange(qty);
        m.setType(type);
        m.setReason(reason);
        m.setTimestamp(LocalDateTime.now());
        movementRepository.save(m);
    }

    /**
     * Корректировка склада по акту (инвентаризация).
     */
    @Transactional(rollbackFor = Exception.class)
    public void processCorrectionStock(Map<Long, Integer> items, String reference) {
        if (items == null) return;
        items.forEach((productId, qty) -> {
            productRepository.findById(productId).ifPresent(p -> {
                int currentStock = (p.getStockQuantity() != null) ? p.getStockQuantity() : 0;
                p.setStockQuantity(currentStock + qty);
                productRepository.save(p);
                logMovement(p.getName(), qty, "CORRECTION", "Корректировка по акту: " + reference, "ADMIN");
            });
        });
    }

    /**
     * Логирование движений (сохранено полностью).
     */
    public void logMovement(String name, Integer qty, String type, String reason, String operator) {
        StockMovement m = new StockMovement();
        m.setProductName(name);
        m.setQuantityChange(qty);
        m.setType(type);
        m.setReason(reason);
        m.setOperator(operator != null ? operator : "SYSTEM"); // Записываем кто нажал кнопку
        m.setTimestamp(LocalDateTime.now());
        movementRepository.save(m);
    }


}
