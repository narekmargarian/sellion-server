package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.PromoAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PromoActionRepository extends JpaRepository<PromoAction, Long> {
    @Query("SELECT p FROM PromoAction p WHERE p.startDate <= :date AND p.endDate >= :date AND p.confirmed = true")
    List<PromoAction> findActivePromos(@Param("date") LocalDate date);

    @Query("SELECT p FROM PromoAction p WHERE p.startDate >= :start AND p.endDate <= :end")
    List<PromoAction> findByPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);
}

