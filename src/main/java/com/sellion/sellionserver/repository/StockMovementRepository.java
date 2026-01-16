package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    // Метод для получения истории конкретного товара
    List<StockMovement> findAllByProductNameOrderByTimestampDesc(String productName);
}
