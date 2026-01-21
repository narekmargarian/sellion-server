package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    /**
     * Атомарное списание товара.
     * Используется при выставлении счета.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductItemsFromStock(Map<Long, Integer> items, String reason, String operator) {
        if (items == null || items.isEmpty()) return;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            if (qty == null || qty <= 0) continue;

            // Блокируем строку товара в БД (Pessimistic Lock), чтобы никто другой не изменил остаток
            Product p = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new RuntimeException("Товар ID " + productId + " не найден"));

            // Атомарное списание через кастомный запрос в репозитории
            int updatedRows = productRepository.deductStockById(productId, qty);

            if (updatedRows == 0) {
                throw new RuntimeException("Недостаточно товара на складе: " + p.getName() +
                        " (Требуется: " + qty + ", Наличие: " + p.getStockQuantity() + ")");
            }

            logMovement(p.getName(), -qty, "SALE", reason, operator);
        }
    }

    /**
     * Резервирование (для заказов с Android).
     * Отличие от списания: другой тип лога.
     */
    @Transactional(rollbackFor = Exception.class)
    public void reserveItemsFromStock(Map<Long, Integer> items, String reason) {
        if (items == null || items.isEmpty()) return;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            if (qty == null || qty <= 0) continue;

            Product p = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new RuntimeException("Товар ID " + productId + " не найден для резерва"));

            int updatedRows = productRepository.deductStockById(productId, qty);
            if (updatedRows == 0) {
                throw new RuntimeException("Не удалось зарезервировать " + p.getName() + ". Недостаточно остатка.");
            }

            logMovement(p.getName(), -qty, "RESERVE", reason, "SYSTEM_SYNC");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void returnItemsToStock(Map<Long, Integer> items, String reason, String operator) {
        if (items == null || items.isEmpty()) return;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            productRepository.findById(productId)
                    .ifPresent(p -> {
                        productRepository.addStockById(productId, qty);
                        logMovement(p.getName(), qty, "RETURN", reason, operator);
                    });
        }
    }

    // Добавьте этот метод в StockService.java
    @Transactional
    public void processCorrectionStock(Map<Long, Integer> items, String reference) {
        items.forEach((productId, qty) -> {
            productRepository.addStockById(productId, qty);
            // Специальный тип лога для инвентаризации/корректировки
            logMovement(
                    productRepository.findById(productId).get().getName(),
                    qty,
                    "CORRECTION",
                    "Корректировка по акту: " + reference,
                    "ADMIN"
            );
        });
    }


    @Transactional
    public void logMovement(String name, Integer qty, String type, String reason, String operator) {
        StockMovement m = new StockMovement();
        m.setProductName(name);
        m.setQuantityChange(qty);
        m.setType(type);
        m.setReason(reason);
        m.setOperator(operator != null ? operator : "UNKNOWN");
        m.setTimestamp(LocalDateTime.now());
        movementRepository.save(m);
    }
}