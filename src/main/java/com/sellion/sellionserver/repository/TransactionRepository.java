package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByClientIdOrderByTimestampAsc(Long clientId);

    /**
     * ИСПРАВЛЕНО: Эффективный поиск транзакций клиента за период напрямую через СУБД.
     */
    @Query("SELECT t FROM Transaction t WHERE t.clientId = :clientId " +
            "AND t.timestamp >= :startDateTime AND t.timestamp <= :endDateTime " +
            "ORDER BY t.timestamp ASC")
    List<Transaction> findAllByClientIdAndTimestampBetween(
            @Param("clientId") Long clientId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

}