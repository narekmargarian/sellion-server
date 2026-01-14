package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Product;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);

    // НОВОЕ: Блокировка строки для точного расчета суммы и исключения конфликтов
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.name = :name")
    Optional<Product> findByNameWithLock(String name);

    // Атомарное списание (защита от минусов на складе)
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty " +
            "WHERE p.name = :name AND p.stockQuantity >= :qty")
    int deductStock(String name, Integer qty);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.name = :name")
    void addStock(String name, Integer qty);
}