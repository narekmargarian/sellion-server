package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT r FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end")
    List<ReturnOrder> findReturnsBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r FROM ReturnOrder r WHERE r.managerId = :managerId AND r.createdAt BETWEEN :start AND :end")
    List<ReturnOrder> findReturnsByManagerAndDateRange(@Param("managerId") String managerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ReturnOrder> findByManagerId(String managerId);

    @Query("SELECT SUM(r.totalAmount) FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end AND r.status = 'CONFIRMED'")
    BigDecimal sumConfirmedReturns(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // --- НОВЫЕ МЕТОДЫ ДЛЯ СЕРВЕРНОГО ПОИСКА ВОЗВРАТОВ ПО МАГАЗИНУ ---
    @Query("SELECT r FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end " +
            "AND (:search IS NULL OR :search = '' OR LOWER(r.shopName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ReturnOrder> findReturnsBetweenDatesWithSearchPaged(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT r FROM ReturnOrder r WHERE r.managerId = :mId AND r.createdAt BETWEEN :start AND :end " +
            "AND (:search IS NULL OR :search = '' OR LOWER(r.shopName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ReturnOrder> findReturnsByManagerWithSearchPaged(
            @Param("mId") String mId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search,
            Pageable pageable
    );
    // -------------------------------------------------------------

    boolean existsByAndroidId(String androidId);
    List<ReturnOrder> findByManagerIdAndCreatedAtBetween(String managerId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT r.id FROM ReturnOrder r WHERE (:mId IS NULL OR r.managerId = :mId) " +
            "AND r.createdAt >= :start AND r.createdAt <= :end")
    List<Long> findAllIdsByFilters(@Param("mId") String mId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}