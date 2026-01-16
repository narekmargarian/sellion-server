package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByStatus(OrderStatus status);

    // Новый метод: Найти заказы, созданные между startOfDay и endOfDay
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startOfDay AND o.createdAt <= :endOfDay")
    List<Order> findOrdersBetweenDates(@Param("startOfDay") String startOfDay, @Param("endOfDay") String endOfDay);


    @Query("SELECT o FROM Order o WHERE o.managerId = :managerId AND o.createdAt >= :start AND o.createdAt <= :end")
    List<Order> findOrdersByManagerAndDateRange(@Param("managerId") String managerId, @Param("start") String start, @Param("end") String end);

    @Query("SELECT o FROM Order o WHERE o.managerId = :managerId " +
            "AND o.deliveryDate = :date AND o.status != 'CANCELLED'")
    List<Order> findDailyRouteOrders(@Param("managerId") String managerId, @Param("date") LocalDate date);

}