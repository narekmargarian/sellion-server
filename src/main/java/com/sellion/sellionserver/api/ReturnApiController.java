package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnApiController {

    private final ReturnOrderRepository returnOrderRepository;

    public ReturnApiController(ReturnOrderRepository returnOrderRepository) {
        this.returnOrderRepository = returnOrderRepository;
    }
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncReturns(@RequestBody List<ReturnOrder> returns) {
        if (returns != null && !returns.isEmpty()) {
            for (ReturnOrder ret : returns) {
                ret.setId(null); // Важно для новой записи в MySQL
            }
            returnOrderRepository.saveAll(returns);
        }
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }
}