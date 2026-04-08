package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop50ByOrderByTimestampDesc();
    List<AuditLog> findAllByEntityIdAndEntityTypeOrderByTimestampDesc(Long entityId, String entityType);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoffDate")
    void deleteOlderThan(LocalDateTime cutoffDate);
}