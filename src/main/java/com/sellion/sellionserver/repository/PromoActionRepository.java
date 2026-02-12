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

    // Для заказов: Активные акции менеджера на конкретную дату
    @Query("SELECT p FROM PromoAction p WHERE p.startDate <= :date AND p.endDate >= :date " +
            "AND p.confirmed = true AND p.managerId = :managerId")
    List<PromoAction> findActivePromosForManager(@Param("date") LocalDate date, @Param("managerId") String managerId);

    // Для заказов (ADMIN): Все активные акции на дату
    @Query("SELECT p FROM PromoAction p WHERE p.startDate <= :date AND p.endDate >= :date AND p.confirmed = true")
    List<PromoAction> findActivePromos(@Param("date") LocalDate date);

    // УЛУЧШЕНО: Поиск акций, которые ПЕРЕСЕКАЮТСЯ с выбранным периодом (для менеджера)
    @Query("SELECT p FROM PromoAction p WHERE p.managerId = :managerId AND p.startDate <= :end AND p.endDate >= :start")
    List<PromoAction> findByPeriodAndManager(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("managerId") String managerId);

    // УЛУЧШЕНО: Поиск акций, которые ПЕРЕСЕКАЮТСЯ с выбранным периодом (для ADMIN)
    @Query("SELECT p FROM PromoAction p WHERE p.startDate <= :end AND p.endDate >= :start")
    List<PromoAction> findByPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
