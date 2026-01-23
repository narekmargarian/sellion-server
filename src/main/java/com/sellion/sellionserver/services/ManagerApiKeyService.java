package com.sellion.sellionserver.services;

import com.sellion.sellionserver.config.ManagerApiKey;
import com.sellion.sellionserver.repository.ManagerApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerApiKeyService {
    private final ManagerApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder; // Используем BCrypt

    public List<ManagerApiKey> findAllKeys() {
        return apiKeyRepository.findAll();
    }

    @Transactional
    public ManagerApiKey generateNewKeyForManager(String managerId) {
        // 1. Генерируем случайный ключ (UUID)
        String rawKey = UUID.randomUUID().toString().replace("-", "");

        // 2. Хэшируем его перед сохранением
        String hashedKey = passwordEncoder.encode(rawKey);

        ManagerApiKey apiKeyEntry = apiKeyRepository.findById(managerId)
                .orElse(new ManagerApiKey());

        apiKeyEntry.setManagerId(managerId);
        apiKeyEntry.setApiKeyHash(hashedKey);
        apiKeyRepository.save(apiKeyEntry);

        // ВАЖНО: Возвращаем объект с СЫРЫМ ключом только один раз, чтобы показать админу
        ManagerApiKey response = new ManagerApiKey();
        response.setManagerId(managerId);
        response.setApiKeyHash(rawKey); // Здесь на мгновение передаем сырой ключ для UI

        log.info("Сгенерирован новый API-ключ для менеджера: {}", managerId);
        return response;
    }

    @Transactional
    public void deleteKey(String managerId) {
        apiKeyRepository.deleteById(managerId);
    }
}
