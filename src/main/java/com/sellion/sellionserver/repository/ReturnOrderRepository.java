package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.entity.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, Long> {

    List<ReturnOrder> findAllByStatus(ReturnStatus status);

    @Query("SELECT r FROM ReturnOrder r WHERE r.createdAt >= :startOfDay AND r.createdAt <= :endOfDay")
    List<ReturnOrder> findReturnsBetweenDates(@Param("startOfDay") String startOfDay, @Param("endOfDay") String endOfDay);

    List<ReturnOrder> findAllByManagerId(String managerId);

}