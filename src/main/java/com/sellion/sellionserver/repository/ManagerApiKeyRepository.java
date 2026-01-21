package com.sellion.sellionserver.repository;


import com.sellion.sellionserver.config.ManagerApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ManagerApiKeyRepository extends JpaRepository<ManagerApiKey, String> {
    // Найти ключ по его хэшу (для проверки)
    Optional<ManagerApiKey> findByApiKeyHash(String apiKeyHash);
}