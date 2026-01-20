package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Оставляем только один метод для получения всей истории по убыванию даты
    List<AuditLog> findAllByOrderByTimestampDesc();

    // Фильтр для конкретной сущности (заказа/товара)
    List<AuditLog> findAllByEntityIdAndEntityTypeOrderByTimestampDesc(Long entityId, String entityType);
}