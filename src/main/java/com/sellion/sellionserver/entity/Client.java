package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    private String address;
    private String ownerName;
    private String inn;
    private String phone;
    private String routeDay;
    private BigDecimal debt = BigDecimal.ZERO;
    private String managerId;
    private boolean isDeleted = false;
    private String bankAccount;
    private String bankName;
    private String category;
    private BigDecimal defaultPercent = BigDecimal.ZERO;

}