package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.ClientRepository;
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
    // ДОБАВЛЕНО: Репозиторий для получения данных о клиентах из твоей БД
    private final ClientRepository clientRepository;

    @Transactional(rollbackFor = Exception.class)
    public void processOrderFromAndroid(Order order) {

        // 1. Проверка на дубликат (без изменений)
        if (order.getAndroidId() != null && orderRepository.existsByAndroidId(order.getAndroidId())) {
            log.info("Пропуск дубликата заказа: AndroidId [{}]", order.getAndroidId());
            return;
        }

        // 2. Блокировка товаров (PESSIMISTIC_WRITE)
        List<Product> lockedProducts = productRepository.findAllByIdWithLock(order.getItems().keySet());

        Map<Long, Product> productMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, java.util.function.Function.identity()));

        order.setId(null);
        order.setStatus(OrderStatus.RESERVED);

        // --- КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: ЛОГИКА ОПРЕДЕЛЕНИЯ СКИДКИ МАГАЗИНА ИЗ ТВОЕЙ БД ---
        // Пытаемся получить процент из пришедшего заказа
        BigDecimal shopPercent = Optional.ofNullable(order.getDiscountPercent()).orElse(BigDecimal.ZERO);

        // Если из приложения пришел 0 (или пусто), берем актуальный процент из твоей базы данных по имени магазина
        if (shopPercent.compareTo(BigDecimal.ZERO) == 0) {
            shopPercent = clientRepository.findByName(order.getShopName())
                    .map(Client::getDefaultPercent)
                    .orElse(BigDecimal.ZERO);

            // Устанавливаем правильный процент в объект заказа для сохранения в БД
            // Теперь в заголовке заказа в офисе будет отображаться "5%", а не "0%"
            order.setDiscountPercent(shopPercent);
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 3. Расчет стоимости каждой позиции
        for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
            Long productId = entry.getKey();
            Integer qtyInt = entry.getValue();

            if (qtyInt == null || qtyInt <= 0) continue;

            Product p = productMap.get(productId);
            if (p == null) {
                throw new RuntimeException("Ошибка: Товар ID " + productId + " не найден");
            }

            BigDecimal qty = BigDecimal.valueOf(qtyInt);

            // --- ЛОГИКА ПРИОРИТЕТА АКЦИИ ---
            BigDecimal currentItemPercent;
            // Акция из Android всегда имеет приоритет. Если её нет — используем shopPercent из БД.
            if (order.getAppliedPromoItems() != null && order.getAppliedPromoItems().containsKey(productId)) {
                currentItemPercent = order.getAppliedPromoItems().get(productId);
            } else {
                currentItemPercent = shopPercent;
            }

            // Модификатор цены
            BigDecimal itemModifier = BigDecimal.ONE.subtract(
                    currentItemPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
            );

            BigDecimal baseSalePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);
            BigDecimal finalPricePerUnit = baseSalePrice.multiply(itemModifier).setScale(2, RoundingMode.HALF_UP);

            // СУММА СТРОКИ
            BigDecimal itemLineTotal = finalPricePerUnit.multiply(qty);
            totalAmount = totalAmount.add(itemLineTotal);

            // СЕБЕСТОИМОСТЬ
            BigDecimal purchasePrice = Optional.ofNullable(p.getPurchasePrice()).orElse(BigDecimal.ZERO);
            totalCost = totalCost.add(purchasePrice.multiply(qty));
        }

        // 4. Фиксация итогов в БД
        order.setTotalPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        order.setPurchaseCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));

        // 5. Резервирование склада
        stockService.reserveItemsFromStock(order.getItems(), "Android Заказ: " + order.getShopName());

        // 6. Сохранение
        orderRepository.save(order);
        log.info("Заказ синхронизирован. Магазин: {}, Применен процент из БД: {}%, Итого: {} ֏",
                order.getShopName(), shopPercent, order.getTotalAmount());
    }
}
