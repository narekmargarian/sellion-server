package com.sellion.sellionserver.services;

import com.sellion.sellionserver.config.ManagerApiKey;
import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagerApiKeyService {
    private final ManagerApiKeyRepository apiKeyRepository;

    public List<ManagerApiKey> findAllKeys() {
        return apiKeyRepository.findAll();
    }

    public Optional<ManagerApiKey> findByKeyHash(String apiKeyHash) {
        return apiKeyRepository.findByApiKeyHash(apiKeyHash);
    }

    @Transactional
    public ManagerApiKey generateNewKeyForManager(String managerId) {
        // Генерируем уникальный, длинный ключ
        String newApiKey = UUID.randomUUID().toString().replace("-", "");

        ManagerApiKey existingKey = apiKeyRepository.findById(managerId).orElse(new ManagerApiKey());
        existingKey.setManagerId(managerId);
        // Пока храним как есть, без хэширования, чтобы было проще найти в фильтре
        existingKey.setApiKeyHash(newApiKey);

        return apiKeyRepository.save(existingKey);
    }

    @Transactional
    public void deleteKey(String managerId) {
        apiKeyRepository.deleteById(managerId);
    }



    @Transactional
    public void saveKeyForManager(String managerId, String rawApiKey) {
        ManagerApiKey key = new ManagerApiKey();
        key.setManagerId(managerId);
        key.setApiKeyHash(rawApiKey); // Сохраняем полученный Android ID
        apiKeyRepository.save(key);
    }
}
