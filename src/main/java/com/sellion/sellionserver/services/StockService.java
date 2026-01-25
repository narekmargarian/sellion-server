package com.sellion.sellionserver.services;

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
    @Transactional(rollbackFor = Exception.class)
    public void deductItemsFromStock(Map<Long, Integer> items, String reason, String operator) {
        if (items == null || items.isEmpty()) return;

        // 1. Блокируем все товары заказа в БД одним запросом для предотвращения Race Condition
        List<Product> products = productRepository.findAllByIdWithLock(items.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qtyToDeduct = entry.getValue();

            if (qtyToDeduct == null || qtyToDeduct <= 0) continue;

            Product p = productMap.get(productId);
            if (p == null) {
                throw new RuntimeException("Критическая ошибка: Товар ID " + productId + " не найден при списании!");
            }

            // 2. Проверка остатка на заблокированном объекте
            int currentStock = (p.getStockQuantity() != null) ? p.getStockQuantity() : 0;
            if (currentStock < qtyToDeduct) {
                throw new RuntimeException("Недостаточно товара: " + p.getName() +
                        " (Нужно: " + qtyToDeduct + ", в наличии: " + currentStock + ")");
            }

            // 3. Обновление и сохранение
            p.setStockQuantity(currentStock - qtyToDeduct);
            productRepository.save(p);

            logMovement(p.getName(), -qtyToDeduct, "SALE", reason, operator);
        }
    }

    /**
     * Резервирование товара (для заказов с Android) с блокировкой.
     */
    @Transactional(rollbackFor = Exception.class)
    public void reserveItemsFromStock(Map<Long, Integer> items, String reason) {
        if (items == null || items.isEmpty()) return;

        // Блокируем товары для безопасного резерва
        List<Product> products = productRepository.findAllByIdWithLock(items.keySet());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            if (qty == null || qty <= 0) continue;

            Product p = productMap.get(productId);
            if (p == null) {
                throw new RuntimeException("Товар ID " + productId + " не найден для резерва");
            }

            int currentStock = (p.getStockQuantity() != null) ? p.getStockQuantity() : 0;
            if (currentStock < qty) {
                throw new RuntimeException("Не удалось зарезервировать " + p.getName() + ". Недостаточно остатка.");
            }

            p.setStockQuantity(currentStock - qty);
            productRepository.save(p);

            logMovement(p.getName(), -qty, "RESERVE", reason, "SYSTEM_SYNC");
        }
    }

    /**
     * Возврат товара на склад (отмена заказа или возврат от клиента).
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnItemsToStock(Map<Long, Integer> items, String reason, String operator) {
        if (items == null || items.isEmpty()) return;

        // Блокируем для безопасного прибавления
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
                    logMovement(p.getName(), qty, "RETURN", reason, operator);
                } else {
                    log.warn("Попытка возврата несуществующего товара ID: {}", productId);
                }
            }
        });
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
    @Transactional(propagation = Propagation.REQUIRED)
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
