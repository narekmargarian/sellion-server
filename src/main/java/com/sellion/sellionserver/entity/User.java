package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username; // Логин (например, "operator1")
    private String password; // Пароль
    private String fullName; // ФИО

    @Enumerated(EnumType.STRING)
    private Role role; // Роль: OPERATOR, ACCOUNTANT, ADMIN
}