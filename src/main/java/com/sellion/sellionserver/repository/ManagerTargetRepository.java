package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.ManagerTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Month;
import java.time.Year;

@Repository
public interface ManagerTargetRepository extends JpaRepository<ManagerTarget, Long> {
    ManagerTarget findByManagerIdAndMonthAndYear(String managerId, Month month, Year year);
}
