package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Client;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByName(String name);

    // Пагинация для WEB: Все активные клиенты
    Page<Client> findAllByIsDeletedFalse(Pageable pageable);

    // Поиск с фильтром категории для пагинации (Web)
    Page<Client> findAllByIsDeletedFalseAndCategory(String category, Pageable pageable);


    // Для Android: Список всех клиентов конкретного менеджера
    List<Client> findAllByManagerIdAndIsDeletedFalse(String managerId);

    // Для выпадающего списка фильтра в WEB
    @Query("SELECT DISTINCT c.category FROM Client c WHERE c.category IS NOT NULL AND c.isDeleted = false")
    List<String> findUniqueCategories();

    @Modifying
    @Transactional
    @Query("UPDATE Client c SET c.isDeleted = true WHERE c.id = :id")
    void softDeleteById(Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Client c SET c.debt = c.debt + :delta WHERE c.id = :id")
    void updateDebt(@Param("id") Long id, @Param("delta") BigDecimal delta);

    List<Client> findAllByIsDeletedFalse();


    @Query("SELECT c FROM Client c WHERE c.isDeleted = false " +
            "AND (:category IS NULL OR c.category = :category) " +
            "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.address) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Client> searchClients(@Param("keyword") String keyword,
                               @Param("category") String category,
                               Pageable pageable);


}
