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
        // 1. Проверка на дубликат по Android ID (защита от повторных нажатий кнопки "Отправить")
        if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
            log.info("Пропуск дубликата заказа: AndroidId [{}]", order.getAndroidId());
            return;
        }

        // 2. Блокировка всех товаров заказа для предотвращения Race Condition (стандарт 2026)
        // Используем метод с @Lock(LockModeType.PESSIMISTIC_WRITE)
        List<Product> lockedProducts = productRepository.findAllByIdWithLock(order.getItems().keySet());

        // Преобразуем в карту для быстрого поиска внутри цикла
        Map<Long, Product> productMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        order.setId(null);
        order.setStatus(OrderStatus.RESERVED);

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 3. Расчет стоимости на основе заблокированных данных
        for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
            Long productId = entry.getKey();
            Integer qtyInt = entry.getValue();

            if (qtyInt == null || qtyInt <= 0) continue;

            Product p = productMap.get(productId);
            if (p == null) {
                throw new RuntimeException("Ошибка синхронизации: Товар ID " + productId + " не найден в базе данных");
            }

            BigDecimal qty = BigDecimal.valueOf(qtyInt);

            // Расчет себестоимости и суммы продажи
            BigDecimal purchasePrice = Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO);
            BigDecimal salePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);

            totalCost = totalCost.add(purchasePrice.multiply(qty));
            totalAmount = totalAmount.add(salePrice.multiply(qty));
        }

        // 4. Фиксация сумм в объекте заказа
        order.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        order.setPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP)); // Для совместимости полей
        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));

        // 5. Выполнение резервирования на складе
        // Если товара не хватит, StockService выбросит RuntimeException и вся транзакция откатится
        stockService.reserveItemsFromStock(order.getItems(), "Android Sync: " + order.getShopName());

        // 6. Финальное сохранение заказа
        orderRepository.save(order);
        log.info("Заказ из Android успешно обработан: Магазин [{}], Сумма [{}]", order.getShopName(), totalAmount);
    }
}
