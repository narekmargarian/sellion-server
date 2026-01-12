package com.sellion.sellionserver.services;

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

    // Запуск каждые 3 часа: 0 0 */3 * * *
    // Запуск каждые 10 минут:  "0 */10 * * * *"
    @Scheduled(cron = "0 */3 * * * *")
    @Transactional
    public void updateStockFromOrders() {
        System.out.println(">>> ЗАПУСК СИНХРОНИЗАЦИИ СКЛАДА. (10 мин)..");

        // 1. Получаем все заказы со статусом PENDING (которые еще не обработаны складом)
        orderRepository.findAllByStatus("PENDING").forEach(order -> {
            Map<String, Integer> items = order.getItems();

            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                String productName = entry.getKey();
                Integer quantityInOrder = entry.getValue();

                // 2. Ищем товар в базе и уменьшаем остаток
                productRepository.findByName(productName).ifPresent(product -> {
                    int newStock = product.getStockQuantity() - quantityInOrder;
                    if (newStock < 0) newStock = 0; // Защита от отрицательного остатка
                    product.setStockQuantity(newStock);
                    productRepository.save(product);
                });
            }

            // 3. Помечаем заказ как "PROCESSED" или "COMPLETED", чтобы не списывать его дважды
            order.setStatus("PROCESSED");
            orderRepository.save(order);
        });

        System.out.println(">>> СКЛАД ОБНОВЛЕН УСПЕШНО.");
    }
}