package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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


    // ИСПРАВЛЕНО: Теперь работаем с LocalDateTime
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    List<Order> findOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.managerId = :mId AND o.createdAt BETWEEN :start AND :end")
    List<Order> findOrdersByManagerAndDateRange(@Param("mId") String mId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.invoiceId IS NOT NULL AND o.status != 'CANCELLED'")
    List<Order> findInvoicedOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findByManagerId(String managerId);

    boolean existsByAndroidId(String androidId);

    @Query("SELECT o FROM Order o WHERE o.managerId = :managerId AND o.deliveryDate = :date AND o.status != 'CANCELLED'")
    List<Order> findDailyRouteOrders(@Param("managerId") String managerId, @Param("date") LocalDate date);


    // ИСПРАВЛЕНО: Добавлен Pageable для ограничения количества записей (напр. по 50 на страницу)
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    Page<Order> findOrdersBetweenDatesPaged(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    // То же самое для фильтрации по менеджеру
    @Query("SELECT o FROM Order o WHERE o.managerId = :mId AND o.createdAt BETWEEN :start AND :end")
    Page<Order> findOrdersByManagerAndDateRangePaged(
            @Param("mId") String mId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
}

