package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, Long> {

    List<ReturnOrder> findAllByStatus(ReturnStatus status);

    // ИСПРАВЛЕНО: Тип LocalDateTime для точного поиска по дате и времени
    @Query("SELECT r FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end")
    List<ReturnOrder> findReturnsBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r FROM ReturnOrder r WHERE r.managerId = :managerId AND r.createdAt BETWEEN :start AND :end")
    List<ReturnOrder> findReturnsByManagerAndDateRange(@Param("managerId") String managerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ReturnOrder> findByManagerId(String managerId);

    // ИСПРАВЛЕНО: Тип LocalDateTime для агрегации данных
    @Query("SELECT SUM(r.totalAmount) FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end AND r.status = 'CONFIRMED'")
    BigDecimal sumConfirmedReturns(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}