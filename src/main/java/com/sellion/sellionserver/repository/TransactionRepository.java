package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Получение истории по клиенту для Акта сверки
    List<Transaction> findAllByClientIdOrderByTimestampAsc(Long clientId);

}