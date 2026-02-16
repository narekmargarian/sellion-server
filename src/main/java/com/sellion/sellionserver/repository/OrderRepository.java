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


    // Найти заказы между датами, исключая списания
//    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.type != 'WRITE_OFF'")
//    List<Order> findOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);


    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
            "AND (o.type IS NULL OR o.type != 'WRITE_OFF')")
    List<Order> findOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Сумма подтвержденных возвратов (уже было, просто проверьте наличие)
    @Query("SELECT SUM(r.totalAmount) FROM ReturnOrder r WHERE r.createdAt BETWEEN :start AND :end AND r.status = 'CONFIRMED'")
    BigDecimal sumConfirmedReturns(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);


    @Query("SELECT o FROM Order o WHERE o.managerId = :mId AND o.createdAt BETWEEN :start AND :end")
    List<Order> findOrdersByManagerAndDateRange(@Param("mId") String mId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.invoiceId IS NOT NULL AND o.status != 'CANCELLED'")
    List<Order> findInvoicedOrdersBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findByManagerId(String managerId);



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


    boolean existsByAndroidId(String androidId);
    List<Order> findByManagerIdAndCreatedAtBetween(String managerId, LocalDateTime start, LocalDateTime end);

    // В OrderRepository.java
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status != 'CANCELLED' AND o.type = 'SALE'")
    BigDecimal sumTotalSales(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(o.totalPurchaseCost) FROM Order o WHERE o.createdAt BETWEEN :start AND :end AND o.status != 'CANCELLED'")
    BigDecimal sumTotalCost(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Новый метод специально для точной выборки по датам
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end AND o.status != 'CANCELLED'")
    List<Order> findOrdersForPrintSummary(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);


}

