package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnApiController {

    private final ReturnOrderRepository returnOrderRepository;

    public ReturnApiController(ReturnOrderRepository returnOrderRepository) {
        this.returnOrderRepository = returnOrderRepository;
    }

    /**
     * Синхронизация возвратов из Android.
     *
     * @Transactional гарантирует целостность данных в MySQL.
     */
    @Transactional
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncReturns(@RequestBody List<ReturnOrder> returns) {
        // Логируем количество полученных записей для отладки в консоли сервера
        System.out.println(">>> ПОЛУЧЕНО ВОЗВРАТОВ: " + (returns != null ? returns.size() : 0));

        if (returns != null && !returns.isEmpty()) {
            for (ReturnOrder ret : returns) {
                // Обнуляем ID, чтобы MySQL использовал AUTO_INCREMENT
                ret.setId(null);
            }
            returnOrderRepository.saveAll(returns);
        }

        // Возвращаем JSON {"status": "success"}
        return ResponseEntity.ok(Collections.singletonMap("status", "success"));
    }
}