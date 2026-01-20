package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    @Transactional(rollbackFor = Exception.class)
    public void deductItemsFromStock(Map<Long, Integer> items, String reason, String operator) {
        if (items == null || items.isEmpty()) return;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            // Ищем товар для получения имени (для лога) и проверки существования
            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Товар ID " + productId + " не найден"));

            // Атомарное списание по ID
            int updatedRows = productRepository.deductStockById(productId, qty);

            if (updatedRows == 0) {
                throw new RuntimeException("Критический дефицит товара: " + p.getName() + " (ID: " + productId + ")");
            }

            logMovement(p.getName(), -qty, "SALE", reason, operator);
        }
    }

    /**
     * Возврат товара по ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnItemsToStock(Map<Long, Integer> items, String reason, String operator) {
        if (items == null || items.isEmpty()) return;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Товар ID " + productId + " не найден"));

            // Атомарное добавление по ID
            productRepository.addStockById(productId, qty);

            logMovement(p.getName(), qty, "RETURN", reason, operator);
        }
    }

    /**
     * Резервирование товаров по ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void reserveItemsFromStock(Map<Long, Integer> items, String reason) {
        if (items == null || items.isEmpty()) return;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long productId = entry.getKey();
            Integer qty = entry.getValue();

            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Товар ID " + productId + " не найден"));

            int updatedRows = productRepository.deductStockById(productId, qty);

            if (updatedRows == 0) {
                throw new RuntimeException("Недостаточно товара для резервирования: " + p.getName());
            }

            logMovement(p.getName(), -qty, "SALE", reason, "SYSTEM/MANAGER");
        }
    }

    @Transactional
    public void logMovement(String name, Integer qty, String type, String reason, String operator) {
        StockMovement m = new StockMovement();
        m.setProductName(name); // В логах оставляем имя для удобства чтения человеком
        m.setQuantityChange(qty);
        m.setType(type);
        m.setReason(reason);
        m.setOperator(operator != null ? operator : "UNKNOWN");
        movementRepository.save(m);
    }
}