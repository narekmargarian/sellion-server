package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.config.ManagerApiKey;
import com.sellion.sellionserver.services.ManagerApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/manager-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Только администратор может управлять ключами
public class ManagerApiKeyApiController {

    private final ManagerApiKeyService apiKeyService;

    @GetMapping
    public List<ManagerApiKey> getAllKeys() {
        return apiKeyService.findAllKeys();
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateKey(@RequestBody Map<String, String> payload) {
        String managerId = payload.get("managerId");
        if (managerId == null || managerId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуется ID менеджера"));
        }
        ManagerApiKey newKey = apiKeyService.generateNewKeyForManager(managerId);
        return ResponseEntity.ok(newKey);
    }

    @DeleteMapping("/delete/{managerId}")
    public ResponseEntity<?> deleteKey(@PathVariable String managerId) {
        apiKeyService.deleteKey(managerId);
        return ResponseEntity.ok(Map.of("message", "Ключ удален для менеджера: " + managerId));
    }
}