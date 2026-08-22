package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByStatus(OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
            "AND (o.type IS NULL OR o.type != 'WRITE_OFF')")
    List<Order> findOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(r.totalAmount) FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end AND r.status = 'CONFIRMED'")
    BigDecimal sumConfirmedReturns(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.managerId = :mId AND o.createdAt BETWEEN :start AND :end")
    List<Order> findOrdersByManagerAndDateRange(@Param("mId") String mId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.invoiceId IS NOT NULL AND o.status != 'CANCELLED'")
    List<Order> findInvoicedOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findByManagerId(String managerId);

    @Query("SELECT o FROM Order o WHERE o.managerId = :managerId AND o.deliveryDate = :date AND o.status != 'CANCELLED'")
    List<Order> findDailyRouteOrders(@Param("managerId") String managerId, @Param("date") LocalDate date);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    Page<Order> findOrdersBetweenDatesPaged(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o WHERE o.managerId = :mId AND o.createdAt BETWEEN :start AND :end")
    Page<Order> findOrdersByManagerAndDateRangePaged(
            @Param("mId") String mId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    // --- НОВЫЕ МЕТОДЫ ДЛЯ СЕРВЕРНОГО ПОИСКА ЗАКАЗОВ ПО МАГАЗИНУ ---
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
            "AND (:search IS NULL OR :search = '' OR LOWER(o.shopName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> findOrdersWithSearchPaged(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o WHERE o.managerId = :mId AND o.createdAt BETWEEN :start AND :end " +
            "AND (:search IS NULL OR :search = '' OR LOWER(o.shopName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> findOrdersByManagerWithSearchPaged(
            @Param("mId") String mId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("search") String search,
            Pageable pageable
    );
    // -------------------------------------------------------------

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.invoiceId IS NOT NULL AND o.status != 'CANCELLED' AND o.managerId = :managerId")
    List<Order> findInvoicedOrdersBetweenDatesAndManager(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("managerId") String managerId);

    @Query("SELECT DISTINCT o.managerId FROM Order o WHERE o.managerId IS NOT NULL")
    List<String> findDistinctManagers();

    boolean existsByAndroidId(String androidId);
    List<Order> findByManagerIdAndCreatedAtBetween(String managerId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status != 'CANCELLED' AND o.type = 'SALE'")
    BigDecimal sumTotalSales(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(o.totalPurchaseCost) FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status != 'CANCELLED'")
    BigDecimal sumTotalCost(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end AND o.status != 'CANCELLED'")
    List<Order> findOrdersForPrintSummary(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.id FROM Order o WHERE (:mId IS NULL OR o.managerId = :mId) " +
            "AND o.createdAt >= :start AND o.createdAt <= :end")
    List<Long> findAllIdsByFilters(@Param("mId") String mId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);
}