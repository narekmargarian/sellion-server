package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.services.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderSyncService {
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final ProductRepository productRepository;

    @Transactional(rollbackFor = Exception.class)
    public void processOrderFromAndroid(Order order) {
        order.setId(null);
        order.setStatus(OrderStatus.RESERVED);

        // Расчет себестоимости через BigDecimal (2026 стандарт)
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
            Product p = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар ID " + entry.getKey() + " не найден"));

            BigDecimal purchasePrice = (p.getPurchasePrice() != null) ? p.getPurchasePrice() : BigDecimal.ZERO;
            totalCost = totalCost.add(purchasePrice.multiply(BigDecimal.valueOf(entry.getValue())));
        }
        order.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));

        // Сначала резервируем на складе. Если не хватит — будет откат всей транзакции
        stockService.reserveItemsFromStock(order.getItems(), "Android Sync: " + order.getShopName());

        // Сохраняем заказ в базу
        orderRepository.save(order);
    }
}