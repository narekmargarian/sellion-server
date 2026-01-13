package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;


    @Scheduled(cron = "0 */3 * * * *")
    @Transactional
    public void updateStockFromOrders() {
        System.out.println(">>> SELLION LOG: Запуск синхронизации склада...");

        // 1. Берем заказы со статусом NEW (те, что пришли с Android и еще не обработаны складом)
        // В репозитории метод должен быть: List<Order> findAllByStatus(OrderStatus status);
        orderRepository.findAllByStatus(OrderStatus.NEW).forEach(order -> {

            Map<String, Integer> items = order.getItems();
            if (items != null && !items.isEmpty()) {

                for (Map.Entry<String, Integer> entry : items.entrySet()) {
                    String productName = entry.getKey();
                    Integer quantityInOrder = entry.getValue();

                    // 2. Ищем товар по имени и обновляем его количество на складе
                    productRepository.findByName(productName).ifPresent(product -> {
                        int currentStock = (product.getStockQuantity() != null) ? product.getStockQuantity() : 0;

                        // Вычитаем заказанное количество
                        int newStock = currentStock - quantityInOrder;

                        // Защита: остаток не может быть меньше 0
                        product.setStockQuantity(Math.max(newStock, 0));

                        productRepository.save(product);
                        System.out.println(">>> Товар [" + productName + "] списан: " + quantityInOrder + " шт.");
                    });
                }
            }

            // 3. МЕНЯЕМ СТАТУС НА PROCESSED.
            // Это гарантирует, что при следующем запуске (через 3 мин) этот заказ не будет обработан повторно.
            order.setStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);
        });

        System.out.println(">>> SELLION LOG: Синхронизация склада завершена успешно.");
    }
}