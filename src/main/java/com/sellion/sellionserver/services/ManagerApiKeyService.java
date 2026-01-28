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
    private final PasswordEncoder passwordEncoder; // BCrypt

    public List<ManagerApiKey> findAllKeys() {
        return apiKeyRepository.findAll();
    }

    /**
     * Генерирует (или обновляет) хэш ключа на основе ID менеджера.
     * Используется формат: sellion.rivento.mg.managerId
     */
    @Transactional
    public void generateKeyForManager(String managerId) {
        // 1. Формируем сырой ключ по вашему стандарту
        String rawKey = "sellion.rivento.mg." + managerId;

        // 2. Хэшируем его для безопасного хранения в БД
        String hashedKey = passwordEncoder.encode(rawKey);

        // 3. Ищем существующую запись или создаем новую
        ManagerApiKey apiKeyEntry = apiKeyRepository.findById(managerId)
                .orElse(new ManagerApiKey());

        apiKeyEntry.setManagerId(managerId);
        apiKeyEntry.setApiKeyHash(hashedKey);

        apiKeyRepository.save(apiKeyEntry);

        log.info("API-ключ для менеджера {} успешно захеширован и сохранен в БД", managerId);
    }

    /**
     * Метод для массовой инициализации всех менеджеров из вашего Enum (ManagerId)
     * Поможет быстро заполнить базу правильными хэшами.
     */
    @Transactional
    public void initializeAllManagerKeys(List<String> managerIds) {
        for (String id : managerIds) {
            generateKeyForManager(id);
        }
        log.info("Все ключи менеджеров ({}) успешно обновлены в базе.", managerIds.size());
    }

    @Transactional
    public void deleteKey(String managerId) {
        apiKeyRepository.deleteById(managerId);
        log.info("Ключ для менеджера {} удален из базы", managerId);
    }
}
