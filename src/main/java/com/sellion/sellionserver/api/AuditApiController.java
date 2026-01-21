package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.AuditLog;
import com.sellion.sellionserver.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditApiController {
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/order/{id}")
    public List<AuditLog> getOrderLogs(@PathVariable Long id) {
        return auditLogRepository.findAllByEntityIdAndEntityTypeOrderByTimestampDesc(id, "ORDER");
    }
}
