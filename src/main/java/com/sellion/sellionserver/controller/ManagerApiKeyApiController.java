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
@PreAuthorize("hasRole('ADMIN')")
public class ManagerApiKeyApiController {

    private final ManagerApiKeyService apiKeyService;

    @GetMapping
    public List<ManagerApiKey> getAllKeys() {
        return apiKeyService.findAllKeys();
    }





    /**
     * Генерирует хэш ключа для менеджера.
     * Теперь метод просто подтверждает успех, так как ключ стандартный.
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateKey(@RequestBody Map<String, String> payload) {
        String managerId = payload.get("managerId");

        if (managerId == null || managerId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуется ID менеджера"));
        }

        // Вызываем обновленный метод сервиса
        apiKeyService.generateKeyForManager(managerId);

        return ResponseEntity.ok(Map.of(
                "message", "Ключ для менеджера " + managerId + " успешно создан/обновлен",
                "format", "sellion.rivento.mg." + managerId
        ));
    }

    @DeleteMapping("/delete/{managerId}")
    public ResponseEntity<?> deleteKey(@PathVariable String managerId) {
        apiKeyService.deleteKey(managerId);
        return ResponseEntity.ok(Map.of("message", "Ключ удален для менеджера: " + managerId));
    }
}
