package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Метод для поиска пользователя по логину (нужен для входа)
    Optional<User> findByUsername(String username);
}