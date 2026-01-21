package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Client;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByName(String name);

    @Query("SELECT c FROM Client c WHERE c.isDeleted = false")
    List<Client> findAllActive();

    @Modifying
    @Transactional
    @Query("UPDATE Client c SET c.isDeleted = true WHERE c.id = :id")
    void softDeleteById(Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Client c SET c.debt = c.debt + :delta WHERE c.id = :id")
    void updateDebt(@Param("id") Long id, @Param("delta") Double delta);

}