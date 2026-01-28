package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;    // Название магазина (например, "Զովք Շրջանային")
    private String address; // Адрес
    private String ownerName; // ИП или владелец (например, "ИП Акопян")
    private String inn;     // ИНН предприятия
    private String phone;   // Номер телефона для связи
    private String routeDay; // День маршрута (Понедельник, Вторник и т.д.)
    private BigDecimal debt = BigDecimal.ZERO;
    private String managerId;
    private boolean isDeleted = false; // Флаг мягкого удаления
    private String bankAccount;
    private String category;

}