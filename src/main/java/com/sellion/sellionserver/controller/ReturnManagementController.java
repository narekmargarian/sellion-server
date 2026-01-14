package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
public class ReturnManagementController {

    private final ReturnOrderRepository returnOrderRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;

    @PutMapping("/{id}/edit")
    @Transactional
    public ResponseEntity<?> fullEditReturn(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ReturnOrder returnOrder = returnOrderRepository.findById(id).orElseThrow();

        // 1. ОТКАТ СТАРОГО ВОЗВРАТА
        // Так как возврат ПРИБАВЛЯЕТ товар, то для отката мы его СПИСЫВАЕМ
        stockService.deductItemsFromStock(returnOrder.getItems());

        // 2. ОБНОВЛЕНИЕ ПОЛЕЙ
        returnOrder.setShopName((String) payload.get("shopName"));
        returnOrder.setReturnDate((String) payload.get("returnDate"));
        returnOrder.setReturnReason((String) payload.get("returnReason"));

        // 3. НОВЫЙ ПРИХОД ОТ ВОЗВРАТА
        Map<String, Integer> newItems = (Map<String, Integer>) payload.get("items");

        // Используем твой метод, который делает + к складу
        stockService.returnItemsToStock(newItems);

        // 4. ПЕРЕСЧЕТ СУММЫ
        double newTotalAmount = 0;
        for (Map.Entry<String, Integer> entry : newItems.entrySet()) {
            Product p = productRepository.findByName(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Товар не найден: " + entry.getKey()));
            newTotalAmount += p.getPrice() * entry.getValue();
        }

        returnOrder.setItems(newItems);
        returnOrder.setTotalAmount(newTotalAmount);
        returnOrderRepository.save(returnOrder);

        return ResponseEntity.ok(Map.of("newTotal", newTotalAmount));
    }

}
