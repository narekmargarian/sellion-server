package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Client;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByName(String name);

    // Обновляем стандартный findAll()
    // В ClientRepository
    @Query("SELECT c FROM Client c WHERE c.isDeleted = false")
    List<Client> findAllActive();

    // Метод для мягкого удаления клиента
    @Modifying
    @Transactional
    @Query("UPDATE Client c SET c.isDeleted = true WHERE c.id = :id")
    void softDeleteById(Long id);
}