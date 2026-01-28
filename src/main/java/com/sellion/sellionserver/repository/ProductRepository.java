package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Product;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);

    // Идеально для расчетов: загружаем пачку товаров и блокируем их для записи
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllByIdWithLock(@Param("ids") Collection<Long> ids);

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


    Optional<Product> findByNameAndIsDeletedFalse(String name);

    // Метод для мягкого удаления товара
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.isDeleted = true WHERE p.id = :id")
    void softDeleteById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);


    // Атомарное обновление цены (если потребуется массово)
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.purchasePrice = :price WHERE p.id = :id")
    void updatePurchasePrice(@Param("id") Long id, @Param("price") BigDecimal price);


    @Query("SELECT p FROM Product p WHERE p.isDeleted = false")
    List<Product> findAllActive();

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty WHERE p.id = :id AND p.stockQuantity >= :qty")
    int deductStockById(@Param("id") Long id, @Param("qty") Integer qty);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :id")
    void addStockById(@Param("id") Long id, @Param("qty") Integer qty);


    // Добавьте этот метод для получения всех не удаленных товаров
    List<Product> findAllByIsDeletedFalse();

    // Если вам нужна группировка по категориям в алфавитном порядке, можно использовать этот:
    List<Product> findAllByIsDeletedFalseOrderByCategoryAscNameAsc();





}