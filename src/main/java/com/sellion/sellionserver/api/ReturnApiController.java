package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
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
    public ResponseEntity<?> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns != null) {
            for (ReturnOrder ret : returns) {
                ret.setId(null);
                // ИСПРАВЛЕНИЕ: Используйте DRAFT вместо PENDING
                ret.setStatus(ReturnStatus.DRAFT);
            }
            returnOrderRepository.saveAll(returns);
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}