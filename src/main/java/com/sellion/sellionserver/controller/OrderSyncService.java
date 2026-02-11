package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.services.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final ProductRepository productRepository;

    @Transactional(rollbackFor = Exception.class)
    public void processOrderFromAndroid(Order order) {
        // 1. Проверка на дубликат
        if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
            log.info("Пропуск дубликата заказа: AndroidId [{}]", order.getAndroidId());
            return;
        }

        // 2. Блокировка товаров (PESSIMISTIC_WRITE) для предотвращения Race Condition
        List<Product> lockedProducts = productRepository.findAllByIdWithLock(order.getItems().keySet());

        Map<Long, Product> productMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        order.setId(null);
        order.setStatus(OrderStatus.RESERVED);

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // --- ЛОГИКА СКИДКИ (ВЫЧИТАНИЕ ПРОЦЕНТА) ---
        // Получаем процент клиента (например, 10.0)
        BigDecimal percent = Optional.ofNullable(order.getDiscountPercent()).orElse(BigDecimal.ZERO);

        // Вычисляем множитель СКИДКИ: (1 - percent/100).
        // Например, если 10%, то 1 - 0.10 = 0.90 (клиент платит 90% от цены)
        BigDecimal modifier = BigDecimal.ONE.subtract(
                percent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
        );
        // ------------------------------------------

        // 3. Расчет стоимости на основе заблокированных данных
        for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
            Long productId = entry.getKey();
            Integer qtyInt = entry.getValue();

            if (qtyInt == null || qtyInt <= 0) continue;

            Product p = productMap.get(productId);
            if (p == null) {
                throw new RuntimeException("Ошибка: Товар ID " + productId + " не найден");
            }

            BigDecimal qty = BigDecimal.valueOf(qtyInt);

            // Себестоимость (всегда базовая для отчетов прибыли)
            BigDecimal purchasePrice = Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO);

            // ЦЕНА ПРОДАЖИ С УЧЕТОМ СКИДКИ
            BigDecimal baseSalePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);

            // Вычисляем итоговую цену за 1 шт, округляем до целых (для ֏)
            BigDecimal finalPricePerUnit = baseSalePrice.multiply(modifier).setScale(0, RoundingMode.HALF_UP);

            totalCost = totalCost.add(purchasePrice.multiply(qty));
            totalAmount = totalAmount.add(finalPricePerUnit.multiply(qty));
        }

        // 4. Фиксация итогов
        order.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        order.setPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));

        // Итоговая сумма заказа (уже накоплена из округленных цен товаров)
        order.setTotalAmount(totalAmount.setScale(0, RoundingMode.HALF_UP));

        // 5. Резервирование склада
        stockService.reserveItemsFromStock(order.getItems(), "Заказ (скидка " + percent + "%): " + order.getShopName());

        // 6. Сохранение
        orderRepository.save(order);
        log.info("Заказ обработан. Магазин: {}, Скидка: {}%, Итоговая сумма: {} ֏",
                order.getShopName(), percent, order.getTotalAmount());
    }
}
